package cl.contasimp.tracker

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity: AppCompatActivity(){
    private lateinit var prefs:AppPrefs; private lateinit var status:TextView
    override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);prefs=AppPrefs(this)
        val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(42,54,42,42)}
        fun label(s:String)=TextView(this).apply{text=s;textSize=14f;setPadding(0,18,0,6)}
        root.addView(TextView(this).apply{text="ContaSimp · Ventas en Terreno";textSize=25f;setTextColor(Color.rgb(15,23,42))})
        root.addView(TextView(this).apply{text="El GPS solo se solicita entre 09:00 y 18:30.";textSize=14f})
        root.addView(label("Servidor")); val url=EditText(this).apply{setText(prefs.baseUrl)};root.addView(url,ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT)
        root.addView(label("Token del dispositivo")); val token=EditText(this).apply{setText(prefs.token);hint="ct_..."};root.addView(token,ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT)
        status=TextView(this).apply{setPadding(0,24,0,18);textSize=16f};root.addView(status)
        val start=Button(this).apply{text="Activar seguimiento laboral"};root.addView(start); val stop=Button(this).apply{text="Desactivar seguimiento"};root.addView(stop)
        val settings=Button(this).apply{text="Abrir permisos de ubicación"};root.addView(settings)
        start.setOnClickListener{prefs.baseUrl=url.text.toString();prefs.token=token.text.toString();if(prefs.token.isBlank()){Toast.makeText(this,"Ingresa el token generado en ContaSimp.",Toast.LENGTH_LONG).show();return@setOnClickListener};requestPermissionsThenStart()}
        stop.setOnClickListener{prefs.trackingEnabled=false;stopService(Intent(this,LocationService::class.java));refresh()}
        settings.setOnClickListener{startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:$packageName")))}
        setContentView(root);refresh()
    }
    private fun requestPermissionsThenStart(){ val list=mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION);if(Build.VERSION.SDK_INT>=33)list.add(Manifest.permission.POST_NOTIFICATIONS);if(list.any{ContextCompat.checkSelfPermission(this,it)!=PackageManager.PERMISSION_GRANTED}){ActivityCompat.requestPermissions(this,list.toTypedArray(),500);return};prefs.trackingEnabled=true;ContextCompat.startForegroundService(this,Intent(this,LocationService::class.java));refresh();if(Build.VERSION.SDK_INT>=29 && ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_BACKGROUND_LOCATION)!=PackageManager.PERMISSION_GRANTED)Toast.makeText(this,"Para mayor estabilidad, en Permisos > Ubicación selecciona 'Permitir todo el tiempo'.",Toast.LENGTH_LONG).show() }
    override fun onRequestPermissionsResult(requestCode:Int,permissions:Array<out String>,grantResults:IntArray){super.onRequestPermissionsResult(requestCode,permissions,grantResults);if(requestCode==500 && grantResults.isNotEmpty() && grantResults.all{it==PackageManager.PERMISSION_GRANTED})requestPermissionsThenStart() else Toast.makeText(this,"Se necesita permiso de ubicación para registrar la ruta laboral.",Toast.LENGTH_LONG).show()}
    private fun refresh(){status.text=if(prefs.trackingEnabled)"🟢 Seguimiento habilitado\nHorario: 09:00–18:30" else "⚪ Seguimiento deshabilitado"}
}
