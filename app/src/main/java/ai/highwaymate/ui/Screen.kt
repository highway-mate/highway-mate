package ai.highwaymate.ui

import ai.highwaymate.Demo
import ai.highwaymate.HighwayMateApp
import ai.highwaymate.core.AlertMode
import ai.highwaymate.core.AppClock
import ai.highwaymate.core.Cruise
import ai.highwaymate.core.Guidance
import ai.highwaymate.core.Level
import ai.highwaymate.core.MIN
import ai.highwaymate.core.Purpose
import ai.highwaymate.core.RestAreas
import ai.highwaymate.core.Settings
import ai.highwaymate.core.Snapshot
import ai.highwaymate.core.Store
import ai.highwaymate.core.TripRecord
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class Dest(val label: String, val icon: ImageVector) {
    HOME("홈", HmIcons.Home), LOG("운행일지", HmIcons.Log), REPORT("리포트", HmIcons.Chart),
    EV("전기차", HmIcons.Battery), SETTINGS("설정", HmIcons.Settings)
}

@Composable
fun HighwayMateScreen(app: HighwayMateApp) {
    val s by Store.state.collectAsStateWithLifecycle()
    var now by remember { mutableLongStateOf(AppClock.now()) }
    LaunchedEffect(Unit) { while (true) { now = AppClock.now(); delay(500) } }
    var dest by remember { mutableStateOf(Dest.HOME) }
    LaunchedEffect(s.sim.tab) { s.sim.tab?.let { dest = Dest.entries.getOrElse(it) { Dest.HOME } } }
    LaunchedEffect(s.pendingClassification?.id) { if (s.pendingClassification != null) dest = Dest.LOG }

    val driving = !s.parked || s.uxRestricted
    val simActive = s.sim.speedKmh != null || s.sim.fuelPct != null || s.sim.gear != null || s.sim.cruise != null ||
        s.sim.daw || AppClock.factor != 1.0 || s.progressM > 0

    MaterialTheme(colorScheme = lightColorScheme(primary = Hm.SignGreen, background = Hm.Paper, surface = Hm.Surface)) {
        Box(Modifier.fillMaxSize().background(if (driving) Hm.Asphalt else Hm.Paper)) {
            Row(Modifier.fillMaxSize()) {
                Rail(dest, driving) { dest = it }
                Box(Modifier.weight(1f).fillMaxHeight().padding(start = 36.dp, end = 36.dp, top = 28.dp, bottom = 28.dp)) {
                    if (driving) DrivingScreen(app, s, now)
                    else when (dest) {
                        Dest.HOME -> HomeScreen(app, s, now)
                        Dest.LOG -> LogScreen(app, s)
                        Dest.REPORT -> ReportScreen(s, now)
                        Dest.EV -> EvScreen(app, s)
                        Dest.SETTINGS -> SettingsScreen(app)
                    }
                }
            }
            if (simActive || s.sim.panelVisible) {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(top = 28.dp, end = 36.dp).clip(RoundedCornerShape(50))
                        .background(Hm.Sim.copy(alpha = 0.14f)).clickable { Demo.run(app, "panel", if (s.sim.panelVisible) "off" else "on") }
                        .padding(horizontal = 18.dp, vertical = 8.dp)
                ) { Text("시연 모드", color = Hm.Sim, fontSize = 18.sp, fontWeight = FontWeight.SemiBold) }
            }
            if (s.sim.panelVisible) SimDrawer(app, s, Modifier.align(Alignment.CenterEnd))
            if (s.sim.caption.isNotBlank()) {
                Box(
                    Modifier.align(Alignment.BottomCenter).padding(bottom = 36.dp).clip(RoundedCornerShape(16.dp))
                        .background(Color(0xE6000000)).padding(horizontal = 40.dp, vertical = 18.dp)
                ) { Text(s.sim.caption, color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Medium) }
            }
        }
    }
}

// ================================================================ navigation rail
@Composable
private fun Rail(selected: Dest, driving: Boolean, onSelect: (Dest) -> Unit) {
    val bg = if (driving) Hm.AsphaltRaised else Hm.Surface
    Column(
        Modifier.width(150.dp).fillMaxHeight().background(bg).padding(vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // mark: a small green expressway sign
        Box(Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)).background(Hm.SignGreen), contentAlignment = Alignment.Center) {
            Text("HM", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.height(24.dp))
        Dest.entries.forEach { d ->
            val locked = driving && d != Dest.HOME
            val sel = d == selected && !driving || (driving && d == Dest.HOME)
            val fg = when {
                locked -> if (driving) Hm.OnAsphaltMuted.copy(alpha = 0.5f) else Hm.Slate
                sel -> if (driving) Hm.OnAsphalt else Hm.SignGreen
                else -> if (driving) Hm.OnAsphaltMuted else Hm.Slate
            }
            Box(
                Modifier.width(126.dp).clip(RoundedCornerShape(16.dp))
                    .background(if (sel) (if (driving) Hm.AsphaltLine else Hm.SignGreen.copy(alpha = 0.10f)) else Color.Transparent)
                    .clickable(enabled = !locked) { onSelect(d) }.padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(if (locked) HmIcons.Lock else d.icon, d.label, tint = fg, modifier = Modifier.size(34.dp))
                    Text(d.label, color = fg, fontSize = 19.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium)
                }
            }
        }
    }
}

