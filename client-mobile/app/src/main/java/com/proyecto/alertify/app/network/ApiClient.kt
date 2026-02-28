package com.proyecto.alertify.app.network

import com.proyecto.alertify.app.BuildConfig
import com.proyecto.alertify.app.data.local.TokenStorage
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Proveedor singleton del cliente Retrofit configurado para el backend Alertify.
 *
 * Centraliza la creación de [OkHttpClient] y [Retrofit] para que todas las capas
 * de la app compartan la misma instancia (pool de conexiones, interceptores, etc.).
 *
 * La base URL se lee de `BuildConfig.API_BASE_URL` (configurable en build.gradle.kts).
 */
object ApiClient {

    private const val TIMEOUT_SECONDS = 30L

    @Volatile
    private var retrofit: Retrofit? = null

    /**
     * Callback global que se invoca cuando el [TokenAuthenticator] detecta
     * que la sesión no se puede renovar. La Activity que se suscriba debe
     * navegar a Login y limpiar el back-stack.
     */
    var onSessionExpired: (() -> Unit)? = null

    /**
     * Retorna (o crea) la instancia singleton de [Retrofit].
     *
     * @param tokenStorage Requerido para el [AuthInterceptor] y [TokenAuthenticator].
     */
    fun getRetrofit(tokenStorage: TokenStorage): Retrofit {
        return retrofit ?: synchronized(this) {
            retrofit ?: buildRetrofit(tokenStorage).also { retrofit = it }
        }
    }

    /**
     * Atajo para obtener la interfaz [AuthApi] directamente.
     */
    fun getAuthApi(tokenStorage: TokenStorage): AuthApi {
        return getRetrofit(tokenStorage).create(AuthApi::class.java)
    }

    private fun buildRetrofit(tokenStorage: TokenStorage): Retrofit {
        val client = buildOkHttpClient(tokenStorage)

        return Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    private fun buildOkHttpClient(tokenStorage: TokenStorage): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .addInterceptor(AuthInterceptor(tokenStorage))

        // T10 – Authenticator para refresh token automático
        builder.authenticator(TokenAuthenticator(tokenStorage) {
            onSessionExpired?.invoke()
        })

        // Logger HTTP solo en debug — NUNCA loguea tokens ni bodies en release
        if (BuildConfig.DEBUG) {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }
            // Redactar header Authorization para no exponer token en logs
            logging.redactHeader("Authorization")
            builder.addInterceptor(logging)
        }

        return builder.build()
    }

    /**
     * Permite resetear la instancia (útil tras logout para limpiar conexiones
     * y forzar re-creación con estado limpio).
     */
    fun reset() {
        synchronized(this) {
            retrofit = null
        }
    }
}
