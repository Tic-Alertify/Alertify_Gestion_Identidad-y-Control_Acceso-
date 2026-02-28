package com.proyecto.alertify.app.presentation.login

import com.proyecto.alertify.app.data.auth.AuthError

/**
 * Eventos one-shot de la pantalla de login.
 *
 * A diferencia de [LoginUiState], estos eventos se consumen una sola vez y
 * NO se re-emiten al rotar la pantalla, evitando navegación duplicada o
 * mostrar diálogos repetidos.
 *
 * Se implementan mediante un [Channel] → [receiveAsFlow] en el ViewModel.
 */
sealed class LoginUiEvent {

    /** Login exitoso: navegar a [MainActivity] y limpiar backstack. */
    object NavigateToMain : LoginUiEvent()

    /**
     * Error que debe mostrarse al usuario (Toast / Snackbar / Dialog).
     *
     * @param authError Error de dominio para que la Activity resuelva el mensaje
     *                  localizado vía [AuthUiMessageFactory].
     */
    data class ShowError(val authError: AuthError) : LoginUiEvent()
}
