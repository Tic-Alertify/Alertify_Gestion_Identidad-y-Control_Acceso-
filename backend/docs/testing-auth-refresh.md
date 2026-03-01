# T10 / T15 / T16 – Testing: Auth Refresh Token, Logout & JWT Blacklist

## Variables de entorno requeridas

```env
# Secrets separados para access y refresh tokens
JWT_ACCESS_SECRET=mi-access-secret-seguro
JWT_ACCESS_TTL=15m
JWT_REFRESH_SECRET=mi-refresh-secret-seguro
JWT_REFRESH_TTL=7d

# Fallback: si JWT_ACCESS_SECRET no está definido, se usa JWT_SECRET
JWT_SECRET=mi-jwt-secret-legacy
```

## Endpoints

| Método | Ruta            | Auth      | Body                            |
| ------ | --------------- | --------- | ------------------------------- |
| POST   | /auth/login     | Ninguna   | `{ email, password }`           |
| POST   | /auth/refresh   | Ninguna   | `{ refresh_token }`             |
| POST   | /auth/logout    | Opcional* | `{ refresh_token }`             |

> \* **T16:** Si se envía el header `Authorization: Bearer <access_token>`, el access token se agrega a la blacklist (tabla `JWT_BLACKLIST`) para revocación inmediata. Si no se envía, solo se revoca el refresh token.

---

## 1. Login – Obtener access_token y refresh_token

```bash
curl -X POST http://localhost:3000/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "usuario@example.com",
    "password": "MiPass123"
  }'
```

**Respuesta exitosa (200):**

```json
{
  "access_token": "eyJhbGciOiJIUzI1NiIs...",
  "refresh_token": "eyJhbGciOiJIUzI1NiIs...",
  "user": {
    "id": 1,
    "email": "usuario@example.com",
    "username": "usuario1",
    "roles": ["CIUDADANO"]
  }
}
```

---

## 2. Refresh – Rotar tokens

```bash
curl -X POST http://localhost:3000/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{
    "refresh_token": "eyJhbGciOiJIUzI1NiIs..."
  }'
```

**Respuesta exitosa (200):**

```json
{
  "access_token": "eyJhbGciOiJIUzI1NiIs...(nuevo)...",
  "refresh_token": "eyJhbGciOiJIUzI1NiIs...(nuevo)..."
}
```

> **Nota:** El refresh_token anterior queda invalidado inmediatamente (rotación).

---

## 3. Logout – Invalidar sesión

```bash
# Logout básico (solo revoca refresh token)
curl -X POST http://localhost:3000/auth/logout \
  -H "Content-Type: application/json" \
  -d '{
    "refresh_token": "eyJhbGciOiJIUzI1NiIs..."
  }'
```

```bash
# Logout completo T16 (revoca refresh + blacklistea access token)
curl -X POST http://localhost:3000/auth/logout \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIs..." \
  -d '{
    "refresh_token": "eyJhbGciOiJIUzI1NiIs..."
  }'
```

**Respuesta exitosa (200):**

```json
{
  "message": "Sesión cerrada correctamente"
}
```

> **T15:** El logout verifica la firma del refresh token. Si el token es inválido o expirado, devuelve 401 `AUTH_REFRESH_INVALID`. Si el token es válido pero el hash ya fue eliminado (doble logout), responde 200 igualmente (idempotente).

> **T16:** Si se incluye el header `Authorization: Bearer <access_token>`, el servidor extrae el `jti` (JWT ID) del access token y lo inserta en la tabla `JWT_BLACKLIST`. A partir de ese momento, cualquier petición protegida con ese access token será rechazada con 401 `AUTH_TOKEN_REVOKED`. Si el access token es inválido o ya expirado, simplemente se ignora (no es un error). Si el `jti` ya estaba en blacklist (P2002), la operación es idempotente.

---

## 4. Casos de error

### 4.1 Logout con token inválido o expirado

```bash
curl -X POST http://localhost:3000/auth/logout \
  -H "Content-Type: application/json" \
  -d '{
    "refresh_token": "token-invalido"
  }'
```

**Respuesta (401):**

```json
{
  "statusCode": 401,
  "message": "Sesión inválida. Inicia sesión nuevamente.",
  "code": "AUTH_REFRESH_INVALID"
}
```

### 4.2 Logout sin body / sin refresh_token

```bash
curl -X POST http://localhost:3000/auth/logout \
  -H "Content-Type: application/json" \
  -d '{}'
```

**Respuesta (400):**

```json
{
  "statusCode": 400,
  "message": ["El refresh token es obligatorio"],
  "code": "VALIDATION_ERROR"
}
```

### 4.3 Refresh con token expirado o firma inválida

```bash
curl -X POST http://localhost:3000/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{
    "refresh_token": "token-invalido-o-expirado"
  }'
```

**Respuesta (401):**

```json
{
  "statusCode": 401,
  "message": "Sesión expirada. Inicia sesión nuevamente.",
  "code": "AUTH_REFRESH_INVALID"
}
```

### 4.4 Refresh con token anterior (ya rotado)

```bash
# Usar el refresh_token que se obtuvo ANTES de hacer refresh
curl -X POST http://localhost:3000/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{
    "refresh_token": "eyJ...(token antiguo)..."
  }'
```

**Respuesta (401):**

```json
{
  "statusCode": 401,
  "message": "Sesión inválida. Inicia sesión nuevamente.",
  "code": "AUTH_REFRESH_INVALID"
}
```

### 4.5 Refresh con cuenta bloqueada

**Respuesta (403):**

