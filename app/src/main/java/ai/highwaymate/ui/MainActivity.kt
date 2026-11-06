package ai.highwaymate.ui

import ai.highwaymate.HighwayMateApp
import ai.highwaymate.MonitorService
import ai.highwaymate.core.Store
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

class MainActivity : ComponentActivity() {
    private val app get() = application as HighwayMateApp

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startForegroundService(Intent(this, MonitorService::class.java))
        // Speed is an AAOS runtime permission (shown as "location access"); ask once at first run.
        val perms = arrayOf("android.car.permission.CAR_SPEED", "android.permission.POST_NOTIFICATIONS")
            .filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (perms.isNotEmpty()) requestPermissions(perms.toTypedArray(), 1)
        setContent { HighwayMateScreen(app) }
    }

    override fun onResume() {
        super.onResume(); visible = true
        // the screen shows the same message, so drop the heads-up card
        getSystemService(android.app.NotificationManager::class.java).cancel(2)
    }
    override fun onPause() { visible = false; super.onPause() }

    companion object {
        @Volatile var visible = false
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        app.bridge.subscribeSpeed()
        Store.log("속도 권한: ${if (checkSelfPermission("android.car.permission.CAR_SPEED") == PackageManager.PERMISSION_GRANTED) "허용" else "거부"}")
    }
}
