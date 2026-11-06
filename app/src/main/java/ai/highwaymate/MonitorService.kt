package ai.highwaymate

import ai.highwaymate.core.AppClock
import ai.highwaymate.core.Cruise
import ai.highwaymate.core.Gear
import ai.highwaymate.core.MIN
import ai.highwaymate.core.Store
import ai.highwaymate.core.VehicleBridge
import ai.highwaymate.core.badge
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper

/**
 * Resident monitor. Android 14 freezes background apps, so the coach runs as a foreground service:
 * it keeps receiving cruise and route events while the navigation is in front.
 */
class MonitorService : Service() {
    private val main = Handler(Looper.getMainLooper())
    private val app get() = application as HighwayMateApp

    private val tick = object : Runnable {
        override fun run() {
            runCatching { app.engine.tick() }.onFailure { Store.log("엔진 오류: $it") }
            main.postDelayed(this, 1000)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("coach", "안전 코치", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, ai.highwaymate.ui.MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(this, "coach")
            .setContentTitle("하이웨이 메이트")
            .setContentText("안전 코치와 운행일지가 켜져 있어요")
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentIntent(open)
            .build()
        runCatching { startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE) }
            .onFailure { Store.log("상주 서비스 시작 실패: $it") }
        nm.createNotificationChannel(NotificationChannel("alert", "주행 중 안내", NotificationManager.IMPORTANCE_HIGH).apply {
            setSound(null, null); enableVibration(false)
        })
        app.startOnce()
        registerReceiver(demo, IntentFilter(DEMO_ACTION), Context.RECEIVER_EXPORTED)
        registerReceiver(action, IntentFilter(ACTION), Context.RECEIVER_NOT_EXPORTED)
        main.post(tick)
        main.post(watchGuidance)
    }

    // ------------------------------------------------------------------ heads-up card over the navigation
    private var lastGuidanceAt = 0L

    /**
     * While driving the navigation stays in front. Each new message is also posted as a heads-up card on top of
     * the map; a rest prompt carries its one button ("경유지로 넣기") so the driver never has to leave the map.
     */
    private val watchGuidance = object : Runnable {
        override fun run() {
            val g = Store.now.guidance
            if (g != null && g.atApp != lastGuidanceAt) {
                lastGuidanceAt = g.atApp
                main.postDelayed({ postHeadsUp() }, 200)   // the rest prompt is stored right after the message
            }
            main.postDelayed(this, 300)
        }
    }

    private fun postHeadsUp() {
        val s = Store.now
        val g = s.guidance ?: return
        if (ai.highwaymate.ui.MainActivity.visible) return          // the app screen already shows it
        if (!ai.highwaymate.core.Settings.now.headsUp) return
        val nm = getSystemService(NotificationManager::class.java)
        val open = PendingIntent.getActivity(this, 0, Intent(this, ai.highwaymate.ui.MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val title = when {
            g.kind == "rest-pre" || g.kind == "rest-now" -> "${g.level.badge()} · 휴식 권유"
            g.kind == "rest-arrive" -> "${g.level.badge()} · 휴게소 도착"
            g.kind == "congestion" -> "${g.level.badge()} · 정체"
            g.kind == "route-event" -> "${g.level.badge()}"
            else -> g.level.badge()
        }
        val b = Notification.Builder(this, "alert")
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(title)
            .setSubText("하이웨이 메이트")
            .setContentText(g.text)
            .setStyle(Notification.BigTextStyle().bigText(g.text))
            .setCategory(Notification.CATEGORY_NAVIGATION)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setTimeoutAfter(12_000)
        val p = s.prompt
        if (p != null && p.atApp >= 0) {
            val accept = PendingIntent.getBroadcast(this, 1, Intent(ACTION).setPackage(packageName).putExtra("do", "accept"), PendingIntent.FLAG_IMMUTABLE)
            val dismiss = PendingIntent.getBroadcast(this, 2, Intent(ACTION).setPackage(packageName).putExtra("do", "dismiss"), PendingIntent.FLAG_IMMUTABLE)
            b.addAction(Notification.Action.Builder(null, "${p.area.name.removeSuffix("휴게소")} 경유", accept).build())
            b.addAction(Notification.Action.Builder(null, "괜찮아요", dismiss).build())
            b.setTimeoutAfter(30_000)
        }
        nm.notify(2, b.build())
    }

    private val action = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            when (i.getStringExtra("do")) {
                "accept" -> Store.now.prompt?.let { app.bridge.addWaypoint(it.area); app.engine.onWaypointAccepted() }
                "dismiss" -> Store.update { it.copy(prompt = null) }
            }
            getSystemService(NotificationManager::class.java).cancel(2)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY

    override fun onDestroy() {
        main.removeCallbacks(tick)
        main.removeCallbacks(watchGuidance)
        runCatching { unregisterReceiver(demo) }
        runCatching { unregisterReceiver(action) }
        super.onDestroy()
    }

    /**
     * Demo control for the screen recording, e.g.
     * adb shell am broadcast -a ai.highwaymate.DEMO --es cmd speed --es v 100
     * Every injected value is also shown on the simulator panel, so the video never hides what is simulated.
     */
    private val demo = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            val cmd = i.getStringExtra("cmd") ?: return
            val v = i.getStringExtra("v")
            Demo.run(app, cmd, v)
        }
    }

    companion object {
        const val DEMO_ACTION = "ai.highwaymate.DEMO"
        const val ACTION = "ai.highwaymate.ACTION"
    }
}

object Demo {
    fun run(app: HighwayMateApp, cmd: String, v: String?) {
        when (cmd) {
            "speed" -> Store.update { it.copy(sim = it.sim.copy(speedKmh = v?.toFloatOrNull())) }
            "fuel" -> Store.update { it.copy(sim = it.sim.copy(fuelPct = v?.toIntOrNull())) }
            "daw" -> Store.update { it.copy(sim = it.sim.copy(daw = v == "on")) }
            "gear" -> Store.update { it.copy(sim = it.sim.copy(gear = v?.let { g -> runCatching { Gear.valueOf(g) }.getOrNull() })) }
            "cruise" -> {
                val c = v?.let { runCatching { Cruise.valueOf(it) }.getOrNull() }
                Store.update { it.copy(sim = it.sim.copy(cruise = c)) }
                if (c != null) app.engine.onCruiseEvent(c)
            }
            "time" -> AppClock.setFactor(v?.toDoubleOrNull() ?: 1.0)
            "drive" -> {   // pretend the car has already been driving for v minutes
                val m = v?.toLongOrNull() ?: 0
                Store.update { it.copy(driveStartApp = AppClock.now() - m * MIN) }
            }
            "progress" -> Store.update { it.copy(progressM = it.progressM + (v?.toDoubleOrNull() ?: 0.0)) }
            "at" -> Store.update { it.copy(progressM = v?.toDoubleOrNull() ?: it.progressM) }
            "atrest" -> Store.update { s -> s.prompt?.let { s.copy(progressM = (it.area.km - 0.3) * 1000) } ?: s }
            "caption" -> Store.update { it.copy(sim = it.sim.copy(caption = v.orEmpty())) }
            "panel" -> Store.update { it.copy(sim = it.sim.copy(panelVisible = v != "off")) }
            "route" -> app.bridge.requestRoute(VehicleBridge.DEMO_DEST_NAME, VehicleBridge.DEMO_DEST_LAT, VehicleBridge.DEMO_DEST_LON)
            "navi" -> app.bridge.refreshNavi()
            "accept" -> Store.now.prompt?.let { app.bridge.addWaypoint(it.area); app.engine.onWaypointAccepted() }
            "trip" -> app.bridge.requestTrip()
            "mode" -> ai.highwaymate.core.AlertMode.entries.firstOrNull { it.name == v }?.let { m -> ai.highwaymate.core.Settings.set { it.copy(mode = m) } }
            "temp" -> runCatching {
                app.bridge.vehicle?.getHVAC()?.setHvacTemperature(
                    ai.pleos.playground.vehicle.api.model.area.SeatArea(ai.pleos.playground.vehicle.constant.area.SeatValue.ROW_1_LEFT),
                    v?.toFloatOrNull() ?: 22f, ai.pleos.playground.vehicle.constant.unit.TemperatureUnit.values().first(), { _, _ -> }, {})
            }
            "tab" -> Store.update { it.copy(sim = it.sim.copy(tab = v?.toIntOrNull())) }
            "classify" -> app.engine.classify(if (v == "personal") ai.highwaymate.core.Purpose.PERSONAL else ai.highwaymate.core.Purpose.BUSINESS)
            "save" -> app.powerSave.apply(v ?: "mid") { r -> Store.update { it.copy(ev = it.ev.copy(appliedLevel = v, lastResult = r)) }; Store.log("절전 적용: $r") }
            "clearlog" -> app.tripLog.clear()
            "reset" -> {
                app.engine.resetDemo()
                AppClock.setFactor(1.0)
                Store.update {
                    it.copy(sim = ai.highwaymate.core.SimState(), driveStartApp = null, parkedSinceApp = null, assistSinceApp = null,
                        progressM = 0.0, guidance = null, prompt = null, restingAt = null, trip = null, pendingClassification = null,
                        today = ai.highwaymate.core.TodayStats(), ev = ai.highwaymate.core.EvState(), log = emptyList())
                }
            }
        }
    }
}
