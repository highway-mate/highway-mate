package ai.highwaymate.core

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.NavigationStatus
import ai.pleos.playground.navi.constants.RouteOption
import ai.pleos.playground.navi.constants.TurnType
import ai.pleos.playground.navi.constants.WaypointIndex
import ai.pleos.playground.navi.data.BookmarkInfo
import ai.pleos.playground.navi.data.CautionPoint
import ai.pleos.playground.navi.data.DrivingInfo
import ai.pleos.playground.navi.data.RequestWaypointInfo
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.RouteStateInfo
import ai.pleos.playground.navi.data.TBTInfo
import ai.pleos.playground.navi.data.TripInfo
import ai.pleos.playground.navi.data.TripRequest
import ai.pleos.playground.navi.data.WaypointChangedInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.telltale.Telltales
import ai.pleos.playground.vehicle.constant.control.CruiseControl
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.constant.telltale.TelltaleState
import ai.pleos.playground.vehicle.constant.telltale.TelltaleType
import ai.pleos.playground.vehicle.listener.CruiseControlStateListener
import ai.pleos.playground.vehicle.listener.DistanceDrivenListener
import ai.pleos.playground.vehicle.listener.EvBatteryStateOfChargeListener
import ai.pleos.playground.vehicle.listener.FuelPercentListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.TelltaleInfoListener
import android.car.Car
import android.car.VehiclePropertyIds
import android.car.drivingstate.CarUxRestrictionsManager
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.content.pm.PackageManager

/**
 * Everything the app reads from the car and the navigation:
 * Pleos Vehicle SDK (cruise, gear, odometer, fuel/SOC, telltales), AAOS Car API (speed, UX restrictions)
 * and Pleos NaviHelper (route, congestion, TBT, caution points, waypoints, trip estimate).
 */
class VehicleBridge(private val context: Context, private val onCruiseEvent: (Cruise) -> Unit) {
    var vehicle: Vehicle? = null
        private set
    private var navi: NaviHelper? = null
    private var car: Car? = null

    fun start() {
        startVehicle()
        startCar()
        startNavi()
    }

    fun release() {
        runCatching { vehicle?.release() }
        runCatching { navi?.release() }
        runCatching { car?.disconnect() }
    }

    // ------------------------------------------------------------------ Vehicle SDK
    private fun startVehicle() {
        val v = runCatching { Vehicle(context).also { it.initialize() } }.getOrElse {
            Store.log("Vehicle SDK 초기화 실패: $it"); return
        }
        vehicle = v
        Store.update { it.copy(vehicleReady = true) }

        runCatching {
            v.getDrivingMode().registerCruiseControlState(object : CruiseControlStateListener {
                override fun onCruiseControlStateUpdated(state: CruiseControl?) {
                    val c = state.toCruise()
                    Store.update { it.copy(cruiseReal = c) }
                    onCruiseEvent(c)
                }
                override fun onFailed(e: Exception) { Store.log("크루즈 상태 수신 실패: ${e.message}") }
            })
        }
        runCatching {
            v.getGear().registerGearSelection(object : GearSelectionListener {
                override fun onSelected(g: VehicleGear?) { Store.update { it.copy(gearReal = g.toGear()) } }
                override fun onFailed(e: Exception) { Store.log("기어 수신 실패: ${e.message}") }
            })
            v.getGear().getCurrentGearSelectionState({ g -> Store.update { it.copy(gearReal = g.toGear()) } }, {})
        }
        runCatching {
            v.getOdometer().registerDistanceDriven(object : DistanceDrivenListener {
                override fun onDistanceDrivenUpdated(km: Float?) { if (km != null) Store.update { it.copy(odometerKm = km) } }
                override fun onFailed(e: Exception) {}
            })
            v.getOdometer().getCurrentDistanceDriven({ km -> if (km != null) Store.update { it.copy(odometerKm = km) } }, {})
        }
        runCatching {
            v.getFuel().registerFuelPercent(object : FuelPercentListener {
                override fun onFuelPercentUpdated(p: Int?) { if (p != null) Store.update { it.copy(fuelReal = p) } }
                override fun onFailed(e: Exception) {}
            })
        }
        runCatching {
            v.getEvBattery().registerEvBatteryStateOfCharge(object : EvBatteryStateOfChargeListener {
                override fun onEvBatteryStateOfChargeUpdated(p: Int?) { if (p != null) Store.update { it.copy(socPct = p) } }
                override fun onFailed(e: Exception) {}
            })
        }
        runCatching {
            v.getDrivingMode().getCurrentDrivingModeState({ m -> Store.update { it.copy(drivingModeType = m?.name) } }, {})
        }
        // Secondary signal. Needs PRIVATE_CAR_SAFETY; the app keeps working when it is refused.
        runCatching {
            v.getSafety().registerTelltales(object : TelltaleInfoListener {
                override fun onTelltaleInfoUpdated(t: Telltales?) { if (t != null) onTelltales(t) }
                override fun onFailed(e: Exception) { Store.update { it.copy(telltalesAvailable = false) } }
            })
        }.onFailure { Store.log("경고등 권한 없음 → 연속 운전 시간만으로 판단") }
    }

