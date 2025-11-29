package com.example.kotlinview.ui.auth

import android.os.Bundle
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.kotlinview.R
import com.example.kotlinview.core.ServiceLocator
import com.example.kotlinview.databinding.FragmentCreateAccountBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.flow.collectLatest
import com.example.kotlinview.core.NetworkUtil


class CreateAccountFragment : Fragment() {

    private var _binding: FragmentCreateAccountBinding? = null
    private val binding get() = _binding!!

    private val vm: CreateAccountViewModel by viewModels { CreateAccountVmFactory() }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCreateAccountBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.tvBackToLogin.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.btnCreateAccount.setOnClickListener {
            val name = binding.etName.text?.toString().orEmpty().trim()
            val email = binding.etEmail.text?.toString().orEmpty().trim()
            val password = binding.etPassword.text?.toString().orEmpty()

            when {
                name.isBlank() -> showDialog("Missing name", "Please enter your name.")
                email.isBlank() -> showDialog("Missing email", "Please enter your email.")
                !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> showDialog("Invalid email", "Please enter a valid email address.")
                password.isBlank() -> showDialog("Missing password", "Please enter your password.")
                password.length < 6 -> showDialog("Weak password", "Password must be at least 6 characters.")
                !NetworkUtil.isOnline(requireContext()) -> {
                    showDialog(
                        "No connection",
                        "You must be online to create an account. Please try again later when your connection is restored."
                    )
                }
                else -> vm.createAccount(name, email, password)
            }
        }

        viewLifecycleOwner.lifecycleScope.launchWhenStarted {
            vm.state.collectLatest { st ->
                binding.progress.visibility = if (st.isLoading) View.VISIBLE else View.GONE
                binding.btnCreateAccount.isEnabled = !st.isLoading

                val err = st.errorMessage
                if (!err.isNullOrBlank()) {
                    showDialog("Create account failed", err)
                    vm.clearError()
                }

                if (st.success) {
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Account created")
                        .setMessage("Your account was created successfully. Please log in with your email and password.")
                        .setPositiveButton("OK") { _, _ ->
                            findNavController().popBackStack() // back to Login
                        }
                        .show()
                }
            }
        }
    }

    private fun showDialog(title: String, message: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
