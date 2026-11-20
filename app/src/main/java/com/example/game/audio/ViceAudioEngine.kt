package com.example.game.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.example.game.model.RadioStation
import com.example.game.model.WeaponType
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

class ViceAudioEngine {
  private val sampleRate = 22050
  private var audioTrack: AudioTrack? = null
  @Volatile private var running = false
  private var thread: Thread? = null

  @Volatile var isMuted = false
  @Volatile var inVehicle = false
  @Volatile var vehicleSpeedNormalized = 0f
  @Volatile var sirenActive = false
  @Volatile var radioStation = RadioStation.WAVE_103

  private data class Voice(
    var age: Float = 0f,
    val duration: Float,
    val startFreq: Float,
    val endFreq: Float,
    val noiseMix: Float,
    val volume: Float,
    val harmonic: Boolean = false,
  )

  private val voices = CopyOnWriteArrayList<Voice>()

  fun start() {
    if (running) return
    running = true
    thread =
      Thread({ runLoop() }, "ViceAudioSynth").apply {
        priority = Thread.NORM_PRIORITY
        start()
      }
  }

  fun stop() {
    running = false
    try {
      thread?.join(300)
    } catch (_: InterruptedException) {}
    thread = null
    audioTrack?.run {
      try {
        pause()
        flush()
        release()
      } catch (_: Exception) {}
    }
    audioTrack = null
  }

  fun playWeapon(type: WeaponType) {
    if (isMuted) return
    when (type) {
      WeaponType.FISTS ->
        voices.add(Voice(0f, 0.11f, 160f, 55f, 0.45f, 0.45f))
      WeaponType.PISTOL ->
        voices.add(Voice(0f, 0.16f, 620f, 110f, 0.72f, 0.55f))
      WeaponType.SMG ->
        voices.add(Voice(0f, 0.09f, 780f, 180f, 0.68f, 0.42f))
      WeaponType.SHOTGUN -> {
        voices.add(Voice(0f, 0.30f, 290f, 55f, 0.85f, 0.75f))
        voices.add(Voice(0f, 0.12f, 900f, 220f, 0.70f, 0.40f))
      }
      WeaponType.CARBINE ->
        voices.add(Voice(0f, 0.14f, 680f, 140f, 0.65f, 0.50f))
    }
  }

  fun playCashPickup() {
    if (isMuted) return
    voices.add(Voice(0f, 0.09f, 880f, 1320f, 0.0f, 0.35f, harmonic = true))
    voices.add(Voice(-0.06f, 0.14f, 1320f, 1760f, 0.0f, 0.35f, harmonic = true))
  }

  fun playImpact() {
    if (isMuted) return
    voices.add(Voice(0f, 0.18f, 150f, 45f, 0.65f, 0.48f))
  }

  fun playExplosion() {
    if (isMuted) return
    voices.add(Voice(0f, 0.65f, 140f, 28f, 0.92f, 0.85f))
  }

  fun playUiConfirm() {
    if (isMuted) return
    voices.add(Voice(0f, 0.10f, 520f, 780f, 0.0f, 0.25f, harmonic = true))
  }

  private fun runLoop() {
    val minBuf =
      AudioTrack.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_OUT_MONO,
        AudioFormat.ENCODING_PCM_16BIT,
      )
    val chunkSamples = 512
    val track =
      try {
        AudioTrack.Builder()
          .setAudioAttributes(
            AudioAttributes.Builder()
              .setUsage(AudioAttributes.USAGE_GAME)
              .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
              .build()
          )
          .setAudioFormat(
            AudioFormat.Builder()
              .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
              .setSampleRate(sampleRate)
              .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
              .build()
          )
          .setBufferSizeInBytes(maxOf(minBuf, chunkSamples * 4))
          .setTransferMode(AudioTrack.MODE_STREAM)
          .build()
      } catch (_: Exception) {
        return
      }
    audioTrack = track
    try {
      track.play()
    } catch (_: Exception) {
      return
    }

    val buffer = ShortArray(chunkSamples)
    val dt = 1f / sampleRate
    var enginePhase = 0f
    var sirenPhase = 0f
    var radioTime = 0f
    var radioBassPhase = 0f
    var radioLeadPhase = 0f
    val rng = Random(42)

    val waveNotes = floatArrayOf(110f, 110f, 130.81f, 146.83f, 164.81f, 146.83f, 130.81f, 98f)
    val espNotes = floatArrayOf(130.81f, 164.81f, 196f, 174.61f, 146.83f, 196f, 220f, 164.81f)
    val vcprNotes = floatArrayOf(98f, 123.47f, 146.83f, 123.47f, 110f, 146.83f, 164.81f, 130.81f)

