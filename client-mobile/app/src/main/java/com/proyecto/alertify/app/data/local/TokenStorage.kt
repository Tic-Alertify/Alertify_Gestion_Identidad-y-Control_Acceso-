package com.proyecto.alertify.app.data.local

/**
 * Contrato para la persistencia local de tokens de autenticación.
 *
 * Centraliza las operaciones de lectura, escritura y eliminación de los
 * tokens de acceso y refresh. Toda clase que necesite acceder a los tokens
 * debe hacerlo a través de esta interfaz, evitando acoplamientos con la
 * implementación concreta.
 */
interface TokenStorage {

    /**
     * Persiste el token de acceso de forma local.
     *
     * @param token JWT recibido del backend tras un login exitoso.
     */
    suspend fun saveAccessToken(token: String)

    /**
     * Recupera el token de acceso almacenado localmente.
     *
     * @return El token si existe y no está vacío, o `null` en caso contrario.
     */
    suspend fun getAccessToken(): String?

    /**
     * Persiste el refresh token de forma local.
     *
     * @param token Refresh token recibido del backend tras login o refresh exitoso.
     */
    suspend fun saveRefreshToken(token: String)

    /**
     * Recupera el refresh token almacenado localmente.
     *
     * @return El token si existe y no está vacío, o `null` en caso contrario.
     */
    suspend fun getRefreshToken(): String?

    /**
     * Lectura síncrona del refresh token.
     *
     * Útil dentro del [okhttp3.Authenticator] que corre en hilo de OkHttp.
     *
     * @return El token si existe y no está vacío, o `null` en caso contrario.
     */
    fun getRefreshTokenSync(): String?

    /**
     * Elimina ambos tokens (access + refresh) del almacenamiento local.
     * Debe invocarse al cerrar sesión (logout) o cuando el refresh falla.
     */
    suspend fun clear()

    /**
     * Lectura síncrona del token de acceso.
     *
     * Útil en puntos donde no se puede usar coroutines (e.g. decisión de ruta
     * en `onCreate` antes de inflar UI). SharedPreferences almacena en memoria
     * tras la primera lectura, por lo que la operación es inmediata.
     *
     * @return El token si existe y no está vacío, o `null` en caso contrario.
     */
    fun getAccessTokenSync(): String?

    /**
     * Guardado síncrono del token de acceso.
     *
     * Útil dentro del [okhttp3.Authenticator] que corre en hilo de OkHttp.
     */
    fun saveAccessTokenSync(token: String)

    /**
     * Guardado síncrono del refresh token.
     *
     * Útil dentro del [okhttp3.Authenticator] que corre en hilo de OkHttp.
     */
    fun saveRefreshTokenSync(token: String)

    /**
     * Limpieza síncrona de ambos tokens.
     *
     * Útil dentro del [okhttp3.Authenticator] cuando el refresh falla.
     */
    fun clearSync()
}
