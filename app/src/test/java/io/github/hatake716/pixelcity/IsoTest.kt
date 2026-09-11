package io.github.hatake716.pixelcity

import io.github.hatake716.pixelcity.ui.Iso
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 斜め見下ろしの座標変換。往復して元に戻ることを確かめる。 */
class IsoTest {

    @Test
    fun `tile coordinates round trip through screen coordinates`() {
        for (ty in 0..20) for (tx in 0..20) {
            // 菱形の中心を取り、そこから戻して同じタイルになること
            val sx = Iso.screenX(tx, ty).toFloat()
            val sy = Iso.screenY(tx, ty).toFloat() + Iso.TILE_H / 2f
            val (rx, ry) = Iso.tileAt(sx, sy)
            assertEquals("tx at ($tx,$ty)", tx, rx)
            assertEquals("ty at ($tx,$ty)", ty, ry)
        }
    }

    @Test
    fun `x axis runs down-right and y axis runs down-left`() {
        val originX = Iso.screenX(0, 0)
        // x が増えると右へ
        assertTrue(Iso.screenX(1, 0) > originX)
        // y が増えると左へ
        assertTrue(Iso.screenX(0, 1) < originX)
        // どちらも下へ
        assertTrue(Iso.screenY(1, 0) > Iso.screenY(0, 0))
        assertTrue(Iso.screenY(0, 1) > Iso.screenY(0, 0))
    }

    @Test
    fun `depth increases toward the viewer`() {
        assertTrue(Iso.depth(0, 0) < Iso.depth(1, 0))
        assertTrue(Iso.depth(0, 0) < Iso.depth(0, 1))
        // 同じ奥行きの帯になる
        assertEquals(Iso.depth(2, 0), Iso.depth(0, 2))
    }

    @Test
    fun `the diamond test accepts the centre and rejects the corners`() {
        assertTrue(Iso.insideDiamond(0f, 0f))
        // 菱形の外（正方形の角）は入らない
        assertTrue(!Iso.insideDiamond(Iso.TILE_W / 2f, Iso.TILE_H / 2f))
        // 左右の頂点は内側
        assertTrue(Iso.insideDiamond(Iso.TILE_W / 2f - 1f, 0f))
    }
}
