package com.proyecto.alertify.app.presentation.login

import com.proyecto.alertify.app.data.auth.AuthError

/**
 * Estado de la UI de login, observado por [LoginActivity] vía StateFlow.
 *
 * El ViewModel expone este estado y la Activity renderiza según el caso.
 * Se usa [AuthError] en lugar de String para que la Activity resuelva
 * el mensaje localizado con [AuthUiMessageFactory], evitando pasar
 * Context al ViewModel.
 */
sealed class LoginUiState {

    /** Estado inicial: formulario habilitado, sin errores. */
    object Idle : LoginUiState()

    /** Petición HTTP en curso: mostrar indicador de carga. */
    object Loading : LoginUiState()

    /** Login exitoso: tokens persistidos (la navegación ocurre vía evento). */
    object Success : LoginUiState()

    /**
     * Error de autenticación.
     *
     * @param authError Error de dominio mapeado por [AuthErrorMapper].
     *                  La Activity usa [AuthUiMessageFactory.toMessage] para obtener
     *                  el mensaje localizado.
     */
    data class Error(val authError: AuthError) : LoginUiState()
}
