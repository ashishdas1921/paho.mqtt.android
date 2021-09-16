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
import android.app.Service;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
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
class AlarmPingSender implements MqttPingSender {
    // Identifier for Intents, log messages, etc..
    private static final String TAG = "AlarmPingSender";

    // TODO: Add log.
    private ClientComms comms;
    private final MqttService service;
    private volatile boolean hasStarted = false;
    private HandlerThread mThread;
    private Handler mHandler;

    public AlarmPingSender(MqttService service) {
        if (service == null) {
            throw new IllegalArgumentException(
                    "Neither service nor client can be null.");
        }
        this.service = service;
    }

    @Override
    public void init(ClientComms comms) {
        this.comms = comms;

        quitSafelyThread();
        mThread = new HandlerThread(TAG);
        mThread.start();
        mHandler = new Handler(mThread.getLooper());
    }

    @SuppressLint("UnspecifiedImmutableFlag")
    @Override
    public void start() {
        String action = MqttServiceConstants.PING_SENDER
                + comms.getClient().getClientId();
        Log.d(TAG, "Register scheduledRunnable to MqttService" + action);
        schedule(comms.getKeepAlive());
        hasStarted = true;
    }

    @Override
    public void stop() {
        Log.d(TAG, "Unregister scheduledRunnable to MqttService" + comms.getClient().getClientId());
        if (hasStarted) {
            quitSafelyThread();
            hasStarted = false;
        }
    }

    @Override
    public void schedule(long delayInMilliseconds) {
        Log.e(TAG, "schedule delayInMilliseconds = " + delayInMilliseconds);

        wakeupThread();
        mHandler.postDelayed(mRunnable, delayInMilliseconds);
    }

    private void quitSafelyThread() {
        if (mThread != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                mThread.quitSafely();
            } else {
                mThread.quit();
            }
        }
    }

    private void wakeupThread() {
        if (mThread.getState() == Thread.State.TIMED_WAITING) {
            mThread.getLooper().getThread().interrupt(); // wakeup the thread if it is in sleep.
            Log.e(TAG, "Interrupt: handlerThread Id = " + mThread.getThreadId());
        }
    }

    private final Runnable mRunnable = new Runnable() {
        @Override
        public void run() {
            sendPing();
        }
    };

    private synchronized void sendPing() {
        Log.d(TAG, "Sending Ping at:" + System.currentTimeMillis());

        // Assign new callback to token to execute code after PingResq
        // arrives. Get another wakelock even receiver already has one,
        // release it until ping response returns.
        comms.checkForActivity(new IMqttActionListener() {
            @Override
            public void onSuccess(IMqttToken asyncActionToken) {
                Log.d(TAG, "Success. " + System.currentTimeMillis());
            }

            @Override
            public void onFailure(IMqttToken asyncActionToken,
                                  Throwable exception) {
                Log.d(TAG, "Failure. " + System.currentTimeMillis());
            }
        });
    }
}
