# AlertifyApp

## Sprint 2 — Frontend Autenticación y Tokens

| Tarea | Descripción | Estado |
|-------|------------|--------|
| T06 | UI formulario registro (Kotlin) | OK |
| T07 | Pruebas unitarias endpoint registro (backend Jest) | OK |
| T10 | Refresh token sesión persistente | OK |
| T11 | Validación estado cuenta | OK |
| T12 | Manejo errores autenticación | OK |
| T13 | UI pantalla login móvil | OK |
| T14 | Almacenamiento local token | OK |

---

## Interfaz de registro (T06)

### Descripción
El formulario de registro comparte layout con el login (`activity_login.xml`) mediante un sistema
de tabs que conmuta la visibilidad de campos. Al seleccionar la tab "Registro", se muestran los
campos adicionales de email y confirmación de contraseña.

### Elementos UI

| ID | Tipo | Modo Login | Modo Registro |
|---|---|---|---|
| `et_username_email` | `TextInputEditText` | hint "username/email" | hint "username" |
| `til_email` / `et_email` | `TextInputLayout` | `GONE` | `VISIBLE` — email dedicado |
| `til_password` / `et_password` | `TextInputLayout` | Visible | Visible |
| `til_confirm_password` / `et_confirm_password` | `TextInputLayout` | `GONE` | `VISIBLE` — confirmar contraseña |
| `tv_login_tab` / `tv_register_tab` | `TextView` | Tab activa: Login | Tab activa: Registro |
| `btn_action` | `Button` | Texto: "Login" | Texto: "Register" |
| `tv_forgot_password` | `TextView` | Visible | `GONE` |

### Validaciones en `performRegister()`
1. **Campos vacíos**: verifica username, email, password y confirmPassword no blank → Toast `error_empty_fields`
2. **Passwords mismatch**: `password != confirmPassword` → Toast `error_passwords_mismatch`
3. **Llamada API**: `authApi.register(RegisterRequest(email, username, password))`
4. **Éxito (201)**: Toast "Registro exitoso" → limpia campos → switch a modo login
5. **Error (4xx/5xx)**: `AuthErrorMapper.map()` → `AuthUiMessageFactory.toMessage()` → Toast

### Toggle de modo
`updateUIMode(isLoginMode: Boolean)` controla:
- Visibilidad de campos email y confirm-password
- Texto del botón de acción
- Hint del campo username/email
- Estilo visual de los tabs (bold / normal)
- Link "Olvidaste tu contraseña" y contenedor social logins


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

## Validación de estado de cuenta (T11)

### Descripción
El backend valida el estado de la cuenta del usuario tanto en login como en refresh.
Los códigos de error diferenciados permiten al cliente Android mostrar mensajes específicos.

### Estados soportados

| Estado BD | Código backend | HTTP | `AuthError` Android | Mensaje usuario |
|-----------|---------------|------|--------------------|-----------------|
| `bloqueado` | `AUTH_ACCOUNT_BLOCKED` | 403 | `AccountBlocked` | `error_auth_account_blocked` |
| `inactivo` | `AUTH_ACCOUNT_INACTIVE` | 403 | `AccountInactive` | `error_auth_account_inactive` |
| otro no-activo | `AUTH_ACCOUNT_INACTIVE` | 403 | `AccountInactive` | `error_auth_account_inactive` |
| `activo` | — | — | — | Acceso permitido |

### Flujo end-to-end
```
Backend: estado = (usuario.estado ?? '').trim().toLowerCase()
  ├─ 'bloqueado' → ForbiddenException { code: AUTH_ACCOUNT_BLOCKED }
  ├─ 'inactivo'  → ForbiddenException { code: AUTH_ACCOUNT_INACTIVE }
  ├─ != 'activo' → ForbiddenException { code: AUTH_ACCOUNT_INACTIVE }
  └─ 'activo'    → continuar login/refresh

Android: AuthErrorMapper.map(apiError, 403, null)
  ├─ code = AUTH_ACCOUNT_BLOCKED  → AuthError.AccountBlocked
  ├─ code = AUTH_ACCOUNT_INACTIVE → AuthError.AccountInactive
  └─ sin code + 403 → heurística por texto en message

AuthUiMessageFactory.toMessage(context, error)
  → String localizado de strings.xml + requestId
```

---

## Manejo de errores de autenticación (T12)

### Descripción
Sistema de manejo de errores en 3 capas que traduce respuestas HTTP del backend
en mensajes legibles para el usuario, manteniendo trazabilidad con `requestId`.

### Arquitectura

```
Backend HTTP Response
  │  { statusCode, message, error, code, path, requestId, timestamp }
  ▼
AuthErrorMapper.map(apiError, httpCode, throwable)
  │  Prioridad: code → excepción → httpCode → heurística
  ▼
AuthError (sealed class)
  │  InvalidCredentials | AccountBlocked | AccountInactive |
  │  ValidationError | ConflictError | ServerError |
  │  NetworkError | UnknownError
  ▼
AuthUiMessageFactory.toMessage(context, error)
  │  String localizado + requestId como código de soporte
  ▼
Toast.makeText(context, message, LENGTH_LONG)
```

### Mapeo de códigos

| Código Backend | HTTP | `AuthError` | String resource |
|---------------|------|------------|----------------|
| `AUTH_INVALID_CREDENTIALS` | 401 | `InvalidCredentials` | `error_auth_invalid_credentials` |
| `AUTH_REFRESH_INVALID` | 401 | `InvalidCredentials` | `error_auth_invalid_credentials` |
| `AUTH_ACCOUNT_BLOCKED` | 403 | `AccountBlocked` | `error_auth_account_blocked` |
| `AUTH_ACCOUNT_INACTIVE` | 403 | `AccountInactive` | `error_auth_account_inactive` |
| `AUTH_UNEXPECTED_ERROR` | 500 | `ServerError` | `error_auth_server` |
| `VALIDATION_ERROR` | 400 | `ValidationError` | Mensajes del backend o `error_auth_validation` |
| `RESOURCE_CONFLICT` | 409 | `ConflictError` | Detalle del backend o `error_auth_conflict` |
| `IOException` | — | `NetworkError` | `error_auth_network` |
| Otro | — | `UnknownError` | `error_auth_unknown` |

### Fallback por HTTP status (sin campo `code`)
- 400 → `ValidationError`
- 401 → `InvalidCredentials`
- 403 → heurística: busca "bloquead" o "inactiv" en `message`
- 409 → `ConflictError`
- 500+ → `ServerError`

### Archivos involucrados
- `data/auth/AuthError.kt` — sealed class con variantes
- `data/auth/AuthErrorMapper.kt` — mapeo API → dominio
- `data/auth/AuthUiMessageFactory.kt` — dominio → string localizado
- `res/values/strings.xml` — mensajes de error en español

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
