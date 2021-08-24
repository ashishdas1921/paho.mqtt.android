/*******************************************************************************
 * Copyright (c) 2014 IBM Corp.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Eclipse Distribution License v1.0 which accompany this distribution. 
 *
 * The Eclipse Public License is available at 
 *    http://www.eclipse.org/legal/epl-v10.html
 * and the Eclipse Distribution License is available at 
 *   http://www.eclipse.org/org/documents/edl-v10.php.
 */
package org.eclipse.paho.android.service;

import android.annotation.SuppressLint;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttPingSender;
import org.eclipse.paho.client.mqttv3.internal.ClientComms;

/**
 * Default ping sender implementation on Android. It is based on AlarmManager.
 *
 * <p>This class implements the {@link MqttPingSender} pinger interface
 * allowing applications to send ping packet to server every keep alive interval.
 * </p>
 *
 * @see MqttPingSender
 */
class AlarmPingSender implements MqttPingSender
{
    // Identifier for Intents, log messages, etc..
    private static final String TAG = "AlarmPingSender";

    // TODO: Add log.
    private ClientComms comms;
    private MqttService service;
    private AlarmPingSender that;
    private volatile boolean hasStarted = false;
    private Handler mHandler = new Handler(Looper.getMainLooper());

    public AlarmPingSender(MqttService service)
    {
        if (service == null)
        {
            throw new IllegalArgumentException(
                    "Neither service nor client can be null.");
        }
        this.service = service;
        that = this;
    }

    @Override
    public void init(ClientComms comms)
    {
        this.comms = comms;
    }

    @SuppressLint("UnspecifiedImmutableFlag")
    @Override
    public void start()
    {
        String action = MqttServiceConstants.PING_SENDER
                + comms.getClient().getClientId();
        Log.d(TAG, "Register scheduledRunnable to MqttService" + action);
        schedule(comms.getKeepAlive());
        hasStarted = true;
    }

    @Override
    public void stop()
    {

        Log.d(TAG, "Unregister scheduledRunnable to MqttService" + comms.getClient().getClientId());
        if (hasStarted)
        {
            if (mHandler != null)
            {
                mHandler.removeCallbacks(scheduledRunnable);
            }
            hasStarted = false;
        }
    }

    private Runnable scheduledRunnable = new Runnable()
    {
        @Override
        public void run()
        {
            // Execute tasks on main thread
            Log.d("Handlers", "Called on main thread");
            // Repeat this task again keepAlive interval
            sendPing();

            schedule(comms.getKeepAlive());
        }
    };

    @Override
    public void schedule(long delayInMilliseconds)
    {
        mHandler.postDelayed(scheduledRunnable, delayInMilliseconds);
    }

    private void sendPing()
    {
        Log.d(TAG, "Sending Ping at:" + System.currentTimeMillis());

        // Assign new callback to token to execute code after PingResq
        // arrives. Get another wakelock even receiver already has one,
        // release it until ping response returns.
        IMqttToken token = comms.checkForActivity(new IMqttActionListener()
        {
            @Override
            public void onSuccess(IMqttToken asyncActionToken)
            {
                Log.d(TAG, "Success. Release :"
                        + System.currentTimeMillis());
            }

            @Override
            public void onFailure(IMqttToken asyncActionToken,
                                  Throwable exception)
            {
                Log.d(TAG, "Failure. :"
                        + System.currentTimeMillis());
            }
        });
    }
}
