package com.legendsayantan.sync.views

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.view.Window
import android.widget.TextView
import com.google.android.material.card.MaterialCardView
import com.legendsayantan.sync.R

/**
 * @author legendsayantan
 */
class AskDialog(var activity: Activity,var string: String, var onYes :() -> Unit = {}, var onNo:() -> Unit = {}) {
    private val d = Dialog(activity)
    @SuppressLint("MissingInflatedId")
    fun show(){
        val view = activity.layoutInflater.inflate(R.layout.ask_popup, null);
        view.findViewById<TextView>(R.id.textView).text = string
        view.findViewById<MaterialCardView>(R.id.yesBtn).setOnClickListener {
            d.dismiss()
            onYes()
        }
        view.findViewById<MaterialCardView>(R.id.noBtn).setOnClickListener {
            d.dismiss()
            onNo()
        }
        d.window?.setBackgroundDrawable(ColorDrawable(activity.resources.getColor(R.color.transparent)))
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        d.setContentView(view)
        d.setCancelable(true)
        d.show()
        d.setOnDismissListener {
            onNo()
        }
    }

}