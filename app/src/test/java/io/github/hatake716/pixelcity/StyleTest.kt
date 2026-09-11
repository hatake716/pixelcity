package io.github.hatake716.pixelcity

import io.github.hatake716.pixelcity.game.City
import io.github.hatake716.pixelcity.ui.IsoBuildings
import io.github.hatake716.pixelcity.ui.Palette
import io.github.hatake716.pixelcity.ui.Pix
import io.github.hatake716.pixelcity.ui.Sprite
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 街並みの様式。
 *
 * 「見た目が変わるだけ」の仕組みなので、
 *  - どの様式でも形は同じ（描き直していない）
 *  - どの様式も、ほかと見分けがつく
 * の2点を守る。
 */
class StyleTest {

    /** 街並みの見た目を代表する建物。 */
    private fun samples(): List<Pair<String, Sprite>> = listOf(
        "HOUSE_2" to IsoBuildings.HOUSE_2,
        "HOUSE_3" to IsoBuildings.HOUSE_3,
        "SHOP_3" to IsoBuildings.SHOP_3,
        "FACTORY_2" to IsoBuildings.FACTORY_2,
    )

    private fun coloursOf(s: Sprite): Set<Int> =
        s.data.filter { it != Pix.TRANSPARENT }.map { it.toInt() }.toSet()

    @Test
    fun `there are nine styles and each is named`() {
        assertEquals(9, City.Style.entries.size)
        for (st in City.Style.entries) {
            assertTrue("${st.name} has no label", st.label.isNotBlank())
            assertTrue("${st.name} has no detail", st.detail.isNotBlank())
        }
    }

    /** 色を変えても、形（どの画素が塗られているか）は変わらないこと。 */
    @Test
    fun `styling changes colour but never shape`() {
        for ((name, base) in samples()) {
            for (st in City.Style.entries) {
                val styled = IsoBuildings.styled(base, st)
                assertEquals("$name/$st width", base.width, styled.width)
                assertEquals("$name/$st height", base.height, styled.height)
                for (i in base.data.indices) {
                    val a = base.data[i] == Pix.TRANSPARENT
                    val b = styled.data[i] == Pix.TRANSPARENT
                    assertEquals("$name/$st shape differs at $i", a, b)
                }
            }
        }
    }

    /**
     * どの様式も、ほかと見分けがつくこと。
     *
     * 同じ色づかいの様式が混じっていると、選ぶ意味がなくなる。
     */
    @Test
    fun `every style looks different from the others`() {
        // 「じぶんで きめる」は、手をつけるまで標準と同じなので、ここでは比べない。
        val fixed = City.Style.entries.filter { it != City.Style.CUSTOM }
        val fingerprints = fixed.associateWith { st ->
            samples().flatMap { (_, sprite) -> coloursOf(IsoBuildings.styled(sprite, st)) }.toSet()
        }
        for (a in fixed) {
            for (b in fixed) {
                if (a >= b) continue
                assertTrue(
                    "$a and $b use the same colours",
                    fingerprints[a] != fingerprints[b],
                )
            }
        }
    }

    /** 様式ごとに、狙った色が実際に使われていること。 */
    @Test
    fun `each style uses the colours it is named for`() {
        fun usesIn(st: City.Style, colour: Int): Boolean =
            samples().any { (_, s) -> colour in coloursOf(IsoBuildings.styled(s, st)) }

        assertTrue("ancient has no mud brick", usesIn(City.Style.ANCIENT, Palette.ANCIENT_WALL))
        assertTrue("future does not glow", usesIn(City.Style.FUTURE, Palette.FUTURE_GLOW))
        assertTrue("europe has no terracotta", usesIn(City.Style.EUROPE, Palette.EURO_ROOF))
        assertTrue("japan has no grey tile", usesIn(City.Style.JAPAN, Palette.JP_ROOF))
        assertTrue("rural has no plaster", usesIn(City.Style.RURAL, Palette.RURAL_WALL))
        assertTrue("gritty has no sooty brick", usesIn(City.Style.GRITTY, Palette.GRIT_WALL))
        assertTrue("prime has no white stone", usesIn(City.Style.PRIME, Palette.PRIME_ROOF))
    }

    /** 標準はそのままの絵を返すこと（無駄に作り直さない）。 */
    @Test
    fun `the standard style returns the sprite unchanged`() {
        for ((_, s) in samples()) {
            assertTrue(IsoBuildings.styled(s, City.Style.STANDARD) === s)
        }
    }

    /** 同じ様式を二度求めても、作り直さないこと。 */
    @Test
    fun `styled sprites are cached`() {
        val a = IsoBuildings.styled(IsoBuildings.HOUSE_2, City.Style.ANCIENT)
        val b = IsoBuildings.styled(IsoBuildings.HOUSE_2, City.Style.ANCIENT)
        assertTrue(a === b)
    }
}