    while (running) {
      val muted = isMuted
      val activeInCar = inVehicle
      val speedNorm = vehicleSpeedNormalized.coerceIn(0f, 1.2f)
      val siren = sirenActive
      val station = radioStation

      for (i in 0 until chunkSamples) {
        if (muted) {
          buffer[i] = 0
          continue
        }
        var sample = 0f

        // 1. Triggered SFX Voices
        if (voices.isNotEmpty()) {
          val iter = voices.iterator()
          while (iter.hasNext()) {
            val v = iter.next()
            v.age += dt
            if (v.age >= v.duration) {
              voices.remove(v)
              continue
            }
            if (v.age < 0f) continue
            val t = v.age / v.duration
            val env = (1f - t) * (1f - t)
            val freq = v.startFreq + (v.endFreq - v.startFreq) * t
            val osc = sin(2.0 * PI * freq * v.age).toFloat()
            val harm = if (v.harmonic) sin(4.0 * PI * freq * v.age).toFloat() * 0.4f else 0f
            val noise = (rng.nextFloat() * 2f - 1f) * v.noiseMix
            val tone = (osc + harm) * (1f - v.noiseMix)
            sample += (tone + noise) * env * v.volume
          }
        }

        // 2. Engine Hum when driving
        if (activeInCar) {
          val engFreq = 46f + speedNorm * 95f
          enginePhase += engFreq * dt
          if (enginePhase > 1f) enginePhase -= 1f
          val saw = (enginePhase * 2f - 1f)
          val sub = sin(2.0 * PI * enginePhase).toFloat()
          sample += (saw * 0.35f + sub * 0.65f) * (0.06f + speedNorm * 0.08f)
        }

        // 3. Police Siren wail
        if (siren) {
          val wail = 0.5f + 0.5f * sin(2.0 * PI * 1.45 * radioTime).toFloat()
          val sirenFreq = 590f + wail * 360f
          sirenPhase += sirenFreq * dt
          if (sirenPhase > 1f) sirenPhase -= 1f
          val sq = if (sirenPhase < 0.5f) 1f else -1f
          sample += sq * 0.065f
        }

        // 4. Procedural Vice Car Radio Stations (active while driving)
        radioTime += dt
        if (activeInCar && station != RadioStation.OFF) {
          val bpm =
            when (station) {
              RadioStation.WAVE_103 -> 118f
              RadioStation.ESPANTOSO -> 124f
              RadioStation.VCPR_PULSE -> 104f
              RadioStation.OFF -> 100f
            }
          val beatSec = 60f / bpm
          val step = (radioTime / (beatSec * 0.5f)).toInt()
          val stepFrac = (radioTime % (beatSec * 0.5f)) / (beatSec * 0.5f)
          val notes =
            when (station) {
              RadioStation.WAVE_103 -> waveNotes
              RadioStation.ESPANTOSO -> espNotes
              RadioStation.VCPR_PULSE -> vcprNotes
              RadioStation.OFF -> waveNotes
            }
          val bassFreq = notes[step % notes.size]
          val leadFreq = bassFreq * if (step % 4 == 2) 3f else 2f

          radioBassPhase += bassFreq * dt
          if (radioBassPhase > 1f) radioBassPhase -= 1f
          radioLeadPhase += leadFreq * dt
          if (radioLeadPhase > 1f) radioLeadPhase -= 1f

          val bassEnv = (1f - stepFrac * 0.65f)
          val bassWave = (radioBassPhase * 2f - 1f) * 0.055f * bassEnv
          val leadEnv = if (step % 2 == 0) (1f - stepFrac) else 0.25f * (1f - stepFrac)
          val leadWave = sin(2.0 * PI * radioLeadPhase).toFloat() * 0.045f * leadEnv
          // Kick on quarter beats
          val beatFrac = (radioTime % beatSec) / beatSec
          val kick =
            if (beatFrac < 0.14f) {
              sin(2.0 * PI * (120f - beatFrac * 520f) * beatFrac).toFloat() *
                (1f - beatFrac / 0.14f) *
                0.09f
            } else {
              0f
            }
          sample += bassWave + leadWave + kick
        }

        val clamped = (sample * 0.85f).coerceIn(-0.98f, 0.98f)
        buffer[i] = (clamped * Short.MAX_VALUE).toInt().toShort()
      }

      try {
        track.write(buffer, 0, chunkSamples)
      } catch (_: Exception) {
        break
      }
    }
  }
}
