package com.example.game.render

import android.opengl.GLES30
import com.example.game.world.CityMap
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.cos
import kotlin.math.sin

class VboMesh(vertices: FloatArray) {
  val vboId: Int
  val vertexCount: Int = vertices.size / FLOATS_PER_VERTEX

  init {
    val ids = IntArray(1)
    GLES30.glGenBuffers(1, ids, 0)
    vboId = ids[0]
    val buf =
      ByteBuffer.allocateDirect(vertices.size * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply {
          put(vertices)
          position(0)
        }
    GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vboId)
    GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, vertices.size * 4, buf, GLES30.GL_STATIC_DRAW)
    GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
  }

  fun draw(mode: Int = GLES30.GL_TRIANGLES) {
    GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vboId)
    val stride = FLOATS_PER_VERTEX * 4
    GLES30.glEnableVertexAttribArray(0)
    GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, stride, 0)
    GLES30.glEnableVertexAttribArray(1)
    GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, stride, 12)
    GLES30.glEnableVertexAttribArray(2)
    GLES30.glVertexAttribPointer(2, 4, GLES30.GL_FLOAT, false, stride, 24)
    GLES30.glEnableVertexAttribArray(3)
    GLES30.glVertexAttribPointer(3, 1, GLES30.GL_FLOAT, false, stride, 40)
    GLES30.glDrawArrays(mode, 0, vertexCount)
    GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
  }

  companion object {
    const val FLOATS_PER_VERTEX = 11 // pos(3), normal(3), color(4), emissive(1)
  }
}

class MeshBuilder(initialFloats: Int = 65536) {
  private var data = FloatArray(initialFloats)
  private var size = 0

  private fun ensure(extra: Int) {
    if (size + extra > data.size) {
      data = data.copyOf(maxOf(data.size * 2, size + extra))
    }
  }

  fun vertex(
    x: Float,
    y: Float,
    z: Float,
    nx: Float,
    ny: Float,
    nz: Float,
    r: Float,
    g: Float,
    b: Float,
    a: Float = 1f,
    emissive: Float = 0f,
  ) {
    ensure(11)
    data[size++] = x
    data[size++] = y
    data[size++] = z
    data[size++] = nx
    data[size++] = ny
    data[size++] = nz
    data[size++] = r
    data[size++] = g
    data[size++] = b
    data[size++] = a
    data[size++] = emissive
  }

  fun quad(
    x1: Float,
    y1: Float,
    z1: Float,
    x2: Float,
    y2: Float,
    z2: Float,
    x3: Float,
    y3: Float,
    z3: Float,
    x4: Float,
    y4: Float,
    z4: Float,
    nx: Float,
    ny: Float,
    nz: Float,
    r: Float,
    g: Float,
    b: Float,
    a: Float = 1f,
    emissive: Float = 0f,
  ) {
    vertex(x1, y1, z1, nx, ny, nz, r, g, b, a, emissive)
    vertex(x2, y2, z2, nx, ny, nz, r, g, b, a, emissive)
    vertex(x3, y3, z3, nx, ny, nz, r, g, b, a, emissive)
    vertex(x1, y1, z1, nx, ny, nz, r, g, b, a, emissive)
    vertex(x3, y3, z3, nx, ny, nz, r, g, b, a, emissive)
    vertex(x4, y4, z4, nx, ny, nz, r, g, b, a, emissive)
  }

  fun box(
    minX: Float,
    minY: Float,
    minZ: Float,
    maxX: Float,
    maxY: Float,
    maxZ: Float,
    r: Float,
    g: Float,
    b: Float,
    a: Float = 1f,
    emissive: Float = 0f,
    skipBottom: Boolean = false,
  ) {
    // Top (+Y)
    quad(minX, maxY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ, minX, maxY, minZ, 0f, 1f, 0f, r, g, b, a, emissive)
    // Bottom (-Y)
    if (!skipBottom) {
      quad(minX, minY, minZ, maxX, minY, minZ, maxX, minY, maxZ, minX, minY, maxZ, 0f, -1f, 0f, r * 0.7f, g * 0.7f, b * 0.7f, a, emissive)
    }
    // Front (+Z)
    quad(minX, minY, maxZ, maxX, minY, maxZ, maxX, maxY, maxZ, minX, maxY, maxZ, 0f, 0f, 1f, r * 0.94f, g * 0.94f, b * 0.94f, a, emissive)
    // Back (-Z)
    quad(maxX, minY, minZ, minX, minY, minZ, minX, maxY, minZ, maxX, maxY, minZ, 0f, 0f, -1f, r * 0.86f, g * 0.86f, b * 0.86f, a, emissive)
    // Right (+X)
    quad(maxX, minY, maxZ, maxX, minY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ, 1f, 0f, 0f, r * 0.90f, g * 0.90f, b * 0.90f, a, emissive)
    // Left (-X)
    quad(minX, minY, minZ, minX, minY, maxZ, minX, maxY, maxZ, minX, maxY, minZ, -1f, 0f, 0f, r * 0.82f, g * 0.82f, b * 0.82f, a, emissive)
  }

