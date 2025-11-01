package com.example.kotlinview.ui.auth

import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.kotlinview.R
import com.example.kotlinview.data.auth.AuthResult
import com.example.kotlinview.databinding.FragmentLoginBinding
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.collectLatest

class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    private val authVm: AuthViewModel by viewModels()

    // Referencia al banner actual (para descartarlo en Success / nuevo intento / ciclo de vida)
    private var currentBanner: View? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1) Click en Login: bloquea si no hay internet y tampoco hay sesión previa local
        binding.btnLogin.setOnClickListener {
            // Cierra cualquier banner previo para evitar overlays persistentes
            dismissBanner()

            val hasLocalSession = FirebaseAuth.getInstance().currentUser != null
            if (!isDeviceOnline(requireContext()) && !hasLocalSession) {
                showGradientBanner(
                    "Login cannot be done due to lack of internet connection. Check your internet connection and try again"
                )
                return@setOnClickListener
            }

            // Obtén email/password (si tienes IDs específicos, úsalo en vez de estos helpers)
            val email = findEmailText(binding.root).orEmpty().trim()
            val password = findPasswordText(binding.root).orEmpty()

            if (email.isEmpty() || password.isEmpty()) {
                showGradientBanner("Please enter your email and password to continue")
                return@setOnClickListener
            }

            binding.btnLogin.isEnabled = false
            authVm.signIn(email, password)
        }

        // 2) Observa estado de Auth
        viewLifecycleOwner.lifecycleScope.launchWhenStarted {
            authVm.state.collectLatest { result ->
                when (result) {
                    is AuthResult.Loading -> {
                        binding.btnLogin.isEnabled = false
                    }
                    is AuthResult.Success -> {
                        binding.btnLogin.isEnabled = true
                        // Quita el banner antes de navegar para que no cubra la nueva pantalla
                        dismissBanner()

                        // Navegación directa por id de destino (Opción A)
                        val nav = findNavController()
                        val options = androidx.navigation.navOptions {
                            popUpTo(R.id.loginFragment) { inclusive = true }
                            launchSingleTop = true
                        }
                        // Asegúrate de que exista este destino en tu nav_graph
                        nav.navigate(R.id.homeFragment, null, options)
                    }
                    is AuthResult.Error -> {
                        binding.btnLogin.isEnabled = true
                        val t = result.throwable
                        if (isNetworkError(t)) {
                            showGradientBanner(
                                "Login cannot be done due to lack of internet connection. Check your internet connection and try again"
                            )
                        } else {
                            showGradientBanner(t?.message ?: "Login failed")
                        }
                    }
                    null -> Unit
                }
            }
        }

        // 3) Auto-login si ya existe sesión persistida localmente
        authVm.tryAutoLoginAndLoadProfile()
    }

    override fun onPause() {
        super.onPause()
        // Evita overlays residuales al salir de la pantalla
        dismissBanner()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        dismissBanner()
        _binding = null
    }

    /* ===================== Helpers de conectividad y UI ===================== */

    private fun isDeviceOnline(context: android.content.Context): Boolean {
        val cm = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE)
                as android.net.ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        // VALIDATED ≈ salida a internet operativa
        return caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    /** Heurística amplia para detectar errores de conectividad (servidor caído, timeout, DNS, SSL, etc.) */
    private fun isNetworkError(t: Throwable?): Boolean {
        if (t == null) return false
        return when (t) {
            is com.google.firebase.FirebaseNetworkException -> true
            is java.net.UnknownHostException -> true
            is java.net.SocketTimeoutException -> true
            is javax.net.ssl.SSLException -> true
            is java.io.IOException -> true // incluye muchos casos de transporte
            else -> {
                val m = t.message?.lowercase() ?: return false
                m.contains("network") ||
                        m.contains("timeout") ||
                        m.contains("failed to connect") ||
                        m.contains("unable to resolve host") ||
                        m.contains("ssl") ||
                        m.contains("host unreachable") ||
                        m.contains("connection reset") ||
                        m.contains("connection refused")
            }
        }
    }

    /**
     * Banner centrado con degradado, borde blanco y texto blanco.
     * No intercepta toques (no bloquea la navegación).
     * Se cierra automáticamente a los 5s o manualmente con dismissBanner().
     */
    private fun showGradientBanner(message: String) {
        // Asegura un solo banner visible
        dismissBanner()

        val contentRoot = requireActivity().findViewById<ViewGroup>(android.R.id.content)

        fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

        // Contenedor externo (borde blanco + esquinas redondeadas)
        val outer = android.widget.FrameLayout(requireContext()).apply {
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                setColor(android.graphics.Color.TRANSPARENT)
                cornerRadius = dp(16).toFloat()
                setStroke(dp(2), android.graphics.Color.WHITE) // borde blanco visible
            }
            setPadding(dp(2), dp(2), dp(2), dp(2)) // deja ver el stroke
            elevation = 24f
            layoutParams = android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = android.view.Gravity.CENTER // centrado en pantalla
                val m = dp(24)
                setMargins(m, m, m, m)
            }
            // Clave: NO interceptar toques para no bloquear nada
            isClickable = false
            isFocusable = false
            alpha = 0f
            scaleX = 0.98f
            scaleY = 0.98f
        }

        // Contenedor interno con tu degradado
        val inner = android.widget.FrameLayout(requireContext()).apply {
            setBackgroundResource(R.drawable.gradient_hero)
            setPadding(dp(16), dp(16), dp(16), dp(16))
            minimumWidth = dp(280)
            isClickable = false
            isFocusable = false
        }

        val tv = android.widget.TextView(requireContext()).apply {
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge)
            setTextColor(android.graphics.Color.WHITE) // texto BLANCO
            text = message
            setLineSpacing(0f, 1.15f)
            setPadding(dp(4), dp(4), dp(4), dp(4))
            maxWidth = dp(360)
            isClickable = false
            isFocusable = false
        }

        inner.addView(tv)
        outer.addView(inner)
        contentRoot.addView(outer)
        currentBanner = outer

        // Animación de entrada
        outer.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(180)
            .start()

        // Auto-ocultar a los 5s
        outer.postDelayed({ dismissBanner() }, 5000)
    }

    private fun dismissBanner() {
        val parent = currentBanner?.parent as? ViewGroup
        currentBanner?.animate()?.cancel()
        if (parent != null && currentBanner != null) {
            parent.removeView(currentBanner)
        }
        currentBanner = null
    }

    /* ===================== Helpers para obtener email/password ===================== */

    private fun findEmailText(root: View): String? {
        val edit = findFirstEditText(root) { et ->
            val itype = et.inputType
            val isEmailType =
                (itype and InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS) == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            val hintHasEmail = et.hint?.toString()?.contains("email", ignoreCase = true) == true
            isEmailType || hintHasEmail
        }
        return edit?.text?.toString()
    }

    private fun findPasswordText(root: View): String? {
        val edit = findFirstEditText(root) { et ->
            val itype = et.inputType
            val isPwdType =
                (itype and InputType.TYPE_TEXT_VARIATION_PASSWORD) == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                        (itype and InputType.TYPE_NUMBER_VARIATION_PASSWORD) == InputType.TYPE_NUMBER_VARIATION_PASSWORD
            val hintHasPwd = et.hint?.toString()?.contains("password", ignoreCase = true) == true
            isPwdType || hintHasPwd
        }
        return edit?.text?.toString()
    }

    private fun findFirstEditText(root: View, predicate: (EditText) -> Boolean): EditText? {
        if (root is EditText && predicate(root)) return root
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                val c = root.getChildAt(i)
                val r = findFirstEditText(c, predicate)
                if (r != null) return r
            }
        }
        return null
    }
}
