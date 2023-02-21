package com.legendsayantan.sync.workers

import android.app.Activity
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.legendsayantan.sync.MainActivity
import com.legendsayantan.sync.R
import com.legendsayantan.sync.models.ClientConfig
import com.legendsayantan.sync.models.ServerConfig
import java.util.*

/**
 * @author legendsayantan
 */
class PermissionManager(var activity: Activity) {
    fun ask(serverConfig: ServerConfig,callback : () -> Unit){
        if(serverConfig.clientConfig.media){
            if (!serverConfig.mediaClientOnly){
                //ask notification listener permission
                if (!NotificationManagerCompat.getEnabledListenerPackages(activity)
                        .contains(activity.packageName)
                ) {
                    activity.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    Toast.makeText(
                        activity,
                        "Please enable notification access for ${activity.getString(R.string.app_name)}",
                        Toast.LENGTH_SHORT
                    ).show()
                    Timer().scheduleAtFixedRate(object : TimerTask() {
                        override fun run() {
                            if (NotificationManagerCompat.getEnabledListenerPackages(activity)
                                    .contains(activity.packageName)
                            ) {
                                cancel()
                                ask(serverConfig,callback)
                            }
                        }
                    },2000, 1000)
                    return
                }
            }
        }
        if(serverConfig.clientConfig.audio){
            //ask for audio permission
            if(ContextCompat.checkSelfPermission(activity,android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(activity, "Please grant audio permission", Toast.LENGTH_SHORT)
                    .show()
                activity.requestPermissions(
                    arrayOf(android.Manifest.permission.RECORD_AUDIO),
                    1
                )
                Timer().scheduleAtFixedRate(object : TimerTask() {
                    override fun run() {
                        if (ContextCompat.checkSelfPermission(
                                activity,
                                android.Manifest.permission.RECORD_AUDIO
                            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                        ) {
                            cancel()
                            ask(serverConfig, callback)
                        }
                    }
                }, 2000, 1000)
                return
            }
            if (!serverConfig.audioMic){
                val captureIntent = MainActivity.mediaProjectionManager.createScreenCaptureIntent()
                activity.startActivityForResult(captureIntent,
                    Values.REQUEST_CODE_CAPTURE_PERM
                )
            }
        }
        if(serverConfig.clientConfig.camera){
            //ask for camera permission
        }
        if(serverConfig.clientConfig.noti){
            //ask for notification permission
            if (!NotificationManagerCompat.getEnabledListenerPackages(activity)
                    .contains(activity.packageName)
            ) {
                activity.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                Toast.makeText(
                    activity,
                    "Please enable notification access for ${activity.getString(R.string.app_name)}",
                    Toast.LENGTH_SHORT
                ).show()
                Timer().scheduleAtFixedRate(object : TimerTask() {
                    override fun run() {
                        if (NotificationManagerCompat.getEnabledListenerPackages(activity)
                                .contains(activity.packageName)
                        ) {
                            cancel()
                            ask(serverConfig,callback)
                        }
                    }
                },2000, 1000)
                return
            }
        }
        callback()
    }
    fun ask(clientConfig: ClientConfig, callback : () -> Unit){
        if(clientConfig.media){
            //ask notification listener permission
            if (!NotificationManagerCompat.getEnabledListenerPackages(activity)
                    .contains(activity.packageName)
            ) {
                activity.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                Toast.makeText(
                    activity,
                    "Please enable notification access for ${activity.getString(R.string.app_name)}",
                    Toast.LENGTH_SHORT
                ).show()
                Timer().scheduleAtFixedRate(object : TimerTask() {
                    override fun run() {
                        if (NotificationManagerCompat.getEnabledListenerPackages(activity)
                                .contains(activity.packageName)
                        ) {
                            cancel()
                            ask(clientConfig,callback)
                        }
                    }
                },2000, 1000)
                return
            }
        }
        if(clientConfig.audio){
            //nothing required
        }
        if(clientConfig.camera){
            //nothing required
        }
        if(clientConfig.noti){
            //nothing required
        }
        callback()
    }
}