package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.game.model.BlipKind
import com.example.game.model.GameHudState
import com.example.game.model.GamePhase
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
import com.example.ui.theme.ViceSurfaceElevated
import com.example.ui.theme.ViceTextMuted
import com.example.ui.theme.ViceTextPrimary

@Composable
fun TitleScreenOverlay(
  hud: GameHudState,
  hasSavedProgress: Boolean,
  onEnterCity: () -> Unit,
  onResetSandbox: () -> Unit,
) {
  Box(modifier = Modifier.fillMaxSize().background(ViceMidnight)) {
    Image(
      painter = painterResource(id = R.drawable.img_vice_hero),
      contentDescription = "Vice Coast 3D artwork",
      contentScale = ContentScale.Crop,
      modifier = Modifier.fillMaxSize(),
    )
    Box(
      modifier =
        Modifier.fillMaxSize()
          .background(
            Brush.verticalGradient(
              colors =
                listOf(
                  ViceMidnight.copy(alpha = 0.45f),
                  ViceMidnight.copy(alpha = 0.78f),
                  ViceMidnight.copy(alpha = 0.96f),
                )
            )
          )
    )

    Column(
      modifier =
        Modifier.fillMaxSize()
          .windowInsetsPadding(WindowInsets.safeDrawing)
          .padding(24.dp),
      verticalArrangement = Arrangement.SpaceBetween,
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 12.dp)) {
        Surface(
          color = ViceMagenta.copy(alpha = 0.22f),
          shape = RoundedCornerShape(20.dp),
          modifier = Modifier.border(1.dp, ViceMagenta, RoundedCornerShape(20.dp)),
        ) {
          Text(
            text = stringResource(R.string.game_subtitle),
            style = MaterialTheme.typography.labelLarge,
            color = ViceCyan,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
          )
        }
        Spacer(Modifier.height(10.dp))
        Text(
          text = stringResource(R.string.app_name).uppercase(),
          style = MaterialTheme.typography.displayLarge,
          color = ViceTextPrimary,
        )
        Text(
          text = "Coastal Strip • Downtown • Palms Estates",
          style = MaterialTheme.typography.bodyMedium,
          color = ViceGold,
        )
      }

      Column(
        modifier = Modifier.widthIn(max = 460.dp).fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        if (hasSavedProgress) {
          Surface(
            color = ViceSurface.copy(alpha = 0.85f),
            shape = RoundedCornerShape(14.dp),
            modifier =
              Modifier.fillMaxWidth()
                .border(1.dp, ViceCyan.copy(alpha = 0.4f), RoundedCornerShape(14.dp)),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              StatChip("BANK", "$${hud.cash}", ViceMint)
              StatChip("KILLS", "${hud.totalEliminations}", ViceMagenta)
              StatChip("MAX HEAT", "${hud.highestWanted}★", ViceGold)
            }
          }
        }

        Button(
          onClick = onEnterCity,
          colors = ButtonDefaults.buttonColors(containerColor = ViceMagenta, contentColor = ViceTextPrimary),
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.fillMaxWidth().height(54.dp).testTag("enter_city_button"),
        ) {
          Icon(Icons.Default.PlayArrow, contentDescription = null)
          Spacer(Modifier.width(8.dp))
          Text(
            text =
              if (hasSavedProgress) stringResource(R.string.cta_continue_city)
              else stringResource(R.string.cta_enter_city),
            style = MaterialTheme.typography.titleMedium,
          )
        }

        if (hasSavedProgress) {
          OutlinedButton(
            onClick = onResetSandbox,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("reset_sandbox_button"),
          ) {
            Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = ViceTextMuted)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.cta_new_sandbox), color = ViceTextMuted)
          }
        }
      }
    }
  }
}

@Composable
private fun StatChip(label: String, value: String, color: Color) {
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Text(label, style = MaterialTheme.typography.labelSmall, color = ViceTextMuted)
    Text(value, style = MaterialTheme.typography.headlineMedium, color = color)
  }
}

