package com.legendsayantan.sync.adapters

import android.content.Context
import android.content.res.ColorStateList
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import androidx.core.content.ContextCompat
import com.legendsayantan.sync.R


/**
 * @author legendsayantan
 */
class DialogGridAdapter(
    private val context: Context,
    private var highlights: ArrayList<Int>,
    private val showmode: Boolean
) : BaseAdapter() {
    private var images = arrayListOf<Int>(
        R.drawable.baseline_music_note_24,
        R.drawable.baseline_camera_24,
        R.drawable.baseline_notifications_24
    )
    private val layoutInflater = LayoutInflater.from(context)

    override fun getCount() = if(showmode)highlights.size else images.size

    override fun getItem(position: Int) = images[position]

    override fun getItemId(position: Int) = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View? {
        val view = convertView ?: layoutInflater.inflate(
            com.legendsayantan.sync.R.layout.grid_item,
            parent,
            false
        )
        val imageView = view.findViewById<ImageView>(com.legendsayantan.sync.R.id.imageView)
        val value = TypedValue()
        context.theme.resolveAttribute(android.R.attr.colorPrimary, value, true)
        if (showmode) {
            if (highlights.contains(position)) {
                imageView.imageTintList = ColorStateList.valueOf(value.data)
            }else{
                view.visibility = View.GONE
            }
        } else {
            imageView.imageTintList = if (highlights.contains(position)) {
                ColorStateList.valueOf(value.data)
            } else {
                ColorStateList.valueOf(ContextCompat.getColor(context, android.R.color.white))
            }
            view.setOnClickListener {
                if (highlights.contains(position)) {
                    highlights.remove(position)
                } else {
                    highlights.add(position)
                }
                notifyDataSetChanged()
            }
        }
        imageView.setImageResource(images[position])
        return view
    }
}