// ================================================================ shared pieces
@Composable
private fun ScreenTitle(title: String, s: Snapshot, dark: Boolean = false, trailing: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(end = 180.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = if (dark) Hm.OnAsphalt else Hm.Ink, fontSize = 38.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(24.dp))
        CruiseChip(s, dark)
        Spacer(Modifier.weight(1f))
        trailing()
    }
}

@Composable
private fun CruiseChip(s: Snapshot, dark: Boolean) {
    val (txt, on) = when (s.cruise) {
        Cruise.ACTIVATED -> "크루즈 작동 중 · 예고 모드" to true
        Cruise.ENABLED -> "크루즈 대기" to false
        Cruise.FORCED_DEACTIVATION_WARNING -> "크루즈 해제 경고" to true
        Cruise.SUSPENDED -> "크루즈 일시 해제" to false
        else -> "크루즈 꺼짐" to false
    }
    val col = if (s.cruise == Cruise.FORCED_DEACTIVATION_WARNING) Hm.Danger else Hm.SignGreen
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(if (on) col else Color.Transparent)
            .border(2.dp, if (on) col else (if (dark) Hm.AsphaltLine else Hm.Line), RoundedCornerShape(50))
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) { Text(txt, color = if (on) Color.White else (if (dark) Hm.OnAsphaltMuted else Hm.Slate), fontSize = 20.sp, fontWeight = FontWeight.SemiBold) }
}

private fun levelColor(l: Level) = when (l) { Level.URGENT -> Hm.Danger; Level.CAUTION -> Hm.LaneYellow; Level.INFO -> Hm.SignGreen }

@Composable
private fun GuidanceBanner(g: Guidance, dark: Boolean, big: Boolean) {
    val c = levelColor(g.level)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(if (dark) Hm.AsphaltRaised else Hm.Surface),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(if (big) 120.dp else 96.dp).height(if (big) 150.dp else 104.dp).background(c), contentAlignment = Alignment.Center) {
            Icon(if (g.level == Level.INFO) HmIcons.Check else HmIcons.Alert, null,
                tint = if (g.level == Level.CAUTION) Hm.Ink else Color.White, modifier = Modifier.size(if (big) 56.dp else 44.dp))
        }
        Column(Modifier.weight(1f).padding(horizontal = 28.dp, vertical = 14.dp)) {
            Text(
                when (g.level) { Level.URGENT -> "위험"; Level.CAUTION -> "주의 구간"; Level.INFO -> "안내" },
                color = if (g.level == Level.CAUTION) Color(0xFFB98700) else c, fontSize = 20.sp, fontWeight = FontWeight.Bold
            )
            Text(g.text, color = if (dark) Hm.OnAsphalt else Hm.Ink, fontSize = if (big) 38.sp else 28.sp,
                fontWeight = FontWeight.SemiBold, lineHeight = if (big) 50.sp else 38.sp)
        }
    }
}

