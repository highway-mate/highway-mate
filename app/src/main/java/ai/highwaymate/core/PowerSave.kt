package ai.highwaymate.core

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.control.LightSwitch
import ai.pleos.playground.vehicle.constant.unit.TemperatureUnit

/**
 * Extension ⑤ (EV, stage 2): battery saving levels, chosen by the driver — never applied automatically.
 * Defroster, headlights and anything else related to visibility are never touched.
 * Valid areas come from the capability queries, so the same code runs on cars with different seat layouts.
 */
class PowerSave(private val vehicle: () -> Vehicle?) {
    private val driver = SeatArea(SeatValue.ROW_1_LEFT)
    private var saved: Saved? = null

    private data class Saved(val temp: Float?, val fan: Int?, val cabinLight: LightSwitch?)

    fun apply(level: String, onDone: (String) -> Unit) {
        val v = vehicle() ?: return onDone("차량 연결 없음")
        val hvac = v.getHVAC()
        val results = mutableListOf<String>()
        fun ok(s: String) = synchronized(results) { results += "✓ $s" }
        fun no(s: String, e: Exception) = synchronized(results) { results += "✗ $s (${e.message?.take(40)})" }

        // remember current values so the driver can restore them
        if (saved == null) {
            var t: Float? = null; var f: Int? = null; var l: LightSwitch? = null
            runCatching { hvac.getHvacTemperature(driver, TemperatureUnit.values().first(), { t = it }, {}) }
            runCatching { hvac.getHvacFanSpeed(driver, { f = it }, {}) }
            runCatching { v.getLight().getCabinLightSwitch({ l = it }, {}) }
            saved = Saved(t, f, l)
        }
        val cur = saved?.temp ?: 22f
        val target = if (level == "max") cur - 3f else cur - 2f   // winter: lower the heating set point

        runCatching { hvac.setHvacTemperature(driver, target, TemperatureUnit.values().first(), { _, x -> ok("공조 온도 ${x}°") }, { no("공조 온도", it) }) }
        runCatching {
            hvac.checkHvacFanSpeedCapabilities({ map ->
                map.forEach { (area, b) -> hvac.setHvacFanSpeed(area, b.minValue ?: 1, { _, x -> ok("풍량 $x") }, { no("풍량", it) }) }
            }, { no("풍량", it) })
        }
        runCatching {
            v.getLight().checkAmbientLightBrightnessCapabilities({ _, map ->
                map.forEach { (area, b) -> v.getLight().setAmbientLightBrightness(area, b.minValue ?: 0, { _, _ -> ok("앰비언트 조명 최소") }, { no("앰비언트", it) }) }
            }, { no("앰비언트", it) })
        }
        runCatching { v.getLight().setCabinLightSwitch(LightSwitch.OFF, { ok("실내등 끔") }, { no("실내등", it) }) }
        if (level == "max") {
            runCatching {
                hvac.checkHvacAcCapabilities({ areas ->
                    areas.forEach { a -> hvac.setHvacAc(a, false, { _, _ -> ok("에어컨 끔") }, { no("에어컨", it) }) }
                }, { no("에어컨", it) })
            }
            runCatching { v.getSeat().setHvacSeatTemperature(driver, 1, { _, x -> ok("운전석 열선 ${x}단") }, { no("운전석 열선", it) }) }
        }
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            onDone(synchronized(results) { results.joinToString("  ") })
        }, 1500)
    }

    fun restore(onDone: (String) -> Unit) {
        val v = vehicle() ?: return onDone("차량 연결 없음")
        val s = saved ?: return onDone("되돌릴 설정 없음")
        s.temp?.let { t -> runCatching { v.getHVAC().setHvacTemperature(driver, t, TemperatureUnit.values().first(), { _, _ -> }, {}) } }
        s.fan?.let { f -> runCatching { v.getHVAC().setHvacFanSpeed(driver, f, { _, _ -> }, {}) } }
        s.cabinLight?.let { l -> runCatching { v.getLight().setCabinLightSwitch(l, {}, {}) } }
        runCatching { v.getSeat().setHvacSeatTemperature(driver, 0, { _, _ -> }, {}) }
        saved = null
        onDone("원래 설정으로 되돌렸어요")
    }
}
