package com.proyecto.alertify.app.presentation.register

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.proyecto.alertify.app.data.auth.AuthError
import com.proyecto.alertify.app.data.auth.AuthErrorMapper
import com.proyecto.alertify.app.data.auth.AuthRepository
import com.proyecto.alertify.app.network.ApiResult
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * ViewModel para el flujo de registro.
 *
 * Encapsula la lógica de registro y expone el estado de la UI de forma
 * reactiva, sobreviviendo a cambios de configuración (rotación de pantalla).
 *
 * - [uiState]: estado persistente de la pantalla (Loading / Error / Idle / Success).
 * - [events]: eventos one-shot (navegación, mostrar error) que se consumen una sola vez.
 *
 * **No contiene referencias a Context.** La Activity resuelve los mensajes
 * localizados usando [AuthUiMessageFactory].
 *
 * @param authRepository Repositorio de autenticación (Retrofit + manejo de errores).
 */
class RegisterViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<RegisterUiState>(RegisterUiState.Idle)
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()

    private val _events = Channel<RegisterUiEvent>(Channel.BUFFERED)
    val events: Flow<RegisterUiEvent> = _events.receiveAsFlow()

    /**
     * Ejecuta el flujo de registro:
     * 1. Valida campos localmente (vacíos, password mismatch).
     * 2. Emite estado [RegisterUiState.Loading].
     * 3. Llama a [AuthRepository.register].
     * 4. Si éxito: emite [RegisterUiEvent.RegistrationSuccess] + [RegisterUiState.Success].
     * 5. Si error: mapea a [AuthError] y emite [RegisterUiEvent.ShowError] + [RegisterUiState.Error].
     *
     * @param username        Nombre de usuario ingresado.
     * @param email           Correo electrónico ingresado.
     * @param password        Contraseña ingresada.
     * @param confirmPassword Confirmación de contraseña.
     */
    fun register(username: String, email: String, password: String, confirmPassword: String) {
        // Evitar múltiples requests simultáneos
        if (_uiState.value is RegisterUiState.Loading) return

        // ── Validación local (sin llamar al backend) ────────────────────
        if (username.isBlank() || email.isBlank() || password.isBlank() || confirmPassword.isBlank()) {
            val error = AuthError.ValidationError(
                errors = listOf("Complete todos los campos"),
                requestId = null
            )
            _uiState.value = RegisterUiState.Error(error)
            viewModelScope.launch { _events.send(RegisterUiEvent.ShowError(error)) }
            return
        }

        if (password != confirmPassword) {
            val error = AuthError.ValidationError(
                errors = listOf("Las contraseñas no coinciden"),
                requestId = null
            )
            _uiState.value = RegisterUiState.Error(error)
            viewModelScope.launch { _events.send(RegisterUiEvent.ShowError(error)) }
            return
        }

        // ── Llamada al backend ──────────────────────────────────────────
        _uiState.value = RegisterUiState.Loading

        viewModelScope.launch {
            val result = authRepository.register(email, username, password)

            when (result) {
                is ApiResult.Success -> {
                    _uiState.value = RegisterUiState.Success
                    _events.send(RegisterUiEvent.RegistrationSuccess)
                }

                is ApiResult.Error -> {
                    val authError = AuthErrorMapper.map(
                        result.apiError, result.httpCode, result.throwable
                    )
                    Log.e(
                        TAG,
                        "Register error: code=${result.apiError?.code} http=${result.httpCode}",
                        result.throwable
                    )
                    _uiState.value = RegisterUiState.Error(authError)
                    _events.send(RegisterUiEvent.ShowError(authError))
                }
            }
        }
    }

    /**
     * Restaura el estado a [RegisterUiState.Idle] tras mostrar un error,
     * habilitando el formulario para un nuevo intento.
     */
    fun resetState() {
        _uiState.value = RegisterUiState.Idle
    }

    companion object {
        private const val TAG = "RegisterViewModel"
    }
}
