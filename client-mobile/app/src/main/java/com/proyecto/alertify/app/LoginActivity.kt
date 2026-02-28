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
import android.util.Log
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import androidx.lifecycle.lifecycleScope
import com.proyecto.alertify.app.data.auth.AuthErrorMapper
import com.proyecto.alertify.app.data.auth.AuthRepository
import com.proyecto.alertify.app.data.auth.AuthSessionManager
import com.proyecto.alertify.app.data.auth.AuthUiMessageFactory
import com.proyecto.alertify.app.data.local.SharedPrefsTokenStorage
import com.proyecto.alertify.app.network.ApiClient
import com.proyecto.alertify.app.network.ApiResult
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

    /** Gestor de sesión – centraliza persistencia del token */
    private lateinit var sessionManager: AuthSessionManager

    /** Repositorio de autenticación – centraliza llamadas + manejo de errores (T12) */
    private lateinit var authRepository: AuthRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inicializar almacenamiento de token y gestor de sesión
        val tokenStorage = SharedPrefsTokenStorage(applicationContext)
        sessionManager = AuthSessionManager(tokenStorage)
        authRepository = AuthRepository(ApiClient.getAuthApi(tokenStorage))

        // T10: Registrar callback para sesión expirada (refresh token rechazado)
        ApiClient.onSessionExpired = {
            runOnUiThread {
                NavigationHelper.navigateToLogin(this)
            }
        }

        // Verificar sesión existente de forma síncrona (SharedPreferences es local e instantáneo).
        // Si hay token, redirigir a Main sin inflar la UI de login (evita flash visual).
        if (sessionManager.isLoggedInSync()) {
            NavigationHelper.navigateToMain(this)
            return
        }

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
                performLogin()
            } else {
                performRegister()
            }
        }

        tvForgotPassword.setOnClickListener {
        }
    }

    /**
     * Se invoca cuando el backend responde con un login exitoso.
     *
     * Persiste ambos tokens localmente y navega a la pantalla principal.
     * Si falla la persistencia, muestra un mensaje de error al usuario.
     *
     * @param accessToken  JWT de acceso devuelto por /auth/login.
     * @param refreshToken Refresh token devuelto por /auth/login.
     */
    fun onLoginSuccess(accessToken: String, refreshToken: String) {
        lifecycleScope.launch {
            try {
                sessionManager.onLoginSuccess(accessToken, refreshToken)
                Toast.makeText(
                    this@LoginActivity,
                    getString(R.string.login_success),
                    Toast.LENGTH_SHORT
                ).show()
                NavigationHelper.navigateToMain(this@LoginActivity)
            } catch (e: Exception) {
                Toast.makeText(
                    this@LoginActivity,
                    getString(R.string.error_persist_session),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // ── Flujo de Login ──────────────────────────────────────────────────────

    /**
     * T12 – Valida campos y ejecuta login vía [AuthRepository].
     *
     * Delega el parsing de errores a [AuthErrorMapper] y la generación
     * de mensajes a [AuthUiMessageFactory]. No limpia campos en caso de error
     * para que el usuario pueda corregir.
     */
    private fun performLogin() {
        val email = etUsernameEmail.text?.toString()?.trim().orEmpty()
        val password = etPassword.text?.toString()?.trim().orEmpty()

        if (email.isBlank() || password.isBlank()) {
            Toast.makeText(this, getString(R.string.error_empty_fields), Toast.LENGTH_SHORT).show()
            return
        }

        setLoadingState(true)

        lifecycleScope.launch {
            val result = authRepository.login(email, password)
            when (result) {
                is ApiResult.Success -> {
                    onLoginSuccess(result.data.accessToken, result.data.refreshToken)
                }
                is ApiResult.Error -> {
                    val authError = AuthErrorMapper.map(
                        result.apiError, result.httpCode, result.throwable
                    )
                    val msg = AuthUiMessageFactory.toMessage(this@LoginActivity, authError)
                    Log.e(TAG, "Login error: code=${result.apiError?.code} http=${result.httpCode}", result.throwable)
                    Toast.makeText(this@LoginActivity, msg, Toast.LENGTH_LONG).show()
                }
            }
            setLoadingState(false)
        }
    }

    // ── Flujo de Registro ───────────────────────────────────────────────────

    /**
     * T12 – Valida campos y ejecuta registro vía [AuthRepository].
     *
     * Reutiliza el mismo pipeline de manejo de errores que login.
     */
    private fun performRegister() {
        val username = etUsernameEmail.text?.toString()?.trim().orEmpty()
        val email = etEmail.text?.toString()?.trim().orEmpty()
        val password = etPassword.text?.toString()?.trim().orEmpty()
        val confirmPassword = etConfirmPassword.text?.toString()?.trim().orEmpty()

        if (username.isBlank() || email.isBlank() || password.isBlank() || confirmPassword.isBlank()) {
            Toast.makeText(this, getString(R.string.error_empty_fields), Toast.LENGTH_SHORT).show()
            return
        }

        if (password != confirmPassword) {
            Toast.makeText(this, getString(R.string.error_passwords_mismatch), Toast.LENGTH_SHORT).show()
            return
        }

        setLoadingState(true)

        lifecycleScope.launch {
            val result = authRepository.register(email, username, password)
            when (result) {
                is ApiResult.Success -> {
                    Toast.makeText(
                        this@LoginActivity,
                        getString(R.string.registration_successful),
                        Toast.LENGTH_LONG
                    ).show()
                    clearFields()
                    updateUIMode(true)
                }
                is ApiResult.Error -> {
                    val authError = AuthErrorMapper.map(
                        result.apiError, result.httpCode, result.throwable
                    )
                    val msg = AuthUiMessageFactory.toMessage(this@LoginActivity, authError)
                    Log.e(TAG, "Register error: code=${result.apiError?.code} http=${result.httpCode}", result.throwable)
                    Toast.makeText(this@LoginActivity, msg, Toast.LENGTH_LONG).show()
                }
            }
            setLoadingState(false)
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

    companion object {
        private const val TAG = "LoginActivity"
    }
}
