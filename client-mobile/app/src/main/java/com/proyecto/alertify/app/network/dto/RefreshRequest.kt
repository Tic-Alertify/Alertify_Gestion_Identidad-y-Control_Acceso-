package com.proyecto.alertify.app.network.dto

import com.google.gson.annotations.SerializedName

/**
 * T10 – Request body para `POST /auth/refresh`.
 *
 * @param refreshToken Refresh token vigente emitido por el backend.
 */
data class RefreshRequest(
    @SerializedName("refresh_token") val refreshToken: String
)
