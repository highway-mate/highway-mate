package ai.highwaymate.core

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** How the driver wants to be told. Changed only while parked. */
enum class AlertMode(val label: String, val detail: String) {
    VOICE("음성 + 화면", "효과음 뒤에 짧게 말해 줘요"),
    CHIME("효과음 + 화면", "주의는 띵 한 번, 위험은 띵띵띵"),
    SCREEN("화면만", "소리 없이 알림 카드만 띄워요"),
}

data class Prefs(
    val mode: AlertMode = AlertMode.VOICE,
    val headsUp: Boolean = true,
    val congestion: Boolean = true,
    val tollJunction: Boolean = true,
    val cautionZones: Boolean = true,
    val rest: Boolean = true,
)

object Settings {
    private val _prefs = MutableStateFlow(Prefs())
    val prefs: StateFlow<Prefs> = _prefs
    val now get() = _prefs.value
    private var ctx: Context? = null

    fun load(context: Context) {
        ctx = context.applicationContext
        val p = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        _prefs.value = Prefs(
            mode = runCatching { AlertMode.valueOf(p.getString("mode", "VOICE")!!) }.getOrDefault(AlertMode.VOICE),
            headsUp = p.getBoolean("headsUp", true),
            congestion = p.getBoolean("congestion", true),
            tollJunction = p.getBoolean("tollJunction", true),
            cautionZones = p.getBoolean("cautionZones", true),
            rest = p.getBoolean("rest", true),
        )
    }

    fun set(f: (Prefs) -> Prefs) {
        val n = f(_prefs.value)
        _prefs.value = n
        ctx?.getSharedPreferences("settings", Context.MODE_PRIVATE)?.edit()?.apply {
            putString("mode", n.mode.name); putBoolean("headsUp", n.headsUp)
            putBoolean("congestion", n.congestion); putBoolean("tollJunction", n.tollJunction)
            putBoolean("cautionZones", n.cautionZones); putBoolean("rest", n.rest)
            apply()
        }
    }
}

/** Short label + colour dot shown with every message. */
fun Level.badge() = when (this) {
    Level.URGENT -> "🔴 위험"
    Level.CAUTION -> "🟡 주의 구간"
    Level.INFO -> "🔵 안내"
}
