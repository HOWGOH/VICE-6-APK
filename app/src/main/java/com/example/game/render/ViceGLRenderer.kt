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
import com.example.game.model.PlayableCharacter
import com.example.game.model.VehicleEntity
import com.example.game.model.VehicleType
import com.example.game.model.WeaponType
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
  private var uAmbientColorLoc = 0
  private var uSunColorLoc = 0
  private var uNeonBoostLoc = 0
  private var uFogColorLoc = 0
  private var uFogStartLoc = 0
  private var uFogEndLoc = 0

  private var uSkyBottomLoc = 0
  private var uSkyMidLoc = 0
  private var uSkyTopLoc = 0

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
    uAmbientColorLoc = worldShader.uniform("uAmbientColor")
    uSunColorLoc = worldShader.uniform("uSunColor")
    uNeonBoostLoc = worldShader.uniform("uNeonBoost")
    uFogColorLoc = worldShader.uniform("uFogColor")
    uFogStartLoc = worldShader.uniform("uFogStart")
    uFogEndLoc = worldShader.uniform("uFogEnd")

    uSkyBottomLoc = skyShader.uniform("uSkyBottom")
    uSkyMidLoc = skyShader.uniform("uSkyMid")
    uSkyTopLoc = skyShader.uniform("uSkyTop")

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

    // Compute Sunset -> Neon Night -> Tropical Dawn -> Day factor
    val nightFactor = (0.5f + 0.5f * sin((snap.timeOfDay - 0.12f) * 2f * PI.toFloat())).coerceIn(0f, 1f)

    GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
    drawSkyBackdrop(nightFactor)

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
    GLES30.glUniform3f(
      uAmbientColorLoc,
      lerp(0.48f, 0.24f, nightFactor),
      lerp(0.42f, 0.20f, nightFactor),
      lerp(0.54f, 0.38f, nightFactor),
    )
    GLES30.glUniform3f(
      uSunColorLoc,
      lerp(1.02f, 0.40f, nightFactor),
      lerp(0.82f, 0.34f, nightFactor),
      lerp(0.70f, 0.56f, nightFactor),
    )
    GLES30.glUniform1f(uNeonBoostLoc, nightFactor)
    GLES30.glUniform3f(
      uFogColorLoc,
      lerp(0.42f, 0.14f, nightFactor),
      lerp(0.18f, 0.06f, nightFactor),
      lerp(0.38f, 0.24f, nightFactor),
    )
    GLES30.glUniform1f(uFogStartLoc, 68f)
    GLES30.glUniform1f(uFogEndLoc, 220f)

    // 1. Static City Mesh
    Matrix.setIdentityM(modelMatrix, 0)
    bindModelAndTint(1f, 1f, 1f, 1f, 0f)
    cityMesh.draw()

    // 2. Animated Turquoise Ocean Water
    val waveGlow = 0.16f + 0.10f * sin(snap.timeSec * 2.2f)
    drawBox(
      168f,
      -0.10f,
      0f,
      92f,
      0.22f,
      360f,
      0f,
      lerp(0.08f, 0.03f, nightFactor),
      lerp(0.74f, 0.44f, nightFactor),
      lerp(0.84f, 0.62f, nightFactor),
      0.92f,
      waveGlow,
    )

    // 3. Persistent Tire Skidmarks
    for (sk in snap.skidDecals) {
      drawBox(sk.x, 0.042f, sk.z, 0.24f, 0.01f, sk.length, sk.headingDeg, 0.04f, 0.04f, 0.06f, sk.alpha)
    }

    // 4. Persistent Blood Pool Decals
    for (d in snap.bloodDecals) {
      Matrix.setIdentityM(modelMatrix, 0)
      Matrix.translateM(modelMatrix, 0, d.x, 0.046f, d.z)
      Matrix.scaleM(modelMatrix, 0, d.radius, 1f, d.radius)
      bindModelAndTint(0.56f, 0.02f, 0.05f, d.alpha, 0.05f)
      unitDisc.draw()
    }

    // 5. Spinning 3D Cash Pickups
    for (c in snap.cashPickups) {
      val bob = 0.55f + 0.16f * sin(c.bobPhase)
      val rotDeg = (c.bobPhase * 57.3f) % 360f
      drawBox(c.x, bob, c.z, 0.58f, 0.24f, 0.34f, rotDeg, 0.12f, 0.90f, 0.42f, 1f, 0.75f)
      drawBox(c.x, bob, c.z, 0.16f, 0.26f, 0.36f, rotDeg, 1.0f, 0.82f, 0.24f, 1f, 0.85f)
    }

    // 6. GPS Waypoint Beacon
    if (snap.waypointX != null && snap.waypointZ != null) {
      val pulse = 0.65f + 0.35f * sin(snap.timeSec * 5f)
      drawBox(snap.waypointX, 16f, snap.waypointZ, 0.65f, 32f, 0.65f, snap.timeSec * 45f, 0.0f, 0.94f, 1.0f, 0.48f * pulse, 1.0f)
    }

    // 7. Vehicles + Headlight Cones + Vehicle Health Bars
    val camYawDeg = Math.toDegrees(camYaw.toDouble()).toFloat()
    for (v in snap.vehicles) {
      drawVehicle(v, snap.timeSec, nightFactor)
      if (!v.isDestroyed && v.hp < v.type.maxHp * 0.98f) {
        drawFloatingHealthBar(v.x, 2.35f, v.z, (v.hp / v.type.maxHp).coerceIn(0f, 1f), 1.6f, camYawDeg, isVehicle = true)
      }
    }

    // 8. NPCs & Cops & SWAT + Enemy Health Bars
    for (npc in snap.npcs) {
      drawNpc(npc)
      if (npc.behavior != NpcBehavior.DEAD && npc.hp < npc.maxHp * 0.98f) {
        drawFloatingHealthBar(npc.x, 2.25f, npc.z, (npc.hp / npc.maxHp).coerceIn(0f, 1f), 0.9f, camYawDeg, isVehicle = false)
      }
    }

    // 9. Active Protagonist (Jason Duval or Lucia Caminos) when on foot
    if (snap.playerInVehicleId == null) {
      drawProtagonist(snap)
    }

    // 10. Blood Particles & Exhaust/Blast Smoke
    for (p in snap.bloodParticles) {
      drawBox(p.x, p.y, p.z, p.size, p.size, p.size, 0f, 0.78f, 0.03f, 0.06f, 1f, 0.15f)
    }
    for (s in snap.smokeParticles) {
      val alpha = (s.ttl / s.maxTtl).coerceIn(0f, 1f) * 0.55f
      val grow = s.size * (1f + (1f - s.ttl / s.maxTtl) * 1.4f)
      drawBox(s.x, s.y, s.z, grow, grow, grow, 0f, s.shade, s.shade, s.shade + 0.04f, alpha)
    }

    // 11. Rockets & Bullet Tracers
    for (r in snap.rockets) {
      val yawDeg = Math.toDegrees(r.heading.toDouble()).toFloat()
      drawBox(r.x, r.y, r.z, 0.18f, 0.18f, 0.75f, yawDeg, 0.25f, 0.35f, 0.22f)
      drawBox(r.x - sin(r.heading) * 0.38f, r.y, r.z - cos(r.heading) * 0.38f, 0.24f, 0.24f, 0.24f, yawDeg + 45f, 1.0f, 0.65f, 0.15f, emissive = 1.0f)
    }
    for (t in snap.tracers) {
      val mx = (t.x1 + t.x2) * 0.5f
      val my = (t.y1 + t.y2) * 0.5f
      val mz = (t.z1 + t.z2) * 0.5f
      val dx = t.x2 - t.x1
      val dz = t.z2 - t.z1
      val len = kotlin.math.hypot(dx, dz).coerceAtLeast(0.1f)
      val yawDeg = Math.toDegrees(kotlin.math.atan2(dx.toDouble(), dz.toDouble())).toFloat()
      drawBox(mx, my, mz, t.thickness, t.thickness, len, yawDeg, t.r, t.g, t.b, t.ttl / t.maxTtl, 1.0f)
    }

    // 12. Explosions
    for (e in snap.explosions) {
      val p = (e.age / e.duration).coerceIn(0f, 1f)
      val s = 2.0f + p * 7.8f
      drawBox(e.x, e.y + p * 1.5f, e.z, s, s, s, p * 120f, 1.0f, 0.45f * (1f - p), 0.12f, 1f - p, 1.0f)
    }
  }

  private fun drawSkyBackdrop(nightFactor: Float) {
    GLES30.glDisable(GLES30.GL_DEPTH_TEST)
    skyShader.use()
    GLES30.glUniform3f(
      uSkyBottomLoc,
      lerp(0.68f, 0.18f, nightFactor),
      lerp(0.24f, 0.06f, nightFactor),
      lerp(0.36f, 0.24f, nightFactor),
    )
    GLES30.glUniform3f(
      uSkyMidLoc,
      lerp(0.55f, 0.22f, nightFactor),
      lerp(0.20f, 0.08f, nightFactor),
      lerp(0.48f, 0.34f, nightFactor),
    )
    GLES30.glUniform3f(
      uSkyTopLoc,
      lerp(0.16f, 0.03f, nightFactor),
      lerp(0.18f, 0.03f, nightFactor),
      lerp(0.42f, 0.10f, nightFactor),
    )
    GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, skyVboId)
    GLES30.glEnableVertexAttribArray(0)
    GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 8, 0)
    GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 6)
    GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
    GLES30.glEnable(GLES30.GL_DEPTH_TEST)
  }

  private fun drawFloatingHealthBar(
    x: Float,
    y: Float,
    z: Float,
    ratio: Float,
    width: Float,
    camYawDeg: Float,
    isVehicle: Boolean,
  ) {
    drawBox(x, y, z, width + 0.08f, 0.16f, 0.06f, camYawDeg, 0.08f, 0.06f, 0.12f, 0.85f, 0.3f)
    val fillW = (width * ratio).coerceAtLeast(0.05f)
    val r = if (isVehicle) 1.0f - ratio * 0.8f else 0.98f
    val g = if (isVehicle) 0.35f + ratio * 0.55f else 0.20f + ratio * 0.68f
    val b = if (isVehicle) 0.20f else 0.30f
    drawBox(x, y, z, fillW, 0.11f, 0.08f, camYawDeg, r, g, b, 0.95f, 0.85f)
  }

  private fun drawProtagonist(snap: RenderSnapshot) {
    val x = snap.playerX
    val z = snap.playerZ
    val yawDeg = Math.toDegrees(snap.playerHeading.toDouble()).toFloat()
    val swing = sin(snap.playerWalkPhase) * 0.24f

    val fwdX = sin(snap.playerHeading)
    val fwdZ = cos(snap.playerHeading)
    val rightX = cos(snap.playerHeading)
    val rightZ = -sin(snap.playerHeading)

    val isLucia = snap.activeCharacter == PlayableCharacter.LUCIA
    val pantsR = if (isLucia) 0.12f else 0.18f
    val pantsG = if (isLucia) 0.12f else 0.28f
    val pantsB = if (isLucia) 0.18f else 0.48f
    val torsoR = if (isLucia) 0.94f else 0.30f
    val torsoG = if (isLucia) 0.20f else 0.38f
    val torsoB = if (isLucia) 0.52f else 0.26f
    val skinR = if (isLucia) 0.82f else 0.86f
    val skinG = if (isLucia) 0.62f else 0.68f
    val skinB = if (isLucia) 0.50f else 0.55f

    // Legs
    drawBox(x - rightX * 0.16f + fwdX * swing, 0.45f, z - rightZ * 0.16f + fwdZ * swing, 0.22f, 0.86f, 0.24f, yawDeg, pantsR, pantsG, pantsB)
    drawBox(x + rightX * 0.16f - fwdX * swing, 0.45f, z + rightZ * 0.16f - fwdZ * swing, 0.22f, 0.86f, 0.24f, yawDeg, pantsR, pantsG, pantsB)

    // Torso
    drawBox(x, 1.18f, z, if (isLucia) 0.54f else 0.62f, 0.72f, 0.32f, yawDeg, torsoR, torsoG, torsoB)

    val w = snap.activeWeapon
    // Left arm (Dual Pistols raises left arm too!)
    if (w == WeaponType.DUAL_PISTOLS) {
      drawBox(x - rightX * 0.36f + fwdX * 0.26f, 1.28f, z - rightZ * 0.36f + fwdZ * 0.26f, 0.16f, 0.16f, 0.54f, yawDeg, skinR, skinG, skinB)
      drawBox(x - rightX * 0.36f + fwdX * 0.60f, 1.30f, z - rightZ * 0.36f + fwdZ * 0.60f, 0.10f, 0.13f, 0.34f, yawDeg, 0.15f, 0.17f, 0.22f)
      if (snap.muzzleFlashTimer > 0f && snap.dualLeftMuzzle) {
        drawBox(x - rightX * 0.36f + fwdX * 0.84f, 1.31f, z - rightZ * 0.36f + fwdZ * 0.84f, 0.24f, 0.24f, 0.24f, yawDeg + 35f, 1.0f, 0.82f, 0.22f, emissive = 1.0f)
      }
    } else {
      drawBox(x - rightX * 0.38f - fwdX * swing * 0.6f, 1.14f, z - rightZ * 0.38f - fwdZ * swing * 0.6f, 0.17f, 0.62f, 0.17f, yawDeg, skinR, skinG, skinB)
    }

    // Right arm & Weapon Geometry
    if (!w.isMelee || w == WeaponType.BAT || snap.playerAiming) {
      drawBox(x + rightX * 0.36f + fwdX * 0.26f, 1.28f, z + rightZ * 0.36f + fwdZ * 0.26f, 0.17f, 0.17f, 0.54f, yawDeg, skinR, skinG, skinB)
      when (w) {
        WeaponType.FISTS -> Unit
        WeaponType.BAT -> {
          drawBox(x + rightX * 0.38f + fwdX * 0.65f, 1.42f, z + rightZ * 0.38f + fwdZ * 0.65f, 0.10f, 0.72f, 0.10f, yawDeg, 0.82f, 0.62f, 0.36f)
        }
        WeaponType.RPG -> {
          drawBox(x + rightX * 0.36f + fwdX * 0.28f, 1.46f, z + rightZ * 0.36f + fwdZ * 0.28f, 0.20f, 0.20f, 1.35f, yawDeg, 0.24f, 0.36f, 0.22f)
          drawBox(x + rightX * 0.36f + fwdX * 0.96f, 1.46f, z + rightZ * 0.36f + fwdZ * 0.96f, 0.22f, 0.22f, 0.24f, yawDeg, 0.88f, 0.25f, 0.20f)
        }
        WeaponType.SNIPER -> {
          drawBox(x + rightX * 0.36f + fwdX * 0.78f, 1.30f, z + rightZ * 0.36f + fwdZ * 0.78f, 0.10f, 0.14f, 1.08f, yawDeg, 0.14f, 0.16f, 0.20f)
          drawBox(x + rightX * 0.36f + fwdX * 0.58f, 1.40f, z + rightZ * 0.36f + fwdZ * 0.58f, 0.08f, 0.08f, 0.32f, yawDeg, 0.0f, 0.90f, 1.0f, emissive = 0.5f)
        }
        else -> {
          val gunLen =
            when (w) {
              WeaponType.SHOTGUN, WeaponType.CARBINE -> 0.76f
              WeaponType.SMG -> 0.48f
              else -> 0.36f
            }
          drawBox(x + rightX * 0.36f + fwdX * 0.62f, 1.30f, z + rightZ * 0.36f + fwdZ * 0.62f, 0.10f, 0.14f, gunLen, yawDeg, 0.14f, 0.16f, 0.20f)
          if (snap.muzzleFlashTimer > 0f && !(w == WeaponType.DUAL_PISTOLS && snap.dualLeftMuzzle)) {
            drawBox(
              x + rightX * 0.36f + fwdX * (0.68f + gunLen * 0.5f),
              1.31f,
              z + rightZ * 0.36f + fwdZ * (0.68f + gunLen * 0.5f),
              0.26f, 0.26f, 0.26f,
              yawDeg + 35f,
              1.0f, 0.82f, 0.22f,
              emissive = 1.0f,
            )
          }
        }
      }
    } else {
      drawBox(x + rightX * 0.38f + fwdX * swing * 0.6f, 1.14f, z + rightZ * 0.38f + fwdZ * swing * 0.6f, 0.17f, 0.62f, 0.17f, yawDeg, skinR, skinG, skinB)
    }

    // Head + Distinct Hair/Cap
    drawBox(x, 1.72f, z, 0.33f, 0.35f, 0.33f, yawDeg, skinR, skinG, skinB)
    if (isLucia) {
      // Dark hair + high ponytail behind head
      drawBox(x, 1.88f, z, 0.36f, 0.14f, 0.36f, yawDeg, 0.10f, 0.07f, 0.08f)
      drawBox(x - fwdX * 0.22f, 1.78f, z - fwdZ * 0.22f, 0.18f, 0.30f, 0.18f, yawDeg, 0.10f, 0.07f, 0.08f)
    } else {
      // Jason's signature backwards cap
      drawBox(x, 1.88f, z, 0.37f, 0.14f, 0.37f, yawDeg, 0.95f, 0.20f, 0.52f)
      drawBox(x - fwdX * 0.22f, 1.84f, z - fwdZ * 0.22f, 0.34f, 0.06f, 0.24f, yawDeg, 0.85f, 0.14f, 0.45f)
    }
  }

  private fun drawNpc(npc: NpcEntity) {
    val yawDeg = Math.toDegrees(npc.heading.toDouble()).toFloat()
    val flash = npc.hitFlashTimer > 0f
    val sr = if (flash) 1f else npc.shirtR
    val sg = if (flash) 0.25f else npc.shirtG
    val sb = if (flash) 0.25f else npc.shirtB

    if (npc.behavior == NpcBehavior.DEAD) {
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

  private fun drawVehicle(v: VehicleEntity, timeSec: Float, nightFactor: Float) {
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

    drawBox(v.x, 0.56f, v.z, w, h * 0.55f, l, yawDeg, br, bg, bb)

    val cabinH = if (v.type == VehicleType.SWAT_ENFORCER) h * 0.55f else h * 0.44f
    val cabinL = if (v.type == VehicleType.SWAT_ENFORCER) l * 0.82f else l * 0.52f
    val cabinR = if (v.type.isPolice && v.type == VehicleType.POLICE_CRUISER) 0.10f else br * 0.72f
    val cabinG = if (v.type.isPolice && v.type == VehicleType.POLICE_CRUISER) 0.14f else bg * 0.72f
    val cabinB = if (v.type.isPolice && v.type == VehicleType.POLICE_CRUISER) 0.24f else bb * 0.72f
    drawBox(v.x - fwdX * 0.2f, 0.56f + h * 0.45f, v.z - fwdZ * 0.2f, w * 0.86f, cabinH, cabinL, yawDeg, cabinR, cabinG, cabinB)

    if (v.type.isPolice && !v.isDestroyed) {
      val redLit = if (v.sirenActive && sin(timeSec * 18f) > 0f) 1.0f else 0.15f
      val blueLit = if (v.sirenActive && sin(timeSec * 18f) <= 0f) 1.0f else 0.15f
      val barY = 0.56f + h * 0.72f
      drawBox(v.x - rightX * 0.35f, barY, v.z - rightZ * 0.35f, 0.52f, 0.16f, 0.28f, yawDeg, 1.0f, 0.12f, 0.20f, emissive = redLit)
      drawBox(v.x + rightX * 0.35f, barY, v.z + rightZ * 0.35f, 0.52f, 0.16f, 0.28f, yawDeg, 0.15f, 0.55f, 1.0f, emissive = blueLit)
    }

    if (!v.isDestroyed) {
      val activeDriver = v.driverRole != DriverRole.NONE
      val hlEm = if (activeDriver) 1.0f else 0.3f
      drawBox(v.x + fwdX * (v.type.halfLength + 0.02f) - rightX * 0.68f, 0.56f, v.z + fwdZ * (v.type.halfLength + 0.02f) - rightZ * 0.68f, 0.34f, 0.18f, 0.08f, yawDeg, 1.0f, 0.96f, 0.82f, emissive = hlEm)
      drawBox(v.x + fwdX * (v.type.halfLength + 0.02f) + rightX * 0.68f, 0.56f, v.z + fwdZ * (v.type.halfLength + 0.02f) + rightZ * 0.68f, 0.34f, 0.18f, 0.08f, yawDeg, 1.0f, 0.96f, 0.82f, emissive = hlEm)

      drawBox(v.x - fwdX * (v.type.halfLength + 0.02f) - rightX * 0.68f, 0.58f, v.z - fwdZ * (v.type.halfLength + 0.02f) - rightZ * 0.68f, 0.38f, 0.16f, 0.08f, yawDeg, 1.0f, 0.10f, 0.18f, emissive = 0.85f)
      drawBox(v.x - fwdX * (v.type.halfLength + 0.02f) + rightX * 0.68f, 0.58f, v.z - fwdZ * (v.type.halfLength + 0.02f) + rightZ * 0.68f, 0.38f, 0.16f, 0.08f, yawDeg, 1.0f, 0.10f, 0.18f, emissive = 0.85f)

      // Ground Headlight Glow Beam ahead of active vehicles (stronger at night)
      if (activeDriver) {
        val beamAlpha = lerp(0.10f, 0.28f, nightFactor)
        val beamDist = v.type.halfLength + 3.2f
        drawBox(
          v.x + fwdX * beamDist,
          0.05f,
          v.z + fwdZ * beamDist,
          w * 1.35f,
          0.02f,
          5.6f,
          yawDeg,
          1.0f,
          0.95f,
          0.76f,
          beamAlpha,
          emissive = 0.95f,
        )
      }
    }

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

  private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t
}
