package ai.highwaymate.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Visual language: Korean expressway signage.
 * Sign green for direction/identity, lane yellow for caution, red for danger,
 * a cool paper white that sits next to the Pleos system UI, and asphalt for the driving state.
 */
object Hm {
    val Paper = Color(0xFFF3F5F2)
    val Surface = Color(0xFFFFFFFF)
    val Ink = Color(0xFF15191C)
    val Slate = Color(0xFF66707A)
    val Line = Color(0xFFDCE1DD)
    val SignGreen = Color(0xFF00694B)
    val SignGreenDeep = Color(0xFF004D37)
    val LaneYellow = Color(0xFFF2B705)
    val Danger = Color(0xFFD93A2B)
    val Asphalt = Color(0xFF23272B)
    val AsphaltRaised = Color(0xFF2E3337)
    val AsphaltLine = Color(0xFF41474D)
    val OnAsphalt = Color(0xFFF1F3F2)
    val OnAsphaltMuted = Color(0xFFA3ABB2)
    val Info = Color(0xFF1F6FB2)
    val Sim = Color(0xFF7B5CC4)
}

// ---------------------------------------------------------------- small line icons (24×24, 2dp stroke)
private fun lineIcon(name: String, build: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit) =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).path(
        stroke = SolidColor(Color.Black), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round, fill = null, pathBuilder = build
    ).build()

object HmIcons {
    val Home = lineIcon("home") { moveTo(4f, 11f); lineTo(12f, 4f); lineTo(20f, 11f); moveTo(6f, 9.5f); lineTo(6f, 20f); lineTo(18f, 20f); lineTo(18f, 9.5f); moveTo(10f, 20f); lineTo(10f, 14f); lineTo(14f, 14f); lineTo(14f, 20f) }
    val Log = lineIcon("log") { moveTo(6f, 3f); lineTo(18f, 3f); lineTo(18f, 21f); lineTo(6f, 21f); close(); moveTo(9f, 8f); lineTo(15f, 8f); moveTo(9f, 12f); lineTo(15f, 12f); moveTo(9f, 16f); lineTo(13f, 16f) }
    val Chart = lineIcon("chart") { moveTo(4f, 20f); lineTo(20f, 20f); moveTo(7f, 20f); lineTo(7f, 13f); moveTo(12f, 20f); lineTo(12f, 7f); moveTo(17f, 20f); lineTo(17f, 10f) }
    val Battery = lineIcon("battery") { moveTo(3f, 7f); lineTo(18f, 7f); lineTo(18f, 17f); lineTo(3f, 17f); close(); moveTo(21f, 10f); lineTo(21f, 14f); moveTo(6f, 10f); lineTo(6f, 14f); moveTo(9f, 10f); lineTo(9f, 14f) }
    val Settings = lineIcon("settings") { moveTo(4f, 7f); lineTo(20f, 7f); moveTo(4f, 17f); lineTo(20f, 17f); moveTo(9f, 4f); lineTo(9f, 10f); moveTo(15f, 14f); lineTo(15f, 20f) }
    val Lock = lineIcon("lock") { moveTo(6f, 11f); lineTo(18f, 11f); lineTo(18f, 20f); lineTo(6f, 20f); close(); moveTo(8.5f, 11f); lineTo(8.5f, 8f); curveTo(8.5f, 4f, 15.5f, 4f, 15.5f, 8f); lineTo(15.5f, 11f) }
    val Rest = lineIcon("rest") { moveTo(5f, 9f); lineTo(16f, 9f); lineTo(15f, 18f); lineTo(6f, 18f); close(); moveTo(16f, 11f); curveTo(20f, 11f, 20f, 15f, 16f, 15f); moveTo(9f, 3f); lineTo(9f, 6f); moveTo(12f, 3f); lineTo(12f, 6f) }
    val Alert = lineIcon("alert") { moveTo(12f, 3f); lineTo(22f, 20f); lineTo(2f, 20f); close(); moveTo(12f, 9f); lineTo(12f, 14f); moveTo(12f, 17f); lineTo(12f, 17.2f) }
    val Check = lineIcon("check") { moveTo(5f, 12.5f); lineTo(10f, 17f); lineTo(19f, 7f) }
    val Fuel = lineIcon("fuel") { moveTo(5f, 21f); lineTo(5f, 5f); curveTo(5f, 4f, 6f, 3f, 7f, 3f); lineTo(12f, 3f); curveTo(13f, 3f, 14f, 4f, 14f, 5f); lineTo(14f, 21f); moveTo(3f, 21f); lineTo(16f, 21f); moveTo(7f, 8f); lineTo(12f, 8f); moveTo(14f, 11f); lineTo(17f, 11f); lineTo(17f, 17f); curveTo(17f, 18.5f, 19.5f, 18.5f, 19.5f, 17f); lineTo(19.5f, 8f); lineTo(17f, 5.5f) }
}
