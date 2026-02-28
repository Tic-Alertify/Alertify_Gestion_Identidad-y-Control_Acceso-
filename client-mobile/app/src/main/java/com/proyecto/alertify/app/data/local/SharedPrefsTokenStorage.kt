package com.proyecto.alertify.app.data.local

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Implementación de [TokenStorage] respaldada por [SharedPreferences].
 *
 * Almacena el token de acceso JWT en un archivo de preferencias privado
 * (`alertify_auth_prefs`) accesible únicamente por la aplicación.
 *
 * Notas de seguridad:
 * - El token **nunca** se loguea ni se expone en variables estáticas globales.
 * - Se utiliza [Context.MODE_PRIVATE] para restringir el acceso.
 *
 * @param context Contexto de la aplicación (preferir `applicationContext`).
 *
 * // TODO Seguridad: si se agrega la dependencia `androidx.security:security-crypto`,
 * //  reemplazar SharedPreferences por EncryptedSharedPreferences para cifrado AES-256
 * //  del token en reposo. Ver:
 * //  https://developer.android.com/reference/androidx/security/crypto/EncryptedSharedPreferences
 */
class SharedPrefsTokenStorage(context: Context) : TokenStorage {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override suspend fun saveAccessToken(token: String) = withContext(Dispatchers.IO) {
        prefs.edit().putString(KEY_ACCESS_TOKEN, token).apply()
    }

    override suspend fun getAccessToken(): String? = withContext(Dispatchers.IO) {
        prefs.getString(KEY_ACCESS_TOKEN, null)?.takeIf { it.isNotBlank() }
    }

    override suspend fun clear() = withContext(Dispatchers.IO) {
        prefs.edit().clear().apply()
    }

    override fun getAccessTokenSync(): String? {
        return prefs.getString(KEY_ACCESS_TOKEN, null)?.takeIf { it.isNotBlank() }
    }

    // TODO T10 – Refresh Token: implementar saveRefreshToken / getRefreshToken
    //  usando la clave KEY_REFRESH_TOKEN en las mismas SharedPreferences.

    private companion object {
        const val PREFS_NAME = "alertify_auth_prefs"
        const val KEY_ACCESS_TOKEN = "access_token"
        // const val KEY_REFRESH_TOKEN = "refresh_token"  // T10
    }
}
