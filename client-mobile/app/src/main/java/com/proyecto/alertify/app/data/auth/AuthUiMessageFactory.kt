package com.proyecto.alertify.app.data.auth

import android.content.Context
import com.proyecto.alertify.app.R

/**
 * T12 – Convierte un [AuthError] de dominio en un mensaje legible para el usuario.
 *
 * Usa `strings.xml` para internacionalización. Si el error incluye un `requestId`,
 * lo añade como sufijo para que el usuario pueda reportarlo a soporte.
 */
object AuthUiMessageFactory {

    /**
     * @param context Contexto Android para acceder a recursos de strings.
     * @param error   Error de dominio devuelto por [AuthErrorMapper].
     * @return Mensaje listo para mostrar en Toast / Snackbar / Dialog.
     */
    fun toMessage(context: Context, error: AuthError): String {
        val base = when (error) {
            is AuthError.InvalidCredentials ->
                context.getString(R.string.error_auth_invalid_credentials)
            is AuthError.AccountBlocked ->
                context.getString(R.string.error_auth_account_blocked)
            is AuthError.AccountInactive ->
                context.getString(R.string.error_auth_account_inactive)
            is AuthError.ValidationError -> {
                val msgs = error.errors
                if (msgs.isNotEmpty()) msgs.joinToString("\n")
                else context.getString(R.string.error_auth_validation)
            }
            is AuthError.ConflictError ->
                error.detail ?: context.getString(R.string.error_auth_conflict)
            is AuthError.ServerError ->
                context.getString(R.string.error_auth_server)
            is AuthError.NetworkError ->
                context.getString(R.string.error_auth_network)
            is AuthError.UnknownError ->
                context.getString(R.string.error_auth_unknown)
        }

        val requestId = error.requestId
        return if (!requestId.isNullOrBlank()) {
            "$base\n${context.getString(R.string.error_support_code, requestId)}"
        } else {
            base
        }
    }
}
