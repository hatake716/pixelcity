package io.github.hatake716.pixelcity.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import java.util.concurrent.Executors
import java.util.concurrent.Future

/** Main-thread controller. Long PCM rendering and native buffer writes run on a worker. */
class Audio {
    private val handler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor { task ->
        Thread(task, "Pixelcity-score").apply { isDaemon = true; priority = Thread.MIN_PRIORITY }
    }
    private var pending: Future<*>? = null
    private var generation = 0
    private var paused = true
    private var released = false
    private var currentBgm: Bgm? = null
    private var playingBgm: Bgm? = null
    private var bgmTrack: AudioTrack? = null
    private var fadingOut: AudioTrack? = null
    private val fadeToken = Any()
    // Accessed only by the worker; bounded to two scores (under 13 MB of PCM).
    private val bgmCache = LinkedHashMap<Bgm, ShortArray>()
    private val sfxCache = HashMap<Sfx, ShortArray>()
    private val playing = ArrayList<AudioTrack>()
    private val lastPlayed = HashMap<Sfx, Long>()

    var sfxEnabled = true
        set(value) { field = value; if (!value) stopAllSfx() }
    var bgmEnabled = true
        set(value) {
            if (field == value) return
            field = value
            if (value) startMusic() else stopMusic()
        }

    fun play(sfx: Sfx) {
        if (!sfxEnabled || paused || released) return
        val now = SystemClock.uptimeMillis()
        if (now - (lastPlayed[sfx] ?: -1_000L) < 75L) return
        lastPlayed[sfx] = now
        var track: AudioTrack? = null
        try {
            val pcm = sfxCache.getOrPut(sfx) { sfx.render() }
            val sound = createTrack(pcm, music = false)
            track = sound
            if (playing.size >= MAX_VOICES) dispose(playing.removeAt(0))
            playing.add(sound)
            sound.setVolume(0.72f)
            sound.play()
            // MODE_STATIC does not necessarily change playState at the end of the buffer.
            // Reap by playback head, even when no further input ever arrives.
            val cleanup = object : Runnable {
                override fun run() {
                    if (sound !in playing) return
                    if (runCatching { sound.playbackHeadPosition >= pcm.size }.getOrDefault(true)) {
                        playing.remove(sound); dispose(sound)
                    } else handler.postDelayed(this, 100L)
                }
            }
            handler.postDelayed(cleanup, pcm.size * 1000L / Waves.RATE + 80L)
        } catch (e: Exception) {
            track?.let { playing.remove(it); dispose(it) }
            Log.w("PixelcityAudio", "Could not play $sfx", e)
        }
    }

    private fun stopAllSfx() {
        playing.forEach(::dispose); playing.clear(); lastPlayed.clear()
    }

    private fun createTrack(pcm: ShortArray, music: Boolean): AudioTrack {
        val track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(if (music) AudioAttributes.CONTENT_TYPE_MUSIC
                    else AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(Waves.RATE)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(pcm.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC).build()
        try {
            check(track.write(pcm, 0, pcm.size) == pcm.size)
            if (music) check(track.setLoopPoints(0, pcm.size, -1) == AudioTrack.SUCCESS)
            return track
        } catch (e: Exception) { dispose(track); throw e }
    }

    fun setBgm(bgm: Bgm) {
        if (released || currentBgm == bgm) return
        currentBgm = bgm
        startMusic()
    }

    private fun cancelPending() {
        generation++; pending?.cancel(true); pending = null
    }

    private fun startMusic() {
        if (released || paused || !bgmEnabled) return
        val bgm = currentBgm ?: return
        if (playingBgm == bgm && bgmTrack != null) {
            cancelPending()
            bgmTrack?.runCatching { play() }
            return
        }
        cancelPending()
        val request = generation
        pending = worker.submit {
            var prepared: AudioTrack? = null
            try {
                val pcm = bgmCache[bgm] ?: bgm.render().also {
                    bgmCache[bgm] = it
                    if (bgmCache.size > 2) bgmCache.remove(bgmCache.keys.first())
                }
                if (Thread.currentThread().isInterrupted) return@submit
                prepared = createTrack(pcm, music = true)
                val ready = prepared
                if (Thread.currentThread().isInterrupted) { dispose(ready); return@submit }
                handler.post {
                    if (released || paused || !bgmEnabled || request != generation) dispose(ready)
                    else {
                        pending = null
                        crossfade(ready, bgm)
                    }
                }
            } catch (e: Exception) {
                prepared?.let(::dispose)
                if (e !is java.util.concurrent.CancellationException && !Thread.currentThread().isInterrupted)
                    Log.w("PixelcityAudio", "Could not prepare $bgm", e)
            }
        }
    }

    private fun finishFade() {
        handler.removeCallbacksAndMessages(fadeToken)
        fadingOut?.let(::dispose); fadingOut = null
        bgmTrack?.runCatching { setVolume(BGM_VOLUME) }
    }

    private fun crossfade(ready: AudioTrack, bgm: Bgm) {
        finishFade()
        fadingOut = bgmTrack
        bgmTrack = ready; playingBgm = bgm
        try {
            ready.setVolume(0f); ready.play()
            val began = SystemClock.uptimeMillis()
            val ramp = object : Runnable {
                override fun run() {
                    if (released || paused || bgmTrack !== ready) return
                    val blend = ((SystemClock.uptimeMillis() - began) / 600f).coerceIn(0f, 1f)
                    ready.runCatching { setVolume(BGM_VOLUME * blend) }
                    fadingOut?.runCatching { setVolume(BGM_VOLUME * (1f - blend)) }
                    if (blend < 1f) handler.postAtTime(this, fadeToken, SystemClock.uptimeMillis() + 25)
                    else finishFade()
                }
            }
            ramp.run()
        } catch (e: Exception) {
            stopMusic(); Log.w("PixelcityAudio", "Could not start $bgm", e)
        }
    }

    private fun stopMusic() {
        cancelPending(); finishFade()
        bgmTrack?.let(::dispose); bgmTrack = null; playingBgm = null
    }

    fun pause() {
        if (released || paused) return
        paused = true; cancelPending(); stopAllSfx(); finishFade()
        // Keep the loop's position while the player uses another screen/app.
        bgmTrack?.runCatching { pause() }
    }

    fun resume() {
        if (released || !paused) return
        paused = false; startMusic()
    }

    fun release() {
        if (released) return
        released = true; paused = true
        stopAllSfx(); stopMusic(); worker.shutdownNow()
        // Pending worker callbacks still check `released` and release their own prepared track.
    }

    private fun dispose(track: AudioTrack) {
        track.runCatching { stop() }
        track.runCatching { release() }
    }

    private companion object {
        const val MAX_VOICES = 6
        const val BGM_VOLUME = 0.52f
    }
}
