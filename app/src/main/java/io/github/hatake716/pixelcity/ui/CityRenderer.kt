package io.github.hatake716.pixelcity.ui

import io.github.hatake716.pixelcity.game.City
import io.github.hatake716.pixelcity.game.Monument
import io.github.hatake716.pixelcity.game.Terrain
import io.github.hatake716.pixelcity.game.TileKind

/**
 * 都市をクォータービュー（斜め見下ろし）で描く。
 *
 * 描く順序が肝心で、
 *  1. すべての地面（菱形）を敷く
 *  2. 建物を**奥から手前へ**（tx+ty の小さい順に）重ねる
 * とすることで、手前の建物が奥の建物を正しく隠す。
 */
class CityRenderer {

    /** 地図に重ねて見る情報。 */
    enum class Overlay(val label: String) {
        NONE("なし"),
        POPULATION("じんこうみつど"),
        LAND_VALUE("ちか"),
        POLLUTION("こうがい"),
        CRIME("はんざい"),
        TRAFFIC("こうつうりょう"),
        POWER("でんりょく"),
        WATER("すいどう"),
        HEALTH("けんこう"),
        EDUCATION("きょういく"),
        FIRE_RISK("しょうぼう"),
    }

    /**
     * [city] を [canvas] の (0, [viewTop]) から高さ [viewHeight] の帯へ描く。
     * [camX]/[camY] は画面の中央に来るタイル座標（小数可）。
     */
    fun draw(
        canvas: PixelCanvas,
        city: City,
        camX: Float,
        camY: Float,
        /** 拡大率の分子と分母。2/1 なら2倍、1/2 なら半分。 */
        zoomNum: Int,
        zoomDen: Int,
        viewTop: Int,
        viewHeight: Int,
        overlay: Overlay = Overlay.NONE,
        highlight: IntArray? = null,
        suggest: ((Int, Int) -> Boolean)? = null,
        suggestOn: Boolean = false,
        /**
         * 建った直後の建物を動かすための進み具合 0f..1f。
         * 1つの月のあいだで 0 から 1 へ動かす。
         */
        animationPhase: Float = 1f,
        /** 下地を塗るか。呼び出し側が空などを描いてあるときは false にする。 */
        clearBackground: Boolean = true,
    ) {
        // 地面の外側は空の色。街が浮いて見えるようにする。
        if (clearBackground) {
            canvas.fillRect(0, viewTop, canvas.width, viewHeight, Palette.SKY)
        }

        // カメラの位置を画面の中央に置く
        val originX = canvas.width / 2 - Iso.screenX2(camX, camY).toInt() * zoomNum / zoomDen
        val originY = viewTop + viewHeight / 2 - Iso.screenY2(camX, camY).toInt() * zoomNum / zoomDen

        val clipTop = viewTop
        val clipBottom = viewTop + viewHeight

        // --- 1周目: 地面 ---
        forEachVisibleTile(city, canvas, originX, originY, zoomNum, zoomDen, clipTop, clipBottom) { tx, ty, sx, sy ->
            val tile = city.tileAt(tx, ty)
            // 道の種類ごとに、つながる向きを見て絵を選ぶ
            fun linked(kind: TileKind, dx: Int, dy: Int): Boolean =
                city.tileOrNull(tx + dx, ty + dy)?.kind == kind
            val ground = when {
                tile.kind == TileKind.AVENUE -> IsoTiles.avenueFor(
                    alongX = linked(TileKind.AVENUE, -1, 0) || linked(TileKind.AVENUE, 1, 0),
                    alongY = linked(TileKind.AVENUE, 0, -1) || linked(TileKind.AVENUE, 0, 1),
                )
                tile.kind == TileKind.HIGHWAY -> IsoTiles.highwayFor(
                    alongX = linked(TileKind.HIGHWAY, -1, 0) || linked(TileKind.HIGHWAY, 1, 0),
                    alongY = linked(TileKind.HIGHWAY, 0, -1) || linked(TileKind.HIGHWAY, 0, 1),
                )
                tile.kind == TileKind.SUBWAY -> IsoTiles.subwayFor(
                    alongX = linked(TileKind.SUBWAY, -1, 0) || linked(TileKind.SUBWAY, 1, 0),
                    alongY = linked(TileKind.SUBWAY, 0, -1) || linked(TileKind.SUBWAY, 0, 1),
                )
                tile.kind == TileKind.LANDFILL -> IsoTiles.GRASS
                tile.kind == TileKind.RAIL -> IsoTiles.railFor(
                    alongX = city.tileOrNull(tx - 1, ty)?.kind == TileKind.RAIL ||
                        city.tileOrNull(tx + 1, ty)?.kind == TileKind.RAIL,
                    alongY = city.tileOrNull(tx, ty - 1)?.kind == TileKind.RAIL ||
                        city.tileOrNull(tx, ty + 1)?.kind == TileKind.RAIL,
                )
                tile.kind == TileKind.FARM -> IsoTiles.FARM
                tile.kind == TileKind.ROAD -> IsoTiles.roadFor(
                    alongX = city.tileOrNull(tx - 1, ty)?.kind == TileKind.ROAD ||
                        city.tileOrNull(tx + 1, ty)?.kind == TileKind.ROAD,
                    alongY = city.tileOrNull(tx, ty - 1)?.kind == TileKind.ROAD ||
                        city.tileOrNull(tx, ty + 1)?.kind == TileKind.ROAD,
                )
                tile.kind.isZone && tile.stage == 0 -> when (tile.kind) {
                    TileKind.ZONE_R -> IsoTiles.ZONE_R_EMPTY
                    TileKind.ZONE_C -> IsoTiles.ZONE_C_EMPTY
                    else -> IsoTiles.ZONE_I_EMPTY
                }
                else -> IsoTiles.terrainTile(tile.terrain)
            }
            blit(canvas, ground, sx, sy, clipTop, clipBottom, zoomNum, zoomDen)
        }

        // --- 2周目: 情報の重ね表示（地面の上、建物の下） ---
        if (overlay != Overlay.NONE) {
            forEachVisibleTile(city, canvas, originX, originY, zoomNum, zoomDen, clipTop, clipBottom) { tx, ty, sx, sy ->
                val tile = city.tileAt(tx, ty)
                // 0（薄い）〜5（濃い）。値が大きいほど目立つ。
                val level = when (overlay) {
                    Overlay.NONE -> 0
                    Overlay.POPULATION ->
                        if (tile.kind == TileKind.ZONE_R) tile.stage * 2 else 0
                    Overlay.LAND_VALUE -> tile.landValue / 20
                    Overlay.POLLUTION -> tile.pollution / 20
                    Overlay.CRIME -> tile.crime / 20
                    Overlay.TRAFFIC ->
                        if (tile.kind.capacity <= 0) 0
                        else (tile.traffic * 5 / tile.kind.capacity.coerceAtLeast(1)).coerceAtMost(5)
                    Overlay.POWER -> if (tile.kind == TileKind.EMPTY) 0 else if (tile.powered) 1 else 5
                    Overlay.WATER -> if (tile.kind == TileKind.EMPTY) 0 else if (tile.watered) 1 else 5
                    Overlay.HEALTH -> tile.health / 20
                    Overlay.EDUCATION -> tile.education / 20
                    Overlay.FIRE_RISK -> tile.safety / 20
                }
                if (level > 0) {
                    tintDiamond(
                        canvas, sx, sy, zoomNum, zoomDen,
                        level.coerceIn(1, 5), overlay, clipTop, clipBottom,
                    )
                }
            }
        }

        // --- 3周目: 置ける場所の目印 ---
        if (suggest != null && suggestOn) {
            forEachVisibleTile(city, canvas, originX, originY, zoomNum, zoomDen, clipTop, clipBottom) { tx, ty, sx, sy ->
                if (suggest(tx, ty)) outlineDiamond(canvas, sx, sy, zoomNum, zoomDen, Palette.UI_ACCENT, clipTop, clipBottom)
            }
        }

        // --- 4周目: 建物。奥から手前へ ---
        // (tx+ty) が同じものは同じ奥行き。行ごとに描けば自然に前後が揃う。
        val maxDepth = city.width + city.height
        for (depth in 0 until maxDepth) {
            var tx = minOf(depth, city.width - 1)
            while (tx >= 0) {
                val ty = depth - tx
                if (ty >= city.height) { tx--; continue }
                val tile = city.tileAt(tx, ty)
                val sprite = buildingFor(city, tx, ty, tile)
                if (sprite != null) {
                    val sx = originX + Iso.screenX(tx, ty) * zoomNum / zoomDen
                    // 建物の下端が、そのタイルの菱形に重なるよう持ち上げる
                    var sy = originY + Iso.screenY(tx, ty) * zoomNum / zoomDen -
                        (sprite.height - Iso.TILE_H) * zoomNum / zoomDen

                    // 建ったばかりなら、地面から せり上がる形で見せる。
                    // 街が育つ様子が、目で分かるようになる。
                    val rising = tile.stageChangedMonth == city.month &&
                        tile.stage > tile.previousStage
                    if (rising && animationPhase < 1f) {
                        val hidden = ((sprite.height - Iso.TILE_H) *
                            (1f - animationPhase)).toInt() * zoomNum / zoomDen
                        sy += hidden
                        blitClipped(
                            canvas, sprite, sx, sy, clipTop, clipBottom, zoomNum, zoomDen,
                            skipTop = (sprite.height - Iso.TILE_H) - ((sprite.height - Iso.TILE_H) *
                                animationPhase).toInt(),
                        )
                    } else {
                        blit(canvas, sprite, sx, sy, clipTop, clipBottom, zoomNum, zoomDen)
                    }

                    if (needsPowerMark(tile)) {
                        val mx = sx + (Iso.TILE_W / 2 - 4) * zoomNum / zoomDen
                        val my = sy - 6 * zoomNum / zoomDen
                        blit(canvas, IsoBuildings.NO_POWER, mx, my, clipTop, clipBottom, zoomNum, zoomDen)
                    }
                }
                tx--
            }
        }

        // --- 5周目: 選択中の枠 ---
        if (highlight != null && highlight.size >= 2) {
            val span = if (highlight.size >= 3) highlight[2] else 1
            for (dy in 0 until span) for (dx in 0 until span) {
                val hx = originX + Iso.screenX(highlight[0] + dx, highlight[1] + dy) * zoomNum / zoomDen
                val hy = originY + Iso.screenY(highlight[0] + dx, highlight[1] + dy) * zoomNum / zoomDen
                outlineDiamond(canvas, hx, hy, zoomNum, zoomDen, Palette.WHITE, clipTop, clipBottom)
            }
        }
    }