```json
{
  "statusCode": 403,
  "message": "La cuenta está bloqueada.",
  "code": "AUTH_ACCOUNT_BLOCKED"
}
```

### 4.6 Refresh con cuenta inactiva

**Respuesta (403):**

```json
{
  "statusCode": 403,
  "message": "La cuenta está inactiva.",
  "code": "AUTH_ACCOUNT_INACTIVE"
}
```

---

## 5. Pruebas en Postman

### Colección recomendada

1. **Login**: POST `/auth/login` → guardar `access_token` y `refresh_token` en variables de colección.
2. **Refresh OK**: POST `/auth/refresh` con `{{refresh_token}}` → actualizar variables con nuevos tokens.
3. **Refresh con token antiguo**: POST `/auth/refresh` con el token del paso 1 → esperar 401.
4. **Logout**: POST `/auth/logout` con `{{refresh_token}}` → esperar 200.
5. **Refresh después de logout**: POST `/auth/refresh` con el último token → esperar 401.

### Tests automáticos sugeridos (Postman)

```javascript
// En Login
pm.test("Login retorna access y refresh token", function () {
  const json = pm.response.json();
  pm.expect(json).to.have.property("access_token");
  pm.expect(json).to.have.property("refresh_token");
  pm.expect(json).to.have.property("user");
  pm.collectionVariables.set("access_token", json.access_token);
  pm.collectionVariables.set("refresh_token", json.refresh_token);
});

// En Refresh
pm.test("Refresh retorna nuevos tokens", function () {
  const json = pm.response.json();
  pm.expect(json).to.have.property("access_token");
  pm.expect(json).to.have.property("refresh_token");
  pm.collectionVariables.set("access_token", json.access_token);
  pm.collectionVariables.set("refresh_token", json.refresh_token);
});

// En Refresh con token antiguo
pm.test("Token antiguo devuelve 401", function () {
  pm.response.to.have.status(401);
  const json = pm.response.json();
  pm.expect(json.code).to.eql("AUTH_REFRESH_INVALID");
});
```

---

## 6. Códigos de error (Android)

| Código                    | HTTP | Significado                             |
| ------------------------- | ---- | --------------------------------------- |
| `AUTH_INVALID_CREDENTIALS`| 401  | Email o contraseña incorrectos          |
| `AUTH_REFRESH_INVALID`    | 401  | Refresh token inválido/expirado/rotado  |
| `AUTH_INVALID_TOKEN`      | 401  | Access token sin jti o malformado       |
| `AUTH_TOKEN_REVOKED`      | 401  | Access token revocado (en blacklist)    |
| `AUTH_ACCOUNT_BLOCKED`    | 403  | Cuenta bloqueada por administrador      |
| `AUTH_ACCOUNT_INACTIVE`   | 403  | Cuenta inactiva                         |

---

## 7. Seguridad

- El refresh token **nunca** se almacena en claro en la base de datos; solo su hash SHA-256.
- Cada login o refresh **rota** el token: el anterior queda invalidado.
- Se almacena **un solo** refresh token por usuario.
- Se verifica el **estado** del usuario en cada refresh (bloqueo/inactivación en tiempo real).
- Los refresh tokens se envían en el **body JSON**, no en cookies.
- **T16:** Los access tokens incluyen un `jti` (JWT ID, UUID v4) que permite su revocación individual.
- **T16:** Al hacer logout, si se envía el header `Authorization`, el access token se blacklistea inmediatamente.
- **T16:** En cada petición protegida, el `JwtStrategy` verifica que el `jti` no esté en la tabla `JWT_BLACKLIST`.

---

## 8. T16 – JWT Blacklist (tabla `JWT_BLACKLIST`)

### Esquema

```sql
CREATE TABLE JWT_BLACKLIST (
  id         INT IDENTITY(1,1) PRIMARY KEY,
  jti        NVARCHAR(64) UNIQUE NOT NULL,
  user_id    INT NULL,
  expires_at DATETIME NOT NULL,
  created_at DATETIME NOT NULL DEFAULT GETDATE()
);
```

### Cron de limpieza

El servicio `JwtBlacklistCleanupService` ejecuta cada hora:

```sql
DELETE FROM JWT_BLACKLIST WHERE expires_at < GETDATE();
```

Esto elimina entradas cuyo token original ya habría sido rechazado por expiración JWT, manteniendo la tabla compacta.

> **Nota**: El cron tiene retry automático (máx 2 intentos, 3 s de espera) ante errores `ETIMEOUT` del adapter MSSQL, que puede perder conexiones idle del pool.

### Flujo completo

1. **Login** → access token generado con `jti: randomUUID()`.
2. **Petición protegida** → `JwtStrategy.validate()` verifica `jti` no está en blacklist.
3. **Logout** → si se envía `Authorization: Bearer <token>`, se inserta `{ jti, user_id, expires_at }` en `JWT_BLACKLIST`.
4. **Petición post-logout** → `JwtStrategy` detecta `jti` en blacklist → 401 `AUTH_TOKEN_REVOKED`.
5. **Cron (cada hora)** → limpia entradas expiradas.

### Unit tests (20 tests)

```bash
npx jest src/auth/auth.service.spec.ts --verbose
```

| Suite                                   | Tests |
| --------------------------------------- | ----- |
| AuthService - registro()                | 5     |
| AuthService - logout()                  | 6     |
| AuthService - logout() + T16 blacklist  | 5     |
| AuthService - isTokenBlacklisted()      | 3     |
| AuthService - generateAccessToken()     | 1     |
| **Total**                               | **20**|
