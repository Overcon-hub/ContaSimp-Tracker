package cl.contasimp.tracker

import android.content.Context

class AppPrefs(context: Context) {
    private val p = context.getSharedPreferences("contasimp_tracker", Context.MODE_PRIVATE)
    var baseUrl: String
        get() = p.getString("base_url", "https://app.contasimp.cl") ?: "https://app.contasimp.cl"
        set(v) = p.edit().putString("base_url", v.trimEnd('/')).apply()
    var token: String
        get() = p.getString("token", "") ?: ""
        set(v) = p.edit().putString("token", v.trim()).apply()
    var trackingEnabled: Boolean
        get() = p.getBoolean("tracking_enabled", false)
        set(v) = p.edit().putBoolean("tracking_enabled", v).apply()
}
