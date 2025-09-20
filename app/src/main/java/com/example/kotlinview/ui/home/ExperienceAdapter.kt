package com.example.kotlinview.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.kotlinview.R

class ExperienceAdapter(
    private val onClick: (Experience) -> Unit
) : ListAdapter<Experience, ExperienceAdapter.VH>(Diff) {

    object Diff : DiffUtil.ItemCallback<Experience>() {
        override fun areItemsTheSame(oldItem: Experience, newItem: Experience) =
            oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Experience, newItem: Experience) =
            oldItem == newItem
    }

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.tv_title)
        val rating: TextView = view.findViewById(R.id.tv_rating)
        val host: TextView = view.findViewById(R.id.tv_host)
        val verified: ImageView = view.findViewById(R.id.iv_verified)
        val location: TextView = view.findViewById(R.id.tv_location)
        val learn: TextView = view.findViewById(R.id.tv_learn)
        val teach: TextView = view.findViewById(R.id.tv_teach)
        val reviews: TextView = view.findViewById(R.id.tv_reviews)
        val duration: TextView = view.findViewById(R.id.tv_duration_chip)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_experience, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        holder.title.text = item.title
        holder.rating.text = "%.1f".format(item.rating)
        holder.host.text = item.hostName
        holder.verified.visibility = if (item.verified) View.VISIBLE else View.GONE
        holder.location.text = item.location
        holder.learn.text = "Learn: " + item.learningSkills.joinToString(", ")
        holder.teach.text = "Teach: " + item.teachingSkills.joinToString(", ")
        holder.reviews.text = "(${item.reviewCount} reviews)"
        holder.duration.text = item.duration

        holder.itemView.setOnClickListener { onClick(item) }
    }
}