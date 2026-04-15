package com.sakayori.music.ui.screen.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.sakayori.music.expect.audio.EqualizerBand
import com.sakayori.music.expect.audio.EqualizerPreset
import com.sakayori.music.expect.audio.createEqualizerController
import com.sakayori.music.ui.theme.seed
import com.sakayori.music.ui.theme.typo
import com.sakayori.music.ui.theme.white
import com.sakayori.music.viewModel.SettingsViewModel
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import com.sakayori.music.generated.resources.Res
import com.sakayori.music.generated.resources.enable_equalizer
import com.sakayori.music.generated.resources.eq_bands
import com.sakayori.music.generated.resources.eq_presets
import com.sakayori.music.generated.resources.equalizer
import com.sakayori.music.generated.resources.equalizer_not_available

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerScreen(
    navController: NavController,
    settingsViewModel: SettingsViewModel = koinViewModel(),
) {
    val audioSessionId = settingsViewModel.getAudioSessionId()
    val controller = remember { createEqualizerController(audioSessionId) }
    var enabled by remember { mutableStateOf(controller.isEnabled()) }
    val bands = remember { mutableStateListOf<EqualizerBand>() }
    val presets = remember { mutableStateListOf<EqualizerPreset>() }
    var selectedPreset by remember { mutableIntStateOf(-1) }

    LaunchedEffect(Unit) {
        if (controller.isAvailable()) {
            bands.clear()
            bands.addAll(controller.getBands())
            presets.clear()
            presets.addAll(controller.getPresets())
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (!enabled) {
                controller.release()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(Res.string.equalizer),
                        style = typo().titleMedium,
                        color = white,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBackIosNew, "Back", tint = white)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        bands.forEachIndexed { index, _ ->
                            controller.setBandLevel(index, 0f)
                        }
                        selectedPreset = -1
                        bands.clear()
                        bands.addAll(controller.getBands())
                    }) {
                        Icon(Icons.Default.Refresh, "Reset", tint = white)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                ),
            )
        },
        containerColor = Color.Black,
    ) { innerPadding ->
        if (!controller.isAvailable()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(Res.string.equalizer_not_available),
                    style = typo().bodyLarge,
                    color = white,
                )
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(Res.string.enable_equalizer),
                    style = typo().bodyLarge,
                    color = white,
                )
                Switch(
                    checked = enabled,
                    onCheckedChange = {
                        enabled = it
                        controller.setEnabled(it)
                    },
                )
            }

            AnimatedVisibility(visible = enabled) {
                Column {
                    Text(
                        text = stringResource(Res.string.eq_presets),
                        style = typo().labelMedium,
                        color = white,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 4.dp),
                    ) {
                        items(presets.size) { index ->
                            FilterChip(
                                selected = selectedPreset == index,
                                onClick = {
                                    selectedPreset = index
                                    controller.applyPreset(index)
                                    bands.clear()
                                    bands.addAll(controller.getBands())
                                },
                                label = {
                                    Text(
                                        text = presets[index].name,
                                        style = typo().bodySmall,
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = seed,
                                    selectedLabelColor = Color.Black,
                                    labelColor = white,
                                ),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = stringResource(Res.string.eq_bands),
                        style = typo().labelMedium,
                        color = white,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )

                    bands.forEachIndexed { index, band ->
                        EqualizerBandSlider(
                            band = band,
                            onLevelChange = { newLevel ->
                                controller.setBandLevel(index, newLevel)
                                bands[index] = band.copy(level = newLevel)
                                selectedPreset = -1
                            },
                        )
                    }

                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}

@Composable
private fun EqualizerBandSlider(
    band: EqualizerBand,
    onLevelChange: (Float) -> Unit,
) {
    var sliderValue by remember(band.level) { mutableFloatStateOf(band.level) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatFrequency(band.centerFrequency),
                style = typo().bodySmall,
                color = Color.White.copy(alpha = 0.7f),
            )
            Text(
                text = "${((sliderValue * 10).toInt() / 10f)} dB",
                style = typo().bodySmall,
                color = if (sliderValue >= 0) Color(0xFF00BCD4) else Color(0xFFFF5722),
            )
        }
        Slider(
            value = sliderValue,
            onValueChange = {
                sliderValue = it
            },
            onValueChangeFinished = {
                onLevelChange(sliderValue)
            },
            valueRange = band.minLevel..band.maxLevel,
            colors = SliderDefaults.colors(
                thumbColor = seed,
                activeTrackColor = seed,
                inactiveTrackColor = Color.DarkGray,
            ),
        )
    }
}

private fun formatFrequency(hz: Int): String =
    when {
        hz >= 1000 -> "${hz / 1000}K Hz"
        else -> "$hz Hz"
    }
