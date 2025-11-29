// app/src/main/java/com/example/kotlinview/ui/detail/ExperienceDetailFragment.kt
package com.example.kotlinview.ui.detail

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.kotlinview.R
import com.example.kotlinview.databinding.FragmentExperienceDetailBinding

class ExperienceDetailFragment : Fragment() {

    companion object {
        const val ARG_EXPERIENCE_ID = "experienceId"
        const val ARG_TITLE         = "experienceTitle"
        const val ARG_HOST_NAME     = "hostName"
        const val ARG_DEPARTMENT    = "department"
        const val ARG_DURATION      = "duration"
        const val ARG_PRICE         = "pricePerPerson"
        const val ARG_IMAGE_URL     = "imageUrl"
    }

    private var _binding: FragmentExperienceDetailBinding? = null
    private val binding get() = _binding!!

    private var experienceId: String? = null
    private var title: String? = null
    private var hostName: String? = null
    private var department: String? = null
    private var duration: Int = 0
    private var pricePerPerson: Long = 0L
    private var imageUrl: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        arguments?.let { args ->
            experienceId   = args.getString(ARG_EXPERIENCE_ID)
            title          = args.getString(ARG_TITLE)
            hostName       = args.getString(ARG_HOST_NAME)
            department     = args.getString(ARG_DEPARTMENT)
            duration       = args.getInt(ARG_DURATION)
            pricePerPerson = args.getLong(ARG_PRICE)
            imageUrl       = args.getString(ARG_IMAGE_URL)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentExperienceDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.buttonBookExperience.setOnClickListener {
            navigateToBooking()
        }

        binding.buttonBackDetail.setOnClickListener {
            findNavController().popBackStack()
        }
    }

    private fun navigateToBooking() {
        val expId = experienceId ?: return  // por seguridad

        val args = bundleOf(
            "experienceId"      to expId,
            "experienceTitle"   to title,
            "hostName"          to hostName,
            "department"        to department,
            "duration"          to duration,
            "pricePerPerson"    to pricePerPerson,
            "imageUrl"          to imageUrl
        )

        findNavController().navigate(
            R.id.bookExperienceFragment,
            args
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
