package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.LocalPolice
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.game.engine.ViceGameEngine
import com.example.game.model.BlipKind
import com.example.game.model.GameHudState
import com.example.game.model.RadioStation
import com.example.game.model.WeaponType
import com.example.game.world.CityMap
import com.example.ui.theme.ViceCrimson
import com.example.ui.theme.ViceCyan
import com.example.ui.theme.ViceGold
import com.example.ui.theme.ViceMagenta
import com.example.ui.theme.ViceMidnight
import com.example.ui.theme.ViceMint
import com.example.ui.theme.VicePoliceBlue
import com.example.ui.theme.ViceSurface
import com.example.ui.theme.ViceTextMuted
import com.example.ui.theme.ViceTextPrimary
import kotlinx.coroutines.delay
import kotlin.math.hypot
import kotlin.math.roundToInt

@Composable
fun ViceHudOverlay(
  hud: GameHudState,
  engine: ViceGameEngine,
  radioBanner: RadioStation?,
  showWeaponWheel: Boolean,
  onOpenMap: () -> Unit,
  onOpenArmory: () -> Unit,
  onPause: () -> Unit,
  onToggleMute: () -> Unit,
  onToggleWeaponWheel: () -> Unit,
  onSelectWeapon: (WeaponType) -> Unit,
  onCycleWeapon: () -> Unit,
  onReload: () -> Unit,
  onToggleVehicle: () -> Unit,
  onCycleRadio: () -> Unit,
  onToggleSiren: () -> Unit,
) {
  var policeBlink by remember { mutableStateOf(false) }
  LaunchedEffect(hud.wantedStars) {
    while (hud.wantedStars > 0) {
      policeBlink = !policeBlink
      delay(340)
    }
    policeBlink = false
  }

  var showCashGain by remember { mutableStateOf<Int?>(null) }
  LaunchedEffect(hud.latestCashPopup?.id) {
    val popup = hud.latestCashPopup ?: return@LaunchedEffect
    showCashGain = popup.amount
    delay(1100)
    showCashGain = null
  }

  var sprintLatch by remember { mutableStateOf(false) }

  BoxWithConstraints(
    modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)
  ) {
    val compactPortrait = maxWidth < 500.dp

    // Right-side invisible touch-drag camera look zone (behind interactive controls)
    Box(
      modifier =
        Modifier.fillMaxSize().pointerInput(Unit) {
          detectDragGestures { change, dragAmount ->
            change.consume()
            engine.orbitCamera(
              deltaYaw = -dragAmount.x * 0.0055f,
              deltaPitch = dragAmount.y * 0.0038f,
            )
          }
        }
    )

    // TOP ROW: Mini-map Radar (Left) + Status / Cash / Wanted / Weapon (Right)
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.Top,
    ) {
      Column(horizontalAlignment = Alignment.Start) {
        MiniMapRadar(
          hud = hud,
          policeBlink = policeBlink,
          sizeDp = if (compactPortrait) 96 else 112,
          onClick = onOpenMap,
        )
        Spacer(Modifier.height(4.dp))
        Surface(
          color = ViceMidnight.copy(alpha = 0.72f),
          shape = RoundedCornerShape(8.dp),
          modifier = Modifier.border(1.dp, ViceCyan.copy(alpha = 0.4f), RoundedCornerShape(8.dp)),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(Icons.Default.Map, contentDescription = "District", tint = ViceCyan, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(4.dp))
            Text(
              text = hud.districtName.uppercase(),
              style = MaterialTheme.typography.labelSmall,
              color = ViceTextPrimary,
            )
          }
        }
      }

      Column(horizontalAlignment = Alignment.End) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          // Cash Readout
          Surface(
            color = ViceSurface.copy(alpha = 0.84f),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.border(1.dp, ViceMint.copy(alpha = 0.65f), RoundedCornerShape(10.dp)),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = "$${hud.cash}",
                style = MaterialTheme.typography.headlineMedium,
                color = ViceMint,
              )
              if (showCashGain != null) {
                Spacer(Modifier.width(6.dp))
                Text(
                  text = "+$${showCashGain}",
                  style = MaterialTheme.typography.labelLarge,
                  color = ViceGold,
                )
              }
            }
          }

          IconButton(
            onClick = onToggleMute,
            modifier =
              Modifier.size(48.dp)
                .clip(CircleShape)
                .background(ViceSurface.copy(alpha = 0.80f))
                .border(1.dp, ViceTextMuted.copy(alpha = 0.3f), CircleShape)
                .testTag("mute_button"),
          ) {
            Icon(
              imageVector = if (hud.isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
              contentDescription = if (hud.isMuted) "Unmute audio" else "Mute audio",
              tint = if (hud.isMuted) ViceCrimson else ViceCyan,
            )
          }

          IconButton(
            onClick = onPause,
            modifier =
              Modifier.size(48.dp)
                .clip(CircleShape)
                .background(ViceSurface.copy(alpha = 0.80f))
                .border(1.dp, ViceMagenta.copy(alpha = 0.5f), CircleShape)
                .testTag("pause_button"),
          ) {
            Icon(Icons.Default.Pause, contentDescription = "Pause game", tint = ViceTextPrimary)
          }
        }

        Spacer(Modifier.height(6.dp))

        // Health + Armor + Wanted Stars
        Surface(
          color = ViceSurface.copy(alpha = 0.82f),
          shape = RoundedCornerShape(12.dp),
          modifier =
            Modifier.width(if (compactPortrait) 170.dp else 210.dp)
              .border(1.dp, ViceMagenta.copy(alpha = 0.35f), RoundedCornerShape(12.dp)),
        ) {
          Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Text("HP", style = MaterialTheme.typography.labelSmall, color = ViceMagenta, modifier = Modifier.width(24.dp))
              LinearProgressIndicator(
                progress = { (hud.health / 100f).coerceIn(0f, 1f) },
                modifier = Modifier.weight(1f).height(7.dp).clip(RoundedCornerShape(4.dp)),
                color = if (hud.health > 30) ViceMagenta else ViceCrimson,
                trackColor = ViceMidnight,
              )
              Spacer(Modifier.width(6.dp))
              Text("${hud.health}", style = MaterialTheme.typography.labelSmall, color = ViceTextPrimary)
            }
            if (hud.armor > 0) {
              Spacer(Modifier.height(4.dp))
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Shield, contentDescription = "Armor", tint = ViceCyan, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(11.dp))
                LinearProgressIndicator(
                  progress = { (hud.armor / 100f).coerceIn(0f, 1f) },
                  modifier = Modifier.weight(1f).height(5.dp).clip(RoundedCornerShape(4.dp)),
                  color = ViceCyan,
                  trackColor = ViceMidnight,
                )
                Spacer(Modifier.width(6.dp))
                Text("${hud.armor}", style = MaterialTheme.typography.labelSmall, color = ViceCyan)
              }
            }

            Spacer(Modifier.height(5.dp))

            // 5 Wanted Stars
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                for (star in 1..5) {
                  val active = star <= hud.wantedStars
                  val starTint =
                    when {
                      !active -> ViceTextMuted.copy(alpha = 0.28f)
                      hud.wantedCoolingDown && policeBlink -> ViceTextMuted.copy(alpha = 0.45f)
                      policeBlink -> VicePoliceBlue
                      else -> ViceCrimson
                    }
                  Icon(
                    imageVector = if (active) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = "Wanted star $star",
                    tint = starTint,
                    modifier = Modifier.size(18.dp),
                  )
                }
              }

              // Weapon badge (tap for quick wheel)
              Surface(
                color = ViceMidnight.copy(alpha = 0.85f),
                shape = RoundedCornerShape(6.dp),
                modifier =
                  Modifier.clickable(onClick = onToggleWeaponWheel)
                    .border(1.dp, Color(hud.activeWeapon.accentHex).copy(alpha = 0.7f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
                    .testTag("weapon_badge"),
              ) {
                val ammoText =
                  when {
                    hud.activeWeapon == WeaponType.FISTS -> "MELEE"
                    hud.isReloading -> "RELOAD"
                    else -> "${hud.clipAmmo}/${hud.reserveAmmo}"
                  }
                Text(
                  text = "${hud.activeWeapon.shortLabel} $ammoText",
                  style = MaterialTheme.typography.labelSmall,
                  color = Color(hud.activeWeapon.accentHex),
                )
              }
            }
          }
        }
      }
    }

    // Center-top Radio Toast or Armory Prompt
    Column(
      modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      AnimatedVisibility(
        visible = radioBanner != null,
        enter = fadeIn() + slideInVertically(),
        exit = fadeOut() + slideOutVertically(),
      ) {
        radioBanner?.let { st ->
          Surface(
            color = ViceSurface.copy(alpha = 0.92f),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.border(1.5.dp, ViceCyan, RoundedCornerShape(14.dp)),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(Icons.Default.Radio, contentDescription = "Radio", tint = ViceMagenta, modifier = Modifier.size(18.dp))
              Spacer(Modifier.width(8.dp))
              Column {
                Text(st.title, style = MaterialTheme.typography.labelLarge, color = ViceCyan)
                Text(st.tagline, style = MaterialTheme.typography.labelSmall, color = ViceTextMuted)
              }
            }
          }
        }
      }
    }

    // Contextual Armory Prompt
    if (hud.nearArmory) {
      Button(
        onClick = onOpenArmory,
        colors = ButtonDefaults.buttonColors(containerColor = ViceCyan, contentColor = ViceMidnight),
        shape = RoundedCornerShape(24.dp),
        modifier =
          Modifier.align(Alignment.Center)
            .offset(y = 58.dp)
            .height(48.dp)
            .testTag("open_armory_button"),
      ) {
        Icon(Icons.Default.ShoppingBag, contentDescription = "Armory")
        Spacer(Modifier.width(8.dp))
        Text("OPEN VICE ARMORY", fontWeight = FontWeight.Bold)
      }
    }

    // Quick Weapon Wheel Selector Popover
    if (showWeaponWheel && !hud.inVehicle) {
      Surface(
        color = ViceSurface.copy(alpha = 0.94f),
        shape = RoundedCornerShape(16.dp),
        modifier =
          Modifier.align(Alignment.Center)
            .border(1.5.dp, ViceMagenta, RoundedCornerShape(16.dp))
            .padding(4.dp),
      ) {
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
          Text("SELECT WEAPON", style = MaterialTheme.typography.headlineMedium, color = ViceGold)
          Spacer(Modifier.height(8.dp))
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            hud.inventory.filter { it.unlocked }.forEach { slot ->
              val selected = slot.type == hud.activeWeapon
              Surface(
                color = if (selected) ViceMagenta.copy(alpha = 0.30f) else ViceMidnight,
                shape = RoundedCornerShape(10.dp),
                modifier =
                  Modifier.size(width = 70.dp, height = 64.dp)
                    .border(
                      1.5.dp,
                      if (selected) ViceCyan else Color(slot.type.accentHex).copy(alpha = 0.5f),
                      RoundedCornerShape(10.dp),
                    )
                    .clickable { onSelectWeapon(slot.type) }
                    .testTag("wheel_${slot.type.id}"),
              ) {
                Column(
                  modifier = Modifier.fillMaxSize().padding(4.dp),
                  verticalArrangement = Arrangement.Center,
                  horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                  Text(slot.type.shortLabel, style = MaterialTheme.typography.labelLarge, color = ViceTextPrimary)
                  if (slot.type != WeaponType.FISTS) {
                    Text("${slot.clipAmmo}/${slot.reserveAmmo}", style = MaterialTheme.typography.labelSmall, color = ViceCyan)
                  }
                }
              }
            }
          }
        }
      }
    }

    // BOTTOM CONTROLS: On-Foot vs Driving
    if (hud.inVehicle) {
      DrivingControlsRow(
        hud = hud,
        engine = engine,
        onToggleVehicle = onToggleVehicle,
        onCycleRadio = onCycleRadio,
        onToggleSiren = onToggleSiren,
        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(14.dp),
      )
    } else {
      OnFootControlsRow(
        hud = hud,
        engine = engine,
        sprintLatch = sprintLatch,
        onToggleSprint = {
          sprintLatch = !sprintLatch
          engine.isSprinting = sprintLatch
        },
        onCycleWeapon = onCycleWeapon,
        onReload = onReload,
        onToggleVehicle = onToggleVehicle,
        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(14.dp),
      )
    }
  }
}

