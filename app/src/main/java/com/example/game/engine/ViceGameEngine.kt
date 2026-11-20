package com.example.game.engine

import com.example.data.SavedSandboxState
import com.example.game.audio.ViceAudioEngine
import com.example.game.model.BlipKind
import com.example.game.model.BloodDecal
import com.example.game.model.BloodParticle
import com.example.game.model.CashPickup
import com.example.game.model.CashPopup
import com.example.game.model.DriverRole
import com.example.game.model.ExplosionEffect
import com.example.game.model.GameHudState
import com.example.game.model.GamePhase
import com.example.game.model.NpcBehavior
import com.example.game.model.NpcEntity
import com.example.game.model.NpcRole
import com.example.game.model.ProjectileTracer
import com.example.game.model.RadarBlip
import com.example.game.model.RadioStation
import com.example.game.model.VehicleEntity
import com.example.game.model.VehicleType
import com.example.game.model.WeaponInventorySlot
import com.example.game.model.WeaponType
import com.example.game.world.CityMap
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

data class RenderSnapshot(
  val timeSec: Float,
  val playerX: Float,
  val playerZ: Float,
  val playerHeading: Float,
  val playerWalkPhase: Float,
  val playerAiming: Boolean,
  val playerInVehicleId: Int?,
  val activeWeapon: WeaponType,
  val muzzleFlashTimer: Float,
  val cameraYaw: Float,
  val cameraPitch: Float,
  val cameraShake: Float,
  val waypointX: Float?,
  val waypointZ: Float?,
  val vehicles: List<VehicleEntity>,
  val npcs: List<NpcEntity>,
  val bloodDecals: List<BloodDecal>,
  val bloodParticles: List<BloodParticle>,
  val cashPickups: List<CashPickup>,
  val tracers: List<ProjectileTracer>,
  val explosions: List<ExplosionEffect>,
)

class ViceGameEngine(private val audio: ViceAudioEngine) {
  private val lock = Any()
  private val rng = Random(System.currentTimeMillis())

  // World Simulation State
  var phase: GamePhase = GamePhase.TITLE
    private set

  private var simTime = 0f

  // Player (Jason Duval)
  private var playerX = 76f
  private var playerZ = 8f
  private var playerHeading = 0f
  private var playerWalkPhase = 0f
  private var playerHp = 100f
  private var playerArmor = 25f
  private var cash = 350
  private var activeWeaponIndex = 1 // Pistol default
  private val inventory = mutableListOf<WeaponInventorySlot>()
  private var fireCooldown = 0f
  private var reloadTimer = 0f
  private var isReloading = false
  private var muzzleFlashTimer = 0f

  // Camera
  private var cameraYaw = 0f
  private var cameraPitch = 0.34f
  private var cameraShake = 0f

  // Wanted & Police
  private var wantedHeat = 0f
  private var wantedStars = 0
  private var timeSincePoliceSeen = 0f
  private var bustedProgress = 0f

  // Career Stats
  private var totalEliminations = 0
  private var totalEarned = 0
  private var highestWanted = 0
  private var lastPenaltyPaid = 0
  private var latestCashPopup: CashPopup? = null
  private var popupCounter = 0L

  // Vehicle & Radio
  private var playerVehicleId: Int? = null
  private var radioStation = RadioStation.WAVE_103
  private var isMuted = false

  // Map Waypoint
  private var waypointX: Float? = null
  private var waypointZ: Float? = null

  // Inputs
  @Volatile var moveStickX = 0f
  @Volatile var moveStickY = 0f
  @Volatile var isSprinting = false
  @Volatile var isFiringInput = false
  @Volatile var driveThrottle = 0f
  @Volatile var driveBrake = 0f
  @Volatile var driveSteer = 0f
  @Volatile var driveDrift = false

  // Entities
  private val vehicles = mutableListOf<VehicleEntity>()
  private val npcs = mutableListOf<NpcEntity>()
  private val bloodDecals = mutableListOf<BloodDecal>()
  private val bloodParticles = mutableListOf<BloodParticle>()
  private val cashPickups = mutableListOf<CashPickup>()
  private val tracers = mutableListOf<ProjectileTracer>()
  private val explosions = mutableListOf<ExplosionEffect>()

  private var nextEntityId = 100
  private var saveDirty = false
  private var saveCallback: (() -> Unit)? = null

  init {
    resetInventoryDefaults()
    populateWorldEntities()
  }

  fun setOnSaveNeeded(callback: () -> Unit) {
    saveCallback = callback
  }

  fun applySavedState(saved: SavedSandboxState) {
    synchronized(lock) {
      cash = saved.cash
      playerArmor = saved.armor.toFloat()
      totalEliminations = saved.totalEliminations
      totalEarned = saved.totalEarned
      highestWanted = saved.highestWanted
      radioStation = saved.radioStation
      isMuted = saved.isMuted
      audio.radioStation = radioStation
      audio.isMuted = isMuted
      inventory.clear()
      inventory.addAll(saved.inventory)
      if (activeWeaponIndex !in inventory.indices || !inventory[activeWeaponIndex].unlocked) {
        activeWeaponIndex = inventory.indexOfFirst { it.unlocked && it.type != WeaponType.FISTS }
          .takeIf { it >= 0 } ?: 0
      }
    }
  }

  fun exportSaveSnapshot(): SavedSandboxState {
    synchronized(lock) {
      return SavedSandboxState(
        hasSave = true,
        cash = cash,
        armor = playerArmor.toInt(),
        totalEliminations = totalEliminations,
        totalEarned = totalEarned,
        highestWanted = highestWanted,
        radioStation = radioStation,
        isMuted = isMuted,
        inventory = inventory.toList(),
      )
    }
  }

  private fun resetInventoryDefaults() {
    inventory.clear()
    inventory.addAll(
      WeaponType.entries.map { w ->
        when (w) {
          WeaponType.FISTS -> WeaponInventorySlot(w, true, 0, 0)
          WeaponType.PISTOL -> WeaponInventorySlot(w, true, w.clipSize, 60)
          else -> WeaponInventorySlot(w, false, 0, 0)
        }
      }
    )
    activeWeaponIndex = 1
  }

