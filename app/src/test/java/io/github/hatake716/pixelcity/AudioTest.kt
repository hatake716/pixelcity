package io.github.hatake716.pixelcity

import io.github.hatake716.pixelcity.audio.Bgm
import io.github.hatake716.pixelcity.audio.Sfx
import io.github.hatake716.pixelcity.audio.Waves
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 音。絵と同じく、式から作っているので Android なしで確かめられる。
 *
 * 耳で聞くのが最後の判断だが、
 *  - 無音でないこと
 *  - 音が割れていないこと
 *  - 端が急に切れていないこと（プチッと鳴らない）
 * は数で確かめられる。
 */
class AudioTest {

    private fun peak(pcm: ShortArray): Int = pcm.maxOf { abs(it.toInt()) }

    /** どの効果音も、鳴っていること。 */
    @Test
    fun `every sound effect makes a sound`() {
        for (sfx in Sfx.entries) {
            val pcm = sfx.render()
            assertTrue("${sfx.name} is empty", pcm.isNotEmpty())
            assertTrue("${sfx.name} is silent (peak ${peak(pcm)})", peak(pcm) > 2_000)
        }
    }

    /**
     * 音が割れていないこと。
     *
     * 16bit の上限に張りついていると、耳ざわりな音になる。
     * [Waves.Track.toPcm] が全体をならしているので、上限には届かないはず。
     */
    @Test
    fun `sounds do not clip`() {
        for (sfx in Sfx.entries) {
            val pcm = sfx.render()
            val stuck = pcm.count { abs(it.toInt()) >= 32_760 }
            assertTrue("${sfx.name} clips at $stuck samples", stuck < pcm.size / 200)
        }
    }

    /**
     * 音の終わりが静かなこと。
     *
     * 鳴っている途中で切ると、プチッという雑音が入る。
     * 減衰を入れてあるので、最後のほうは小さくなっているはず。
     */
    @Test
    fun `sounds fade out instead of being cut off`() {
        for (sfx in Sfx.entries) {
            val pcm = sfx.render()
            val tailStart = (pcm.size * 0.94f).toInt()
            val tail = pcm.copyOfRange(tailStart, pcm.size)
            val tailPeak = peak(tail)
            assertTrue(
                "${sfx.name} is cut off (tail peak $tailPeak vs ${peak(pcm)})",
                tailPeak < peak(pcm) / 3,
            )
        }
    }

    /** 効果音どうしが、それぞれ違う音であること。 */
    @Test
    fun `each sound effect differs from the others`() {
        val rendered = Sfx.entries.associateWith { it.render() }
        for (a in Sfx.entries) for (b in Sfx.entries) {
            if (a >= b) continue
            assertNotEquals("$a and $b are the same sound", rendered[a]!!.toList(), rendered[b]!!.toList())
        }
    }

    /**
     * 街づくりの音は短いこと。
     *
     * 何百回も鳴るので、長いと重なって濁る。
     */
    @Test
    fun `the sounds heard most often are short`() {
        val frequent = listOf(Sfx.ROAD, Sfx.ZONE_R, Sfx.ZONE_C, Sfx.ZONE_I, Sfx.BUILD, Sfx.TAP)
        for (sfx in frequent) {
            val seconds = sfx.render().size.toFloat() / Waves.RATE
            assertTrue("${sfx.name} is too long ($seconds s)", seconds <= 0.35f)
        }
    }

    /** 曲は、繰り返せる長さがあること。 */
    @Test
    fun `every tune is long enough to loop`() {
        for (bgm in Bgm.entries) {
            val pcm = bgm.render()
            val seconds = pcm.size.toFloat() / Waves.RATE
            assertTrue("${bgm.name} is too short ($seconds s)", seconds >= 8f)
            assertTrue("${bgm.name} is silent", peak(pcm) > 2_000)
            assertTrue("${bgm.name} has no name", bgm.label.isNotBlank())
        }
    }

    /**
     * 曲の継ぎ目で音が飛ばないこと。
     *
     * 繰り返して流すので、終わりと始まりがどちらも静かでないと、
     * 一周するたびにプチッと鳴る。
     */
    @Test
    fun `tunes start and end quietly so the loop is seamless`() {
        for (bgm in Bgm.entries) {
            val pcm = bgm.render()
            val head = peak(pcm.copyOfRange(0, 64))
            val tail = peak(pcm.copyOfRange(pcm.size - 64, pcm.size))
            val full = peak(pcm)
            assertTrue("${bgm.name} starts abruptly ($head vs $full)", head < full / 4)
            assertTrue("${bgm.name} ends abruptly ($tail vs $full)", tail < full / 4)
        }
    }

    /** 曲どうしが、別の曲であること。 */
    @Test
    fun `each tune differs from the others`() {
        val rendered = Bgm.entries.associateWith { it.render() }
        for (a in Bgm.entries) for (b in Bgm.entries) {
            if (a >= b) continue
            assertNotEquals("$a and $b are the same tune", rendered[a]!!.toList(), rendered[b]!!.toList())
        }
    }

    /**
     * 何度作っても同じ音になること。
     *
     * 雑音に乱数を使っているので、種を固定していないと
     * 鳴らすたびに音が変わり、試験でも確かめられなくなる。
     */
    @Test
    fun `rendering twice gives the same sound`() {
        for (sfx in Sfx.entries) {
            assertEquals("${sfx.name} is not repeatable",
                sfx.render().toList(), sfx.render().toList())
        }
    }

    /** 音の高さが、12平均律で並んでいること。 */
    @Test
    fun `notes follow equal temperament`() {
        assertEquals(440f, Waves.freq(Waves.A4), 0.01f)
        // 1オクターブ上は2倍
        assertEquals(880f, Waves.freq(Waves.A4 + 12), 0.01f)
        // ドはラより9半音下
        assertTrue(Waves.freq(Waves.C4) < Waves.freq(Waves.A4))
        assertTrue(Waves.freq(Waves.C5) > Waves.freq(Waves.A4))
    }
}
