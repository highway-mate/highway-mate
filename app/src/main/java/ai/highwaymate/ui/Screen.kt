package ai.highwaymate.ui

import ai.highwaymate.Demo
import ai.highwaymate.HighwayMateApp
import ai.highwaymate.core.AppClock
import ai.highwaymate.core.Cruise
import ai.highwaymate.core.Gear
import ai.highwaymate.core.Level
import ai.highwaymate.core.MIN
import ai.highwaymate.core.Purpose
import ai.highwaymate.core.RestAreas
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.font.FontWeight
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

// ---------------------------------------------------------------- palette
private val Bg = Color(0xFF0E1621)
private val Surface1 = Color(0xFF16222F)
private val Surface2 = Color(0xFF1D2C3C)
private val Line = Color(0xFF2B3E52)
private val Ink = Color(0xFFEAF0F6)
private val Muted = Color(0xFF93A4B6)
private val Accent = Color(0xFF4FA3FF)
private val Ok = Color(0xFF4CC38A)
private val Warn = Color(0xFFF2C14E)
private val Danger = Color(0xFFFF6B5E)
private val SimTint = Color(0xFFB98CFF)

@Composable
fun HighwayMateScreen(app: HighwayMateApp) {
    val s by Store.state.collectAsStateWithLifecycle()
    var now by remember { mutableLongStateOf(AppClock.now()) }
    LaunchedEffect(Unit) { while (true) { now = AppClock.now(); delay(500) } }

    MaterialTheme(colorScheme = darkColorScheme(primary = Accent, background = Bg, surface = Surface1)) {
        Box(Modifier.fillMaxSize().background(Bg)) {
            Row(Modifier.fillMaxSize().padding(28.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    Header(s)
                    StatusRow(s, now)
                    GuidanceCard(app, s)
                    if (s.parked && !s.uxRestricted) ParkedArea(app, s, now)
                    else DrivingNote(s, now)
                }
                if (s.sim.panelVisible) SimPanel(app, s, now)
            }
            if (s.sim.caption.isNotBlank()) {
                Box(
                    Modifier.align(Alignment.BottomCenter).padding(bottom = 36.dp)
                        .clip(RoundedCornerShape(16.dp)).background(Color(0xE6000000))
                        .padding(horizontal = 40.dp, vertical = 18.dp)
                ) { Text(s.sim.caption, color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Medium) }
            }
        }
    }
}

@Composable
private fun Header(s: Snapshot) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("하이웨이 메이트", color = Ink, fontSize = 40.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(20.dp))
        Text("고속도로 안전 코치 · 자동 운행일지", color = Muted, fontSize = 24.sp)
        Spacer(Modifier.weight(1f))
        val mode = if (s.assistActive) "예고 모드 켜짐" else if (s.parked) "정차 중" else "주행 중"
        Pill("${"%.0f".format(s.speedKmh)} km/h", Ink)
        Spacer(Modifier.width(12.dp))
        Pill(mode, if (s.assistActive) Accent else Muted)
    }
}

@Composable
private fun StatusRow(s: Snapshot, now: Long) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        val cruiseTxt = when (s.cruise) {
            Cruise.ACTIVATED -> "작동 중"
            Cruise.ENABLED -> "대기"
            Cruise.FORCED_DEACTIVATION_WARNING -> "해제 경고"
            Cruise.SUSPENDED -> "일시 해제"
            Cruise.USER_OVERRIDE -> "운전자 개입"
            else -> "꺼짐"
        }
        Tile("스마트 크루즈", cruiseTxt, if (s.cruise == Cruise.ACTIVATED) Accent else Ink, Modifier.weight(1f))
        val cont = s.continuousMs(now)
        val contColor = when { cont >= 120 * MIN -> Danger; cont >= 110 * MIN -> Warn; else -> Ink }
        Tile("연속 운전", hm(cont), contColor, Modifier.weight(1.1f))
        val routeKm = s.progressM / 1000
        val next = RestAreas.next(routeKm, false, false)
        Tile("다음 휴게소", next?.let { "${it.name.removeSuffix("휴게소")} ${"%.0f".format(it.km - routeKm)}km" } ?: "-", Ink, Modifier.weight(1.2f))
        val energyLabel = if (s.fuelPct != null) "연료" else "배터리"
        val energy = (s.fuelPct ?: s.socPct)?.let { "$it%" } ?: "-"
        Tile(energyLabel, energy, if ((s.fuelPct ?: 100) <= 30) Warn else Ink, Modifier.weight(0.7f))
    }
}

