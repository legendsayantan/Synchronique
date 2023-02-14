package com.legendsayantan.sync.workers

import android.annotation.SuppressLint
import android.app.Activity
import android.transition.TransitionManager
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Switch
import androidx.core.view.children
import com.google.android.material.card.MaterialCardView

/**
 * @author legendsayantan
 */
class CardAnimator(context: Activity) {
    companion object{
        @SuppressLint("UseSwitchCompatOrMaterialCode")
        fun initTogglableCards(cardList: List<MaterialCardView>){
            for (card in cardList){
                if(card.visibility == View.GONE)continue
                val switch = ((card.getChildAt(0) as LinearLayout).getChildAt(0) as LinearLayout).getChildAt(1) as Switch
                val settings = (card.getChildAt(0) as LinearLayout).getChildAt(1)
                switch.setOnClickListener{
                    TransitionManager.beginDelayedTransition(card.parent as ViewGroup)
                    settings.visibility = if(switch.isChecked) View.VISIBLE else View.GONE
                }
                settings.visibility = if(switch.isChecked) View.VISIBLE else View.GONE
            }
        }
        fun staggerList(layout: LinearLayout){
            for(card in layout.children){
                card.alpha = 0f
                card.animate().alpha(1f).setDuration(500).setStartDelay((200*layout.indexOfChild(card)).toLong()).start()
            }
        }
    }

}