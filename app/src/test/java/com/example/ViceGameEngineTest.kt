package com.example

import com.example.game.audio.ViceAudioEngine
import com.example.game.engine.ViceGameEngine
import com.example.game.model.DriverRole
import com.example.game.model.GamePhase
import com.example.game.model.PlayableCharacter
import com.example.game.model.WeaponType
import com.example.game.world.CityMap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ViceGameEngineTest {

  @Test
  fun cityMap_collisionKeepsPlayerOutOfOceanAndBuildings() {
    val oceanResolved = CityMap.resolveCircleCollision(145f, 0f, 0.5f)
    assertTrue(oceanResolved.third)
    assertTrue(oceanResolved.first < CityMap.OCEAN_START_X)

    val b = CityMap.buildings.first()
    val cx = (b.minX + b.maxX) * 0.5f
    val cz = (b.minZ + b.maxZ) * 0.5f
    val buildingResolved = CityMap.resolveCircleCollision(cx, cz, 0.5f)
    assertTrue(buildingResolved.third)
  }

  @Test
  fun engine_switchCharacterBetweenJasonAndLucia() {
    val audio = ViceAudioEngine().apply { isMuted = true }
    val engine = ViceGameEngine(audio)
    assertEquals(PlayableCharacter.JASON, engine.getHudState().activeCharacter)

    engine.switchCharacter()
    assertEquals(PlayableCharacter.LUCIA, engine.getHudState().activeCharacter)

    engine.switchCharacter()
    assertEquals(PlayableCharacter.JASON, engine.getHudState().activeCharacter)
  }

  @Test
  fun engine_npcTrafficDrivesAndTimeOfDayAdvances() {
    val audio = ViceAudioEngine().apply { isMuted = true }
    val engine = ViceGameEngine(audio)
    engine.setPhase(GamePhase.PLAYING)

    val initialTraffic =
      engine.getRenderSnapshot().vehicles.filter { it.driverRole == DriverRole.CIVILIAN_AI }
    assertTrue(initialTraffic.isNotEmpty())
    val tracked = initialTraffic.first()
    val startX = tracked.x
    val startZ = tracked.z
    val startTime = engine.getHudState().timeOfDay

    repeat(60) { engine.step(0.016f) }

    val afterTraffic =
      engine.getRenderSnapshot().vehicles.first { it.id == tracked.id }
    assertTrue(afterTraffic.x != startX || afterTraffic.z != startZ)
    assertNotEquals(startTime, engine.getHudState().timeOfDay)
  }

  @Test
  fun engine_enterDriveAndExitVehicleWorks() {
    val audio = ViceAudioEngine().apply { isMuted = true }
    val engine = ViceGameEngine(audio)
    engine.setPhase(GamePhase.PLAYING)

    engine.moveStickX = 0.5f
    engine.moveStickY = -0.8f
    repeat(120) { engine.step(0.016f) }
    engine.moveStickX = 0f
    engine.moveStickY = 0f

    val hudBefore = engine.getHudState()
    if (hudBefore.canEnterVehicle) {
      engine.toggleEnterExitVehicle()
      assertTrue(engine.getHudState().inVehicle)
      engine.driveThrottle = 1f
      repeat(45) { engine.step(0.016f) }
      assertTrue(engine.getHudState().vehicleSpeedMph > 5)
      engine.driveThrottle = 0f
      engine.toggleEnterExitVehicle()
      assertFalse(engine.getHudState().inVehicle)
    }
  }

  @Test
  fun engine_armoryPurchasesAndNewWeapons() {
    val audio = ViceAudioEngine().apply { isMuted = true }
    val engine = ViceGameEngine(audio)
    engine.setPhase(GamePhase.PLAYING)

    assertEquals(350, engine.getHudState().cash)
    assertTrue(engine.buyWeaponOrAmmo(WeaponType.BAT))
    assertEquals(230, engine.getHudState().cash)
    assertEquals(WeaponType.BAT, engine.getHudState().activeWeapon)

    engine.selectWeapon(WeaponType.PISTOL)
    assertEquals(WeaponType.PISTOL, engine.getHudState().activeWeapon)
    assertTrue(engine.buyArmor())
    assertEquals(80, engine.getHudState().cash)
    assertFalse(engine.buyArmor())
  }

  @Test
  fun engine_weaponFiringSpawnsTracersAndConsumesAmmo() {
    val audio = ViceAudioEngine().apply { isMuted = true }
    val engine = ViceGameEngine(audio)
    engine.setPhase(GamePhase.PLAYING)

    val initialClip = engine.getHudState().clipAmmo
    engine.isFiringInput = true
    engine.step(0.016f)
    engine.isFiringInput = false

    assertEquals(initialClip - 1, engine.getHudState().clipAmmo)
    assertTrue(engine.getRenderSnapshot().tracers.isNotEmpty())
  }
}
