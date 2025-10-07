package com.example.kotlinview.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.example.kotlinview.R
import com.example.kotlinview.data.auth.AuthResult
import com.example.kotlinview.databinding.FragmentLoginBinding
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
        super.onViewCreated(view, savedInstanceState)

        // 1) Click de Login: SOLO dispara el signIn. NO navegar aquí.
        binding.btnLogin.setOnClickListener {
            val email = binding.etEmail.text?.toString().orEmpty().trim()
            val pass  = binding.etPassword.text?.toString().orEmpty()
            if (email.isBlank() || pass.isBlank()) {
                Toast.makeText(requireContext(), "Ingresa email y contraseña", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            authVm.signIn(email, pass)
        }

        // (Opcional) Botón de registro si luego lo implementas
        // binding.btnRegister.setOnClickListener { /* abrir pantalla de registro */ }

        // 2) Observa el estado y SOLO navega cuando sea Success
        viewLifecycleOwner.lifecycleScope.launchWhenStarted {
            authVm.state.collectLatest { st ->
                when (st) {
                    is AuthResult.Loading -> {
                        // muestra un spinner si quieres
                        binding.btnLogin.isEnabled = false
                    }
                    is AuthResult.Success -> {
                        binding.btnLogin.isEnabled = true
                        // Navega a Home y saca Login del back stack
                        findNavController().navigate(
                            R.id.homeFragment,
                            null,
                            NavOptions.Builder()
                                .setPopUpTo(R.id.loginFragment, true)
                                .build()
                        )
                    }
                    is AuthResult.Error -> {
                        binding.btnLogin.isEnabled = true
                        Toast.makeText(
                            requireContext(),
                            st.throwable.message ?: "Error de autenticación",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    null -> Unit
                }
            }
        }

        // 3) Si ya hay sesión previa, el VM lo detecta y emitirá Success (no navegues manualmente aquí)
        authVm.tryAutoLoginAndLoadProfile()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
