package io.github.hatake716.pixelcity.ui

import androidx.core.graphics.Insets
import androidx.core.view.WindowInsetsCompat

/**
 * カメラ穴・通知欄・操作欄を避けるための余白。
 *
 * 絵は画面の端まで描くが、文字や釦はこの内側に置く。
 * そうしないと、時計や戻る釦と重なって読めない・押せない。
 *
 * 値は [MainActivity] が端末から受け取って入れる。
 * 画面ごとに読み取ると、まだ欄の高さが届いていないうちに
 * 描き始めてしまい、画面によって位置がずれる。
 */
object SystemBars {

    /** カメラ穴が通知欄より深い端末でも、画面全幅の操作領域をその下へ下げる。 */
    fun safeInsets(insets: WindowInsetsCompat, topClearance: Int): Insets {
        val safe = insets.getInsetsIgnoringVisibility(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
        )
        return Insets.of(safe.left, safe.top + if (safe.top > 0) topClearance else 0, safe.right, safe.bottom)
    }

    /** 端末の画素での高さ。 */
    var rawTop = 0
        private set
    var rawBottom = 0
        private set
    var rawLeft = 0
        private set
    var rawRight = 0
        private set

    fun set(top: Int, bottom: Int, left: Int = 0, right: Int = 0) {
        rawTop = top
        rawBottom = bottom
        rawLeft = left
        rawRight = right
    }

    /**
     * 論理ピクセルでの高さ。[scale] は論理1ドットが何画素か。
     *
     * 足りないより余るほうがよいので、切り上げる。
     */
    fun top(scale: Int): Int = if (scale <= 0) 0 else (rawTop + scale - 1) / scale

    fun bottom(scale: Int): Int = if (scale <= 0) 0 else (rawBottom + scale - 1) / scale
}
