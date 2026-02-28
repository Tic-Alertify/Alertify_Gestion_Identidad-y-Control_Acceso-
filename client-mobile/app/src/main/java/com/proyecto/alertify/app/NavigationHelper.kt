package com.proyecto.alertify.app

import android.app.Activity
import android.content.Intent

/**
 * Utilidad de navegación centralizada para transiciones entre pantallas de autenticación.
 *
 * Evita duplicar lógica de Intent/flags en múltiples Activities.
 */
object NavigationHelper {

    /**
     * Navega a [MainActivity] limpiando el back-stack (el usuario no puede volver a Login con "atrás").
     */
    fun navigateToMain(activity: Activity) {
        val intent = Intent(activity, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        activity.startActivity(intent)
        activity.finish()
    }

    /**
     * Navega a [LoginActivity] limpiando el back-stack (usado tras logout).
     */
    fun navigateToLogin(activity: Activity) {
        val intent = Intent(activity, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        activity.startActivity(intent)
        activity.finish()
    }
}
