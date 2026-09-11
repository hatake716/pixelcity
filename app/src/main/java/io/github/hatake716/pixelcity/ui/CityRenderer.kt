package io.github.hatake716.pixelcity.ui

import io.github.hatake716.pixelcity.game.City
import io.github.hatake716.pixelcity.game.TileKind

/**
 * 都市をピクセルバッファへ描く。
 *
 * 画面に見えている範囲のタイルだけを描くので、マップを広げても描画量は増えない。
 */
class CityRenderer {

    /** 何を強調表示するか。 */
    enum class Overlay { NONE, LAND_VALUE, POLLUTION, POWER }

    /**
     * [city] を [canvas] へ描く。[camX]/[camY] は左上に来るタイル座標（小数可）。
     * [tileSize] は1タイルの論理ピクセル数。
     */
    fun draw(
        canvas: PixelCanvas,
        city: City,
        camX: Float,
        camY: Float,
        tileSize: Int,
        overlay: Overlay = Overlay.NONE,
        highlight: IntArray? = null,
    ) {
        canvas.clear(0)

        val originX = -((camX * tileSize).toInt())
        val originY = -((camY * tileSize).toInt())

        val firstX = maxOf(0, camX.toInt())
        val firstY = maxOf(0, camY.toInt())
        val lastX = minOf(city.width - 1, ((camX * tileSize + canvas.width) / tileSize).toInt() + 1)
        val lastY = minOf(city.height - 1, ((camY * tileSize + canvas.height) / tileSize).toInt() + 1)

        // 1周目: 地形と道路と区分
        for (ty in firstY..lastY) {
            for (tx in firstX..lastX) {
                val px = originX + tx * tileSize
                val py = originY + ty * tileSize
                val tile = city.tileAt(tx, ty)

                drawScaled(canvas, Sprites.forTerrain(tile.terrain), Sprites.SIZE, px, py, tileSize)

                when {
                    tile.kind == TileKind.ROAD -> {
                        val s = Sprites.roadFor(
                            left = city.tileOrNull(tx - 1, ty)?.kind == TileKind.ROAD,
                            right = city.tileOrNull(tx + 1, ty)?.kind == TileKind.ROAD,
                            up = city.tileOrNull(tx, ty - 1)?.kind == TileKind.ROAD,
                            down = city.tileOrNull(tx, ty + 1)?.kind == TileKind.ROAD,
                        )
                        drawScaled(canvas, s, Sprites.SIZE, px, py, tileSize)
                    }
                    tile.kind.isZone -> {
                        drawScaled(canvas, Sprites.forZone(tile.kind, tile.stage), Sprites.SIZE, px, py, tileSize, transparent = 0)
                    }
                    tile.kind == TileKind.MONUMENT -> {
                        // 本体タイルでのみ 16×16 を描く
                        val m = tile.monument
                        if (m != null) {
                            drawScaled(canvas, MonumentSprites.of(m), MonumentSprites.SIZE, px, py, tileSize * 2, transparent = 0)
                        }
                    }
                    else -> {
                        val s = Sprites.forBuilding(tile.kind)
                        if (s != null) drawScaled(canvas, s, Sprites.SIZE, px, py, tileSize, transparent = 0)
                    }
                }

                // 電気が来ていない区分・施設に印をつける
                if (tile.kind.isZone && tile.stage > 0 && !tile.powered ||
                    (tile.kind.isBuilding && !tile.kind.isPowerPlant && !tile.powered && tile.kind != TileKind.MONUMENT)
                ) {
                    drawScaled(canvas, Sprites.NO_POWER, Sprites.SIZE, px, py, tileSize, transparent = 0)
                }
            }
        }

        // 2周目: 情報の重ね表示
        if (overlay != Overlay.NONE) {
            for (ty in firstY..lastY) for (tx in firstX..lastX) {
                val px = originX + tx * tileSize
                val py = originY + ty * tileSize
                val tile = city.tileAt(tx, ty)
                val level = when (overlay) {
                    Overlay.LAND_VALUE -> tile.landValue / 16
                    Overlay.POLLUTION -> tile.pollution / 25
                    Overlay.POWER -> if (tile.powered) 0 else 3
                    Overlay.NONE -> 0
                }
                if (level > 0) canvas.ditherRect(px, py, tileSize, tileSize, level.coerceIn(1, 3))
            }
        }

        // 3周目: 選択中の枠
        if (highlight != null && highlight.size >= 2) {
            val hx = originX + highlight[0] * tileSize
            val hy = originY + highlight[1] * tileSize
            val span = if (highlight.size >= 3) highlight[2] else 1
            canvas.drawRect(hx, hy, tileSize * span, tileSize * span, 3)
            canvas.drawRect(hx + 1, hy + 1, tileSize * span - 2, tileSize * span - 2, 0)
        }
    }

    /**
     * スプライトを [dstSize] の大きさで描く。等倍・整数倍・縮小に対応する。
     * 最近傍でドットを保つ。
     */
    private fun drawScaled(
        canvas: PixelCanvas,
        sprite: ByteArray,
        spriteSize: Int,
        x: Int,
        y: Int,
        dstSize: Int,
        transparent: Int = -1,
    ) {
        if (dstSize == spriteSize) {
            canvas.blit(sprite, spriteSize, x, y, transparent)
            return
        }
        // 画面外は描かない
        if (x + dstSize <= 0 || y + dstSize <= 0 || x >= canvas.width || y >= canvas.height) return
        for (dy in 0 until dstSize) {
            val ty = y + dy
            if (ty < 0 || ty >= canvas.height) continue
            val sy = dy * spriteSize / dstSize
            for (dx in 0 until dstSize) {
                val tx = x + dx
                if (tx < 0 || tx >= canvas.width) continue
                val v = sprite[sy * spriteSize + dx * spriteSize / dstSize].toInt()
                if (v == transparent) continue
                canvas.set(tx, ty, v)
            }
        }
    }
}
