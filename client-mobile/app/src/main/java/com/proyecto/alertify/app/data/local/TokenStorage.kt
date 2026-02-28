package com.proyecto.alertify.app.data.local

/**
 * Contrato para la persistencia local de tokens de autenticación.
 *
 * Centraliza las operaciones de lectura, escritura y eliminación del token
 * de acceso (JWT). Toda clase que necesite acceder al token debe hacerlo
 * a través de esta interfaz, evitando acoplamientos con la implementación concreta.
 *
 * Puntos de extensión previstos:
 * - T10 (Refresh Token): agregar `saveRefreshToken`, `getRefreshToken`.
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
     * Elimina el token de acceso (y cualquier dato de sesión) del almacenamiento local.
     * Debe invocarse al cerrar sesión (logout).
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

    // TODO T10 – Refresh Token: agregar las siguientes operaciones cuando se implemente:
    // suspend fun saveRefreshToken(token: String)
    // suspend fun getRefreshToken(): String?
}
