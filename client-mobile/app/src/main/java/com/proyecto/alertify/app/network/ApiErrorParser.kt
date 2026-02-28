package com.proyecto.alertify.app.network

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.proyecto.alertify.app.network.dto.ErrorResponse
import retrofit2.Response

/**
 * T12 – Parser centralizado de error bodies de Retrofit.
 *
 * Usa una instancia compartida de [Gson] para evitar crear objetos
 * en cada llamada. Tolera bodies vacíos o JSON malformado devolviendo `null`.
 *
 * Uso:
 * ```
 * val error = ApiErrorParser.parse(response)
 * ```
 */
object ApiErrorParser {

    private val gson = Gson()

    /**
     * Parsea el error body de una respuesta HTTP no exitosa.
     *
     * @param response Respuesta de Retrofit con código HTTP != 2xx.
     * @return [ErrorResponse] parseado o `null` si el body está vacío / no es JSON válido.
     */
    fun parse(response: Response<*>): ErrorResponse? {
        val raw = response.errorBody()?.string()
        if (raw.isNullOrBlank()) return null

        return try {
            gson.fromJson(raw, ErrorResponse::class.java)
        } catch (_: JsonSyntaxException) {
            null
        }
    }
}
