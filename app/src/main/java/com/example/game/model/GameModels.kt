package com.example.game.model

import androidx.compose.runtime.Immutable

enum class PlayableCharacter(
  val displayName: String,
  val shortTag: String,
  val sprintMultiplier: Float,
  val accentHex: Long,
) {
  JASON("Jason Duval", "JASON", 1.00f, 0xFF00F0FF),
  LUCIA("Lucia Caminos", "LUCIA", 1.10f, 0xFFFF2E93),
}

enum class WeaponType(
  val id: String,
  val displayName: String,
  val shortLabel: String,
  val damage: Float,
  val range: Float,
  val fireIntervalSec: Float,
  val clipSize: Int,
  val reserveCap: Int,
  val reloadSec: Float,
  val pellets: Int,
  val spreadRad: Float,
  val unlockCost: Int,
  val ammoPackCost: Int,
  val ammoPackAmount: Int,
  val accentHex: Long,
  val isMelee: Boolean = false,
) {
  FISTS(
    id = "fists",
    displayName = "Brawl Fists",
    shortLabel = "FISTS",
    damage = 34f,
    range = 2.6f,
    fireIntervalSec = 0.40f,
    clipSize = 0,
    reserveCap = 0,
    reloadSec = 0f,
    pellets = 1,
    spreadRad = 0.15f,
    unlockCost = 0,
    ammoPackCost = 0,
    ammoPackAmount = 0,
    accentHex = 0xFFFFB830,
    isMelee = true,
  ),
  BAT(
    id = "bat",
    displayName = "Vice Slugger Bat",
    shortLabel = "BAT",
    damage = 58f,
    range = 3.2f,
    fireIntervalSec = 0.50f,
    clipSize = 0,
    reserveCap = 0,
    reloadSec = 0f,
    pellets = 1,
    spreadRad = 0.25f,
    unlockCost = 120,
    ammoPackCost = 0,
    ammoPackAmount = 0,
    accentHex = 0xFFFFB830,
    isMelee = true,
  ),
  PISTOL(
    id = "pistol",
    displayName = "9mm Vice Pistol",
    shortLabel = "9MM",
    damage = 36f,
    range = 42f,
    fireIntervalSec = 0.28f,
    clipSize = 12,
    reserveCap = 120,
    reloadSec = 1.05f,
    pellets = 1,
    spreadRad = 0.03f,
    unlockCost = 0,
    ammoPackCost = 40,
    ammoPackAmount = 24,
    accentHex = 0xFF00F0FF,
  ),
  DUAL_PISTOLS(
    id = "dual_pistols",
    displayName = "Dual Vice Pistols",
    shortLabel = "DUAL",
    damage = 34f,
    range = 44f,
    fireIntervalSec = 0.14f,
    clipSize = 24,
    reserveCap = 192,
    reloadSec = 1.30f,
    pellets = 1,
    spreadRad = 0.055f,
    unlockCost = 360,
    ammoPackCost = 65,
    ammoPackAmount = 48,
    accentHex = 0xFFFF2E93,
  ),
  SMG(
    id = "smg",
    displayName = "Micro Neon SMG",
    shortLabel = "SMG",
    damage = 22f,
    range = 38f,
    fireIntervalSec = 0.095f,
    clipSize = 30,
    reserveCap = 240,
    reloadSec = 1.30f,
    pellets = 1,
    spreadRad = 0.08f,
    unlockCost = 250,
    ammoPackCost = 75,
    ammoPackAmount = 60,
    accentHex = 0xFFFF2E93,
  ),
  SHOTGUN(
    id = "shotgun",
    displayName = "Coast Pump Shotgun",
    shortLabel = "12G",
    damage = 25f,
    range = 24f,
    fireIntervalSec = 0.75f,
    clipSize = 6,
    reserveCap = 48,
    reloadSec = 1.50f,
    pellets = 5,
    spreadRad = 0.18f,
    unlockCost = 450,
    ammoPackCost = 90,
    ammoPackAmount = 12,
    accentHex = 0xFFFFB830,
  ),
  CARBINE(
    id = "carbine",
    displayName = "Tactical Carbine",
    shortLabel = "AR-15",
    damage = 42f,
    range = 64f,
    fireIntervalSec = 0.13f,
    clipSize = 28,
    reserveCap = 210,
    reloadSec = 1.40f,
    pellets = 1,
    spreadRad = 0.025f,
    unlockCost = 750,
    ammoPackCost = 120,
    ammoPackAmount = 56,
    accentHex = 0xFF25E89B,
  ),
  SNIPER(
    id = "sniper",
    displayName = "Vice Long-Range Sniper",
    shortLabel = "SNIPER",
    damage = 145f,
    range = 115f,
    fireIntervalSec = 1.05f,
    clipSize = 5,
    reserveCap = 35,
    reloadSec = 1.85f,
    pellets = 1,
    spreadRad = 0.004f,
    unlockCost = 950,
    ammoPackCost = 140,
    ammoPackAmount = 10,
    accentHex = 0xFF00F0FF,
  ),
  RPG(
    id = "rpg",
    displayName = "RPG-7 Rocket Launcher",
    shortLabel = "RPG",
    damage = 160f,
    range = 85f,
    fireIntervalSec = 1.45f,
    clipSize = 1,
    reserveCap = 12,
    reloadSec = 1.95f,
    pellets = 1,
    spreadRad = 0.01f,
    unlockCost = 1450,
    ammoPackCost = 220,
    ammoPackAmount = 3,
    accentHex = 0xFFFF334B,
  ),
}

