package com.bbzone.valvecontrol

import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.RadioButton
import android.widget.TextView
import android.widget.Toast
import android.widget.Toast.makeText
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import com.bbzone.valvecontrol.helpers.MqttHelper
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended
import org.eclipse.paho.client.mqttv3.MqttMessage
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.*
import kotlin.concurrent.timerTask

@RequiresApi(Build.VERSION_CODES.O)
class MainActivity : AppCompatActivity() {

    lateinit var sharedPreferences: SharedPreferences
    lateinit var mqttHelper: MqttHelper

    var fmtTime: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    var lastOnA: LocalDateTime  = LocalDateTime.MIN
    var lastOnB: LocalDateTime  = LocalDateTime.MIN
    var lastOnC: LocalDateTime  = LocalDateTime.MIN
    var lastOnD: LocalDateTime  = LocalDateTime.MIN
    var lastOffA: LocalDateTime = LocalDateTime.MIN
    var lastOffB: LocalDateTime = LocalDateTime.MIN
    var lastOffC: LocalDateTime = LocalDateTime.MIN
    var lastOffD: LocalDateTime = LocalDateTime.MIN

    private var listener: SharedPreferences.OnSharedPreferenceChangeListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _: SharedPreferences, _: String ->
            preferencesChangeCB()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById(R.id.toolbar_main))

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)

        sharedPreferences.registerOnSharedPreferenceChangeListener(listener)

        startMqtt()

        findViewById<TextView>(R.id.tvVentA).apply {
            text = sharedPreferences.getString("pref_valve_a", "Ventil A")
        }

        findViewById<TextView>(R.id.tvVentB).apply {
            text = sharedPreferences.getString("pref_valve_b", "Ventil B")
        }

        findViewById<TextView>(R.id.tvVentC).apply {
            text = sharedPreferences.getString("pref_valve_c", "Ventil C")
        }

        findViewById<TextView>(R.id.tvVentD).apply {
            text = sharedPreferences.getString("pref_valve_d", "Ventil D")
        }

        showConnectionState()
    }

    override fun onResume() {
        super.onResume()
        // not sure why, but MQTT needs some time to settle. So wait 0,5s before requesting the refresh
        Timer().schedule(timerTask {
            val prefix = sharedPreferences.getString("pref_prefix", "YardControl")
            mqttHelper.mqttAndroidClient.publish("/$prefix/Command/Refresh",         "INFO".toByteArray(), 1 ,false)
            mqttHelper.mqttAndroidClient.publish("/$prefix/Command/getEventHistory", "INFO".toByteArray(), 1 ,false)
        }, 500)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater: MenuInflater = menuInflater
        inflater.inflate( R.menu.settings, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle item selection
        val prefix = sharedPreferences.getString("pref_prefix", "YardControl")
        return when (item.itemId) {
            R.id.menu_refresh -> {
                makeText(applicationContext, "Refresh", Toast.LENGTH_SHORT).show()

                // Request status update from valveControl server
                mqttHelper.mqttAndroidClient.publish("/$prefix/Command/Refresh",           "ON".toByteArray(), 1, false )
                mqttHelper.mqttAndroidClient.publish("/$prefix/Command/getEventHistory", "INFO".toByteArray(), 1 ,false)

                showConnectionState()
                true
            }
            R.id.menu_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            R.id.menu_timer -> {
                startActivity(Intent(this, SchedulerActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun preferencesChangeCB() {
        if(mqttHelper.getConnectionState()) {
            findViewById<TextView>(R.id.tvVentA).apply {
                text = sharedPreferences.getString("pref_valve_a", "Ventil A")
            }

            findViewById<TextView>(R.id.tvVentB).apply {
                text = sharedPreferences.getString("pref_valve_b", "Ventil B")
            }

            findViewById<TextView>(R.id.tvVentC).apply {
                text = sharedPreferences.getString("pref_valve_c", "Ventil C")
            }

            findViewById<TextView>(R.id.tvVentD).apply {
                text = sharedPreferences.getString("pref_valve_d", "Ventil D")
            }

            mqttHelper.updateTopic()
        }

        makeText(applicationContext, "Preferences Changed", Toast.LENGTH_SHORT).show()
        showConnectionState()
    }

    fun showConnectionState() {
        findViewById<TextView>(R.id.online).apply {
            if (mqttHelper.getConnectionState()) {
                text = getString(R.string.Online)
                setTextColor(Color.parseColor("#00FF00")) // Green
            } else {
                text = getString(R.string.Offline)
                setTextColor(Color.parseColor("#FF0000")) // Red
            }
        }
    }

    private fun getTimeDiff (start: LocalDateTime, end: LocalDateTime ) : String {
        val diff:   Long = ChronoUnit.MINUTES.between(start, end)
        val hours:  Int = (diff / 60).toInt()
        val mins:   Int = (diff % 60).toInt()

        if ( hours > 0 ) {
            return (String.format( "%02d:%02d Stunden", hours, mins ))
        } else if ( mins == 1 ) {
            return (String.format( "eine Minute", mins ))
        } else if ( mins > 0 ) {
            return (String.format( "%d Minuten", mins ))
        } else {
            return ("wenige Sekunden")
        }
    }

    private fun getStatusMessage( lastOn: LocalDateTime, lastOff: LocalDateTime ) : String {
        var info = "Keine Status Historie bekannt"
        if ( lastOn !=  LocalDateTime.MIN || lastOff !=  LocalDateTime.MIN ) {
            if ( lastOff >= lastOn ) {
                val date = lastOn.toLocalDate()
                val time = lastOn.toLocalTime()
                val today = LocalDate.now()
                val dateFormat = DateTimeFormatter.ofPattern("dd.MM.yyyy")

                if ( date == today ) {
                    info = "Zuletzt an um " + time + " für " + getTimeDiff( lastOn, lastOff )
                } else if ( date == today.minusDays(1) ) {
                    info = "Zuletzt an gestern um " + time + " für " + getTimeDiff( lastOn, lastOff )
                } else if ( date > today.minusDays(7) ) {
                    info = "Zuletzt an am " + date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.GERMAN) + " um " + time + " für " + getTimeDiff( lastOn, lastOff )
                } else {
                    info = "Zuletzt an am " + dateFormat.format(lastOn) + " um " + time + "h für " + getTimeDiff( lastOn, lastOff )
                }
            } else {
                info = "Eingeschaltet seit " + getTimeDiff( lastOn, LocalDateTime.now() )
            }
        }
        return ( info )
    }

    private fun startMqtt() {
        mqttHelper = MqttHelper(applicationContext)

        mqttHelper.setCallback(object : MqttCallbackExtended {

            override fun connectComplete(b: Boolean, s: String) {
                Log.i("Main", "MQTT Connection Complete")
                showConnectionState()

                // Request status update from valveControl server
                mqttHelper.mqttAndroidClient.publish(
                    "/" + sharedPreferences.getString("pref_prefix", "YardControl").toString() + "/Command/Refresh",
                    "ON".toByteArray(),
                    1,
                    false
                )
            }

            override fun connectionLost(throwable: Throwable) {
                Log.w("Main", "MQTT Connection Lost")
                showConnectionState()
            }

            @Throws(Exception::class)

            override fun messageArrived(topic: String, message: MqttMessage) {

                var msgType: Int
                val prefix = sharedPreferences.getString("pref_prefix", "YardControl")
                val switch: RadioButton =
                    when (topic) {
                        "/$prefix/State/Valve_A" -> { msgType = 1; findViewById(R.id.btVentA) }
                        "/$prefix/State/Valve_B" -> { msgType = 1; findViewById(R.id.btVentB) }
                        "/$prefix/State/Valve_C" -> { msgType = 1; findViewById(R.id.btVentC) }
                        "/$prefix/State/Valve_D" -> { msgType = 1; findViewById(R.id.btVentD) }
                        "/$prefix/State/Timer"   -> { msgType = 1; findViewById(R.id.btTimer) }
                        else -> {
                            msgType = 0
                            findViewById(R.id.btVentA)
                        }
                    }

                if ( msgType == 1 ) {
                    when (message.toString()) {
                        "ON"  -> { switch.isChecked = true  }
                        "OFF" -> { switch.isChecked = false }
                        "1"   -> { switch.isChecked = true  }
                        "0"   -> { switch.isChecked = false }
                    }
                } else if ( topic == "/$prefix/State/LastOn" ) {
                    val parts  = message.toString().split(" ")
                    val lastOn = LocalDateTime.parse( parts[1] + " " + parts[2], fmtTime )

                    when(parts[0]) {
                        "Valve_A" -> { lastOnA = lastOn }
                        "Valve_B" -> { lastOnB = lastOn }
                        "Valve_C" -> { lastOnC = lastOn }
                        "Valve_D" -> { lastOnD = lastOn }
                    }
                    msgType = 2

                } else if ( topic == "/$prefix/State/LastOff" ) {
                    val parts   = message.toString().split(" ")
                    val lastOff = LocalDateTime.parse( parts[1] + " " + parts[2], fmtTime )

                    when(parts[0]) {
                        "Valve_A" -> { lastOffA = lastOff }
                        "Valve_B" -> { lastOffB = lastOff }
                        "Valve_C" -> { lastOffC = lastOff }
                        "Valve_D" -> { lastOffD = lastOff }
                    }
                    msgType = 3
                }

                if ( msgType ==2 || msgType == 3 ) {
                    findViewById<TextView>(R.id.tvVentA_info).apply { text = getStatusMessage(lastOnA, lastOffA) }
                    findViewById<TextView>(R.id.tvVentB_info).apply { text = getStatusMessage(lastOnB, lastOffB) }
                    findViewById<TextView>(R.id.tvVentC_info).apply { text = getStatusMessage(lastOnC, lastOffC) }
                    findViewById<TextView>(R.id.tvVentD_info).apply { text = getStatusMessage(lastOnD, lastOffD) }
                }
            }

            override fun deliveryComplete(iMqttDeliveryToken: IMqttDeliveryToken) {

            }
        })
        showConnectionState()
    }

    fun buttonPressCB(view: View) {
        val prefix = sharedPreferences.getString("pref_prefix", "YardControl")
        var element = "?"
        var label   = "?"
        var message = "?"

        when (view.id) {
            R.id.btVentA_Ein -> {
                message = "ON"
                element = "Valve_A"
                label = sharedPreferences.getString("pref_valve_a", "Ventil A").toString() + " EIN"
            }

            R.id.btVentA_Aus -> {
                message = "OFF"
                element = "Valve_A"
                label = sharedPreferences.getString("pref_valve_a", "Ventil A").toString() + " AUS"
            }

            R.id.btVentB_Ein -> {
                message = "ON"
                element = "Valve_B"
                label = sharedPreferences.getString("pref_valve_b", "Ventil B").toString() + " EIN"
            }

            R.id.btVentB_Aus -> {
                message = "OFF"
                element = "Valve_B"
                label = sharedPreferences.getString("pref_valve_b", "Ventil B").toString() + " AUS"
            }

            R.id.btVentC_Ein -> {
                message = "ON"
                element = "Valve_C"
                label = sharedPreferences.getString("pref_valve_c", "Ventil C").toString() + " EIN"
            }

            R.id.btVentC_Aus -> {
                message = "OFF"
                element = "Valve_C"
                label = sharedPreferences.getString("pref_valve_c", "Ventil C").toString() + " AUS"
            }

            R.id.btVentD_Ein -> {
                message = "ON"
                element = "Valve_D"
                label = sharedPreferences.getString("pref_valve_d", "Ventil D").toString() + " EIN"
            }

            R.id.btVentD_Aus -> {
                message = "OFF"
                element = "Valve_D"
                label = sharedPreferences.getString("pref_valve_d", "Ventil D").toString() + " AUS"
            }

            R.id.btTimer_Ein -> {
                element = "Timer"
                label = "Timer AN"
                message = "ON"
            }

            R.id.btTimer_Aus -> {
                element = "Timer"
                label = "Timer AUS"
                message = "OFF"
            }
        }

        makeText(applicationContext, label, Toast.LENGTH_SHORT).show()
        mqttHelper.mqttAndroidClient.publish("/$prefix/Command/$element", message.toByteArray(),1,false)
        mqttHelper.mqttAndroidClient.publish("/$prefix/Command/getEventHistory", "INFO".toByteArray(), 1 ,false)
    }
}