@Composable
private fun MiniMapRadar(
  hud: GameHudState,
  policeBlink: Boolean,
  sizeDp: Int,
  onClick: () -> Unit,
) {
  Box(
    modifier =
      Modifier.size(sizeDp.dp)
        .clip(CircleShape)
        .background(ViceMidnight.copy(alpha = 0.85f))
        .border(2.5.dp, Brush.sweepGradient(listOf(ViceCyan, ViceMagenta, ViceGold, ViceCyan)), CircleShape)
        .clickable(onClick = onClick)
        .testTag("minimap_button")
  ) {
    Canvas(modifier = Modifier.fillMaxSize().padding(4.dp)) {
      val cx = size.width / 2f
      val cy = size.height / 2f
      val rangeMeters = 90f
      val scale = (size.minDimension / 2f) / rangeMeters

      fun toRadar(wx: Float, wz: Float): Offset {
        val dx = (wx - hud.playerX) * scale
        val dz = (wz - hud.playerZ) * scale
        return Offset(cx + dx, cy - dz)
      }

      // Ocean region east of BEACH_START_X
      val oceanEdge = toRadar(CityMap.BEACH_START_X, hud.playerZ).x
      if (oceanEdge < size.width) {
        drawRect(
          color = Color(0xFF0A4F7A).copy(alpha = 0.65f),
          topLeft = Offset(oceanEdge.coerceAtLeast(0f), 0f),
          size = Size((size.width - oceanEdge).coerceAtLeast(0f), size.height),
        )
      }

      // Roads
      for (vr in CityMap.verticalRoads) {
        val rx = toRadar(vr.pos, hud.playerZ).x
        if (rx in 0f..size.width) {
          drawLine(Color(0xFF474060), Offset(rx, 0f), Offset(rx, size.height), strokeWidth = 4f)
        }
      }
      for (hz in CityMap.horizontalRoads) {
        val rz = toRadar(hud.playerX, hz.pos).y
        if (rz in 0f..size.height) {
          drawLine(Color(0xFF474060), Offset(0f, rz), Offset(size.width, rz), strokeWidth = 4f)
        }
      }

      // Blips
      for (b in hud.radarBlips) {
        val p = toRadar(b.x, b.z)
        if (hypot(p.x - cx, p.y - cy) > size.minDimension * 0.47f) continue
        when (b.kind) {
          BlipKind.CIVILIAN -> drawCircle(Color(0xFFBBB4D6), radius = 2.4f, center = p)
          BlipKind.VEHICLE -> drawRect(ViceGold, topLeft = Offset(p.x - 3f, p.y - 3f), size = Size(6f, 6f))
          BlipKind.CASH -> drawCircle(ViceMint, radius = 3.2f, center = p)
          BlipKind.POLICE ->
            drawCircle(if (policeBlink) VicePoliceBlue else ViceCrimson, radius = 4.6f, center = p)
          BlipKind.ARMORY -> drawCircle(ViceCyan, radius = 5.5f, center = p)
          BlipKind.CLINIC -> drawCircle(ViceMagenta, radius = 4.5f, center = p)
          BlipKind.WAYPOINT -> {
            drawLine(ViceCyan.copy(alpha = 0.55f), Offset(cx, cy), p, strokeWidth = 2f)
            drawCircle(ViceCyan, radius = 5.5f, center = p)
          }
        }
      }

      // Player heading arrow at center
      val deg = Math.toDegrees(hud.playerHeading.toDouble()).toFloat()
      rotate(degrees = deg, pivot = Offset(cx, cy)) {
        val arrow =
          Path().apply {
            moveTo(cx, cy - 8f)
            lineTo(cx + 5.5f, cy + 6f)
            lineTo(cx, cy + 3f)
            lineTo(cx - 5.5f, cy + 6f)
            close()
          }
        drawPath(arrow, color = ViceTextPrimary)
      }
    }
  }
}

