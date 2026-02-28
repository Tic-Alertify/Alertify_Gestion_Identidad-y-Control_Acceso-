package com.proyecto.alertify.app.presentation.register

import com.proyecto.alertify.app.data.auth.AuthError

/**
 * Eventos one-shot del flujo de registro.
 *
 * Se consumen una sola vez y NO se re-emiten al rotar la pantalla.
 * Se implementan mediante un [Channel] → [receiveAsFlow] en el ViewModel.
 */
sealed class RegisterUiEvent {

    /**
     * Registro exitoso: cambiar a modo login y mostrar mensaje de éxito.
     * El usuario debe iniciar sesión manualmente (el backend no devuelve tokens en registro).
     */
    object RegistrationSuccess : RegisterUiEvent()

    /**
     * Error que debe mostrarse al usuario (Toast / Snackbar / Dialog).
     *
     * @param authError Error de dominio para que la Activity resuelva el mensaje
     *                  localizado vía [AuthUiMessageFactory].
     */
    data class ShowError(val authError: AuthError) : RegisterUiEvent()
}
