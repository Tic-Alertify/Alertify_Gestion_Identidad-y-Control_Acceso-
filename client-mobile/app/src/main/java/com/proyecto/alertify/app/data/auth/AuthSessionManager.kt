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
     * Persiste el token de acceso tras un login exitoso.
     *
     * @param accessToken JWT devuelto por el backend.
     * @throws Exception si la persistencia falla (el llamador debe manejar el error).
     */
    suspend fun onLoginSuccess(accessToken: String) {
        tokenStorage.saveAccessToken(accessToken)
    }

    /**
     * Verifica si existe una sesión activa (token almacenado localmente).
     *
     * **Nota:** No valida la expiración del JWT; esa responsabilidad recae en el
     * backend o en la futura implementación de refresh token (T10).
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
     * Cierra la sesión del usuario eliminando el token almacenado.
     */
    suspend fun logout() {
        tokenStorage.clear()
    }

    // TODO T10 – Refresh Token:
    //  - Agregar lógica para verificar expiración del access token (decodificar JWT exp).
    //  - Invocar endpoint de refresh y actualizar el token almacenado.
    //  - Manejar caso de refresh token expirado → forzar logout.
}
