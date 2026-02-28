package com.proyecto.alertify.app.presentation.session

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Bus de eventos de sesión – singleton observable por cualquier pantalla.
 *
 * Reemplaza el patrón de callback `ApiClient.onSessionExpired` con un flujo
 * reactivo basado en [SharedFlow]. Esto permite:
 *
 * - **Desacoplamiento:** la capa de red emite eventos sin conocer la UI.
 * - **Lifecycle-safety:** cada Activity observa con `repeatOnLifecycle(STARTED)`,
 *   evitando leaks o crashes.
 * - **Múltiples observadores:** todas las Activities activas reciben el evento.
 *
 * Uso en la capa de red (ApiClient / TokenAuthenticator):
 * ```
 * SessionEventBus.emit(SessionEvent.SessionExpired)
 * ```
 *
 * Uso en Activities:
 * ```
 * lifecycleScope.launch {
 *     repeatOnLifecycle(Lifecycle.State.STARTED) {
 *         SessionEventBus.events.collect { event ->
 *             when (event) {
 *                 is SessionEvent.SessionExpired -> navigateToLogin()
 *             }
 *         }
 *     }
 * }
 * ```
 *
 * **Thread-safe:** [tryEmit] puede invocarse desde cualquier hilo (incluido el
 * hilo de OkHttp en el que corre [TokenAuthenticator]).
 */
object SessionEventBus {

    /**
     * Flujo interno mutable. `extraBufferCapacity = 1` permite emitir sin suspender
     * desde contextos no-coroutine mediante [tryEmit]. `replay = 0` evita que
     * Activities nuevas reciban eventos antiguos.
     */
    private val _events = MutableSharedFlow<SessionEvent>(
        replay = 0,
        extraBufferCapacity = 1
    )

    /** Flujo público de solo lectura para las Activities. */
    val events: SharedFlow<SessionEvent> = _events.asSharedFlow()

    /**
     * Emite un evento de sesión de forma no-bloqueante.
     *
     * Seguro para invocar desde cualquier hilo (OkHttp, Main, IO).
     * Si no hay suscriptores activos, el evento se descarta silenciosamente
     * (comportamiento correcto: si no hay UI visible, no hay nadie que actúe).
     *
     * @param event Evento de sesión a emitir.
     */
    fun emit(event: SessionEvent) {
        _events.tryEmit(event)
    }
}