/** The next rest area drawn like a green expressway direction sign. */
@Composable
private fun RestSign(s: Snapshot, modifier: Modifier = Modifier) {
    val routeKm = s.progressM / 1000
    val next = RestAreas.next(routeKm, (s.fuelPct ?: 100) <= 30, false)
    Column(
        modifier.clip(RoundedCornerShape(10.dp)).background(Hm.SignGreen)
            .border(4.dp, Color.White.copy(alpha = 0.85f), RoundedCornerShape(10.dp)).padding(horizontal = 28.dp, vertical = 20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(HmIcons.Rest, null, tint = Color.White, modifier = Modifier.size(34.dp))
            Spacer(Modifier.width(12.dp))
            Text("다음 휴게소", color = Color.White.copy(alpha = 0.85f), fontSize = 21.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.weight(1f))
            if (next != null && next.fuel) Icon(HmIcons.Fuel, "주유", tint = Color.White, modifier = Modifier.size(30.dp))
            if (next != null && next.evCharger) Icon(HmIcons.Battery, "충전", tint = Color.White, modifier = Modifier.padding(start = 10.dp).size(30.dp))
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(next?.name ?: "경로에 휴게소 없음", color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1)
            if (next != null) {
                Text("%.0f".format(next.km - routeKm), color = Color.White, fontSize = 52.sp, fontWeight = FontWeight.Black)
                Text(" km", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
            }
        }
    }
}

/** Continuous driving time against the 2-hour line, with the 1h50m "announce" mark. */
@Composable
private fun DriveGauge(s: Snapshot, now: Long, dark: Boolean, modifier: Modifier = Modifier) {
    val cont = s.continuousMs(now)
    val frac = (cont.toFloat() / (120 * MIN)).coerceIn(0f, 1f)
    val col = when { cont >= 120 * MIN -> Hm.Danger; cont >= 110 * MIN -> Hm.LaneYellow; else -> Hm.SignGreen }
    val ink = if (dark) Hm.OnAsphalt else Hm.Ink
    val muted = if (dark) Hm.OnAsphaltMuted else Hm.Slate
    Column(modifier) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("연속 운전", color = muted, fontSize = 21.sp)
            Spacer(Modifier.weight(1f))
            Text(hm(cont), color = ink, fontSize = 34.sp, fontWeight = FontWeight.Bold)
            Text("  / 2시간", color = muted, fontSize = 21.sp, modifier = Modifier.padding(bottom = 4.dp))
        }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().height(18.dp).clip(RoundedCornerShape(9.dp)).background(if (dark) Hm.AsphaltLine else Hm.Line)) {
            Box(Modifier.fillMaxWidth(frac).fillMaxHeight().clip(RoundedCornerShape(9.dp)).background(col))
            Box(Modifier.fillMaxWidth(110f / 120f).fillMaxHeight(), contentAlignment = Alignment.CenterEnd) {
                Box(Modifier.width(3.dp).fillMaxHeight().background(ink.copy(alpha = 0.6f)))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            when {
                s.driveStartApp == null -> "출발하면 시간을 재기 시작해요"
                cont >= 120 * MIN -> "지금 쉬어 가세요"
                cont >= 110 * MIN -> "곧 2시간이에요. 다음 휴게소를 권해 드렸어요"
                else -> "1시간 50분이 되면 다음 휴게소를 알려 드려요"
            }, color = muted, fontSize = 19.sp
        )
    }
}

@Composable
private fun Panel(modifier: Modifier = Modifier, dark: Boolean = false, content: @Composable () -> Unit) {
    Box(modifier.clip(RoundedCornerShape(22.dp)).background(if (dark) Hm.AsphaltRaised else Hm.Surface).padding(28.dp)) { content() }
}

@Composable
private fun PrimaryButton(text: String, color: Color = Hm.SignGreen, textColor: Color = Color.White, modifier: Modifier = Modifier, height: Dp = 88.dp, size: TextUnit = 28.sp, onClick: () -> Unit) {
    Box(
        modifier.height(height).clip(RoundedCornerShape(18.dp)).background(color).clickable(onClick = onClick).padding(horizontal = 36.dp),
        contentAlignment = Alignment.Center
    ) { Text(text, color = textColor, fontSize = size, fontWeight = FontWeight.Bold) }
}

@Composable
private fun QuietButton(text: String, dark: Boolean = false, modifier: Modifier = Modifier, height: Dp = 88.dp, onClick: () -> Unit) {
    Box(
        modifier.height(height).clip(RoundedCornerShape(18.dp)).border(2.dp, if (dark) Hm.AsphaltLine else Hm.Line, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick).padding(horizontal = 32.dp),
        contentAlignment = Alignment.Center
    ) { Text(text, color = if (dark) Hm.OnAsphalt else Hm.Ink, fontSize = 26.sp, fontWeight = FontWeight.SemiBold) }
}

@Composable
private fun RestPromptRow(app: HighwayMateApp, s: Snapshot, dark: Boolean) {
    val p = s.prompt ?: return
    if (p.atApp >= 0) {
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
            PrimaryButton("${p.area.name} 경유지로 넣기", if (p.urgent) Hm.Danger else Hm.SignGreen, modifier = Modifier.width(620.dp), height = 104.dp, size = 32.sp) {
                app.bridge.addWaypoint(p.area); app.engine.onWaypointAccepted()
            }
            QuietButton("괜찮아요", dark, height = 104.dp) { Store.update { it.copy(prompt = null) } }
        }
    } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(HmIcons.Check, null, tint = Hm.SignGreen, modifier = Modifier.size(36.dp))
            Spacer(Modifier.width(12.dp))
            Text("${p.area.name}을 경유지로 넣었어요. 순정 내비가 경로를 다시 안내해요.", color = if (dark) Hm.OnAsphalt else Hm.Ink, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ================================================================ driving
@Composable
private fun DrivingScreen(app: HighwayMateApp, s: Snapshot, now: Long) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(26.dp)) {
        ScreenTitle("주행 중", s, dark = true) {
            Text("${"%.0f".format(s.speedKmh)}", color = Hm.OnAsphalt, fontSize = 44.sp, fontWeight = FontWeight.Bold)
            Text(" km/h", color = Hm.OnAsphaltMuted, fontSize = 22.sp)
        }
        val g = s.guidance
        if (g != null) GuidanceBanner(g, dark = true, big = true)
        else Panel(Modifier.fillMaxWidth(), dark = true) {
            Text(if (s.assistActive) "크루즈로 달리는 동안 앞길을 살피고, 위험 구간 전에 미리 알려 드려요." else "주행 중에는 소리와 내비 위 알림으로만 알려 드려요.",
                color = Hm.OnAsphalt, fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
        }
        RestPromptRow(app, s, dark = true)
        Panel(Modifier.fillMaxWidth().weight(1f), dark = true) {
            Column(Modifier.fillMaxSize()) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(if (s.route.destName.isBlank()) "앞길" else "${s.route.destName}까지 ", color = Hm.OnAsphalt, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    if (s.route.totalM > 0) {
                        Text("%.0f".format(s.remainM() / 1000), color = Hm.OnAsphalt, fontSize = 40.sp, fontWeight = FontWeight.Black)
                        Text(" km", color = Hm.OnAsphaltMuted, fontSize = 24.sp, modifier = Modifier.padding(bottom = 4.dp))
                    }
                }
                RouteStrip(s, dark = true, windowKm = 30.0, modifier = Modifier.weight(1f), height = Dp.Unspecified)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(26.dp)) {
            RestSign(s, Modifier.weight(1f))
            Panel(Modifier.weight(1f), dark = true) { DriveGauge(s, now, dark = true) }
        }
    }
}