  private fun populateWorldEntities() {
    vehicles.clear()
    npcs.clear()
    bloodDecals.clear()
    bloodParticles.clear()
    cashPickups.clear()
    tracers.clear()
    explosions.clear()

    // Spawn iconic Vice vehicles across the districts
    val initialCars =
      listOf(
        Triple(VehicleType.SPORTS_COUPE, 80f to 16f, Triple(0.98f, 0.18f, 0.56f)), // Neon Magenta Cheetah near Jason
        Triple(VehicleType.MUSCLE_SEDAN, 55f to -14f, Triple(0.05f, 0.85f, 0.95f)), // Electric Cyan Sabre
        Triple(VehicleType.SPORTS_COUPE, 0f to 28f, Triple(1.0f, 0.74f, 0.16f)), // Sunset Gold Cheetah downtown
        Triple(VehicleType.POLICE_CRUISER, -4f to -55f, Triple(0.95f, 0.96f, 1.0f)), // VCPD parked cruiser
        Triple(VehicleType.MUSCLE_SEDAN, 92f to -45f, Triple(0.22f, 0.90f, 0.55f)), // Mint Ocean Drive
        Triple(VehicleType.SPORTS_COUPE, -55f to 42f, Triple(0.65f, 0.35f, 1.0f)), // Downtown West
        Triple(VehicleType.MUSCLE_SEDAN, -110f to -20f, Triple(0.92f, 0.32f, 0.24f)), // Industrial loop
        Triple(VehicleType.POLICE_CRUISER, 55f to 55f, Triple(0.95f, 0.96f, 1.0f)), // South Strip VCPD
      )
    for ((type, pos, col) in initialCars) {
      vehicles.add(
        VehicleEntity(
          id = nextEntityId++,
          type = type,
          x = pos.first,
          z = pos.second,
          heading = if (abs(pos.first) in setOf(0f, 55f, 92f, 110f)) 0f else (PI * 0.5).toFloat(),
          bodyR = col.first,
          bodyG = col.second,
          bodyB = col.third,
        )
      )
    }

    // Spawn 22 Civilians along sidewalks and boardwalk
    repeat(22) { spawnCivilian() }
  }

  private fun spawnCivilian(nearPlayer: Boolean = false) {
    val node = CityMap.sidewalkWalkNodes[rng.nextInt(CityMap.sidewalkWalkNodes.size)]
    var x = node.x + (rng.nextFloat() * 4f - 2f)
    var z = node.z + (rng.nextFloat() * 4f - 2f)
    if (nearPlayer && hypot(x - playerX, z - playerZ) < 18f) {
      x = -x
    }
    val resolved = CityMap.resolveCircleCollision(x, z, 0.5f)
    val shirts =
      listOf(
        Triple(1.0f, 0.25f, 0.58f),
        Triple(0.12f, 0.88f, 0.92f),
        Triple(1.0f, 0.78f, 0.24f),
        Triple(0.96f, 0.95f, 0.98f),
        Triple(0.35f, 0.92f, 0.58f),
        Triple(0.66f, 0.42f, 0.98f),
      )
    val sh = shirts[rng.nextInt(shirts.size)]
    val target = CityMap.sidewalkWalkNodes[rng.nextInt(CityMap.sidewalkWalkNodes.size)]
    npcs.add(
      NpcEntity(
        id = nextEntityId++,
        role = NpcRole.CIVILIAN,
        x = resolved.first,
        z = resolved.second,
        heading = rng.nextFloat() * 6.28f,
        hp = 60f,
        maxHp = 60f,
        behavior = NpcBehavior.WANDER,
        targetX = target.x,
        targetZ = target.z,
        shirtR = sh.first,
        shirtG = sh.second,
        shirtB = sh.third,
        pantsR = 0.16f + rng.nextFloat() * 0.2f,
        pantsG = 0.18f + rng.nextFloat() * 0.2f,
        pantsB = 0.26f + rng.nextFloat() * 0.25f,
      )
    )
  }

  fun setPhase(newPhase: GamePhase) {
    synchronized(lock) {
      phase = newPhase
      if (newPhase != GamePhase.PLAYING) {
        audio.inVehicle = false
        audio.sirenActive = false
      }
    }
  }

  fun orbitCamera(deltaYaw: Float, deltaPitch: Float) {
    synchronized(lock) {
      cameraYaw += deltaYaw
      cameraPitch = (cameraPitch + deltaPitch).coerceIn(0.14f, 0.92f)
    }
  }

  fun toggleSiren() {
    synchronized(lock) {
      val vId = playerVehicleId ?: return
      val v = vehicles.firstOrNull { it.id == vId } ?: return
      if (v.type.isPolice) {
        v.sirenActive = !v.sirenActive
        audio.playUiConfirm()
      }
    }
  }

  fun cycleRadioStation() {
    synchronized(lock) {
      val stations = RadioStation.entries
      radioStation = stations[(radioStation.ordinal + 1) % stations.size]
      audio.radioStation = radioStation
      audio.playUiConfirm()
      saveDirty = true
    }
  }

  fun toggleMute() {
    synchronized(lock) {
      isMuted = !isMuted
      audio.isMuted = isMuted
      saveDirty = true
    }
  }

  fun setWaypoint(wx: Float?, wz: Float?) {
    synchronized(lock) {
      waypointX = wx
      waypointZ = wz
      audio.playUiConfirm()
    }
  }

  fun selectWeapon(type: WeaponType) {
    synchronized(lock) {
      val idx = inventory.indexOfFirst { it.type == type && it.unlocked }
      if (idx >= 0 && idx != activeWeaponIndex) {
        activeWeaponIndex = idx
        isReloading = false
        reloadTimer = 0f
        audio.playUiConfirm()
      }
    }
  }

  fun cycleWeapon() {
    synchronized(lock) {
      val unlockedIndices = inventory.mapIndexedNotNull { idx, slot -> if (slot.unlocked) idx else null }
      if (unlockedIndices.isEmpty()) return
      val curPos = unlockedIndices.indexOf(activeWeaponIndex)
      activeWeaponIndex = unlockedIndices[(curPos + 1) % unlockedIndices.size]
      isReloading = false
      reloadTimer = 0f
      audio.playUiConfirm()
    }
  }

  fun triggerReload() {
    synchronized(lock) {
      val slot = inventory[activeWeaponIndex]
      if (slot.type == WeaponType.FISTS || isReloading) return
      if (slot.clipAmmo < slot.type.clipSize && slot.reserveAmmo > 0) {
        isReloading = true
        reloadTimer = slot.type.reloadSec
        audio.playUiConfirm()
      }
    }
  }

  fun buyWeaponOrAmmo(type: WeaponType): Boolean {
    synchronized(lock) {
      val idx = inventory.indexOfFirst { it.type == type }
      if (idx < 0) return false
      val slot = inventory[idx]
      if (!slot.unlocked) {
        if (cash < type.unlockCost) return false
        cash -= type.unlockCost
        inventory[idx] = slot.copy(unlocked = true, clipAmmo = type.clipSize, reserveAmmo = type.ammoPackAmount)
        activeWeaponIndex = idx
        audio.playCashPickup()
        saveDirty = true
        return true
      } else if (type != WeaponType.FISTS && slot.reserveAmmo < type.reserveCap) {
        if (cash < type.ammoPackCost) return false
        cash -= type.ammoPackCost
        val newReserve = min(type.reserveCap, slot.reserveAmmo + type.ammoPackAmount)
        val newClip = if (slot.clipAmmo == 0) min(type.clipSize, newReserve) else slot.clipAmmo
        val taken = newClip - slot.clipAmmo
        inventory[idx] = slot.copy(clipAmmo = newClip, reserveAmmo = newReserve - taken)
        audio.playCashPickup()
        saveDirty = true
        return true
      }
      return false
    }
  }

