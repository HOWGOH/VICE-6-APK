package com.example.game.render

import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import com.example.game.engine.RenderSnapshot
import com.example.game.engine.ViceGameEngine
import com.example.game.model.DriverRole
import com.example.game.model.NpcBehavior
import com.example.game.model.NpcEntity
import com.example.game.model.NpcRole
import com.example.game.model.VehicleEntity
import com.example.game.model.VehicleType
import com.example.game.model.WeaponType
import com.example.game.world.CityMap
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class ViceGLRenderer(
  private val engine: ViceGameEngine,
  private val onFrameHudTick: () -> Unit,
) : GLSurfaceView.Renderer {

  private lateinit var worldShader: ShaderProgram
  private lateinit var skyShader: ShaderProgram
  private lateinit var cityMesh: VboMesh
  private lateinit var unitCube: VboMesh
  private lateinit var unitDisc: VboMesh
  private var skyVboId = 0

  private val projMatrix = FloatArray(16)
  private val viewMatrix = FloatArray(16)
  private val vpMatrix = FloatArray(16)
  private val modelMatrix = FloatArray(16)
  private val mvpMatrix = FloatArray(16)

  private var uMvpLoc = 0
  private var uModelLoc = 0
  private var uTintLoc = 0
  private var uExtraEmissiveLoc = 0
  private var uCamPosLoc = 0
  private var uSunDirLoc = 0
  private var uFogColorLoc = 0
  private var uFogStartLoc = 0
  private var uFogEndLoc = 0

  private var lastNanoTime = 0L
  private var hudAccumSec = 0f

  private var smoothCamX = 76f
  private var smoothCamY = 4.5f
  private var smoothCamZ = 0f
  private var camInitialized = false

  override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
    GLES30.glEnable(GLES30.GL_DEPTH_TEST)
    GLES30.glEnable(GLES30.GL_BLEND)
    GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
    GLES30.glClearColor(0.08f, 0.05f, 0.14f, 1f)

    worldShader = ShaderProgram(ShaderProgram.WORLD_VERTEX_SHADER, ShaderProgram.WORLD_FRAGMENT_SHADER)
    skyShader = ShaderProgram(ShaderProgram.SKY_VERTEX_SHADER, ShaderProgram.SKY_FRAGMENT_SHADER)

    uMvpLoc = worldShader.uniform("uMVP")
    uModelLoc = worldShader.uniform("uModel")
    uTintLoc = worldShader.uniform("uTint")
    uExtraEmissiveLoc = worldShader.uniform("uExtraEmissive")
    uCamPosLoc = worldShader.uniform("uCameraPos")
    uSunDirLoc = worldShader.uniform("uSunDir")
    uFogColorLoc = worldShader.uniform("uFogColor")
    uFogStartLoc = worldShader.uniform("uFogStart")
    uFogEndLoc = worldShader.uniform("uFogEnd")

    cityMesh = MeshFactory.buildStaticCity()
    unitCube = MeshFactory.buildUnitCube()
    unitDisc = MeshFactory.buildUnitDisc()

    val skyVerts = floatArrayOf(-1f, -1f, 1f, -1f, 1f, 1f, -1f, -1f, 1f, 1f, -1f, 1f)
    val ids = IntArray(1)
    GLES30.glGenBuffers(1, ids, 0)
    skyVboId = ids[0]
    val buf =
      ByteBuffer.allocateDirect(skyVerts.size * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply {
          put(skyVerts)
          position(0)
        }
    GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, skyVboId)
    GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, skyVerts.size * 4, buf, GLES30.GL_STATIC_DRAW)
    GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)

    lastNanoTime = System.nanoTime()
  }

  override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
    GLES30.glViewport(0, 0, width, height)
    val aspect = width.toFloat() / height.coerceAtLeast(1).toFloat()
    Matrix.perspectiveM(projMatrix, 0, 58f, aspect, 0.4f, 340f)
  }

  override fun onDrawFrame(gl: GL10?) {
    val now = System.nanoTime()
    val dt = if (lastNanoTime == 0L) 0.016f else ((now - lastNanoTime) / 1_000_000_000f)
    lastNanoTime = now

    engine.step(dt)
    hudAccumSec += dt
    if (hudAccumSec >= 0.05f) {
      hudAccumSec = 0f
      onFrameHudTick()
    }

    val snap = engine.getRenderSnapshot()

    GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
    drawSkyBackdrop()

    // Setup 3rd-person follow camera
    val inCar = snap.playerInVehicleId != null
    val activeCar =
      if (inCar) snap.vehicles.firstOrNull { it.id == snap.playerInVehicleId } else null
    val camYaw = if (activeCar != null) activeCar.heading else snap.cameraYaw
    val camPitch = if (inCar) 0.30f else snap.cameraPitch
    val camDist =
      if (activeCar != null) 9.8f + abs(activeCar.speed) * 0.09f else 6.6f

    val targetX = snap.playerX
    val targetY = if (inCar) 1.35f else 1.45f
    val targetZ = snap.playerZ

    val desiredEyeX = targetX - sin(camYaw) * cos(camPitch) * camDist
    val desiredEyeY = targetY + sin(camPitch) * camDist
    val desiredEyeZ = targetZ - cos(camYaw) * cos(camPitch) * camDist

    if (!camInitialized) {
      smoothCamX = desiredEyeX
      smoothCamY = desiredEyeY
      smoothCamZ = desiredEyeZ
      camInitialized = true
    } else {
      val lerp = (dt * 10f).coerceIn(0.1f, 0.85f)
      smoothCamX += (desiredEyeX - smoothCamX) * lerp
      smoothCamY += (desiredEyeY - smoothCamY) * lerp
      smoothCamZ += (desiredEyeZ - smoothCamZ) * lerp
    }

    val shakeX =
      if (snap.cameraShake > 0.01f) sin(snap.timeSec * 55f) * snap.cameraShake * 0.35f else 0f
    val shakeY =
      if (snap.cameraShake > 0.01f) cos(snap.timeSec * 48f) * snap.cameraShake * 0.25f else 0f

    Matrix.setLookAtM(
      viewMatrix,
      0,
      smoothCamX + shakeX,
      smoothCamY.coerceAtLeast(1.4f) + shakeY,
      smoothCamZ,
      targetX,
      targetY,
      targetZ,
      0f,
      1f,
      0f,
    )
    Matrix.multiplyMM(vpMatrix, 0, projMatrix, 0, viewMatrix, 0)

    worldShader.use()
    GLES30.glUniform3f(uCamPosLoc, smoothCamX, smoothCamY, smoothCamZ)
    GLES30.glUniform3f(uSunDirLoc, 0.55f, 0.72f, 0.42f)
    GLES30.glUniform3f(uFogColorLoc, 0.24f, 0.10f, 0.30f)
    GLES30.glUniform1f(uFogStartLoc, 68f)
    GLES30.glUniform1f(uFogEndLoc, 220f)

    // 1. Static City Mesh
    Matrix.setIdentityM(modelMatrix, 0)
    bindModelAndTint(1f, 1f, 1f, 1f, 0f)
    cityMesh.draw()

    // 2. Animated Turquoise Ocean Water
    val waveGlow = 0.20f + 0.10f * sin(snap.timeSec * 2.2f)
    drawBox(168f, -0.10f, 0f, 92f, 0.22f, 360f, 0f, 0.06f, 0.68f, 0.80f, 0.92f, waveGlow)

    // 3. Persistent Blood Pool Decals
    for (d in snap.bloodDecals) {
      Matrix.setIdentityM(modelMatrix, 0)
      Matrix.translateM(modelMatrix, 0, d.x, 0.045f, d.z)
      Matrix.scaleM(modelMatrix, 0, d.radius, 1f, d.radius)
      bindModelAndTint(0.56f, 0.02f, 0.05f, d.alpha, 0.05f)
      unitDisc.draw()
    }

    // 4. Spinning 3D Cash Pickups
    for (c in snap.cashPickups) {
      val bob = 0.55f + 0.16f * sin(c.bobPhase)
      val rotDeg = (c.bobPhase * 57.3f) % 360f
      drawBox(c.x, bob, c.z, 0.58f, 0.24f, 0.34f, rotDeg, 0.12f, 0.90f, 0.42f, 1f, 0.75f)
      drawBox(c.x, bob, c.z, 0.16f, 0.26f, 0.36f, rotDeg, 1.0f, 0.82f, 0.24f, 1f, 0.85f)
    }

    // 5. GPS Waypoint Beacon
    if (snap.waypointX != null && snap.waypointZ != null) {
      val pulse = 0.65f + 0.35f * sin(snap.timeSec * 5f)
      drawBox(snap.waypointX, 16f, snap.waypointZ, 0.65f, 32f, 0.65f, snap.timeSec * 45f, 0.0f, 0.94f, 1.0f, 0.48f * pulse, 1.0f)
    }

    // 6. Vehicles
    for (v in snap.vehicles) {
      drawVehicle(v, snap.timeSec)
    }

    // 7. NPCs & Cops & SWAT
    for (npc in snap.npcs) {
      drawNpc(npc)
    }

    // 8. Jason Duval (when on foot)
    if (snap.playerInVehicleId == null) {
      drawJasonDuval(snap)
    }

    // 9. Blood Particles
    for (p in snap.bloodParticles) {
      drawBox(p.x, p.y, p.z, p.size, p.size, p.size, 0f, 0.78f, 0.03f, 0.06f, 1f, 0.15f)
    }

    // 10. Bullet Tracers
    for (t in snap.tracers) {
      val mx = (t.x1 + t.x2) * 0.5f
      val my = (t.y1 + t.y2) * 0.5f
      val mz = (t.z1 + t.z2) * 0.5f
      val dx = t.x2 - t.x1
      val dz = t.z2 - t.z1
      val len = kotlin.math.hypot(dx, dz).coerceAtLeast(0.1f)
      val yawDeg = Math.toDegrees(kotlin.math.atan2(dx.toDouble(), dz.toDouble())).toFloat()
      drawBox(mx, my, mz, 0.08f, 0.08f, len, yawDeg, t.r, t.g, t.b, t.ttl / t.maxTtl, 1.0f)
    }

    // 11. Explosions
    for (e in snap.explosions) {
      val p = (e.age / e.duration).coerceIn(0f, 1f)
      val s = 1.8f + p * 6.5f
      drawBox(e.x, e.y + p * 1.5f, e.z, s, s, s, p * 120f, 1.0f, 0.45f * (1f - p), 0.12f, 1f - p, 1.0f)
    }
  }

  private fun drawSkyBackdrop() {
    GLES30.glDisable(GLES30.GL_DEPTH_TEST)
    skyShader.use()
    GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, skyVboId)
    GLES30.glEnableVertexAttribArray(0)
    GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 8, 0)
    GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 6)
    GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
    GLES30.glEnable(GLES30.GL_DEPTH_TEST)
  }

  private fun drawJasonDuval(snap: RenderSnapshot) {
    val x = snap.playerX
    val z = snap.playerZ
    val yawDeg = Math.toDegrees(snap.playerHeading.toDouble()).toFloat()
    val swing = sin(snap.playerWalkPhase) * 0.24f

    val fwdX = sin(snap.playerHeading)
    val fwdZ = cos(snap.playerHeading)
    val rightX = cos(snap.playerHeading)
    val rightZ = -sin(snap.playerHeading)

    // Legs (Denim Jeans)
    drawBox(x - rightX * 0.17f + fwdX * swing, 0.45f, z - rightZ * 0.17f + fwdZ * swing, 0.24f, 0.86f, 0.26f, yawDeg, 0.18f, 0.28f, 0.48f)
    drawBox(x + rightX * 0.17f - fwdX * swing, 0.45f, z + rightZ * 0.17f - fwdZ * swing, 0.24f, 0.86f, 0.26f, yawDeg, 0.18f, 0.28f, 0.48f)

    // Torso (Olive tactical vest over white tee)
    drawBox(x, 1.18f, z, 0.62f, 0.72f, 0.34f, yawDeg, 0.30f, 0.38f, 0.26f)
    // Left arm
    drawBox(x - rightX * 0.40f - fwdX * swing * 0.6f, 1.14f, z - rightZ * 0.40f - fwdZ * swing * 0.6f, 0.18f, 0.62f, 0.18f, yawDeg, 0.84f, 0.66f, 0.54f)
    // Right arm (raised if aiming/firing or holding firearm)
    val hasGun = snap.activeWeapon != WeaponType.FISTS
    if (hasGun || snap.playerAiming) {
      drawBox(x + rightX * 0.38f + fwdX * 0.26f, 1.28f, z + rightZ * 0.38f + fwdZ * 0.26f, 0.18f, 0.18f, 0.56f, yawDeg, 0.84f, 0.66f, 0.54f)
      if (hasGun) {
        val gunLen =
          when (snap.activeWeapon) {
            WeaponType.SHOTGUN, WeaponType.CARBINE -> 0.76f
            WeaponType.SMG -> 0.48f
            else -> 0.36f
          }
        drawBox(x + rightX * 0.38f + fwdX * 0.62f, 1.30f, z + rightZ * 0.38f + fwdZ * 0.62f, 0.10f, 0.14f, gunLen, yawDeg, 0.14f, 0.16f, 0.20f)
        if (snap.muzzleFlashTimer > 0f) {
          drawBox(
            x + rightX * 0.38f + fwdX * (0.68f + gunLen * 0.5f),
            1.31f,
            z + rightZ * 0.38f + fwdZ * (0.68f + gunLen * 0.5f),
            0.26f, 0.26f, 0.26f,
            yawDeg + 35f,
            1.0f, 0.82f, 0.22f,
            emissive = 1.0f,
          )
        }
      }
    } else {
      drawBox(x + rightX * 0.40f + fwdX * swing * 0.6f, 1.14f, z + rightZ * 0.40f + fwdZ * swing * 0.6f, 0.18f, 0.62f, 0.18f, yawDeg, 0.84f, 0.66f, 0.54f)
    }

    // Head (Tan skin) + Jason's Signature Backwards Cap
    drawBox(x, 1.72f, z, 0.34f, 0.36f, 0.34f, yawDeg, 0.86f, 0.68f, 0.55f)
    drawBox(x, 1.88f, z, 0.37f, 0.14f, 0.37f, yawDeg, 0.95f, 0.20f, 0.52f) // Cap crown
    drawBox(x - fwdX * 0.22f, 1.84f, z - fwdZ * 0.22f, 0.34f, 0.06f, 0.24f, yawDeg, 0.85f, 0.14f, 0.45f) // Backwards visor
  }

  private fun drawNpc(npc: NpcEntity) {
    val yawDeg = Math.toDegrees(npc.heading.toDouble()).toFloat()
    val flash = npc.hitFlashTimer > 0f
    val sr = if (flash) 1f else npc.shirtR
    val sg = if (flash) 0.25f else npc.shirtG
    val sb = if (flash) 0.25f else npc.shirtB

    if (npc.behavior == NpcBehavior.DEAD) {
      // Fallen flat on ground over blood pool
      drawBox(npc.x, 0.16f, npc.z, 0.55f, 0.24f, 1.45f, yawDeg, sr * 0.8f, sg * 0.8f, sb * 0.8f)
      drawBox(npc.x + sin(npc.heading) * 0.78f, 0.18f, npc.z + cos(npc.heading) * 0.78f, 0.30f, 0.26f, 0.30f, yawDeg, 0.82f, 0.64f, 0.52f)
      return
    }

    val swing = sin(npc.walkPhase) * 0.20f
    val fwdX = sin(npc.heading)
    val fwdZ = cos(npc.heading)
    val rightX = cos(npc.heading)
    val rightZ = -sin(npc.heading)

    drawBox(npc.x - rightX * 0.15f + fwdX * swing, 0.44f, npc.z - rightZ * 0.15f + fwdZ * swing, 0.21f, 0.82f, 0.22f, yawDeg, npc.pantsR, npc.pantsG, npc.pantsB)
    drawBox(npc.x + rightX * 0.15f - fwdX * swing, 0.44f, npc.z + rightZ * 0.15f - fwdZ * swing, 0.21f, 0.82f, 0.22f, yawDeg, npc.pantsR, npc.pantsG, npc.pantsB)
    drawBox(npc.x, 1.15f, npc.z, 0.56f, 0.68f, 0.32f, yawDeg, sr, sg, sb)
    drawBox(npc.x, 1.66f, npc.z, 0.31f, 0.32f, 0.31f, yawDeg, 0.84f, 0.66f, 0.54f)

    if (npc.role == NpcRole.PATROL_COP) {
      drawBox(npc.x, 1.84f, npc.z, 0.35f, 0.12f, 0.35f, yawDeg, 0.10f, 0.22f, 0.56f)
    } else if (npc.role == NpcRole.SWAT) {
      drawBox(npc.x, 1.80f, npc.z, 0.38f, 0.24f, 0.38f, yawDeg, 0.08f, 0.10f, 0.16f)
      drawBox(npc.x + fwdX * 0.16f, 1.72f, npc.z + fwdZ * 0.16f, 0.28f, 0.10f, 0.10f, yawDeg, 0.0f, 0.90f, 1.0f, emissive = 0.9f)
    }
  }

  private fun drawVehicle(v: VehicleEntity, timeSec: Float) {
    val yawDeg = Math.toDegrees(v.heading.toDouble()).toFloat()
    val w = v.type.halfWidth * 2f
    val l = v.type.halfLength * 2f
    val h = v.type.height
    val fwdX = sin(v.heading)
    val fwdZ = cos(v.heading)
    val rightX = cos(v.heading)
    val rightZ = -sin(v.heading)

    val br = if (v.isDestroyed) 0.14f else v.bodyR
    val bg = if (v.isDestroyed) 0.14f else v.bodyG
    val bb = if (v.isDestroyed) 0.16f else v.bodyB

    // Lower Chassis
    drawBox(v.x, 0.56f, v.z, w, h * 0.55f, l, yawDeg, br, bg, bb)

    // Upper Cabin / Windshield
    val cabinH = if (v.type == VehicleType.SWAT_ENFORCER) h * 0.55f else h * 0.44f
    val cabinL = if (v.type == VehicleType.SWAT_ENFORCER) l * 0.82f else l * 0.52f
    val cabinR = if (v.type.isPolice && v.type == VehicleType.POLICE_CRUISER) 0.10f else br * 0.72f
    val cabinG = if (v.type.isPolice && v.type == VehicleType.POLICE_CRUISER) 0.14f else bg * 0.72f
    val cabinB = if (v.type.isPolice && v.type == VehicleType.POLICE_CRUISER) 0.24f else bb * 0.72f
    drawBox(v.x - fwdX * 0.2f, 0.56f + h * 0.45f, v.z - fwdZ * 0.2f, w * 0.86f, cabinH, cabinL, yawDeg, cabinR, cabinG, cabinB)

    // Police Lightbar (Flashing Red & Blue!)
    if (v.type.isPolice && !v.isDestroyed) {
      val redLit = if (v.sirenActive && sin(timeSec * 18f) > 0f) 1.0f else 0.15f
      val blueLit = if (v.sirenActive && sin(timeSec * 18f) <= 0f) 1.0f else 0.15f
      val barY = 0.56f + h * 0.72f
      drawBox(v.x - rightX * 0.35f, barY, v.z - rightZ * 0.35f, 0.52f, 0.16f, 0.28f, yawDeg, 1.0f, 0.12f, 0.20f, emissive = redLit)
      drawBox(v.x + rightX * 0.35f, barY, v.z + rightZ * 0.35f, 0.52f, 0.16f, 0.28f, yawDeg, 0.15f, 0.55f, 1.0f, emissive = blueLit)
    }

    // Headlights & Taillights
    if (!v.isDestroyed) {
      val hlEm = if (v.driverRole != DriverRole.NONE) 1.0f else 0.3f
      drawBox(v.x + fwdX * (v.type.halfLength + 0.02f) - rightX * 0.68f, 0.56f, v.z + fwdZ * (v.type.halfLength + 0.02f) - rightZ * 0.68f, 0.34f, 0.18f, 0.08f, yawDeg, 1.0f, 0.96f, 0.82f, emissive = hlEm)
      drawBox(v.x + fwdX * (v.type.halfLength + 0.02f) + rightX * 0.68f, 0.56f, v.z + fwdZ * (v.type.halfLength + 0.02f) + rightZ * 0.68f, 0.34f, 0.18f, 0.08f, yawDeg, 1.0f, 0.96f, 0.82f, emissive = hlEm)

      drawBox(v.x - fwdX * (v.type.halfLength + 0.02f) - rightX * 0.68f, 0.58f, v.z - fwdZ * (v.type.halfLength + 0.02f) - rightZ * 0.68f, 0.38f, 0.16f, 0.08f, yawDeg, 1.0f, 0.10f, 0.18f, emissive = 0.85f)
      drawBox(v.x - fwdX * (v.type.halfLength + 0.02f) + rightX * 0.68f, 0.58f, v.z - fwdZ * (v.type.halfLength + 0.02f) + rightZ * 0.68f, 0.38f, 0.16f, 0.08f, yawDeg, 1.0f, 0.10f, 0.18f, emissive = 0.85f)
    }

    // 4 Wheels
    val wx = v.type.halfWidth * 0.92f
    val wz = v.type.halfLength * 0.64f
    val wheelOffsets = listOf(-wx to wz, wx to wz, -wx to -wz, wx to -wz)
    for ((ox, oz) in wheelOffsets) {
      drawBox(
        v.x + rightX * ox + fwdX * oz,
        0.34f,
        v.z + rightZ * ox + fwdZ * oz,
        0.28f,
        0.64f,
        0.64f,
        yawDeg,
        0.09f,
        0.09f,
        0.11f,
      )
    }
  }

  private fun drawBox(
    cx: Float,
    cy: Float,
    cz: Float,
    sx: Float,
    sy: Float,
    sz: Float,
    yawDeg: Float,
    r: Float,
    g: Float,
    b: Float,
    a: Float = 1f,
    emissive: Float = 0f,
  ) {
    Matrix.setIdentityM(modelMatrix, 0)
    Matrix.translateM(modelMatrix, 0, cx, cy, cz)
    if (yawDeg != 0f) {
      Matrix.rotateM(modelMatrix, 0, yawDeg, 0f, 1f, 0f)
    }
    Matrix.scaleM(modelMatrix, 0, sx, sy, sz)
    bindModelAndTint(r, g, b, a, emissive)
    unitCube.draw()
  }

  private fun bindModelAndTint(r: Float, g: Float, b: Float, a: Float, emissive: Float) {
    Matrix.multiplyMM(mvpMatrix, 0, vpMatrix, 0, modelMatrix, 0)
    GLES30.glUniformMatrix4fv(uMvpLoc, 1, false, mvpMatrix, 0)
    GLES30.glUniformMatrix4fv(uModelLoc, 1, false, modelMatrix, 0)
    GLES30.glUniform4f(uTintLoc, r, g, b, a)
    GLES30.glUniform1f(uExtraEmissiveLoc, emissive)
  }
}
