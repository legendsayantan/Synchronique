package com.legendsayantan.sync.views

import android.app.Activity
import android.app.Dialog
import android.graphics.drawable.ColorDrawable
import android.view.Window
import android.widget.TextView
import com.google.android.material.card.MaterialCardView
import com.legendsayantan.sync.R

/**
 * @author legendsayantan
 */
class ConnectionDialog(
    var activity: Activity,
    var header:String,
    var name:String,
    var hash:String,
    var btn: String,
    var onAction: (dialog:ConnectionDialog) -> Unit = { },
    var onDismiss: () -> Unit = {}) {
    val d = Dialog(activity)
    fun show(){
        var view = activity.layoutInflater.inflate(R.layout.popup_device_link, null);
        view.findViewById<TextView>(R.id.name).text = name
        view.findViewById<TextView>(R.id.hash).text =
            hash.substring(0, hash.length / 3) + " " + hash.substring(
                hash.length / 3,
                hash.length * 2 / 3
            ) + " " + hash.substring(hash.length * 2 / 3, hash.length)
        view.findViewById<TextView>(R.id.headerText).text = header
        view.findViewById<TextView>(R.id.btnText).text = btn
        view.findViewById<MaterialCardView>(R.id.linkBtn).setOnClickListener {
            onAction(this)
            it.setOnClickListener {  }
        }
        d.window?.setBackgroundDrawable(ColorDrawable(activity.resources.getColor(R.color.transparent)))
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        d.setContentView(view)
        d.setCancelable(true)
        d.show()
        d.setOnDismissListener {
            onDismiss()
        }
    }
    fun hide(){
        if(d.isShowing)d.dismiss()
    }
}