// ================================================================ home (parked)
@Composable
private fun HomeScreen(app: HighwayMateApp, s: Snapshot, now: Long) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        ScreenTitle("하이웨이 메이트", s)
        s.guidance?.let { GuidanceBanner(it, dark = false, big = false) }
        Panel(Modifier.fillMaxWidth()) {
            Column {
                if (s.route.destName.isBlank()) {
                    Text("내비에서 목적지를 정하면 앞길의 정체 구간과 휴게소가 여기에 보여요.", color = Hm.Slate, fontSize = 26.sp)
                    RouteStrip(s, dark = false, windowKm = null, height = 120.dp)
                } else {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("${s.route.destName}까지 ", color = Hm.Ink, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                        if (s.route.totalM > 0) {
                            Text("%.0f".format(s.remainM() / 1000), color = Hm.Ink, fontSize = 44.sp, fontWeight = FontWeight.Black)
                            Text(" km", color = Hm.Ink, fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
                        } else Text("경로를 받는 중", color = Hm.Slate, fontSize = 24.sp, modifier = Modifier.padding(bottom = 4.dp))
                        Spacer(Modifier.weight(1f))
                        if (s.route.totalCongestionM > 0) Text("경로상 정체 ${"%.0f".format(s.route.totalCongestionM / 1000)} km", color = Color(0xFF9A6F00), fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                    }
                    RouteStrip(s, dark = false, windowKm = null, height = 150.dp)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                RestSign(s, Modifier.fillMaxWidth())
                Panel(Modifier.fillMaxWidth()) { DriveGauge(s, now, dark = false) }
            }
            Panel(Modifier.weight(1.15f)) { TripNow(app, s, now) }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(56.dp), verticalAlignment = Alignment.Bottom) {
            Text("오늘", color = Hm.Ink, fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 10.dp))
            BigStat("크루즈로 달린 시간", hmShort(s.today.assistMs), "")
            BigStat("미리 알린 위험 구간", "${s.today.forecasts}", "번")
            BigStat("쉬어 간 횟수", "${s.today.rests}", "번")
        }
    }
}

