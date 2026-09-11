package io.github.hatake716.pixelcity

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 折り返しの規則。
 *
 * 実機で、禁則の例外が無制限に効いてしまい、句読点のある行が
 * 画面の外へはみ出していた。幅の上限を必ず守ることを確かめる。
 *
 * 文字の幅を測るには Android の Paint が要るので、ここでは
 * 「すべての文字が同じ幅」とみなす簡易な実装で規則だけを検証する。
 */
class TextWrapTest {

    /** GbText.wrap と同じ規則を、等幅で再現したもの。 */
    private fun wrap(text: String, maxWidth: Int, charW: Int = 10): List<String> {
        val noLineStart = "、。！？」）ー・"
        fun measure(s: String) = s.length * charW
        if (text.isEmpty()) return listOf("")
        val out = mutableListOf<String>()
        for (paragraph in text.split("\n")) {
            if (measure(paragraph) <= maxWidth) { out.add(paragraph); continue }
            val line = StringBuilder()
            val hardLimit = maxWidth + measure("。。")
            for (ch in paragraph) {
                val next = measure(line.toString() + ch)
                val overflow = next > maxWidth
                val keepWithPrevious = ch in noLineStart && next <= hardLimit
                if (overflow && line.isNotEmpty() && !keepWithPrevious) {
                    out.add(line.toString().trimEnd())
                    line.setLength(0)
                }
                line.append(ch)
            }
            if (line.isNotEmpty()) out.add(line.toString().trimEnd())
        }
        return out
    }

    @Test
    fun `no line exceeds the hard limit even with punctuation`() {
        val text = "きょうから あなたが このまちの しちょうです。もくひょうは まちの じんこうを ふやすこと。でも おかねが なくなると はさんします。"
        val maxWidth = 200
        val charW = 10
        val lines = wrap(text, maxWidth, charW)
        val hardLimit = maxWidth + 2 * charW
        for (l in lines) {
            assertTrue("'$l' is ${l.length * charW} wide, over $hardLimit", l.length * charW <= hardLimit)
        }
    }

    /** 句読点が行頭に来ないこと。 */
    @Test
    fun `punctuation does not start a line`() {
        val text = "あいうえお。かきくけこ、さしすせそ。たちつてと"
        val lines = wrap(text, 60)
        for (l in lines) {
            assertTrue("'$l' starts with punctuation", l.firstOrNull() !in listOf('。', '、'))
        }
    }

    @Test
    fun `short text is not wrapped`() {
        assertTrue(wrap("みじかい", 200).size == 1)
    }

    @Test
    fun `every character survives the wrap`() {
        val text = "まちには でんきが いります。「かりょく」を えらんで、どうろの ちかくに はつでんしょを 1つ たてましょう。"
        val joined = wrap(text, 180).joinToString("").replace(" ", "")
        assertTrue(joined == text.replace(" ", ""))
    }
}
