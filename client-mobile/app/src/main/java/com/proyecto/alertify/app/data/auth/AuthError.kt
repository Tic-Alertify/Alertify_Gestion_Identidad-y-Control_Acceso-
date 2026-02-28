package com.proyecto.alertify.app.data.auth

/**
 * T12 – Modelo de dominio para errores de autenticación.
 *
 * Permite que la UI mapee cada caso a un mensaje localizado (strings.xml)
 * sin depender de códigos HTTP ni de la estructura JSON del backend.
 *
 * @property requestId Identificador de request para soporte técnico (puede ser null
 * si el backend no lo proporcionó o si fue un error de red).
 */
sealed class AuthError(open val requestId: String? = null) {

    /** 401 – Email o contraseña incorrectos (sin revelar cuál). */
    data class InvalidCredentials(override val requestId: String? = null) : AuthError(requestId)

    /** 403 – Cuenta en estado "bloqueado". */
    data class AccountBlocked(override val requestId: String? = null) : AuthError(requestId)

    /** 403 – Cuenta en estado "inactivo". */
    data class AccountInactive(override val requestId: String? = null) : AuthError(requestId)

    /** 400 – Error de validación de datos (DTO). */
    data class ValidationError(
        val errors: List<String> = emptyList(),
        override val requestId: String? = null
    ) : AuthError(requestId)

    /** 409 – Recurso en conflicto (email/username duplicado). */
    data class ConflictError(
        val detail: String? = null,
        override val requestId: String? = null
    ) : AuthError(requestId)

    /** 500+ – Error inesperado del servidor. */
    data class ServerError(override val requestId: String? = null) : AuthError(requestId)

    /** Sin conexión / timeout / DNS. */
    data class NetworkError(val cause: Throwable? = null) : AuthError(null)

    /** Cualquier error no categorizado. */
    data class UnknownError(
        override val requestId: String? = null,
        val cause: Throwable? = null
    ) : AuthError(requestId)
}