    /** そのタイルに建つもの。地面だけのタイルは null。 */
    private fun buildingFor(
        city: City,
        tx: Int,
        ty: Int,
        tile: io.github.hatake716.pixelcity.game.Tile,
    ): Sprite? = when {
        // モニュメントは本体タイルでのみ描く（2×2 を1枚で覆う）
        tile.monument != null -> MonumentSprites.of(tile.monument!!)
        tile.kind == TileKind.MONUMENT -> null
        tile.kind.isZone -> if (tile.stage == 0) null else when (tile.kind) {
            TileKind.ZONE_R -> when (tile.stage) {
                1 -> IsoBuildings.HOUSE_1
                2 -> IsoBuildings.HOUSE_2
                else -> IsoBuildings.HOUSE_3
            }
            TileKind.ZONE_C -> when (tile.stage) {
                1 -> IsoBuildings.SHOP_1
                2 -> IsoBuildings.SHOP_2
                else -> IsoBuildings.SHOP_3
            }
            else -> when (tile.stage) {
                1 -> IsoBuildings.FACTORY_1
                2 -> IsoBuildings.FACTORY_2
                else -> IsoBuildings.FACTORY_3
            }
        }
        else -> when (tile.kind) {
            TileKind.POWER_COAL -> IsoBuildings.POWER_COAL
            TileKind.POWER_SOLAR -> IsoBuildings.POWER_SOLAR
            TileKind.POWER_WIND -> IsoBuildings.POWER_WIND
            TileKind.POWER_LINE -> IsoBuildings.POWER_LINE
            TileKind.CLINIC -> IsoBuildings.CLINIC
            TileKind.WATER_TOWER -> IsoBuildings.WATER_TOWER
            TileKind.WATER_PLANT -> IsoBuildings.WATER_PLANT
            TileKind.SEWAGE_PLANT -> IsoBuildings.SEWAGE_PLANT
            TileKind.LANDFILL -> IsoBuildings.LANDFILL
            TileKind.INCINERATOR -> IsoBuildings.INCINERATOR
            TileKind.RECYCLING -> IsoBuildings.RECYCLING
            TileKind.BUS_STOP -> IsoBuildings.BUS_STOP
            TileKind.SUBWAY_STATION -> IsoBuildings.SUBWAY_STATION
            TileKind.AIRPORT -> IsoBuildings.AIRPORT
            TileKind.SEAPORT -> IsoBuildings.SEAPORT
            TileKind.PARK -> IsoBuildings.PARK
            TileKind.POLICE -> IsoBuildings.POLICE
            TileKind.FIRE -> IsoBuildings.FIRE
            TileKind.SCHOOL -> IsoBuildings.SCHOOL
            TileKind.HOSPITAL -> IsoBuildings.HOSPITAL
            else -> null
        }
    }

