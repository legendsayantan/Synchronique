package com.legendsayantan.sync.views

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.view.Window
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import com.legendsayantan.sync.views.chatview.model.ChatMessage
import com.legendsayantan.sync.views.chatview.ChatView
import com.legendsayantan.sync.R
import com.legendsayantan.sync.models.NotificationData
import org.jsoup.Jsoup
import java.util.*

/**
 * @author legendsayantan
 */
@SuppressLint("MissingInflatedId")
class ChatDialog(var activity: Activity, var data: ArrayList<NotificationData>,var index: Int) {
    private val d = Dialog(activity)
    private var chatView: ChatView
    var replyListener: (String, String) -> Unit = { text: String, key: String -> }
    var appIconUrl = ""
    init {
        val view = activity.layoutInflater.inflate(R.layout.popup_reply, null);
        view.findViewById<TextView>(R.id.name).text = data[index].title
        chatView = view.findViewById(R.id.chatView)
        //dark mode

        chatView.setTextColor(activity.resources.getColor(R.color.accent1_400))
        chatView.findViewById<ImageView>(com.fasilthottathil.simplechatview.R.id.btnSend).visibility = ImageView.GONE
        data.forEach {
            newData(it)
        }
        val url = "https://play.google.com/store/apps/details?id=${data[index].app_id}"
        Thread{
            try {
                val doc = Jsoup.connect(url).get()
                val icon = doc.select("img[alt=${data[index].app_id}]")
                appIconUrl = icon.attr("src")
            }catch (e:Exception){
                e.printStackTrace()
            }
        }.start()
        val input = view.findViewById<EditText>(R.id.text)
        val btn = view.findViewById<ImageView>(R.id.sendBtn)
        btn.setOnClickListener {
            if (input.text.toString().isNotEmpty()) {
                replyListener(input.text.toString(), data[index].key!!)
                input.text.clear()
            }
        }
        chatView.setTextColor(activity.resources.getColor(R.color.accent1_400))
        d.window?.setBackgroundDrawable(ColorDrawable(activity.resources.getColor(R.color.transparent)))
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        d.setContentView(view)
        d.setCancelable(true)
        d.show()
        d.setOnDismissListener {
            replyListener = { text: String, key: String -> }
        }
    }

    private fun addToChat(element: NotificationData, index: Int, is_from_me: Boolean) {
        chatView.addMessage(
            ChatMessage(
                index.toString(),
                element.text.toString(),
                "",
                appIconUrl,
                is_from_me,
                element.time,
                ChatView.TYPE_TEXT
            )
        )
    }

    fun newData(element: NotificationData) {
        if(element.title?.lowercase().equals("you") && element.app_id==data[index].app_id && element.key==data[index].key){
            addToChat(element,index,true)
        }else if (element.title == data[index].title && element.canReply && element.app_id == data[index].app_id) {
            addToChat(element, index, false)
        }
    }

}