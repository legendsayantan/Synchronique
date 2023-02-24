package com.legendsayantan.sync.adapters

import android.app.Dialog
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.ListAdapter
import android.widget.TextView
import com.legendsayantan.sync.R
import com.legendsayantan.sync.models.NotificationData
import java.text.DateFormat
import java.util.*
import kotlin.collections.ArrayList

/**
 * @author legendsayantan
 */
class NotificationListAdapter(context: Context, data: ArrayList<NotificationData>) : BaseAdapter() {

    private val mContext: Context = context
    private val mData: ArrayList<NotificationData> = data

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view: View = convertView ?: LayoutInflater.from(mContext).inflate(R.layout.item_noti, parent, false)

        val item = getItem(position)

        view.findViewById<TextView>(R.id.app).text = item.app_id
        view.findViewById<TextView>(R.id.subtext).text = item.subtext
        view.findViewById<TextView>(R.id.interpunct).visibility = if(item.subtext.isNullOrEmpty()) View.GONE else View.VISIBLE
        view.findViewById<TextView>(R.id.time).text = DateFormat.getTimeInstance().format(Date(item.time))
        view.findViewById<TextView>(R.id.title).text = item.title
        view.findViewById<TextView>(R.id.text).text = item.text

        val replyImage = view.findViewById<ImageView>(R.id.replyImage)
        replyImage.visibility = if (item.canReply) View.VISIBLE else View.GONE
        return view
    }

    override fun getItem(position: Int): NotificationData = mData[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getCount(): Int = mData.size

}
