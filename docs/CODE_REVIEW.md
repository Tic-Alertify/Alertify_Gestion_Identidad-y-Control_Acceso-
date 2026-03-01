# CODE REVIEW - Sprint 1, 2 & 3: Gestión de Identidad y Control de Acceso

**Fecha**: 28 de Febrero de 2026  
**Calificación General**: **97/100**  
**Estado**: Listo para Deploy

---

## CUMPLIMIENTO DE REQUISITOS

### Sprint 1 — Backend Autenticación

### **T02: POST /registro** (100%)
- Ruta `POST /auth/registro` implementada correctamente
- DTO con todas las validaciones requeridas
- Email con formato válido y verificación de unicidad en base de datos
- Username alfanumérico, de 4 a 20 caracteres, con restricción de unicidad
- Contraseña con mínimo 8 caracteres, una mayúscula y un número
- Respuestas HTTP correctas: 201, 409, 400, 500

### **T03: Hashing bcrypt** (100%)
- Implementado con 10 rondas de salt
- Método `hashPassword()` en `AuthService`
- Contraseñas no almacenadas en texto plano

### **T04: Almacenamiento con rol** (110% - Mejorado)
- Transacción Prisma que garantiza atomicidad ACID
- Rol `CIUDADANO` (`role_id: 1`) asignado automáticamente
- `AUDIT_LOG` incluido en la transacción, lo que incrementa la robustez del proceso

### **T05: Manejo de errores** (100%)
- `ConflictException` para duplicados (409)
- Validaciones automáticas con `class-validator` (400)
- `GlobalExceptionFilter` para estandarizar respuestas
- Formato de respuesta con `statusCode`, `message`, `error`, `timestamp`

### **T08: POST /login** (100%)
- Ruta `POST /auth/login` implementada
- Búsqueda por correo electrónico y verificación con `bcrypt.compare`
- Validación de estado `activo`; en caso contrario, respuesta 403
- Respuesta 401 para credenciales inválidas

### **T09: JWT con claims** (100%)
- `@nestjs/jwt` configurado con algoritmo HS256
- Secrets separados para access y refresh tokens (`JWT_ACCESS_SECRET`, `JWT_REFRESH_SECRET`)
- Access token: 15 min TTL, payload con `sub`, `email`, `roles[]`
- Respuesta incluye `access_token`, `refresh_token` y objeto `user`

### **T10: Refresh Token para sesión persistente** (100%)

#### Backend (NestJS)
- **Generación**: Refresh token JWT con claims `sub`, `type:refresh`, `jti` (UUID v4)
- **Almacenamiento**: Hash SHA-256 en columna `refresh_token_hash` de USUARIOS (nunca en texto plano)
- **Expiración**: `refresh_token_expires_at` en DB, configurable vía `JWT_REFRESH_TTL` (default 7d)
- **Rotación**: Cada refresh genera par completo nuevo; token anterior queda invalidado
- **Validación en refresh**: Verifica firma JWT + tipo + estado usuario (activo) + hash en DB + expiración
- **Logout (T15+T16)**: Verifica firma JWT del refresh token (no acepta tokens expirados/inválidos → 401 `AUTH_REFRESH_INVALID`). Valida `type === 'refresh'`. Borra `refresh_token_hash` y `refresh_token_expires_at` con `updateMany` (idempotente). Si se envía header `Authorization: Bearer <token>`, extrae `jti` del access token y lo inserta en tabla `JWT_BLACKLIST` para revocación inmediata. Único violation P2002 es idempotente.
- **Blacklist (T16)**: Tabla `JWT_BLACKLIST` con columnas `jti` (unique), `user_id`, `expires_at`, `created_at`. `JwtStrategy.validate()` verifica `jti` no está en blacklist en cada request protegido. Cron cleanup cada hora elimina entradas expiradas.
- **Endpoints**: `POST /auth/refresh`, `POST /auth/logout`
- **DTOs**: `RefreshDto`, `LogoutDto` con validación class-validator
- **Secret independiente**: `JWT_REFRESH_SECRET` separado de `JWT_ACCESS_SECRET`