@Composable
fun ViceArmoryModal(
  hud: GameHudState,
  onBuyWeaponOrAmmo: (WeaponType) -> Unit,
  onSelectWeapon: (WeaponType) -> Unit,
  onBuyArmor: () -> Unit,
  onClose: () -> Unit,
) {
  BackHandler(onBack = onClose)

  Box(
    modifier =
      Modifier.fillMaxSize()
        .background(ViceMidnight.copy(alpha = 0.80f))
        .windowInsetsPadding(WindowInsets.safeDrawing)
        .padding(16.dp),
    contentAlignment = Alignment.Center,
  ) {
    Surface(
      color = ViceSurface,
      shape = RoundedCornerShape(20.dp),
      modifier =
        Modifier.widthIn(max = 560.dp)
          .fillMaxWidth()
          .fillMaxHeight(0.90f)
          .border(1.5.dp, ViceCyan, RoundedCornerShape(20.dp)),
    ) {
      Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.ShoppingBag, contentDescription = "Armory", tint = ViceCyan)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.armory_title), style = MaterialTheme.typography.displayMedium, color = ViceCyan)
          }
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$${hud.cash}", style = MaterialTheme.typography.headlineMedium, color = ViceMint)
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = onClose, modifier = Modifier.size(48.dp).testTag("close_armory_button")) {
              Icon(Icons.Default.Close, contentDescription = "Close Armory", tint = ViceTextPrimary)
            }
          }
        }

        Spacer(Modifier.height(10.dp))

        // Kevlar Armor Card
        Card(
          colors = CardDefaults.cardColors(containerColor = ViceSurfaceElevated),
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Default.Shield, contentDescription = "Kevlar Armor", tint = ViceCyan, modifier = Modifier.size(28.dp))
              Spacer(Modifier.width(10.dp))
              Column {
                Text("Tactical Kevlar Vest", style = MaterialTheme.typography.titleMedium, color = ViceTextPrimary)
                Text("Current Armor: ${hud.armor}/100", style = MaterialTheme.typography.bodyMedium, color = ViceTextMuted)
              }
            }
            Button(
              onClick = onBuyArmor,
              enabled = hud.armor < 100 && hud.cash >= 150,
              colors = ButtonDefaults.buttonColors(containerColor = ViceCyan, contentColor = ViceMidnight),
              modifier = Modifier.height(48.dp).testTag("buy_armor_button"),
            ) {
              Text(if (hud.armor >= 100) "FULL" else "$150", fontWeight = FontWeight.Bold)
            }
          }
        }

        Spacer(Modifier.height(10.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
          items(hud.inventory.filter { it.type != WeaponType.FISTS }) { slot ->
            val w = slot.type
            val equipped = hud.activeWeapon == w
            Card(
              colors = CardDefaults.cardColors(containerColor = ViceSurfaceElevated),
              shape = RoundedCornerShape(14.dp),
              modifier =
                Modifier.fillMaxWidth()
                  .border(
                    1.dp,
                    if (equipped) ViceMagenta else Color.Transparent,
                    RoundedCornerShape(14.dp),
                  ),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(w.displayName, style = MaterialTheme.typography.titleMedium, color = Color(w.accentHex))
                  Text(
                    text =
                      if (w.isMelee) "DMG ${w.damage.toInt()} • MELEE • HEAVY KNOCKBACK"
                      else "DMG ${w.damage.toInt()} • RNG ${w.range.toInt()}m • MAG ${w.clipSize}",
                    style = MaterialTheme.typography.labelSmall,
                    color = ViceTextMuted,
                  )
                  Spacer(Modifier.height(4.dp))
                  LinearProgressIndicator(
                    progress = { (w.damage * w.pellets / 150f).coerceIn(0.15f, 1f) },
                    modifier = Modifier.fillMaxWidth(0.75f).height(5.dp).clip(RoundedCornerShape(3.dp)),
                    color = Color(w.accentHex),
                    trackColor = ViceMidnight,
                  )
                  if (slot.unlocked) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                      text = if (w.isMelee) "Owned • Unlimited" else "Ammo: ${slot.clipAmmo} + ${slot.reserveAmmo}",
                      style = MaterialTheme.typography.labelSmall,
                      color = ViceMint,
                    )
                  }
                }

                Spacer(Modifier.width(8.dp))

                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                  if (!slot.unlocked) {
                    Button(
                      onClick = { onBuyWeaponOrAmmo(w) },
                      enabled = hud.cash >= w.unlockCost,
                      colors = ButtonDefaults.buttonColors(containerColor = ViceMagenta, contentColor = ViceTextPrimary),
                      modifier = Modifier.height(48.dp).testTag("buy_${w.id}"),
                    ) {
                      Text("BUY $${w.unlockCost}")
                    }
                  } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                      if (!equipped) {
                        OutlinedButton(
                          onClick = { onSelectWeapon(w) },
                          modifier = Modifier.height(48.dp).testTag("equip_${w.id}"),
                        ) {
                          Text("EQUIP", color = ViceCyan)
                        }
                      }
                      if (!w.isMelee) {
                        Button(
                          onClick = { onBuyWeaponOrAmmo(w) },
                          enabled = slot.reserveAmmo < w.reserveCap && hud.cash >= w.ammoPackCost,
                          colors = ButtonDefaults.buttonColors(containerColor = ViceGold, contentColor = ViceMidnight),
                          modifier = Modifier.height(48.dp).testTag("ammo_${w.id}"),
                        ) {
                          Text(if (slot.reserveAmmo >= w.reserveCap) "MAX" else "+${w.ammoPackAmount} ($${w.ammoPackCost})")
                        }
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
fun WorldMapModal(
  hud: GameHudState,
  onSetWaypoint: (Float?, Float?) -> Unit,
  onClose: () -> Unit,
) {
  BackHandler(onBack = onClose)

  Box(
    modifier =
      Modifier.fillMaxSize()
        .background(ViceMidnight.copy(alpha = 0.88f))
        .windowInsetsPadding(WindowInsets.safeDrawing)
        .padding(14.dp),
    contentAlignment = Alignment.Center,
  ) {
    Surface(
      color = ViceSurface,
      shape = RoundedCornerShape(20.dp),
      modifier = Modifier.fillMaxSize().border(1.5.dp, ViceCyan, RoundedCornerShape(20.dp)),
    ) {
      Column(modifier = Modifier.fillMaxSize().padding(14.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column {
            Text(stringResource(R.string.map_title), style = MaterialTheme.typography.headlineMedium, color = ViceCyan)
            Text("Tap anywhere on map to place GPS beacon", style = MaterialTheme.typography.bodyMedium, color = ViceTextMuted)
          }
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (hud.waypointX != null) {
              OutlinedButton(onClick = { onSetWaypoint(null, null) }, modifier = Modifier.height(48.dp)) {
                Text("CLEAR PIN", color = ViceGold)
              }
            }
            IconButton(onClick = onClose, modifier = Modifier.size(48.dp).testTag("close_map_button")) {
              Icon(Icons.Default.Close, contentDescription = "Close Map", tint = ViceTextPrimary)
            }
          }
        }

        Spacer(Modifier.height(8.dp))

        Box(
          modifier =
            Modifier.weight(1f)
              .fillMaxWidth()
              .clip(RoundedCornerShape(14.dp))
              .background(Color(0xFF131020))
              .border(1.dp, ViceMagenta.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
        ) {
          Canvas(
            modifier =
              Modifier.fillMaxSize().pointerInput(Unit) {
                detectTapGestures { offset ->
                  val minC = CityMap.WORLD_MIN
                  val span = CityMap.WORLD_MAX - CityMap.WORLD_MIN
                  val wx = minC + (offset.x / size.width) * span
                  val wz = CityMap.WORLD_MAX - (offset.y / size.height) * span
                  onSetWaypoint(wx, wz)
                }
              }
          ) {
            val minC = CityMap.WORLD_MIN
            val span = CityMap.WORLD_MAX - CityMap.WORLD_MIN
            fun toScreen(wx: Float, wz: Float): Offset {
              val sx = ((wx - minC) / span) * size.width
              val sy = ((CityMap.WORLD_MAX - wz) / span) * size.height
              return Offset(sx, sy)
            }

            // Ocean East
            val oceanLeft = toScreen(CityMap.BEACH_START_X, 0f).x
            drawRect(Color(0xFF0A4F7A), Offset(oceanLeft, 0f), Size(size.width - oceanLeft, size.height))

            // Roads
            for (vr in CityMap.verticalRoads) {
              val rx = toScreen(vr.pos, 0f).x
              drawLine(Color(0xFF39324F), Offset(rx, 0f), Offset(rx, size.height), strokeWidth = 10f)
            }
            for (hz in CityMap.horizontalRoads) {
              val rz = toScreen(0f, hz.pos).y
              drawLine(Color(0xFF39324F), Offset(0f, rz), Offset(size.width, rz), strokeWidth = 10f)
            }

            // Buildings
            for (b in CityMap.buildings) {
              val tl = toScreen(b.minX, b.maxZ)
              val br = toScreen(b.maxX, b.minZ)
              drawRect(
                color = Color(b.r, b.g, b.b, 0.72f),
                topLeft = tl,
                size = Size((br.x - tl.x).coerceAtLeast(2f), (br.y - tl.y).coerceAtLeast(2f)),
              )
            }

            // Blips
            for (blip in hud.radarBlips) {
              val p = toScreen(blip.x, blip.z)
              when (blip.kind) {
                BlipKind.ARMORY -> drawCircle(ViceCyan, 9f, p)
                BlipKind.CLINIC -> drawCircle(ViceMagenta, 8f, p)
                BlipKind.VEHICLE -> drawRect(ViceGold, Offset(p.x - 4f, p.y - 4f), Size(8f, 8f))
                BlipKind.POLICE -> drawCircle(VicePoliceBlue, 6f, p)
                BlipKind.CASH -> drawCircle(ViceMint, 4.5f, p)
                BlipKind.WAYPOINT -> drawCircle(ViceCyan, 10f, p)
                BlipKind.CIVILIAN -> Unit
              }
            }

            // Jason Duval Position
            val jp = toScreen(hud.playerX, hud.playerZ)
            drawCircle(ViceTextPrimary, 8f, jp)
            drawCircle(ViceMagenta, 4.5f, jp)
          }
        }

        Spacer(Modifier.height(8.dp))

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
          LegendDot("JASON", ViceTextPrimary)
          LegendDot("ARMORY", ViceCyan)
          LegendDot("CLINIC", ViceMagenta)
          LegendDot("CARS", ViceGold)
          LegendDot("POLICE", VicePoliceBlue)
          LegendDot("CASH", ViceMint)
        }
      }
    }
  }
}

@Composable
private fun LegendDot(label: String, color: Color) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    Box(modifier = Modifier.size(9.dp).background(color, RoundedCornerShape(50)))
    Spacer(Modifier.width(4.dp))
    Text(label, style = MaterialTheme.typography.labelSmall, color = ViceTextMuted)
  }
}

