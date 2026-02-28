package com.proyecto.alertify.app.network

import com.proyecto.alertify.app.network.dto.LoginRequest
import com.proyecto.alertify.app.network.dto.LoginResponse
import com.proyecto.alertify.app.network.dto.RegisterRequest
import com.proyecto.alertify.app.network.dto.RegisterResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Interfaz Retrofit para los endpoints de autenticación del backend NestJS.
 *
 * Base URL configurada en [ApiClient]: `http://10.0.2.2:3000/` (dev con emulador).
 */
interface AuthApi {

    /**
     * Inicia sesión con email y contraseña.
     *
     * @return [LoginResponse] con `access_token` y datos del usuario.
     *         Errores: 401 (credenciales inválidas), 403 (cuenta bloqueada).
     */
    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    /**
     * Registra un nuevo usuario.
     *
     * @return [RegisterResponse] con mensaje de confirmación y userId.
     *         Errores: 400 (validación), 409 (email/username duplicado).
     */
    @POST("auth/registro")
    suspend fun register(@Body request: RegisterRequest): Response<RegisterResponse>
}
