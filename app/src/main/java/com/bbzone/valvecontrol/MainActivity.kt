package com.bbzone.valvecontrol

import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
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
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended
import org.eclipse.paho.client.mqttv3.MqttMessage
import com.bbzone.valvecontrol.helpers.MqttHelper
import java.util.*
import kotlin.concurrent.timerTask

class MainActivity : AppCompatActivity() {

    lateinit var sharedPreferences: SharedPreferences
    lateinit var mqttHelper: MqttHelper

    var listener: SharedPreferences.OnSharedPreferenceChangeListener =
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
            setText(sharedPreferences.getString("pref_valve_a", "Ventil A"))
        }

        findViewById<TextView>(R.id.tvVentB).apply {
            setText(sharedPreferences.getString("pref_valve_b", "Ventil B"))
        }

        findViewById<TextView>(R.id.tvVentC).apply {
            setText(sharedPreferences.getString("pref_valve_c", "Ventil C"))
        }

        findViewById<TextView>(R.id.tvVentD).apply {
            setText(sharedPreferences.getString("pref_valve_d", "Ventil D"))
        }

        showConnectionState()
    }

    override fun onResume() {
        super.onResume()
        // not sure why, but MQTT needs some time to settle. So wait 0,5s before requesting the refresh
        Timer().schedule(timerTask {
            val prefix = sharedPreferences.getString("pref_prefix", "YardControl")
            mqttHelper.mqttAndroidClient.publish("/$prefix/Command/Refresh", "INFO".toByteArray(), 1 ,false)
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
                mqttHelper.mqttAndroidClient.publish(
                    "/" + prefix + "/Command/Refresh",
                    "ON".toByteArray(),
                    1,
                    false
                )

                showConnectionState()
                true
            }
            R.id.menu_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                makeText(applicationContext, "Settings", Toast.LENGTH_SHORT).show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    fun preferencesChangeCB() {
        if(mqttHelper.getConnectionState()) {
            findViewById<TextView>(R.id.tvVentA).apply {
                setText(sharedPreferences.getString("pref_valve_a", "Ventil A"))
            }

            findViewById<TextView>(R.id.tvVentB).apply {
                setText(sharedPreferences.getString("pref_valve_b", "Ventil B"))
            }

            findViewById<TextView>(R.id.tvVentC).apply {
                setText(sharedPreferences.getString("pref_valve_c", "Ventil C"))
            }

            findViewById<TextView>(R.id.tvVentD).apply {
                setText(sharedPreferences.getString("pref_valve_d", "Ventil D"))
            }

            mqttHelper.updateTopic()
        }

        makeText(applicationContext, "Preferences Changed", Toast.LENGTH_SHORT).show()
        showConnectionState()
    }

    fun showConnectionState() {
        val apply = findViewById<TextView>(R.id.online).apply {
            if (mqttHelper.getConnectionState()) {
                text = "Online"
                setTextColor(Color.parseColor("#00FF00")) // Green
            } else {
                text = "Offline"
                setTextColor(Color.parseColor("#FF0000")) // Red
            }
        }
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

                var valid: Boolean = true
                val prefix = sharedPreferences.getString("pref_prefix", "YardControl")
                val switch: RadioButton =
                    when (topic) {
                        "/$prefix/State/Valve_A" -> { findViewById(R.id.btVentA) }
                        "/$prefix/State/Valve_B" -> { findViewById(R.id.btVentB) }
                        "/$prefix/State/Valve_C" -> { findViewById(R.id.btVentC) }
                        "/$prefix/State/Valve_D" -> { findViewById(R.id.btVentD) }
                        "/$prefix/State/Timer"   -> { findViewById(R.id.btTimer) }

                        else -> {
                            valid = false
                            findViewById(R.id.btVentA)
                        }
                    }

                if (valid == true) {
                    when (message.toString()) {
                        "ON"  -> { switch.isChecked = true  }
                        "OFF" -> { switch.isChecked = false }
                        "1"   -> { switch.isChecked = true  }
                        "0"   -> { switch.isChecked = false }
                    }
                }
            }

            override fun deliveryComplete(iMqttDeliveryToken: IMqttDeliveryToken) {

            }
        })
        showConnectionState()
    }

    fun schedulerSettigsCB(view: View) {
        val intent = Intent(this, SchedulerActivity::class.java).apply {}
        startActivity(intent)
    }

    fun buttonPressCB(view: View) {
        val prefix = sharedPreferences.getString("pref_prefix", "YardControl")
        var element: String = "?"
        var label: String = "?"
        var message: String = "?"

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
    }
}
