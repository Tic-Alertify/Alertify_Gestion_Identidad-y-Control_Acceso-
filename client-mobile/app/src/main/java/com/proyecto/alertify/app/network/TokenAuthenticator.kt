package com.proyecto.alertify.app.network

import android.util.Log
import com.google.gson.Gson
import com.proyecto.alertify.app.BuildConfig
import com.proyecto.alertify.app.data.local.TokenStorage
import com.proyecto.alertify.app.network.dto.RefreshRequest
import com.proyecto.alertify.app.network.dto.RefreshResponse
import okhttp3.Authenticator
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route
import java.util.concurrent.TimeUnit

/**
 * T10 – OkHttp [Authenticator] que intercepta respuestas 401 y solicita
 * un nuevo access token usando el refresh token almacenado localmente.
 *
 * Características:
 * - **Anti-loop:** Si la request ya tiene el header `X-Auth-Retry`, no reintenta.
 * - **Concurrencia:** Un [lock] evita que múltiples requests en paralelo disparen
 *   refresh simultáneos; la primera que obtiene el lock hace el refresh y las demás
 *   reutilizan el nuevo token.
 * - **Rotación:** Guarda los nuevos tokens (access + refresh) en [TokenStorage].
 * - **Sesión expirada:** Si el refresh falla (401/403), limpia la sesión
 *   y emite un evento vía [onSessionExpired] para navegar al login.
 *
 * @param tokenStorage  Almacenamiento local de tokens.
 * @param onSessionExpired Callback invocado cuando la sesión no se puede renovar.
 */
class TokenAuthenticator(
    private val tokenStorage: TokenStorage,
    private val onSessionExpired: () -> Unit
) : Authenticator {

    private val gson = Gson()
    private val lock = Any()

    /**
     * Cliente OkHttp independiente (sin interceptores ni authenticator) para
     * evitar loops recursivos al llamar al endpoint de refresh.
     */
    private val refreshClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    override fun authenticate(route: Route?, response: Response): Request? {
        // 1. Anti-loop: si ya se reintentó, no volver a intentar
        if (response.request.header(HEADER_AUTH_RETRY) != null) {
            Log.d(TAG, "Retry ya ejecutado; no reintentar refresh")
            return null
        }

        synchronized(lock) {
            // 2. Obtener refresh token actual
            val currentRefreshToken = tokenStorage.getRefreshTokenSync()
            if (currentRefreshToken.isNullOrBlank()) {
                Log.d(TAG, "No hay refresh token; limpiar sesión")
                handleSessionExpired()
                return null
            }

            // 3. Verificar si otro hilo ya hizo refresh mientras esperábamos el lock
            val currentAccessToken = tokenStorage.getAccessTokenSync()
            val failedAccessToken = response.request.header("Authorization")
                ?.removePrefix("Bearer ")?.trim()

            if (currentAccessToken != null && currentAccessToken != failedAccessToken) {
                // Otro hilo ya refrescó; reintentar con el nuevo token
                Log.d(TAG, "Token ya actualizado por otro hilo; reintentar request")
                return response.request.newBuilder()
                    .header("Authorization", "Bearer $currentAccessToken")
                    .header(HEADER_AUTH_RETRY, "1")
                    .build()
            }

            // 4. Ejecutar refresh
            return try {
                val newTokens = executeRefresh(currentRefreshToken)
                if (newTokens != null) {
                    // Guardar nuevos tokens de forma síncrona (estamos en hilo OkHttp)
                    saveTokensSync(newTokens.accessToken, newTokens.refreshToken)
                    Log.d(TAG, "Refresh exitoso; reintentando request original")

                    response.request.newBuilder()
                        .header("Authorization", "Bearer ${newTokens.accessToken}")
                        .header(HEADER_AUTH_RETRY, "1")
                        .build()
                } else {
                    handleSessionExpired()
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error durante refresh", e)
                handleSessionExpired()
                null
            }
        }
    }

    /**
     * Ejecuta la llamada HTTP de refresh usando el [refreshClient] limpio.
     *
     * @return [RefreshResponse] si fue exitoso, o `null` si falló (401/403/error).
     */
    private fun executeRefresh(refreshToken: String): RefreshResponse? {
        val requestBody = RefreshRequest(refreshToken)
        val json = gson.toJson(requestBody)
        val mediaType = "application/json; charset=utf-8".toMediaType()

        val request = Request.Builder()
            .url("${BuildConfig.API_BASE_URL}auth/refresh")
            .post(json.toRequestBody(mediaType))
            .build()

        val response = refreshClient.newCall(request).execute()

        return if (response.isSuccessful) {
            val body = response.body?.string()
            if (body != null) {
                gson.fromJson(body, RefreshResponse::class.java)
            } else {
                null
            }
        } else {
            Log.w(TAG, "Refresh falló con HTTP ${response.code}")
            null
        }
    }

    /**
     * Guarda ambos tokens usando las APIs síncronas de [TokenStorage].
     * Se ejecuta dentro del bloque synchronized, en el hilo de OkHttp.
     */
    private fun saveTokensSync(accessToken: String, refreshToken: String) {
        tokenStorage.saveAccessTokenSync(accessToken)
        tokenStorage.saveRefreshTokenSync(refreshToken)
    }

    private fun handleSessionExpired() {
        tokenStorage.clearSync()
        onSessionExpired()
    }

    companion object {
        private const val TAG = "TokenAuthenticator"
        const val HEADER_AUTH_RETRY = "X-Auth-Retry"
    }
}