@Composable
private fun TripNow(app: HighwayMateApp, s: Snapshot, now: Long) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        val p = s.pendingClassification
        when {
            p != null -> {
                Text("방금 운행을 기록했어요", color = Hm.Ink, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Text("${p.destination}  ${p.distanceKm?.let { "%.1f km".format(it) } ?: ""}", color = Hm.Slate, fontSize = 24.sp)
                Text("업무로 다녀오셨나요?", color = Hm.Ink, fontSize = 26.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    PrimaryButton("업무", modifier = Modifier.width(240.dp)) { app.engine.classify(Purpose.BUSINESS) }
                    QuietButton("개인", modifier = Modifier.width(240.dp)) { app.engine.classify(Purpose.PERSONAL) }
                }
            }
            s.restingAt != null -> {
                Text("${s.restingAt}에서 쉬는 중", color = Hm.SignGreen, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Text("10분 넘게 쉬면 연속 운전 시간이 처음부터 다시 시작돼요.", color = Hm.Slate, fontSize = 22.sp)
                TripFacts(s, now)
            }
            s.trip != null -> {
                Text("이번 운행", color = Hm.Ink, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                TripFacts(s, now)
            }
            else -> {
                Text("이번 운행", color = Hm.Ink, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Text("출발하면 계기판 누적거리부터 자동으로 기록해요. 도착해서 P단에 두면 운행일지에 한 줄이 생겨요.", color = Hm.Slate, fontSize = 23.sp, lineHeight = 32.sp)
                Fact("지금 계기판", odo(s.odometerKm))
            }
        }
    }
}

@Composable
private fun TripFacts(s: Snapshot, now: Long) {
    val d = s.trip ?: return
    Fact("출발 계기판", odo(d.startOdoKm))
    Fact("지금 계기판", odo(s.odometerKm))
    Fact("출발 후", hm(now - d.startApp))
    if (d.notes.isNotEmpty()) Fact("함께 기록", d.notes.joinToString(", "))
}

@Composable
private fun Fact(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Hm.Slate, fontSize = 22.sp, modifier = Modifier.width(200.dp))
        Text(value, color = Hm.Ink, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ================================================================ trip log + monthly export
@Composable
private fun LogScreen(app: HighwayMateApp, s: Snapshot) {
    val month = SimpleDateFormat("yyyy-MM", Locale.KOREA).format(Date(AppClock.now()))
    val monthLabel = SimpleDateFormat("M월", Locale.KOREA).format(Date(AppClock.now()))
    val trips = remember(s.pendingClassification, s.trip, s.guidance) { app.tripLog.trips.reversed() }
    val csv = remember(trips) { app.tripLog.monthCsv(month) }
    val qr = remember(csv) { qr(csv, 520) }
    val total = trips.sumOf { (it.distanceKm ?: 0f).toDouble() }
    val business = trips.filter { it.purpose == Purpose.BUSINESS || it.purpose == Purpose.COMMUTE }.sumOf { (it.distanceKm ?: 0f).toDouble() }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        ScreenTitle("$monthLabel 운행일지", s)
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(Modifier.weight(1.5f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(48.dp)) {
                    BigStat("총 주행", "%.1f".format(total), "km")
                    BigStat("업무", "%.1f".format(business), "km")
                    BigStat("업무사용비율", if (total > 0) "%.0f".format(business / total * 100) else "-", "%")
                }
                s.pendingClassification?.let { p ->
                    Panel(Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("${p.destination}  ${p.distanceKm?.let { "%.1f km".format(it) } ?: ""}", color = Hm.Ink, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                                Text("업무인지 개인인지 한 번만 골라 주세요", color = Hm.Slate, fontSize = 22.sp)
                            }
                            PrimaryButton("업무", modifier = Modifier.width(200.dp)) { app.engine.classify(Purpose.BUSINESS) }
                            Spacer(Modifier.width(14.dp))
                            QuietButton("개인", modifier = Modifier.width(200.dp)) { app.engine.classify(Purpose.PERSONAL) }
                        }
                    }
                }
                Panel(Modifier.fillMaxWidth().weight(1f)) {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        LedgerHeader()
                        if (trips.isEmpty()) Text("아직 기록이 없어요. 출발하면 자동으로 시작돼요.", color = Hm.Slate, fontSize = 22.sp, modifier = Modifier.padding(top = 20.dp))
                        trips.take(12).forEach { LedgerRow(it) }
                    }
                }
            }
            Panel(Modifier.weight(1f).fillMaxHeight()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("월말 내보내기", color = Hm.Ink, fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
                    Text("국세청 업무용승용차 운행기록부 항목에 맞춘 표예요.", color = Hm.Slate, fontSize = 21.sp, modifier = Modifier.fillMaxWidth())
                    if (qr != null) Image(qr, "운행기록부 QR", Modifier.size(400.dp).clip(RoundedCornerShape(16.dp)).border(2.dp, Hm.Line, RoundedCornerShape(16.dp)).background(Color.White).padding(14.dp))
                    Text("휴대폰 카메라로 찍으면 $monthLabel 기록을 CSV(엑셀) 파일로 받아요", color = Hm.Ink, fontSize = 22.sp, textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold)
                    Text("기록은 차 안에만 저장되고 서버를 거치지 않아요", color = Hm.SignGreen, fontSize = 20.sp)
                }
            }
        }
    }
}

@Composable
private fun BigStat(label: String, value: String, unit: String, dark: Boolean = false) {
    Column {
        Text(label, color = if (dark) Hm.OnAsphaltMuted else Hm.Slate, fontSize = 21.sp)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, color = if (dark) Hm.OnAsphalt else Hm.Ink, fontSize = 48.sp, fontWeight = FontWeight.Black)
            Text(" $unit", color = if (dark) Hm.OnAsphaltMuted else Hm.Slate, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp))
        }
    }
}

private val colWeights = listOf(1.0f, 1.25f, 1.25f, 1.0f, 0.75f)

@Composable
private fun LedgerHeader() {
    Row(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
        listOf("날짜", "출발 계기판", "도착 계기판", "거리", "구분").forEachIndexed { i, t ->
            Text(t, color = Hm.Slate, fontSize = 19.sp, modifier = Modifier.weight(colWeights[i]), maxLines = 1)
        }
    }
    Box(Modifier.fillMaxWidth().height(2.dp).background(Hm.Ink))
}