@Composable
fun PauseMenuModal(
  hud: GameHudState,
  onResume: () -> Unit,
  onOpenMap: () -> Unit,
  onResetSandbox: () -> Unit,
) {
  BackHandler(onBack = onResume)

  Box(
    modifier =
      Modifier.fillMaxSize()
        .background(ViceMidnight.copy(alpha = 0.82f))
        .windowInsetsPadding(WindowInsets.safeDrawing)
        .padding(20.dp),
    contentAlignment = Alignment.Center,
  ) {
    Surface(
      color = ViceSurface,
      shape = RoundedCornerShape(20.dp),
      modifier =
        Modifier.widthIn(max = 420.dp)
          .fillMaxWidth()
          .border(1.5.dp, ViceMagenta, RoundedCornerShape(20.dp)),
    ) {
      Column(
        modifier = Modifier.padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Text("VICE COAST PAUSED", style = MaterialTheme.typography.displayMedium, color = ViceMagenta)
        Text(
          "District: ${hud.districtName} • Eliminations: ${hud.totalEliminations} • Total Earned: $${hud.totalEarned}",
          style = MaterialTheme.typography.bodyMedium,
          color = ViceTextMuted,
        )
        Spacer(Modifier.height(4.dp))

        Button(
          onClick = onResume,
          colors = ButtonDefaults.buttonColors(containerColor = ViceCyan, contentColor = ViceMidnight),
          modifier = Modifier.fillMaxWidth().height(50.dp).testTag("resume_button"),
        ) {
          Text("RESUME SANDBOX", fontWeight = FontWeight.Bold)
        }
        OutlinedButton(
          onClick = onOpenMap,
          modifier = Modifier.fillMaxWidth().height(48.dp).testTag("pause_open_map_button"),
        ) {
          Icon(Icons.Default.Map, contentDescription = null, tint = ViceCyan)
          Spacer(Modifier.width(8.dp))
          Text("TACTICAL WORLD MAP", color = ViceTextPrimary)
        }
        OutlinedButton(
          onClick = onResetSandbox,
          modifier = Modifier.fillMaxWidth().height(48.dp).testTag("pause_reset_button"),
        ) {
          Text("RESET SANDBOX PROGRESS", color = ViceCrimson)
        }
      }
    }
  }
}

