package io.github.hatake716.pixelcity

import io.github.hatake716.pixelcity.game.City
import io.github.hatake716.pixelcity.game.Ordinance
import io.github.hatake716.pixelcity.game.Terrain
import io.github.hatake716.pixelcity.game.TileKind
import io.github.hatake716.pixelcity.game.Traffic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 交通量。交通工学の式のとおりに動くことを確かめる。
 *
 * 数式そのものの正しさ（BPR式など）と、
 * 街のなかで正しく効くこと（道を足せば混雑が減る）の両方を見る。
 */
class TrafficTest {

    // ------------------------------------------------------------------
    // 式そのもの
    // ------------------------------------------------------------------

    /**
     * BPR式が、原典のとおりの値を返すこと。
     *
     * t = t0 * (1 + 0.15 * (v/c)^4)
     * v/c = 1.0 で 1.15倍、2.0 で 3.4倍。
     */
    @Test
    fun `the BPR function matches the published curve`() {
        assertEquals(1.00f, Traffic.delayFactor(0f), 0.001f)
        assertEquals(1.15f, Traffic.delayFactor(1f), 0.001f)
        // 1.5^4 = 5.0625, 0.15 * 5.0625 = 0.759
        assertEquals(1.759f, Traffic.delayFactor(1.5f), 0.01f)
        // 2^4 = 16, 0.15 * 16 = 2.4
        assertEquals(3.40f, Traffic.delayFactor(2f), 0.01f)
    }

    /** 混むほど遅くなり、途中で速くなったりしないこと。 */
    @Test
    fun `travel time only ever gets worse as traffic grows`() {
        var prev = 0f
        var vc = 0f
        while (vc <= 3f) {
            val t = Traffic.delayFactor(vc)
            assertTrue("the curve went backwards at v/c=$vc", t >= prev)
            prev = t
            vc += 0.05f
        }
    }

    /**
     * サービス水準が、Highway Capacity Manual の区切りと合うこと。
     */
    @Test
    fun `level of service matches the standard thresholds`() {
        assertEquals(Traffic.Los.A, Traffic.losOf(0.30f))
        assertEquals(Traffic.Los.A, Traffic.losOf(0.60f))
        assertEquals(Traffic.Los.B, Traffic.losOf(0.65f))
        assertEquals(Traffic.Los.C, Traffic.losOf(0.75f))
        assertEquals(Traffic.Los.D, Traffic.losOf(0.85f))
        assertEquals(Traffic.Los.E, Traffic.losOf(0.95f))
        assertEquals(Traffic.Los.F, Traffic.losOf(1.50f))
        // D 以上を渋滞と呼ぶ
        assertTrue(!Traffic.losOf(0.70f).isCongested)
        assertTrue(Traffic.losOf(0.85f).isCongested)
    }

    /**
     * Greenshields のモデルどおり、容量のところで
     * 速度が自由速度の半分になること。
     */
    @Test
    fun `speed halves at capacity, as Greenshields predicts`() {
        assertEquals(1.0f, Traffic.speedFactor(0f), 0.001f)
        assertEquals(0.5f, Traffic.speedFactor(1f), 0.001f)
        // 混むほど遅くなる
        assertTrue(Traffic.speedFactor(0.5f) > Traffic.speedFactor(0.9f))
        assertTrue(Traffic.speedFactor(1.0f) > Traffic.speedFactor(2.0f))
        // 止まりはしない（0 で割らない）
        assertTrue(Traffic.speedFactor(10f) > 0f)
    }

    /** 重力モデルが、距離の2乗に反比例すること。 */
    @Test
    fun `the gravity model falls off with the square of distance`() {
        val at1 = Traffic.friction(1f)
        val at2 = Traffic.friction(2f)
        val at4 = Traffic.friction(4f)
        // 距離が2倍なら 1/4
        assertEquals(at1 / 4f, at2, at1 * 0.01f)
        assertEquals(at2 / 4f, at4, at2 * 0.01f)
    }

    /**
     * 道の容量が、実際の目安に沿っていること。
     *
     * よくある誤りは、飽和交通流率（1900）を
     * そのまま1車線の1時間の容量にしてしまうこと。
     */
    @Test
    fun `road capacities follow the published figures`() {
        assertEquals(600, Traffic.capacityPerHour(TileKind.ROAD))
        assertEquals(1_900, Traffic.capacityPerHour(TileKind.AVENUE))
        assertEquals(4_000, Traffic.capacityPerHour(TileKind.HIGHWAY))
        // 大きい道ほど多く通せる
        assertTrue(
            Traffic.capacityPerHour(TileKind.HIGHWAY) >
                Traffic.capacityPerHour(TileKind.AVENUE),
        )
        assertTrue(
            Traffic.capacityPerHour(TileKind.AVENUE) >
                Traffic.capacityPerHour(TileKind.ROAD),
        )
        // 道でないものは 0
        assertEquals(0, Traffic.capacityPerHour(TileKind.ZONE_R))
    }

    // ------------------------------------------------------------------
    // 街のなかでの効き目
    // ------------------------------------------------------------------

