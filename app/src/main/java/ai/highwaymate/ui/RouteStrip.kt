package ai.highwaymate.ui

import ai.highwaymate.core.RestAreas
import ai.highwaymate.core.Snapshot
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The route ahead as one straight line, like an expressway route map:
 * congestion as yellow/red stretches, rest areas as green sign posts, toll gates, the car, the destination.
 * [windowKm] = null shows the whole route; otherwise the next [windowKm] km from the car.
 */
@Composable
fun RouteStrip(s: Snapshot, dark: Boolean, windowKm: Double?, modifier: Modifier = Modifier, height: Dp = 150.dp) {
    val tm = rememberTextMeasurer()
    val ink = if (dark) Hm.OnAsphalt else Hm.Ink
    val muted = if (dark) Hm.OnAsphaltMuted else Hm.Slate
    val road = if (dark) Hm.AsphaltLine else Hm.Line
    val totalKm = (if (s.route.totalM > 0) s.route.totalM else 141_000.0) / 1000
    val carKm = s.progressM / 1000
    val startKm = if (windowKm == null) 0.0 else (carKm - windowKm * 0.08).coerceAtLeast(0.0)
    val endKm = if (windowKm == null) totalKm else (startKm + windowKm).coerceAtMost(totalKm)

    Canvas(modifier.fillMaxWidth().then(if (height == Dp.Unspecified) Modifier.fillMaxHeight() else Modifier.height(height))) {
        val padX = 24.dp.toPx()
        val y = size.height * 0.52f
        val w = size.width - padX * 2
        fun x(km: Double) = padX + ((km - startKm) / (endKm - startKm)).toFloat().coerceIn(0f, 1f) * w
        val thick = 14.dp.toPx()

        // road
        drawRoundRect(road, Offset(padX, y - thick / 2), Size(w, thick), CornerRadius(thick / 2))
        // travelled part
        if (carKm > startKm) drawRoundRect(if (dark) Hm.OnAsphaltMuted.copy(alpha = 0.45f) else Hm.Slate.copy(alpha = 0.35f),
            Offset(padX, y - thick / 2), Size(x(carKm) - padX, thick), CornerRadius(thick / 2))
        // centre lane dashes
        drawLine(if (dark) Hm.Asphalt else Color.White, Offset(padX + thick, y), Offset(padX + w - thick, y), 2.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 14f)))

        // congestion
        s.route.congestion.forEach { c ->
            val a = c.offsetM / 1000; val b = a + c.lengthM / 1000
            if (b < startKm || a > endKm) return@forEach
            val col = if (c.type == "HEAVY") Hm.Danger else Hm.LaneYellow
            val x0 = x(a); val x1 = maxOf(x(b), x0 + 4.dp.toPx())
            drawRoundRect(col, Offset(x0, y - thick / 2), Size(x1 - x0, thick), CornerRadius(3.dp.toPx()))
        }

        // toll gates from the navigation TBT (labels never overlap each other or the start label)
        var tollLabelEnd = padX + 70.dp.toPx()
        s.route.tbt.filter { it.kind == "TURN_TOLL" }.forEach { e ->
            val km = (e.progressAtReportM + e.distanceM) / 1000
            if (km in startKm..endKm) {
                val cx = x(km)
                drawLine(ink, Offset(cx, y - thick), Offset(cx, y + thick), 3.dp.toPx(), cap = StrokeCap.Round)
                val t = tm.measure("요금소", TextStyle(color = muted, fontSize = 15.sp))
                val left = cx - t.size.width / 2
                if (left > tollLabelEnd + 6.dp.toPx()) {
                    drawText(t, topLeft = Offset(left, y + thick + 6.dp.toPx()))
                    tollLabelEnd = left + t.size.width
                }
            }
        }

        // rest areas as small green signs above the road
        var lastLabelEnd = -1f
        RestAreas.gyeongbuSouth.forEach { r ->
            if (r.km !in startKm..endKm || r.km > totalKm) return@forEach
            val cx = x(r.km)
            drawLine(Hm.SignGreen, Offset(cx, y - thick / 2), Offset(cx, y - 30.dp.toPx()), 2.dp.toPx())
            val label = tm.measure(r.name.removeSuffix("휴게소"), TextStyle(color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold))
            val bw = label.size.width + 16.dp.toPx(); val bh = label.size.height + 8.dp.toPx()
            val left = cx - bw / 2
            if (left > lastLabelEnd + 4.dp.toPx()) {
                drawRoundRect(Hm.SignGreen, Offset(left, y - 30.dp.toPx() - bh), Size(bw, bh), CornerRadius(4.dp.toPx()))
                drawText(label, topLeft = Offset(left + 8.dp.toPx(), y - 30.dp.toPx() - bh + 4.dp.toPx()))
                lastLabelEnd = left + bw
            }
        }

        // destination
        if (endKm >= totalKm - 0.01) {
            val dx = x(totalKm)
            drawCircle(ink, 9.dp.toPx(), Offset(dx, y))
            drawCircle(if (dark) Hm.Asphalt else Hm.Surface, 4.dp.toPx(), Offset(dx, y))
            val name = s.route.destName.ifBlank { "목적지" }
            val t = tm.measure(name, TextStyle(color = ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold))
            drawText(t, topLeft = Offset((dx - t.size.width).coerceAtLeast(0f), y + thick + 6.dp.toPx()))
        }

        // the car
        if (carKm in startKm..endKm) {
            val cx = x(carKm)
            val r = 13.dp.toPx()
            drawCircle(if (dark) Hm.OnAsphalt else Hm.Ink, r, Offset(cx, y))
            val p = Path().apply {
                moveTo(cx, y - r * 0.55f); lineTo(cx + r * 0.45f, y + r * 0.45f); lineTo(cx, y + r * 0.2f); lineTo(cx - r * 0.45f, y + r * 0.45f); close()
            }
            drawPath(p, if (dark) Hm.Asphalt else Color.White)
        }

        // scale labels
        val l = tm.measure(if (windowKm == null) "출발" else "지금", TextStyle(color = muted, fontSize = 15.sp))
        drawText(l, topLeft = Offset(padX, y + thick + 6.dp.toPx()))
        if (windowKm != null) {
            val r = tm.measure("${"%.0f".format(endKm - carKm)} km 앞", TextStyle(color = muted, fontSize = 15.sp))
            drawText(r, topLeft = Offset(padX + w - r.size.width, y + thick + 6.dp.toPx()))
        }
    }
}
