package com.proyecto.alertify.app.network.dto

import com.google.gson.annotations.SerializedName

/**
 * Response body de `POST /auth/login` (HTTP 200).
 *
 * @param accessToken JWT emitido por el backend.
 * @param user Datos públicos del usuario autenticado.
 */
data class LoginResponse(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("user") val user: UserDto
)

/**
 * Datos públicos del usuario incluidos en la respuesta de login.
 */
data class UserDto(
    @SerializedName("id") val id: Int,
    @SerializedName("email") val email: String,
    @SerializedName("username") val username: String,
    @SerializedName("roles") val roles: List<String>
)