  fun floorDisc(
    cx: Float,
    y: Float,
    cz: Float,
    radius: Float,
    segments: Int,
    r: Float,
    g: Float,
    b: Float,
    a: Float = 1f,
    emissive: Float = 0f,
  ) {
    for (i in 0 until segments) {
      val a0 = (i.toFloat() / segments) * (2f * Math.PI.toFloat())
      val a1 = ((i + 1).toFloat() / segments) * (2f * Math.PI.toFloat())
      vertex(cx, y, cz, 0f, 1f, 0f, r, g, b, a, emissive)
      vertex(cx + cos(a1) * radius, y, cz + sin(a1) * radius, 0f, 1f, 0f, r, g, b, a, emissive)
      vertex(cx + cos(a0) * radius, y, cz + sin(a0) * radius, 0f, 1f, 0f, r, g, b, a, emissive)
    }
  }

  fun toArray(): FloatArray = data.copyOf(size)
}

object MeshFactory {
  fun buildUnitCube(): VboMesh {
    val mb = MeshBuilder(512)
    mb.box(-0.5f, -0.5f, -0.5f, 0.5f, 0.5f, 0.5f, 1f, 1f, 1f, 1f, 0f)
    return VboMesh(mb.toArray())
  }

  fun buildUnitDisc(): VboMesh {
    val mb = MeshBuilder(512)
    mb.floorDisc(0f, 0f, 0f, 1f, 14, 1f, 1f, 1f, 1f, 0f)
    return VboMesh(mb.toArray())
  }

