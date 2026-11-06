package ai.highwaymate

import ai.highwaymate.core.Engine
import ai.highwaymate.core.PowerSave
import ai.highwaymate.core.TripLog
import ai.highwaymate.core.VehicleBridge
import ai.highwaymate.core.Voice
import android.app.Application

/** Process-wide objects shared by the foreground service and the screen. */
class HighwayMateApp : Application() {
    lateinit var tripLog: TripLog
    lateinit var voice: Voice
    lateinit var engine: Engine
    lateinit var bridge: VehicleBridge
    lateinit var powerSave: PowerSave
    private var started = false

    override fun onCreate() {
        super.onCreate()
        instance = this
        ai.highwaymate.core.Settings.load(this)
        tripLog = TripLog(this)
        voice = Voice(this)
        engine = Engine(tripLog) { text, level -> voice.say(text, level) }
        bridge = VehicleBridge(this) { engine.onCruiseEvent(it) }
        powerSave = PowerSave { bridge.vehicle }
    }

    @Synchronized fun startOnce() {
        if (started) return
        started = true
        bridge.start()
    }

    companion object {
        lateinit var instance: HighwayMateApp
            private set
    }
}