enum class VehicleType(
  val label: String,
  val maxSpeed: Float,
  val accel: Float,
  val turnRate: Float,
  val maxHp: Float,
  val halfWidth: Float,
  val halfLength: Float,
  val height: Float,
  val isPolice: Boolean,
) {
  SPORTS_COUPE("Cheetah Vice", 34f, 22f, 2.5f, 160f, 1.10f, 2.25f, 1.15f, false),
  MUSCLE_SEDAN("Sabre Coast", 28f, 18f, 2.2f, 190f, 1.12f, 2.35f, 1.28f, false),
  POLICE_CRUISER("VCPD Cruiser", 32f, 21f, 2.4f, 210f, 1.14f, 2.40f, 1.32f, true),
  SWAT_ENFORCER("SWAT Enforcer", 25f, 15f, 1.75f, 360f, 1.35f, 2.95f, 1.95f, true),
}

enum class RadioStation(val title: String, val tagline: String) {
  WAVE_103("WAVE 103.5", "Vice Synth Classics"),
  ESPANTOSO("RADIO ESPANTOSO", "Tropical Neon Bass"),
  VCPR_PULSE("VCPR 98.2", "Midnight Coastline Groove"),
  OFF("RADIO OFF", "Engine & Street Ambience"),
}

enum class GamePhase {
  TITLE,
  PLAYING,
  PAUSED,
  ARMORY,
  MAP,
  WASTED,
  BUSTED,
}

enum class NpcRole {
  CIVILIAN,
  PATROL_COP,
  SWAT,
}

enum class NpcBehavior {
  WANDER,
  FLEE,
  PURSUE,
  ATTACK,
  DEAD,
}

enum class NpcActivity {
  STROLLER,
  JOGGER,
  BEACHGOER,
  CHATTING,
}

data class BuildingBlock(
  val id: Int,
  val minX: Float,
  val maxX: Float,
  val minZ: Float,
  val maxZ: Float,
  val height: Float,
  val r: Float,
  val g: Float,
  val b: Float,
  val neonR: Float,
  val neonG: Float,
  val neonB: Float,
  val hasNeonBand: Boolean,
  val districtName: String,
)

data class PalmProp(val x: Float, val z: Float, val height: Float, val leanAngle: Float)

data class WeaponInventorySlot(
  val type: WeaponType,
  val unlocked: Boolean,
  val clipAmmo: Int,
  val reserveAmmo: Int,
)

enum class DriverRole {
  NONE,
  PLAYER,
  POLICE_AI,
  CIVILIAN_AI,
}

data class VehicleEntity(
  val id: Int,
  val type: VehicleType,
  var x: Float,
  var z: Float,
  var heading: Float,
  var speed: Float = 0f,
  var steerInput: Float = 0f,
  var hp: Float = type.maxHp,
  val bodyR: Float,
  val bodyG: Float,
  val bodyB: Float,
  var sirenActive: Boolean = false,
  var driverRole: DriverRole = DriverRole.NONE,
  var wheelSpin: Float = 0f,
  var aiCooldown: Float = 0f,
  var isDestroyed: Boolean = false,
  var smokeTimer: Float = 0f,
  var isTrafficCruiser: Boolean = false,
  var trafficAxis: Int = 0, // 0 = moving along Z on vertical road, 1 = moving along X on horizontal road
  var trafficDir: Int = 1,
  var trafficRoadCoord: Float = 0f,
  var turnCooldown: Float = 0f,
  var hornCooldown: Float = 0f,
  var isPanicking: Boolean = false,
  var exhaustTimer: Float = 0f,
  var skidTimer: Float = 0f,
)

