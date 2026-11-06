package ai.highwaymate.core

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * App time. In demo mode time can run faster (e.g. ×60) so a 2-hour drive fits in a short video.
 * Every rule in the app reads time from here, never from the system clock directly.
 */
object AppClock {
    @Volatile var factor = 1.0
        private set
    private var baseReal = SystemClock.elapsedRealtime()
    private var baseApp = System.currentTimeMillis()

    @Synchronized fun now(): Long = baseApp + ((SystemClock.elapsedRealtime() - baseReal) * factor).toLong()

    @Synchronized fun setFactor(f: Double) {
        val n = now()
        baseApp = n
        baseReal = SystemClock.elapsedRealtime()
        factor = f
    }
}

const val MIN = 60_000L

enum class Gear { P, R, N, D, UNKNOWN }

enum class Cruise { OFF, ENABLED, ACTIVATED, SUSPENDED, USER_OVERRIDE, FORCED_DEACTIVATION_WARNING, UNKNOWN }

enum class Level { INFO, CAUTION, URGENT }

/** One spoken + displayed message. */
data class Guidance(val kind: String, val text: String, val level: Level, val atApp: Long)

/** "Next rest area, put it in as a waypoint?" — the one button shown while driving. */
data class RestPrompt(
    val area: RestArea,
    val distanceKm: Double,
    val urgent: Boolean,
    val reason: String,
    val atApp: Long,
)

data class Congestion(val offsetM: Double, val lengthM: Double, val road: String, val type: String)

/** A route event whose distance was reported at [progressAtReportM] of virtual progress. */
data class AheadEvent(val kind: String, val label: String, val distanceM: Double, val progressAtReportM: Double)

data class RouteView(
    val destName: String = "",
    val destLat: Double? = null,
    val destLon: Double? = null,
    val totalM: Double = 0.0,
    val remainAtReportM: Double = 0.0,
    val progressAtRemainReportM: Double = 0.0,
    val congestion: List<Congestion> = emptyList(),
    val totalCongestionM: Double = 0.0,
    val tbt: List<AheadEvent> = emptyList(),
    val cautions: List<AheadEvent> = emptyList(),
    val waypoints: List<String> = emptyList(),
    val active: Boolean = false,
)

/** Values injected by the demo simulator panel. null = use the real SDK value. */
data class SimState(
    val speedKmh: Float? = null,
    val fuelPct: Int? = null,
    val daw: Boolean = false,
    val gear: Gear? = null,
    val cruise: Cruise? = null,
    val progressEnabled: Boolean = true,
    val caption: String = "",
    val panelVisible: Boolean = true,
    val tab: Int? = null,
)

data class TodayStats(
    val assistMs: Long = 0,
    val forecasts: Int = 0,
    val forcedDeactivations: Int = 0,
    val overrides: Int = 0,
    val rests: Int = 0,
    val longestContinuousMs: Long = 0,
)

data class EvState(
    val estSocAtDest: Int? = null,
    val tripKm: Double? = null,
    val energyKwh: Double? = null,
    val appliedLevel: String? = null,
    val lastResult: String = "",
)

data class Snapshot(
    // real SDK values
    val gearReal: Gear = Gear.UNKNOWN,
    val cruiseReal: Cruise = Cruise.UNKNOWN,
    val speedReal: Float? = null,
    val odometerKm: Float? = null,
    val fuelReal: Int? = null,
    val socPct: Int? = null,
    val dawReal: Boolean = false,
    val lfaReal: Boolean = false,
    val telltalesAvailable: Boolean = false,
    val drivingModeType: String? = null,
    val uxRestricted: Boolean = false,
    val vehicleReady: Boolean = false,
    val naviReady: Boolean = false,
    val speedPermission: Boolean = false,
    val bookmarks: Map<String, String> = emptyMap(),

    // derived
    val driveStartApp: Long? = null,
    val parkedSinceApp: Long? = null,
    val assistSinceApp: Long? = null,
    val progressM: Double = 0.0,
    val route: RouteView = RouteView(),
    val guidance: Guidance? = null,
    val prompt: RestPrompt? = null,
    val restingAt: String? = null,
    val trip: TripDraft? = null,
    val pendingClassification: TripRecord? = null,
    val today: TodayStats = TodayStats(),
    val ev: EvState = EvState(),
    val sim: SimState = SimState(),
    val log: List<String> = emptyList(),
) {
    val gear get() = sim.gear ?: gearReal
    val cruise get() = sim.cruise ?: cruiseReal
    val speedKmh get() = sim.speedKmh ?: speedReal ?: 0f
    val fuelPct get() = sim.fuelPct ?: fuelReal
    val daw get() = sim.daw || dawReal
    val assistActive get() = cruise == Cruise.ACTIVATED && gear == Gear.D
    val parked get() = gear == Gear.P || gear == Gear.UNKNOWN
    fun continuousMs(now: Long) = driveStartApp?.let { now - it } ?: 0L
    fun remainM(): Double = (route.remainAtReportM - (progressM - route.progressAtRemainReportM)).coerceAtLeast(0.0)
}

object Store {
    private val _state = MutableStateFlow(Snapshot())
    val state: StateFlow<Snapshot> = _state
    val now get() = _state.value

    fun update(f: (Snapshot) -> Snapshot) = _state.update(f)

    fun log(msg: String) {
        android.util.Log.i("HWM", msg)
        val t = java.text.SimpleDateFormat("HH:mm", java.util.Locale.KOREA).format(java.util.Date(AppClock.now()))
        _state.update { it.copy(log = (listOf("$t $msg") + it.log).take(40)) }
    }
}
