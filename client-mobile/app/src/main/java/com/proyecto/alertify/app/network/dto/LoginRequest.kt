package com.proyecto.alertify.app.network.dto

import com.google.gson.annotations.SerializedName

/**
 * Request body para `POST /auth/login`.
 *
 * @param email Correo electrónico del usuario.
 * @param password Contraseña en texto plano (se envía sobre HTTPS en producción).
 */
data class LoginRequest(
    @SerializedName("email") val email: String,
    @SerializedName("password") val password: String
)
