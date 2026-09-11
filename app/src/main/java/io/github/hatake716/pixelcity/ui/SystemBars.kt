package io.github.hatake716.pixelcity.ui

/**
 * 通知の欄・操作の欄の高さ。
 *
 * 絵は画面の端まで描くが、文字や釦はこの内側に置く。
 * そうしないと、時計や戻る釦と重なって読めない・押せない。
 *
 * 値は [MainActivity] が端末から受け取って入れる。
 * 画面ごとに読み取ると、まだ欄の高さが届いていないうちに
 * 描き始めてしまい、画面によって位置がずれる。
 */
object SystemBars {

    /** 端末の画素での高さ。 */
    var rawTop = 0
        private set
    var rawBottom = 0
        private set

    fun set(top: Int, bottom: Int) {
        rawTop = top
        rawBottom = bottom
    }

    /**
     * 論理ピクセルでの高さ。[scale] は論理1ドットが何画素か。
     *
     * 足りないより余るほうがよいので、切り上げる。
     */
    fun top(scale: Int): Int = if (scale <= 0) 0 else (rawTop + scale - 1) / scale

    fun bottom(scale: Int): Int = if (scale <= 0) 0 else (rawBottom + scale - 1) / scale
}