@Composable
private fun OnFootControlsRow(
  hud: GameHudState,
  engine: ViceGameEngine,
  sprintLatch: Boolean,
  onToggleSprint: () -> Unit,
  onCycleWeapon: () -> Unit,
  onReload: () -> Unit,
  onToggleVehicle: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier = modifier,
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.Bottom,
  ) {
    // Left Analog Thumbstick
    VirtualJoystick(
      onMove = { sx, sy ->
        engine.moveStickX = sx
        engine.moveStickY = sy
      }
    )

    // Right Combat & Vehicle Cluster
    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
      if (hud.canEnterVehicle) {
        Button(
          onClick = onToggleVehicle,
          colors = ButtonDefaults.buttonColors(containerColor = ViceGold, contentColor = ViceMidnight),
          shape = RoundedCornerShape(20.dp),
          modifier = Modifier.height(48.dp).testTag("enter_vehicle_button"),
        ) {
          Icon(Icons.Default.DirectionsCar, contentDescription = "Enter vehicle")
          Spacer(Modifier.width(6.dp))
          Text("ENTER ${hud.nearVehicleName.uppercase()}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
      }

      Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        HudCircleButton(
          label = if (sprintLatch) "RUN" else "WALK",
          active = sprintLatch,
          accent = ViceMint,
          onClick = onToggleSprint,
          testTag = "sprint_button",
        ) {
          Icon(Icons.AutoMirrored.Filled.DirectionsRun, contentDescription = "Toggle sprint", tint = ViceTextPrimary)
        }

        HudCircleButton(
          label = "GUN",
          active = false,
          accent = ViceCyan,
          onClick = onCycleWeapon,
          testTag = "switch_weapon_button",
        ) {
          Icon(Icons.Default.SwapHoriz, contentDescription = "Switch weapon", tint = ViceCyan)
        }

        if (hud.activeWeapon != WeaponType.FISTS) {
          HudCircleButton(
            label = "RLD",
            active = hud.isReloading,
            accent = ViceGold,
            onClick = onReload,
            testTag = "reload_button",
          ) {
            Icon(Icons.Default.Refresh, contentDescription = "Reload weapon", tint = ViceGold)
          }
        }

        // Hold-to-fire tactical trigger
        HoldActionButton(
          label = if (hud.activeWeapon == WeaponType.FISTS) "PUNCH" else "FIRE",
          accent = ViceMagenta,
          testTag = "fire_button",
          sizeDp = 74,
          onPressChange = { pressed -> engine.isFiringInput = pressed },
        ) {
          Icon(Icons.Default.GpsFixed, contentDescription = "Fire weapon", tint = ViceTextPrimary, modifier = Modifier.size(30.dp))
        }
      }
    }
  }
}

