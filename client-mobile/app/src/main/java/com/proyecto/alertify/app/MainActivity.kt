package com.proyecto.alertify.app

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.proyecto.alertify.app.data.auth.AuthSessionManager
import com.proyecto.alertify.app.data.local.SharedPrefsTokenStorage
import com.proyecto.alertify.app.network.ApiClient
import com.proyecto.alertify.app.presentation.session.SessionEvent
import com.proyecto.alertify.app.presentation.session.SessionEventBus
import kotlinx.coroutines.launch

/**
 * Pantalla principal de la aplicación una vez autenticado.
 *
 * De momento actúa como placeholder; incluye un botón de logout
 * para validar el flujo completo de persistencia de sesión.
 *
 * Observa [SessionEventBus] para reaccionar a la expiración de sesión
 * de forma lifecycle-safe (solo mientras la Activity está en STARTED).
 */
class MainActivity : AppCompatActivity() {

    private lateinit var sessionManager: AuthSessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val tokenStorage = SharedPrefsTokenStorage(applicationContext)
        sessionManager = AuthSessionManager(tokenStorage)

        val tvWelcome = findViewById<TextView>(R.id.tv_welcome)
        val btnLogout = findViewById<Button>(R.id.btn_logout)

        tvWelcome.text = getString(R.string.welcome_message)

        btnLogout.setOnClickListener {
            lifecycleScope.launch {
                sessionManager.logout()
                ApiClient.reset() // Limpiar conexiones HTTP tras cerrar sesión
                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.logout_success),
                    Toast.LENGTH_SHORT
                ).show()
                NavigationHelper.navigateToLogin(this@MainActivity)
            }
        }

        // ── Observar sesión expirada (SharedFlow – lifecycle-safe) ────────
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                SessionEventBus.events.collect { event ->
                    when (event) {
                        is SessionEvent.SessionExpired -> {
                            Toast.makeText(
                                this@MainActivity,
                                getString(R.string.session_expired),
                                Toast.LENGTH_LONG
                            ).show()
                            NavigationHelper.navigateToLogin(this@MainActivity)
                        }
                    }
                }
            }
        }
    }
}
