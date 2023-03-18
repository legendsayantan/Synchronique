package com.legendsayantan.sync.workers

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
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
class PermissionManager() {
    fun ask(activity: Activity,serverConfig: ServerConfig,callback : () -> Unit){
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
                                ask(activity,serverConfig,callback)
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
                            ask(activity,serverConfig, callback)
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
        if(serverConfig.clientConfig.trigger){
            //ask for accessibility service
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
                            ask(activity,serverConfig,callback)
                        }
                    }
                },2000, 1000)
                return
            }
        }
        if(Values(activity.applicationContext).socketOnline){
            //ask for storage write permission
            if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.S){
                if(!Environment.isExternalStorageManager()) {
                    Toast.makeText(activity, "Please grant storage permission", Toast.LENGTH_SHORT)
                        .show()
                    try {
                        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                        intent.addCategory("android.intent.category.DEFAULT")
                        intent.data = Uri.parse(String.format("package:%s", activity.applicationContext.packageName))
                        activity.startActivity(intent)
                    } catch (e: Exception) {
                        val intent = Intent()
                        intent.action = Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION
                        activity.startActivity(intent)
                    }
                    Timer().scheduleAtFixedRate(object : TimerTask() {
                        override fun run() {
                            if (Environment.isExternalStorageManager()) {
                                cancel()
                                ask(activity,serverConfig, callback)
                            }
                        }
                    }, 2000, 1000)
                    return
                }
            }else{
                if(ContextCompat.checkSelfPermission(activity,android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(activity, "Please grant storage permission", Toast.LENGTH_SHORT)
                        .show()
                    activity.requestPermissions(
                        arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE),
                        10
                    )
                    Timer().scheduleAtFixedRate(object : TimerTask() {
                        override fun run() {
                            if (ContextCompat.checkSelfPermission(
                                    activity,
                                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                            ) {
                                cancel()
                                ask(activity,serverConfig, callback)
                            }
                        }
                    }, 2000, 1000)
                    return
                }
            }
        }
        callback()
    }
    fun ask(context: Context,clientConfig: ClientConfig, callback : () -> Unit){
        if(clientConfig.media){
            //ask notification listener permission
            if (!NotificationManagerCompat.getEnabledListenerPackages(context)
                    .contains(context.packageName)
            ) {
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                Toast.makeText(
                    context,
                    "Please enable notification access for ${context.getString(R.string.app_name)}",
                    Toast.LENGTH_SHORT
                ).show()
                Timer().scheduleAtFixedRate(object : TimerTask() {
                    override fun run() {
                        if (NotificationManagerCompat.getEnabledListenerPackages(context)
                                .contains(context.packageName)
                        ) {
                            cancel()
                            ask(context,clientConfig,callback)
                        }
                    }
                },2000, 1000)
                return
            }
        }
        if(clientConfig.audio){
            //nothing required
        }
        if(clientConfig.trigger){
            //nothing required
        }
        if(clientConfig.noti){
            //nothing required
        }
        callback()
    }
}