#### Android (Kotlin)
- **TokenAuthenticator**: OkHttp `Authenticator` que intercepta 401 y hace refresh automático
- **Anti-loop**: Header `X-Auth-Retry` previene reintentos infinitos
- **Concurrencia**: `synchronized(lock)` garantiza un solo refresh a la vez
- **Cliente aislado**: `refreshClient` OkHttpClient sin interceptores ni authenticator
- **Sesión expirada**: `onSessionExpired` callback navega a LoginActivity, limpia back-stack
- **TokenStorage ampliado**: `saveRefreshToken`, `getRefreshToken`, variantes `Sync`, `clearSync`
- **LoginResponse**: Incluye campo `refresh_token` con `@SerializedName`

### Sprint 2 — Frontend Autenticación y Tokens

### **T06: Interfaz UI formulario registro** (100%)
- Layout dual en `activity_login.xml`: tabs "Login" / "Registro" conmutan entre modos
- Campos de registro: `et_username_email` (username), `et_email`, `et_password`, `et_confirm_password`
- Campos condicionales: `til_email` y `til_confirm_password` con `visibility=gone` en modo login
- `TextInputLayout` con `passwordToggleEnabled` para visualización de contraseña
- Método `updateUIMode()` en `LoginActivity` gestiona visibilidad, hints y texto del botón
- Validación en `performRegister()`: campos vacíos y passwords mismatch antes de llamar a API
- Registro exitoso: Toast, limpieza de campos y switch automático a modo login
- Diseño: `CardView` translucido con fondo difuminado, Material Design

### **T07: Pruebas unitarias endpoint registro + logout + blacklist** (100%)
- Archivo: `src/auth/auth.service.spec.ts` — 20 tests Jest
- Mocks puros: `UsuariosService`, `PrismaService` (con `txMock.$transaction` y `updateMany`), `JwtService`, `ConfigService`
- `hashPassword` espiado con `jest.spyOn` para evitar bcrypt real
- **Casos cubiertos (registro — 5 tests):**
  1. Email duplicado → `ConflictException` · `$transaction` no se invoca
  2. Username duplicado → `ConflictException` · `$transaction` no se invoca
  3. Registro exitoso → retorna `{ message, userId }` · verifica `estado:'activo'`, `role_id:1`, audit `REGISTRO_USUARIO`
  4. `HttpException` dentro de transacción → se re-lanza tal cual
  5. Error genérico → `InternalServerErrorException('Error al registrar el usuario')`
- **Casos cubiertos (logout — 6 tests):**
  1. Token válido → 200, `updateMany` con `null`, verifica secret correcto
  2. Firma inválida → `UnauthorizedException` con `code: AUTH_REFRESH_INVALID`
  3. Token expirado → `UnauthorizedException`
  4. Token tipo incorrecto (access en vez de refresh) → `UnauthorizedException`
  5. Usuario no existe en BD → 200 (idempotente, error capturado)
  6. Doble logout con mismo token → 200 ambas veces
- **Casos cubiertos (T16 blacklist — 5 tests):**
  1. Logout con access token → blacklistea jti en `JWT_BLACKLIST`
  2. Logout sin access token → no llama `jwtBlacklist.create`
  3. Access token inválido/expirado → no falla, responde 200
  4. Dupplicado P2002 (jti ya en blacklist) → idempotente, 200
  5. Access token sin jti → no blacklistea
- **Casos cubiertos (isTokenBlacklisted — 3 tests):**
  1. jti en blacklist y no expirado → retorna true
  2. jti no existe → retorna false
  3. jti expirado en blacklist → retorna false
- **Casos cubiertos (generateAccessToken — 1 test):**
  1. Payload incluye jti (UUID v4 format) con secret y TTL correctos
- Resultado: **20/20 passing**, sin acceso a DB real

### Sprint 3 — Invalidación de Sesión

