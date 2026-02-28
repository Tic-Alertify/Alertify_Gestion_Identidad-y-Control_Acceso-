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
import com.google.gson.Gson
import com.proyecto.alertify.app.data.auth.AuthSessionManager
import com.proyecto.alertify.app.data.local.SharedPrefsTokenStorage
import com.proyecto.alertify.app.network.ApiClient
import com.proyecto.alertify.app.network.AuthApi
import com.proyecto.alertify.app.network.dto.ErrorResponse
import com.proyecto.alertify.app.network.dto.LoginRequest
import com.proyecto.alertify.app.network.dto.RegisterRequest
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

    /** Cliente Retrofit para endpoints de autenticación */
    private lateinit var authApi: AuthApi

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inicializar almacenamiento de token y gestor de sesión
        val tokenStorage = SharedPrefsTokenStorage(applicationContext)
        sessionManager = AuthSessionManager(tokenStorage)
        authApi = ApiClient.getAuthApi(tokenStorage)

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
     * Se invoca cuando el backend responde con un login exitoso y devuelve un JWT.
     *
     * Persiste el token localmente y navega a la pantalla principal.
     * Si falla la persistencia, muestra un mensaje de error al usuario.
     *
     * @param accessToken JWT devuelto por el endpoint /auth/login.
     */
    fun onLoginSuccess(accessToken: String) {
        lifecycleScope.launch {
            try {
                sessionManager.onLoginSuccess(accessToken)
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
     * Valida los campos del formulario y ejecuta el request `POST /auth/login`.
     *
     * En caso de éxito delega a [onLoginSuccess] para persistir el token
     * y navegar a [MainActivity]. Si el backend devuelve un error HTTP,
     * se parsea el body de error y se muestra al usuario.
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
            try {
                val response = authApi.login(LoginRequest(email, password))  // suspende en IO interno de Retrofit
                if (response.isSuccessful) {
                    val body = response.body()!!
                    onLoginSuccess(body.accessToken)
                } else {
                    val errorMsg = parseErrorBody(response.errorBody()?.string())
                    Toast.makeText(this@LoginActivity, errorMsg, Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Login network error", e)
                Toast.makeText(
                    this@LoginActivity,
                    getString(R.string.error_connection, e.localizedMessage ?: ""),
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                setLoadingState(false)
            }
        }
    }

    // ── Flujo de Registro ───────────────────────────────────────────────────

    /**
     * Valida los campos del formulario (incluida coincidencia de contraseñas)
     * y ejecuta el request `POST /auth/registro`.
     *
     * En caso de éxito muestra un Toast con el mensaje del backend,
     * limpia los campos y cambia automáticamente a modo login para
     * que el usuario pueda autenticarse de inmediato.
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
            try {
                val response = authApi.register(RegisterRequest(email, username, password))
                if (response.isSuccessful) {
                    Toast.makeText(
                        this@LoginActivity,
                        getString(R.string.registration_successful),
                        Toast.LENGTH_LONG
                    ).show()
                    clearFields()
                    updateUIMode(true)
                } else {
                    val errorMsg = parseErrorBody(response.errorBody()?.string())
                    Toast.makeText(this@LoginActivity, errorMsg, Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Register network error", e)
                Toast.makeText(
                    this@LoginActivity,
                    getString(R.string.error_connection, e.localizedMessage ?: ""),
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                setLoadingState(false)
            }
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    /**
     * Parsea el cuerpo de error JSON del backend NestJS.
     *
     * El backend devuelve `{ message, error, statusCode }` donde `message`
     * puede ser un String o un Array de Strings (errores de validación DTO).
     */
    private fun parseErrorBody(json: String?): String {
        if (json.isNullOrBlank()) return getString(R.string.error_unknown)
        return try {
            Gson().fromJson(json, ErrorResponse::class.java).getDisplayMessage()
        } catch (_: Exception) {
            getString(R.string.error_unknown)
        }
    }

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
