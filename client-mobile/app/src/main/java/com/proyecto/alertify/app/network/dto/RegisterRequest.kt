package com.proyecto.alertify.app.network.dto

import com.google.gson.annotations.SerializedName

/**
 * Request body para `POST /auth/registro`.
 *
 * @param email Correo electrónico (único).
 * @param username Nombre de usuario (4-20 chars, alfanumérico, único).
 * @param password Contraseña (≥8 chars, al menos 1 mayúscula y 1 dígito).
 */
data class RegisterRequest(
    @SerializedName("email") val email: String,
    @SerializedName("username") val username: String,
    @SerializedName("password") val password: String
)