    /** 住宅と職場をつないだ、小さな街。 */
    private fun commuterCity(road: TileKind = TileKind.ROAD): City =
        City(40, 40).apply {
            funds = 5_000_000
            for (t in tiles) t.terrain = Terrain.LAND
            // 横に1本の道
            for (x in 4..34) build(x, 20, road)
            build(3, 20, TileKind.POWER_COAL)
            // 西側に住宅、東側に職場
            for (x in 6..14) {
                build(x, 19, TileKind.ZONE_R)
                build(x, 21, TileKind.ZONE_R)
            }
            for (x in 24..32) {
                build(x, 19, TileKind.ZONE_C)
                build(x, 21, TileKind.ZONE_I)
            }
            repeat(80) { step() }
        }

    /** 住宅があれば、道に交通量が出ること。 */
    @Test
    fun `homes put traffic on the road`() {
        val c = commuterCity()
        val onRoad = c.tiles.filter { it.kind == TileKind.ROAD }
        assertTrue("no houses grew", c.tiles.any { it.kind == TileKind.ZONE_R && it.stage > 0 })
        assertTrue("no traffic anywhere", onRoad.any { it.traffic > 0 })
    }

    /**
     * 大通りにすると、同じ街でも混雑が減ること。
     *
     * 容量が 600 から 1900 に増えるので、v/c が下がる。
     * これが「道を広げれば楽になる」の理屈。
     */
    @Test
    fun `widening the road reduces congestion`() {
        val narrow = commuterCity(TileKind.ROAD)
        val wide = commuterCity(TileKind.AVENUE)
        assertTrue(
            "the avenue was not less congested " +
                "(${narrow.congestionRate}% vs ${wide.congestionRate}%)",
            wide.congestionRate <= narrow.congestionRate,
        )
    }

    /** 交通量が、道の容量と釣り合った桁に収まること。 */
    @Test
    fun `traffic volumes stay in a believable range`() {
        val c = commuterCity()
        for (t in c.tiles) {
            if (t.kind != TileKind.ROAD) continue
            // 容量の10倍を超えるような数は、計算が壊れている証拠
            assertTrue(
                "traffic ${t.traffic} is far beyond capacity",
                t.traffic <= Traffic.capacityPerHour(TileKind.ROAD) * 10,
            )
            assertTrue("traffic went negative", t.traffic >= 0)
        }
    }

    /** 住宅から遠い道ほど、交通量が少ないこと（距離減衰）。 */
    @Test
    fun `traffic thins out with distance from the homes`() {
        val c = commuterCity()
        // 住宅のすぐ隣（x=10）と、遠く離れた端（x=34）
        val near = c.tileAt(10, 20).traffic
        val far = c.tileAt(34, 20).traffic
        assertTrue("traffic did not fall with distance ($near vs $far)", near > far)
    }

    /**
     * 公共交通の条例で、車の交通量が減ること（第3段階＝分担）。
     *
     * バス停や駅は人口が要る（800／8000）ので、小さな街では建てられない。
     * 同じ第3段階を通る「こうつうの ほじょ」条例で確かめる。
     */
    @Test
    fun `public transport support takes cars off the road`() {
        val plain = commuterCity()
        val subsidised = commuterCity().apply {
            ordinances.add(Ordinance.TRANSIT_SUBSIDY)
            repeat(12) { step() }
        }
        val a = plain.tiles.filter { it.kind == TileKind.ROAD }.sumOf { it.traffic }
        val b = subsidised.tiles.filter { it.kind == TileKind.ROAD }.sumOf { it.traffic }
        assertTrue("the subsidy did not reduce traffic ($a vs $b)", b < a)
    }

    /**
     * バス停の近くでは、車に乗る割合が下がること。
     *
     * 交通量そのものではなく、分担の値（transitRelief）で見る。
     * これなら、街の大きさに左右されずに確かめられる。
     */
    @Test
    fun `a bus stop lowers the share of people who drive`() {
        val c = City(30, 30).apply {
            funds = 5_000_000
            for (t in tiles) t.terrain = Terrain.LAND
            for (x in 4..26) build(x, 15, TileKind.ROAD)
            build(3, 15, TileKind.POWER_COAL)
            for (x in 6..12) build(x, 14, TileKind.ZONE_R)
            for (x in 20..25) build(x, 14, TileKind.ZONE_C)
            repeat(40) { step() }
        }
        val before = c.tileAt(8, 14).transitRelief
        // 解禁を待たずに直接置く（分担の計算だけを見たいため）
        c.tileAt(8, 16).kind = TileKind.BUS_STOP
        c.step()
        val after = c.tileAt(8, 14).transitRelief
        assertEquals("there should be no relief without a stop", 0, before)
        assertTrue("the bus stop gave no relief", after > 0)
    }

    /** 道がなければ、交通量は 0 のままであること。 */
    @Test
    fun `a city with no roads has no traffic`() {
        val c = City(20, 20).apply {
            for (t in tiles) t.terrain = Terrain.LAND
            repeat(12) { step() }
        }
        assertEquals(0, c.congestionRate)
        assertTrue(c.tiles.all { it.traffic == 0 })
    }
}
