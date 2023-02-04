package com.legendsayantan.sync.views

import android.app.Activity
import android.app.Dialog
import android.graphics.drawable.ColorDrawable
import android.view.Window
import android.widget.GridView
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.card.MaterialCardView
import com.legendsayantan.sync.R
import com.legendsayantan.sync.adapters.DialogGridAdapter

/**
 * @author legendsayantan
 */
class AppDialog(
    private var activity: Activity,
    var header:String,
    var name:String,
    var hash:String,
    var btn: String,
    var params: ArrayList<Int>,
    var onAction: (data:ArrayList<Int>,dialog:AppDialog) -> Unit = { ints: ArrayList<Int>, appDialog: AppDialog -> },
    var onDismiss: () -> Unit = {}) {
    val d = Dialog(activity)
    fun show(){
        var view = activity.layoutInflater.inflate(R.layout.device_link_popup, null);
        view.findViewById<TextView>(R.id.name).text = name
        view.findViewById<TextView>(R.id.hash).text =
            hash.substring(0, hash.length / 3) + " " + hash.substring(
                hash.length / 3,
                hash.length * 2 / 3
            ) + " " + hash.substring(hash.length * 2 / 3, hash.length)
        view.findViewById<TextView>(R.id.headerText).text = header
        view.findViewById<TextView>(R.id.btnText).text = btn
        val gridView = view.findViewById<GridView>(R.id.grid)
        gridView.adapter = DialogGridAdapter(activity,params,params.isNotEmpty())
        gridView.numColumns = if(params.isEmpty()||params.size>3) 3 else params.size
        view.findViewById<MaterialCardView>(R.id.linkBtn).setOnClickListener {
            if(params.isNotEmpty())onAction(params,this)
            else Toast.makeText(activity,"Select at least one parameter to sync.",Toast.LENGTH_SHORT).show()
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