@Composable
private fun LedgerRow(r: TripRecord) {
    val f = SimpleDateFormat("M/d (E)", Locale.KOREA)
    Column(Modifier.padding(vertical = 14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(f.format(Date(r.startApp)), color = Hm.Ink, fontSize = 22.sp, modifier = Modifier.weight(colWeights[0]), maxLines = 1)
            Text(odo(r.startOdoKm), color = Hm.Ink, fontSize = 22.sp, modifier = Modifier.weight(colWeights[1]), maxLines = 1)
            Text(odo(r.endOdoKm), color = Hm.Ink, fontSize = 22.sp, modifier = Modifier.weight(colWeights[2]), maxLines = 1)
            Text(r.distanceKm?.let { "%.1f km".format(it) } ?: "-", color = Hm.Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(colWeights[3]), maxLines = 1)
            val pc = when (r.purpose) { Purpose.BUSINESS, Purpose.COMMUTE -> Hm.SignGreen; Purpose.PERSONAL -> Hm.Slate; else -> Color(0xFF9A6F00) }
            Box(Modifier.weight(colWeights[4])) {
                Box(Modifier.clip(RoundedCornerShape(8.dp)).background(pc.copy(alpha = 0.12f)).padding(horizontal = 12.dp, vertical = 4.dp)) {
                    Text(r.purpose.label, color = pc, fontSize = 19.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
        }
        Text((listOf(r.destination) + r.notes).joinToString(", "), color = Hm.Slate, fontSize = 19.sp, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(Hm.Line))
}

// ================================================================ report
@Composable
private fun ReportScreen(s: Snapshot, now: Long) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(28.dp)) {
        ScreenTitle("오늘의 운전", s)
        Panel(Modifier.fillMaxWidth()) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                BigStat("크루즈로 달린 시간", hmShort(s.today.assistMs), "")
                BigStat("미리 알린 위험 구간", "${s.today.forecasts}", "번")
                BigStat("쉬어 간 횟수", "${s.today.rests}", "번")
                BigStat("크루즈 강제 해제 경고", "${s.today.forcedDeactivations}", "번")
            }
        }
        Panel(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val longest = s.today.longestContinuousMs
                Text("가장 길게 쉬지 않고 달린 시간", color = Hm.Slate, fontSize = 22.sp)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(hm(longest), color = if (longest >= 120 * MIN) Hm.Danger else Hm.Ink, fontSize = 48.sp, fontWeight = FontWeight.Black)
                    Spacer(Modifier.width(18.dp))
                    Text(if (longest >= 120 * MIN) "2시간을 넘겼어요. 다음엔 1시간 50분 알림에서 쉬어 가요." else "2시간 안에서 쉬어 갔어요.",
                        color = Hm.Slate, fontSize = 22.sp, modifier = Modifier.padding(bottom = 10.dp))
                }
                Box(Modifier.fillMaxWidth().height(22.dp).clip(RoundedCornerShape(11.dp)).background(Hm.Line)) {
                    Box(Modifier.fillMaxWidth((longest.toFloat() / (150 * MIN)).coerceIn(0f, 1f)).fillMaxHeight().background(if (longest >= 120 * MIN) Hm.Danger else Hm.SignGreen))
                    Box(Modifier.fillMaxWidth(120f / 150f).fillMaxHeight(), contentAlignment = Alignment.CenterEnd) { Box(Modifier.width(3.dp).fillMaxHeight().background(Hm.Ink)) }
                }
                Text("세로선이 2시간이에요", color = Hm.Slate, fontSize = 18.sp)
            }
        }
        Text("이 화면은 운전자 본인만 봐요. 회사로 보내는 건 운행일지(거리·업무 구분)뿐이에요.", color = Hm.Slate, fontSize = 21.sp)
    }
}

