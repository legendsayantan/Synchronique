package com.legendsayantan.sync.views

import android.app.Activity
import android.app.Dialog
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.view.Window
import android.widget.TextView
import com.google.android.material.card.MaterialCardView
import com.legendsayantan.sync.R

/**
 * @author legendsayantan
 */
class AskDialog(var activity: Activity,var string: String, var onYes :() -> Unit = {}, var onNo:() -> Unit = {},var showBtns:Boolean = true) {
    private val d = Dialog(activity)
    init{
        val view = activity.layoutInflater.inflate(R.layout.popup_ask, null);
        view.findViewById<TextView>(R.id.textView).text = string
        if(!showBtns) view.findViewById<View>(R.id.btns).visibility = View.GONE
        view.findViewById<MaterialCardView>(R.id.yesBtn).setOnClickListener {
            onYes()
            onNo = {}
            d.dismiss()
        }
        view.findViewById<MaterialCardView>(R.id.noBtn).setOnClickListener {
            d.dismiss()
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