package com.proyecto.alertify.app.presentation.register

import com.proyecto.alertify.app.data.auth.AuthError

/**
 * Estado de la UI de registro, observado por la Activity vía StateFlow.
 *
 * El ViewModel expone este estado y la Activity renderiza según el caso.
 * Se usa [AuthError] en lugar de String para que la Activity resuelva
 * el mensaje localizado con [AuthUiMessageFactory], evitando pasar
 * Context al ViewModel.
 */
sealed class RegisterUiState {

    /** Estado inicial: formulario habilitado, sin errores. */
    object Idle : RegisterUiState()

    /** Petición HTTP en curso: mostrar indicador de carga. */
    object Loading : RegisterUiState()

    /** Registro exitoso. */
    object Success : RegisterUiState()

    /**
     * Error de registro.
     *
     * @param authError Error de dominio mapeado por [AuthErrorMapper].
     */
    data class Error(val authError: AuthError) : RegisterUiState()
}