@Composable
private fun DrivingControlsRow(
  hud: GameHudState,
  engine: ViceGameEngine,
  onToggleVehicle: () -> Unit,
  onCycleRadio: () -> Unit,
  onToggleSiren: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier = modifier,
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.Bottom,
  ) {
    // Left Steering Controls
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
      HoldActionButton(
        label = "LEFT",
        accent = ViceCyan,
        testTag = "steer_left",
        sizeDp = 64,
        onPressChange = { pressed -> engine.driveSteer = if (pressed) -1f else 0f },
      ) {
        Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "Steer left", tint = ViceTextPrimary, modifier = Modifier.size(34.dp))
      }
      HoldActionButton(
        label = "RIGHT",
        accent = ViceCyan,
        testTag = "steer_right",
        sizeDp = 64,
        onPressChange = { pressed -> engine.driveSteer = if (pressed) 1f else 0f },
      ) {
        Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Steer right", tint = ViceTextPrimary, modifier = Modifier.size(34.dp))
      }
    }

    // Right Driving Controls + Speedometer + Radio + Exit
    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        // Speed & Hull pill
        Surface(
          color = ViceSurface.copy(alpha = 0.86f),
          shape = RoundedCornerShape(10.dp),
          modifier = Modifier.border(1.dp, ViceCyan.copy(alpha = 0.5f), RoundedCornerShape(10.dp)),
        ) {
          Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp), horizontalAlignment = Alignment.End) {
            Text("${hud.vehicleSpeedMph} MPH", style = MaterialTheme.typography.headlineMedium, color = ViceCyan)
            Text(hud.vehicleName.uppercase(), style = MaterialTheme.typography.labelSmall, color = ViceTextMuted)
            LinearProgressIndicator(
              progress = { hud.vehicleHealthPct },
              modifier = Modifier.width(72.dp).height(4.dp).clip(RoundedCornerShape(2.dp)),
              color = if (hud.vehicleHealthPct > 0.35f) ViceMint else ViceCrimson,
              trackColor = ViceMidnight,
            )
          }
        }

        if (hud.isPoliceVehicle) {
          HudCircleButton(
            label = "SIREN",
            active = hud.sirenOn,
            accent = VicePoliceBlue,
            onClick = onToggleSiren,
            testTag = "siren_button",
          ) {
            Icon(Icons.Default.LocalPolice, contentDescription = "Toggle siren", tint = if (hud.sirenOn) ViceCyan else ViceTextMuted)
          }
        }

        HudCircleButton(
          label = "RADIO",
          active = hud.radioStation != RadioStation.OFF,
          accent = ViceMagenta,
          onClick = onCycleRadio,
          testTag = "radio_tuner",
        ) {
          Icon(Icons.Default.Radio, contentDescription = "Tune car radio", tint = ViceMagenta)
        }

        Button(
          onClick = onToggleVehicle,
          colors = ButtonDefaults.buttonColors(containerColor = ViceGold, contentColor = ViceMidnight),
          shape = RoundedCornerShape(18.dp),
          modifier = Modifier.height(48.dp).testTag("exit_vehicle_button"),
        ) {
          Text("EXIT", fontWeight = FontWeight.Bold)
        }
      }

      Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        HoldActionButton(
          label = "DRIFT",
          accent = ViceGold,
          testTag = "drive_drift",
          sizeDp = 54,
          onPressChange = { pressed -> engine.driveDrift = pressed },
        ) {
          Text("DRIFT", style = MaterialTheme.typography.labelSmall, color = ViceGold)
        }
        HoldActionButton(
          label = "BRAKE",
          accent = ViceCrimson,
          testTag = "drive_brake",
          sizeDp = 62,
          onPressChange = { pressed -> engine.driveBrake = if (pressed) 1f else 0f },
        ) {
          Text("REV", style = MaterialTheme.typography.labelLarge, color = ViceTextPrimary)
        }
        HoldActionButton(
          label = "GAS",
          accent = ViceMint,
          testTag = "drive_gas",
          sizeDp = 72,
          onPressChange = { pressed -> engine.driveThrottle = if (pressed) 1f else 0f },
        ) {
          Text("GAS", style = MaterialTheme.typography.labelLarge, color = ViceMint)
        }
      }
    }
  }
}

