package io.github.hatake716.pixelcity.ui

import io.github.hatake716.pixelcity.game.City
import io.github.hatake716.pixelcity.game.Traffic
import io.github.hatake716.pixelcity.game.TileKind

/**
 * 道路を走る車。
 *
 * 1台ずつの動きを本気で解くと、128×128 の街では数万台になって重い。
 * かわりに「見えている範囲の道に、交通量に見合った数の車を置き、
 * 決まった速さで流す」形にする。
 *
 * 大事なのは、**画面の見た目が交通量の計算と食い違わないこと**。
 *  - 交通量が多い道ほど、車が多い
 *  - 混んでいる道ほど、車が遅い（Greenshields のモデル）
 *  - 道の種類で、速さと台数が変わる
 *
 * こうすると、データマップの「こうつうりょう」で赤い道を見つけたとき、
 * その道を覗けば実際に車が詰まっている、という形になる。
 */
class TrafficAnimation {

    /**
     * 1台の車。
     *
     * 位置はタイル座標で持つ。[progress] は、いまいるタイルの
     * どこまで進んだか（0..1）。
     */
    class Car(
        val kind: Vehicles.Kind,
        var tx: Int,
        var ty: Int,
        var dir: Vehicles.Dir,
        var progress: Float,
    )

    private val cars = ArrayList<Car>()

    /** 何度たずねても同じ車が出るように、種を固定した乱数。 */
    private var seed = 12_345

    private fun rand(): Int {
        seed = seed * 1_103_515_245 + 12_345
        return (seed ushr 16) and 0x7FFF
    }

    /**
     * 見えている範囲に、車を配り直す。
     *
     * 画面の外の車は覚えておかない。街のどこかで車が走っていても、
     * 見えていなければ意味がないし、数万台を抱えると重い。
     */
    fun refill(
        city: City,
        minX: Int, minY: Int, maxX: Int, maxY: Int,
    ) {
        cars.clear()
        var placed = 0
        for (ty in minY..maxY) for (tx in minX..maxX) {
            if (placed >= MAX_CARS) return
            if (tx < 0 || ty < 0 || tx >= city.width || ty >= city.height) continue
            val t = city.tileAt(tx, ty)
            if (!t.kind.isRoad) continue

            // 交通量に見合った台数。v/c が 1 のとき、道いっぱいに並ぶ。
            val vc = t.volumeCapacityRatio
            if (vc <= 0f) continue
            // 切り捨てると、すいている道（v/c が 0.2 など）から
            // 車がまったく消えてしまう。端数は「その確率で1台」とみなす。
            val exact = vc * CARS_AT_CAPACITY
            var want = exact.toInt()
            val frac = exact - want
            if (frac > 0f && (rand() % 1000) < frac * 1000) want++
            want = want.coerceAtMost(MAX_PER_TILE)
            if (want <= 0) continue

            // その道が、どの向きに伸びているか
            val alongX = tx > 0 && city.tileAt(tx - 1, ty).kind.isRoad ||
                tx < city.width - 1 && city.tileAt(tx + 1, ty).kind.isRoad
            val alongY = ty > 0 && city.tileAt(tx, ty - 1).kind.isRoad ||
                ty < city.height - 1 && city.tileAt(tx, ty + 1).kind.isRoad

            for (k in 0 until want) {
                // 向きは、道の伸びている方向のどちらか
                val dir = when {
                    alongX && alongY -> Vehicles.Dir.entries[rand() % 4]
                    alongY -> if (rand() % 2 == 0) Vehicles.Dir.SOUTH else Vehicles.Dir.NORTH
                    else -> if (rand() % 2 == 0) Vehicles.Dir.EAST else Vehicles.Dir.WEST
                }
                val kind = kindFor(t.kind, rand())
                cars.add(Car(kind, tx, ty, dir, (rand() % 1000) / 1000f))
                placed++
                if (placed >= MAX_CARS) return
            }
        }
    }

