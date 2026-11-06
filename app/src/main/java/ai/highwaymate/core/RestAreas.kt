package ai.highwaymate.core

/**
 * Rest areas along the route.
 *
 * NaviHelper has no place (POI) search, so the app carries rest-area coordinates itself and hands them to
 * `addWaypoint`. The product would load the Korea Expressway Corporation open data (rest areas, drowsy-driving
 * shelters, fuel and EV charger flags). This prototype ships a small sample for the Gyeongbu Expressway
 * southbound for the demo route (Pangyo → Daejeon, 141 km). Coordinates are approximate;
 * km is the approximate distance from the demo start along that route.
 */
data class RestArea(
    val name: String,
    val lat: Double,
    val lon: Double,
    val km: Double,
    val fuel: Boolean = true,
    val evCharger: Boolean = true,
    val shelterOnly: Boolean = false,
)

object RestAreas {
    val gyeongbuSouth = listOf(
        RestArea("기흥휴게소", 37.2412, 127.1093, 17.0),
        RestArea("안성휴게소", 37.0136, 127.2066, 48.0),
        RestArea("천안삼거리휴게소", 36.8034, 127.1922, 74.0),
        RestArea("옥산휴게소", 36.6862, 127.3598, 96.0),
        RestArea("죽암휴게소", 36.5297, 127.4398, 114.0),
        RestArea("신탄진휴게소", 36.4358, 127.4247, 124.0),
    )

    /**
     * The next rest area ahead of [routeKm]. When fuel (or battery) is low, prefer one that can refuel
     * within [lookAheadKm].
     */
    fun next(routeKm: Double, needEnergy: Boolean, ev: Boolean, lookAheadKm: Double = 40.0): RestArea? {
        val ahead = gyeongbuSouth.filter { it.km > routeKm + 1.0 }
        if (needEnergy) {
            ahead.firstOrNull { it.km - routeKm <= lookAheadKm && (if (ev) it.evCharger else it.fuel) }?.let { return it }
        }
        return ahead.firstOrNull()
    }
}