    private fun onTelltales(t: Telltales) {
        fun on(type: TelltaleType) = t.telltales.any { it.type == type && it.state != TelltaleState.OFF }
        Store.update { it.copy(telltalesAvailable = true, dawReal = on(TelltaleType.DRIVER_ATTENTION), lfaReal = on(TelltaleType.LFA)) }
    }

    // ------------------------------------------------------------------ AAOS Car API
    private fun startCar() {
        runCatching {
            val c = Car.createCar(context) ?: return
            car = c
            val ux = c.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as CarUxRestrictionsManager
            Store.update { it.copy(uxRestricted = ux.currentCarUxRestrictions.isRequiresDistractionOptimization) }
            ux.registerListener { r -> Store.update { it.copy(uxRestricted = r.isRequiresDistractionOptimization) } }
            subscribeSpeed()
        }.onFailure { Store.log("Car API 연결 실패: $it") }
    }

    fun subscribeSpeed() {
        val c = car ?: return
        val granted = context.checkSelfPermission("android.car.permission.CAR_SPEED") == PackageManager.PERMISSION_GRANTED
        Store.update { it.copy(speedPermission = granted) }
        if (!granted) return
        runCatching {
            val cpm = c.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager
            cpm.registerCallback(object : CarPropertyManager.CarPropertyEventCallback {
                override fun onChangeEvent(v: CarPropertyValue<*>) {
                    val mps = (v.value as? Float) ?: return
                    Store.update { it.copy(speedReal = mps * 3.6f) }
                }
                override fun onErrorEvent(propId: Int, zone: Int) {}
            }, VehiclePropertyIds.PERF_VEHICLE_SPEED, CarPropertyManager.SENSOR_RATE_NORMAL)
        }
    }

    // ------------------------------------------------------------------ NaviHelper
    private fun startNavi() {
        val n = NaviHelper(context)
        navi = n
        n.addListener(object : NaviHelperEventListener {
            override fun onNavigationStatus(s: NavigationStatus) {}
            override fun onError(code: NaviErrorCode) { Store.log("내비 오류 ${code.name}") }
            override fun onRouteStateInfo(info: RouteStateInfo) = onRouteState(info)
            override fun onTBTInfo(list: List<TBTInfo>) = onTbt(list)
            override fun onCautionPointInfo(list: List<CautionPoint>) = onCautions(list)
            override fun onDrivingInfo(info: DrivingInfo) = onDriving(info)
            override fun onCurrentLocationInfo(info: ai.pleos.playground.navi.data.CurrentLocationInfo) = onLocation(info.latitude, info.longitude)
            override fun onBookmarkInfo(list: List<BookmarkInfo>) {
                Store.update { s -> s.copy(bookmarks = list.associate { it.category.name to it.name }) }
            }
            override fun onWaypointChanged(info: WaypointChangedInfo) {
                Store.log("경유지 변경: ${info}")
                n.getRouteStateInfo(); n.getTBTInfo()
            }
            override fun onRoutingCompleted() {
                Store.log("내비 경로 계산 완료")
                n.getRouteStateInfo(); n.getTBTInfo()
            }
            override fun onTripInfo(info: TripInfo) {
                Store.update {
                    it.copy(ev = it.ev.copy(estSocAtDest = info.estimatedSocAtDest, tripKm = info.distance / 1000.0,
                        energyKwh = info.energyConsumption))
                }
                Store.log("도착 예상 배터리 ${info.estimatedSocAtDest}% (${info.distance / 1000} km)")
            }
        })
        runCatching {
            n.initialize({
                Store.update { it.copy(naviReady = true) }
                n.getRouteStateInfo(); n.getBookmarkInfo(); n.getTBTInfo()
            }, { t -> Store.log("NaviHelper 초기화 실패: $t") })
        }.onFailure { Store.log("NaviHelper 초기화 실패: $it") }
    }

    private fun onRouteState(info: RouteStateInfo) {
        val ts = info.trafficStatus
        val items = ts?.congestionItemList.orEmpty()
            .filter { it.trafficCongestionType.name == "HEAVY" || it.trafficCongestionType.name == "SLOW" }
            .map { Congestion(it.offset, it.distance, it.roadName.orEmpty(), it.trafficCongestionType.name) }
        Store.update {
            it.copy(route = it.route.copy(active = info.isRouting, congestion = items,
                totalCongestionM = ts?.summary?.totalCongestionDist ?: 0.0))
        }
    }

    private fun onTbt(list: List<TBTInfo>) {
        val kinds = mapOf(
            TurnType.TURN_TOLL to "요금소",
            TurnType.TURN_ENTER_SERVICEAREA to "휴게소 진입로",
            TurnType.TURN_RESTAREA to "졸음쉼터",
            TurnType.TURN_DEVIDE_MERGE to "분기·합류 구간",
        )
        Store.update { s ->
            if (navStale(s)) return@update s
            val ev = list.mapNotNull { t -> kinds[t.type]?.let { AheadEvent(t.type.name, it, t.distance, s.progressM) } }
            s.copy(route = s.route.copy(tbt = ev))
        }
    }