// ================================================================ EV extension ⑤
@Composable
private fun EvScreen(app: HighwayMateApp, s: Snapshot) {
    var local by remember { mutableStateOf("") }
    val result = local.ifBlank { s.ev.lastResult }
    val applied = s.ev.appliedLevel
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        ScreenTitle("배터리 절전", s)
        Panel(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (s.route.destName.isBlank()) "목적지에 도착하면" else "${s.route.destName}에 도착하면", color = Hm.Slate, fontSize = 24.sp)
                    val est = s.ev.estSocAtDest
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(est?.let { "$it" } ?: "-", color = when { (est ?: 100) < 10 -> Hm.Danger; (est ?: 100) < 20 -> Color(0xFF9A6F00); else -> Hm.Ink }, fontSize = 72.sp, fontWeight = FontWeight.Black)
                        Text(" % 남아요", color = Hm.Ink, fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 14.dp))
                    }
                    Text("지금 ${s.socPct?.let { "$it%" } ?: "-"}  ·  경로 ${s.ev.tripKm?.let { "%.0f km".format(it) } ?: "-"}  ·  내비가 계산한 값이에요", color = Hm.Slate, fontSize = 21.sp)
                }
                QuietButton("다시 계산") { app.bridge.requestTrip() }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            SaveOption("중간 절전", "공조 온도 2℃ 낮춤, 풍량 최저, 앰비언트 조명·실내등 끄기", applied == "mid", Modifier.weight(1f)) {
                app.powerSave.apply("mid") { r -> local = r; Store.update { it.copy(ev = it.ev.copy(appliedLevel = "mid", lastResult = r)) } }
            }
            SaveOption("최대 절전", "중간 절전에 더해 에어컨 끄기, 실내 대신 운전석 열선으로", applied == "max", Modifier.weight(1f)) {
                app.powerSave.apply("max") { r -> local = r; Store.update { it.copy(ev = it.ev.copy(appliedLevel = "max", lastResult = r)) } }
            }
            SaveOption("원래대로", "절전 전 설정으로 되돌려요", applied == null, Modifier.weight(1f)) {
                app.powerSave.restore { r -> local = r; Store.update { it.copy(ev = it.ev.copy(appliedLevel = null, lastResult = r)) } }
            }
        }
        if (result.isNotBlank()) Text(result, color = Hm.SignGreen, fontSize = 21.sp)
        Text("김서림 제거와 전조등은 건드리지 않아요. 주행 모드와 회생제동은 앱이 바꿀 수 없어서, 필요하면 ECO 모드를 권해 드려요.", color = Hm.Slate, fontSize = 21.sp)
        Text("전기차 확장 기능이에요. 차량 제어 SDK가 열리면 제공해요.", color = Hm.Slate, fontSize = 19.sp)
    }
}

@Composable
private fun SaveOption(title: String, detail: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(20.dp)).background(if (selected) Hm.SignGreen else Hm.Surface)
            .border(2.dp, if (selected) Hm.SignGreen else Hm.Line, RoundedCornerShape(20.dp)).clickable(onClick = onClick).padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(title, color = if (selected) Color.White else Hm.Ink, fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Text(detail, color = if (selected) Color.White.copy(alpha = 0.85f) else Hm.Slate, fontSize = 21.sp, lineHeight = 30.sp)
    }
}

// ================================================================ settings
@Composable
private fun SettingsScreen(app: HighwayMateApp) {
    val p by Settings.prefs.collectAsStateWithLifecycle()
    var taps by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        ScreenTitle("알림 설정", Store.now)
        Text("어떻게 알려 드릴까요", color = Hm.Ink, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            AlertMode.entries.forEach { m ->
                SaveOption(m.label, m.detail, p.mode == m, Modifier.weight(1f)) { Settings.set { it.copy(mode = m) } }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("미리 듣기", color = Hm.Slate, fontSize = 22.sp)
            PrimaryButton("주의 구간", Hm.LaneYellow, Hm.Ink, height = 64.dp, size = 22.sp) { app.voice.say("2km 앞 정체 구간이에요.", Level.CAUTION) }
            PrimaryButton("위험", Hm.Danger, height = 64.dp, size = 22.sp) { app.voice.say("크루즈가 곧 해제돼요. 직접 운전하세요.", Level.URGENT) }
        }
        Text("무엇을 알려 드릴까요", color = Hm.Ink, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Panel(Modifier.fillMaxWidth()) {
            Column {
                Toggle("내비 위에 알림 카드 띄우기", "지도를 보면서 카드로 확인하고, 휴게소 경유지도 카드에서 바로 넣어요", p.headsUp) { v -> Settings.set { it.copy(headsUp = v) } }
                Toggle("경로상 정체 구간", "크루즈 작동 중, 정체 2km 전에", p.congestion) { v -> Settings.set { it.copy(congestion = v) } }
                Toggle("요금소·분기점·휴게소 진입로", "크루즈 작동 중, 1.5km 전에", p.tollJunction) { v -> Settings.set { it.copy(tollJunction = v) } }
                Toggle("사고 다발·공사·안개 구간", "내비 주의 지점, 1.5km 전에", p.cautionZones) { v -> Settings.set { it.copy(cautionZones = v) } }
                Toggle("휴식 권유", "연속 운전 1시간 50분과 2시간, 운전자 주의 경고", p.rest, last = true) { v -> Settings.set { it.copy(rest = v) } }
            }
        }
        Text("같은 알림은 10분에 한 번, 모든 알림을 합쳐 1시간에 4번까지만 알려요. 위험 알림은 예외예요.", color = Hm.Slate, fontSize = 20.sp)
        Text("하이웨이 메이트 0.1.0", color = Hm.Slate.copy(alpha = 0.7f), fontSize = 18.sp,
            modifier = Modifier.clickable { taps++; if (taps >= 5) { taps = 0; Demo.run(app, "panel", "on") } })
    }
}

@Composable
private fun Toggle(title: String, detail: String, on: Boolean, last: Boolean = false, onChange: (Boolean) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth().clickable { onChange(!on) }.padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, color = Hm.Ink, fontSize = 25.sp, fontWeight = FontWeight.SemiBold)
                Text(detail, color = Hm.Slate, fontSize = 20.sp)
            }
            Switch(checked = on, onCheckedChange = onChange,
                colors = SwitchDefaults.colors(checkedTrackColor = Hm.SignGreen, checkedThumbColor = Color.White))
        }
        if (!last) Box(Modifier.fillMaxWidth().height(1.dp).background(Hm.Line))
    }
}