    private fun needsPowerMark(tile: io.github.hatake716.pixelcity.game.Tile): Boolean = when {
        tile.kind.isZone -> tile.stage > 0 && !tile.powered
        tile.kind.isPowerPlant || tile.kind == TileKind.MONUMENT -> false
        tile.kind.isBuilding -> !tile.powered
        else -> false
    }

    /** 画面に入っているタイルだけを、奥から手前の順で回す。 */
    private inline fun forEachVisibleTile(
        city: City,
        canvas: PixelCanvas,
        originX: Int,
        originY: Int,
        zoomNum: Int,
        zoomDen: Int,
        clipTop: Int,
        clipBottom: Int,
        body: (tx: Int, ty: Int, sx: Int, sy: Int) -> Unit,
    ) {
        for (ty in 0 until city.height) for (tx in 0 until city.width) {
            val sx = originX + Iso.screenX(tx, ty) * zoomNum / zoomDen
            val sy = originY + Iso.screenY(tx, ty) * zoomNum / zoomDen
            if (sx + Iso.TILE_W * zoomNum / zoomDen < 0 || sx > canvas.width) continue
            if (sy + Iso.TILE_H * zoomNum / zoomDen < clipTop || sy > clipBottom) continue
            body(tx, ty, sx, sy)
        }
    }

