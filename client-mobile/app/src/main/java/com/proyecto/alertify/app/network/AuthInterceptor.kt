package com.proyecto.alertify.app.network

import com.proyecto.alertify.app.data.local.TokenStorage
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Interceptor OkHttp que inyecta el header `Authorization: Bearer <token>`
 * en cada request hacia endpoints protegidos.
 *
 * Excluye rutas públicas de autenticación para evitar enviar tokens innecesarios
 * (y para que login/registro funcionen sin sesión previa).
 *
 * @param tokenStorage Fuente del token almacenado localmente.
 */
class AuthInterceptor(private val tokenStorage: TokenStorage) : Interceptor {

    /** Rutas públicas que NO requieren Authorization header */
    private val publicPaths = listOf("/auth/login", "/auth/registro", "/auth/refresh")

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val path = request.url.encodedPath

        // No agregar header a endpoints públicos
        if (publicPaths.any { path.contains(it) }) {
            return chain.proceed(request)
        }

        // Lectura síncrona — el interceptor corre en el hilo de OkHttp (no main thread)
        val token = tokenStorage.getAccessTokenSync()

        val authenticatedRequest = if (!token.isNullOrBlank()) {
            request.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        } else {
            request
        }

        return chain.proceed(authenticatedRequest)
    }

}

