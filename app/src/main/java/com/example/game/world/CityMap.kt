package com.example.game.world

import com.example.game.model.BuildingBlock
import com.example.game.model.PalmProp
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random

data class RoadLine(val pos: Float, val halfWidth: Float)

data class Vec2(val x: Float, val z: Float)

object CityMap {
  const val WORLD_MIN = -148f
  const val WORLD_MAX = 148f
  const val BEACH_START_X = 104f
  const val OCEAN_START_X = 122f

  const val ARMORY_X = 15f
  const val ARMORY_Z = 16f
  const val ARMORY_RADIUS = 3.8f

  const val CLINIC_X = -16f
  const val CLINIC_Z = -16f
  const val CLINIC_RADIUS = 3.6f

  val verticalRoads =
    listOf(
      RoadLine(-110f, 6.5f),
      RoadLine(-55f, 6.5f),
      RoadLine(0f, 7.0f),
      RoadLine(55f, 7.0f),
      RoadLine(92f, 6.5f),
    )

  val horizontalRoads =
    listOf(
      RoadLine(-110f, 6.5f),
      RoadLine(-55f, 6.5f),
      RoadLine(0f, 7.0f),
      RoadLine(55f, 6.5f),
      RoadLine(110f, 6.5f),
    )

  val buildings: List<BuildingBlock>
  val palms: List<PalmProp>
  val streetLampPositions: List<Vec2>
  val sidewalkWalkNodes: List<Vec2>
  val roadNodes: List<Vec2>