    /**
     * 上から [skipTop] 行を描かずに転送する。
     * 地面から せり上がってくる様子を出すために使う。
     */
    private fun blitClipped(
        canvas: PixelCanvas,
        s: Sprite,
        x: Int,
        y: Int,
        clipTop: Int,
        clipBottom: Int,
        zoomNum: Int,
        zoomDen: Int,
        skipTop: Int,
    ) {
        val dw = s.width * zoomNum / zoomDen
        val dh = s.height * zoomNum / zoomDen
        for (dy in 0 until dh) {
            val sy = dy * zoomDen / zoomNum
            if (sy < skipTop) continue
            val ty = y + dy
            if (ty < clipTop || ty >= clipBottom || ty >= canvas.height) continue
            for (dx in 0 until dw) {
                val tx = x + dx
                if (tx < 0 || tx >= canvas.width) continue
                val v = s.at(dx * zoomDen / zoomNum, sy)
                if (v == Pix.TRANSPARENT) continue
                canvas.set(tx, ty, v.toInt())
            }
        }
    }

    /**
     * スプライトを [zoomNum]/[zoomDen] 倍で転送する。透明は飛ばす。
     * 縮小のときは最近傍で間引く（ドットの粒を保つため）。
     */
    private fun blit(
        canvas: PixelCanvas,
        s: Sprite,
        x: Int,
        y: Int,
        clipTop: Int,
        clipBottom: Int,
        zoomNum: Int,
        zoomDen: Int,
    ) {
        val dw = s.width * zoomNum / zoomDen
        val dh = s.height * zoomNum / zoomDen
        for (dy in 0 until dh) {
            val ty = y + dy
            if (ty < clipTop || ty >= clipBottom || ty >= canvas.height) continue
            val sy = dy * zoomDen / zoomNum
            for (dx in 0 until dw) {
                val tx = x + dx
                if (tx < 0 || tx >= canvas.width) continue
                val v = s.at(dx * zoomDen / zoomNum, sy)
                if (v == Pix.TRANSPARENT) continue
                canvas.set(tx, ty, v.toInt())
            }
        }
    }

