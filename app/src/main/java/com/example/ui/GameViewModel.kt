package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.SandboxSaveRepository
import com.example.game.audio.ViceAudioEngine
import com.example.game.engine.ViceGameEngine
import com.example.game.model.GameHudState
import com.example.game.model.GamePhase
import com.example.game.model.RadioStation
import com.example.game.model.WeaponType
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class GameViewModel(application: Application) : AndroidViewModel(application) {
  private val saveRepo = SandboxSaveRepository(application.applicationContext)
  val audioEngine = ViceAudioEngine()
  val gameEngine = ViceGameEngine(audioEngine)

  private val _hudState = MutableStateFlow(gameEngine.getHudState())
  val hudState: StateFlow<GameHudState> = _hudState.asStateFlow()

  private val _hasSavedProgress = MutableStateFlow(false)
  val hasSavedProgress: StateFlow<Boolean> = _hasSavedProgress.asStateFlow()

  private val _radioBannerStation = MutableStateFlow<RadioStation?>(null)
  val radioBannerStation: StateFlow<RadioStation?> = _radioBannerStation.asStateFlow()

  private val _showWeaponWheel = MutableStateFlow(false)
  val showWeaponWheel: StateFlow<Boolean> = _showWeaponWheel.asStateFlow()

  private var bannerJob: Job? = null

  init {
    gameEngine.setOnSaveNeeded { persistNow() }
    viewModelScope.launch {
      val loaded = saveRepo.load()
      _hasSavedProgress.value = loaded.hasSave
      gameEngine.applySavedState(loaded)
      refreshHud()
    }
  }

  fun startAudio() {
    audioEngine.start()
  }

  fun stopAudio() {
    audioEngine.stop()
  }

  fun refreshHud() {
    _hudState.value = gameEngine.getHudState()
  }

  fun enterCity() {
    gameEngine.setPhase(GamePhase.PLAYING)
    refreshHud()
  }

  fun pauseGame() {
    if (_hudState.value.phase == GamePhase.PLAYING) {
      gameEngine.setPhase(GamePhase.PAUSED)
      refreshHud()
    }
  }

  fun resumeGame() {
    _showWeaponWheel.value = false
    gameEngine.setPhase(GamePhase.PLAYING)
    refreshHud()
  }

  fun openArmory() {
    gameEngine.setPhase(GamePhase.ARMORY)
    refreshHud()
  }

  fun openWorldMap() {
    gameEngine.setPhase(GamePhase.MAP)
    refreshHud()
  }

  fun toggleWeaponWheel() {
    _showWeaponWheel.value = !_showWeaponWheel.value
  }

  fun selectWeapon(type: WeaponType) {
    gameEngine.selectWeapon(type)
    _showWeaponWheel.value = false
    refreshHud()
  }

  fun cycleWeapon() {
    gameEngine.cycleWeapon()
    refreshHud()
  }

  fun reloadWeapon() {
    gameEngine.triggerReload()
    refreshHud()
  }

  fun buyWeaponOrAmmo(type: WeaponType) {
    gameEngine.buyWeaponOrAmmo(type)
    refreshHud()
  }

  fun buyArmor() {
    gameEngine.buyArmor()
    refreshHud()
  }

  fun toggleEnterExitVehicle() {
    gameEngine.toggleEnterExitVehicle()
    refreshHud()
  }

  fun toggleSiren() {
    gameEngine.toggleSiren()
    refreshHud()
  }

  fun cycleRadio() {
    gameEngine.cycleRadioStation()
    refreshHud()
    val station = _hudState.value.radioStation
    _radioBannerStation.value = station
    bannerJob?.cancel()
    bannerJob =
      viewModelScope.launch {
        delay(2400)
        _radioBannerStation.value = null
      }
  }

  fun toggleMute() {
    gameEngine.toggleMute()
    refreshHud()
  }

  fun setWaypoint(x: Float?, z: Float?) {
    gameEngine.setWaypoint(x, z)
    refreshHud()
  }

  fun respawn() {
    gameEngine.respawnAfterFall()
    refreshHud()
  }

  fun resetSandbox() {
    viewModelScope.launch {
      val fresh = saveRepo.reset()
      _hasSavedProgress.value = false
      gameEngine.resetSandbox(fresh)
      refreshHud()
    }
  }

  fun persistNow() {
    val snap = gameEngine.exportSaveSnapshot()
    viewModelScope.launch {
      saveRepo.save(
        cash = snap.cash,
        armor = snap.armor,
        totalEliminations = snap.totalEliminations,
        totalEarned = snap.totalEarned,
        highestWanted = snap.highestWanted,
        radioStation = snap.radioStation,
        isMuted = snap.isMuted,
        inventory = snap.inventory,
      )
      _hasSavedProgress.value = true
    }
  }

  override fun onCleared() {
    super.onCleared()
    audioEngine.stop()
  }
}
