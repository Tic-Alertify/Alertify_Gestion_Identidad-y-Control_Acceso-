package com.proyecto.alertify.app.network.dto

import com.google.gson.annotations.SerializedName

/**
 * Estructura genérica de error devuelta por el backend NestJS.
 *
 * Cubre tanto errores de validación (400) como errores de negocio (401, 403, 409).
 * El campo [message] puede ser un String simple o un Array de Strings (validaciones DTO).
 * Se modela como Any y se convierte en la capa de presentación.
 */
data class ErrorResponse(
    @SerializedName("message") val message: Any?,
    @SerializedName("error") val error: String? = null,
    @SerializedName("statusCode") val statusCode: Int? = null
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
