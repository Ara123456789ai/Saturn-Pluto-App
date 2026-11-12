package com.ascendant.sentiment.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ascendant.sentiment.model.TriggerCardData
import com.ascendant.sentiment.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

private val TriggerGold = Color(0xFFFFD700)
private val TriggerViolet = Color(0xFFC4B5FD)

private fun trigDur(ms: Long): String {
    val h = abs(ms) / 3_600_000.0
    return if (h < 36) "%.1f h".format(h) else "%.1f days".format(h / 24.0)
}

private fun shortLabel(glyph: String, angles: List<Int>): String =
    if (angles.size == 1) glyph + angles[0] else glyph

/**
 * Pluto–Saturn app: the trigger card, 1-hour bars.
 * Rows from the top: ENTRY (◆ = trigger, gold ◆ = Asc–Saturn and Asc–Pluto lit at the same moment), the Moon–Saturn and
 * Moon–Pluto lanes, the Ascendant lanes, and the hard aspects (90, 135, 150, 180) to the Moon from the neutrals, Saturn and Pluto.
 */
@Composable
fun TriggerCard(
    data: TriggerCardData,
    marks: IntArray,
    windowH: Int,
    days: Int,
    inspectedTimeMs: Long,
    nowMs: Long,
    timeZoneId: String,
    onWindowChange: (Int) -> Unit,
    onDaysChange: (Int) -> Unit,
    onDayStep: (Int) -> Unit,
    onInspectTime: (Long) -> Unit
) {
    val n = data.n
    if (n == 0) return
    val start = data.startMs
    val barMs = data.barMs
    val end = start + n * barMs
    val dayFmt = remember(timeZoneId) { SimpleDateFormat("EEE d", Locale.US).apply { timeZone = TimeZone.getTimeZone(timeZoneId) } }
    val tFmt = remember(timeZoneId) { SimpleDateFormat("EEE HH:mm", Locale.US).apply { timeZone = TimeZone.getTimeZone(timeZoneId) } }
    val hmFmt = remember(timeZoneId) { SimpleDateFormat("HH:mm", Locale.US).apply { timeZone = TimeZone.getTimeZone(timeZoneId) } }
    val onInspect = rememberUpdatedState(onInspectTime)
    val selIdx = ((inspectedTimeMs - start) / barMs).toInt().coerceIn(0, n - 1)
    val lanes = data.lanes
    val groupSizes = intArrayOf(1, 7, 2, 6)
    val totalTriggers = marks.count { it > 0 }
    val goldCount = marks.count { it == 2 }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardDark),
        shape = RoundedCornerShape(16.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(BorderDark))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("ENTRY TRIGGERS · MOON LANES · HARD ASPECTS TO THE MOON (1h bars)", color = Slate100, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text(
                "◆ = Asc–♄ (120/135/150) and Asc–♇ 135° within ±${windowH}h, with a ☽–♄ or ☽–♇ lane lit that hour · gold ◆ = both Ascendant aspects at the same moment · " +
                    "$totalTriggers trigger(s) in view, $goldCount gold",
                color = Slate400, fontSize = 9.sp, lineHeight = 12.sp
            )
            Spacer(Modifier.height(6.dp))

            // controls: view length, day steppers, trigger window
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = { onDayStep(-1) }, shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(0.dp), modifier = Modifier.size(width = 36.dp, height = 28.dp)
                ) { Text("◀", fontSize = 12.sp, color = Slate100) }
                OutlinedButton(
                    onClick = { onDayStep(1) }, shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(0.dp), modifier = Modifier.size(width = 36.dp, height = 28.dp)
                ) { Text("▶", fontSize = 12.sp, color = Slate100) }
                OutlinedButton(
                    onClick = { onDaysChange(if (days == 3) 7 else 3) }, shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp), modifier = Modifier.height(28.dp)
                ) { Text("${days}d", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate100) }
                Spacer(Modifier.weight(1f))
                Text("window ±", fontSize = 10.sp, color = Slate400)
                OutlinedButton(
                    onClick = { onWindowChange((windowH - 1).coerceIn(1, 6)) }, shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(0.dp), modifier = Modifier.size(width = 30.dp, height = 28.dp)
                ) { Text("−", fontSize = 14.sp, color = Slate100) }
                Text("${windowH}h", fontSize = 11.sp, color = Slate100, fontWeight = FontWeight.Bold)
                OutlinedButton(
                    onClick = { onWindowChange((windowH + 1).coerceIn(1, 6)) }, shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(0.dp), modifier = Modifier.size(width = 30.dp, height = 28.dp)
                ) { Text("+", fontSize = 14.sp, color = Slate100) }
            }
            Spacer(Modifier.height(6.dp))

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(330.dp)
                    .weekScrub(data, n, start, barMs, onInspect)
            ) {
                val left = 42.dp.toPx()
                val top = 6.dp.toPx()
                val bottom = 14.dp.toPx()
                val gap = 6.dp.toPx()
                val plotW = size.width - left - 12.dp.toPx()
                val rows = groupSizes.sum()
                val rowH = (size.height - top - bottom - gap * (groupSizes.size - 1)) / rows
                val cell = plotW / n
                val paint = android.graphics.Paint().apply { isAntiAlias = true; textSize = 8.sp.toPx(); color = Slate400.toArgb(); textAlign = android.graphics.Paint.Align.RIGHT }

                // y of a row index (0 = ENTRY row, 1.. = lanes)
                fun rowY(r: Int): Float {
                    var g = 0
                    var acc = 0
                    while (g < groupSizes.size && r >= acc + groupSizes[g]) { acc += groupSizes[g]; g++ }
                    return top + r * rowH + g * gap
                }
                val plotBottom = rowY(rows - 1) + rowH

                // trigger columns, faint behind everything
                for (i in 0 until n) if (marks[i] > 0) {
                    drawRect(
                        (if (marks[i] == 2) TriggerGold else TriggerViolet).copy(alpha = 0.14f),
                        Offset(left + i * cell, top), Size(cell, plotBottom - top)
                    )
                }

                // lane backgrounds, labels and cells
                for (r in 0 until rows) {
                    val y = rowY(r)
                    drawRect(Color(0xFF0F172A), Offset(left, y), Size(plotW, rowH - 1.dp.toPx()))
                    val label = if (r == 0) "ENTRY" else shortLabel(lanes[r - 1].glyph, lanes[r - 1].angles)
                    drawContext.canvas.nativeCanvas.drawText(label, left - 4.dp.toPx(), y + rowH * 0.7f, paint)
                    if (r > 0) {
                        val l = r - 1
                        for (i in 0 until n) {
                            val a = data.angle[l][i]
                            if (a < 0) continue
                            val base = HelioAspectColors[a] ?: Color.White
                            val alpha = (0.4f + 0.6f * (1f - (data.orb[l][i] / 3.0).toFloat())).coerceIn(0.4f, 1f)
                            drawRect(base.copy(alpha = alpha), Offset(left + i * cell, y), Size(cell + 0.5f, rowH - 1.dp.toPx()))
                        }
                    }
                }

                // ENTRY diamonds
                for (i in 0 until n) if (marks[i] > 0) {
                    val cx = left + (i + 0.5f) * cell
                    val cy = rowY(0) + rowH / 2
                    val rr = (if (marks[i] == 2) 0.55f else 0.42f) * rowH
                    val p = Path().apply {
                        moveTo(cx, cy - rr); lineTo(cx + rr, cy); lineTo(cx, cy + rr); lineTo(cx - rr, cy); close()
                    }
                    drawPath(p, if (marks[i] == 2) TriggerGold else Color.White)
                }

                // day separators and labels
                var lastDay = ""
                for (i in 0 until n) {
                    val label = dayFmt.format(Date(start + i * barMs))
                    if (label != lastDay) {
                        lastDay = label
                        drawLine(Color(0xFF334155).copy(alpha = 0.8f), Offset(left + i * cell, top), Offset(left + i * cell, plotBottom), 1f)
                        paint.textAlign = android.graphics.Paint.Align.LEFT
                        drawContext.canvas.nativeCanvas.drawText(label, left + i * cell + 3.dp.toPx(), size.height - 2.dp.toPx(), paint)
                        paint.textAlign = android.graphics.Paint.Align.RIGHT
                    }
                }
                if (nowMs in start until end) {
                    val nx = left + ((nowMs - start).toFloat() / (end - start)) * plotW
                    drawLine(Color(0xFFFBBF24).copy(alpha = 0.7f), Offset(nx, top), Offset(nx, plotBottom), 1.5f)
                }
                val sx = left + (selIdx + 0.5f) * cell
                drawLine(Color.White, Offset(sx, top), Offset(sx, plotBottom), 2f)
            }

            Spacer(Modifier.height(6.dp))
            Text("At the cursor · ${tFmt.format(Date(inspectedTimeMs))}", color = Slate400, fontSize = 10.sp, fontWeight = FontWeight.Bold)

            // trigger status at the cursor, and the next one
            val here = marks[selIdx]
            if (here > 0) {
                Text(
                    (if (here == 2) "◆ GOLD ENTRY TRIGGER" else "◆ ENTRY TRIGGER") + " in this hour (${hmFmt.format(Date(start + selIdx * barMs))}–${hmFmt.format(Date(start + (selIdx + 1) * barMs))})",
                    color = if (here == 2) TriggerGold else TriggerViolet, fontSize = 11.sp, fontWeight = FontWeight.Bold
                )
            } else {
                val next = (selIdx + 1 until n).firstOrNull { marks[it] > 0 }
                Text(
                    if (next != null) "Next trigger in view: ${tFmt.format(Date(start + next * barMs))} (in ${trigDur(start + next * barMs - inspectedTimeMs)})" +
                        if (marks[next] == 2) " · gold" else ""
                    else "No further trigger in view.",
                    color = Slate100, fontSize = 11.sp
                )
            }

            var any = false
            for (l in lanes.indices) {
                val a = data.angle[l][selIdx]
                if (a < 0) continue
                any = true
                val t = data.atMs[l][selIdx]
                val lane = lanes[l]
                val text = when (lane.group) {
                    0 -> "☽ $a° ♄"
                    1 -> "☽ $a° ♇"
                    2 -> if (lane.angles.size == 1) "Asc $a° ♇" else "Asc $a° ♄"
                    else -> lane.glyph.dropLast(1) + " $a° ☽"
                }
                Text(
                    "$text · orb %.1f° · tightest ${hmFmt.format(Date(t))}".format(data.orb[l][selIdx]),
                    color = HelioAspectColors[a] ?: Slate100, fontSize = 11.sp
                )
            }
            if (!any) Text("No lane is lit in this hour.", color = Slate100, fontSize = 11.sp)
        }
    }
}
