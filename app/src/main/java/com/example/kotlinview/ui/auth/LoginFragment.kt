package com.example.kotlinview.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.kotlinview.R
import com.example.kotlinview.databinding.FragmentLoginBinding
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.kotlinview.data.auth.AuthResult
import kotlinx.coroutines.flow.collectLatest

class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    private val authVm: AuthViewModel by viewModels { AuthVmFactory() }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.btnLogin.setOnClickListener {
            // Ejemplo: obtén referencias a los EditText y botón
            val emailEt = binding.emailEditText   // ajusta a tus ids reales
            val passEt  = binding.passwordEditText
            val loginBtn = binding.loginButton

            loginBtn.setOnClickListener {
                val email = emailEt.text?.toString().orEmpty().trim()
                val pass  = passEt.text?.toString().orEmpty()
                if (email.isNotBlank() && pass.isNotBlank()) {
                    authVm.signIn(email, pass)
                } else {
                    Toast.makeText(requireContext(), "Ingresa email y contraseña", Toast.LENGTH_SHORT).show()
                }
            }

            // Auto-login si ya hay sesión (y carga perfil)
            authVm.tryAutoLoginAndLoadProfile()

            viewLifecycleOwner.lifecycleScope.launchWhenStarted {
                authVm.state.collectLatest { st ->
                    when (st) {
                        is AuthResult.Loading -> {
                            // muestra spinner si tienes
                        }
                        is AuthResult.Success -> {
                            // Navega a Home y elimina Login del backstack
                            findNavController().navigate(
                                R.id.homeFragment,
                                null,
                                androidx.navigation.NavOptions.Builder()
                                    .setPopUpTo(R.id.loginFragment, true)
                                    .build()
                            )
                        }
                        is AuthResult.Error -> {
                            Toast.makeText(requireContext(), st.throwable.message ?: "Error de autenticación", Toast.LENGTH_LONG).show()
                        }
                        null -> Unit
                    }
                }
            }

            // Navegar al Home (ajusta el id si tu destino tiene otro ID)
            try {
                findNavController().navigate(R.id.homeFragment)
            } catch (_: Exception) {
                Toast.makeText(requireContext(), "Home destination not found in nav_graph", Toast.LENGTH_SHORT).show()
            }




        }

        binding.btnRegister.setOnClickListener {
            // TODO: llevar a RegisterFragment cuando lo tengas. Por ahora un toast.
            Toast.makeText(requireContext(), "Go to registration", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}