package com.proyecto.alertify.app.data.auth

import com.proyecto.alertify.app.data.local.TokenStorage

/**
 * Gestor centralizado de la sesión de autenticación.
 *
 * Encapsula la lógica de negocio sobre el estado de la sesión del usuario,
 * delegando la persistencia al [TokenStorage] subyacente.
 *
 * Uso típico:
 * ```
 * val manager = AuthSessionManager(tokenStorage)
 * if (manager.isLoggedIn()) { /* ir a Home */ }
 * ```
 *
 * @param tokenStorage Implementación de [TokenStorage] (inyectada por constructor).
 */
class AuthSessionManager(private val tokenStorage: TokenStorage) {

    /**
     * Persiste ambos tokens tras un login exitoso.
     *
     * @param accessToken  JWT de acceso devuelto por el backend.
     * @param refreshToken Refresh token devuelto por el backend.
     * @throws Exception si la persistencia falla (el llamador debe manejar el error).
     */
    suspend fun onLoginSuccess(accessToken: String, refreshToken: String) {
        tokenStorage.saveAccessToken(accessToken)
        tokenStorage.saveRefreshToken(refreshToken)
    }

    /**
     * Verifica si existe una sesión activa (token almacenado localmente).
     *
     * **Nota:** No valida la expiración del JWT; esa responsabilidad recae en el
     * backend y en el [TokenAuthenticator] que renueva automáticamente.
     *
     * @return `true` si hay un token no vacío almacenado.
     */
    suspend fun isLoggedIn(): Boolean {
        return tokenStorage.getAccessToken() != null
    }

    /**
     * Versión síncrona de [isLoggedIn] para uso en `onCreate` al decidir
     * la ruta de navegación inicial sin necesidad de coroutines.
     *
     * SharedPreferences mantiene los valores en caché en memoria tras la
     * primera lectura, por lo que esta lectura es instantánea.
     */
    fun isLoggedInSync(): Boolean {
        return tokenStorage.getAccessTokenSync() != null
    }

    /**
     * Obtiene el token de acceso actual, si existe.
     *
     * @return El JWT almacenado, o `null` si no hay sesión.
     */
    suspend fun getAccessToken(): String? {
        return tokenStorage.getAccessToken()
    }

    /**
     * Cierra la sesión del usuario eliminando ambos tokens almacenados.
     */
    suspend fun logout() {
        tokenStorage.clear()
    }
}