@Composable
private fun VirtualJoystick(onMove: (Float, Float) -> Unit) {
  var knobX by remember { mutableFloatStateOf(0f) }
  var knobY by remember { mutableFloatStateOf(0f) }
  val maxRadiusPx = 100f

  Box(
    modifier =
      Modifier.size(124.dp)
        .clip(CircleShape)
        .background(ViceMidnight.copy(alpha = 0.55f))
        .border(2.dp, ViceCyan.copy(alpha = 0.55f), CircleShape)
        .testTag("virtual_joystick")
        .pointerInput(Unit) {
          awaitPointerEventScope {
            while (true) {
              val event = awaitPointerEvent()
              val change = event.changes.firstOrNull()
              if (change == null || event.type == PointerEventType.Release || !change.pressed) {
                knobX = 0f
                knobY = 0f
                onMove(0f, 0f)
              } else {
                change.consume()
                val cx = size.width / 2f
                val cy = size.height / 2f
                val dx = change.position.x - cx
                val dy = change.position.y - cy
                val d = hypot(dx, dy).coerceAtLeast(0.001f)
                val clamped = d.coerceAtMost(maxRadiusPx)
                knobX = (dx / d) * clamped
                knobY = (dy / d) * clamped
                onMove(knobX / maxRadiusPx, knobY / maxRadiusPx)
              }
            }
          }
        },
    contentAlignment = Alignment.Center,
  ) {
    Box(
      modifier =
        Modifier.offset { IntOffset(knobX.roundToInt(), knobY.roundToInt()) }
          .size(48.dp)
          .clip(CircleShape)
          .background(Brush.radialGradient(listOf(ViceCyan, ViceMagenta)))
          .border(2.dp, ViceTextPrimary.copy(alpha = 0.8f), CircleShape)
    )
  }
}

