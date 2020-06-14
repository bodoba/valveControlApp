package com.bbzone.valvecontrol

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import com.bbzone.valvecontrol.helpers.MqttHelper
import com.bbzone.valvecontrol.helpers.ScheduleTableAdapter
import com.bbzone.valvecontrol.helpers.TimerEvent
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended
import org.eclipse.paho.client.mqttv3.MqttMessage
import java.util.*
import kotlin.concurrent.timerTask

class SchedulerActivity : AppCompatActivity() {

    lateinit var sharedPreferences: SharedPreferences
    lateinit var mqttHelper: MqttHelper
    private lateinit var scheduleTableView: ListView

    var scheduleTable = mutableListOf<TimerEvent>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scheduler)

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)

        startMqtt()

        scheduleTableView = findViewById(R.id.scheduleList)
        val adapter = ScheduleTableAdapter(this, scheduleTable)
        scheduleTableView.adapter = adapter


        val context = this
        scheduleTableView.setOnItemClickListener { _, itemView, position, id ->
            // toggle selection
            if ( eventToggleSelection(id.toInt()) ) {
                itemView.setBackgroundColor(ContextCompat.getColor( context, R.color.not_so_dark_grey))
            } else {
                itemView.setBackgroundColor(ContextCompat.getColor( context, R.color.dark_grey))
            }
        }
    }

    fun eventToggleSelection( index: Int ): Boolean {
        var retval = false
        scheduleTable.forEach {
            Log.d("Schedule", "Event " + it.index )
            if ( it.index == index ) {
                retval = !it.selected
                it.select(retval)
            }
        }
        return( retval )
    }

    fun refreshButtonCB(view: View) {
        val prefix = sharedPreferences.getString("pref_prefix", "YardControl")
        scheduleTable.clear()
        mqttHelper.mqttAndroidClient.publish("/$prefix/Command/dumpScheduleTable", "INFO".toByteArray(), 1 ,false)
        Toast.makeText(applicationContext, "Reload Event List", Toast.LENGTH_SHORT).show()
    }

    fun removeButtonCB(view: View) {
        // count selected events
        var numSelected = 0
        var timesSelected = ""
        scheduleTable.sorted().forEach {
            if (it.selected) {
                numSelected++
                if ( numSelected > 1 ) {
                    timesSelected += ", " + it.time +"h"
                } else {
                    timesSelected = it.time + "h"
                }
            }
        }

        if ( numSelected > 0 ) {
            val mAlertDialog = AlertDialog.Builder(this@SchedulerActivity)
            //mAlertDialog.setIcon(R.mipmap.ic_launcher_round) //set alertdialog icon
            mAlertDialog.setTitle("Schaltzeitpunkte löschen") //set alertdialog title
            if ( numSelected == 1 ) {
                mAlertDialog.setMessage("Willst Du wirklich den Schaltzeitpunkt um ${timesSelected} löschen?")
            } else {
                mAlertDialog.setMessage("Willst Du wirklich $numSelected Schaltzeitpunkte ($timesSelected) löschen?")
            }

            mAlertDialog.setPositiveButton("Ja") { dialog, id ->
                scheduleTable.forEach {
                    if (it.selected) {
                        Log.i("Scheduler", "Remove Event <${it.event}>")
                        // Request status update from valveControl server
                        mqttHelper.mqttAndroidClient.publish(
                            "/" + sharedPreferences.getString("pref_prefix", "YardControl").toString() + "/Command/removeEvent",
                            it.event.toByteArray(),
                            1,
                            false
                        )
                        it.select(false)
                    }
                }
                scheduleTable.clear()
                // Request status update from valveControl server
                mqttHelper.mqttAndroidClient.publish(
                    "/" + sharedPreferences.getString("pref_prefix", "YardControl").toString() + "/Command/dumpScheduleTable",
                    "INFO".toByteArray(),
                    1,
                    false
                )
                scheduleTableView.invalidateViews()
            }

            mAlertDialog.setNegativeButton("Nein") { dialog, id ->
                scheduleTable.forEach {
                    if (it.selected) {
                        it.select(false)
                    }
                }
                scheduleTableView.invalidateViews()
            }

            mAlertDialog.show()
        } else {
            val mAlertDialog = AlertDialog.Builder(this@SchedulerActivity)
            //mAlertDialog.setIcon(R.mipmap.ic_launcher_round) //set alertdialog icon
            mAlertDialog.setTitle("Schaltzeitpunkte löschen") //set alertdialog title
            mAlertDialog.setMessage("Bitte wähle die Schaltzeitpunkte die gelöscht werden sollen aus.") //set alertdialog message

            mAlertDialog.setNegativeButton("OK") { dialog, id ->
            }

            mAlertDialog.show()
        }
    }

    override fun onResume() {
        super.onResume()
        // not sure why, but MQTT needs some time to settle. So wait 0,5s before requesting the refresh
        Timer().schedule(timerTask {
            val prefix = sharedPreferences.getString("pref_prefix", "YardControl")
            scheduleTable.clear()
            mqttHelper.mqttAndroidClient.publish("/$prefix/Command/dumpScheduleTable", "INFO".toByteArray(), 1 ,false)
        }, 500)
    }

    fun addButtonCB(view: View) {
        val intent = Intent(this, AddEventActivity::class.java).apply {}
        startActivity(intent)
    }

    private fun startMqtt() {
        mqttHelper = MqttHelper(applicationContext)
        mqttHelper.setCallback(object : MqttCallbackExtended {

            override fun connectComplete(b: Boolean, s: String) {
                Log.i("Scheduler", "MQTT Connection Complete")

                // Request status update from valveControl server
                mqttHelper.mqttAndroidClient.publish(
                    "/" + sharedPreferences.getString("pref_prefix", "YardControl").toString() + "/Command/dumpScheduleTable",
                    "INFO".toByteArray(),
                    1,
                    false
                )
            }

            override fun connectionLost(throwable: Throwable) {
                Log.w("Scheduler", "MQTT Connection Lost")
            }

            @Throws(Exception::class)

            override fun messageArrived(topic: String, message: MqttMessage) {
                val prefix = sharedPreferences.getString("pref_prefix", "YardControl")
                //  Add event to schedule table unless it already exists
                if ( topic == "/$prefix/ScheduleTable/Entry" ) {
                    val event = TimerEvent(message.toString())
                    if ( ! scheduleTable.contains(event)) {
                        scheduleTable.add(event)
                        scheduleTableView.invalidateViews()
                    }
                }
            }

            override fun deliveryComplete(iMqttDeliveryToken: IMqttDeliveryToken) {

            }
        })
    }
}