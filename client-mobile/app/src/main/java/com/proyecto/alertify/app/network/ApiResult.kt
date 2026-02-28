package com.proyecto.alertify.app.network

import com.proyecto.alertify.app.network.dto.ErrorResponse

/**
 * T12 – Resultado genérico para llamadas a la API.
 *
 * Encapsula éxito o error de forma tipada, evitando manejar
 * excepciones en la capa de UI.
 *
 * Uso típico:
 * ```
 * when (val result = repository.login(email, password)) {
 *     is ApiResult.Success -> handleSuccess(result.data)
 *     is ApiResult.Error   -> handleError(result)
 * }
 * ```
 */
sealed class ApiResult<out T> {

    /** Respuesta HTTP exitosa (2xx) con body parseado. */
    data class Success<T>(val data: T) : ApiResult<T>()

    /**
     * Error de red, HTTP o inesperado.
     *
     * @param httpCode Código HTTP (null si fue error de red / IOException).
     * @param apiError Body de error parseado del backend (null si no se pudo parsear).
     * @param throwable Excepción original (null si fue error HTTP sin excepción).
     */
    data class Error(
        val httpCode: Int? = null,
        val apiError: ErrorResponse? = null,
        val throwable: Throwable? = null
    ) : ApiResult<Nothing>()
}
