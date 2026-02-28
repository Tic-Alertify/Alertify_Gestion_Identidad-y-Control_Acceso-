# AlertifyApp


---

## Persistencia de sesión (T14)

### Descripción
El token JWT (`access_token`) devuelto por el backend tras un login exitoso se almacena
localmente en `SharedPreferences` (`alertify_auth_prefs`, clave `access_token`, `MODE_PRIVATE`).
Esto permite que la sesión persista al cerrar y reabrir la aplicación.

### Arquitectura

| Capa | Clase | Responsabilidad |
|---|---|---|
| Contrato | `TokenStorage` | Interfaz: `saveAccessToken`, `getAccessToken`, `getAccessTokenSync`, `clear` |
| Persistencia | `SharedPrefsTokenStorage` | Implementación con SharedPreferences |
| Sesión | `AuthSessionManager` | Reglas: `isLoggedIn`, `isLoggedInSync`, `onLoginSuccess`, `logout` |
| Navegación | `NavigationHelper` | Transiciones Login ↔ Main con limpieza de back-stack |

### Seguridad
- El token **nunca** se loguea (`Log.d`, `println`, etc.).
- Se usa `MODE_PRIVATE`; el archivo de prefs solo es accesible por la app.
- No se almacena en variables estáticas globales.
- **TODO**: migrar a `EncryptedSharedPreferences` cuando se agregue `androidx.security:security-crypto`.

### Flujo de arranque
```
LoginActivity.onCreate()
  └─ isLoggedInSync()?
       ├─ true  → navigateToMain() + finish() (no infla UI de login)
       └─ false → muestra pantalla de login
```

### Integración con otras tareas
- **T13 (Login HTTP)**: Integrado — `LoginActivity` llama a `AuthApi.login()` / `AuthApi.register()` vía Retrofit. Al recibir `access_token` y `refresh_token`, invoca `onLoginSuccess(accessToken, refreshToken)` automáticamente.
- **T10 (Refresh Token)**: Implementado — ver sección T10 más abajo.

---

## Refresh Token automático (T10)

### Descripción
Cuando el `access_token` expira y una petición HTTP recibe `401 Unauthorized`,
OkHttp ejecuta automáticamente el flujo de refresh mediante `TokenAuthenticator`.
Si el refresh tiene éxito, la petición original se reintenta con el nuevo token.
Si falla, se cierra la sesión y se redirige al login.

### Arquitectura

| Capa | Clase | Responsabilidad |
|---|---|---|
| Authenticator | `TokenAuthenticator` | OkHttp `Authenticator`: detecta 401, llama `/auth/refresh`, reintenta |
| DTOs | `RefreshRequest`, `RefreshResponse` | Bodies JSON para el endpoint de refresh |
| Storage | `TokenStorage` | Extendido: `saveRefreshToken`, `getRefreshToken`, variantes `Sync`, `clearSync` |
| Persistencia | `SharedPrefsTokenStorage` | Implementación con SharedPreferences (clave `refresh_token`) |
| Sesión | `AuthSessionManager` | `onLoginSuccess(accessToken, refreshToken)`, `handleSessionExpired()` |
| Singleton | `ApiClient` | Registra `TokenAuthenticator` en OkHttpClient; expone `onSessionExpired` callback |
| Interceptor | `AuthInterceptor` | Excluye `/auth/refresh` de inyección de token (publicPaths) |
| Errores | `AuthErrorMapper` | Mapea `AUTH_REFRESH_INVALID` a mensaje localizado |

### Flujo de Refresh (automático)
```
Petición HTTP → 401 Unauthorized
  └─ TokenAuthenticator.authenticate()
       ├─ Anti-loop: verifica header X-Auth-Retry (evita refresh infinito)
       ├─ synchronized(lock): solo un thread hace refresh a la vez
       ├─ Lee refreshToken de SharedPreferences (Sync)
       ├─ POST /auth/refresh { refresh_token } (con refreshClient independiente)
       │    ├─ 200 → guarda nuevos tokens → reintenta petición original
       │    └─ Error → handleSessionExpired() → clearSync() → callback a LoginActivity
       └─ Si no hay refresh token → handleSessionExpired()
```

### Seguridad
- `TokenAuthenticator` usa un `OkHttpClient` independiente (sin interceptores ni authenticator) para evitar loops.
- Header `X-Auth-Retry` previene reintentos infinitos.
- `synchronized(lock)` evita múltiples refreshes concurrentes.
- Los tokens solo se acceden vía `SharedPreferences` `MODE_PRIVATE`.