// ================================================================ demo drawer (hidden by default)
@Composable
private fun SimDrawer(app: HighwayMateApp, s: Snapshot, modifier: Modifier) {
    Column(
        modifier.padding(top = 90.dp, bottom = 28.dp, end = 28.dp).width(470.dp).fillMaxHeight().clip(RoundedCornerShape(22.dp))
            .background(Color(0xFF1C1A24)).padding(24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("시연 패널", color = Color(0xFFCDB8FF), fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text("닫기", color = Color(0xFFCDB8FF), fontSize = 20.sp, modifier = Modifier.clickable { Demo.run(app, "panel", "off") })
        }
        Text("SDK는 실제 Pleos SDK 값, SIM은 시연용으로 넣은 값이에요", color = Color(0xFFA39DB5), fontSize = 17.sp)
        SimRow("기어", s.gear.name, s.sim.gear == null)
        SimRow("크루즈", s.cruise.name, s.sim.cruise == null)
        SimRow("계기판", odo(s.odometerKm), true)
        SimRow("속도", "${"%.0f".format(s.speedKmh)} km/h", s.sim.speedKmh == null)
        SimRow("연료", s.fuelPct?.let { "$it%" } ?: "-", s.sim.fuelPct == null)
        SimRow("배터리", s.socPct?.let { "$it%" } ?: "-", true)
        SimRow("주의 경고", if (s.daw) "켜짐" else "꺼짐", !s.sim.daw)
        SimRow("정체 구간", "${s.route.congestion.size}곳", true)
        SimRow("경로 진행", "${"%.1f".format(s.progressM / 1000)} km", false)
        SimRow("시간 배속", "×${"%.0f".format(AppClock.factor)}", false)
        Spacer(Modifier.height(6.dp))
        listOf(
            listOf("경로: 대전" to { Demo.run(app, "route", null) }, "내비 새로고침" to { Demo.run(app, "navi", null) }),
            listOf("속도 100" to { Demo.run(app, "speed", "100") }, "속도 0" to { Demo.run(app, "speed", "0") }, "SDK 속도" to { Demo.run(app, "speed", null) }),
            listOf("연료 28%" to { Demo.run(app, "fuel", "28") }, "주유 75%" to { Demo.run(app, "fuel", "75") }, "주의 경고" to { Demo.run(app, "daw", if (s.sim.daw) "off" else "on") }),
            listOf("×1" to { Demo.run(app, "time", "1") }, "×60" to { Demo.run(app, "time", "60") }, "운전 1:45" to { Demo.run(app, "drive", "105") }),
            listOf("기어 D" to { Demo.run(app, "gear", "D") }, "기어 P" to { Demo.run(app, "gear", "P") }, "SDK 기어" to { Demo.run(app, "gear", null) }),
            listOf("시연 초기화" to { Demo.run(app, "reset", null) }),
        ).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (label, act) ->
                    Box(Modifier.clip(RoundedCornerShape(12.dp)).border(1.dp, Color(0xFF4A4458), RoundedCornerShape(12.dp)).clickable { act() }.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text(label, color = Color(0xFFE6E0F0), fontSize = 18.sp)
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        s.log.take(6).forEach { Text(it, color = Color(0xFF8E889E), fontSize = 15.sp, maxLines = 2) }
    }
}

@Composable
private fun SimRow(label: String, value: String, sdk: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color(0xFFA39DB5), fontSize = 19.sp, modifier = Modifier.width(140.dp))
        Text(value, color = Color(0xFFF2EEF9), fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1)
        Text(if (sdk) "SDK" else "SIM", color = if (sdk) Color(0xFF7FD6AE) else Color(0xFFCDB8FF), fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

// ================================================================ helpers
private fun hm(ms: Long): String { val m = ms / MIN; return if (m / 60 > 0) "${m / 60}시간 ${m % 60}분" else "${m % 60}분" }
private fun hmShort(ms: Long): String { val m = ms / MIN; return "${m / 60}:${"%02d".format(m % 60)}" }
private fun odo(km: Float?) = km?.let { "%,.0f km".format(it) } ?: "-"

private fun qr(text: String, size: Int): ImageBitmap? = runCatching {
    val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.CHARACTER_SET to "UTF-8", EncodeHintType.MARGIN to 1))
    val bmp = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.RGB_565)
    for (x in 0 until size) for (y in 0 until size) bmp.setPixel(x, y, if (m[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
    bmp.asImageBitmap()
}.getOrNull()
