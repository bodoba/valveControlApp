package com.bbzone.valvecontrol.helpers

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import androidx.preference.PreferenceManager
import com.bbzone.valvecontrol.R

class ScheduleTableAdapter (private val context: Context, private val dataSource: MutableList<TimerEvent>): BaseAdapter() {
    private val inflater: LayoutInflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater

    var sharedPreferences: SharedPreferences

    init {
        Log.i("ScTA", "Created Adapter")
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
    }

    override fun getCount(): Int {
        return dataSource.size
        Log.d("ScTA", "getCount() =" + dataSource.size)
    }

    override fun getItem(position: Int): Any {
        Log.d("ScTA", "getItem($position)")
        var sortedSource = dataSource.sorted()
        return sortedSource[position]
    }

    override fun getItemId(position: Int): Long {
        Log.d("ScTA", "getItemID($position)")
        var sortedSource = dataSource.sorted()
        return sortedSource[position].index.toLong()
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {

        // Get view for row item
        val rowView = inflater.inflate( R.layout.scheduletable_view, parent, false)
        val time = rowView.findViewById(R.id.event_time) as TextView
        val valve = rowView.findViewById(R.id.event_valve) as TextView
        val state = rowView.findViewById(R.id.event_state) as TextView
        val name = rowView.findViewById(R.id.event_name) as TextView

        val event = getItem(position) as TimerEvent

        name.text = when (event.valve) {
            "Valve_A" -> { sharedPreferences.getString("pref_valve_a", "Ventil A").toString() }
            "Valve_B" -> { sharedPreferences.getString("pref_valve_b", "Ventil B").toString() }
            "Valve_C" -> { sharedPreferences.getString("pref_valve_c", "Ventil C").toString() }
            "Valve_D" -> { sharedPreferences.getString("pref_valve_d", "Ventil D").toString() }

            else -> { "Unbekannt" }
        }

        valve.text = when (event.valve) {
            "Valve_A" -> { "Ventil A" }
            "Valve_B" -> { "Ventil B" }
            "Valve_C" -> { "Ventil C" }
            "Valve_D" -> { "Ventil D" }

            else -> { "Unbekannt" }
        }
        time.text = event.time

        state.text = when ( event.state ) {
            "OFF" -> { "Aus" }
            "ON"  -> { "Ein" }

            else -> { "???" }
        }

        var color = Color.parseColor("#FF6666")

        if ( event.state == "ON" ) {
            color = Color.parseColor("#d9ff66")
        }

        time.setTextColor(color)
        state.setTextColor(color)
        name.setTextColor(color)
        valve.setTextColor(color)

        Log.d("ScTA", "getView($position): " + event.summary )

        return rowView
    }
}