@Composable
private fun GuidanceCard(app: HighwayMateApp, s: Snapshot) {
    val g = s.guidance
    val color = when (g?.level) { Level.URGENT -> Danger; Level.CAUTION -> Warn; else -> Accent }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Surface1)
            .border(3.dp, if (g != null) color else Line, RoundedCornerShape(24.dp)).padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        if (g != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.clip(RoundedCornerShape(10.dp)).background(color).padding(horizontal = 18.dp, vertical = 6.dp)) {
                    Text(when (g.level) { Level.URGENT -> "위험"; Level.CAUTION -> "주의 구간"; else -> "안내" },
                        color = if (g.level == Level.CAUTION) Color.Black else Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
                Text(when {
                    g.kind == "congestion" -> "경로상 정체"
                    g.kind == "route-event" -> "요금소·분기·주의 지점"
                    g.kind.startsWith("rest") -> "휴식 코치"
                    g.kind == "forced" -> "크루즈 강제 해제"
                    g.kind == "long-cruise" -> "크루즈 장시간 사용"
                    else -> "하이웨이 메이트"
                }, color = Muted, fontSize = 22.sp)
            }
        }
        Text(
            when {
                g == null && s.assistActive -> "크루즈 주행 중이에요. 위험 구간 전에 미리 알려 드릴게요."
                g == null -> "출발하면 안전 코치와 운행일지가 자동으로 시작돼요."
                else -> g.text
            },
            color = Ink, fontSize = 38.sp, fontWeight = FontWeight.SemiBold, lineHeight = 50.sp
        )
        val p = s.prompt
        if (p != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                if (p.atApp >= 0) {
                    Button(
                        onClick = { app.bridge.addWaypoint(p.area); app.engine.onWaypointAccepted() },
                        colors = ButtonDefaults.buttonColors(containerColor = if (p.urgent) Danger else Accent, contentColor = Color.White),
                        modifier = Modifier.height(96.dp).width(560.dp), shape = RoundedCornerShape(20.dp)
                    ) { Text("${p.area.name} 경유지로 넣기", fontSize = 32.sp, fontWeight = FontWeight.Bold) }
                    OutlinedButton(onClick = { Store.update { it.copy(prompt = null) } }, modifier = Modifier.height(96.dp)) {
                        Text("괜찮아요", fontSize = 28.sp, color = Muted)
                    }
                } else {
                    Pill("✓ ${p.area.name} 경유지 추가됨 · 순정 내비 경로에 반영", Ok)
                }
            }
        }
    }
}

@Composable
private fun DrivingNote(s: Snapshot, now: Long) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Surface1).padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("주행 중에는 상태 카드와 버튼 하나만 보여요. 리포트·운행일지·내보내기는 정차 중에 열려요.", color = Muted, fontSize = 24.sp)
        val c = s.route.congestion.firstOrNull { it.offsetM - s.progressM > 0 }
        val rem = s.remainM()
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (s.route.destName.isNotBlank()) Pill("목적지 ${s.route.destName} · ${"%.0f".format(rem / 1000)}km", Ink)
            if (c != null) Pill("다음 정체 ${"%.1f".format((c.offsetM - s.progressM) / 1000)}km 앞 ${c.road}", Warn)
            if (s.route.waypoints.isNotEmpty()) Pill("경유 ${s.route.waypoints.joinToString()}", Ok)
        }
    }
}