  fun buyArmor(): Boolean {
    synchronized(lock) {
      if (playerArmor >= 100f || cash < 150) return false
      cash -= 150
      playerArmor = 100f
      audio.playCashPickup()
      saveDirty = true
      return true
    }
  }

  fun toggleEnterExitVehicle() {
    synchronized(lock) {
      if (phase != GamePhase.PLAYING) return
      val currentVId = playerVehicleId
      if (currentVId != null) {
        // Exit vehicle beside left door
        val v = vehicles.firstOrNull { it.id == currentVId }
        if (v != null) {
          v.driverRole = DriverRole.NONE
          v.steerInput = 0f
          val doorX = v.x - cos(v.heading) * (v.type.halfWidth + 1.0f)
          val doorZ = v.z + sin(v.heading) * (v.type.halfWidth + 1.0f)
          val res = CityMap.resolveCircleCollision(doorX, doorZ, 0.55f)
          playerX = res.first
          playerZ = res.second
        }
        playerVehicleId = null
        audio.inVehicle = false
        audio.playUiConfirm()
        return
      }

      // Find nearest non-destroyed vehicle within 4.4m
      val nearest =
        vehicles
          .filter { !it.isDestroyed }
          .minByOrNull { hypot(it.x - playerX, it.z - playerZ) }
      if (nearest != null && hypot(nearest.x - playerX, nearest.z - playerZ) <= 4.4f) {
        val wasPolice = nearest.type.isPolice
        val wasAi = nearest.driverRole != DriverRole.NONE
        nearest.driverRole = DriverRole.PLAYER
        playerVehicleId = nearest.id
        playerX = nearest.x
        playerZ = nearest.z
        cameraYaw = nearest.heading
        if (wasPolice || wasAi) {
          addWantedHeat(if (wasPolice) 35f else 18f)
        }
        audio.inVehicle = true
        audio.playUiConfirm()
      }
    }
  }

  fun respawnAfterFall() {
    synchronized(lock) {
      val fee = min(cash, 100)
      cash -= fee
      lastPenaltyPaid = fee
      playerHp = 100f
      wantedHeat = 0f
      wantedStars = 0
      bustedProgress = 0f
      timeSincePoliceSeen = 0f
      playerVehicleId = null
      playerX = CityMap.CLINIC_X
      playerZ = CityMap.CLINIC_Z + 4.5f
      playerHeading = 0f
      cameraYaw = 0f
      // Clear aggressive nearby cops
      npcs.removeAll { it.role != NpcRole.CIVILIAN }
      vehicles.forEach {
        if (it.driverRole == DriverRole.POLICE_AI) {
          it.driverRole = DriverRole.NONE
          it.sirenActive = false
          it.speed = 0f
        }
      }
      phase = GamePhase.PLAYING
      saveDirty = true
    }
  }

  fun resetSandbox(fresh: SavedSandboxState) {
    synchronized(lock) {
      applySavedState(fresh)
      playerHp = 100f
      wantedHeat = 0f
      wantedStars = 0
      bustedProgress = 0f
      playerVehicleId = null
      playerX = 76f
      playerZ = 8f
      playerHeading = 0f
      cameraYaw = 0f
      waypointX = null
      waypointZ = null
      populateWorldEntities()
      phase = GamePhase.PLAYING
    }
  }

  fun step(dtRaw: Float) {
    val dt = dtRaw.coerceIn(0.001f, 0.05f)
    var triggerSave = false
    synchronized(lock) {
      if (phase != GamePhase.PLAYING) {
        audio.inVehicle = false
        audio.sirenActive = false
        return
      }
      simTime += dt
      fireCooldown = (fireCooldown - dt).coerceAtLeast(0f)
      muzzleFlashTimer = (muzzleFlashTimer - dt).coerceAtLeast(0f)
      cameraShake = (cameraShake - dt * 2.8f).coerceAtLeast(0f)

      updateReload(dt)
      updatePlayerOnFoot(dt)
      updateVehicles(dt)
      updateNpcs(dt)
      updatePoliceDirector(dt)
      updateEffectsAndPickups(dt)
      updateAudioState()

      if (saveDirty) {
        saveDirty = false
        triggerSave = true
      }
    }
    if (triggerSave) {
      saveCallback?.invoke()
    }
  }

  private fun updateReload(dt: Float) {
    if (!isReloading) return
    reloadTimer -= dt
    if (reloadTimer <= 0f) {
      isReloading = false
      val slot = inventory[activeWeaponIndex]
      val needed = slot.type.clipSize - slot.clipAmmo
      val load = min(needed, slot.reserveAmmo)
      inventory[activeWeaponIndex] =
        slot.copy(clipAmmo = slot.clipAmmo + load, reserveAmmo = slot.reserveAmmo - load)
      saveDirty = true
    }
  }

  private fun updatePlayerOnFoot(dt: Float) {
    if (playerVehicleId != null) return

    val sx = moveStickX
    val sy = moveStickY
    val mag = hypot(sx, sy).coerceAtMost(1f)
    if (mag > 0.08f) {
      val speed = if (isSprinting || mag > 0.82f) 9.8f else 6.0f
      // Camera-relative movement: forward = (sin(cameraYaw), cos(cameraYaw))
      // Wait: camera sits behind Jason at (playerX - sin(yaw)*dist, playerZ - cos(yaw)*dist), looking toward +sin,+cos
      val fwdX = sin(cameraYaw)
      val fwdZ = cos(cameraYaw)
      val rightX = -cos(cameraYaw)
      val rightZ = sin(cameraYaw)

      // stickY up is negative in Compose screen coords -> invert sy for forward
      val moveX = rightX * sx + fwdX * (-sy)
      val moveZ = rightZ * sx + fwdZ * (-sy)
      val moveLen = hypot(moveX, moveZ).coerceAtLeast(0.001f)
      val nx = moveX / moveLen
      val nz = moveZ / moveLen

      val nextX = playerX + nx * speed * mag * dt
      val nextZ = playerZ + nz * speed * mag * dt
      val resolved = CityMap.resolveCircleCollision(nextX, nextZ, 0.55f)
      playerX = resolved.first
      playerZ = resolved.second

      playerHeading = atan2(nx, nz)
      playerWalkPhase += dt * speed * 1.85f
    } else {
      playerWalkPhase = 0f
    }

    // Clear waypoint if arrived
    val wx = waypointX
    val wz = waypointZ
    if (wx != null && wz != null && hypot(wx - playerX, wz - playerZ) < 6f) {
      waypointX = null
      waypointZ = null
    }

    if (isFiringInput && fireCooldown <= 0f && !isReloading) {
      fireActiveWeapon()
    }
  }

