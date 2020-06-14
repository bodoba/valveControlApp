package com.bbzone.valvecontrol.helpers

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.preference.PreferenceManager

import org.eclipse.paho.client.mqttv3.MqttException
import org.eclipse.paho.client.mqttv3.IMqttToken
import org.eclipse.paho.client.mqttv3.IMqttActionListener
import org.eclipse.paho.client.mqttv3.DisconnectedBufferOptions
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.android.service.MqttAndroidClient
import java.util.*

val uniqueID: String = UUID.randomUUID().toString()

class MqttHelper(context: Context) {
    var mqttAndroidClient: MqttAndroidClient

    var sharedPreferences: SharedPreferences

    internal var serverUri: String = ""
    internal var password: String = ""
    internal var username: String = ""

    internal var currentTopic = "none"

    init {
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

        serverUri = sharedPreferences.getString("pref_broker", "localhost").toString()
        password = sharedPreferences.getString("pref_password", "").toString()
        username = sharedPreferences.getString("pref_username", "").toString()

        mqttAndroidClient = MqttAndroidClient(context, serverUri, uniqueID)

        mqttAndroidClient.setCallback(object : MqttCallbackExtended {

            override fun connectComplete(b: Boolean, s: String) { }

            override fun connectionLost(throwable: Throwable) { }

            @Throws(Exception::class)

            override fun messageArrived(topic: String, mqttMessage: MqttMessage) {
                Log.w("Mqtt", mqttMessage.toString())
            }

            override fun deliveryComplete(iMqttDeliveryToken: IMqttDeliveryToken) { }
        })
        connect()
    }

    fun getConnectionState () : Boolean {
        return mqttAndroidClient.isConnected()
    }

    fun updateTopic() {
        var newTopic = "/" + sharedPreferences.getString("pref_prefix", "YardControl").toString() + "/#"

        if ( newTopic != currentTopic ) {
            Log.i("Mqtt", "Changing topic from '$currentTopic' to '$newTopic'")
            mqttAndroidClient.unsubscribe(currentTopic)
            subscribeToTopic(newTopic)
        }
    }

    fun disconnect () {
        try {
            val disconToken = mqttAndroidClient.disconnect()
            disconToken.actionCallback = object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken) {
                    Log.w("Mqtt", "Disconnected")
                }
                override fun onFailure( asyncActionToken: IMqttToken, exception: Throwable) {
                    Log.w("Mqtt", "Failed to disconnect")
                }
            }
        } catch (e: MqttException) {
            // Give Callback on error here
        }
    }

    fun setCallback(callback: MqttCallbackExtended) {
        mqttAndroidClient.setCallback(callback)
    }

    private fun connect() {
        val mqttConnectOptions = MqttConnectOptions()
        mqttConnectOptions.isAutomaticReconnect = true
        mqttConnectOptions.connectionTimeout = 0
        mqttConnectOptions.isCleanSession = false

        if ( password.length > 0 && username.length > 0 ) {
            mqttConnectOptions.userName = username
            mqttConnectOptions.password = password.toCharArray()
        } else {
            mqttConnectOptions.userName = null
            mqttConnectOptions.password = null
            Log.w("Mqtt", "Empty username/password")
        }

        try {

            mqttAndroidClient.connect(mqttConnectOptions, null, object : IMqttActionListener {

                override fun onSuccess(asyncActionToken: IMqttToken) {
                    Log.i("Mqtt", "Connected to: $serverUri")

                    val disconnectedBufferOptions = DisconnectedBufferOptions()
                    disconnectedBufferOptions.isBufferEnabled = true
                    disconnectedBufferOptions.bufferSize = 100
                    disconnectedBufferOptions.isPersistBuffer = false
                    disconnectedBufferOptions.isDeleteOldestMessages = false
                    mqttAndroidClient.setBufferOpts(disconnectedBufferOptions)

                    subscribeToTopic("/" + sharedPreferences.getString("pref_prefix", "YardControl").toString() + "/#")
                }

                override fun onFailure(asyncActionToken: IMqttToken, exception: Throwable) {
                    Log.w("Mqtt", "Failed to connect to: $serverUri$exception")
                }
            })

        } catch (ex: MqttException) {
            ex.printStackTrace()
        }
    }

    private fun subscribeToTopic(topic: String) {
        try {
            mqttAndroidClient.subscribe( topic , 0, null, object : IMqttActionListener {

                override fun onSuccess(asyncActionToken: IMqttToken) {
                    currentTopic = topic
                    Log.i("Mqtt", "Subscribed to '$currentTopic'")
                }

                override fun onFailure(asyncActionToken: IMqttToken, exception: Throwable) {
                    Log.w("Mqtt", "Subscribed fail!")
                }
            })

        } catch (ex: MqttException) {
            System.err.println("Exception whilst subscribing")
            ex.printStackTrace()
        }
    }
}