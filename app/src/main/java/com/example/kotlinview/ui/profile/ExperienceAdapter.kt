package com.example.kotlinview.ui.profile

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.kotlinview.databinding.ItemExperienceProfileBinding
import com.example.kotlinview.model.Experience

class ExperienceAdapter(private val onEdit: (Experience) -> Unit) :
    ListAdapter<Experience, ExperienceAdapter.VH>(DIFF) {

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<Experience>() {
            override fun areItemsTheSame(oldItem: Experience, newItem: Experience) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: Experience, newItem: Experience) = oldItem == newItem
        }
    }

    inner class VH(private val b: ItemExperienceProfileBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(e: Experience) {
            b.tvTitle.text = e.title
            b.tvRating.text = if (e.rating > 0) "${e.rating} (${e.reviewCount} reviews)" else "No reviews"
            b.btnEditExp.setOnClickListener { onEdit(e) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemExperienceProfileBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }
}