// ---------------------------------------------------------------- parked area: report, log, export, EV
@Composable
private fun ParkedArea(app: HighwayMateApp, s: Snapshot, now: Long) {
    var tab by remember { mutableStateOf(0) }
    LaunchedEffect(s.pendingClassification?.id) { if (s.pendingClassification != null) tab = 1 }
    LaunchedEffect(s.sim.tab) { s.sim.tab?.let { tab = it } }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf("오늘 리포트", "운행일지", "월말 내보내기", "전기차 절전 ⑤", "알림 설정").forEachIndexed { i, t ->
                val sel = i == tab
                Box(
                    Modifier.clip(RoundedCornerShape(14.dp)).background(if (sel) Accent else Surface2)
                        .clickable { tab = i }
                        .padding(horizontal = 28.dp, vertical = 14.dp)
                ) {
                    Text(t, color = if (sel) Color.White else Muted, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(20.dp)).background(Surface1).padding(28.dp)) {
            when (tab) {
                0 -> Report(s, now)
                1 -> TripsTab(app, s)
                2 -> ExportTab(app)
                3 -> EvTab(app, s)
                else -> SettingsTab(app)
            }
        }
    }
}

@Composable
private fun Report(s: Snapshot, now: Long) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        if (s.restingAt != null) {
            Text("${s.restingAt}에서 쉬는 중 · 목적지까지 ${"%.0f".format(s.remainM() / 1000)}km 남았어요", color = Ok, fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
        }
        Text("오늘의 운전 리포트 (운전자 본인만 봐요)", color = Ink, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Tile("크루즈 사용", hm(s.today.assistMs), Ink, Modifier.weight(1f), Surface2)
            Tile("연속 운전 최장", hm(s.today.longestContinuousMs), if (s.today.longestContinuousMs >= 120 * MIN) Warn else Ink, Modifier.weight(1f), Surface2)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Tile("위험 구간 예고", "${s.today.forecasts}번", Ink, Modifier.weight(1f), Surface2)
            Tile("강제 해제 경고", "${s.today.forcedDeactivations}번", if (s.today.forcedDeactivations > 0) Warn else Ink, Modifier.weight(1f), Surface2)
            Tile("휴식", "${s.today.rests}번", Ink, Modifier.weight(1f), Surface2)
        }
        val d = s.trip
        if (d != null) Text("진행 중인 운행: 출발 계기판 ${d.startOdoKm?.let { "%,.0f km".format(it) } ?: "-"} · ${hm(now - d.startApp)} 경과", color = Muted, fontSize = 24.sp)
    }
}

@Composable
private fun TripsTab(app: HighwayMateApp, s: Snapshot) {
    val trips = remember(s.pendingClassification, s.trip, s.log.size) { app.tripLog.trips.reversed() }
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        val p = s.pendingClassification
        if (p != null) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Surface2).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("이번 운행: ${p.destination} · ${p.distanceKm?.let { "%.1f km".format(it) } ?: "-"} (계기판 ${odo(p.startOdoKm)} → ${odo(p.endOdoKm)})",
                    color = Ink, fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    Button(onClick = { app.engine.classify(Purpose.BUSINESS) }, modifier = Modifier.height(88.dp).width(300.dp)) { Text("업무", fontSize = 32.sp) }
                    OutlinedButton(onClick = { app.engine.classify(Purpose.PERSONAL) }, modifier = Modifier.height(88.dp).width(300.dp)) { Text("개인", fontSize = 32.sp) }
                }
            }
        }
        Text("운행일지 (계기판 누적거리 기준 · 기기 안에만 저장)", color = Ink, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        if (trips.isEmpty()) Text("아직 기록이 없어요. 출발하면 자동으로 시작돼요.", color = Muted, fontSize = 24.sp)
        trips.take(8).forEach { TripRow(it) }
    }
}

@Composable
private fun TripRow(r: TripRecord) {
    val f = SimpleDateFormat("M/d(E) HH:mm", Locale.KOREA)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Surface2).padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically
    ) {
        Text(f.format(Date(r.startApp)), color = Muted, fontSize = 22.sp, modifier = Modifier.width(220.dp))
        Text("${odo(r.startOdoKm)} → ${odo(r.endOdoKm)}", color = Ink, fontSize = 22.sp, modifier = Modifier.width(360.dp))
        Text(r.distanceKm?.let { "%.1f km".format(it) } ?: "-", color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(150.dp))
        Text(r.destination, color = Muted, fontSize = 22.sp, modifier = Modifier.weight(1f))
        Pill(r.purpose.label, when (r.purpose) { Purpose.BUSINESS, Purpose.COMMUTE -> Accent; Purpose.PERSONAL -> Muted; else -> Warn })
    }
}

