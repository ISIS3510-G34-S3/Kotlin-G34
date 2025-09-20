package com.example.kotlinview.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.kotlinview.databinding.FragmentProfileBinding
import com.example.kotlinview.model.Experience

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private lateinit var hostedAdapter: ExperienceAdapter
    private lateinit var joinedAdapter: ExperienceAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setupToolbar()
        setupAdapters()
        populateMock()
        bindClicks()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener { requireActivity().onBackPressed() }
        binding.btnEdit.setOnClickListener {
            // TODO: navigate to edit profile
        }
    }

    private fun setupAdapters() {
        hostedAdapter = ExperienceAdapter { experience ->
            // TODO: edit experience callback
        }
        joinedAdapter = ExperienceAdapter { _ -> /* click */ }

        binding.rvHosted.layoutManager = LinearLayoutManager(requireContext())
        binding.rvHosted.adapter = hostedAdapter

        binding.rvJoined.layoutManager = LinearLayoutManager(requireContext())
        binding.rvJoined.adapter = joinedAdapter
    }

    private fun populateMock() {
        val mockUser = mapOf(
            "name" to "Ana Sofia Rodriguez",
            "email" to "ana.sofia@email.com",
            "verified" to false,
            "bio" to "Passionate about Colombian culture and digital design. Love connecting with travelers and sharing our beautiful traditions. I have been hosting experiences for over 3 years and enjoy meeting people from around the world.",
            "languages" to listOf("Spanish (Native)","English (Fluent)","Portuguese (Basic)","French (Learning)"),
            "memberSince" to "January 2024"
        )

        binding.tvName.text = mockUser["name"] as String
        binding.tvEmail.text = mockUser["email"] as String
        binding.tvBio.text = mockUser["bio"] as String
        binding.tvMemberSince.text = "Member since ${mockUser["memberSince"]}"

        val languages = mockUser["languages"] as List<String>
        binding.languagesContainer.removeAllViews()
        for (l in languages) {
            val tv = layoutInflater.inflate(android.R.layout.simple_list_item_1, binding.languagesContainer, false) as android.widget.TextView
            tv.text = l
            tv.setTextAppearance(android.R.style.TextAppearance_Material_Body1)
            binding.languagesContainer.addView(tv)
        }

        val experiences = sampleExperiences()
        hostedAdapter.submitList(experiences.take(3))
        joinedAdapter.submitList(experiences.drop(3))
    }

    private fun bindClicks() {
        binding.btnStartVerification.setOnClickListener {
            // start verification action
        }
    }

    private fun sampleExperiences(): List<Experience> {
        return listOf(
            Experience("1","Traditional Arepa Cooking & Learn Web Design","Ana Sofia Rodriguez","Medellín, Colombia",true,4.8,24,"Master the art...", "3 hours","Cooking & Technology", listOf("Traditional Recipes","Colombian Cuisine"), listOf("Web Design Basics"), listOf("Arepa Making"), true),
            Experience("2","Coffee Farm Photography Workshop","Ana Sofia Rodriguez","Armenia, Colombia",true,0.0,0,"Learn advanced photography...", "4 hours","Photography & Agriculture", listOf("Photography","Coffee Knowledge"), listOf("Advanced Photography"), listOf("Coffee Processing"), false),
            Experience("3","Urban Art & Design Thinking Workshop","Ana Sofia Rodriguez","Medellín, Colombia",true,4.9,15,"Explore street art...", "5 hours","Art & Design", listOf("Urban Art"), listOf("Design Thinking"), listOf("Street Art History"), true),
            Experience("4","Salsa Dancing & Spanish Lessons","Carlos Mendoza","Cali, Colombia",true,4.9,31,"Learn salsa...", "2 hours","Dance & Language", listOf("Salsa"), listOf("English Conversation"), listOf("Salsa Dancing"), true),
            Experience("5","Handicraft Making & Marketing Tips","Isabella Torres","Cartagena, Colombia",true,4.7,18,"Create traditional...", "3 hours","Crafts & Business", listOf("Handicrafts"), listOf("Digital Marketing"), listOf("Handicraft Techniques"), true),
            Experience("6","Coffee Cupping & Business English","Diego Herrera","Armenia, Colombia",true,4.6,12,"Professional coffee tasting...", "3 hours","Food & Language", listOf("Coffee Cupping"), listOf("Business English"), listOf("Coffee Tasting"), true)
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
