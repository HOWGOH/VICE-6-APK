package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.game.model.RadioStation
import com.example.game.model.WeaponInventorySlot
import com.example.game.model.WeaponType
import kotlinx.coroutines.flow.first

private val Context.viceDataStore: DataStore<Preferences> by preferencesDataStore(name = "vice_coast_save")

data class SavedSandboxState(
  val hasSave: Boolean,
  val cash: Int,
  val armor: Int,
  val totalEliminations: Int,
  val totalEarned: Int,
  val highestWanted: Int,
  val radioStation: RadioStation,
  val isMuted: Boolean,
  val inventory: List<WeaponInventorySlot>,
)

class SandboxSaveRepository(private val context: Context) {

  private object Keys {
    val HAS_SAVE = booleanPreferencesKey("has_save")
    val CASH = intPreferencesKey("cash")
    val ARMOR = intPreferencesKey("armor")
    val KILLS = intPreferencesKey("total_kills")
    val EARNED = intPreferencesKey("total_earned")
    val HIGHEST_WANTED = intPreferencesKey("highest_wanted")
    val RADIO = stringPreferencesKey("radio_station")
    val MUTED = booleanPreferencesKey("is_muted")
    val UNLOCKED_WEAPONS = stringPreferencesKey("unlocked_weapons")

    fun clipKey(id: String) = intPreferencesKey("clip_$id")

    fun reserveKey(id: String) = intPreferencesKey("reserve_$id")
  }

  suspend fun load(): SavedSandboxState {
    val prefs = context.viceDataStore.data.first()
    val hasSave = prefs[Keys.HAS_SAVE] ?: false
    if (!hasSave) {
      return defaultState()
    }
    val unlockedSet =
      (prefs[Keys.UNLOCKED_WEAPONS] ?: "fists,pistol").split(",").filter { it.isNotBlank() }.toSet()
    val slots =
      WeaponType.entries.map { w ->
        val unlocked = w == WeaponType.FISTS || w == WeaponType.PISTOL || w.id in unlockedSet
        val defaultClip = if (w == WeaponType.PISTOL) w.clipSize else 0
        val defaultReserve = if (w == WeaponType.PISTOL) 48 else 0
        WeaponInventorySlot(
          type = w,
          unlocked = unlocked,
          clipAmmo = prefs[Keys.clipKey(w.id)] ?: defaultClip,
          reserveAmmo = prefs[Keys.reserveKey(w.id)] ?: defaultReserve,
        )
      }
    val radioName = prefs[Keys.RADIO] ?: RadioStation.WAVE_103.name
    val radio = RadioStation.entries.firstOrNull { it.name == radioName } ?: RadioStation.WAVE_103
    return SavedSandboxState(
      hasSave = true,
      cash = prefs[Keys.CASH] ?: 350,
      armor = prefs[Keys.ARMOR] ?: 25,
      totalEliminations = prefs[Keys.KILLS] ?: 0,
      totalEarned = prefs[Keys.EARNED] ?: 0,
      highestWanted = prefs[Keys.HIGHEST_WANTED] ?: 0,
      radioStation = radio,
      isMuted = prefs[Keys.MUTED] ?: false,
      inventory = slots,
    )
  }

  suspend fun save(
    cash: Int,
    armor: Int,
    totalEliminations: Int,
    totalEarned: Int,
    highestWanted: Int,
    radioStation: RadioStation,
    isMuted: Boolean,
    inventory: List<WeaponInventorySlot>,
  ) {
    context.viceDataStore.edit { prefs ->
      prefs[Keys.HAS_SAVE] = true
      prefs[Keys.CASH] = cash
      prefs[Keys.ARMOR] = armor
      prefs[Keys.KILLS] = totalEliminations
      prefs[Keys.EARNED] = totalEarned
      prefs[Keys.HIGHEST_WANTED] = highestWanted
      prefs[Keys.RADIO] = radioStation.name
      prefs[Keys.MUTED] = isMuted
      prefs[Keys.UNLOCKED_WEAPONS] =
        inventory.filter { it.unlocked }.joinToString(",") { it.type.id }
      inventory.forEach { slot ->
        prefs[Keys.clipKey(slot.type.id)] = slot.clipAmmo
        prefs[Keys.reserveKey(slot.type.id)] = slot.reserveAmmo
      }
    }
  }

  suspend fun reset(): SavedSandboxState {
    context.viceDataStore.edit { it.clear() }
    return defaultState()
  }

  companion object {
    fun defaultState(): SavedSandboxState {
      val slots =
        WeaponType.entries.map { w ->
          when (w) {
            WeaponType.FISTS -> WeaponInventorySlot(w, unlocked = true, clipAmmo = 0, reserveAmmo = 0)
            WeaponType.PISTOL ->
              WeaponInventorySlot(w, unlocked = true, clipAmmo = w.clipSize, reserveAmmo = 60)
            else -> WeaponInventorySlot(w, unlocked = false, clipAmmo = 0, reserveAmmo = 0)
          }
        }
      return SavedSandboxState(
        hasSave = false,
        cash = 350,
        armor = 25,
        totalEliminations = 0,
        totalEarned = 0,
        highestWanted = 0,
        radioStation = RadioStation.WAVE_103,
        isMuted = false,
        inventory = slots,
      )
    }
  }
}