  private fun fireActiveWeapon() {
    val slot = inventory[activeWeaponIndex]
    val weapon = slot.type

    if (weapon != WeaponType.FISTS) {
      if (slot.clipAmmo <= 0) {
        if (slot.reserveAmmo > 0) {
          triggerReload()
        }
        return
      }
      inventory[activeWeaponIndex] = slot.copy(clipAmmo = slot.clipAmmo - 1)
      if (inventory[activeWeaponIndex].clipAmmo == 0 && inventory[activeWeaponIndex].reserveAmmo > 0) {
        triggerReload()
      }
    }

    fireCooldown = weapon.fireIntervalSec
    audio.playWeapon(weapon)

    // Determine aim direction: camera forward, with soft auto-aim toward nearest NPC in cone
    var aimDirX = sin(cameraYaw)
    var aimDirZ = cos(cameraYaw)
    val bestTarget = findAimAssistTarget(aimDirX, aimDirZ, weapon.range)
    if (bestTarget != null) {
      val dx = bestTarget.x - playerX
      val dz = bestTarget.z - playerZ
      val dist = hypot(dx, dz).coerceAtLeast(0.01f)
      aimDirX = dx / dist
      aimDirZ = dz / dist
    }
    playerHeading = atan2(aimDirX, aimDirZ)

    if (weapon == WeaponType.FISTS) {
      // Melee sweep
      val hitNpc =
        npcs
          .filter { it.behavior != NpcBehavior.DEAD }
          .firstOrNull {
            val dx = it.x - playerX
            val dz = it.z - playerZ
            val d = hypot(dx, dz)
            d <= weapon.range && (dx * aimDirX + dz * aimDirZ) / d.coerceAtLeast(0.01f) > 0.35f
          }
      if (hitNpc != null) {
        damageNpc(hitNpc, weapon.damage, aimDirX, aimDirZ)
        audio.playImpact()
      }
      return
    }

    muzzleFlashTimer = 0.065f
    cameraShake = maxOf(cameraShake, if (weapon == WeaponType.SHOTGUN) 0.35f else 0.14f)
    alertNearbyCivilians(playerX, playerZ, 26f)
    if (wantedStars == 0) addWantedHeat(10f)

    val muzzleX = playerX + aimDirX * 0.7f
    val muzzleZ = playerZ + aimDirZ * 0.7f
    val baseAngle = atan2(aimDirX, aimDirZ)

    repeat(weapon.pellets) {
      val angle = baseAngle + (rng.nextFloat() * 2f - 1f) * weapon.spreadRad
      val dx = sin(angle)
      val dz = cos(angle)
      val wallDist = CityMap.raycastBuildings(muzzleX, muzzleZ, dx, dz, weapon.range)

      var closestNpc: NpcEntity? = null
      var bestNpcT = wallDist

      for (npc in npcs) {
        if (npc.behavior == NpcBehavior.DEAD) continue
        val t = rayCircleIntersect(muzzleX, muzzleZ, dx, dz, npc.x, npc.z, 0.72f)
        if (t != null && t in 0f..bestNpcT) {
          bestNpcT = t
          closestNpc = npc
        }
      }

      var closestVeh: VehicleEntity? = null
      var bestVehT = bestNpcT
      for (v in vehicles) {
        if (v.isDestroyed) continue
        val t = rayCircleIntersect(muzzleX, muzzleZ, dx, dz, v.x, v.z, v.type.halfWidth + 0.4f)
        if (t != null && t in 0f..bestVehT) {
          bestVehT = t
          closestVeh = v
        }
      }

      val hitT =
        when {
          closestVeh != null -> {
            damageVehicle(closestVeh, weapon.damage * 0.65f)
            bestVehT
          }
          closestNpc != null -> {
            damageNpc(closestNpc, weapon.damage, dx, dz)
            bestNpcT
          }
          else -> wallDist
        }

      tracers.add(
        ProjectileTracer(
          x1 = muzzleX,
          y1 = 1.32f,
          z1 = muzzleZ,
          x2 = muzzleX + dx * hitT,
          y2 = 1.25f,
          z2 = muzzleZ + dz * hitT,
          ttl = 0.08f,
          maxTtl = 0.08f,
          r = 1.0f,
          g = 0.86f,
          b = 0.25f,
        )
      )
    }
  }

  private fun findAimAssistTarget(fwdX: Float, fwdZ: Float, range: Float): NpcEntity? {
    var best: NpcEntity? = null
    var bestScore = Float.MAX_VALUE
    for (npc in npcs) {
      if (npc.behavior == NpcBehavior.DEAD) continue
      val dx = npc.x - playerX
      val dz = npc.z - playerZ
      val dist = hypot(dx, dz)
      if (dist > range || dist < 0.2f) continue
      val dot = (dx * fwdX + dz * fwdZ) / dist
      if (dot > 0.72f) {
        val score = dist * (1.25f - dot)
        if (score < bestScore) {
          bestScore = score
          best = npc
        }
      }
    }
    return best
  }

  private fun damageNpc(npc: NpcEntity, damage: Float, dirX: Float, dirZ: Float) {
    if (npc.behavior == NpcBehavior.DEAD) return
    npc.hp -= damage
    npc.hitFlashTimer = 0.14f
    npc.vx = dirX * 4.5f
    npc.vz = dirZ * 4.5f

    // Spawn 3D Blood Particle spray!
    val pCount = if (npc.hp <= 0f) 14 else 7
    repeat(pCount) {
      bloodParticles.add(
        BloodParticle(
          x = npc.x,
          y = 1.15f + rng.nextFloat() * 0.35f,
          z = npc.z,
          vx = dirX * (2.5f + rng.nextFloat() * 3.5f) + (rng.nextFloat() - 0.5f) * 3.2f,
          vy = 1.8f + rng.nextFloat() * 3.0f,
          vz = dirZ * (2.5f + rng.nextFloat() * 3.5f) + (rng.nextFloat() - 0.5f) * 3.2f,
          ttl = 0.55f + rng.nextFloat() * 0.35f,
          size = 0.11f + rng.nextFloat() * 0.10f,
        )
      )
    }

    // Spawn ground blood splatter decal immediately on hit
    addBloodDecal(npc.x + dirX * 0.35f, npc.z + dirZ * 0.35f, if (npc.hp <= 0f) 1.35f else 0.65f)

    if (npc.role == NpcRole.CIVILIAN) {
      addWantedHeat(if (npc.hp <= 0f) 32f else 14f)
    } else {
      addWantedHeat(if (npc.hp <= 0f) 55f else 25f)
    }

    if (npc.hp <= 0f) {
      npc.behavior = NpcBehavior.DEAD
      npc.deadTimer = 6.5f
      totalEliminations++
      // Drop rotating glowing 3D cash bundle(s)
      val dropAmount =
        when (npc.role) {
          NpcRole.CIVILIAN -> 20 + rng.nextInt(55)
          NpcRole.PATROL_COP -> 45 + rng.nextInt(60)
          NpcRole.SWAT -> 80 + rng.nextInt(80)
        }
      cashPickups.add(
        CashPickup(
          id = nextEntityId++,
          x = npc.x + (rng.nextFloat() - 0.5f) * 0.8f,
          z = npc.z + (rng.nextFloat() - 0.5f) * 0.8f,
          amount = dropAmount,
          bobPhase = rng.nextFloat() * 6.28f,
        )
      )
      // Cops also replenish small pistol/carbine reserve on kill
      if (npc.role != NpcRole.CIVILIAN) {
        val targetSlotIdx = if (npc.role == NpcRole.SWAT) 4 else 1
        val s = inventory[targetSlotIdx]
        if (s.unlocked) {
          inventory[targetSlotIdx] =
            s.copy(reserveAmmo = min(s.type.reserveCap, s.reserveAmmo + 12))
        }
      }
      saveDirty = true
    } else if (npc.role == NpcRole.CIVILIAN) {
      npc.behavior = NpcBehavior.FLEE
    }
  }

