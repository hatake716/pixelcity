package io.github.hatake716.pixelcity

import android.media.AudioAttributes
import android.media.AudioTrack
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.hatake716.pixelcity.audio.Audio
import io.github.hatake716.pixelcity.audio.Bgm
import io.github.hatake716.pixelcity.audio.Sfx
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.FutureTask

/** Run only on the disposable emulator: real AudioTrack state, not stubbed calls. */
@RunWith(AndroidJUnit4::class)
class AudioPlaybackTest {
    private fun <T> main(block: () -> T): T {
        val work = FutureTask(block)
        InstrumentationRegistry.getInstrumentation().runOnMainSync(work)
        return work.get()
    }
    private fun field(a: Audio, name: String): Any? = Audio::class.java.getDeclaredField(name)
        .apply { isAccessible = true }.get(a)
    private fun track(a: Audio) = field(a, "bgmTrack") as AudioTrack?
    private fun await(message: String, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 30_000
        while (SystemClock.uptimeMillis() < deadline) {
            if (main(condition)) return
            SystemClock.sleep(40)
        }
        fail(message)
    }
    private fun withAudio(block: (Audio) -> Unit) {
        val a = main { Audio() }
        try { block(a) } finally { main { a.release() } }
    }

    @Test fun musicIsAsyncAndResumesAtTheSamePositionWithoutRestarting() = withAudio { a ->
        main { a.setBgm(Bgm.DAWN); assertNull(track(a)) }
        val cost = main {
            val start = SystemClock.uptimeMillis(); a.resume(); SystemClock.uptimeMillis() - start
        }
        assertTrue("score rendering blocked UI for $cost ms", cost < 200)
        await("DAWN never started") { track(a)?.playbackHeadPosition?.let { it > 8_000 } == true }
        val original = main { track(a)!! }
        main {
            assertEquals(AudioAttributes.CONTENT_TYPE_MUSIC, original.audioAttributes.contentType)
            val before = original.playbackHeadPosition
            a.resume(); a.setBgm(Bgm.DAWN)
            assertSame(original, track(a)); assertTrue(original.playbackHeadPosition >= before)
            a.pause()
        }
        SystemClock.sleep(120)
        val pausedAt = main { original.playbackHeadPosition }
        SystemClock.sleep(200)
        main { assertEquals(pausedAt, original.playbackHeadPosition); a.resume() }
        await("resume did not advance") { original.playbackHeadPosition > pausedAt + 2_000 }
        main { assertSame(original, track(a)); a.bgmEnabled = false; assertNull(track(a)) }
    }

    @Test fun switchingScoresCrossfadesAndDisposesTheOldTrack() = withAudio { a ->
        main { a.setBgm(Bgm.CITY); a.resume() }
        await("CITY never started") { track(a)?.playbackHeadPosition?.let { it > 24_000 } == true }
        val old = main { track(a)!! }
        main { a.setBgm(Bgm.GARDEN); assertSame(old, track(a)) }
        await("GARDEN never crossfaded") { field(a, "playingBgm") == Bgm.GARDEN && field(a, "fadingOut") != null }
        val next = main { track(a)!! }
        assertNotSame(old, next)
        await("old score leaked") { field(a, "fadingOut") == null && old.state == AudioTrack.STATE_UNINITIALIZED }
        main { assertEquals(AudioTrack.PLAYSTATE_PLAYING, next.playState) }
    }

    @Test fun rapidChangesMutePauseAndReleaseNeverResurrectPendingMusic() = withAudio { a ->
        main {
            a.resume(); a.setBgm(Bgm.CITY); a.setBgm(Bgm.GARDEN)
            a.bgmEnabled = false
        }
        SystemClock.sleep(500)
        main { assertNull(track(a)); a.bgmEnabled = true; a.setBgm(Bgm.INDUSTRY); a.pause() }
        SystemClock.sleep(500)
        main { assertNull(track(a)); a.resume(); a.setBgm(Bgm.TROUBLE); a.release(); a.release(); a.resume(); a.play(Sfx.BUILD) }
        await("worker did not stop") { (field(a, "worker") as java.util.concurrent.ExecutorService).isTerminated }
        main { assertNull(track(a)); assertTrue((field(a, "playing") as List<*>).isEmpty()) }
    }

    @Test fun soundEffectsAreCappedMutedAndReapedWithoutFurtherInput() = withAudio { a ->
        main {
            a.resume(); a.play(Sfx.BUILD); a.play(Sfx.BUILD)
            assertEquals(1, (field(a, "playing") as List<*>).size)
        }
        for (s in Sfx.entries) main { a.play(s); assertTrue((field(a, "playing") as List<*>).size <= 6) }
        await("finished effects retained native tracks") { (field(a, "playing") as List<*>).isEmpty() }
        main {
            a.play(Sfx.MONUMENT); assertTrue((field(a, "playing") as List<*>).isNotEmpty())
            a.sfxEnabled = false; assertTrue((field(a, "playing") as List<*>).isEmpty())
            a.play(Sfx.TAP); assertTrue((field(a, "playing") as List<*>).isEmpty())
        }
    }
}