@Composable
private fun ExportTab(app: HighwayMateApp) {
    val month = SimpleDateFormat("yyyy-MM", Locale.KOREA).format(Date(AppClock.now()))
    val csv = remember(app.tripLog.trips.size) { app.tripLog.monthCsv(month) }
    val qr = remember(csv) { qr(csv, 560) }
    Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (qr != null) Image(qr, "운행기록부 QR", Modifier.size(420.dp).clip(RoundedCornerShape(12.dp)).background(Color.White).padding(12.dp))
            Text("휴대폰으로 찍으면 CSV(엑셀)로 받아요", color = Muted, fontSize = 22.sp)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("$month 업무용승용차 운행기록부 (국세청 고시 서식 항목)", color = Ink, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            csv.lines().filter { it.isNotBlank() }.take(14).forEach { Text(it, color = Muted, fontSize = 20.sp) }
            Text("기록은 차 안에만 저장되고 서버를 거치지 않아요.", color = Ok, fontSize = 22.sp)
        }
    }
}

@Composable
private fun EvTab(app: HighwayMateApp, s: Snapshot) {
    var result by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text("배터리 절전 코치 — 전기차 확장 기능 ⑤ (2단계: 차량 제어 SDK 승인 후 제공)", color = Ink, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Tile("현재 배터리", s.socPct?.let { "$it%" } ?: "-", Ink, Modifier.weight(1f))
            Tile("도착 예상 배터리", s.ev.estSocAtDest?.let { "$it%" } ?: "조회 전", when { (s.ev.estSocAtDest ?: 100) < 10 -> Danger; (s.ev.estSocAtDest ?: 100) < 20 -> Warn; else -> Ink }, Modifier.weight(1f))
            Tile("경로 거리", s.ev.tripKm?.let { "%.0f km".format(it) } ?: "-", Ink, Modifier.weight(1f))
            Tile("주행 모드", s.drivingModeType ?: "-", Ink, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedButton(onClick = { app.bridge.requestTrip() }, modifier = Modifier.height(80.dp)) { Text("도착 예상 배터리 확인 (내비)", fontSize = 24.sp) }
            Button(onClick = { app.powerSave.apply("mid") { result = "중간 절전: $it" } }, modifier = Modifier.height(80.dp)) { Text("중간 절전", fontSize = 26.sp) }
            Button(onClick = { app.powerSave.apply("max") { result = "최대 절전: $it" } }, colors = ButtonDefaults.buttonColors(containerColor = Warn), modifier = Modifier.height(80.dp)) { Text("최대 절전", fontSize = 26.sp, color = Color.Black) }
            OutlinedButton(onClick = { app.powerSave.restore { result = it } }, modifier = Modifier.height(80.dp)) { Text("되돌리기", fontSize = 24.sp) }
        }
        Text("중간: 공조 온도 2℃ 낮춤 · 풍량 최저 · 앰비언트·실내등 끔   |   최대: + 에어컨 끔 · 운전석 열선으로 대신   |   김서림 제거·전조등은 건드리지 않아요",
            color = Muted, fontSize = 21.sp)
        if (s.drivingModeType != null && s.drivingModeType != "ECO") Text("ECO 모드로 바꾸면 도착 배터리가 늘어요 (주행 모드는 앱이 바꿀 수 없어 안내만 해요)", color = Warn, fontSize = 22.sp)
        val shown = result.ifBlank { s.ev.lastResult.takeIf { it.isNotBlank() }?.let { "${if (s.ev.appliedLevel == "max") "최대" else "중간"} 절전: $it" } ?: "" }
        if (shown.isNotBlank()) Text(shown, color = Ok, fontSize = 22.sp)
    }
}

