package com.ascendant.sentiment

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ascendant.sentiment.engine.AstroEngine
import com.ascendant.sentiment.model.GeoLocation
import com.ascendant.sentiment.ui.*
import com.ascendant.sentiment.ui.theme.*
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {

    private fun triggerHaptic() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                @Suppress("DEPRECATION")
                vibrator?.vibrate(20)
            }
        } catch (_: Exception) {
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            AscendantSentimentTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = BgDark
                ) { innerPadding ->
                    MainScreen(
                        modifier = Modifier.padding(innerPadding),
                        onHaptic = { triggerHaptic() }
                    )
                }
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    onHaptic: () -> Unit
) {
    var isLive by remember { mutableStateOf(true) }
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var centerTimeMs by remember { mutableLongStateOf((System.currentTimeMillis() / 300_000L) * 300_000L) }
    var inspectedTimeMs by remember { mutableLongStateOf((System.currentTimeMillis() / 300_000L) * 300_000L) }
    var selectedLocationIdx by remember { mutableIntStateOf(0) }
    var selectedTimezone by remember { mutableStateOf("Europe/London") }
    var zoom by remember { mutableStateOf(2f) }
    var trigDays by remember { mutableIntStateOf(3) }
    var trigWindow by remember { mutableIntStateOf(3) }
    var showTrigger by remember { mutableStateOf(true) }
    var showSaturnAsp by remember { mutableStateOf(true) }
    var showPlutoAsp by remember { mutableStateOf(true) }
    var showPlutoStrip by remember { mutableStateOf(true) }
    var showSaturnStrip by remember { mutableStateOf(true) }
    var showPlutoWeek by remember { mutableStateOf(true) }
    var showSaturnWeek by remember { mutableStateOf(true) }

    val locations = AstroEngine.PRESET_LOCATIONS
    val activeLocation = locations[selectedLocationIdx]

    LaunchedEffect(isLive) {
        while (true) {
            val cur = System.currentTimeMillis()
            nowMs = cur
            if (isLive) {
                val snapped = (cur / 300_000L) * 300_000L
                centerTimeMs = snapped
                inspectedTimeMs = snapped
            }
            delay(1000)
        }
    }

    val series = remember(centerTimeMs, activeLocation) {
        AstroEngine.generateSeries(
            centerMs = centerTimeMs, windowHours = 6, lat = activeLocation.lat, lon = activeLocation.lon,
            orbLimit = 3.0, useSd = true, usePm = true, taintAllMm = true, orbFalloff = true, pairBoost = true, trigger = true
        )
    }
    val chartView = remember(series) { intArrayOf(0, series.size, 1000) }
    val orbFalloff = true

    val weekBounds = remember(inspectedTimeMs / 86_400_000L, selectedTimezone) { AstroEngine.weekBoundsMs(inspectedTimeMs, selectedTimezone) }
    val trigBounds = remember(inspectedTimeMs / 86_400_000L, selectedTimezone, trigDays) {
        if (trigDays == 3) AstroEngine.weekBoundsMs(inspectedTimeMs, selectedTimezone, 1, 2)
        else AstroEngine.weekBoundsMs(inspectedTimeMs, selectedTimezone, 2, 5)
    }
    val trigData = remember(trigBounds.first, trigBounds.second, activeLocation, showTrigger) {
        if (showTrigger) AstroEngine.triggerCard(trigBounds.first, trigBounds.second, activeLocation.lat, activeLocation.lon) else null
    }
    val trigMarks = remember(trigData, trigWindow) { trigData?.let { AstroEngine.triggerMarks(it, trigWindow) } }

    val moonPlutoMarks = remember(series, showPlutoAsp) { if (showPlutoAsp) AstroEngine.moonAspectMarks(series, "Pluto") else null }
    val moonSatMarks = remember(series, showSaturnAsp) { if (showSaturnAsp) AstroEngine.moonAspectMarks(series, "Saturn") else null }
    val moonMarsMarks = remember(series, showPlutoAsp, showSaturnAsp) { if (showPlutoAsp || showSaturnAsp) AstroEngine.moonAspectMarks(series, "Mars") else null }
    val satAsc = remember(series, showSaturnAsp) { if (showSaturnAsp) AstroEngine.planetAscendantSeries(series, orbFalloff, "Saturn") else null }
    val plutoAsc = remember(series, showPlutoAsp) { if (showPlutoAsp) AstroEngine.planetAscendantSeries(series, orbFalloff, "Pluto") else null }
    val plutoGrid = remember(series, showPlutoStrip) { if (showPlutoStrip) AstroEngine.plutoActorGrid(series) else null }
    val saturnGrid = remember(series, showSaturnStrip) {
        if (showSaturnStrip) AstroEngine.plutoActorGrid(series, target = "Saturn", actorNames = AstroEngine.SATURN_ACTOR_NAMES) else null
    }
    val plutoWeek = remember(weekBounds.first, showPlutoWeek) { if (showPlutoWeek) AstroEngine.plutoActorWeek(weekBounds.first) else null }
    val plutoExacts = remember(inspectedTimeMs / 3_600_000L, showPlutoWeek) {
        if (showPlutoWeek) AstroEngine.plutoExactInfo((inspectedTimeMs / 300_000L) * 300_000L) else emptyList()
    }
    val saturnWeek = remember(weekBounds.first, showSaturnWeek) {
        if (showSaturnWeek) AstroEngine.plutoActorWeek(weekBounds.first, target = "Saturn", actorNames = AstroEngine.SATURN_ACTOR_NAMES) else null
    }
    val saturnExacts = remember(inspectedTimeMs / 3_600_000L, showSaturnWeek) {
        if (showSaturnWeek) AstroEngine.plutoExactInfo((inspectedTimeMs / 300_000L) * 300_000L, target = "Saturn", actorNames = AstroEngine.SATURN_ACTOR_NAMES) else emptyList()
    }

    val currentBar = remember(series, inspectedTimeMs) {
        if (series.isEmpty()) null
        else {
            val idx = ((inspectedTimeMs - series.first().timeMs) / 300_000.0).toInt().coerceIn(0, series.size - 1)
            series[idx]
        }
    }
    val curIdx = if (series.isEmpty()) 0 else ((inspectedTimeMs - series.first().timeMs) / 300_000L).toInt().coerceIn(0, series.size - 1)
    if (series.isNotEmpty()) updateChartView(chartView, series.size, zoom, curIdx)

    val liveSdf = remember(selectedTimezone) {
        SimpleDateFormat("EEE, dd MMM HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone(selectedTimezone) }
    }
    val jump: (Long) -> Unit = { t ->
        isLive = false
        if (series.isNotEmpty() && (t < series.first().timeMs || t > series.last().timeMs)) centerTimeMs = t
        inspectedTimeMs = t
    }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Pluto–Saturn", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Slate100)
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(color = Emerald500.copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp)) {
                        Text("Brent CFD", color = Emerald400, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
                Text(
                    text = liveSdf.format(Date(nowMs)) + if (isLive) " · LIVE" else " · PAUSED",
                    fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = if (isLive) Emerald400 else Amber500
                )
            }
            Surface(
                color = CardDark, shape = RoundedCornerShape(8.dp),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(BorderDark))
            ) {
                Text("100% Offline Native", color = Slate400, fontSize = 10.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
            }
        }

        // Location, card switches, time steppers, Live and zoom
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardDark),
            shape = RoundedCornerShape(14.dp),
            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(BorderDark))
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    locations.forEachIndexed { idx, loc ->
                        val isSelected = idx == selectedLocationIdx
                        Surface(
                            color = if (isSelected) Emerald500.copy(alpha = 0.2f) else Slate950,
                            shape = RoundedCornerShape(8.dp),
                            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(if (isSelected) Emerald400 else BorderDark)),
                            modifier = Modifier.clickable { onHaptic(); selectedLocationIdx = idx }
                        ) {
                            Text(
                                text = loc.name.split(" ").first(), fontSize = 11.sp,
                                color = if (isSelected) Emerald400 else Slate400,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(-60 to "−1h", -5 to "−5m", 5 to "+5m", 60 to "+1h").forEach { (mins, label) ->
                        OutlinedButton(
                            onClick = {
                                onHaptic()
                                isLive = false
                                val newT = inspectedTimeMs + mins * 60_000L
                                centerTimeMs = newT
                                inspectedTimeMs = newT
                            },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.weight(1f).height(38.dp)
                        ) { Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Slate100) }
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = { onHaptic(); isLive = !isLive },
                        colors = ButtonDefaults.buttonColors(containerColor = if (isLive) Emerald500 else Slate950),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(
                            imageVector = if (isLive) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = null, tint = if (isLive) Slate950 else Slate100, modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isLive) "Live Active" else "Resume Live", fontSize = 12.sp, color = if (isLive) Slate950 else Slate100, fontWeight = FontWeight.Bold)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(
                            onClick = { onHaptic(); zoom = (zoom / 2f).coerceIn(1f, 12f) },
                            shape = RoundedCornerShape(8.dp), contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.size(width = 34.dp, height = 30.dp)
                        ) { Text("−", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Slate100) }
                        Text(
                            text = if (zoom < 10f) "%.1f×".format(zoom) else "%.0f×".format(zoom),
                            fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Slate400,
                            textAlign = TextAlign.Center, modifier = Modifier.width(38.dp)
                        )
                        OutlinedButton(
                            onClick = { onHaptic(); zoom = (zoom * 2f).coerceIn(1f, 12f) },
                            shape = RoundedCornerShape(8.dp), contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.size(width = 34.dp, height = 30.dp)
                        ) { Text("+", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Slate100) }
                    }
                }

                ResonanceSwitch("Trigger card", showTrigger, Modifier.fillMaxWidth()) { onHaptic(); showTrigger = it }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ResonanceSwitch("Saturn-only Asc pane", showSaturnAsp, Modifier.weight(1f)) { onHaptic(); showSaturnAsp = it }
                    ResonanceSwitch("Pluto-only Asc pane", showPlutoAsp, Modifier.weight(1f)) { onHaptic(); showPlutoAsp = it }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ResonanceSwitch("Saturn actors strip", showSaturnStrip, Modifier.weight(1f)) { onHaptic(); showSaturnStrip = it }
                    ResonanceSwitch("Pluto actors strip", showPlutoStrip, Modifier.weight(1f)) { onHaptic(); showPlutoStrip = it }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ResonanceSwitch("Saturn week card", showSaturnWeek, Modifier.weight(1f)) { onHaptic(); showSaturnWeek = it }
                    ResonanceSwitch("Pluto week card", showPlutoWeek, Modifier.weight(1f)) { onHaptic(); showPlutoWeek = it }
                }
            }
        }

        // Trigger card (the decision card)
        if (showTrigger && trigData != null && trigMarks != null) {
            TriggerCard(
                data = trigData,
                marks = trigMarks,
                windowH = trigWindow,
                days = trigDays,
                inspectedTimeMs = inspectedTimeMs,
                nowMs = nowMs,
                timeZoneId = selectedTimezone,
                onWindowChange = { onHaptic(); trigWindow = it },
                onDaysChange = { onHaptic(); trigDays = it },
                onDayStep = { d ->
                    onHaptic()
                    isLive = false
                    val newT = inspectedTimeMs + d * 86_400_000L
                    centerTimeMs = newT
                    inspectedTimeMs = newT
                },
                onInspectTime = jump
            )
        }

        // 5-minute panes
        if (series.isNotEmpty() && (showSaturnAsp || showPlutoAsp || showSaturnStrip || showPlutoStrip)) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardDark),
                shape = RoundedCornerShape(16.dp),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(BorderDark))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("PANES · 5-MIN BARS", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Slate100)
                    Spacer(modifier = Modifier.height(4.dp))
                    HelioLegend()
                    Text("Dots on the bars: the Moon aspects that planet (dot colour = that aspect, large dot = exact)", color = Slate400, fontSize = 9.sp)

                if (showSaturnAsp && satAsc != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    val satNow = currentBar?.aspects?.filter { it.planetName == "Saturn" }?.minByOrNull { it.orbDiff }
                    val vSat = satAsc.detr.getOrNull(curIdx)
                    PaneHeader(
                        title = "SATURN ONLY · ASPECTS TO THE ASCENDANT  (detrended)",
                        value = (satNow?.let { "♄ ${it.aspectAngle}°  " + (vSat?.let { v -> (if (v > 0) "+" else "") + "%.2f".format(v) } ?: "") } ?: "no Saturn aspect") +
                            (moonSatMarks?.angle?.getOrNull(curIdx)?.takeIf { it >= 0 }?.let { "   ☽ $it° ♄" } ?: ""),
                        valueColor = satNow?.let { HelioAspectColors[it.aspectAngle] } ?: Slate400
                    )
                    Text("Background tint: the Moon aspects Mars (tint colour = that aspect)", color = Slate400, fontSize = 9.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))) {
                        SunAscendantPane(
                            data = satAsc,
                            firstBarMs = series.first().timeMs,
                            inspectedTimeMs = currentBar?.timeMs ?: inspectedTimeMs,
                            view = chartView,
                            zoom = zoom,
                            marks = moonSatMarks,
                            bgMarks = moonMarsMarks
                        )
                    }
                }
                if (showPlutoAsp && plutoAsc != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    val plutoNow = currentBar?.aspects?.filter { it.planetName == "Pluto" }?.minByOrNull { it.orbDiff }
                    val vPluto = plutoAsc.detr.getOrNull(curIdx)
                    PaneHeader(
                        title = "PLUTO ONLY · ASPECTS TO THE ASCENDANT  (detrended)",
                        value = (plutoNow?.let { "♇ ${it.aspectAngle}°  " + (vPluto?.let { v -> (if (v > 0) "+" else "") + "%.2f".format(v) } ?: "") } ?: "no Pluto aspect") +
                            (moonPlutoMarks?.angle?.getOrNull(curIdx)?.takeIf { it >= 0 }?.let { "   ☽ $it° ♇" } ?: ""),
                        valueColor = plutoNow?.let { HelioAspectColors[it.aspectAngle] } ?: Slate400
                    )
                    Text("Background tint: the Moon aspects Mars (tint colour = that aspect)", color = Slate400, fontSize = 9.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))) {
                        SunAscendantPane(
                            data = plutoAsc,
                            firstBarMs = series.first().timeMs,
                            inspectedTimeMs = currentBar?.timeMs ?: inspectedTimeMs,
                            view = chartView,
                            zoom = zoom,
                            marks = moonPlutoMarks,
                            bgMarks = moonMarsMarks
                        )
                    }
                }
                if (showSaturnStrip && saturnGrid != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    val litS = saturnGrid.actors.indices.count { saturnGrid.angle[it].getOrNull(curIdx)?.let { a -> a >= 0 } == true }
                    PaneHeader(
                        title = "SATURN AND ITS ACTORS  (☽ ☿ ♂ ♇ ☉ aspecting Saturn, amber = 2 or more)",
                        value = if (litS == 0) "none" else "$litS in aspect",
                        valueColor = if (litS >= 2) Amber500 else Slate400
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))) {
                        PlutoActorStrip(
                            grid = saturnGrid,
                            barCount = series.size,
                            firstBarMs = series.first().timeMs,
                            inspectedTimeMs = currentBar?.timeMs ?: inspectedTimeMs,
                            view = chartView,
                            zoom = zoom
                        )
                    }
                }
                if (showPlutoStrip && plutoGrid != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    val lit = plutoGrid.actors.indices.count { plutoGrid.angle[it].getOrNull(curIdx)?.let { a -> a >= 0 } == true }
                    PaneHeader(
                        title = "PLUTO AND ITS ACTORS  (☽ ☿ ♂ ♄ ☉ aspecting Pluto, amber = 2 or more)",
                        value = if (lit == 0) "none" else "$lit in aspect",
                        valueColor = if (lit >= 2) Amber500 else Slate400
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))) {
                        PlutoActorStrip(
                            grid = plutoGrid,
                            barCount = series.size,
                            firstBarMs = series.first().timeMs,
                            inspectedTimeMs = currentBar?.timeMs ?: inspectedTimeMs,
                            view = chartView,
                            zoom = zoom
                        )
                    }
                }

                }
            }
        }

        // Pluto and its actors (week)
        if (showPlutoWeek && plutoWeek != null) {
            PlutoWeekCard(
                week = plutoWeek, exacts = plutoExacts, inspectedTimeMs = inspectedTimeMs, nowMs = nowMs,
                timeZoneId = selectedTimezone, onInspectTime = jump
            )
        }

        // Saturn and its actors (week)
        if (showSaturnWeek && saturnWeek != null) {
            PlutoWeekCard(
                week = saturnWeek, exacts = saturnExacts, inspectedTimeMs = inspectedTimeMs, nowMs = nowMs,
                timeZoneId = selectedTimezone, targetName = "Saturn", targetGlyph = "♄", onInspectTime = jump
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "by A'RA", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
            fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Slate400
        )
        Spacer(modifier = Modifier.height(24.dp))
    }
}

/** v4.0: small labelled switch used for the resonance model options. */
@Composable
private fun ResonanceSwitch(label: String, checked: Boolean, modifier: Modifier = Modifier, onChange: (Boolean) -> Unit) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = Emerald500)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(label, fontSize = 11.sp, color = Slate100)
    }
}

/** v4.3: title on the left, current value on the right, used above each sub pane. */
@Composable
private fun PaneHeader(title: String, value: String?, valueColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = Slate400,
            modifier = Modifier.weight(1f)
        )
        if (value != null) {
            Text(
                text = value,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = valueColor
            )
        }
    }
}
