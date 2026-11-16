package cz.kuba.dash407.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import cz.kuba.dash407.MainActivity
import cz.kuba.dash407.R
import cz.kuba.dash407.data.Gps
import cz.kuba.dash407.data.Prefs
import cz.kuba.dash407.data.Trip

/**
 * Služba Jízda: drží GPS zapnuté, i když je vpředu jiná aplikace (třeba navigace Mapy.com),
 * aby palubní počítač z GPS počítal celou cestu.
 */
class DriveService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        Trip.init(this)
        cz.kuba.dash407.car.Engine.init(this)
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Jízda", NotificationManager.IMPORTANCE_MIN))
            val open = PendingIntent.getActivity(
                this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
            )
            val n = Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("Dash 407")
                .setContentText("Palubní počítač počítá jízdu")
                .setContentIntent(open)
                .setOngoing(true)
                .build()
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            Gps.start(this, "service")
        } catch (e: Exception) {
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        Gps.stop("service")
        Trip.save()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "drive"

        fun start(context: Context) {
            if (!Prefs.driveService.value || !Gps.hasPermission(context)) return
            try {
                context.startForegroundService(Intent(context, DriveService::class.java))
            } catch (e: Exception) {
                // Systém službu teď nepovolí; GPS poběží aspoň s aplikací.
            }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, DriveService::class.java))
            } catch (e: Exception) {
            }
        }
    }
}
