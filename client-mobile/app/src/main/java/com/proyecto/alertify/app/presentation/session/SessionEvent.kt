package com.proyecto.alertify.app.presentation.session

/**
 * Eventos de sesión observables por cualquier Activity.
 *
 * Emitidos por [SessionEventBus] cuando ocurre un cambio de sesión
 * que requiere acción de la capa de UI (navegar a login, mostrar mensaje, etc.).
 *
 * A diferencia de los eventos one-shot de cada ViewModel (LoginUiEvent, RegisterUiEvent),
 * estos son **globales** y producidos desde la capa de red (TokenAuthenticator).
 */
sealed class SessionEvent {

    /**
     * La sesión del usuario expiró irrecuperablemente.
     *
     * Se emite cuando [TokenAuthenticator] detecta que el refresh token
     * fue rechazado/expiró y ya no es posible renovar la sesión.
     *
     * La Activity observadora debe:
     * 1. Mostrar un mensaje al usuario (si aplica).
     * 2. Navegar a la pantalla de login limpiando el back-stack.
     */
    object SessionExpired : SessionEvent()
}
