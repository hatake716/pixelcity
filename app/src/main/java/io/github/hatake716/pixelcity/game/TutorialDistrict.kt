package io.github.hatake716.pixelcity.game

/** Optional, compact planning suggestions. These are guides, never free buildings or forced placements. */
object TutorialDistrict {
    fun suggestions(city: City, tutorial: Tutorial): List<Int> {
        val offsets: List<Pair<Int, Int>> = when (tutorial.neededTool) {
            TileKind.ROAD -> (-6..5).map { it to 0 }
            TileKind.POWER_COAL -> listOf(-6 to 1)
            TileKind.ZONE_R -> (-2..5).map { it to -1 }
            TileKind.WATER_TOWER -> listOf(0 to 1)
            TileKind.ZONE_C -> (1..4).map { it to 1 }
            TileKind.ZONE_I -> listOf(-5 to 1, -4 to 1)
            TileKind.PARK -> listOf(-1 to 1, -3 to 1)
            TileKind.CLINIC -> listOf(5 to 1)
            TileKind.LANDFILL -> listOf(-6 to -1, -5 to -1)
            else -> emptyList()
        }
        val proposed = offsets.mapNotNull { (dx, dy) ->
            val x = city.originX + dx; val y = city.originY + dy
            if (city.inBounds(x, y) && city.canBuildOn(x, y) && city.tileAt(x, y).kind == TileKind.EMPTY) city.index(x, y) else null
        }
        val kind = tutorial.neededTool ?: return emptyList()
        val offPlanStreet = kind == TileKind.ROAD && city.tiles.indices.any { i ->
            city.tiles[i].kind == TileKind.ROAD && (i / city.width != city.originY ||
                i % city.width !in (city.originX - 6)..(city.originX + 5))
        }
        val usable = if (kind == TileKind.ROAD && !offPlanStreet) proposed
            else proposed.filter { city.touchesRoad(it % city.width, it / city.width) }
        val remaining = tutorial.remaining().coerceAtLeast(1)
        if (!tutorial.inheritedCity && usable.size >= remaining) return usable
        // If the mayor chose a different street, guide that real street instead of pointing at empty grass.
        val fallback = city.tiles.indices.filter { i ->
            city.tiles[i].kind == TileKind.EMPTY && city.canBuildOn(i % city.width, i / city.width) && city.touchesRoad(i % city.width, i / city.width)
        }.sortedBy { i ->
            val tile = city.tiles[i]
            kotlin.math.abs(i % city.width - city.originX) + kotlin.math.abs(i / city.width - city.originY) +
                if (kind == TileKind.ZONE_R) tile.pollution * 2 else 0
        }
        return (usable + fallback).distinct().take(maxOf(24, remaining))
    }

    /** Repair is allowed; roads must extend a street rather than create unreachable islands. */
    fun placementBlocker(city: City, x: Int, y: Int, kind: TileKind, selected: Set<Int>): String? {
        if (kind == TileKind.EMPTY || kind == TileKind.POWER_LINE) return null
        fun joinsStreet(): Boolean = listOf(x-1 to y, x+1 to y, x to y-1, x to y+1).any { (nx, ny) ->
            city.inBounds(nx, ny) && (city.tileAt(nx, ny).kind.isLocalRoad || city.index(nx, ny) in selected)
        }
        if (kind == TileKind.ROAD) {
            val hasStreet = city.tiles.any { it.kind.isLocalRoad }
            if ((hasStreet || selected.isNotEmpty()) && !joinsStreet()) return "ひより：今ある道からつなげましょう"
        } else if (!city.touchesRoad(x,y)) return "ひより：道沿いの空き地に置きましょう"
        return null
    }
}