    /**
     * 道の種類で、走る車の顔ぶれを変える。
     *
     * 高速道路にはトラックが多く、生活道路にはバスが来ない。
     */
    private fun kindFor(road: TileKind, r: Int): Vehicles.Kind {
        val cars = Vehicles.Kind.entries
        return when (road) {
            TileKind.HIGHWAY ->
                // 高速は長距離。トラックとバンが多い。
                when (r % 10) {
                    in 0..2 -> Vehicles.Kind.TRUCK
                    in 3..4 -> Vehicles.Kind.VAN
                    else -> cars[r % 4]
                }
            TileKind.AVENUE ->
                // 大通りはバスが通る。
                when (r % 10) {
                    0 -> Vehicles.Kind.BUS
                    1 -> Vehicles.Kind.TRUCK
                    2 -> Vehicles.Kind.VAN
                    else -> cars[r % 4]
                }
            else ->
                // 生活道路は乗用車ばかり。たまに配送のバン。
                if (r % 12 == 0) Vehicles.Kind.VAN else cars[r % 4]
        }
    }

    /**
     * [dt] 秒ぶん進める。
     *
     * 速さは Greenshields のモデルから出す。
     * 混んだ道ほど遅く、容量を超えると止まりかける。
     */
    fun advance(city: City, dt: Float) {
        for (c in cars) {
            if (c.tx < 0 || c.ty < 0 || c.tx >= city.width || c.ty >= city.height) continue
            val t = city.tileAt(c.tx, c.ty)
            if (!t.kind.isRoad) continue

            val free = Traffic.freeFlowSpeed(t.kind)
            val factor = Traffic.speedFactor(t.volumeCapacityRatio)
            c.progress += dt * free * factor * c.kind.speedScale

            // タイルを抜けたら、次のタイルへ
            while (c.progress >= 1f) {
                c.progress -= 1f
                val (nx, ny) = step(c.tx, c.ty, c.dir)
                if (nx < 0 || ny < 0 || nx >= city.width || ny >= city.height ||
                    !city.tileAt(nx, ny).kind.isRoad
                ) {
                    // 行き止まり。向きを変えて引き返す。
                    c.dir = opposite(c.dir)
                } else {
                    c.tx = nx
                    c.ty = ny
                    // 交差点では、たまに曲がる
                    if (rand() % 5 == 0) {
                        val turned = turn(c.dir, rand() % 2 == 0)
                        val (ax, ay) = step(c.tx, c.ty, turned)
                        if (ax in 0 until city.width && ay in 0 until city.height &&
                            city.tileAt(ax, ay).kind.isRoad
                        ) {
                            c.dir = turned
                        }
                    }
                }
            }
        }
    }

    private fun step(x: Int, y: Int, d: Vehicles.Dir): Pair<Int, Int> = when (d) {
        Vehicles.Dir.EAST -> (x + 1) to y
        Vehicles.Dir.WEST -> (x - 1) to y
        Vehicles.Dir.SOUTH -> x to (y + 1)
        Vehicles.Dir.NORTH -> x to (y - 1)
    }

    private fun opposite(d: Vehicles.Dir): Vehicles.Dir = when (d) {
        Vehicles.Dir.EAST -> Vehicles.Dir.WEST
        Vehicles.Dir.WEST -> Vehicles.Dir.EAST
        Vehicles.Dir.SOUTH -> Vehicles.Dir.NORTH
        Vehicles.Dir.NORTH -> Vehicles.Dir.SOUTH
    }

    private fun turn(d: Vehicles.Dir, left: Boolean): Vehicles.Dir = when (d) {
        Vehicles.Dir.EAST -> if (left) Vehicles.Dir.NORTH else Vehicles.Dir.SOUTH
        Vehicles.Dir.WEST -> if (left) Vehicles.Dir.SOUTH else Vehicles.Dir.NORTH
        Vehicles.Dir.SOUTH -> if (left) Vehicles.Dir.EAST else Vehicles.Dir.WEST
        Vehicles.Dir.NORTH -> if (left) Vehicles.Dir.WEST else Vehicles.Dir.EAST
    }

    /** いま走っている車。描くときに読む。 */
    val all: List<Car> get() = cars

    private companion object {
        /**
         * 容量いっぱいのとき、1タイルに並ぶ台数。
         * 多すぎると重なって潰れ、少なすぎると混雑が伝わらない。
         */
        const val CARS_AT_CAPACITY = 4f
        const val MAX_PER_TILE = 4
        /** 一度に動かす車の上限。これ以上はどうせ見分けがつかない。 */
        const val MAX_CARS = 420
    }
}
