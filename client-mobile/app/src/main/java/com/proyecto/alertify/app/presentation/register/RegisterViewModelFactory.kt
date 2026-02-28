package com.proyecto.alertify.app.presentation.register

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.proyecto.alertify.app.data.auth.AuthRepository
import com.proyecto.alertify.app.data.local.SharedPrefsTokenStorage
import com.proyecto.alertify.app.network.ApiClient

/**
 * Factory para construir [RegisterViewModel] con sus dependencias.
 *
 * Necesaria porque no se usa framework de inyección de dependencias (Hilt/Koin).
 *
 * Uso en Activity:
 * ```
 * val vm by viewModels<RegisterViewModel> { RegisterViewModelFactory(application) }
 * ```
 *
 * @param application Instancia de [Application] para obtener `applicationContext`.
 */
class RegisterViewModelFactory(
    private val application: Application
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (!modelClass.isAssignableFrom(RegisterViewModel::class.java)) {
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }

        val tokenStorage = SharedPrefsTokenStorage(application.applicationContext)
        val authApi = ApiClient.getAuthApi(tokenStorage)
        val authRepository = AuthRepository(authApi)

        return RegisterViewModel(authRepository) as T
    }
}
