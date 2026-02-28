package com.proyecto.alertify.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.os.Bundle
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.proyecto.alertify.app.data.auth.AuthUiMessageFactory
import com.proyecto.alertify.app.presentation.login.LoginUiEvent
import com.proyecto.alertify.app.presentation.login.LoginUiState
import com.proyecto.alertify.app.presentation.login.LoginViewModel
import com.proyecto.alertify.app.presentation.login.LoginViewModelFactory
import com.proyecto.alertify.app.presentation.register.RegisterUiEvent
import com.proyecto.alertify.app.presentation.register.RegisterUiState
import com.proyecto.alertify.app.presentation.register.RegisterViewModel
import com.proyecto.alertify.app.presentation.register.RegisterViewModelFactory
import com.proyecto.alertify.app.presentation.session.SessionEvent
import com.proyecto.alertify.app.presentation.session.SessionEventBus
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var tvLoginTab: TextView
    private lateinit var tvRegisterTab: TextView
    private lateinit var tilUsername: TextInputLayout
    private lateinit var etUsernameEmail: TextInputEditText
    private lateinit var tilEmail: TextInputLayout
    private lateinit var etEmail: TextInputEditText
    private lateinit var etPassword: TextInputEditText
    private lateinit var tilConfirmPassword: TextInputLayout
    private lateinit var etConfirmPassword: TextInputEditText
    private lateinit var tvForgotPassword: TextView
    private lateinit var btnAction: Button
    private lateinit var ivBackgroundImage: ImageView
    private lateinit var loginCardView: CardView
    private lateinit var llSocialLogins: LinearLayout

    private var isLoginMode = true

    /** ViewModel – sobrevive a cambios de configuración (rotación) */
    private val loginViewModel: LoginViewModel by viewModels {
        LoginViewModelFactory(application)
    }

    /** ViewModel de registro – misma estrategia MVVM que login */
    private val registerViewModel: RegisterViewModel by viewModels {
        RegisterViewModelFactory(application)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_login)

        // View references
        tvLoginTab = findViewById(R.id.tv_login_tab)
        tvRegisterTab = findViewById(R.id.tv_register_tab)
        tilUsername = findViewById(R.id.til_username)
        etUsernameEmail = findViewById(R.id.et_username_email)
        tilEmail = findViewById(R.id.til_email)
        etEmail = findViewById(R.id.et_email)
        etPassword = findViewById(R.id.et_password)
        tilConfirmPassword = findViewById(R.id.til_confirm_password)
        etConfirmPassword = findViewById(R.id.et_confirm_password)
        tvForgotPassword = findViewById(R.id.tv_forgot_password)
        btnAction = findViewById(R.id.btn_action)

        ivBackgroundImage = findViewById(R.id.iv_background_image)
        loginCardView = findViewById(R.id.login_card_view)

        llSocialLogins = findViewById(R.id.ll_social_logins)

        applyBlurToBackground()
        updateUIMode(true)

        tvLoginTab.setOnClickListener { updateUIMode(true) }
        tvRegisterTab.setOnClickListener { updateUIMode(false) }

        btnAction.setOnClickListener {
            hideKeyboard()
            if (isLoginMode) {
                val email = etUsernameEmail.text?.toString()?.trim().orEmpty()
                val password = etPassword.text?.toString()?.trim().orEmpty()
                if (email.isBlank() || password.isBlank()) {
                    Toast.makeText(this, getString(R.string.error_empty_fields), Toast.LENGTH_SHORT).show()
                } else {
                    loginViewModel.login(email, password)
                }
            } else {
                val username = etUsernameEmail.text?.toString()?.trim().orEmpty()
                val email = etEmail.text?.toString()?.trim().orEmpty()
                val password = etPassword.text?.toString()?.trim().orEmpty()
                val confirmPassword = etConfirmPassword.text?.toString()?.trim().orEmpty()
                registerViewModel.register(username, email, password, confirmPassword)
            }
        }

        tvForgotPassword.setOnClickListener {
        }

        // ── Observar estado de la UI (StateFlow) ──────────────────────────
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                loginViewModel.uiState.collect { state -> renderLoginState(state) }
            }
        }

        // ── Observar eventos one-shot (Channel → Flow) ────────────────────
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                loginViewModel.events.collect { event -> handleLoginEvent(event) }
            }
        }

        // ── Observar estado de registro (StateFlow) ───────────────────────
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                registerViewModel.uiState.collect { state -> renderRegisterState(state) }
            }
        }

        // ── Observar eventos one-shot de registro (Channel → Flow) ────────
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                registerViewModel.events.collect { event -> handleRegisterEvent(event) }
            }
        }

        // Verificar si ya hay sesión activa (redirige sin mostrar formulario)
        loginViewModel.checkSession()
    }

    // ── MVVM: render + eventos ───────────────────────────────────────────

    /**
     * Renderiza el estado actual del flujo de login.
     * Loading: deshabilita botón. Idle/Error: habilita.
     */
    private fun renderLoginState(state: LoginUiState) {
        when (state) {
            is LoginUiState.Loading -> setLoadingState(true)
            is LoginUiState.Idle,
            is LoginUiState.Success -> setLoadingState(false)
            is LoginUiState.Error -> setLoadingState(false)
        }
    }

    /**
     * Procesa eventos one-shot del ViewModel (navegación, errores).
     * Se consume una sola vez – no se re-emite al rotar.
     */
    private fun handleLoginEvent(event: LoginUiEvent) {
        when (event) {
            is LoginUiEvent.NavigateToMain -> {
                Toast.makeText(this, getString(R.string.login_success), Toast.LENGTH_SHORT).show()
                NavigationHelper.navigateToMain(this)
            }
            is LoginUiEvent.ShowError -> {
                val msg = AuthUiMessageFactory.toMessage(this, event.authError)
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
            }
        }
    }

    // ── Flujo de Registro (MVVM) ──────────────────────────────────────────

    /**
     * Renderiza el estado actual del flujo de registro.
     * Loading: deshabilita botón. Idle/Error/Success: habilita.
     */
    private fun renderRegisterState(state: RegisterUiState) {
        when (state) {
            is RegisterUiState.Loading -> setLoadingState(true)
            is RegisterUiState.Idle,
            is RegisterUiState.Success,
            is RegisterUiState.Error -> setLoadingState(false)
        }
    }

    /**
     * Procesa eventos one-shot del ViewModel de registro.
     * [RegisterUiEvent.RegistrationSuccess] limpia campos y cambia a modo login.
     * [RegisterUiEvent.ShowError] muestra el mensaje localizado vía [AuthUiMessageFactory].
     */
    private fun handleRegisterEvent(event: RegisterUiEvent) {
        when (event) {
            is RegisterUiEvent.RegistrationSuccess -> {
                Toast.makeText(this, getString(R.string.registration_successful), Toast.LENGTH_LONG).show()
                clearFields()
                updateUIMode(true)
            }
            is RegisterUiEvent.ShowError -> {
                val msg = AuthUiMessageFactory.toMessage(this, event.authError)
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
            }
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    /**
     * Habilita/deshabilita el botón de acción y muestra feedback visual
     * mientras una petición HTTP está en curso.
     */
    private fun setLoadingState(loading: Boolean) {
        btnAction.isEnabled = !loading
        btnAction.alpha = if (loading) 0.6f else 1.0f
    }

    /**
     * Limpia todos los campos de texto del formulario (usado tras registro exitoso).
     */
    private fun clearFields() {
        etUsernameEmail.text?.clear()
        etEmail.text?.clear()
        etPassword.text?.clear()
        etConfirmPassword.text?.clear()
    }

    private fun updateUIMode(toLoginMode: Boolean) {
        isLoginMode = toLoginMode

        if (isLoginMode) {
            tvLoginTab.setBackgroundResource(R.drawable.shape_toggle_button_selected)
            tvLoginTab.setTextColor(getColor(R.color.toggle_unselected))
            tvRegisterTab.setBackgroundResource(R.drawable.shape_toggle_button_unselected)
            tvRegisterTab.setTextColor(getColor(R.color.toggle_selected))
            etUsernameEmail.hint = getString(R.string.hint_username_email)
        } else {
            tvLoginTab.setBackgroundResource(R.drawable.shape_toggle_button_unselected)
            tvLoginTab.setTextColor(getColor(R.color.toggle_selected))
            tvRegisterTab.setBackgroundResource(R.drawable.shape_toggle_button_selected)
            tvRegisterTab.setTextColor(getColor(R.color.toggle_unselected))
            etUsernameEmail.hint = getString(R.string.hint_username)
        }

        tilEmail.visibility = if (isLoginMode) View.GONE else View.VISIBLE
        tilConfirmPassword.visibility = if (isLoginMode) View.GONE else View.VISIBLE
        tvForgotPassword.visibility = if (isLoginMode) View.VISIBLE else View.GONE
        llSocialLogins.visibility = if (isLoginMode) View.VISIBLE else View.GONE

        btnAction.text = if (isLoginMode) getString(R.string.button_login) else getString(R.string.register_tab_text)
    }

    @Suppress("DEPRECATION")
    private fun applyBlurToBackground() {
        val bitmap = BitmapFactory.decodeResource(resources, R.drawable.background_general)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ivBackgroundImage.setRenderEffect(
                RenderEffect.createBlurEffect(20f, 20f, Shader.TileMode.CLAMP)
            )
            ivBackgroundImage.setImageBitmap(bitmap)
        } else {
            val scaledBitmap = Bitmap.createScaledBitmap(bitmap, bitmap.width / 4, bitmap.height / 4, false)
            val blurred = blurBitmap(this, scaledBitmap, 20f)
            ivBackgroundImage.setImageBitmap(blurred)
            bitmap.recycle()
        }
    }

    @Suppress("DEPRECATION")
    private fun blurBitmap(context: Context, bitmap: Bitmap, radius: Float): Bitmap {
        val rs = RenderScript.create(context)
        val input = Allocation.createFromBitmap(rs, bitmap)
        val output = Allocation.createTyped(rs, input.type)
        val script = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs))
        script.setRadius(radius)
        script.setInput(input)
        script.forEach(output)
        output.copyTo(bitmap)
        rs.destroy()
        return bitmap
    }

    private fun hideKeyboard() {
        val view = currentFocus
        if (view != null) {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(view.windowToken, 0)
        }
    }
}
