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
        // Evitamos 'id' porque tu modelo UI no lo tiene (o no es estable).
        override fun areItemsTheSame(oldItem: Experience, newItem: Experience): Boolean =
            oldItem == newItem

        override fun areContentsTheSame(oldItem: Experience, newItem: Experience): Boolean =
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

        // Título y rating
        holder.title.text = item.title
        holder.rating.text = "%.1f".format(item.rating)

        // NO mostrar nombre ni check
        holder.host.visibility = View.GONE
        holder.verified.visibility = View.GONE

        // Ubicación (usa tal cual viene del modelo)
        holder.location.text = item.department

        // Learn / Teach (listas no nulas en tu modelo)
        val learnText = if (item.learnSkills.isEmpty()) "—"
        else item.learnSkills.joinToString(", ")
        holder.learn.text = "Learn: $learnText"

        val teachText = if (item.teachSkills.isEmpty()) "—"
        else item.teachSkills.joinToString(", ")
        holder.teach.text = "Teach: $teachText"

        // (n reviews) y duración (tal cual en tu modelo)
        holder.reviews.text = "(${item.reviewCount} reviews)"
        holder.duration.text = "${item.duration} days"

        holder.itemView.setOnClickListener { onClick(item) }
    }
}
