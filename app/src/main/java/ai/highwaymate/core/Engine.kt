package ai.highwaymate.core

/**
 * Situation engine. Called once per second by MonitorService.
 *
 * ① Assist over-trust: while smart cruise is ACTIVATED, forecast congestion, toll gates, junctions and caution
 *    points on the route BEFORE the car reaches them.
 * ② Rest coach: continuous driving time (measured by the app from gear) → next rest area as a one-tap waypoint.
 *    The driver attention telltale (DAW) is a secondary trigger when the permission is granted.
 * ③ Trip log: odometer at departure and at park, purpose by one tap.
 * Notification budget: the same kind at most once per 10 min, at most 4 per hour (urgent ones skip the hourly cap).
 */
class Engine(
    private val log: TripLog,
    private val speak: (String, Level) -> Unit,
) {
    companion object {
        const val PRE_REST_MIN = 110L     // 1h50m: announce the next rest area
        const val REST_MIN = 120L         // 2h: recommend resting now
        const val REST_DONE_MIN = 10L     // parked this long = rested
        const val TRIP_END_PARK_MIN = 30L // parked this long away from the destination = trip ended
        const val FORECAST_M = 2_000.0
        const val LONG_CRUISE_MIN = 30L
        const val DEMO_ROUTE_START_KM = 0.0
    }

    private var lastTickApp = AppClock.now()
    private var prevGear: Gear = Gear.UNKNOWN
    private val announced = mutableSetOf<String>()
    private val lastByKind = mutableMapOf<String, Long>()
    private val history = ArrayDeque<Long>()
    private var lastLongCruiseApp = 0L
    private var lastDaw = false
    private var fuelAtPark: Int? = null
    private val visited = mutableSetOf<String>()

    fun onCruiseEvent(c: Cruise) {
        val now = AppClock.now()
        when (c) {
            Cruise.FORCED_DEACTIVATION_WARNING -> {
                Store.update { it.copy(today = it.today.copy(forcedDeactivations = it.today.forcedDeactivations + 1)) }
                emit("forced", "크루즈가 곧 해제돼요. 직접 운전하세요.", Level.URGENT, now, urgent = true)
            }
            Cruise.USER_OVERRIDE -> Store.update { it.copy(today = it.today.copy(overrides = it.today.overrides + 1)) }
            else -> {}
        }
    }

    fun tick() {
        val now = AppClock.now()
        val dt = (now - lastTickApp).coerceIn(0, 10 * MIN)
        lastTickApp = now
        val s = Store.now

        handleGear(s, now)

        // virtual progress along the route (the emulator car does not move; in a real car this follows the route)
        if (s.gear == Gear.D && s.sim.progressEnabled && s.speedKmh > 1f) {
            Store.update { it.copy(progressM = it.progressM + it.speedKmh / 3.6 * dt / 1000.0) }
        }

        val s2 = Store.now
        // assist time
        if (s2.assistActive) {
            Store.update {
                it.copy(assistSinceApp = it.assistSinceApp ?: now, today = it.today.copy(assistMs = it.today.assistMs + dt))
            }
            forecast(Store.now, now)
            longCruise(Store.now, now)
        } else if (s2.assistSinceApp != null) {
            Store.update { it.copy(assistSinceApp = null) }
        }

        restCoach(Store.now, now)
        arrival(Store.now, now)

        // longest continuous driving today
        val cont = Store.now.continuousMs(now)
        if (cont > Store.now.today.longestContinuousMs) Store.update { it.copy(today = it.today.copy(longestContinuousMs = cont)) }
    }

    // ------------------------------------------------------------------ gear → trip and rest
    private fun handleGear(s: Snapshot, now: Long) {
        val g = s.gear
        if (g == prevGear) {
            if (g == Gear.P) parkedChecks(s, now)
            return
        }
        val was = prevGear
        prevGear = g
        if (g == Gear.D || g == Gear.R || g == Gear.N) {
            if (was == Gear.P || was == Gear.UNKNOWN) {
                val newTrip = s.trip == null
                Store.update {
                    it.copy(
                        parkedSinceApp = null,
                        restingAt = null,
                        driveStartApp = it.driveStartApp ?: now,
                        trip = it.trip ?: TripDraft(now, it.odometerKm),
                    )
                }
                if (newTrip) Store.log("운행 시작: 계기판 ${s.odometerKm?.let { k -> "%.0f km".format(k) } ?: "-"}")
            }
        } else if (g == Gear.P) {
            fuelAtPark = s.fuelPct
            Store.update { it.copy(parkedSinceApp = now, prompt = null) }
            val arrived = s.route.destName.isNotBlank() && s.route.totalM > 0 && s.remainM() < 1_000
            val restStop = (s.route.waypoints.firstOrNull { it !in visited } ?: s.prompt?.area?.name)
                ?.takeIf { it !in visited && !arrived }
            if (restStop != null) {
                visited += restStop
                Store.update { it.copy(restingAt = restStop) }
                direct("rest-arrive", "${restStop}에 도착했어요. 10분 이상 쉬면 연속 운전 시간이 초기화돼요.", Level.INFO, now)
            } else if (s.trip != null && (arrived || !s.route.active || s.route.totalM == 0.0)) {
                endTrip(now, s.route.destName)
            }
        }
    }

    private fun parkedChecks(s: Snapshot, now: Long) {
        val since = s.parkedSinceApp ?: return
        // refuel: fuel level jumps while parked
        val f0 = fuelAtPark; val f1 = s.fuelPct
        if (f0 != null && f1 != null && f1 - f0 >= 10) {
            fuelAtPark = f1
            Store.update { st -> st.copy(trip = st.trip?.copy(notes = st.trip.notes + "주유 ${f0}→${f1}%")) }
            Store.log("주유 감지: $f0% → $f1%")
        }
        if (s.driveStartApp != null && now - since >= REST_DONE_MIN * MIN) {
            Store.update { it.copy(driveStartApp = null, today = it.today.copy(rests = it.today.rests + 1)) }
            announced.removeAll { it.startsWith("rest") }
            Store.log("휴식 완료 → 연속 운전 시간 초기화")
        }
        if (s.trip != null && s.restingAt == null && now - since >= TRIP_END_PARK_MIN * MIN) endTrip(now, s.route.destName)
    }

    private fun endTrip(now: Long, dest: String) {
        val s = Store.now
        val d = s.trip ?: return
        val purpose = when {
            dest.isNotBlank() && s.bookmarks["OFFICE"]?.takeIf { it.isNotBlank() }?.let { dest.contains(it) } == true -> Purpose.COMMUTE
            dest.isNotBlank() && s.bookmarks["HOME"]?.takeIf { it.isNotBlank() }?.let { dest.contains(it) } == true -> Purpose.COMMUTE
            else -> Purpose.UNSET
        }
        val r = TripRecord(now, d.startApp, now, d.startOdoKm, s.odometerKm, dest.ifBlank { "목적지 미설정" }, purpose, d.notes)
        log.add(r)
        Store.update { it.copy(trip = null, pendingClassification = if (purpose == Purpose.UNSET) r else null) }
        Store.log("운행 종료: ${r.distanceKm?.let { "%.1f km".format(it) } ?: "-"} → 운행일지 저장")
        direct("trip-end", "운행이 기록됐어요. ${r.distanceKm?.let { "%.1f".format(it) + "km" } ?: ""} · 업무인지 개인인지 한 번만 눌러 주세요.", Level.INFO, now)
    }

    fun classify(p: Purpose) {
        val r = Store.now.pendingClassification ?: return
        log.setPurpose(r.id, p)
        val now = AppClock.now()
        Store.update { it.copy(pendingClassification = null, guidance = Guidance("classified", "「${p.label}」으로 기록했어요. 월말에 운행기록부로 한 번에 내보낼 수 있어요.", Level.INFO, now)) }
    }

    // ------------------------------------------------------------------ ① forecast while cruise is active
    private fun forecast(s: Snapshot, now: Long) {
        val p = s.progressM
        val prefs = Settings.now
        if (prefs.congestion) s.route.congestion.forEachIndexed { i, c ->
            val ahead = c.offsetM - p
            if (ahead in 0.0..FORECAST_M) {
                val km = "%.1f".format(ahead / 1000).removeSuffix(".0")
                once("cong-$i", "congestion", now,
                    "${km}km 앞 정체 구간이에요. 앞차를 확인해 주세요.", Level.CAUTION)
            }
        }
        ((if (prefs.tollJunction) s.route.tbt else emptyList()) + (if (prefs.cautionZones) s.route.cautions else emptyList())).forEach { e ->
            val ahead = e.distanceM - (p - e.progressAtReportM)
            if (ahead in 0.0..1_500.0) {
                val km = "%.1f".format(ahead / 1000)
                val msg = when (e.kind) {
                    "TURN_TOLL" -> "${km}km 앞 요금소예요. 속도가 크게 줄어요. 앞을 봐 주세요."
                    "TURN_DEVIDE_MERGE" -> "${km}km 앞 분기·합류 구간이에요. 끼어드는 차를 조심하세요."
                    else -> "${km}km 앞 ${e.label}${ieyo(e.label)}. 앞을 봐 주세요."
                }
                once("ev-${e.kind}-${e.distanceM.toInt()}-${e.progressAtReportM.toInt()}", "route-event", now, msg, Level.CAUTION)
            }
        }
    }

    private fun longCruise(s: Snapshot, now: Long) {
        val since = s.assistSinceApp ?: return
        if (now - since >= LONG_CRUISE_MIN * MIN && now - lastLongCruiseApp >= LONG_CRUISE_MIN * MIN) {
            if (emit("long-cruise", "크루즈로 30분째 달리고 있어요. 앞 상황 한 번 확인할까요?", Level.INFO, now)) lastLongCruiseApp = now
        }
    }

    // ------------------------------------------------------------------ ② rest coach
    private fun restCoach(s: Snapshot, now: Long) {
        if (s.gear != Gear.D || !Settings.now.rest) return
        // a rest stop is already on the route: do not ask again
        if (s.prompt?.atApp == -1L || s.route.waypoints.any { it !in visited }) { lastDaw = s.daw; return }
        val cont = s.continuousMs(now)
        val dawNow = s.daw && !lastDaw
        lastDaw = s.daw
        val pre = cont >= PRE_REST_MIN * MIN && "rest-pre" !in announced
        val must = (cont >= REST_MIN * MIN && "rest-now" !in announced) || dawNow
        if (!pre && !must) return

        val routeKm = DEMO_ROUTE_START_KM + s.progressM / 1000
        val ev = s.fuelPct == null && s.socPct != null
        val energyPct = if (ev) s.socPct else s.fuelPct
        val lowEnergy = energyPct != null && energyPct <= 30
        val area = RestAreas.next(routeKm, lowEnergy, ev) ?: return
        val dist = area.km - routeKm
        val distTxt = "%.0f".format(dist)
        val energyTxt = if (lowEnergy) (if (ev) " 배터리도 ${energyPct}%예요." else " 연료도 ${energyPct}%예요.") else ""
        val reason = when {
            dawNow -> "운전자 주의 경고"
            cont >= REST_MIN * MIN -> "연속 운전 2시간"
            else -> "연속 운전 ${cont / MIN / 60}시간 ${cont / MIN % 60}분"
        }
        val head = when {
            dawNow -> "주의 경고가 켜졌어요. 쉬어 갈 때예요."
            must -> "2시간째 운전 중이에요. 쉬어 갈 때예요."
            else -> "곧 2시간이에요."
        }
        val text = "$head ${area.name}까지 ${distTxt}km.$energyTxt 경유지로 넣을까요?"
        if (emit(if (must) "rest-now" else "rest-pre", text, if (must) Level.URGENT else Level.INFO, now, urgent = must)) {
            announced += if (must) "rest-now" else "rest-pre"
            if (must) announced += "rest-pre"
            Store.update { it.copy(prompt = RestPrompt(area, dist, must, reason, now)) }
        }
    }

    private fun arrival(s: Snapshot, now: Long) {
        if (s.route.totalM > 0 && s.route.destName.isNotBlank() && s.remainM() < 1_000 && "arrive" !in announced && s.gear == Gear.D) {
            announced += "arrive"
            emit("arrive", "곧 목적지예요. 주차하면 이번 운행이 자동으로 기록돼요.", Level.INFO, now)
        }
    }

    fun onWaypointAccepted() {
        Store.update { it.copy(prompt = it.prompt?.copy(atApp = -1)) }
    }

    fun resetDemo() {
        announced.clear(); lastByKind.clear(); history.clear(); visited.clear(); lastLongCruiseApp = 0; prevGear = Gear.UNKNOWN
    }

    // ------------------------------------------------------------------ notification budget
    private fun once(key: String, kind: String, now: Long, text: String, level: Level) {
        if (key in announced) return
        if (emit(kind, text, level, now)) {
            announced += key
            Store.update { it.copy(today = it.today.copy(forecasts = it.today.forecasts + 1)) }
        }
    }

    /** Parked-state messages: no budget (the car is stopped), but still shown and spoken once. */
    private fun direct(kind: String, text: String, level: Level, now: Long) {
        Store.update { it.copy(guidance = Guidance(kind, text, level, now)) }
        Store.log("안내: $text")
        speak(text, level)
    }

    /** 받침이 있으면 「이에요」, 없으면 「예요」. */
    private fun ieyo(word: String): String {
        val c = word.lastOrNull() ?: return "이에요"
        return if (c in '가'..'힣' && (c - '가') % 28 == 0) "예요" else "이에요"
    }

    private var lastSpokenReal = 0L

    private fun emit(kind: String, text: String, level: Level, now: Long, urgent: Boolean = false): Boolean {
        // never talk over the previous message: keep at least 6 real seconds between two messages
        val real = android.os.SystemClock.elapsedRealtime()
        if (!urgent && real - lastSpokenReal < 6_000) return false
        val last = lastByKind[kind]
        if (last != null && now - last < 10 * MIN && !urgent) return false
        while (history.isNotEmpty() && now - history.first() > 60 * MIN) history.removeFirst()
        if (!urgent && history.size >= 4) { Store.log("알림 상한 도달 → 생략: $text"); return false }
        lastByKind[kind] = now
        lastSpokenReal = real
        history.addLast(now)
        Store.update { it.copy(guidance = Guidance(kind, text, level, now)) }
        Store.log("안내: $text")
        speak(text, level)
        return true
    }
}
