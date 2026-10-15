package com.ryoku.transit

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.ryoku.transit.domain.BoardingDetector
import com.ryoku.transit.domain.LocationSample
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Modo «estoy esperando aquí»: servicio en primer plano y de duración acotada ([MAX_MINUTES]) que alimenta
 * un [BoardingDetector]. Las posiciones solo viven en memoria: no se guardan ni se envían. Al detectar la
 * subida, deja un evento pendiente (parada + hora) para que el usuario confirme la línea.
 */
class WaitingService : Service() {
    private lateinit var lm: LocationManager
    private var listener: LocationListener? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("MissingPermission")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopSelf(); return START_NOT_STICKY }
        val stopId = intent?.getStringExtra(EXTRA_STOP_ID) ?: return stopSelf().let { START_NOT_STICKY }
        val name = intent.getStringExtra(EXTRA_STOP_NAME).orEmpty()
        val lat = intent.getDoubleExtra(EXTRA_LAT, 0.0); val lon = intent.getDoubleExtra(EXTRA_LON, 0.0)
        createChannel()
        val stopPi = PendingIntent.getService(this, 1, Intent(this, WaitingService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("Esperando en $name")
            .setContentText("Si subes a un autobús te pediré confirmar la línea. Ubicación solo en el móvil.")
            .setOngoing(true).setOnlyAlertOnce(true)
            .addAction(0, "Cancelar", stopPi).build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION) else startForeground(NOTIF_ID, n)
        running.value = stopId

        val detector = BoardingDetector(stopId, lat, lon)
        lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val l = LocationListener { loc: Location ->
            val s = LocationSample(loc.time, loc.latitude, loc.longitude, if (loc.hasAccuracy()) loc.accuracy else 10f, if (loc.hasSpeed()) loc.speed else null)
            detector.onSample(s)?.let { ev ->
                getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString("pending_stop", ev.stopId).putLong("pending_at", ev.movedAtMs).apply()
                notifyDetected(name)
                stopSelf()
            }
        }
        listener = l
        runCatching { lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, l, Looper.getMainLooper()) }
            .onFailure { stopSelf() }
        handler.postDelayed({ stopSelf() }, MAX_MINUTES * 60_000L)
        return START_NOT_STICKY
    }

    private fun notifyDetected(stopName: String) {
        val open = PendingIntent.getActivity(this, 2, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("¿Subiste a un autobús?").setContentText("Salida de $stopName detectada. Toca para confirmar la línea.")
            .setContentIntent(open).setAutoCancel(true).build()
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIF_ID + 1, n)
    }

    private fun createChannel() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Modo esperando", NotificationManager.IMPORTANCE_LOW))
    }

    override fun onDestroy() {
        listener?.let { lm.removeUpdates(it) }
        handler.removeCallbacksAndMessages(null)
        running.value = null
        super.onDestroy()
    }

    companion object {
        const val PREFS = "learning"
        const val ACTION_STOP = "stop"
        const val EXTRA_STOP_ID = "stopId"; const val EXTRA_STOP_NAME = "stopName"; const val EXTRA_LAT = "lat"; const val EXTRA_LON = "lon"
        private const val CHANNEL = "waiting"; private const val NOTIF_ID = 42; private const val MAX_MINUTES = 25
        /** Parada para la que hay un servicio activo, o null. */
        val running = MutableStateFlow<String?>(null)

        fun start(ctx: Context, stopId: String, name: String, lat: Double, lon: Double) {
            val i = Intent(ctx, WaitingService::class.java).putExtra(EXTRA_STOP_ID, stopId).putExtra(EXTRA_STOP_NAME, name).putExtra(EXTRA_LAT, lat).putExtra(EXTRA_LON, lon)
            androidx.core.content.ContextCompat.startForegroundService(ctx, i)
        }
        fun stop(ctx: Context) { ctx.startService(Intent(ctx, WaitingService::class.java).setAction(ACTION_STOP)) }
    }
}
