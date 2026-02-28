package com.proyecto.alertify.app.presentation.login

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.proyecto.alertify.app.data.auth.AuthRepository
import com.proyecto.alertify.app.data.auth.AuthSessionManager
import com.proyecto.alertify.app.data.local.SharedPrefsTokenStorage
import com.proyecto.alertify.app.network.ApiClient

/**
 * Factory para construir [LoginViewModel] con sus dependencias.
 *
 * Necesaria porque no se usa framework de inyección de dependencias (Hilt/Koin).
 * Construye el grafo de dependencias manualmente:
 *
 * ```
 * Application
 *   └─ SharedPrefsTokenStorage(appContext)
 *       ├─ AuthSessionManager(tokenStorage)
 *       └─ ApiClient.getAuthApi(tokenStorage)
 *           └─ AuthRepository(authApi)
 *               └─ LoginViewModel(authRepository, sessionManager)
 * ```
 *
 * Uso en Activity:
 * ```
 * val vm by viewModels<LoginViewModel> { LoginViewModelFactory(application) }
 * ```
 *
 * @param application Instancia de [Application] para obtener `applicationContext`
 *                    (necesario para SharedPreferences).
 */
class LoginViewModelFactory(
    private val application: Application
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (!modelClass.isAssignableFrom(LoginViewModel::class.java)) {
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }

        val tokenStorage = SharedPrefsTokenStorage(application.applicationContext)
        val sessionManager = AuthSessionManager(tokenStorage)
        val authApi = ApiClient.getAuthApi(tokenStorage)
        val authRepository = AuthRepository(authApi)

        return LoginViewModel(authRepository, sessionManager) as T
    }
}
