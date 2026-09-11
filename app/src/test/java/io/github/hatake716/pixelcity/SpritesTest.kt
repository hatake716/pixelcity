package io.github.hatake716.pixelcity

import io.github.hatake716.pixelcity.game.Monument
import io.github.hatake716.pixelcity.game.Terrain
import io.github.hatake716.pixelcity.game.TileKind
import io.github.hatake716.pixelcity.ui.MonumentSprites
import io.github.hatake716.pixelcity.ui.Sprites
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** ドット絵は文字列で書いているので、寸法と値の範囲を機械的に確かめる。 */
class SpritesTest {

    @Test
    fun `every monument has a well formed sprite`() {
        for (m in Monument.entries) {
            val s = MonumentSprites.of(m)
            assertEquals("${m.name} size", MonumentSprites.SIZE * MonumentSprites.SIZE, s.size)
            assertTrue("${m.name} palette", s.all { it in 0..3 })
            // 真っ白（全0）では建物が見えない
            assertTrue("${m.name} is blank", s.any { it > 0 })
        }
    }

    @Test
    fun `zone sprites exist for every stage`() {
        for (kind in listOf(TileKind.ZONE_R, TileKind.ZONE_C, TileKind.ZONE_I)) {
            for (stage in 0..3) {
                val s = Sprites.forZone(kind, stage)
                assertEquals("$kind stage$stage", Sprites.SIZE * Sprites.SIZE, s.size)
                assertTrue("$kind stage$stage palette", s.all { it in 0..3 })
            }
        }
    }

    @Test
    fun `terrain and building sprites are well formed`() {
        for (t in Terrain.entries) {
            assertEquals(Sprites.SIZE * Sprites.SIZE, Sprites.forTerrain(t).size)
        }
        for (kind in listOf(
            TileKind.POWER_COAL, TileKind.POWER_SOLAR, TileKind.PARK,
            TileKind.POLICE, TileKind.FIRE, TileKind.SCHOOL, TileKind.HOSPITAL,
        )) {
            val s = Sprites.forBuilding(kind)
            assertNotNull("$kind missing", s)
            assertEquals("$kind", Sprites.SIZE * Sprites.SIZE, s!!.size)
        }
    }

    @Test
    fun `road sprite varies with its connections`() {
        val cross = Sprites.roadFor(left = true, right = true, up = true, down = true)
        val vertical = Sprites.roadFor(left = false, right = false, up = true, down = true)
        val horizontal = Sprites.roadFor(left = true, right = true, up = false, down = false)
        assertTrue(!cross.contentEquals(vertical))
        assertTrue(!vertical.contentEquals(horizontal))
    }
}
