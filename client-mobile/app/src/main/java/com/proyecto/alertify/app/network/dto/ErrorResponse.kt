package com.proyecto.alertify.app.network.dto

import com.google.gson.annotations.SerializedName

/**
 * T12 – Estructura estandarizada de error devuelta por el backend NestJS.
 *
 * Compatible hacia atrás: tolera que `code`, `path` y `requestId` sean null
 * (respuestas del backend anterior a T12).
 *
 * El campo [message] puede ser un String simple o un Array de Strings
 * (errores de validación DTO). Se modela como `Any?` y se convierte a
 * texto legible con [getDisplayMessage].
 */
data class ErrorResponse(
    @SerializedName("statusCode") val statusCode: Int? = null,
    @SerializedName("message") val message: Any? = null,
    @SerializedName("error") val error: String? = null,
    @SerializedName("code") val code: String? = null,
    @SerializedName("timestamp") val timestamp: String? = null,
    @SerializedName("path") val path: String? = null,
    @SerializedName("requestId") val requestId: String? = null,
    @SerializedName("details") val details: Any? = null
) {
    /**
     * Extrae el mensaje de error como String legible.
     * Si [message] es una lista, une los elementos con salto de línea.
     */
    fun getDisplayMessage(): String {
        return when (message) {
            is String -> message
            is List<*> -> message.filterIsInstance<String>().joinToString("\n")
            else -> error ?: "Error desconocido"
        }
    }
}