  private fun addBloodDecal(x: Float, z: Float, targetR: Float) {
    if (bloodDecals.size >= 85) {
      bloodDecals.removeAt(0)
    }
    bloodDecals.add(
      BloodDecal(
        x = x,
        z = z,
        radius = targetR * 0.25f,
        targetRadius = targetR,
        rotation = rng.nextFloat() * 6.28f,
      )
    )
  }

  private fun damageVehicle(v: VehicleEntity, dmg: Float) {
    if (v.isDestroyed) return
    v.hp -= dmg
    if (v.type.isPolice) addWantedHeat(18f)
    if (v.hp <= 0f) {
      v.hp = 0f
      v.isDestroyed = true
      v.speed = 0f
      v.sirenActive = false
      explosions.add(ExplosionEffect(v.x, 1.0f, v.z))
      audio.playExplosion()
      cameraShake = 0.75f
      addWantedHeat(60f)
      if (playerVehicleId == v.id) {
        playerVehicleId = null
        audio.inVehicle = false
        applyPlayerDamage(75f)
        playerX = v.x + 2.2f
      }
    }
  }

  private fun updateVehicles(dt: Float) {
    for (v in vehicles) {
      if (v.isDestroyed) continue

      when (v.driverRole) {
        DriverRole.PLAYER -> {
          val accelInput = driveThrottle - driveBrake
          if (accelInput > 0.05f) {
            v.speed += v.type.accel * accelInput * dt
          } else if (accelInput < -0.05f) {
            if (v.speed > 1.0f) {
              v.speed -= v.type.accel * 1.9f * (-accelInput) * dt
            } else {
              v.speed += v.type.accel * 0.55f * accelInput * dt
            }
          } else {
            // Rolling drag
            v.speed *= (1f - 1.6f * dt).coerceAtLeast(0f)
          }
          if (driveDrift) {
            v.speed *= (1f - 1.2f * dt).coerceAtLeast(0f)
          }
          v.speed = v.speed.coerceIn(-v.type.maxSpeed * 0.42f, v.type.maxSpeed)
          v.steerInput = driveSteer
          val steerFactor = (abs(v.speed) / 7f).coerceIn(0f, 1f) * if (v.speed >= 0f) 1f else -1f
          val driftMult = if (driveDrift) 1.45f else 1f
          // Screen steer right -> negative heading delta in right-handed XZ with +sin/+cos forward
          v.heading -= driveSteer * v.type.turnRate * steerFactor * driftMult * dt
        }

        DriverRole.POLICE_AI -> {
          v.sirenActive = wantedStars >= 2
          val dx = playerX - v.x
          val dz = playerZ - v.z
          val dist = hypot(dx, dz)
          val desiredHeading = atan2(dx, dz)
          var angleDiff = desiredHeading - v.heading
          while (angleDiff > PI) angleDiff -= (2 * PI).toFloat()
          while (angleDiff < -PI) angleDiff += (2 * PI).toFloat()

          v.steerInput = (angleDiff * 1.6f).coerceIn(-1f, 1f)
          v.heading += v.steerInput * v.type.turnRate * dt
          val targetSpeed =
            if (dist > 14f) v.type.maxSpeed * 0.88f
            else if (playerVehicleId != null) v.type.maxSpeed * 0.92f // Ram player's car!
            else 14f
          v.speed += (targetSpeed - v.speed).coerceIn(-v.type.accel * dt, v.type.accel * dt)
        }

        else -> {
          v.speed *= (1f - 2.5f * dt).coerceAtLeast(0f)
          v.steerInput = 0f
        }
      }

      if (abs(v.speed) > 0.05f) {
        val nx = v.x + sin(v.heading) * v.speed * dt
        val nz = v.z + cos(v.heading) * v.speed * dt
        val col = CityMap.resolveCircleCollision(nx, nz, v.type.halfWidth + 0.35f)
        v.x = col.first
        v.z = col.second
        if (col.third) {
          if (abs(v.speed) > 10f && v.driverRole == DriverRole.PLAYER) {
            audio.playImpact()
            cameraShake = maxOf(cameraShake, 0.25f)
            damageVehicle(v, abs(v.speed) * 0.55f)
          }
          v.speed *= -0.32f
        }
        v.wheelSpin += v.speed * dt * 2.4f
      }

      if (v.driverRole == DriverRole.PLAYER) {
        playerX = v.x
        playerZ = v.z
        playerHeading = v.heading
      }

      // Ramming NPCs
      if (abs(v.speed) > 5.0f) {
        val ramRadius = v.type.halfWidth + 0.85f
        for (npc in npcs) {
          if (npc.behavior == NpcBehavior.DEAD) continue
          if (hypot(npc.x - v.x, npc.z - v.z) < ramRadius) {
            val rx = sin(v.heading)
            val rz = cos(v.heading)
            damageNpc(npc, abs(v.speed) * 8.5f, rx, rz)
            if (v.driverRole == DriverRole.PLAYER) audio.playImpact()
          }
        }
      }

      // Vehicle-to-Vehicle collision
      for (other in vehicles) {
        if (other.id <= v.id || other.isDestroyed) continue
        val dx = other.x - v.x
        val dz = other.z - v.z
        val minSep = v.type.halfWidth + other.type.halfWidth + 0.5f
        val d = hypot(dx, dz)
        if (d in 0.01f..minSep) {
          val push = (minSep - d) * 0.5f
          val nx = dx / d
          val nz = dz / d
          v.x -= nx * push
          v.z -= nz * push
          other.x += nx * push
          other.z += nz * push
          val relSpeed = abs(v.speed - other.speed)
          if (relSpeed > 8f) {
            if (v.driverRole == DriverRole.PLAYER || other.driverRole == DriverRole.PLAYER) {
              audio.playImpact()
              cameraShake = maxOf(cameraShake, 0.30f)
            }
            if (v.driverRole == DriverRole.PLAYER && other.type.isPolice) {
              addWantedHeat(22f)
            }
            damageVehicle(v, relSpeed * 0.8f)
            damageVehicle(other, relSpeed * 0.8f)
          }
          v.speed *= 0.45f
          other.speed *= 0.45f
        }
      }
    }
  }