// ---------------------------------------------------------------- alert settings (parked only)
@Composable
private fun SettingsTab(app: HighwayMateApp) {
    val p by ai.highwaymate.core.Settings.prefs.collectAsStateWithLifecycle()
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text("안내 방식", color = Ink, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ai.highwaymate.core.AlertMode.entries.forEach { m ->
                val sel = p.mode == m
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).background(if (sel) Accent.copy(alpha = 0.18f) else Surface2)
                        .border(2.dp, if (sel) Accent else Color.Transparent, RoundedCornerShape(18.dp))
                        .clickable { ai.highwaymate.core.Settings.set { it.copy(mode = m) } }.padding(20.dp)
                ) {
                    Text(m.label, color = if (sel) Accent else Ink, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Text(m.detail, color = Muted, fontSize = 20.sp)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("미리 듣기", color = Muted, fontSize = 22.sp)
            OutlinedButton(onClick = { app.voice.say("2km 앞 정체 구간이에요.", Level.CAUTION) }) { Text("🟡 주의 구간", fontSize = 22.sp, color = Warn) }
            OutlinedButton(onClick = { app.voice.say("크루즈가 곧 해제돼요. 직접 운전하세요.", Level.URGENT) }) { Text("🔴 위험", fontSize = 22.sp, color = Danger) }
        }
        Text("무엇을 알려 줄까요", color = Ink, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Toggle("내비 위에 알림 카드 띄우기", "지도를 보면서 카드로 확인하고, 경유지 버튼도 카드에서 눌러요", p.headsUp) { v -> ai.highwaymate.core.Settings.set { it.copy(headsUp = v) } }
        Toggle("경로상 정체 구간", "크루즈 작동 중, 정체 2km 전에", p.congestion) { v -> ai.highwaymate.core.Settings.set { it.copy(congestion = v) } }
        Toggle("요금소·분기·휴게소 진입로", "크루즈 작동 중, 1.5km 전에", p.tollJunction) { v -> ai.highwaymate.core.Settings.set { it.copy(tollJunction = v) } }
        Toggle("사고 다발·공사·안개 구간", "내비 주의 지점, 1.5km 전에", p.cautionZones) { v -> ai.highwaymate.core.Settings.set { it.copy(cautionZones = v) } }
        Toggle("휴식 코치", "연속 운전 1시간 50분·2시간, 주의 경고", p.rest) { v -> ai.highwaymate.core.Settings.set { it.copy(rest = v) } }
        Text("알림은 같은 종류 10분에 한 번, 전체 1시간에 4번까지만 해요. 위험(빨강)은 예외예요.", color = Muted, fontSize = 20.sp)
    }
}

@Composable
private fun Toggle(title: String, detail: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Surface2).clickable { onChange(!on) }
            .padding(horizontal = 22.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Ink, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
            Text(detail, color = Muted, fontSize = 19.sp)
        }
        androidx.compose.material3.Switch(checked = on, onCheckedChange = onChange)
    }
}

