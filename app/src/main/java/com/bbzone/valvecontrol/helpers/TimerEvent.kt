package com.bbzone.valvecontrol.helpers

// Keep data of a timer event
data class TimerEvent  ( val event: String ) : Comparable<TimerEvent> {
    var valve: String = "Valve_X"
    private var hour: Int = 0
    private var minute: Int = 0
    var state: String = "ON"
    var index: Int = 0
    var summary: String = ""
    var time: String = "00:00"
    var selected: Boolean = false

    init {
        // parse ScheduleTable/Entry message into it's parts
        val parts = event.split(" ")
        valve = parts[0]
        state = parts[1]
        hour = parts[2].substring(0, 2).toInt()
        minute = parts[2].substring(3, 5).toInt()
        time = parts[2]
    }
    constructor ( valve: String, hour: Int, minute:  Int, state: String ): this ( "$valve $state $hour:$minute") {}

    init {
        // index is used for sorting and identifying an event (only one at a time is allowed)
        index = minute + (hour * 100)

        // summary for displaying in GUI
        summary = "$hour:$minute\t$valve\t$state"
    }

    override fun compareTo ( other: TimerEvent ) = when {
        index > other.index -> 1
        index < other.index -> -1
        else -> 0
    }

    fun select( state: Boolean ) {
        selected = state
    }
}