  private fun updateNpcs(dt: Float) {
    val iter = npcs.iterator()
    var copCanSeePlayer = false
    var copCloseForBust = false

    while (iter.hasNext()) {
      val npc = iter.next()
      npc.hitFlashTimer = (npc.hitFlashTimer - dt).coerceAtLeast(0f)

      if (npc.behavior == NpcBehavior.DEAD) {
        npc.deadTimer -= dt
        if (npc.deadTimer <= 0f) {
          iter.remove()
        }
        continue
      }

      // Apply knockback velocity decay
      if (abs(npc.vx) > 0.05f || abs(npc.vz) > 0.05f) {
        val res = CityMap.resolveCircleCollision(npc.x + npc.vx * dt, npc.z + npc.vz * dt, 0.45f)
        npc.x = res.first
        npc.z = res.second
        npc.vx *= (1f - 6f * dt).coerceAtLeast(0f)
        npc.vz *= (1f - 6f * dt).coerceAtLeast(0f)
      }

      val distToPlayer = hypot(playerX - npc.x, playerZ - npc.z)

      if (npc.role == NpcRole.CIVILIAN) {
        if (npc.behavior == NpcBehavior.FLEE) {
          val dx = npc.x - playerX
          val dz = npc.z - playerZ
          val d = hypot(dx, dz).coerceAtLeast(0.01f)
          moveNpcToward(npc, npc.x + (dx / d) * 10f, npc.z + (dz / d) * 10f, 5.2f, dt)
          if (distToPlayer > 42f) {
            npc.behavior = NpcBehavior.WANDER
          }
        } else {
          if (hypot(npc.targetX - npc.x, npc.targetZ - npc.z) < 2.2f) {
            val next = CityMap.sidewalkWalkNodes[rng.nextInt(CityMap.sidewalkWalkNodes.size)]
            npc.targetX = next.x
            npc.targetZ = next.z
          }
          moveNpcToward(npc, npc.targetX, npc.targetZ, 2.3f, dt)
        }
      } else {
        // Police / SWAT AI
        if (wantedStars == 0) {
          iter.remove()
          continue
        }
        if (distToPlayer < 32f) copCanSeePlayer = true
        if (distToPlayer < 2.5f) copCloseForBust = true

        val attackRange = if (npc.role == NpcRole.SWAT) 24f else 17f
        val moveSpeed = if (npc.role == NpcRole.SWAT) 5.1f else 4.6f

        if (distToPlayer > attackRange * 0.72f) {
          moveNpcToward(npc, playerX, playerZ, moveSpeed, dt)
        } else {
          npc.heading = atan2(playerX - npc.x, playerZ - npc.z)
          npc.walkPhase = 0f
        }

        npc.fireCooldown -= dt
        if (distToPlayer <= attackRange && npc.fireCooldown <= 0f) {
          val dx = (playerX - npc.x) / distToPlayer.coerceAtLeast(0.01f)
          val dz = (playerZ - npc.z) / distToPlayer.coerceAtLeast(0.01f)
          val wallDist = CityMap.raycastBuildings(npc.x, npc.z, dx, dz, distToPlayer)
          if (wallDist >= distToPlayer - 0.6f) {
            npc.fireCooldown = if (npc.role == NpcRole.SWAT) 0.42f else 0.85f
            val hitChance = if (playerVehicleId != null) 0.35f else 0.52f
            if (rng.nextFloat() < hitChance) {
              applyPlayerDamage(if (npc.role == NpcRole.SWAT) 9f else 6f)
            }
            tracers.add(
              ProjectileTracer(
                x1 = npc.x + dx * 0.6f,
                y1 = 1.32f,
                z1 = npc.z + dz * 0.6f,
                x2 = playerX,
                y2 = 1.15f,
                z2 = playerZ,
                ttl = 0.08f,
                maxTtl = 0.08f,
                r = 0.25f,
                g = 0.72f,
                b = 1.0f,
              )
            )
          }
        }
      }
    }

    // Keep civilian crowd populated around 20-22
    val civCount = npcs.count { it.role == NpcRole.CIVILIAN }
    if (civCount < 20) {
      spawnCivilian(nearPlayer = true)
    }

    // Police vision & busted check
    val activePoliceCars =
      vehicles.count { it.driverRole == DriverRole.POLICE_AI && hypot(it.x - playerX, it.z - playerZ) < 38f }
    if (copCanSeePlayer || activePoliceCars > 0) {
      timeSincePoliceSeen = 0f
    } else {
      timeSincePoliceSeen += dt
    }

    val playerSpeed =
      playerVehicleId?.let { vid -> abs(vehicles.firstOrNull { it.id == vid }?.speed ?: 0f) } ?: 0f
    if (wantedStars >= 1 && copCloseForBust && (playerVehicleId == null || playerSpeed < 1.3f)) {
      bustedProgress += dt
      if (bustedProgress >= 2.4f) {
        phase = GamePhase.BUSTED
        audio.inVehicle = false
        audio.sirenActive = false
      }
    } else {
      bustedProgress = (bustedProgress - dt * 1.5f).coerceAtLeast(0f)
    }
  }

  private fun moveNpcToward(npc: NpcEntity, tx: Float, tz: Float, speed: Float, dt: Float) {
    val dx = tx - npc.x
    val dz = tz - npc.z
    val d = hypot(dx, dz)
    if (d < 0.05f) return
    val nx = dx / d
    val nz = dz / d
    val res = CityMap.resolveCircleCollision(npc.x + nx * speed * dt, npc.z + nz * speed * dt, 0.45f)
    npc.x = res.first
    npc.z = res.second
    npc.heading = atan2(nx, nz)
    npc.walkPhase += dt * speed * 1.9f
    if (res.third && npc.role == NpcRole.CIVILIAN) {
      val next = CityMap.sidewalkWalkNodes[rng.nextInt(CityMap.sidewalkWalkNodes.size)]
      npc.targetX = next.x
      npc.targetZ = next.z
    }
  }

