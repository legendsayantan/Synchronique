package com.legendsayantan.sync.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Path
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.ImageView
import com.legendsayantan.sync.R

class ControlViaAccessibility : AccessibilityService() {
    var screenPos = Pair(0F, 0F)
    val windowManager by lazy { getSystemService(Context.WINDOW_SERVICE) as WindowManager }
    val params by lazy {
        WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
    }
    val cursorView by lazy{ImageView(applicationContext)}
    override fun onCreate() {
        super.onCreate()
        //register a broadcast receiver to receive control data from the service
        val broadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                //get the data from the intent
                val action = intent?.getIntExtra("action", -1)
                if(action==-1){
                    val x = intent.getFloatExtra("x", 0F)
                    val y = intent.getFloatExtra("y", 0F)

                    if (x == 0F && y == 0F) {
                        //perform a click at the screen pos
                        performClickAtCoordinate(screenPos.first + 23, screenPos.second + getStatusBarHeight(applicationContext)-1)
                    } else {
                        screenPos = Pair(
                            (screenPos.first - x),
                            screenPos.second - y
                        )
                        try {
                            updatePointer()
                        }catch (e:Exception){}
                    }
                }else{
                    performGlobalAction(action!!)
                }
            }
        }
        onStart = {
            startPointer()
        }
        onStop = {
            stopPointer()
        }
        //register the broadcast receiver
        registerReceiver(
            broadcastReceiver,
            IntentFilter("${applicationContext.packageName}.control")
        )
    }
    private fun startPointer(){
        //start displaying a cursor overlay using window manager
        cursorView.setImageResource(R.drawable.baseline_pan_tool_alt_24)
        cursorView.imageTintList = resources.getColorStateList(R.color.white)
        params.gravity = Gravity.TOP or Gravity.START
        params.x = screenPos.first.toInt()
        params.y = screenPos.second.toInt()
        try {
            windowManager.addView(cursorView, params)
        }catch (e:Exception){
            e.printStackTrace()
            windowManager.updateViewLayout(cursorView, params)
        }
    }
    fun updatePointer(){
        params.x = screenPos.first.toInt()
        params.y = screenPos.second.toInt()
        windowManager.updateViewLayout(cursorView, params)
    }

    private fun stopPointer(){
        //stop displaying the cursor overlay
        windowManager.removeView(cursorView)
    }
    fun performClickAtCoordinate(x: Float, y: Float) {
        val path = Path()
        path.moveTo(x.coerceAtLeast(0F), y.coerceAtLeast(0F))

        val gestureDescription = GestureDescription.Builder().addStroke(
            GestureDescription.StrokeDescription(path, 0, 250)
        ).build()
        dispatchGesture(gestureDescription, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                super.onCompleted(gestureDescription)
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                super.onCancelled(gestureDescription)
            }
        }, null)
    }
    fun getStatusBarHeight(context: Context): Int {
        var statusBarHeight = 0
        val resourceId: Int = context.resources.getIdentifier("status_bar_height", "dimen", "android")

        if (resourceId > 0) {
            statusBarHeight = context.resources.getDimensionPixelSize(resourceId)
        }

        return statusBarHeight
    }



    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {
        onStart = {}
        onStop = {}
    }

    companion object{
        var onStart = {}
        var onStop = {}
    }
}