  init {
    val rng = Random(202606)
    val bList = mutableListOf<BuildingBlock>()
    val pList = mutableListOf<PalmProp>()
    val lamps = mutableListOf<Vec2>()

    var bId = 1
    // City block intervals between roads:
    // X blocks: [-98..-66], [-43..-12], [12..43], [67..81]
    // Z blocks: [-98..-66], [-43..-12], [12..43], [67..98]
    val xRanges = listOf(-98f to -66f, -43f to -12f, 12f to 43f, 67f to 81f)
    val zRanges = listOf(-98f to -66f, -43f to -12f, 12f to 43f, 67f to 98f)

    for ((xi, xr) in xRanges.withIndex()) {
      for ((zi, zr) in zRanges.withIndex()) {
        val bx1 = xr.first
        val bx2 = xr.second
        val bz1 = zr.first
        val bz2 = zr.second
        val cx = (bx1 + bx2) * 0.5f
        val cz = (bz1 + bz2) * 0.5f

        val district = districtAt(cx, cz)

        // Leave open plaza pads near Armory (12..43, 12..43 -> push building deeper)
        // and Clinic (-43..-12, -43..-12 -> push building deeper)
        if (xi == 2 && zi == 2) {
          // Armory block: Vice Armory building tucked back, open forecourt at (15, 16)
          bList.add(
            BuildingBlock(
              id = bId++,
              minX = 22f,
              maxX = 42f,
              minZ = 18f,
              maxZ = 40f,
              height = 10f,
              r = 0.22f,
              g = 0.20f,
              b = 0.32f,
              neonR = 0.0f,
              neonG = 0.94f,
              neonB = 1.0f,
              hasNeonBand = true,
              districtName = district,
            )
          )
          continue
        }

        if (xi == 1 && zi == 1) {
          // Safe House / Vice Clinic block
          bList.add(
            BuildingBlock(
              id = bId++,
              minX = -41f,
              maxX = -22f,
              minZ = -40f,
              maxZ = -18f,
              height = 12f,
              r = 0.85f,
              g = 0.84f,
              b = 0.90f,
              neonR = 1.0f,
              neonG = 0.20f,
              neonB = 0.45f,
              hasNeonBand = true,
              districtName = district,
            )
          )
          continue
        }

        // Subdivide large blocks into 2 buildings with an alley or park court
        val width = bx2 - bx1
        val depth = bz2 - bz1
        if (width > 24f && rng.nextFloat() < 0.7f) {
          val splitZ = cz + rng.nextFloat() * 4f - 2f
          bList.add(
            makeStyledBuilding(
              bId++,
              bx1 + 2f,
              bx2 - 2f,
              bz1 + 2f,
              splitZ - 2.5f,
              district,
              rng,
            )
          )
          bList.add(
            makeStyledBuilding(
              bId++,
              bx1 + 2f,
              bx2 - 2f,
              splitZ + 2.5f,
              bz2 - 2f,
              district,
              rng,
            )
          )
        } else {
          val pad = if (xi == 3) 1.2f else 2.2f
          bList.add(
            makeStyledBuilding(
              bId++,
              bx1 + pad,
              bx2 - pad,
              bz1 + pad,
              bz2 - pad,
              district,
              rng,
            )
          )
        }
      }
    }

    // Perimeter industrial / palms buildings beyond outer roads
    val outerBlocks =
      listOf(
        Triple(-142f to -122f, -95f to -55f, "Palms Estates"),
        Triple(-142f to -122f, -35f to 15f, "Industrial Yard"),
        Triple(-142f to -122f, 35f to 90f, "Cargo Docks"),
        Triple(-95f to -62f, -142f to -122f, "Palms Estates"),
        Triple(-38f to 10f, -142f to -122f, "Palms Estates"),
        Triple(24f to 75f, -142f to -122f, "North Strip"),
        Triple(-95f to -62f, 122f to 142f, "South Docks"),
        Triple(-35f to 20f, 122f to 142f, "South Docks"),
        Triple(35f to 82f, 122f to 142f, "Boardwalk South"),
      )
    for ((xr, zr, distName) in outerBlocks) {
      bList.add(makeStyledBuilding(bId++, xr.first, xr.second, zr.first, zr.second, distName, rng))
    }

    buildings = bList

    // Palm trees lining Ocean Beach boardwalk and Neon Strip median/sidewalks
    for (z in -125..125 step 16) {
      pList.add(PalmProp(102.5f, z.toFloat(), 7.8f + (abs(z) % 3) * 0.6f, 0.14f))
      if (abs(z) % 32 == 0) {
        pList.add(PalmProp(82.5f, z.toFloat() + 6f, 7.2f, -0.10f))
      }
    }
    // Palms Estates & Downtown plazas
    val extraPalms =
      listOf(
        Vec2(13f, 28f),
        Vec2(41f, 14f),
        Vec2(-14f, -34f),
        Vec2(-40f, -14f),
        Vec2(-64f, -64f),
        Vec2(46f, -64f),
        Vec2(46f, 64f),
        Vec2(-64f, 64f),
        Vec2(-118f, -45f),
        Vec2(-118f, 45f),
      )
    extraPalms.forEachIndexed { i, p ->
      pList.add(PalmProp(p.x, p.z, 7.4f + (i % 3) * 0.7f, if (i % 2 == 0) 0.12f else -0.12f))
    }
    palms = pList

    // Streetlamps at major avenue intersections
    for (vx in verticalRoads) {
      for (hz in horizontalRoads) {
        lamps.add(Vec2(vx.pos + vx.halfWidth + 1.1f, hz.pos + hz.halfWidth + 1.1f))
      }
    }
    streetLampPositions = lamps

    // Sidewalk nodes for pedestrian patrolling
    val sNodes = mutableListOf<Vec2>()
    for (vx in verticalRoads) {
      for (hz in horizontalRoads) {
        sNodes.add(Vec2(vx.pos - vx.halfWidth - 1.8f, hz.pos - hz.halfWidth - 1.8f))
        sNodes.add(Vec2(vx.pos + vx.halfWidth + 1.8f, hz.pos + hz.halfWidth + 1.8f))
      }
    }
    // Boardwalk promenade nodes
    for (z in -100..100 step 25) {
      sNodes.add(Vec2(101f, z.toFloat()))
    }
    sidewalkWalkNodes = sNodes

    val rNodes = mutableListOf<Vec2>()
    for (vx in verticalRoads) {
      for (hz in horizontalRoads) {
        rNodes.add(Vec2(vx.pos, hz.pos))
      }
    }
    roadNodes = rNodes
  }

  private fun makeStyledBuilding(
    id: Int,
    minX: Float,
    maxX: Float,
    minZ: Float,
    maxZ: Float,
    district: String,
    rng: Random,
  ): BuildingBlock {
    val (height, baseColor, neon) =
      when {
        district.contains("Downtown") -> {
          val h = 18f + rng.nextFloat() * 24f
          val shade = 0.18f + rng.nextFloat() * 0.16f
          val palette =
            listOf(
              Triple(0.0f, 0.94f, 1.0f),
              Triple(1.0f, 0.18f, 0.58f),
              Triple(0.45f, 0.55f, 1.0f),
            )
          Triple(h, Triple(shade, shade + 0.02f, shade + 0.08f), palette[id % palette.size])
        }
        district.contains("Neon") || district.contains("Ocean") || district.contains("Boardwalk") -> {
          val h = 9f + rng.nextFloat() * 11f
          val pastelChoices =
            listOf(
              Triple(0.95f, 0.72f, 0.82f),
              Triple(0.68f, 0.92f, 0.90f),
              Triple(0.96f, 0.85f, 0.65f),
              Triple(0.84f, 0.76f, 0.96f),
            )
          val neonChoices =
            listOf(
              Triple(1.0f, 0.18f, 0.58f),
              Triple(0.0f, 0.94f, 1.0f),
              Triple(1.0f, 0.72f, 0.19f),
            )
          Triple(h, pastelChoices[id % pastelChoices.size], neonChoices[(id + 1) % neonChoices.size])
        }
        else -> {
          val h = 7.5f + rng.nextFloat() * 8.5f
          val earthy =
            listOf(
              Triple(0.42f, 0.36f, 0.34f),
              Triple(0.32f, 0.38f, 0.42f),
              Triple(0.55f, 0.48f, 0.42f),
            )
          val neon = Triple(1.0f, 0.65f, 0.18f)
          Triple(h, earthy[id % earthy.size], neon)
        }
      }
    return BuildingBlock(
      id = id,
      minX = minX,
      maxX = maxX,
      minZ = minZ,
      maxZ = maxZ,
      height = height,
      r = baseColor.first,
      g = baseColor.second,
      b = baseColor.third,
      neonR = neon.first,
      neonG = neon.second,
      neonB = neon.third,
      hasNeonBand = true,
      districtName = district,
    )
  }

