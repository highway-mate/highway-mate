package ai.highwaymate.core

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class Purpose(val label: String) { UNSET("미분류"), BUSINESS("업무"), COMMUTE("출퇴근"), PERSONAL("개인") }

data class TripDraft(val startApp: Long, val startOdoKm: Float?, val notes: List<String> = emptyList())

data class TripRecord(
    val id: Long,
    val startApp: Long,
    val endApp: Long,
    val startOdoKm: Float?,
    val endOdoKm: Float?,
    val destination: String,
    val purpose: Purpose,
    val notes: List<String>,
) {
    val distanceKm: Float? get() = if (startOdoKm != null && endOdoKm != null) endOdoKm - startOdoKm else null

    fun toJson() = JSONObject().apply {
        put("id", id); put("startApp", startApp); put("endApp", endApp)
        put("startOdoKm", startOdoKm?.toDouble() ?: JSONObject.NULL)
        put("endOdoKm", endOdoKm?.toDouble() ?: JSONObject.NULL)
        put("destination", destination); put("purpose", purpose.name)
        put("notes", JSONArray(notes))
    }

    companion object {
        fun fromJson(o: JSONObject) = TripRecord(
            id = o.getLong("id"),
            startApp = o.getLong("startApp"),
            endApp = o.getLong("endApp"),
            startOdoKm = if (o.isNull("startOdoKm")) null else o.getDouble("startOdoKm").toFloat(),
            endOdoKm = if (o.isNull("endOdoKm")) null else o.getDouble("endOdoKm").toFloat(),
            destination = o.optString("destination"),
            purpose = runCatching { Purpose.valueOf(o.getString("purpose")) }.getOrDefault(Purpose.UNSET),
            notes = o.optJSONArray("notes")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList(),
        )
    }
}

/**
 * Trip log kept only on the device (no server). One JSON file is enough for a prototype;
 * the product would use Room.
 */
class TripLog(context: Context) {
    private val file = File(context.filesDir, "trips.json")
    private val _trips = mutableListOf<TripRecord>()
    val trips: List<TripRecord> get() = synchronized(this) { _trips.toList() }

    init {
        runCatching {
            if (file.exists()) {
                val a = JSONArray(file.readText())
                for (i in 0 until a.length()) _trips += TripRecord.fromJson(a.getJSONObject(i))
            }
        }
    }

    @Synchronized fun add(r: TripRecord) { _trips += r; save() }

    @Synchronized fun setPurpose(id: Long, p: Purpose) {
        val i = _trips.indexOfFirst { it.id == id }
        if (i >= 0) { _trips[i] = _trips[i].copy(purpose = p); save() }
    }

    @Synchronized fun clear() { _trips.clear(); save() }

    private fun save() {
        val a = JSONArray(); _trips.forEach { a.put(it.toJson()) }
        file.writeText(a.toString())
    }

    /**
     * NTS notice 2025-8 「업무용승용차 운행기록부」 columns: date, user, odometer before/after, distance,
     * commute km, general business km, note. Plus the business-use ratio for the period.
     */
    fun monthCsv(month: String): String {
        val day = SimpleDateFormat("yyyy-MM-dd(E)", Locale.KOREA)
        val ym = SimpleDateFormat("yyyy-MM", Locale.KOREA)
        val rows = trips.filter { ym.format(Date(it.startApp)) == month }
        val sb = StringBuilder("사용일자,사용자,주행전계기판(km),주행후계기판(km),주행거리(km),출퇴근용(km),일반업무용(km),비고\n")
        var total = 0f; var business = 0f
        rows.forEach { r ->
            val d = r.distanceKm ?: 0f
            total += d
            val commute = if (r.purpose == Purpose.COMMUTE) d else 0f
            val general = if (r.purpose == Purpose.BUSINESS) d else 0f
            business += commute + general
            val note = (listOf(r.destination) + r.notes + if (r.purpose == Purpose.PERSONAL) listOf("개인") else emptyList())
                .filter { it.isNotBlank() }.joinToString(" / ")
            sb.append("${day.format(Date(r.startApp))},,${f1(r.startOdoKm)},${f1(r.endOdoKm)},${f1(d)},${f1(commute)},${f1(general)},\"$note\"\n")
        }
        val ratio = if (total > 0) business / total * 100 else 0f
        sb.append("합계,,,,${f1(total)},,${f1(business)},업무사용비율 ${"%.1f".format(ratio)}%\n")
        return sb.toString()
    }

    private fun f1(v: Float?) = v?.let { "%.1f".format(it) } ?: ""
}