  private fun addWantedHeat(amount: Float) {
    wantedHeat = (wantedHeat + amount).coerceAtMost(360f)
    timeSincePoliceSeen = 0f
    recomputeWantedStars()
  }

  private fun recomputeWantedStars() {
    wantedStars =
      when {
        wantedHeat >= 290f -> 5
        wantedHeat >= 195f -> 4
        wantedHeat >= 115f -> 3
        wantedHeat >= 55f -> 2
        wantedHeat >= 18f -> 1
        else -> 0
      }
    if (wantedStars > highestWanted) {
      highestWanted = wantedStars
      saveDirty = true
    }
  }

  private fun updatePoliceDirector(dt: Float) {
    if (wantedStars == 0) {
      wantedHeat = (wantedHeat - dt * 6f).coerceAtLeast(0f)
      return
    }

    if (timeSincePoliceSeen > 7.5f) {
      wantedHeat = (wantedHeat - dt * 16f).coerceAtLeast(0f)
      recomputeWantedStars()
      if (wantedStars == 0) {
        vehicles.forEach {
          if (it.driverRole == DriverRole.POLICE_AI) {
            it.driverRole = DriverRole.NONE
            it.sirenActive = false
          }
        }
        return
      }
    }

    val desiredCops =
      when (wantedStars) {
        1 -> 2
        2 -> 3
        3 -> 4
        4 -> 5
        else -> 6
      }
    val desiredSwat = if (wantedStars >= 4) wantedStars - 2 else 0
    val desiredCruisers =
      when (wantedStars) {
        1 -> 0
        2 -> 1
        3 -> 2
        4 -> 2
        else -> 3
      }

    val curCops = npcs.count { it.role == NpcRole.PATROL_COP && it.behavior != NpcBehavior.DEAD }
    val curSwat = npcs.count { it.role == NpcRole.SWAT && it.behavior != NpcBehavior.DEAD }
    val curCruisers = vehicles.count { it.driverRole == DriverRole.POLICE_AI && !it.isDestroyed }

    if (curCops < desiredCops) {
      spawnPoliceUnit(NpcRole.PATROL_COP)
    }
    if (curSwat < desiredSwat) {
      spawnPoliceUnit(NpcRole.SWAT)
    }
    if (curCruisers < desiredCruisers) {
      spawnPoliceVehicle(useSwat = wantedStars >= 4 && curCruisers >= 1)
    }
  }

  private fun spawnPoliceUnit(role: NpcRole) {
    val angle = rng.nextFloat() * 6.28f
    val dist = 24f + rng.nextFloat() * 14f
    val res =
      CityMap.resolveCircleCollision(
        playerX + cos(angle) * dist,
        playerZ + sin(angle) * dist,
        0.5f,
      )
    val isSwat = role == NpcRole.SWAT
    npcs.add(
      NpcEntity(
        id = nextEntityId++,
        role = role,
        x = res.first,
        z = res.second,
        heading = 0f,
        hp = if (isSwat) 120f else 75f,
        maxHp = if (isSwat) 120f else 75f,
        behavior = NpcBehavior.PURSUE,
        shirtR = if (isSwat) 0.10f else 0.14f,
        shirtG = if (isSwat) 0.14f else 0.28f,
        shirtB = if (isSwat) 0.22f else 0.65f,
        pantsR = 0.08f,
        pantsG = 0.10f,
        pantsB = 0.18f,
      )
    )
  }

  private fun spawnPoliceVehicle(useSwat: Boolean) {
    // Pick a road intersection 40-75m away
    val candidate =
      CityMap.roadNodes
        .filter { hypot(it.x - playerX, it.z - playerZ) in 32f..85f }
        .randomOrNull(rng) ?: return
    val type = if (useSwat) VehicleType.SWAT_ENFORCER else VehicleType.POLICE_CRUISER
    vehicles.add(
      VehicleEntity(
        id = nextEntityId++,
        type = type,
        x = candidate.x,
        z = candidate.z,
        heading = atan2(playerX - candidate.x, playerZ - candidate.z),
        bodyR = if (useSwat) 0.12f else 0.95f,
        bodyG = if (useSwat) 0.15f else 0.96f,
        bodyB = if (useSwat) 0.22f else 1.0f,
        sirenActive = true,
        driverRole = DriverRole.POLICE_AI,
      )
    )
  }

  private fun applyPlayerDamage(dmg: Float) {
    var rem = dmg
    if (playerArmor > 0f) {
      val absorbed = min(playerArmor, rem * 0.75f)
      playerArmor -= absorbed
      rem -= absorbed
    }
    playerHp = (playerHp - rem).coerceAtLeast(0f)
    cameraShake = maxOf(cameraShake, 0.24f)
    if (playerHp <= 0f) {
      phase = GamePhase.WASTED
      audio.inVehicle = false
      audio.sirenActive = false
    }
  }

  private fun updateEffectsAndPickups(dt: Float) {
    // Blood particles gravity & ground settle
    val bpIter = bloodParticles.iterator()
    while (bpIter.hasNext()) {
      val p = bpIter.next()
      p.ttl -= dt
      if (p.ttl <= 0f) {
        bpIter.remove()
        continue
      }
      p.vy -= 12f * dt
      p.x += p.vx * dt
      p.y += p.vy * dt
      p.z += p.vz * dt
      if (p.y <= 0.05f) {
        p.y = 0.05f
        p.vx *= 0.4f
        p.vz *= 0.4f
        p.vy = 0f
      }
    }

    // Expanding ground blood pools
    val bdIter = bloodDecals.iterator()
    while (bdIter.hasNext()) {
      val d = bdIter.next()
      if (d.radius < d.targetRadius) {
        d.radius = min(d.targetRadius, d.radius + dt * 1.4f)
      }
    }

    // Cash pickups (magnetic pull + collection)
    val cIter = cashPickups.iterator()
    while (cIter.hasNext()) {
      val c = cIter.next()
      c.bobPhase += dt * 3.8f
      c.ttl -= dt
      if (c.ttl <= 0f) {
        cIter.remove()
        continue
      }
      val dx = playerX - c.x
      val dz = playerZ - c.z
      val dist = hypot(dx, dz)
      if (dist < 4.5f) {
        c.x += (dx / dist.coerceAtLeast(0.01f)) * 9.5f * dt
        c.z += (dz / dist.coerceAtLeast(0.01f)) * 9.5f * dt
      }
      if (dist < 1.65f) {
        cash += c.amount
        totalEarned += c.amount
        popupCounter++
        latestCashPopup = CashPopup(popupCounter, c.amount)
        audio.playCashPickup()
        saveDirty = true
        cIter.remove()
      }
    }

    // Bullet tracers
    val tIter = tracers.iterator()
    while (tIter.hasNext()) {
      val t = tIter.next()
      t.ttl -= dt
      if (t.ttl <= 0f) tIter.remove()
    }

    // Explosions
    val eIter = explosions.iterator()
    while (eIter.hasNext()) {
      val e = eIter.next()
      e.age += dt
      if (e.age >= e.duration) eIter.remove()
    }
  }

