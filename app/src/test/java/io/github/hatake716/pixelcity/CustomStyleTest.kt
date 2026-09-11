package io.github.hatake716.pixelcity

import io.github.hatake716.pixelcity.game.City
import io.github.hatake716.pixelcity.game.CustomStyle
import io.github.hatake716.pixelcity.ui.IsoBuildings
import io.github.hatake716.pixelcity.ui.Palette
import io.github.hatake716.pixelcity.ui.Pix
import io.github.hatake716.pixelcity.ui.Sprite
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 自分で決める様式。
 *
 * 決められた7つの様式とちがい、色は遊ぶ人が決める。
 * だから守るべきなのは「決めた色が、そのとおりに出る」ことと、
 * 「決めていないうちは、標準のまま何も壊れない」ことの2点。
 */
class CustomStyleTest {

    private fun samples(): List<Pair<String, Sprite>> = listOf(
        "HOUSE_2" to IsoBuildings.HOUSE_2,
        "HOUSE_3" to IsoBuildings.HOUSE_3,
        "SHOP_3" to IsoBuildings.SHOP_3,
        "FACTORY_2" to IsoBuildings.FACTORY_2,
    )

    private fun coloursOf(s: Sprite): Set<Int> =
        s.data.filter { it != Pix.TRANSPARENT }.map { it.toInt() }.toSet()

    /** 選べる場所に、名前と初期の色があること。 */
    @Test
    fun `every slot is named and starts from a real colour`() {
        assertTrue(CustomStyle.Slot.entries.isNotEmpty())
        for (sl in CustomStyle.Slot.entries) {
            assertTrue("${sl.name} has no label", sl.label.isNotBlank())
            assertTrue("${sl.name} source out of range", sl.source in 0 until Palette.SIZE)
            assertTrue("${sl.name} default out of range", sl.default in 0 until Palette.SIZE)
        }
    }

    /** 同じ場所を二重に割り当てていないこと（片方の変更がもう片方に化ける）。 */
    @Test
    fun `no two slots edit the same colour`() {
        val seen = mutableSetOf<Int>()
        for (sl in CustomStyle.Slot.entries) {
            assertTrue("${sl.name} duplicates another slot", seen.add(sl.source))
        }
    }

    /** 何も決めていなければ、標準のまま。 */
    @Test
    fun `an untouched custom style is the standard one`() {
        val c = CustomStyle()
        assertTrue(c.isDefault)
        for ((_, s) in samples()) {
            assertTrue(IsoBuildings.styled(s, City.Style.CUSTOM, c) === s)
        }
    }

    /** 決めた色が、実際に絵に出ること。 */
    @Test
    fun `a chosen colour actually appears on the building`() {
        val c = CustomStyle()
        c[CustomStyle.Slot.HOUSE_ROOF] = Palette.GOLD
        assertFalse(c.isDefault)

        val before = coloursOf(IsoBuildings.HOUSE_2)
        val after = coloursOf(IsoBuildings.styled(IsoBuildings.HOUSE_2, City.Style.CUSTOM, c))
        assertNotEquals("the building did not change", before, after)
        assertTrue("the chosen colour is missing", Palette.GOLD in after)
    }

    /** 色を変えても、形は変わらないこと。 */
    @Test
    fun `choosing colours never changes the shape`() {
        val c = CustomStyle()
        for (sl in CustomStyle.Slot.entries) c[sl] = Palette.GOLD
        for ((name, base) in samples()) {
            val styled = IsoBuildings.styled(base, City.Style.CUSTOM, c)
            assertEquals("$name width", base.width, styled.width)
            assertEquals("$name height", base.height, styled.height)
            for (i in base.data.indices) {
                assertEquals(
                    "$name shape differs at $i",
                    base.data[i] == Pix.TRANSPARENT,
                    styled.data[i] == Pix.TRANSPARENT,
                )
            }
        }
    }

    /** もとに もどす、で初期の色に戻ること。 */
    @Test
    fun `reset puts every colour back`() {
        val c = CustomStyle()
        for (sl in CustomStyle.Slot.entries) c[sl] = Palette.GOLD
        c.reset()
        assertTrue(c.isDefault)
        for (sl in CustomStyle.Slot.entries) assertEquals(sl.default, c[sl])
    }

    /** 同じ色づかいなら、絵を作り直さないこと。 */
    @Test
    fun `custom sprites are cached while the colours stay the same`() {
        val c = CustomStyle()
        c[CustomStyle.Slot.HOUSE_ROOF] = Palette.SKY
        val a = IsoBuildings.styled(IsoBuildings.HOUSE_2, City.Style.CUSTOM, c)
        val b = IsoBuildings.styled(IsoBuildings.HOUSE_2, City.Style.CUSTOM, c)
        assertTrue(a === b)

        // 色を変えたら、別の絵になること（古い絵を使い回さない）。
        c[CustomStyle.Slot.HOUSE_ROOF] = Palette.RED
        val d = IsoBuildings.styled(IsoBuildings.HOUSE_2, City.Style.CUSTOM, c)
        assertFalse("the old sprite was reused", a === d)
        assertTrue("the new colour is missing", Palette.RED in coloursOf(d))
    }

    /** 保存して読み直しても、決めた色が残ること。 */
    @Test
    fun `the chosen colours survive a save and restore`() {
        val c = CustomStyle()
        c[CustomStyle.Slot.OFFICE_WALL] = Palette.TREE
        c[CustomStyle.Slot.GLASS_LIT] = Palette.GOLD
        val saved = c.save()

        val other = CustomStyle()
        other.restore(saved)
        for (sl in CustomStyle.Slot.entries) assertEquals(sl.name, c[sl], other[sl])
    }

    /** 場所が増えても、古い保存を読めること。 */
    @Test
    fun `an older save with fewer slots still loads`() {
        val c = CustomStyle()
        c.restore(intArrayOf(Palette.GOLD))
        assertEquals(Palette.GOLD, c[CustomStyle.Slot.entries.first()])
        // 足りない分は初期の色のまま。
        val last = CustomStyle.Slot.entries.last()
        assertEquals(last.default, c[last])
    }
}
