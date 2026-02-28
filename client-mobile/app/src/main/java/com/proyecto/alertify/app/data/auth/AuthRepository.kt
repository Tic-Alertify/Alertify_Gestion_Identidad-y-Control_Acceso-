package com.proyecto.alertify.app.data.auth

import com.proyecto.alertify.app.network.ApiErrorParser
import com.proyecto.alertify.app.network.ApiResult
import com.proyecto.alertify.app.network.AuthApi
import com.proyecto.alertify.app.network.dto.LoginRequest
import com.proyecto.alertify.app.network.dto.LoginResponse
import com.proyecto.alertify.app.network.dto.RegisterRequest
import com.proyecto.alertify.app.network.dto.RegisterResponse
import java.io.IOException

/**
 * T12 – Repositorio de autenticación que encapsula las llamadas a [AuthApi]
 * y traduce las respuestas HTTP a [ApiResult].
 *
 * Centraliza el manejo de errores de red y parsing, eliminando la lógica
 * dispersa que antes vivía en `LoginActivity`.
 *
 * Uso:
 * ```
 * val result = authRepository.login(email, password)
 * when (result) {
 *     is ApiResult.Success -> { /* body disponible en result.data */ }
 *     is ApiResult.Error   -> { /* mapear con AuthErrorMapper */ }
 * }
 * ```
 */
class AuthRepository(private val authApi: AuthApi) {

    /**
     * Ejecuta `POST /auth/login`.
     *
     * @return [ApiResult.Success] con [LoginResponse] o [ApiResult.Error] con detalles.
     */
    suspend fun login(email: String, password: String): ApiResult<LoginResponse> {
        return safeApiCall { authApi.login(LoginRequest(email, password)) }
    }

    /**
     * Ejecuta `POST /auth/registro`.
     *
     * @return [ApiResult.Success] con [RegisterResponse] o [ApiResult.Error] con detalles.
     */
    suspend fun register(
        email: String,
        username: String,
        password: String
    ): ApiResult<RegisterResponse> {
        return safeApiCall { authApi.register(RegisterRequest(email, username, password)) }
    }

    /**
     * Wrapper genérico que ejecuta una llamada Retrofit de forma segura.
     *
     * - IOException (timeout, sin red) → Error con throwable.
     * - HTTP != 2xx → Error con código y body parseado.
     * - Éxito → Success con body deserializado.
     */
    private suspend fun <T> safeApiCall(
        call: suspend () -> retrofit2.Response<T>
    ): ApiResult<T> {
        return try {
            val response = call()
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) {
                    ApiResult.Success(body)
                } else {
                    ApiResult.Error(httpCode = response.code())
                }
            } else {
                val apiError = ApiErrorParser.parse(response)
                ApiResult.Error(
                    httpCode = response.code(),
                    apiError = apiError
                )
            }
        } catch (e: IOException) {
            ApiResult.Error(throwable = e)
        } catch (e: Exception) {
            ApiResult.Error(throwable = e)
        }
    }
}