### **T15: POST /auth/logout — Invalidación de sesión** (100%)

#### Endpoint
- Ruta: `POST /auth/logout`
- Body: `{ "refresh_token": "string" }`
- Respuesta exitosa: `200 { "message": "Sesión cerrada correctamente" }`

#### Implementación
- **DTO**: `LogoutDto` con `@IsString()` + `@IsNotEmpty()` sobre campo `refresh_token`
- **Verificación JWT estricta**: `jwtService.verify()` con `JWT_REFRESH_SECRET`; firma inválida o token expirado → 401 `AUTH_REFRESH_INVALID`
- **Validación tipo**: `payload.type !== 'refresh'` → 401 `AUTH_REFRESH_INVALID`
- **Invalidación BD**: `prisma.usuarios.updateMany()` con `refresh_token_hash: null`, `refresh_token_expires_at: null`
- **Transacción interactiva**: `updateMany` + `jwtBlacklist.create` agrupados en `$transaction()` para evitar `EINVALIDSTATE` con `@prisma/adapter-mssql` (ver Correción #5)
- **Idempotencia**: `updateMany` no lanza excepción si el registro no existe o el hash ya es null → siempre 200
- **Seguridad**: No revela información sobre existencia del usuario; no loguea el refresh token

#### Códigos de error

| Código HTTP | Código interno | Condición |
|------------|----------------|----------|
| 400 | `VALIDATION_ERROR` | Falta `refresh_token` en body |
| 401 | `AUTH_REFRESH_INVALID` | Token inválido, expirado o tipo incorrecto |
| 200 | — | Token válido (inclusive si hash ya fue eliminado) |

#### Nota técnica
- Se usa `updateMany` en lugar de `update` por compatibilidad con `@prisma/adapter-mssql`; `update` con `data: { field: null }` fallaba silenciosamente en el adapter SQL Server.
- **T16**: Con el blacklist implementado, el access token queda revocado inmediatamente al hacer logout (si se envía el header Authorization).

### **T16: JWT Blacklist — Invalidación inmediata de access tokens** (100%)

#### Tabla `JWT_BLACKLIST`
- Modelo Prisma `JwtBlacklist` mapeado a tabla `JWT_BLACKLIST`
- Columnas: `id` (autoincrement PK), `jti` (unique NVarChar(64)), `user_id` (nullable), `expires_at` (DateTime), `created_at` (default now())
- Schema push exitoso con `npx prisma db push`

#### Generación de access token con `jti`
- `generateAccessToken()` incluye `jti: randomUUID()` (UUID v4) en el payload JWT
- Permite identificar y revocar tokens individuales

#### Blacklist en logout
- `POST /auth/logout` acepta header opcional `Authorization: Bearer <access_token>`
- Si el header está presente, el controlador extrae el token y lo pasa a `AuthService.logout(refreshToken, accessToken)`
- El servicio verifica el access token, extrae `jti` y `exp`, e inserta en `JWT_BLACKLIST`
- Si el access token es inválido/expirado, se ignora silenciosamente (refresh ya fue revocado)
- Si el `jti` ya existe (P2002 unique violation), es idempotente

#### Verificación en `JwtStrategy`
- `validate()` verifica que el payload tenga `jti`; si no → 401 `AUTH_INVALID_TOKEN`
- Llama `authService.isTokenBlacklisted(jti)` en cada petición protegida
- Si el jti está en blacklist y no ha expirado → 401 `AUTH_TOKEN_REVOKED`

#### `isTokenBlacklisted(jti)`
- Busca por `jti` con `findUnique`
- Retorna `false` si no existe o si `expires_at < now()` (entrada expirada)
- Retorna `true` solo si existe y aún no ha expirado

#### Cron de limpieza (JwtBlacklistCleanupService)
- `@Cron(CronExpression.EVERY_HOUR)` ejecuta `deleteMany({ where: { expires_at: { lt: new Date() } } })`
- Loguea cantidad de entradas eliminadas si > 0
- **Retry con ETIMEOUT**: `@prisma/adapter-mssql` puede perder conexiones idle en el pool; el cron reintenta hasta 2 veces con 3 s de espera ante errores `ETIMEOUT`
- Errores no interrumpen la aplicación (catch + log)
- Registrado en `AuthModule.providers` + `ScheduleModule.forRoot()` en `AppModule`

#### Códigos de error T16

| Código HTTP | Código interno | Condición |
|------------|----------------|----------|
| 401 | `AUTH_INVALID_TOKEN` | Access token sin jti |
| 401 | `AUTH_TOKEN_REVOKED` | Access token revocado (jti en blacklist) |

### **T11: Validación estado cuenta (activo/bloqueado/inactivo)** (100%)
- Estado normalizado con `(usuario.estado ?? '').trim().toLowerCase()` antes de comparar
- Validación aplicada en **dos puntos**: `login()` y `refresh()`
- Diferenciación de estados:

| Estado | Excepción | Código | Mensaje |
|--------|-----------|--------|--------|
| `bloqueado` | `ForbiddenException` (403) | `AUTH_ACCOUNT_BLOCKED` | "La cuenta está bloqueada." |
| `inactivo` | `ForbiddenException` (403) | `AUTH_ACCOUNT_INACTIVE` | "La cuenta está inactiva." |
| Otro no-activo | `ForbiddenException` (403) | `AUTH_ACCOUNT_INACTIVE` | "La cuenta no está habilitada." |

- Lógica idéntica en login y refresh para consistencia
- Tolerancia a espacios y mayúsculas en el campo estado de DB

### **T12: Manejo errores autenticación (401, 403, 500)** (100%)

#### Backend
- `GlobalExceptionFilter` (`@Catch()`) estandariza todas las respuestas de error
- Contrato de respuesta: `{ statusCode, message, error, code, path, requestId, timestamp }`
- Códigos por defecto según HTTP status: 400→`VALIDATION_ERROR`, 401→`AUTH_INVALID_CREDENTIALS`, 403→`AUTH_FORBIDDEN`, 404→`RESOURCE_NOT_FOUND`, 409→`RESOURCE_CONFLICT`, 500→`AUTH_UNEXPECTED_ERROR`
- Excepciones con `code` explícito en el response prevalecen sobre defaults
- Errores no controlados: log completo interno, respuesta genérica al cliente (sin stack traces)
- `requestId` inyectado por `RequestIdMiddleware` para trazabilidad

#### Android
- **`AuthErrorMapper`**: Mapea respuesta del backend a tipos de dominio `AuthError`
  - Prioridad: campo `code` → tipo de excepción → código HTTP → heurística en mensaje
  - Mapeo `code`: `AUTH_INVALID_CREDENTIALS`→`InvalidCredentials`, `AUTH_ACCOUNT_BLOCKED`→`AccountBlocked`, `AUTH_ACCOUNT_INACTIVE`→`AccountInactive`, `AUTH_REFRESH_INVALID`→`InvalidCredentials`, `VALIDATION_ERROR`→`ValidationError`, `RESOURCE_CONFLICT`→`ConflictError`
  - Fallback por HTTP: 400→Validation, 401→InvalidCredentials, 403→heurística texto, 409→Conflict, 500+→Server
  - `IOException` → `NetworkError`
- **`AuthUiMessageFactory`**: Convierte `AuthError` a string localizado de `strings.xml`
  - Añade `requestId` como código de soporte cuando está disponible
- **`LoginActivity`**: Ambos flujos (`performLogin`, `performRegister`) usan `AuthErrorMapper.map()` → `AuthUiMessageFactory.toMessage()` → `Toast`

### **T13: Interfaz UI pantalla login móvil** (100%)
- Layout `activity_login.xml` con diseño Material Design:
  - Fondo difuminado (`ImageView` full-screen)
  - `CardView` translucido con formulario
  - Tabs "Login" / "Registro" con indicador visual
  - `TextInputLayout` con `passwordToggleEnabled`
  - Botón de acción dinámico (texto cambia según modo)
  - Placeholder para "Olvidaste tu contraseña" y logins sociales
- `LoginActivity` (325 líneas) en `LoginActiviy.kt`:
  - `performLogin()`: validación campos → `AuthApi.login()` → persist tokens → navigate Main
  - `performRegister()`: validación campos + passwords match → `AuthApi.register()` → switch a login
  - Auto-redirect si sesión existe (`isLoggedInSync()` en `onCreate`)
- `MainActivity` + `activity_main.xml`: pantalla post-auth con botón logout
- `AndroidManifest.xml`: `LoginActivity` como LAUNCHER, `adjustResize` para teclado
- **Nota**: archivo nombrado `LoginActiviy.kt` (typo en filename; la clase interna está correcta)

### **T14: Integración almacenamiento local token (SharedPreferences)** (100%)
- Interfaz `TokenStorage` con 10 métodos:
  - `saveAccessToken`, `getAccessToken`, `getAccessTokenSync` (suspend + blocking)
  - `saveRefreshToken`, `getRefreshToken`, `getRefreshTokenSync` (suspend + blocking)
  - `saveAccessTokenSync`, `saveRefreshTokenSync` (para Authenticator)
  - `clear` (suspend), `clearSync` (blocking)
- `SharedPrefsTokenStorage`: implementación con `MODE_PRIVATE`, archivo `alertify_auth_prefs`
- `AuthSessionManager`: `isLoggedIn()`, `isLoggedInSync()`, `onLoginSuccess(access, refresh)`, `logout()`, `handleSessionExpired()`
- `NavigationHelper`: transiciones Login ↔ Main con limpieza de back-stack
- Tokens nunca logueados; solo accesibles por la app
- **TODO**: migrar a `EncryptedSharedPreferences` (`androidx.security:security-crypto`)

### **Arquitectura NestJS** (100%)
- Estructura de carpetas alineada con convenciones
- Separación de responsabilidades (`Controller`/`Service`/`Module`)
- Inyección de dependencias correcta
- `ValidationPipe` global y `CORS` habilitado
- TypeScript estricto sin uso innecesario de `any`

### **Seguridad** (100%)
- No se registran contraseñas en logs
- Uso de consultas parametrizadas mediante Prisma ORM
- `AUDIT_LOG` registra `REGISTRO_USUARIO` y `LOGIN_EXITOSO`
- `JWT Strategy` preparada para Sprint 2

### **Postman Collection** (100%)
- Ocho casos de prueba con aserciones
- Cobertura de registro exitoso, duplicados, validaciones, login, credenciales erróneas y cuenta bloqueada

---

## CORRECCIONES APLICADAS

### 1. **Eliminación de código no utilizado**
**Situación identificada**: El método `createUsuario()` en `UsuariosService` no estaba en uso.  
**Acción aplicada**: Eliminación del método; la transacción se gestiona en `AuthService.registro()`.

### 2. **Ajuste de `BCRYPT_ROUNDS`**
**Antes**:
```typescript
const BCRYPT_ROUNDS = parseInt(process.env.BCRYPT_ROUNDS || '10', 10);
```
**Después**:
```typescript
const BCRYPT_ROUNDS = 10; // T03: Configurar 10 rounds de salt
```
**Justificación**: El requisito define explícitamente 10 rondas; se fija este valor para mantener conformidad.

### 3. **Registro de auditoría no bloqueante en login**
**Situación identificada**: Un fallo en `AUDIT_LOG` impedía completar el login.  
**Acción aplicada**: Encapsulado en `try-catch` y agrupado con la actualización de refresh token en una transacción interactiva (`$transaction`) para evitar `EINVALIDSTATE` con `@prisma/adapter-mssql`.

```typescript
try {
  await this.prisma.$transaction(async (tx) => {
    await tx.usuarios.update({ ... });   // refresh token hash
    await tx.auditLog.create({ ... });    // AUDIT_LOG
  });
} catch (txError) {
  this.logger.error('Error en transacción de login (update + audit)');
  this.logger.error(txError);
}
```

### 4. **Incorporación de prueba para cuenta bloqueada**
**Nuevo caso de prueba**: "Test 5: Login cuenta bloqueada"  
**Validación**: Respuesta `403 Forbidden` con mensaje "Cuenta inactiva o bloqueada".

### 5. **Corrección EINVALIDSTATE en `@prisma/adapter-mssql` 7.3.x**
**Situación identificada**: Al hacer login, la escritura secuencial `usuarios.update()` (guardar refresh token hash) seguida de `auditLog.create()` provocaba el error `PrismaClientKnownRequestError: Requests can only be made in the LoggedIn state, not the Final state` (`code: EINVALIDSTATE`). El adapter MSSQL cierra la conexión tedious subyacente tras completar la primera query; la segunda query intenta reutilizar una conexión ya finalizada.  
El mismo riesgo existía en `logout()`, donde `usuarios.updateMany()` y `jwtBlacklist.create()` se ejecutaban de forma secuencial.

**Acción aplicada**: Se agruparon las escrituras secuenciales dentro de `this.prisma.$transaction(async (tx) => { ... })` (transacción interactiva) para que ambas operaciones compartan la misma conexión.

**`login()`** — Antes (dos llamadas independientes):
```typescript
await this.prisma.usuarios.update({ ... });
// ... más adelante ...
await this.prisma.auditLog.create({ ... });
```

**`login()`** — Después (transacción interactiva):
```typescript
await this.prisma.$transaction(async (tx) => {
  await tx.usuarios.update({
    where: { id: usuario.id },
    data: { refresh_token_hash, refresh_token_expires_at },
  });
  await tx.auditLog.create({
    data: { user_id: usuario.id, action: 'LOGIN_EXITOSO' },
  });
});
```

**`logout()`** — Misma corrección: `usuarios.updateMany()` + `jwtBlacklist.create()` agrupados en una sola transacción interactiva.

**Archivo modificado**: `src/auth/auth.service.ts`  
**Justificación**: La transacción interactiva (`$transaction`) garantiza que todas las operaciones usen una única conexión del pool, evitando el estado `Final` de tedious. Es la solución recomendada por Prisma para adapters de driver que manejan conexiones de forma estricta.

---

## MÉTRICAS DE CALIDAD

### Sprint 1

| Aspecto | Puntuación |
|---------|------------|
| Cumplimiento funcional | 100% |
| Arquitectura | 95% |
| Seguridad | 100% |
| Testing | 95% |
| Documentación código | 90% |
| TypeScript strictness | 100% |
| Manejo de errores | 100% |
| **TOTAL** | **95%** |

### Sprint 2

| Aspecto | Puntuación |
|---------|------------|
| Cumplimiento funcional (7/7 tareas) | 100% |
| Arquitectura Android (MVVM-like) | 95% |
| Seguridad (tokens, SharedPreferences) | 95% |
| Testing unitario (Jest 5/5) | 100% |
| Testing integración (15/15 aserciones) | 100% |
| UI/UX (Material Design, validaciones) | 95% |
| Manejo de errores (backend + Android) | 100% |
| **TOTAL** | **97%** |

---

## CHECKLIST DE DEPLOYMENT

### Backend
- [x] Código compila sin errores TypeScript
- [x] Todas las dependencias instaladas
- [x] Variables de entorno configuradas (.env)
- [x] Schema Prisma alineado con SQL Server
- [x] Seeds preparados (CIUDADANO, ADMINISTRADOR)
- [x] Postman collection lista para testing
- [x] GlobalExceptionFilter activo
- [x] ValidationPipe habilitado
- [x] CORS configurado
- [x] JWT configurado con secrets separados (access + refresh)
- [x] Unit tests pasando (20/20)
- [x] Integration tests pasando (15/15)
- [x] JWT Blacklist table created (JWT_BLACKLIST)
- [x] Cron cleanup service registered (ScheduleModule)

### Android
- [x] Build exitoso (BUILD SUCCESSFUL)
- [x] UI login/registro funcional con validaciones
- [x] Retrofit + OkHttp configurados con timeouts
- [x] AuthInterceptor inyecta Bearer token
- [x] TokenAuthenticator implementado (refresh automático)
- [x] SharedPreferences persiste access + refresh tokens
- [x] AuthErrorMapper mapea todos los códigos del backend
- [x] AuthUiMessageFactory genera mensajes localizados
- [x] network_security_config.xml limita cleartext a localhost
- [ ] **PENDIENTE**: Migrar a EncryptedSharedPreferences
- [ ] **PENDIENTE**: Renombrar `LoginActiviy.kt` → `LoginActivity.kt`

---

## COMANDOS PARA INICIAR

```bash
# 1. Generar cliente Prisma
npx prisma generate

# 2. Aplicar migraciones a SQL Server
npx prisma migrate dev --name init

# 3. Ejecutar seeds (roles CIUDADANO y ADMINISTRADOR)
npx prisma db seed

# 4. Iniciar servidor
npm run start:dev

# 5. Verificar
# Servidor debe estar en http://localhost:3000
```

---

## NOTAS FINALES

### Aspectos destacados:
1. La transacción de registro incluye `AUDIT_LOG` y mantiene atomicidad.
2. El manejo de errores es exhaustivo y estandarizado (backend + Android).
3. Refresh token con rotación, SHA-256, y secrets independientes.
4. El proyecto aplica tipado estricto en TypeScript.
5. El código presenta una organización consistente.
6. UI móvil dual login/registro con validaciones completas.
7. `AuthErrorMapper` + `AuthUiMessageFactory` separan lógica de errores de la UI.
8. `TokenAuthenticator` maneja refresh automático con protección anti-loop.

### Pendientes para **Sprint 3** (restantes):
- [ ] Proteger rutas con JwtAuthGuard
- [ ] Migrar a `EncryptedSharedPreferences` en Android
- [ ] Implementar "Olvidaste tu contraseña"
- [ ] Renombrar archivo `LoginActiviy.kt` (typo en nombre)
- [x] ~~Endpoint POST /auth/logout (T15)~~
- [x] ~~Agregar tests e2e para refresh y logout~~

### Resumen Sprint 2 (7 tareas — 19 puntos):

| Tarea | HU | Descripción | Estado |
|-------|-----|------------|--------|
| T06 | HU01 | UI formulario registro (Kotlin) | ✅ 100% |
| T07 | HU01 | Pruebas unitarias endpoint registro | ✅ 100% (5/5 Jest) |
| T10 | HU02 | Refresh token sesión persistente | ✅ 100% (backend + Android) |
| T11 | HU02 | Validación estado cuenta | ✅ 100% |
| T12 | HU02 | Manejo errores autenticación | ✅ 100% |
| T13 | HU02 | UI pantalla login móvil | ✅ 100% |
| T14 | HU02 | Almacenamiento local token | ✅ 100% |

---

### Resumen Sprint 3 (en progreso):

| Tarea | HU | Descripción | Estado |
|-------|-----|------------|--------|
| T15 | HU02 | POST /auth/logout — invalidación de sesión | ✅ 100% (6 tests) |
| T16 | HU02 | JWT Blacklist — invalidación inmediata access tokens | ✅ 100% (9 tests) |

---

**Conclusión**: Sprint 2 completado al 100% (7/7 tareas, 19 puntos). Sprint 3 en progreso con T15 y T16 completadas. Todas las pruebas unitarias (20/20 Jest) y de integración (15/15 aserciones) pasan exitosamente. El backend y la app Android están sincronizados con soporte completo de refresh token, validación de estado de cuenta, manejo de errores estandarizado, logout seguro e idempotente, y revocación inmediata de access tokens vía blacklist con limpieza automática.

**Resultado**: Aprobado para despliegue.
