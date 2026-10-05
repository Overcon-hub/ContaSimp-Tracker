package cl.contasimp.tracker

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object ApiClient {
    fun post(context: Context, endpoint: String, payload: JSONObject): Boolean {
        val prefs=AppPrefs(context); if(prefs.token.isBlank()) return false
        val conn=(URL(prefs.baseUrl+endpoint).openConnection() as HttpURLConnection)
        return try {
            conn.requestMethod="POST"; conn.connectTimeout=10000; conn.readTimeout=10000; conn.doOutput=true
            conn.setRequestProperty("Authorization", "Bearer ${prefs.token}"); conn.setRequestProperty("Content-Type","application/json; charset=utf-8")
            conn.outputStream.use{it.write(payload.toString().toByteArray(Charsets.UTF_8))}
            conn.responseCode in 200..299
        } catch (_: Exception) { false } finally { conn.disconnect() }
    }
}