@Composable
fun WastedBustedOverlay(
  phase: GamePhase,
  penaltyPaid: Int,
  onRespawn: () -> Unit,
) {
  val isBusted = phase == GamePhase.BUSTED
  val title = if (isBusted) stringResource(R.string.hud_busted) else stringResource(R.string.hud_wasted)
  val bannerColor = if (isBusted) VicePoliceBlue else ViceCrimson

  Box(
    modifier =
      Modifier.fillMaxSize()
        .background(ViceMidnight.copy(alpha = 0.80f))
        .windowInsetsPadding(WindowInsets.safeDrawing)
        .padding(24.dp),
    contentAlignment = Alignment.Center,
  ) {
    Surface(
      color = ViceSurface,
      shape = RoundedCornerShape(22.dp),
      modifier =
        Modifier.widthIn(max = 400.dp)
          .fillMaxWidth()
          .border(2.dp, bannerColor, RoundedCornerShape(22.dp)),
    ) {
      Column(
        modifier = Modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Text(title, style = MaterialTheme.typography.displayLarge, color = bannerColor)
        Text(
          text =
            if (isBusted) "VCPD apprehended the suspect. Bail fee: $$penaltyPaid (up to $100)."
            else "Dropped off at Vice Clinic. Medical fee deducted on discharge.",
          style = MaterialTheme.typography.bodyMedium,
          color = ViceTextMuted,
        )
        Button(
          onClick = onRespawn,
          colors = ButtonDefaults.buttonColors(containerColor = ViceCyan, contentColor = ViceMidnight),
          modifier = Modifier.fillMaxWidth().height(50.dp).testTag("respawn_button"),
        ) {
          Text(stringResource(R.string.hud_respawn), fontWeight = FontWeight.Bold)
        }
      }
    }
  }
}
