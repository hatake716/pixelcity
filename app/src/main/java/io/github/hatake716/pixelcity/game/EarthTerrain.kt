package io.github.hatake716.pixelcity.game

import java.io.DataInputStream
import java.util.zip.GZIPInputStream
import kotlin.math.*

/** NASA Blue Marble / GEBCO derived data. See assets/earth/sources.json and tools/planet. */
object EarthTerrain {
    const val VERSION = 1
    const val EXAGGERATION = 65f
    private const val RADIUS_METERS = 6_371_000f
    private val heights = ShortArray(Planet.SIZE * Planet.SIZE)
    private val biomes = ByteArray(heights.size)
    private val colors = IntArray(heights.size)
    val startX: Int
    val startY: Int
    init {
        val source = requireNotNull(EarthTerrain::class.java.getResourceAsStream("/earth/terrain.bin.gz"))
        DataInputStream(GZIPInputStream(source).buffered()).use { stream ->
            require(stream.readInt() == 0x45525448 && stream.readInt() == VERSION && stream.readInt() == Planet.SIZE)
            startX = stream.readInt(); startY = stream.readInt()
            for (i in heights.indices) heights[i] = stream.readShort()
            stream.readFully(biomes)
            for (i in colors.indices) colors[i] = stream.readInt()
            require(stream.read() == -1)
        }
    }
    fun biome(index: Int) = PlanetTerrain.entries[biomes[index].toInt()]
    fun color(index: Int) = colors[index]
    fun meters(index: Int) = heights[index].toInt()
    fun index(longitudeDegrees: Double, latitudeDegrees: Double): Int {
        val x = floor((longitudeDegrees / 360 + .5) * Planet.SIZE).toInt()
        val y = floor((1 - sin(Math.toRadians(latitudeDegrees))) * Planet.SIZE / 2).toInt()
        return y.coerceIn(0, Planet.SIZE - 1) * Planet.SIZE + Math.floorMod(x, Planet.SIZE)
    }
    /** Edits use authored heights; unchanged cells retain the measured source relief. */
    fun relief(index: Int, terrain: PlanetTerrain): Float {
        val meters = if (terrain == biome(index)) meters(index).toFloat() else {
            val x = index % Planet.SIZE; val y = index / Planet.SIZE
            val ridge = abs(sin(x * 1.71f + y * 0.43f) * cos(y * 0.92f - x * 0.27f))
            when (terrain) {
                PlanetTerrain.PLAIN -> 80f
                PlanetTerrain.SEA -> -3500f - ridge * 2000f
                PlanetTerrain.RIVER -> -100f
                PlanetTerrain.MOUNTAIN -> 3200f + ridge * 1700f
                PlanetTerrain.RANGE -> 2900f + ridge * 3300f
                PlanetTerrain.VOLCANO -> 4000f + ridge * 1800f
                PlanetTerrain.FOREST -> 280f + ridge * 380f
                PlanetTerrain.RAINFOREST -> 180f + ridge * 260f
                PlanetTerrain.DESERT -> 180f + ridge * 800f
                PlanetTerrain.SNOW -> 300f + ridge * 350f
                PlanetTerrain.GLACIER -> 2100f + ridge * 1800f
            }
        }
        return meters / RADIUS_METERS * EXAGGERATION
    }
}
