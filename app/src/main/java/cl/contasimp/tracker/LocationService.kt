package cl.contasimp.tracker

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import org.json.JSONObject
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.Executors
import kotlin.math.abs

class LocationService: Service() {
    private lateinit var fused: FusedLocationProviderClient
    private val handler=Handler(Looper.getMainLooper())
    private val executor=Executors.newSingleThreadExecutor()
    private var requesting=false
    private lateinit var callback: LocationCallback
    private val queue by lazy { QueueDb(this) }
    private val prefs by lazy { AppPrefs(this) }

    override fun onCreate() {
        super.onCreate()
        fused=LocationServices.getFusedLocationProviderClient(this)
        callback=object: LocationCallback(){
            override fun onLocationResult(result: LocationResult){
                result.locations.forEach { loc ->
                    if(!loc.hasAccuracy() || loc.accuracy > 35f) {
                        val accuracyText=if(loc.hasAccuracy()) loc.accuracy.toInt().toString()+" m" else "sin precisión"
                        updateNotification("GPS buscando precisión · "+accuracyText)
                        return@forEach
                    }
                    if(abs(System.currentTimeMillis()-loc.time) > 60_000) return@forEach
                    val captured=Instant.ofEpochMilli(loc.time).atZone(ZoneId.systemDefault()).toOffsetDateTime().toString()
                    val p=JSONObject().apply{
                        put("lat",loc.latitude)
                        put("lng",loc.longitude)
                        put("accuracy",loc.accuracy.toDouble())
                        put("speed",if(loc.hasSpeed()) loc.speed.toDouble() else 0.0)
                        put("bearing",if(loc.hasBearing()) loc.bearing.toDouble() else 0.0)
                        put("epoch_ms",loc.time)
                        put("captured_at",captured)
                        put("source","fused")
                        put("battery",batteryPct())
                        put("model",Build.MANUFACTURER+" "+Build.MODEL)
                        put("device_uuid",deviceUuid())
                        put("app_version","1.41.60")
                    }
                    updateNotification("Seguimiento activo · precisión "+loc.accuracy.toInt()+" m")
                    executor.execute {
                        flushQueue()
                        if(!ApiClient.post(this@LocationService,"/api/terreno/location.php",p)) queue.add(p.toString())
                    }
                }
            }
        }
        createChannel()
        startForeground(14160,notification("Seguimiento laboral configurado"))
        handler.post(scheduleLoop)
    }

    private val scheduleLoop=object: Runnable {
        override fun run(){
            if(!prefs.trackingEnabled){ stopUpdates(); stopSelf(); return }
            val t=LocalTime.now()
            val inside=!t.isBefore(LocalTime.of(9,0)) && !t.isAfter(LocalTime.of(18,30))
            if(inside)startUpdates() else stopUpdates()
            handler.postDelayed(this,60_000)
        }
    }

    private fun startUpdates(){
        if(requesting)return
        if(ActivityCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED)return
        val req=LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY,8_000)
            .setMinUpdateIntervalMillis(5_000)
            .setMaxUpdateDelayMillis(15_000)
            .setMinUpdateDistanceMeters(4f)
            .setWaitForAccurateLocation(true)
            .build()
        fused.requestLocationUpdates(req,callback,Looper.getMainLooper())
        requesting=true
        updateNotification("Seguimiento activo · buscando GPS preciso")
    }

    private fun stopUpdates(){
        if(requesting){fused.removeLocationUpdates(callback);requesting=false}
        updateNotification("Fuera de horario · GPS detenido")
    }

    private fun flushQueue(){
        for((id,raw) in queue.first()){
            val ok=try{ApiClient.post(this,"/api/terreno/location.php",JSONObject(raw))}catch(_:Exception){false}
            if(ok)queue.delete(id) else break
        }
    }

    private fun batteryPct(): Int {
        val bm=getSystemService(Context.BATTERY_SERVICE) as android.os.BatteryManager
        return bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0,100)
    }

    private fun deviceUuid(): String {
        val sp=getSharedPreferences("contasimp_tracker",MODE_PRIVATE)
        var id=sp.getString("uuid",null)
        if(id==null){id=UUID.randomUUID().toString();sp.edit().putString("uuid",id).apply()}
        return id
    }

    private fun createChannel(){
        if(Build.VERSION.SDK_INT>=26){
            val c=NotificationChannel("tracker","Seguimiento de terreno",NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(c)
        }
    }

    private fun notification(text:String)=NotificationCompat.Builder(this,"tracker")
        .setSmallIcon(android.R.drawable.ic_menu_mylocation)
        .setContentTitle("ContaSimp · Ventas en Terreno")
        .setContentText(text)
        .setOngoing(true)
        .build()

    private fun updateNotification(text:String){
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(14160,notification(text))
    }

    override fun onStartCommand(intent: Intent?, flags:Int, startId:Int):Int=START_STICKY
    override fun onDestroy(){handler.removeCallbacks(scheduleLoop);stopUpdates();executor.shutdownNow();super.onDestroy()}
    override fun onBind(intent: Intent?): IBinder?=null
}