### Nuevos archivos creados
- `network/TokenAuthenticator.kt`
- `network/dto/RefreshRequest.kt`
- `network/dto/RefreshResponse.kt`

### Archivos modificados
- `TokenStorage.kt` — nuevos métodos Sync para refresh token
- `SharedPrefsTokenStorage.kt` — implementación de los nuevos métodos
- `AuthSessionManager.kt` — `onLoginSuccess` acepta ambos tokens; `handleSessionExpired()`
- `ApiClient.kt` — registra `TokenAuthenticator`; expone `onSessionExpired`
- `AuthApi.kt` — `suspend fun refresh()`
- `AuthInterceptor.kt` — `/auth/refresh` en publicPaths
- `AuthErrorMapper.kt` — `AUTH_REFRESH_INVALID`
- `LoginActivity.kt` — guarda ambos tokens; conecta `onSessionExpired` callback
- `LoginResponse.kt` — campo `refreshToken` con `@SerializedName("refresh_token")`

---

## Capa de red – Integración con backend NestJS (T13)

### Descripción
La app se conecta al backend NestJS mediante **Retrofit 2.11.0** + **OkHttp 4.12.0** para
ejecutar login (`POST /auth/login`) y registro (`POST /auth/registro`) con peticiones HTTP reales.
El token JWT obtenido se persiste automáticamente a través de la infraestructura de T14.

### Arquitectura de red

| Capa | Clase | Responsabilidad |
|---|---|---|
| Interfaz API | `AuthApi` | Contrato Retrofit: `login()`, `register()` (funciones `suspend`) |
| DTOs request | `LoginRequest`, `RegisterRequest` | Bodies JSON con `@SerializedName` |
| DTOs response | `LoginResponse`, `RegisterResponse`, `ErrorResponse` | Mapeo JSON → Kotlin |
| Interceptor | `AuthInterceptor` | Inyecta `Authorization: Bearer <token>` (excluye rutas de auth) |
| Singleton | `ApiClient` | Provee `Retrofit` y `OkHttpClient` configurados (timeouts, logging) |

### Flujo de Login
```
LoginActivity → performLogin()
  └─ authApi.login(LoginRequest(email, password))
       ├─ HTTP 200 → onLoginSuccess(accessToken) → SharedPreferences → Main
       ├─ HTTP 4xx → parseErrorBody(json) → Toast con mensaje del backend
       └─ Exception → Toast "Error de conexión: ..."
```

### Flujo de Registro
```
LoginActivity → performRegister()
  └─ authApi.register(RegisterRequest(email, username, password))
       ├─ HTTP 201 → Toast "Registro exitoso" → clearFields → switch a login
       ├─ HTTP 4xx → parseErrorBody(json) → Toast con mensaje del backend
       └─ Exception → Toast "Error de conexión: ..."
```

### Manejo de errores
El backend devuelve `{ message, error, statusCode }`. `ErrorResponse.getDisplayMessage()` maneja:
- `message` como **String** → lo muestra directamente.
- `message` como **Array** (validaciones DTO) → une con salto de línea.

### Seguridad de red
- `network_security_config.xml` restringe tráfico cleartext exclusivamente a `10.0.2.2` y `localhost` (emulador ↔ backend local).
- El `AuthInterceptor` excluye `/auth/login` y `/auth/registro` de la inyección de token.
- `HttpLoggingInterceptor` solo activo en `BuildConfig.DEBUG`, con header `Authorization` redactado.

### Dependencias añadidas
```toml
# Retrofit + OkHttp
com.squareup.retrofit2:retrofit:2.11.0
com.squareup.retrofit2:converter-gson:2.11.0
com.squareup.okhttp3:okhttp:4.12.0
com.squareup.okhttp3:logging-interceptor:4.12.0
```

### Configuración
- Base URL: `BuildConfig.API_BASE_URL` → `http://10.0.2.2:3000/` (configurable en `app/build.gradle.kts`)
- Timeouts: 30 s (connect, read, write)
- Permiso: `INTERNET` declarado en `AndroidManifest.xml`

### Validación manual

1. **Persistencia de sesión**
   - Iniciar sesión (o simular con `onLoginSuccess("test-token")`) → cerrar app → reabrirla → abre `MainActivity` directamente (no pide login).

2. **Logout**
   - En `MainActivity`, pulsar "Cerrar sesión" → vuelve a `LoginActivity`.
   - Presionar botón "Back" → **no** regresa a `MainActivity` (back-stack limpio).
