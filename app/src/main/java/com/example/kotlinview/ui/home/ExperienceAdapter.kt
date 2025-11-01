package com.example.kotlinview.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.kotlinview.R

class ExperienceAdapter(
    private val onClick: (Experience) -> Unit
) : ListAdapter<Experience, ExperienceAdapter.VH>(Diff) {

    object Diff : DiffUtil.ItemCallback<Experience>() {
        override fun areItemsTheSame(oldItem: Experience, newItem: Experience): Boolean {
            // Si no tienes id en el UI model, usa combinación estable:
            return oldItem.title == newItem.title &&
                    oldItem.department == newItem.department
        }
        override fun areContentsTheSame(oldItem: Experience, newItem: Experience) = oldItem == newItem
    }

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        // Usa el id REAL del ImageView en tu item_experience.xml
        val photo: ImageView = view.findViewById(R.id.iv_photo)

        val title: TextView = view.findViewById(R.id.tv_title)
        val rating: TextView = view.findViewById(R.id.tv_rating)
        val location: TextView = view.findViewById(R.id.tv_location)
        val learn: TextView = view.findViewById(R.id.tv_learn)
        val teach: TextView = view.findViewById(R.id.tv_teach)
        val reviews: TextView = view.findViewById(R.id.tv_reviews)
        val duration: TextView = view.findViewById(R.id.tv_duration_chip)
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_experience, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)

        // Imagen (Coil). Si no tienes placeholder, usa el que creamos: ic_image_placeholder
        holder.photo.load(item.imageUrl.ifBlank { null }) {
            crossfade(true)
            placeholder(R.drawable.ic_image_placeholder)
            error(R.drawable.ic_image_placeholder)
        }

        holder.title.text = item.title

        // ⭐ Cambio: mostrar "N/A" si el rating es NaN; si no, formatear con 1 decimal
        holder.rating.text = if (item.rating.isNaN()) "N/A" else String.format("%.1f", item.rating)

        // Ubicación: usamos department
        holder.location.text = item.department

        // Learn / Teach (ojo con nombres: learnSkills / teachSkills)
        holder.learn.text = if (item.learnSkills.isEmpty())
            "Learn: —" else "Learn: ${item.learnSkills.joinToString(", ")}"
        holder.teach.text = if (item.teachSkills.isEmpty())
            "Teach: —" else "Teach: ${item.teachSkills.joinToString(", ")}"

        holder.reviews.text = "(${item.reviewCount} reviews)"
        holder.duration.text = "${item.duration} days"

        holder.itemView.setOnClickListener { onClick(item) }
    }
}
