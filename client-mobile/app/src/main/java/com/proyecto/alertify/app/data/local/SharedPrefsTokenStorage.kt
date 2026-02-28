package com.proyecto.alertify.app.data.local

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Implementación de [TokenStorage] respaldada por [SharedPreferences].
 *
 * Almacena los tokens de acceso y refresh en un archivo de preferencias privado
 * (`alertify_auth_prefs`) accesible únicamente por la aplicación.
 *
 * Notas de seguridad:
 * - Los tokens **nunca** se loguean ni se exponen en variables estáticas globales.
 * - Se utiliza [Context.MODE_PRIVATE] para restringir el acceso.
 *
 * @param context Contexto de la aplicación (preferir `applicationContext`).
 *
 * // TODO Seguridad: si se agrega la dependencia `androidx.security:security-crypto`,
 * //  reemplazar SharedPreferences por EncryptedSharedPreferences para cifrado AES-256
 * //  de los tokens en reposo. Ver:
 * //  https://developer.android.com/reference/androidx/security/crypto/EncryptedSharedPreferences
 */
class SharedPrefsTokenStorage(context: Context) : TokenStorage {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ── Access Token ────────────────────────────────────────────────────

    override suspend fun saveAccessToken(token: String) = withContext(Dispatchers.IO) {
        prefs.edit().putString(KEY_ACCESS_TOKEN, token).apply()
    }

    override suspend fun getAccessToken(): String? = withContext(Dispatchers.IO) {
        prefs.getString(KEY_ACCESS_TOKEN, null)?.takeIf { it.isNotBlank() }
    }

    override fun getAccessTokenSync(): String? {
        return prefs.getString(KEY_ACCESS_TOKEN, null)?.takeIf { it.isNotBlank() }
    }

    // ── Refresh Token ───────────────────────────────────────────────────

    override suspend fun saveRefreshToken(token: String) = withContext(Dispatchers.IO) {
        prefs.edit().putString(KEY_REFRESH_TOKEN, token).apply()
    }

    override suspend fun getRefreshToken(): String? = withContext(Dispatchers.IO) {
        prefs.getString(KEY_REFRESH_TOKEN, null)?.takeIf { it.isNotBlank() }
    }

    override fun getRefreshTokenSync(): String? {
        return prefs.getString(KEY_REFRESH_TOKEN, null)?.takeIf { it.isNotBlank() }
    }

    // ── Sync saves (para OkHttp Authenticator) ────────────────────────

    override fun saveAccessTokenSync(token: String) {
        prefs.edit().putString(KEY_ACCESS_TOKEN, token).apply()
    }

    override fun saveRefreshTokenSync(token: String) {
        prefs.edit().putString(KEY_REFRESH_TOKEN, token).apply()
    }

    // ── Clear ───────────────────────────────────────────────────────────

    override suspend fun clear() = withContext(Dispatchers.IO) {
        prefs.edit().clear().apply()
    }

    override fun clearSync() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val PREFS_NAME = "alertify_auth_prefs"
        const val KEY_ACCESS_TOKEN = "access_token"
        const val KEY_REFRESH_TOKEN = "refresh_token"
    }
}
