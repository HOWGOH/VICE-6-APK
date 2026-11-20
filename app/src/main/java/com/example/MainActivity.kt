package com.example

import android.opengl.GLSurfaceView
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.game.model.GamePhase
import com.example.game.render.ViceGLRenderer
import com.example.ui.GameViewModel
import com.example.ui.components.ViceHudOverlay
import com.example.ui.screens.PauseMenuModal
import com.example.ui.screens.TitleScreenOverlay
import com.example.ui.screens.ViceArmoryModal
import com.example.ui.screens.WastedBustedOverlay
import com.example.ui.screens.WorldMapModal
import com.example.ui.theme.ViceCoastTheme

class MainActivity : ComponentActivity() {
  private val viewModel: GameViewModel by viewModels()
  private var glSurfaceView: GLSurfaceView? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      ViceCoastTheme {
        ViceCoastRoot(
          viewModel = viewModel,
          onCreateGlView = { view -> glSurfaceView = view },
        )
      }
    }
  }

  override fun onResume() {
    super.onResume()
    glSurfaceView?.onResume()
    viewModel.startAudio()
  }

  override fun onPause() {
    super.onPause()
    glSurfaceView?.onPause()
    viewModel.persistNow()
    viewModel.stopAudio()
  }
}

@Composable
private fun ViceCoastRoot(
  viewModel: GameViewModel,
  onCreateGlView: (GLSurfaceView) -> Unit,
) {
  val hud by viewModel.hudState.collectAsStateWithLifecycle()
  val hasSave by viewModel.hasSavedProgress.collectAsStateWithLifecycle()
  val radioBanner by viewModel.radioBannerStation.collectAsStateWithLifecycle()
  val showWeaponWheel by viewModel.showWeaponWheel.collectAsStateWithLifecycle()

  Box(modifier = Modifier.fillMaxSize()) {
    // Full-screen OpenGL ES 3.0 3D Viewport
    AndroidView(
      factory = { context ->
        GLSurfaceView(context).apply {
          setEGLContextClientVersion(3)
          setRenderer(
            ViceGLRenderer(
              engine = viewModel.gameEngine,
              onFrameHudTick = { viewModel.refreshHud() },
            )
          )
          renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
          onCreateGlView(this)
        }
      },
      modifier = Modifier.fillMaxSize(),
    )

    when (hud.phase) {
      GamePhase.TITLE -> {
        TitleScreenOverlay(
          hud = hud,
          hasSavedProgress = hasSave,
          onEnterCity = { viewModel.enterCity() },
          onResetSandbox = { viewModel.resetSandbox() },
        )
      }
      GamePhase.PLAYING -> {
        ViceHudOverlay(
          hud = hud,
          engine = viewModel.gameEngine,
          radioBanner = radioBanner,
          showWeaponWheel = showWeaponWheel,
          onOpenMap = { viewModel.openWorldMap() },
          onOpenArmory = { viewModel.openArmory() },
          onPause = { viewModel.pauseGame() },
          onToggleMute = { viewModel.toggleMute() },
          onToggleWeaponWheel = { viewModel.toggleWeaponWheel() },
          onSelectWeapon = { viewModel.selectWeapon(it) },
          onCycleWeapon = { viewModel.cycleWeapon() },
          onReload = { viewModel.reloadWeapon() },
          onToggleVehicle = { viewModel.toggleEnterExitVehicle() },
          onCycleRadio = { viewModel.cycleRadio() },
          onToggleSiren = { viewModel.toggleSiren() },
        )
      }
      GamePhase.ARMORY -> {
        ViceArmoryModal(
          hud = hud,
          onBuyWeaponOrAmmo = { viewModel.buyWeaponOrAmmo(it) },
          onSelectWeapon = { viewModel.selectWeapon(it) },
          onBuyArmor = { viewModel.buyArmor() },
          onClose = { viewModel.resumeGame() },
        )
      }
      GamePhase.MAP -> {
        WorldMapModal(
          hud = hud,
          onSetWaypoint = { x, z -> viewModel.setWaypoint(x, z) },
          onClose = { viewModel.resumeGame() },
        )
      }
      GamePhase.PAUSED -> {
        PauseMenuModal(
          hud = hud,
          onResume = { viewModel.resumeGame() },
          onOpenMap = { viewModel.openWorldMap() },
          onResetSandbox = { viewModel.resetSandbox() },
        )
      }
      GamePhase.WASTED, GamePhase.BUSTED -> {
        WastedBustedOverlay(
          phase = hud.phase,
          penaltyPaid = hud.lastPenaltyPaid,
          onRespawn = { viewModel.respawn() },
        )
      }
    }
  }
}
