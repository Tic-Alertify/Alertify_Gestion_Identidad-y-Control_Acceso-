package com.proyecto.alertify.app.network.dto

import com.google.gson.annotations.SerializedName

/**
 * T10 – Response body de `POST /auth/refresh` (HTTP 200).
 *
 * @param accessToken  Nuevo JWT de acceso.
 * @param refreshToken Nuevo refresh token (rotación).
 */
data class RefreshResponse(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("refresh_token") val refreshToken: String
)
