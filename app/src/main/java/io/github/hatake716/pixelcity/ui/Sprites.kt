package io.github.hatake716.pixelcity.ui

import io.github.hatake716.pixelcity.game.Monument
import io.github.hatake716.pixelcity.game.Terrain
import io.github.hatake716.pixelcity.game.TileKind

/**
 * 8×8 のドット絵。文字列で書き、`.`=0（最明） `-`=1 `+`=2 `#`=3（最暗）で表す。
 *
 * 外部の画像素材を持たず、すべてこのファイルのコードで描いている。
 * ライセンス上の制約がなく、パレットも4階調に収まる。
 */
object Sprites {
    const val SIZE = 8

    private fun parse(rows: List<String>): ByteArray {
        require(rows.size == SIZE) { "sprite must have $SIZE rows, got ${rows.size}" }
        val out = ByteArray(SIZE * SIZE)
        rows.forEachIndexed { y, row ->
            require(row.length == SIZE) { "row $y must be $SIZE wide, got '${row}'" }
            row.forEachIndexed { x, ch ->
                out[y * SIZE + x] = when (ch) {
                    '.' -> 0
                    '-' -> 1
                    '+' -> 2
                    '#' -> 3
                    else -> throw IllegalArgumentException("bad pixel '$ch'")
                }
            }
        }
        return out
    }

    private fun sprite(vararg rows: String): ByteArray = parse(rows.toList())

    // --- 地形 ---

    val GRASS = sprite(
        "........",
        "..-.....",
        "........",
        ".....-..",
        "..-.....",
        "........",
        "......-.",
        "........",
    )

    /** 水面。横縞で流れを表す。 */
    val WATER = sprite(
        "++++++++",
        "+##++##+",
        "++++++++",
        "##++##++",
        "++++++++",
        "+##++##+",
        "++++++++",
        "##++##++",
    )

    val SHORE = sprite(
        "........",
        "........",
        "..-..-..",
        "-.-.-.-.",
        "-+-+-+-+",
        "++++++++",
        "+#+#+#+#",
        "########",
    )

    // --- 道路 ---

    /** 十字路。接続に応じて [roadFor] が選ぶ。 */
    val ROAD_CROSS = sprite(
        "##....##",
        "##....##",
        "........",
        "...--...",
        "...--...",
        "........",
        "##....##",
        "##....##",
    )

    val ROAD_H = sprite(
        "########",
        "........",
        "........",
        "--..--..",
        "--..--..",
        "........",
        "........",
        "########",
    )

    val ROAD_V = sprite(
        "#......#",
        "#..--..#",
        "#..--..#",
        "#......#",
        "#......#",
        "#..--..#",
        "#..--..#",
        "#......#",
    )

    // --- 区分: 住宅（段階1..3） ---

    val ZONE_R_EMPTY = sprite(
        "--------",
        "-......-",
        "-.-..-.-",
        "-......-",
        "-......-",
        "-.-..-.-",
        "-......-",
        "--------",
    )

    val ZONE_R1 = sprite(
        "........",
        "...##...",
        "..####..",
        ".######.",
        "..#..#..",
        "..#++#..",
        "..#++#..",
        "..####..",
    )

    val ZONE_R2 = sprite(
        "..####..",
        ".######.",
        "########",
        "#+#++#+#",
        "#++++++#",
        "#+#++#+#",
        "#++++++#",
        "########",
    )

    val ZONE_R3 = sprite(
        ".######.",
        "########",
        "#+#++#+#",
        "#++++++#",
        "##+##+##",
        "#++++++#",
        "#+#++#+#",
        "########",
    )

    // --- 区分: 商業 ---

    val ZONE_C_EMPTY = sprite(
        "--------",
        "-..--..-",
        "-......-",
        "--......",
        "......--",
        "-......-",
        "-..--..-",
        "--------",
    )

    val ZONE_C1 = sprite(
        "........",
        "..####..",
        ".#++++#.",
        ".#+##+#.",
        ".#++++#.",
        ".#+##+#.",
        ".#++++#.",
        ".######.",
    )

    val ZONE_C2 = sprite(
        "..####..",
        ".##++##.",
        "#++##++#",
        "#+#++#+#",
        "#++##++#",
        "#+#++#+#",
        "#++##++#",
        "########",
    )

    val ZONE_C3 = sprite(
        "#.####.#",
        "###++###",
        "#++##++#",
        "##+##+##",
        "#++##++#",
        "##+##+##",
        "#++##++#",
        "########",
    )

    // --- 区分: 工業 ---

