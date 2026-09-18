package com.segovia.tv.ajustes

import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.segovia.tv.playback.ExternalPlayerLauncher
import com.segovia.tv.ui.NavigationUi
import com.segovia.tv.ui.UiUtils

class PlayerSettingsScreen(private val activity:AppCompatActivity,private val nav:NavigationUi,private val player:ExternalPlayerLauncher,private val back:()->Unit){
    private val d get()=activity.resources.displayMetrics.density;private fun dp(v:Int)=UiUtils.dp(v,d)
    fun show(){
        val root=FrameLayout(activity).apply{setBackgroundColor(Color.parseColor(UiUtils.BG))};val bar=nav.topBar("Ajustes");root.addView(bar,FrameLayout.LayoutParams(-1,dp(76)))
        val scroll=ScrollView(activity).apply{layoutParams=FrameLayout.LayoutParams(-1,-1).apply{topMargin=dp(86);bottomMargin=dp(20)}};val c=LinearLayout(activity).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(48),dp(20),dp(48),dp(40))}
        c.addView(TextView(activity).apply{text="Ajustes";textSize=28f;setTextColor(Color.WHITE);setTypeface(null,Typeface.BOLD);setPadding(0,0,0,dp(25))})
        c.addView(TextView(activity).apply{text="REPRODUCCIÓN";textSize=15f;setTextColor(Color.parseColor(UiUtils.YELLOW));setTypeface(null,Typeface.BOLD);setPadding(0,0,0,dp(10))})
        val selected=TextView(activity).apply{text="Reproductor externo: ${player.preferredName() ?: "Seleccionar reproductor"}";textSize=16f;setTextColor(Color.WHITE);gravity=Gravity.CENTER_VERTICAL;background=UiUtils.focusable(Color.parseColor(UiUtils.CARD),dp(8).toFloat());isFocusable=true;isFocusableInTouchMode=true;setPadding(dp(20),0,dp(20),0);layoutParams=LinearLayout.LayoutParams(dp(620),dp(64));setOnClickListener{choose();}}
        c.addView(selected);c.addView(TextView(activity).apply{text="La aplicación utiliza únicamente reproductores externos. ExoPlayer/Media3 no forman parte de esta versión.";textSize=13f;setTextColor(Color.parseColor("#94A3B8"));setPadding(0,dp(14),0,0)})
        scroll.addView(c);root.addView(scroll);activity.setContentView(root);bar.bringToFront();selected.requestFocus()
    }
    private fun choose(){val ps=player.players();if(ps.isEmpty()){android.widget.Toast.makeText(activity,"No hay reproductores externos instalados",android.widget.Toast.LENGTH_LONG).show();return};val names=ps.map{it.loadLabel(activity.packageManager).toString()}.toTypedArray();AlertDialog.Builder(activity).setTitle("Reproductor externo preferido").setSingleChoiceItems(names,names.indexOfFirst{it==player.preferredName()}){dialog,which->val info=ps[which];player.save(info.activityInfo.packageName,names[which]);dialog.dismiss();show()}.setNegativeButton("CANCELAR",null).show()}
}
