package com.proyecto.alertify.app.data.auth

import com.proyecto.alertify.app.network.dto.ErrorResponse
import java.io.IOException

/**
 * T12 – Mapea la respuesta de error de la API a un [AuthError] de dominio.
 *
 * Prioridad de clasificación:
 * 1. Campo `code` del backend (más específico).
 * 2. Código HTTP como fallback.
 * 3. Contenido de `message` como heurística para 403 sin code.
 * 4. Tipo de excepción (IOException → NetworkError).
 */
object AuthErrorMapper {

    /**
     * @param apiError  Body de error parseado (puede ser null si body vacío / error de red).
     * @param httpCode  Código HTTP de la respuesta (null si fue IOException).
     * @param throwable Excepción original (null si fue error HTTP sin excepción).
     */
    fun map(
        apiError: ErrorResponse?,
        httpCode: Int?,
        throwable: Throwable?
    ): AuthError {
        val requestId = apiError?.requestId
        val code = apiError?.code
        val message = when (val msg = apiError?.message) {
            is String -> msg
            else -> null
        }

        // ── 1. Mapeo por code del backend (T12) ────────────────────────
        if (code != null) {
            return when (code) {
                "AUTH_INVALID_CREDENTIALS" -> AuthError.InvalidCredentials(requestId)
                "AUTH_REFRESH_INVALID" -> AuthError.InvalidCredentials(requestId)
                "AUTH_ACCOUNT_BLOCKED" -> AuthError.AccountBlocked(requestId)
                "AUTH_ACCOUNT_INACTIVE" -> AuthError.AccountInactive(requestId)
                "AUTH_UNEXPECTED_ERROR" -> AuthError.ServerError(requestId)
                "VALIDATION_ERROR" -> AuthError.ValidationError(
                    errors = extractMessageList(apiError?.message),
                    requestId = requestId
                )
                "RESOURCE_CONFLICT" -> AuthError.ConflictError(
                    detail = message,
                    requestId = requestId
                )
                else -> mapByHttpCode(httpCode, requestId, throwable, message, apiError)
            }
        }

        // ── 2. Mapeo por tipo de excepción ──────────────────────────────
        if (throwable is IOException) {
            return AuthError.NetworkError(throwable)
        }

        // ── 3. Mapeo por HTTP status code ───────────────────────────────
        return mapByHttpCode(httpCode, requestId, throwable, message, apiError)
    }

    private fun mapByHttpCode(
        httpCode: Int?,
        requestId: String?,
        throwable: Throwable?,
        message: String?,
        apiError: ErrorResponse? = null
    ): AuthError {
        return when {
            httpCode == 400 -> AuthError.ValidationError(
                errors = extractMessageList(apiError?.message),
                requestId = requestId
            )

            httpCode == 401 -> AuthError.InvalidCredentials(requestId)

            httpCode == 403 -> {
                // Heurística si el backend viejo no envía code
                when {
                    message?.contains("bloquead", ignoreCase = true) == true ->
                        AuthError.AccountBlocked(requestId)
                    message?.contains("inactiv", ignoreCase = true) == true ->
                        AuthError.AccountInactive(requestId)
                    else -> AuthError.AccountInactive(requestId)
                }
            }

            httpCode == 409 -> AuthError.ConflictError(
                detail = message,
                requestId = requestId
            )

            httpCode != null && httpCode >= 500 -> AuthError.ServerError(requestId)

            throwable is IOException -> AuthError.NetworkError(throwable)

            else -> AuthError.UnknownError(requestId, throwable)
        }
    }

    /**
     * Extrae la lista de mensajes del campo `message` del error body.
     * El backend puede enviar un String simple o un List<String>.
     */
    private fun extractMessageList(message: Any?): List<String> {
        return when (message) {
            is String -> listOf(message)
            is List<*> -> message.filterIsInstance<String>()
            else -> emptyList()
        }
    }
}
