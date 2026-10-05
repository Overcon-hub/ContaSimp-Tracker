package cl.contasimp.tracker

import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.Executors

object UpdateManager {
    private const val UPDATE_PREFS="contasimp_updates"
    private const val CHANNEL="contasimp_updates"
    private const val NOTIFICATION_ID=14161
    private val executor=Executors.newSingleThreadExecutor()

    fun check(context: Context, showNoUpdate: Boolean=false) {
        executor.execute {
            try {
                val prefs=AppPrefs(context)
                val conn=(URL(prefs.baseUrl+"/api/terreno/app-version.php?version_code="+BuildConfig.VERSION_CODE).openConnection() as HttpURLConnection)
                conn.connectTimeout=10000
                conn.readTimeout=10000
                conn.setRequestProperty("Accept","application/json")
                val raw=conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()
                val j=JSONObject(raw)
                if(!j.optBoolean("ok",false) || !j.optBoolean("available",false)) {
                    if(showNoUpdate) notifyText(context,"ContaSimp Terreno","Ya tienes la versión más reciente.")
                    return@execute
                }
                val code=j.optInt("version_code",0)
                if(code<=BuildConfig.VERSION_CODE) {
                    if(showNoUpdate) notifyText(context,"ContaSimp Terreno","Ya tienes la versión más reciente.")
                    return@execute
                }
                val version=j.optString("version_name")
                val apkUrl=j.optString("apk_url")
                val sha=j.optString("sha256").lowercase()
                if(apkUrl.isBlank() || sha.length!=64) return@execute
                download(context,version,code,apkUrl,sha)
            } catch (_:Exception) {}
        }
    }

    private fun download(context: Context, version:String, code:Int, url:String, sha:String) {
        val sp=context.getSharedPreferences(UPDATE_PREFS,Context.MODE_PRIVATE)
        val existing=sp.getLong("download_id",-1)
        if(existing>0 && sp.getInt("version_code",0)==code) return
        val filename="ContaSimp-Terreno-v"+version+".apk"
        val file=File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),filename)
        if(file.exists()) file.delete()
        val req=DownloadManager.Request(Uri.parse(url))
            .setTitle("ContaSimp Terreno v"+version)
            .setDescription("Descargando actualización")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setDestinationInExternalFilesDir(context,Environment.DIRECTORY_DOWNLOADS,filename)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
        val id=(context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(req)
        sp.edit().putLong("download_id",id).putInt("version_code",code).putString("version_name",version).putString("filename",filename).putString("sha256",sha).apply()
    }

    fun onDownloadComplete(context: Context, id:Long) {
        val sp=context.getSharedPreferences(UPDATE_PREFS,Context.MODE_PRIVATE)
        if(id!=sp.getLong("download_id",-1)) return
        val filename=sp.getString("filename","") ?: return
        val expected=sp.getString("sha256","") ?: return
        val file=File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),filename)
        if(!file.exists() || sha256(file)!=expected) {
            sp.edit().clear().apply()
            file.delete()
            notifyText(context,"Actualización no instalada","La descarga no superó la validación de seguridad.")
            return
        }
        sp.edit().putBoolean("ready",true).apply()
        notifyReady(context)
    }

    fun installIfReady(context: Context): Boolean {
        val sp=context.getSharedPreferences(UPDATE_PREFS,Context.MODE_PRIVATE)
        if(!sp.getBoolean("ready",false)) return false
        val filename=sp.getString("filename","") ?: return false
        val file=File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),filename)
        if(!file.exists()) return false
        if(Build.VERSION.SDK_INT>=26 && !context.packageManager.canRequestPackageInstalls()) {
            val i=Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+context.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(i)
            return true
        }
        val uri=FileProvider.getUriForFile(context,context.packageName+".fileprovider",file)
        val i=Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri,"application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(i)
        return true
    }

    private fun notifyReady(context:Context) {
        createChannel(context)
        val launch=Intent(context,MainActivity::class.java).putExtra("install_update",true)
        val pi=PendingIntent.getActivity(context,14161,launch,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n=NotificationCompat.Builder(context,CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Actualización de ContaSimp lista")
            .setContentText("Toca para instalarla. El seguimiento y token se conservarán.")
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIFICATION_ID,n)
    }

    private fun notifyText(context:Context,title:String,text:String) {
        createChannel(context)
        val n=NotificationCompat.Builder(context,CHANNEL).setSmallIcon(android.R.drawable.ic_popup_sync).setContentTitle(title).setContentText(text).setAutoCancel(true).build()
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIFICATION_ID,n)
    }

    private fun createChannel(context:Context) {
        if(Build.VERSION.SDK_INT>=26) {
            val c=NotificationChannel(CHANNEL,"Actualizaciones ContaSimp",NotificationManager.IMPORTANCE_DEFAULT)
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(c)
        }
    }

    private fun sha256(file:File):String {
        val md=MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val b=ByteArray(8192)
            while(true) {
                val n=input.read(b)
                if(n<=0) break
                md.update(b,0,n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
