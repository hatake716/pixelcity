package io.github.hatake716.pixelcity

import io.github.hatake716.pixelcity.game.TileKind
import io.github.hatake716.pixelcity.ui.Iso
import io.github.hatake716.pixelcity.ui.IsoBuildings
import io.github.hatake716.pixelcity.ui.Pix
import io.github.hatake716.pixelcity.ui.Sprite
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 街並みのバリエーション。
 *
 * 同じ形の箱が並ぶと、どれだけ数があっても「街」に見えない。
 * 棟ごとに違う絵が出ることを、数で確かめる。
 */
class VarietyTest {

    private val kinds = listOf(TileKind.ZONE_R, TileKind.ZONE_C, TileKind.ZONE_I)

    /**
     * 絵ができあがるまで待つ。
     *
     * 本番では別の糸で作り、できるまでは「間に合わせの箱」を返す。
     * 試験でそれを比べると、どれも同じ箱に見えてしまう。
     */
    private fun build(kind: TileKind, stage: Int, v: Int): Sprite {
        IsoBuildings.zoneBuilding(kind, stage, v)      // 作り始めさせる
        val until = System.currentTimeMillis() + 30_000
        while (!IsoBuildings.isReady(kind, stage, v)) {
            if (System.currentTimeMillis() > until) {
                throw AssertionError("$kind stage $stage variant $v was never built")
            }
            Thread.sleep(10)
        }
        return IsoBuildings.zoneBuilding(kind, stage, v)
    }

    private fun shape(s: Sprite): String = buildString {
        for (y in 0 until s.height step 8) {
            for (x in 0 until s.width step 8) {
                append(if (s.at(x, y) == Pix.TRANSPARENT) '.' else '#')
            }
        }
    }

    /** 同じ段階のなかで、見た目が散らばっていること。 */
    @Test
    fun `buildings of the same stage do not all look alike`() {
        for (kind in kinds) {
            for (stage in 1..3) {
                val looks = (0 until IsoBuildings.VARIANTS).map {
                    val s = build(kind, stage, it)
                    // 形と、使っている色の組で見分ける
                    shape(s) to s.data.filter { v -> v != Pix.TRANSPARENT }.toSet()
                }
                val distinct = looks.toSet().size
                assertTrue(
                    "$kind stage $stage has only $distinct different looks",
                    distinct >= IsoBuildings.VARIANTS - 1,
                )
            }
        }
    }

    /** どの棟も、1タイルの幅に収まっていること。 */
    @Test
    fun `every building fits one tile across`() {
        for (kind in kinds) for (stage in 1..3) {
            for (v in 0 until IsoBuildings.VARIANTS) {
                val s = build(kind, stage, v)
                assertEquals("$kind $stage $v width", Iso.TILE_W, s.width)
                assertTrue("$kind $stage $v is not taller than the ground", s.height > Iso.TILE_H)
            }
        }
    }

    /** 段階が上がるほど、高くなること。 */
    @Test
    fun `a later stage is taller than an earlier one`() {
        for (kind in kinds) {
            val h1 = build(kind, 1, 0).height
            val h2 = build(kind, 2, 0).height
            val h3 = build(kind, 3, 0).height
            assertTrue("$kind: stage 2 ($h2) is not taller than stage 1 ($h1)", h2 > h1)
            assertTrue("$kind: stage 3 ($h3) is not taller than stage 2 ($h2)", h3 > h2)
        }
    }

    /** 同じ場所を何度たずねても、同じ絵が返ること。 */
    @Test
    fun `the same plot always gets the same building`() {
        val a = build(TileKind.ZONE_R, 2, 3)
        val b = IsoBuildings.zoneBuilding(TileKind.ZONE_R, 2, 3)
        assertTrue("the building changed between lookups", a === b)
    }

    /** 番号が範囲の外でも、落ちずに何か返ること。 */
    @Test
    fun `an out of range variant still gives a building`() {
        for (v in intArrayOf(-5, -1, 0, 99, 1000)) {
            val s = IsoBuildings.zoneBuilding(TileKind.ZONE_C, 2, v)
            assertEquals("variant $v width", Iso.TILE_W, s.width)
        }
    }
}