    /** 菱形の内側を市松で暗くする。情報の重ね表示に使う。 */
    private fun tintDiamond(
        canvas: PixelCanvas, x: Int, y: Int, zoomNum: Int, zoomDen: Int, level: Int,
        overlay: Overlay, clipTop: Int, clipBottom: Int,
    ) {
        val w = Iso.TILE_W * zoomNum / zoomDen
        val h = Iso.TILE_H * zoomNum / zoomDen
        for (yy in 0 until h) {
            val ty = y + yy
            if (ty < clipTop || ty >= clipBottom || ty >= canvas.height) continue
            for (xx in 0 until w) {
                val tx = x + xx
                if (tx < 0 || tx >= canvas.width) continue
                val dx = (xx - w / 2f) / (w / 2f)
                val dy = (yy - h / 2f) / (h / 2f)
                if (Math.abs(dx) + Math.abs(dy) > 1f) continue
                // 濃さに応じた色で市松に塗る。地の色に足すのではなく、
                // 決まった色を置くことで、どの地面の上でも同じ見え方にする。
                if ((tx + ty) % 2 == 0) {
                    // 「多いほど良い」ものは青〜緑、「多いほど悪い」ものは黄〜赤。
                    val bad = overlay == Overlay.POLLUTION || overlay == Overlay.CRIME ||
                        overlay == Overlay.TRAFFIC || overlay == Overlay.POWER ||
                        overlay == Overlay.WATER
                    val c = if (bad) {
                        when {
                            level >= 5 -> Palette.RED_DARK
                            level >= 4 -> Palette.RED
                            level >= 3 -> Palette.GOLD
                            level >= 2 -> Palette.WINDOW_LIT
                            else -> Palette.WHITE
                        }
                    } else {
                        when {
                            level >= 5 -> Palette.SKY_DEEP
                            level >= 4 -> Palette.WATER
                            level >= 3 -> Palette.TREE
                            level >= 2 -> Palette.TREE_LIT
                            else -> Palette.WHITE
                        }
                    }
                    canvas.set(tx, ty, c)
                }
            }
        }
    }

    /** 菱形の縁をなぞる。 */
    private fun outlineDiamond(
        canvas: PixelCanvas, x: Int, y: Int, zoomNum: Int, zoomDen: Int, value: Int,
        clipTop: Int, clipBottom: Int,
    ) {
        val w = Iso.TILE_W * zoomNum / zoomDen
        val h = Iso.TILE_H * zoomNum / zoomDen
        for (yy in 0 until h) {
            val ty = y + yy
            if (ty < clipTop || ty >= clipBottom || ty >= canvas.height) continue
            for (xx in 0 until w) {
                val dx = (xx + 0.5f - w / 2f) / (w / 2f)
                val dy = (yy + 0.5f - h / 2f) / (h / 2f)
                val d = Math.abs(dx) + Math.abs(dy)
                if (d in 0.86f..1f) {
                    val tx = x + xx
                    if (tx in 0 until canvas.width) canvas.set(tx, ty, value)
                }
            }
        }
    }
}