    private fun onCautions(list: List<CautionPoint>) {
        val names = mapOf(
            "ACCIDENT_PRONE_AREA" to "사고 다발 구간", "HIGHWAY_CONSTRUCTION" to "공사 구간", "FOG_AREA" to "안개 구간",
            "FALLING_ROCK_AREA" to "낙석 주의 구간", "SLIPPERY_ROAD_ALERT" to "미끄럼 주의 구간",
            "WRONG_WAY_DRIVING_ALERT" to "역주행 주의",
        )
        Store.update { s ->
            if (navStale(s)) return@update s
            val ev = list.mapNotNull { c -> names[c.type.name]?.let { AheadEvent(c.type.name, it, c.remainDistance.toDouble(), s.progressM) } }
            s.copy(route = s.route.copy(cautions = ev))
        }
    }

    private fun onDriving(info: DrivingInfo) {
        Store.update { s ->
            // The emulator car never moves, so later reports are still measured from the start point.
            // Take the distance once per route (a moving car would keep updating it).
            val take = (s.route.totalM == 0.0 || !navStale(s)) && info.destination.distance > 0
            s.copy(route = s.route.copy(
                destName = info.destination.name.ifBlank { s.route.destName },
                remainAtReportM = if (take) info.destination.distance.toDouble() else s.route.remainAtReportM,
                progressAtRemainReportM = if (take) s.progressM else s.route.progressAtRemainReportM,
                totalM = if (s.route.totalM == 0.0 && take) info.destination.distance.toDouble() + s.progressM else s.route.totalM,
                waypoints = info.waypoints.map { it.name },
            ))
        }
    }

    /**
     * True when the route has advanced only virtually (demo): the navigation position has not moved,
     * so its distances are still measured from the start point.
     */
    private fun navStale(s: Snapshot) = s.progressM > 500 && !positionMoved

    private var routeStartLat: Double? = null
    private var routeStartLon: Double? = null
    @Volatile private var positionMoved = false

    private fun onLocation(lat: Double, lon: Double) {
        val la = routeStartLat; val lo = routeStartLon
        if (la == null || lo == null) { routeStartLat = lat; routeStartLon = lon; return }
        val dLat = (lat - la) * 111_000; val dLon = (lon - lo) * 88_000
        if (dLat * dLat + dLon * dLon > 150.0 * 150.0) positionMoved = true
    }

    // ------------------------------------------------------------------ actions
    fun requestRoute(name: String, lat: Double, lon: Double) = runCatching {
        Store.update { it.copy(progressM = 0.0, route = RouteView(destName = name, destLat = lat, destLon = lon)) }
        routeStartLat = null; routeStartLon = null; positionMoved = false
        navi?.getCurrentLocationInfo()
        navi?.requestRoute(RouteInfo(lat, lon, null, null, name, "", "", "", RouteOption.RECOMMENDED, "", false))
        Store.log("내비 경로 요청: $name")
    }.onFailure { Store.log("경로 요청 실패: $it") }

    fun addWaypoint(a: RestArea) = runCatching {
        navi?.addWaypoint(RequestWaypointInfo(a.lat, a.lon, a.name, WaypointIndex.FIRST, "", "", "", ""))
        Store.log("경유지 추가 요청: ${a.name}")
    }.onFailure { Store.log("경유지 추가 실패: $it") }

    fun requestTrip() = runCatching {
        val r = Store.now.route
        val lat = r.destLat ?: DEMO_DEST_LAT
        val lon = r.destLon ?: DEMO_DEST_LON
        navi?.requestTrip(TripRequest(lat, lon, "", r.destName.ifBlank { DEMO_DEST_NAME }, ""))
        Store.log("도착 예상 배터리 조회")
    }.onFailure { Store.log("도착 배터리 조회 실패: $it") }

    fun refreshNavi() = runCatching { navi?.getRouteStateInfo(); navi?.getTBTInfo(); navi?.getBookmarkInfo() }

    companion object {
        const val DEMO_DEST_NAME = "대전 거래처(시연)"
        const val DEMO_DEST_LAT = 36.3504
        const val DEMO_DEST_LON = 127.3845
    }
}

private fun CruiseControl?.toCruise() = when (this) {
    null -> Cruise.OFF
    CruiseControl.ACTIVATED -> Cruise.ACTIVATED
    CruiseControl.ENABLED -> Cruise.ENABLED
    CruiseControl.SUSPENDED -> Cruise.SUSPENDED
    CruiseControl.USER_OVERRIDE -> Cruise.USER_OVERRIDE
    CruiseControl.FORCED_DEACTIVATION_WARNING -> Cruise.FORCED_DEACTIVATION_WARNING
    else -> Cruise.UNKNOWN
}

private fun VehicleGear?.toGear() = when (this) {
    VehicleGear.GEAR_PARK -> Gear.P
    VehicleGear.GEAR_REVERSE -> Gear.R
    VehicleGear.GEAR_NEUTRAL -> Gear.N
    VehicleGear.GEAR_DRIVE -> Gear.D
    else -> Gear.UNKNOWN
}
