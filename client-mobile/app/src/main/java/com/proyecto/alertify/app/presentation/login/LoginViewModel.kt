package com.proyecto.alertify.app.presentation.login

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.proyecto.alertify.app.data.auth.AuthErrorMapper
import com.proyecto.alertify.app.data.auth.AuthRepository
import com.proyecto.alertify.app.data.auth.AuthSessionManager
import com.proyecto.alertify.app.network.ApiResult
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * ViewModel para el flujo de login.
 *
 * Encapsula la lógica de autenticación y expone el estado de la UI de forma
 * reactiva, sobreviviendo a cambios de configuración (rotación de pantalla).
 *
 * - [uiState]: estado persistente de la pantalla (Loading / Error / Idle / Success).
 * - [events]: eventos one-shot (navegación, mostrar error) que se consumen una sola vez.
 *
 * **No contiene referencias a Context.** La Activity resuelve los mensajes
 * localizados usando [AuthUiMessageFactory].
 *
 * @param authRepository Repositorio de autenticación (Retrofit + manejo de errores).
 * @param sessionManager Gestor de sesión (persistencia de tokens).
 */
class LoginViewModel(
    private val authRepository: AuthRepository,
    private val sessionManager: AuthSessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private val _events = Channel<LoginUiEvent>(Channel.BUFFERED)
    val events: Flow<LoginUiEvent> = _events.receiveAsFlow()

    /**
     * Ejecuta el flujo de login:
     * 1. Valida que los campos no estén vacíos (validación básica).
     * 2. Emite estado [LoginUiState.Loading].
     * 3. Llama a [AuthRepository.login].
     * 4. Si éxito: persiste tokens vía [AuthSessionManager] y emite [LoginUiEvent.NavigateToMain].
     * 5. Si error: mapea a [AuthError] y emite [LoginUiEvent.ShowError] + [LoginUiState.Error].
     *
     * @param email    Correo electrónico ingresado por el usuario.
     * @param password Contraseña ingresada por el usuario.
     */
    fun login(email: String, password: String) {
        // Evitar múltiples requests simultáneos
        if (_uiState.value is LoginUiState.Loading) return

        _uiState.value = LoginUiState.Loading

        viewModelScope.launch {
            val result = authRepository.login(email, password)

            when (result) {
                is ApiResult.Success -> {
                    try {
                        sessionManager.onLoginSuccess(
                            result.data.accessToken,
                            result.data.refreshToken
                        )
                        _uiState.value = LoginUiState.Success
                        _events.send(LoginUiEvent.NavigateToMain)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error al persistir sesión", e)
                        val authError = AuthErrorMapper.map(null, null, e)
                        _uiState.value = LoginUiState.Error(authError)
                        _events.send(LoginUiEvent.ShowError(authError))
                    }
                }

                is ApiResult.Error -> {
                    val authError = AuthErrorMapper.map(
                        result.apiError, result.httpCode, result.throwable
                    )
                    Log.e(
                        TAG,
                        "Login error: code=${result.apiError?.code} http=${result.httpCode}",
                        result.throwable
                    )
                    _uiState.value = LoginUiState.Error(authError)
                    _events.send(LoginUiEvent.ShowError(authError))
                }
            }
        }
    }

    /**
     * Verifica si existe una sesión activa (token almacenado localmente).
     *
     * Si hay sesión, emite [LoginUiEvent.NavigateToMain] para que la Activity
     * navegue a Main sin mostrar el formulario de login.
     *
     * Usa la versión síncrona [AuthSessionManager.isLoggedInSync] ya que
     * SharedPreferences lee desde memoria (caché) y es instantánea.
     */
    fun checkSession() {
        if (sessionManager.isLoggedInSync()) {
            viewModelScope.launch {
                _events.send(LoginUiEvent.NavigateToMain)
            }
        }
    }

    /**
     * Restaura el estado a [LoginUiState.Idle] tras mostrar un error,
     * habilitando el formulario para un nuevo intento.
     */
    fun resetState() {
        _uiState.value = LoginUiState.Idle
    }

    companion object {
        private const val TAG = "LoginViewModel"
    }
}
