package io.github.hatake716.pixelcity.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 音を鳴らす係。
 *
 * 波形は [Sfx] と [Bgm] が作る。ここは鳴らすことだけを受け持つ。
 *
 * ## 作りの方針
 *
 * - 効果音は [android.media.SoundPool] ではなく、短い [AudioTrack] を使い捨てる。
 *   SoundPool は音の入ったファイルを読む前提の仕組みで、
 *   ここでは配列を直接渡したいため。
 * - 曲は 1本の [AudioTrack] を静的モードで持ち、端末側で繰り返させる。
 *   自前で書き足し続けると、画面が重いときに音が途切れる。
 * - 波形を作るのは重い（数十ミリ秒）。作った結果は使い回す。
 * - 音を切っているときは、何も作らない。
 */
class Audio {

    /** 効果音を鳴らすか。 */
    var sfxEnabled: Boolean = true
        set(value) {
            field = value
            if (!value) stopAllSfx()
        }

    /** 曲を流すか。 */
    var bgmEnabled: Boolean = true
        set(value) {
            if (field == value) return
            field = value
            if (value) restartBgm() else stopBgm()
        }

    /** 作った波形の置き場。同じ音を何度も作らない。 */
    private val sfxCache = HashMap<Sfx, ShortArray>()
    private val bgmCache = HashMap<Bgm, ShortArray>()

    /** 鳴っている最中の効果音。止めるときに使う。 */
    private val playing = ArrayList<AudioTrack>()

    private var bgmTrack: AudioTrack? = null
    private var currentBgm: Bgm? = null

    /** 画面が消えているあいだは鳴らさない。 */
    private val paused = AtomicBoolean(false)

    // ------------------------------------------------------------------
    // 効果音
    // ------------------------------------------------------------------

    fun play(sfx: Sfx) {
        if (!sfxEnabled || paused.get()) return
        val pcm = sfxCache.getOrPut(sfx) { sfx.render() }
        try {
            val track = newTrack(pcm.size)
            track.write(pcm, 0, pcm.size)
            // 鳴り終わったものを片づけてから足す
            reapFinished()
            // 一度に鳴らしすぎると、音が濁って端末も苦しくなる
            if (playing.size >= MAX_VOICES) {
                playing.removeAt(0).runCatching { stop(); release() }
            }
            playing.add(track)
            track.play()
        } catch (e: Exception) {
            // 音が鳴らないことで遊べなくなっては困るので、握りつぶす
        }
    }

    /** 鳴り終わった音を片づける。 */
    private fun reapFinished() {
        val it = playing.iterator()
        while (it.hasNext()) {
            val t = it.next()
            if (t.playState == AudioTrack.PLAYSTATE_STOPPED) {
                t.runCatching { release() }
                it.remove()
            }
        }
    }

    private fun stopAllSfx() {
        for (t in playing) t.runCatching { stop(); release() }
        playing.clear()
    }

    private fun newTrack(frames: Int): AudioTrack {
        val bytes = frames * 2
        val format = AudioFormat.Builder()
            .setSampleRate(Waves.RATE)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            AudioTrack.Builder()
                .setAudioAttributes(attrs)
                .setAudioFormat(format)
                .setBufferSizeInBytes(bytes)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
        } else {
            @Suppress("DEPRECATION")
            AudioTrack(
                AudioManager.STREAM_MUSIC, Waves.RATE,
                AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
                bytes, AudioTrack.MODE_STATIC,
            )
        }
    }

    // ------------------------------------------------------------------
    // 曲
    // ------------------------------------------------------------------

    /**
     * 流す曲を決める。同じ曲なら何もしない。
     * 曲が変わるときだけ、鳴らし直す。
     */
    fun setBgm(bgm: Bgm) {
        if (currentBgm == bgm) return
        currentBgm = bgm
        if (bgmEnabled && !paused.get()) restartBgm()
    }

    private fun restartBgm() {
        stopBgm()
        val bgm = currentBgm ?: return
        if (!bgmEnabled || paused.get()) return
        try {
            val pcm = bgmCache.getOrPut(bgm) { bgm.render() }
            val track = newTrack(pcm.size)
            track.write(pcm, 0, pcm.size)
            // 端末側で繰り返させる。自前で書き足すと、画面が重いときに途切れる。
            track.setLoopPoints(0, pcm.size, -1)
            track.setVolume(BGM_VOLUME)
            track.play()
            bgmTrack = track
        } catch (e: Exception) {
            bgmTrack = null
        }
    }

    private fun stopBgm() {
        bgmTrack?.runCatching { stop(); release() }
        bgmTrack = null
    }

    // ------------------------------------------------------------------
    // 画面の出入り
    // ------------------------------------------------------------------

    fun pause() {
        paused.set(true)
        stopAllSfx()
        stopBgm()
    }

    fun resume() {
        paused.set(false)
        if (bgmEnabled) restartBgm()
    }

    fun release() {
        stopAllSfx()
        stopBgm()
    }

    private companion object {
        /** 同時に鳴らす効果音の上限。 */
        const val MAX_VOICES = 6
        /** 曲の音量。効果音より控えめにして、操作の手ごたえを消さない。 */
        const val BGM_VOLUME = 0.42f
    }
}