  fun buildStaticCity(): VboMesh {
    val mb = MeshBuilder(160000)

    // 1. Main Urban Ground Base (-180..104)
    mb.quad(
      -180f, 0f, 180f,
      CityMap.BEACH_START_X, 0f, 180f,
      CityMap.BEACH_START_X, 0f, -180f,
      -180f, 0f, -180f,
      0f, 1f, 0f,
      0.16f, 0.15f, 0.22f,
    )

    // 2. Sandy Ocean Beach Strip (104..124)
    mb.quad(
      CityMap.BEACH_START_X, 0.01f, 180f,
      CityMap.OCEAN_START_X + 2f, 0.01f, 180f,
      CityMap.OCEAN_START_X + 2f, 0.01f, -180f,
      CityMap.BEACH_START_X, 0.01f, -180f,
      0f, 1f, 0f,
      0.86f, 0.74f, 0.55f,
    )

    // Boardwalk Promenade Strip along Ocean Beach (99..104)
    mb.quad(
      98.5f, 0.03f, 155f,
      CityMap.BEACH_START_X, 0.03f, 155f,
      CityMap.BEACH_START_X, 0.03f, -155f,
      98.5f, 0.03f, -155f,
      0f, 1f, 0f,
      0.42f, 0.36f, 0.44f,
    )

    // 3. Roads (Asphalt + Yellow Dashed Centerlines)
    for (vr in CityMap.verticalRoads) {
      mb.quad(
        vr.pos - vr.halfWidth, 0.02f, 155f,
        vr.pos + vr.halfWidth, 0.02f, 155f,
        vr.pos + vr.halfWidth, 0.02f, -155f,
        vr.pos - vr.halfWidth, 0.02f, -155f,
        0f, 1f, 0f,
        0.10f, 0.10f, 0.14f,
      )
      for (z in -144..140 step 10) {
        mb.quad(
          vr.pos - 0.18f, 0.035f, z + 4.5f,
          vr.pos + 0.18f, 0.035f, z + 4.5f,
          vr.pos + 0.18f, 0.035f, z.toFloat(),
          vr.pos - 0.18f, 0.035f, z.toFloat(),
          0f, 1f, 0f,
          0.98f, 0.78f, 0.22f,
          emissive = 0.25f,
        )
      }
    }

    for (hr in CityMap.horizontalRoads) {
      mb.quad(
        -155f, 0.025f, hr.pos + hr.halfWidth,
        100f, 0.025f, hr.pos + hr.halfWidth,
        100f, 0.025f, hr.pos - hr.halfWidth,
        -155f, 0.025f, hr.pos - hr.halfWidth,
        0f, 1f, 0f,
        0.10f, 0.10f, 0.14f,
      )
      for (x in -144..92 step 10) {
        mb.quad(
          x.toFloat(), 0.038f, hr.pos + 0.18f,
          x + 4.5f, 0.038f, hr.pos + 0.18f,
          x + 4.5f, 0.038f, hr.pos - 0.18f,
          x.toFloat(), 0.038f, hr.pos - 0.18f,
          0f, 1f, 0f,
          0.98f, 0.78f, 0.22f,
          emissive = 0.25f,
        )
      }
    }

    // 4. Armory Pad (Cyan/Gold glowing zone) & Clinic Pad (Magenta/Mint)
    mb.floorDisc(CityMap.ARMORY_X, 0.05f, CityMap.ARMORY_Z, CityMap.ARMORY_RADIUS, 20, 0.0f, 0.94f, 1.0f, 0.75f, emissive = 0.85f)
    mb.floorDisc(CityMap.ARMORY_X, 0.06f, CityMap.ARMORY_Z, CityMap.ARMORY_RADIUS * 0.55f, 16, 1.0f, 0.72f, 0.19f, 0.85f, emissive = 0.90f)

    mb.floorDisc(CityMap.CLINIC_X, 0.05f, CityMap.CLINIC_Z, CityMap.CLINIC_RADIUS, 20, 1.0f, 0.20f, 0.58f, 0.75f, emissive = 0.85f)
    mb.floorDisc(CityMap.CLINIC_X, 0.06f, CityMap.CLINIC_Z, CityMap.CLINIC_RADIUS * 0.55f, 16, 0.15f, 0.92f, 0.62f, 0.85f, emissive = 0.90f)

    // 5. City Buildings with Rooftop Trim & Neon Bands
    for (b in CityMap.buildings) {
      mb.box(
        b.minX, 0f, b.minZ,
        b.maxX, b.height, b.maxZ,
        b.r, b.g, b.b,
        skipBottom = true,
      )
      if (b.hasNeonBand) {
        // Glowing neon band near roofline
        val bandTop = b.height - 0.4f
        val bandBot = (b.height - 1.25f).coerceAtLeast(1.5f)
        mb.box(
          b.minX - 0.12f, bandBot, b.minZ - 0.12f,
          b.maxX + 0.12f, bandTop, b.maxZ + 0.12f,
          b.neonR, b.neonG, b.neonB,
          emissive = 0.95f,
          skipBottom = true,
        )
        // Lower storefront neon accent
        if (b.height > 8f) {
          mb.box(
            b.minX - 0.08f, 3.2f, b.minZ - 0.08f,
            b.maxX + 0.08f, 3.65f, b.maxZ + 0.08f,
            b.neonR, b.neonG, b.neonB,
            emissive = 0.72f,
            skipBottom = true,
          )
        }
      }
    }

    // 6. Palm Trees (Trunk + Tropical Canopy Layers)
    for (p in CityMap.palms) {
      val topX = p.x + p.leanAngle * p.height
      // Trunk segments
      mb.box(p.x - 0.24f, 0f, p.z - 0.24f, p.x + 0.24f, p.height * 0.5f, p.z + 0.24f, 0.45f, 0.30f, 0.20f, skipBottom = true)
      mb.box(topX - 0.18f, p.height * 0.48f, p.z - 0.18f, topX + 0.18f, p.height, p.z + 0.18f, 0.50f, 0.34f, 0.22f, skipBottom = true)
      // Crossed palm frond canopies
      mb.box(topX - 2.6f, p.height - 0.2f, p.z - 0.7f, topX + 2.6f, p.height + 0.35f, p.z + 0.7f, 0.16f, 0.68f, 0.34f)
      mb.box(topX - 0.7f, p.height - 0.2f, p.z - 2.6f, topX + 0.7f, p.height + 0.35f, p.z + 2.6f, 0.14f, 0.62f, 0.30f)
      mb.box(topX - 1.8f, p.height + 0.2f, p.z - 1.8f, topX + 1.8f, p.height + 0.65f, p.z + 1.8f, 0.20f, 0.76f, 0.38f)
    }

    // 7. Streetlamps
    for ((idx, lamp) in CityMap.streetLampPositions.withIndex()) {
      mb.box(lamp.x - 0.12f, 0f, lamp.z - 0.12f, lamp.x + 0.12f, 5.4f, lamp.z + 0.12f, 0.24f, 0.24f, 0.30f, skipBottom = true)
      val isCyan = idx % 2 == 0
      mb.box(
        lamp.x - 0.32f, 5.3f, lamp.z - 0.32f,
        lamp.x + 0.32f, 5.65f, lamp.z + 0.32f,
        if (isCyan) 0.2f else 1.0f,
        if (isCyan) 0.95f else 0.75f,
        if (isCyan) 1.0f else 0.35f,
        emissive = 1.0f,
      )
    }

    return VboMesh(mb.toArray())
  }
}