  fun districtAt(x: Float, z: Float): String {
    return when {
      x >= 96f -> "Ocean Beach"
      x >= 44f -> "Neon Vice Strip"
      x >= -65f && abs(z) <= 68f -> "Downtown Grid"
      z < -68f -> "Palms Estates"
      else -> "Industrial Palms"
    }
  }

  fun isOnRoad(x: Float, z: Float): Boolean {
    for (r in verticalRoads) {
      if (abs(x - r.pos) <= r.halfWidth) return true
    }
    for (r in horizontalRoads) {
      if (abs(z - r.pos) <= r.halfWidth) return true
    }
    return false
  }

  /**
   * Resolves circle vs world bounds, ocean surf, and building AABBs.
   * Returns adjusted (x, z, collided).
   */
  fun resolveCircleCollision(cx: Float, cz: Float, radius: Float): Triple<Float, Float, Boolean> {
    var x = cx.coerceIn(WORLD_MIN + radius, OCEAN_START_X - radius)
    var z = cz.coerceIn(WORLD_MIN + radius, WORLD_MAX - radius)
    var hit = (x != cx || z != cz)

    for (b in buildings) {
      val closestX = x.coerceIn(b.minX, b.maxX)
      val closestZ = z.coerceIn(b.minZ, b.maxZ)
      val dx = x - closestX
      val dz = z - closestZ
      val distSq = dx * dx + dz * dz
      if (distSq < radius * radius) {
        hit = true
        val dist = sqrt(distSq)
        if (dist > 0.0001f) {
          val push = radius - dist
          x += (dx / dist) * push
          z += (dz / dist) * push
        } else {
          // Inside AABB: push out along shallowest axis
          val leftPen = abs(x - b.minX)
          val rightPen = abs(b.maxX - x)
          val topPen = abs(z - b.minZ)
          val botPen = abs(b.maxZ - z)
          val m = min(min(leftPen, rightPen), min(topPen, botPen))
          when (m) {
            leftPen -> x = b.minX - radius
            rightPen -> x = b.maxX + radius
            topPen -> z = b.minZ - radius
            else -> z = b.maxZ + radius
          }
        }
      }
    }
    return Triple(x, z, hit)
  }

  fun raycastBuildings(ox: Float, oz: Float, dx: Float, dz: Float, maxDist: Float): Float {
    var best = maxDist
    for (b in buildings) {
      val t = rayAabb2D(ox, oz, dx, dz, b.minX, b.maxX, b.minZ, b.maxZ)
      if (t != null && t in 0f..best) {
        best = t
      }
    }
    return best
  }

  private fun rayAabb2D(
    ox: Float,
    oz: Float,
    dx: Float,
    dz: Float,
    minX: Float,
    maxX: Float,
    minZ: Float,
    maxZ: Float,
  ): Float? {
    var tMin = 0f
    var tMax = Float.MAX_VALUE

    if (abs(dx) < 1e-6f) {
      if (ox < minX || ox > maxX) return null
    } else {
      val invD = 1f / dx
      var t1 = (minX - ox) * invD
      var t2 = (maxX - ox) * invD
      if (t1 > t2) {
        val tmp = t1
        t1 = t2
        t2 = tmp
      }
      tMin = max(tMin, t1)
      tMax = min(tMax, t2)
      if (tMin > tMax) return null
    }

    if (abs(dz) < 1e-6f) {
      if (oz < minZ || oz > maxZ) return null
    } else {
      val invD = 1f / dz
      var t1 = (minZ - oz) * invD
      var t2 = (maxZ - oz) * invD
      if (t1 > t2) {
        val tmp = t1
        t1 = t2
        t2 = tmp
      }
      tMin = max(tMin, t1)
      tMax = min(tMax, t2)
      if (tMin > tMax) return null
    }
    return tMin
  }
}