// ---------------------------------------------------------------- simulator panel (demo)
@Composable
private fun SimPanel(app: HighwayMateApp, s: Snapshot, now: Long) {
    Column(
        Modifier.width(500.dp).fillMaxHeight().clip(RoundedCornerShape(24.dp)).background(Surface1)
            .border(2.dp, SimTint.copy(alpha = 0.5f), RoundedCornerShape(24.dp)).padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("시연용 시뮬레이터", color = SimTint, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("SDK = 실제 Pleos SDK로 받은 값 · SIM = 시뮬레이터로 넣은 값", color = Muted, fontSize = 18.sp)
        SimRow("기어", s.gear.name, s.sim.gear == null)
        SimRow("크루즈 상태", s.cruise.name, s.sim.cruise == null)
        SimRow("계기판 누적거리", odo(s.odometerKm), true)
        SimRow("속도", "${"%.0f".format(s.speedKmh)} km/h", s.sim.speedKmh == null)
        SimRow("연료", s.fuelPct?.let { "$it%" } ?: "-", s.sim.fuelPct == null)
        SimRow("배터리(SOC)", s.socPct?.let { "$it%" } ?: "-", true)
        SimRow("운전자 주의 경고", if (s.daw) "켜짐" else "꺼짐", !s.sim.daw && s.telltalesAvailable)
        SimRow("내비 경로", if (s.route.destName.isBlank()) "-" else "${s.route.destName}", true)
        SimRow("경로상 정체 구간", "${s.route.congestion.size}개 · ${"%.1f".format(s.route.totalCongestionM / 1000)}km", true)
        SimRow("경로 진행", "${"%.1f".format(s.progressM / 1000)} km", false)
        SimRow("시간 배속", "×${"%.0f".format(AppClock.factor)}", false)
        Spacer(Modifier.height(8.dp))
        val b = Modifier.height(60.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SimBtn("경로: 대전", b) { Demo.run(app, "route", null) }
            SimBtn("내비 새로고침", b) { Demo.run(app, "navi", null) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SimBtn("속도 0", b) { Demo.run(app, "speed", "0") }
            SimBtn("속도 100", b) { Demo.run(app, "speed", "100") }
            SimBtn("SDK 속도", b) { Demo.run(app, "speed", null) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SimBtn("연료 28%", b) { Demo.run(app, "fuel", "28") }
            SimBtn("주유 75%", b) { Demo.run(app, "fuel", "75") }
            SimBtn(if (s.sim.daw) "DAW 끄기" else "DAW 켜기", b) { Demo.run(app, "daw", if (s.sim.daw) "off" else "on") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SimBtn("×1", b) { Demo.run(app, "time", "1") }
            SimBtn("×60", b) { Demo.run(app, "time", "60") }
            SimBtn("운전 1시간 45분", b) { Demo.run(app, "drive", "105") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SimBtn("기어 D", b) { Demo.run(app, "gear", "D") }
            SimBtn("기어 P", b) { Demo.run(app, "gear", "P") }
            SimBtn("SDK 기어", b) { Demo.run(app, "gear", null) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SimBtn("시연 초기화", b) { Demo.run(app, "reset", null) }
            SimBtn("패널 숨기기", b) { Demo.run(app, "panel", "off") }
        }
        Spacer(Modifier.height(8.dp))
        Text("최근 기록", color = Muted, fontSize = 18.sp)
        s.log.take(8).forEach { Text(it, color = Muted, fontSize = 16.sp, maxLines = 2) }
    }
}

@Composable
private fun SimRow(label: String, value: String, sdk: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Muted, fontSize = 20.sp, modifier = Modifier.width(190.dp))
        Text(value, color = Ink, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1)
        Box(Modifier.clip(RoundedCornerShape(8.dp)).background(if (sdk) Ok.copy(alpha = 0.2f) else SimTint.copy(alpha = 0.25f)).padding(horizontal = 10.dp, vertical = 2.dp)) {
            Text(if (sdk) "SDK" else "SIM", color = if (sdk) Ok else SimTint, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SimBtn(label: String, modifier: Modifier, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = modifier) { Text(label, fontSize = 18.sp, color = Ink) }
}

// ---------------------------------------------------------------- small pieces
@Composable
private fun Tile(label: String, value: String, color: Color, modifier: Modifier = Modifier, bg: Color = Surface1) {
    Column(modifier.clip(RoundedCornerShape(18.dp)).background(bg).padding(horizontal = 20.dp, vertical = 14.dp)) {
        Text(label, color = Muted, fontSize = 20.sp, maxLines = 1)
        Text(value, color = color, fontSize = 30.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
    }
}

@Composable
private fun Pill(text: String, color: Color) {
    Box(Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.18f)).padding(horizontal = 22.dp, vertical = 10.dp)) {
        Text(text, color = color, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun hm(ms: Long): String { val m = ms / MIN; return "${m / 60}시간 ${m % 60}분" }
private fun odo(km: Float?) = km?.let { "%,.0f km".format(it) } ?: "-"

private fun qr(text: String, size: Int): ImageBitmap? = runCatching {
    val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.CHARACTER_SET to "UTF-8", EncodeHintType.MARGIN to 1))
    val bmp = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.RGB_565)
    for (x in 0 until size) for (y in 0 until size) bmp.setPixel(x, y, if (m[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
    bmp.asImageBitmap()
}.getOrNull()
