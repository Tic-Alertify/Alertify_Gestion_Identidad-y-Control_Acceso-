package com.proyecto.alertify.app.network.dto

import com.google.gson.annotations.SerializedName

/**
 * Response body de `POST /auth/registro` (HTTP 201).
 *
 * @param message Mensaje de confirmación del backend.
 * @param userId ID del usuario recién creado.
 */
data class RegisterResponse(
    @SerializedName("message") val message: String,
    @SerializedName("userId") val userId: Int
)
