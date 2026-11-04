package com.bytegif.glassbeat.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bytegif.glassbeat.data.local.AppStorage
import com.bytegif.glassbeat.ui.MainViewModel
import com.bytegif.glassbeat.ui.equalizer.EqualizerSpecs
import com.bytegif.glassbeat.ui.theme.GlassModalOverlay
import com.bytegif.glassbeat.ui.theme.GlassTheme
import com.bytegif.glassbeat.ui.theme.glassCard
import kotlin.math.roundToInt

@Composable
fun EqualizerScreen(viewModel: MainViewModel) {
    val colors = GlassTheme.colors
    val prefs by AppStorage.state.collectAsState()
    val eqEnabled = prefs.eqEnabled
    val presetIndex = prefs.eqPresetIndex.coerceIn(0, EqualizerSpecs.presets.lastIndex)
    val activePreset = EqualizerSpecs.presets[presetIndex]
    val bands = prefs.eqBands

    var showPresetsModal by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        // Header with Master Switch
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .glassCard(cornerRadius = 20.dp)
                .padding(horizontal = 22.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Equalizer,
                    contentDescription = null,
                    tint = if (eqEnabled) colors.accent else colors.onSurfaceVariant,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        text = "5-Band Parametric Equalizer",
                        color = colors.onSurface,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (eqEnabled) "Active · Direct-Form II Biquad & CoreAudio EQ" else "Bypassed · Flat audio output",
                        color = colors.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (eqEnabled) "ON" else "OFF",
                    color = if (eqEnabled) colors.accent else colors.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Spacer(Modifier.width(10.dp))
                Switch(
                    checked = eqEnabled,
                    onCheckedChange = { viewModel.setEqualizerEnabled(it) }
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // Active Preset Selector Card + Frequency Curve Visualization
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(if (eqEnabled) 1f else 0.48f),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Active Preset Card
            Column(
                modifier = Modifier
                    .weight(0.42f)
                    .height(145.dp)
                    .glassCard(cornerRadius = 22.dp, elevated = true)
                    .clickable(enabled = eqEnabled) { showPresetsModal = true }
                    .padding(20.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ACTIVE PRESET",
                        color = colors.onSurfaceVariant,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Choose Preset",
                        tint = colors.accent,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column {
                    Text(
                        text = activePreset.name,
                        color = colors.onSurface,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "${activePreset.description} · Click to change",
                        color = colors.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
            }

            // Real-Time Frequency Response Curve
            Box(
                modifier = Modifier
                    .weight(0.58f)
                    .height(145.dp)
                    .glassCard(cornerRadius = 22.dp)
                    .padding(16.dp)
            ) {
                EqualizerResponseCurve(bands = bands, enabled = eqEnabled)
            }
        }

        Spacer(Modifier.height(16.dp))

        // 5-Band Sliders Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(if (eqEnabled) 1f else 0.45f)
                .glassCard(cornerRadius = 24.dp)
                .padding(horizontal = 24.dp, vertical = 20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Frequency Bands (-15 dB to +15 dB)",
                    color = colors.onSurface,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                OutlinedButton(
                    onClick = {
                        for (i in 0 until EqualizerSpecs.BANDS_COUNT) {
                            viewModel.updateEqualizerBand(i, 0)
                        }
                    },
                    enabled = eqEnabled,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Flat (0 dB)", fontSize = 12.sp)
                }
            }

            Spacer(Modifier.height(12.dp))

            for (i in 0 until EqualizerSpecs.BANDS_COUNT) {
                val freqHz = EqualizerSpecs.bandCenterFreqsHz[i]
                val currentMb = bands.getOrElse(i) { 0 }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = EqualizerSpecs.formatFrequency(freqHz),
                        color = colors.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        modifier = Modifier.width(80.dp)
                    )

                    Slider(
                        value = currentMb.toFloat(),
                        onValueChange = { newVal ->
                            val snapped = (newVal / 50f).roundToInt() * 50
                            viewModel.updateEqualizerBand(i, snapped)
                        },
                        valueRange = EqualizerSpecs.MIN_LEVEL_MB.toFloat()..EqualizerSpecs.MAX_LEVEL_MB.toFloat(),
                        enabled = eqEnabled,
                        colors = SliderDefaults.colors(
                            thumbColor = colors.accent,
                            activeTrackColor = colors.accent
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(Modifier.width(14.dp))

                    Text(
                        text = EqualizerSpecs.formatDb(currentMb),
                        color = if (currentMb != 0) colors.accent else colors.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        modifier = Modifier.width(64.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(100.dp))
    }

    if (showPresetsModal) {
        PresetsGridModal(
            selectedIndex = presetIndex,
            onSelectPreset = { idx ->
                viewModel.selectEqualizerPreset(idx)
                showPresetsModal = false
            },
            onDismiss = { showPresetsModal = false }
        )
    }
}

@Composable
private fun EqualizerResponseCurve(bands: List<Int>, enabled: Boolean) {
    val colors = GlassTheme.colors
    val accent = if (enabled) colors.accent else colors.onSurfaceVariant

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val midY = h / 2f

        // 0 dB center line
        drawLine(
            color = colors.divider,
            start = Offset(0f, midY),
            end = Offset(w, midY),
            strokeWidth = 1.dp.toPx()
        )

        val points = (0 until 5).map { idx ->
            val x = (w * (idx + 0.5f)) / 5f
            val mb = if (enabled) (bands.getOrElse(idx) { 0 }).toFloat() else 0f
            val normalized = (mb / 1500f).coerceIn(-1f, 1f)
            val y = midY - normalized * (h * 0.38f)
            Offset(x, y)
        }

        val path = Path().apply {
            if (points.isNotEmpty()) {
                moveTo(0f, points.first().y)
                lineTo(points.first().x, points.first().y)
                for (i in 0 until points.size - 1) {
                    val p0 = points[i]
                    val p1 = points[i + 1]
                    val ctrlX = (p0.x + p1.x) / 2f
                    cubicTo(ctrlX, p0.y, ctrlX, p1.y, p1.x, p1.y)
                }
                lineTo(w, points.last().y)
            }
        }

        val fillPath = Path().apply {
            addPath(path)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }

        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(accent.copy(alpha = 0.30f), accent.copy(alpha = 0.0f))
            )
        )

        drawPath(
            path = path,
            color = accent,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
        )

        points.forEach { pt ->
            drawCircle(
                color = accent,
                radius = 5.dp.toPx(),
                center = pt
            )
        }
    }
}

@Composable
private fun PresetsGridModal(
    selectedIndex: Int,
    onSelectPreset: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = GlassTheme.colors

    GlassModalOverlay(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .width(540.dp)
                .glassCard(cornerRadius = 26.dp, elevated = true)
                .padding(24.dp)
        ) {
            Text(
                text = "Equalizer Presets",
                color = colors.onSurface,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Choose an acoustic profile or tune manually",
                color = colors.onSurfaceVariant,
                fontSize = 12.sp
            )

            Spacer(Modifier.height(16.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.height(340.dp)
            ) {
                itemsIndexed(EqualizerSpecs.presets) { index, preset ->
                    val isSelected = index == selectedIndex
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .glassCard(
                                cornerRadius = 16.dp,
                                elevated = isSelected,
                                isSelected = isSelected
                            )
                            .clickable { onSelectPreset(index) }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = null,
                            tint = if (isSelected) colors.accent else colors.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = preset.name,
                                color = if (isSelected) colors.primary else colors.onSurface,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = preset.description,
                                color = colors.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        }
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Selected",
                                tint = colors.accent,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