  private fun alertNearbyCivilians(ox: Float, oz: Float, radius: Float) {
    for (npc in npcs) {
      if (npc.role == NpcRole.CIVILIAN && npc.behavior != NpcBehavior.DEAD) {
        if (hypot(npc.x - ox, npc.z - oz) < radius) {
          npc.behavior = NpcBehavior.FLEE
        }
      }
    }
  }

  private fun updateAudioState() {
    val activeVeh = playerVehicleId?.let { id -> vehicles.firstOrNull { it.id == id } }
    audio.inVehicle = activeVeh != null
    audio.vehicleSpeedNormalized =
      if (activeVeh != null) abs(activeVeh.speed) / activeVeh.type.maxSpeed else 0f
    val anySiren =
      vehicles.any {
        !it.isDestroyed && it.sirenActive && hypot(it.x - playerX, it.z - playerZ) < 60f
      }
    audio.sirenActive = anySiren
  }

  private fun rayCircleIntersect(
    ox: Float,
    oz: Float,
    dx: Float,
    dz: Float,
    cx: Float,
    cz: Float,
    r: Float,
  ): Float? {
    val fx = ox - cx
    val fz = oz - cz
    val b = 2f * (fx * dx + fz * dz)
    val c = fx * fx + fz * fz - r * r
    val disc = b * b - 4f * c
    if (disc < 0f) return null
    val sqrtD = kotlin.math.sqrt(disc)
    val t1 = (-b - sqrtD) * 0.5f
    val t2 = (-b + sqrtD) * 0.5f
    return when {
      t1 >= 0f -> t1
      t2 >= 0f -> t2
      else -> null
    }
  }

  fun getRenderSnapshot(): RenderSnapshot {
    synchronized(lock) {
      return RenderSnapshot(
        timeSec = simTime,
        playerX = playerX,
        playerZ = playerZ,
        playerHeading = playerHeading,
        playerWalkPhase = playerWalkPhase,
        playerAiming = isFiringInput || muzzleFlashTimer > 0f,
        playerInVehicleId = playerVehicleId,
        activeWeapon = inventory[activeWeaponIndex].type,
        muzzleFlashTimer = muzzleFlashTimer,
        cameraYaw = cameraYaw,
        cameraPitch = cameraPitch,
        cameraShake = cameraShake,
        waypointX = waypointX,
        waypointZ = waypointZ,
        vehicles = vehicles.map { it.copy() },
        npcs = npcs.map { it.copy() },
        bloodDecals = bloodDecals.map { it.copy() },
        bloodParticles = bloodParticles.map { it.copy() },
        cashPickups = cashPickups.map { it.copy() },
        tracers = tracers.map { it.copy() },
        explosions = explosions.map { it.copy() },
      )
    }
  }

  fun getHudState(): GameHudState {
    synchronized(lock) {
      val slot = inventory[activeWeaponIndex]
      val currentVeh = playerVehicleId?.let { vid -> vehicles.firstOrNull { it.id == vid } }
      val nearestVeh =
        if (currentVeh == null) {
          vehicles
            .filter { !it.isDestroyed }
            .minByOrNull { hypot(it.x - playerX, it.z - playerZ) }
            ?.takeIf { hypot(it.x - playerX, it.z - playerZ) <= 4.4f }
        } else null

      val blips = mutableListOf<RadarBlip>()
      blips.add(RadarBlip(CityMap.ARMORY_X, CityMap.ARMORY_Z, BlipKind.ARMORY))
      blips.add(RadarBlip(CityMap.CLINIC_X, CityMap.CLINIC_Z, BlipKind.CLINIC))
      waypointX?.let { wx -> waypointZ?.let { wz -> blips.add(RadarBlip(wx, wz, BlipKind.WAYPOINT)) } }
      for (v in vehicles) {
        if (v.isDestroyed || v.id == playerVehicleId) continue
        val kind = if (v.driverRole == DriverRole.POLICE_AI) BlipKind.POLICE else BlipKind.VEHICLE
        blips.add(RadarBlip(v.x, v.z, kind))
      }
      for (n in npcs) {
        if (n.behavior == NpcBehavior.DEAD) continue
        if (n.role != NpcRole.CIVILIAN) {
          blips.add(RadarBlip(n.x, n.z, BlipKind.POLICE))
        } else if (hypot(n.x - playerX, n.z - playerZ) < 42f) {
          blips.add(RadarBlip(n.x, n.z, BlipKind.CIVILIAN))
        }
      }
      for (c in cashPickups) {
        blips.add(RadarBlip(c.x, c.z, BlipKind.CASH))
      }

      val nearArmory =
        currentVeh == null &&
          hypot(playerX - CityMap.ARMORY_X, playerZ - CityMap.ARMORY_Z) <= CityMap.ARMORY_RADIUS + 0.8f

      return GameHudState(
        phase = phase,
        districtName = CityMap.districtAt(playerX, playerZ),
        health = playerHp.toInt(),
        maxHealth = 100,
        armor = playerArmor.toInt(),
        maxArmor = 100,
        cash = cash,
        wantedStars = wantedStars,
        wantedCoolingDown = wantedStars > 0 && timeSincePoliceSeen > 2.5f,
        activeWeapon = slot.type,
        clipAmmo = slot.clipAmmo,
        reserveAmmo = slot.reserveAmmo,
        isReloading = isReloading,
        inventory = inventory.toList(),
        inVehicle = currentVeh != null,
        vehicleName = currentVeh?.type?.label ?: "",
        vehicleSpeedMph = currentVeh?.let { (abs(it.speed) * 2.4f).toInt() } ?: 0,
        vehicleHealthPct = currentVeh?.let { (it.hp / it.type.maxHp).coerceIn(0f, 1f) } ?: 1f,
        isPoliceVehicle = currentVeh?.type?.isPolice == true,
        sirenOn = currentVeh?.sirenActive == true,
        canEnterVehicle = nearestVeh != null,
        nearVehicleName = nearestVeh?.type?.label ?: "",
        nearArmory = nearArmory,
        radioStation = radioStation,
        isMuted = isMuted,
        playerX = playerX,
        playerZ = playerZ,
        playerHeading = playerHeading,
        waypointX = waypointX,
        waypointZ = waypointZ,
        radarBlips = blips,
        totalEliminations = totalEliminations,
        totalEarned = totalEarned,
        highestWanted = highestWanted,
        latestCashPopup = latestCashPopup,
        lastPenaltyPaid = lastPenaltyPaid,
      )
    }
  }
}
