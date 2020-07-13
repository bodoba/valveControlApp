package com.bbzone.valvecontrol

import android.app.TimePickerDialog
import android.content.SharedPreferences
import android.graphics.Color
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.preference.PreferenceManager
import com.bbzone.valvecontrol.helpers.MqttHelper
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended
import org.eclipse.paho.client.mqttv3.MqttMessage

class AddEventActivity : AppCompatActivity() {

    lateinit var sharedPreferences: SharedPreferences
    lateinit var mqttHelper: MqttHelper

    var state = "ON"
    var stateLabel = "Ein"

    var valve = "Valve_A"
    var valveLabel = "Ventil A"
    var valveName = "Ventil A"


    var time = "12:00"
    var hour=12
    var minute=0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_event)
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)

        startMqtt()

        findViewById<RadioButton>(R.id.btValveA).apply {
            valveLabel = sharedPreferences.getString("pref_valve_a", "Ventil A").toString()
            valveName = "Ventil A"
            valve = "Valve_A"
            setText(valveLabel)
            check(true)
        }

        findViewById<RadioButton>(R.id.btValveB).apply {
            setText(sharedPreferences.getString("pref_valve_b", "Ventil B"))
        }

        findViewById<RadioButton>(R.id.btValveC).apply {
            setText(sharedPreferences.getString("pref_valve_c", "Ventil C"))
        }

        findViewById<RadioButton>(R.id.btValveD).apply {
            setText(sharedPreferences.getString("pref_valve_d", "Ventil D"))
        }

        findViewById<RadioButton>(R.id.btOn).apply {
            state = "ON"
            stateLabel = "Ein"
            check(true)
        }

        updateSummary()
    }

    fun selectTimeCB(view: View) {
        val timeDialog =  TimePickerDialog(this@AddEventActivity, TimePickerDialog.OnTimeSetListener { view, h, m ->
            time = String.format("%02d:%02d", h, m)
            hour=h
            minute=m
            updateSummary()
            findViewById<TextView>(R.id.time).apply {
                text=  "${time}h"
            }
        }, hour, minute, true)
        timeDialog.show()
    }

    fun buttonPressCB(view: View) {
        when (view.id) {
            R.id.btValveA -> {
                view.apply {
                    valveLabel = sharedPreferences.getString("pref_valve_a", "Ventil A").toString()
                    valveName = "Ventil A"
                    valve = "Valve_A"
                }
            }

            R.id.btValveB -> {
                view.apply {
                    valveLabel = sharedPreferences.getString("pref_valve_b", "Ventil B").toString()
                    valveName = "Ventil B"
                    valve = "Valve_B"
                }
            }

            R.id.btValveC -> {
                view.apply {
                    valveLabel = sharedPreferences.getString("pref_valve_c", "Ventil C").toString()
                    valve = "Valve_C"
                    valveName = "Ventil C"
                }
            }

            R.id.btValveD -> {
                view.apply {
                    valveLabel = sharedPreferences.getString("pref_valve_d", "Ventil D").toString()
                    valve = "Valve_D"
                    valveName = "Ventil D"
                }
            }

            R.id.btOn -> {
                view.apply {
                    stateLabel = "Ein"
                    state = "ON"
                }
            }

            R.id.btOff -> {
                view.apply {
                    stateLabel = "Aus"
                    state = "OFF"
                }
            }
        }
        updateSummary()
    }

    fun btAddCB (notio view: View ) {
        val topic = "/" + sharedPreferences.getString("pref_prefix", "YardControl") + "/Command/addEvent"
        mqttHelper.mqttAndroidClient.publish(topic, "$valve $state $time".toByteArray(), 1 ,false)
        this.finish()
    }

    private fun updateSummary() {
        var color = Color.parseColor("#FF6666")
        if ( state == "ON" ) {
            color = Color.parseColor("#d9ff66")
        }

        findViewById<TextView>(R.id.event_time).apply {
            setText(time)
            setTextColor(color)
        }

        findViewById<TextView>(R.id.event_valve_name).apply {
            setText(valveName)
            setTextColor(color)
        }

        findViewById<TextView>(R.id.event_valve_label).apply {
            setText(valveLabel)
            setTextColor(color)
        }

        findViewById<TextView>(R.id.event_state).apply {
            setText(stateLabel)
            setTextColor(color)
        }
    }

    private fun startMqtt() {
        mqttHelper = MqttHelper(applicationContext)
        mqttHelper.setCallback(object : MqttCallbackExtended {

            override fun connectComplete(b: Boolean, s: String) {
                Log.i("Add", "MQTT Connection Complete")
            }

            override fun connectionLost(throwable: Throwable) {
                Log.w("Add", "MQTT Connection Lost")
            }

            @Throws(Exception::class)

            override fun messageArrived(topic: String, message: MqttMessage) {
            }

            override fun deliveryComplete(iMqttDeliveryToken: IMqttDeliveryToken) {

            }
        })
    }

}
