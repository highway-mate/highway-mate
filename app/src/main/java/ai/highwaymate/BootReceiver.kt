package ai.highwaymate

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Starts the coach when the head unit boots, so the trip log needs no tap at departure. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        runCatching { context.startForegroundService(Intent(context, MonitorService::class.java)) }
    }
}