    val ZONE_I_EMPTY = sprite(
        "--------",
        "-.-.-.-.",
        "-.......",
        "-.-.-.-.",
        "-.......",
        "-.-.-.-.",
        "-.......",
        "--------",
    )

    val ZONE_I1 = sprite(
        "........",
        "..+.....",
        ".####...",
        ".#++#...",
        ".####.#.",
        ".#++#.#.",
        ".####+#.",
        "########",
    )

    val ZONE_I2 = sprite(
        "..+..+..",
        ".##..##.",
        ".##..##.",
        "########",
        "#++##++#",
        "#+####+#",
        "#++##++#",
        "########",
    )

    val ZONE_I3 = sprite(
        "+.+..+.+",
        "###..###",
        "###..###",
        "########",
        "#+#++#+#",
        "#++##++#",
        "#+#++#+#",
        "########",
    )

    // --- 施設 ---

    /** 火力発電所。煙突から煙。 */
    val POWER_COAL = sprite(
        "..#..#..",
        ".##..##.",
        ".##..##.",
        "########",
        "#+#++#+#",
        "#+####+#",
        "#++++++#",
        "########",
    )

    /** 太陽光発電。パネルの格子。 */
    val POWER_SOLAR = sprite(
        "........",
        ".######.",
        ".#+#+#+#",
        ".#+#+#+#",
        ".######.",
        "...##...",
        "..####..",
        ".######.",
    )

    val PARK = sprite(
        "........",
        "...##...",
        "..####..",
        ".######.",
        "..####..",
        "...##...",
        "...##...",
        "..-..-..",
    )

    /** 警察署。 */
    val POLICE = sprite(
        "........",
        "...##...",
        "..####..",
        "########",
        "#++++++#",
        "#+#++#+#",
        "#++++++#",
        "########",
    )

    /** 消防署。 */
    val FIRE = sprite(
        "........",
        ".#....#.",
        ".######.",
        ".#++++#.",
        ".#+##+#.",
        ".#++++#.",
        ".######.",
        ".#+##+#.",
    )

    /** 学校。 */
    val SCHOOL = sprite(
        "........",
        "...#....",
        "..###...",
        ".#####..",
        "########",
        "#+#++#+#",
        "#++++++#",
        "########",
    )

    /** 病院。十字。 */
    val HOSPITAL = sprite(
        "........",
        ".######.",
        ".#+##+#.",
        ".##..##.",
        ".#....#.",
        ".##..##.",
        ".#+##+#.",
        ".######.",
    )

    /** 電力不足のときに重ねる印。 */
    val NO_POWER = sprite(
        "........",
        "...##...",
        "..#..#..",
        "..#..#..",
        "...##...",
        "...##...",
        "........",
        "...##...",
    )

    fun forTerrain(terrain: Terrain): ByteArray = when (terrain) {
        Terrain.WATER -> WATER
        Terrain.SHORE -> SHORE
        Terrain.LAND -> GRASS
    }

    /** 区分の段階に応じたドット絵。 */
    fun forZone(kind: TileKind, stage: Int): ByteArray = when (kind) {
        TileKind.ZONE_R -> when (stage) {
            0 -> ZONE_R_EMPTY
            1 -> ZONE_R1
            2 -> ZONE_R2
            else -> ZONE_R3
        }
        TileKind.ZONE_C -> when (stage) {
            0 -> ZONE_C_EMPTY
            1 -> ZONE_C1
            2 -> ZONE_C2
            else -> ZONE_C3
        }
        else -> when (stage) {
            0 -> ZONE_I_EMPTY
            1 -> ZONE_I1
            2 -> ZONE_I2
            else -> ZONE_I3
        }
    }

    fun forBuilding(kind: TileKind): ByteArray? = when (kind) {
        TileKind.POWER_COAL -> POWER_COAL
        TileKind.POWER_SOLAR -> POWER_SOLAR
        TileKind.PARK -> PARK
        TileKind.POLICE -> POLICE
        TileKind.FIRE -> FIRE
        TileKind.SCHOOL -> SCHOOL
        TileKind.HOSPITAL -> HOSPITAL
        else -> null
    }

    /** 隣接する道路の有無から、十字・横・縦を選ぶ。 */
    fun roadFor(left: Boolean, right: Boolean, up: Boolean, down: Boolean): ByteArray {
        val horizontal = left || right
        val vertical = up || down
        return when {
            horizontal && vertical -> ROAD_CROSS
            vertical -> ROAD_V
            else -> ROAD_H
        }
    }
}