@Composable
private fun HoldActionButton(
  label: String,
  accent: Color,
  testTag: String,
  sizeDp: Int,
  onPressChange: (Boolean) -> Unit,
  content: @Composable () -> Unit,
) {
  var pressed by remember { mutableStateOf(false) }
  Box(
    modifier =
      Modifier.size(sizeDp.dp)
        .clip(CircleShape)
        .background(if (pressed) accent.copy(alpha = 0.45f) else ViceSurface.copy(alpha = 0.82f))
        .border(2.dp, accent, CircleShape)
        .testTag(testTag)
        .pointerInput(Unit) {
          awaitPointerEventScope {
            while (true) {
              val event = awaitPointerEvent()
              val down = event.changes.any { it.pressed }
              if (down != pressed) {
                pressed = down
                onPressChange(down)
              }
              event.changes.forEach { it.consume() }
            }
          }
        },
    contentAlignment = Alignment.Center,
  ) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      content()
      if (sizeDp >= 64) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = ViceTextPrimary, fontSize = 9.sp)
      }
    }
  }
}

@Composable
private fun HudCircleButton(
  label: String,
  active: Boolean,
  accent: Color,
  onClick: () -> Unit,
  testTag: String,
  icon: @Composable () -> Unit,
) {
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Box(
      modifier =
        Modifier.size(50.dp)
          .clip(CircleShape)
          .background(if (active) accent.copy(alpha = 0.30f) else ViceSurface.copy(alpha = 0.82f))
          .border(1.5.dp, if (active) accent else accent.copy(alpha = 0.55f), CircleShape)
          .clickable(onClick = onClick)
          .testTag(testTag),
      contentAlignment = Alignment.Center,
    ) {
      icon()
    }
    Text(label, style = MaterialTheme.typography.labelSmall, color = ViceTextMuted, fontSize = 9.sp)
  }
}