data class NpcEntity(
  val id: Int,
  val role: NpcRole,
  var x: Float,
  var z: Float,
  var heading: Float,
  var vx: Float = 0f,
  var vz: Float = 0f,
  var hp: Float,
  val maxHp: Float,
  var behavior: NpcBehavior = NpcBehavior.WANDER,
  var activity: NpcActivity = NpcActivity.STROLLER,
  var walkPhase: Float = 0f,
  var targetX: Float = x,
  var targetZ: Float = z,
  var fireCooldown: Float = 0.6f,
  var hitFlashTimer: Float = 0f,
  var deadTimer: Float = 0f,
  var shirtR: Float,
  var shirtG: Float,
  var shirtB: Float,
  var pantsR: Float,
  var pantsG: Float,
  var pantsB: Float,
)

data class ProjectileTracer(
  var x1: Float,
  var y1: Float,
  var z1: Float,
  var x2: Float,
  var y2: Float,
  var z2: Float,
  var ttl: Float,
  val maxTtl: Float,
  val r: Float,
  val g: Float,
  val b: Float,
  val thickness: Float = 0.08f,
)

data class RocketProjectile(
  val id: Int,
  var x: Float,
  var y: Float,
  var z: Float,
  val vx: Float,
  val vy: Float,
  val vz: Float,
  val heading: Float,
  var ttl: Float = 2.5f,
  var smokeAccum: Float = 0f,
)

data class SmokeParticle(
  var x: Float,
  var y: Float,
  var z: Float,
  var vx: Float,
  var vy: Float,
  var vz: Float,
  var ttl: Float,
  val maxTtl: Float,
  val size: Float,
  val shade: Float,
)

data class TireSkidDecal(
  val x: Float,
  val z: Float,
  val headingDeg: Float,
  val length: Float,
  val alpha: Float = 0.65f,
)

data class BloodParticle(
  var x: Float,
  var y: Float,
  var z: Float,
  var vx: Float,
  var vy: Float,
  var vz: Float,
  var ttl: Float,
  val size: Float,
)

data class BloodDecal(
  val x: Float,
  val z: Float,
  var radius: Float,
  val targetRadius: Float,
  val rotation: Float,
  var alpha: Float = 0.92f,
)

data class CashPickup(
  val id: Int,
  var x: Float,
  var z: Float,
  val amount: Int,
  var bobPhase: Float = 0f,
  var ttl: Float = 60f,
)

data class ExplosionEffect(
  val x: Float,
  val y: Float,
  val z: Float,
  var age: Float = 0f,
  val duration: Float = 0.72f,
)

data class RadarBlip(
  val x: Float,
  val z: Float,
  val kind: BlipKind,
)

enum class BlipKind {
  CIVILIAN,
  POLICE,
  VEHICLE,
  CASH,
  ARMORY,
  CLINIC,
  WAYPOINT,
}

@Immutable
data class CashPopup(val id: Long, val amount: Int)

@Immutable
data class HitmarkerEvent(val id: Long, val isKill: Boolean)

@Immutable
data class KillFeedItem(val id: Long, val label: String, val bounty: Int)

@Immutable
data class GameHudState(
  val phase: GamePhase = GamePhase.TITLE,
  val activeCharacter: PlayableCharacter = PlayableCharacter.JASON,
  val districtName: String = "Ocean Beach",
  val timeOfDay: Float = 0.08f,
  val health: Int = 100,
  val maxHealth: Int = 100,
  val armor: Int = 0,
  val maxArmor: Int = 100,
  val cash: Int = 350,
  val wantedStars: Int = 0,
  val wantedCoolingDown: Boolean = false,
  val activeWeapon: WeaponType = WeaponType.PISTOL,
  val clipAmmo: Int = 12,
  val reserveAmmo: Int = 48,
  val isReloading: Boolean = false,
  val inventory: List<WeaponInventorySlot> = emptyList(),
  val inVehicle: Boolean = false,
  val vehicleName: String = "",
  val vehicleSpeedMph: Int = 0,
  val vehicleHealthPct: Float = 1f,
  val isPoliceVehicle: Boolean = false,
  val sirenOn: Boolean = false,
  val canEnterVehicle: Boolean = false,
  val nearVehicleName: String = "",
  val nearArmory: Boolean = false,
  val radioStation: RadioStation = RadioStation.WAVE_103,
  val isMuted: Boolean = false,
  val playerX: Float = 76f,
  val playerZ: Float = 10f,
  val playerHeading: Float = 0f,
  val waypointX: Float? = null,
  val waypointZ: Float? = null,
  val radarBlips: List<RadarBlip> = emptyList(),
  val totalEliminations: Int = 0,
  val totalEarned: Int = 0,
  val highestWanted: Int = 0,
  val latestCashPopup: CashPopup? = null,
  val latestHitmarker: HitmarkerEvent? = null,
  val killFeed: List<KillFeedItem> = emptyList(),
  val lastPenaltyPaid: Int = 0,
)
