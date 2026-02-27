# Alertify - Módulo de Gestión de Identidad y Control de Acceso

## Información general
- **Sprint**: 1 (backend de autenticación)
- **Tecnologías**: NestJS, TypeScript, SQL Server, Prisma, JWT
- **Estado**: Implementación funcional para registro y autenticación
Este repositorio contiene el microservicio de autenticación y autorización para Alertify, con enfoque en gestión de identidad y control de acceso basado en roles (RBAC).
## Funcionalidades implementadas

### Registro de usuarios (`POST /auth/registro`)
- Validación de correo electrónico único
- Validación de nombre de usuario alfanumérico (4 a 20 caracteres)
- Validación de contraseña (mínimo 8 caracteres, al menos 1 mayúscula y 1 número)
- Cifrado de contraseña con `bcrypt` (10 rondas)
- Asignación automática del rol `CIUDADANO`

### Autenticación (`POST /auth/login`)
- Verificación de credenciales con `bcrypt.compare`
- Generación de token JWT con expiración de 1 hora
- Validación de estado de cuenta (activa o bloqueada)
- Claims incluidos: `sub`, `email`, `roles`

### Auditoría
- Registro de eventos `REGISTRO_USUARIO` y `LOGIN_EXITOSO`
- Persistencia en tabla `AUDIT_LOG`

### Manejo de errores
- Filtro global de excepciones para respuestas estandarizadas
- Uso consistente de códigos HTTP (201, 400, 401, 403, 409, 500)

## Stack tecnológico
| Componente | Tecnología | Versión |
|------------|------------|---------|
| Framework | NestJS | 11.x |
| Lenguaje | TypeScript | 5.x |
| Base de datos | SQL Server | 2019+ |
| ORM | Prisma | 7.x |
| Autenticación | `@nestjs/jwt` | 11.x |
| Hashing | `bcrypt` | 6.x |
| Validación | `class-validator` | 0.14.x |
## Estructura del proyecto

```text
src/
├── auth/
│   ├── auth.controller.ts
│   ├── auth.service.ts
│   ├── auth.module.ts
│   ├── dto/
│   │   ├── registro.dto.ts
│   │   └── login.dto.ts
│   ├── guards/
│   │   └── jwt-auth.guard.ts
│   └── strategies/
│       └── jwt.strategy.ts
├── usuarios/
│   ├── usuarios.service.ts
│   └── usuarios.module.ts
├── prisma/
│   ├── prisma.service.ts
│   └── prisma.module.ts
├── common/
│   └── filters/
│       └── http-exception.filter.ts
└── main.ts

prisma/
├── schema.prisma
└── seed.ts

docs/testing/postman/
└── Alertify_Sprint1.postman_collection.json
```

## Modelo de datos

```sql
USUARIOS (id, email*, username*, password_hash, estado, puntuacion, created_at, updated_at)
ROLES (id, nombre*, descripcion)
USER_ROLES (user_id, role_id) [PK compuesta]
AUDIT_LOG (id, user_id, action, created_at)

* = restricción UNIQUE
```

### Roles predefinidos

| ID | Nombre | Descripción |
|----|--------|-------------|
| 1 | CIUDADANO | Rol por defecto para usuarios registrados |
| 2 | ADMINISTRADOR | Gestión completa del sistema |



## Endpoints disponibles

| Método | Endpoint | Body | Respuesta | Autenticación |
|--------|----------|------|-----------|---------------|
| `POST` | `/auth/registro` | `{email, username, password}` | `{message, userId}` | No |
| `POST` | `/auth/login` | `{email, password}` | `{access_token, user}` | No |

## Pruebas

### Casos incluidos en Postman

1. Registro exitoso
2. Correo duplicado (409)
3. Usuario duplicado (409)
4. Validaciones de DTO (400)
5. Inicio de sesión exitoso
6. Contraseña incorrecta (401)
7. Correo inexistente (401)
8. Cuenta bloqueada (403)

### Comandos de pruebas automatizadas

```bash
npm run test
npm run test:e2e
npm run test:cov
```

## Seguridad

- Contraseñas cifradas con `bcrypt` (10 rondas)
- JWT firmado con algoritmo HS256
- Validación de entrada con `class-validator`
- Consultas parametrizadas mediante Prisma
- CORS habilitado para integración con frontend móvil

Para entornos productivos, es obligatorio reemplazar `JWT_SECRET` por una clave robusta y administrada de forma segura.

## Documentación adicional

- [CODE_REVIEW](./docs/CODE_REVIEW.md)

- [Colección Postman](./docs/testing/postman/)