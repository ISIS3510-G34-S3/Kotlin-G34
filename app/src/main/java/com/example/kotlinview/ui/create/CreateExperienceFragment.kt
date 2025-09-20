package com.example.kotlinview.ui.create

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.kotlinview.R
import com.example.kotlinview.databinding.FragmentCreateExperienceBinding

class CreateExperienceFragment : Fragment() {

    private var _binding: FragmentCreateExperienceBinding? = null
    private val binding get() = _binding!!

    private val categories = listOf(
        "Select category",
        "Agriculture & Arts",
        "Cooking & Technology",
        "Outdoor & Language",
        "Crafts & Business",
        "Photography & Agriculture"
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCreateExperienceBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // Spinner de categorías
        binding.spCategory.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            categories
        )

        // Add Photo placeholders (stub)
        binding.photoSlot1.setOnClickListener {
            Toast.makeText(requireContext(), "Add Photo 1 (TODO picker)", Toast.LENGTH_SHORT).show()
        }
        binding.photoSlot2.setOnClickListener {
            Toast.makeText(requireContext(), "Add Photo 2 (TODO picker)", Toast.LENGTH_SHORT).show()
        }

        // Añadir otra fila de fecha/hora
        binding.btnAddDate.setOnClickListener {
            val row = layoutInflater.inflate(R.layout.item_date_time_row, binding.containerDates, false)
            binding.containerDates.addView(row)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
