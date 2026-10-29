package io.github.hatake716.pixelcity.ui

import io.github.hatake716.pixelcity.game.City
import io.github.hatake716.pixelcity.game.Terrain
import io.github.hatake716.pixelcity.game.TileKind
import kotlin.math.floor

/** Cosmetic street life. Stable street seeds keep walkers in place while panning.
 * Routes belong to connected pairs of pavement tiles, never to highways or water.
 * Residents stroll between two points and turn back; they are not commuting agents.
 */
object Pedestrians {
    private val spriteIds=Array(6) { outfit -> Array(4) { facing -> Array(2) { step -> "PERSON_${outfit}_${facing}_$step" } } }
    data class Pose(val x: Float, val y: Float, val outfit: Int, val facing: Int, val step: Int) {
        val spriteId get() = spriteIds[outfit][facing][step]
    }

    private fun pavement(city: City, x: Int, y: Int): Boolean {
        val t = city.tileOrNull(x, y) ?: return false
        return t.terrain != Terrain.WATER && (t.kind == TileKind.ROAD || t.kind == TileKind.AVENUE)
    }

    private fun lively(city: City, x: Int, y: Int): Boolean {
        for (dy in -1..1) for (dx in -1..1) {
            val t = city.tileOrNull(x + dx, y + dy) ?: continue
            if ((t.kind.isZone && t.stage > 0) ||
                (t.kind.isBuilding && !t.kind.isZone && t.kind != TileKind.POWER_LINE)) return true
        }
        return false
    }

    private fun lane(kind: TileKind) = if (kind == TileKind.AVENUE) .355f else .292f

    /** A pair is owned only by its western/northern tile, so no duplicate walkers. */
    fun onTile(city: City, x: Int, y: Int, seconds: Double, output: MutableList<Pose>) {
        if (!pavement(city, x, y) || !lively(city, x, y)) return
        for (axis in 0..1) {
            val nx = x + if (axis == 0) 1 else 0
            val ny = y + if (axis == 1) 1 else 0
            if (!pavement(city, nx, ny)) continue
            val seed = ((x * 73856093) xor (y * 19349663) xor (axis * 83492791)) and Int.MAX_VALUE
            if (seed % 3 == 0) continue
            for (side in 0..1) {
                val h = (seed ushr (side * 5))
                if (side == 1 && h % 3 == 0) continue
                val speed = .085 + h % 5 * .009
                // Bounded double precision elapsed time avoids uptime float jitter.
                val phase = ((seconds.coerceAtLeast(0.0) * speed + h % 1000 / 1000.0 * 2) % 2)
                val forward = phase < 1
                val along = (if (forward) phase else 2 - phase).toFloat()
                val distance = -.18f + along * 1.36f
                val a = lane(city.tileAt(x, y).kind)
                val b = lane(city.tileAt(nx, ny).kind)
                val offset = (a + (b - a) * distance.coerceIn(0f, 1f)) * if (side == 0) 1 else -1
                val facing = if (axis == 0) { if (forward) 0 else 2 } else { if (forward) 1 else 3 }
                output.add(Pose(x + if (axis == 0) distance else offset,
                    y + if (axis == 1) distance else offset, h % 6, facing,
                    (floor(seconds.coerceAtLeast(0.0) * 3.2 + h).toLong() and 1).toInt()))
            }
        }
    }
}
