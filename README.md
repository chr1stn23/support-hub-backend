# SupportHub — Backend

API REST para gestionar **tickets de soporte** y **activos tecnológicos** (inventario y asignaciones) de una organización.
Monolito modular con Spring Boot 4, seguridad por roles con JWT, reglas de negocio explícitas y garantías de concurrencia respaldadas por PostgreSQL.

> El frontend (Angular) vive en un repositorio aparte y consume esta API.

## Stack

| Área | Tecnología |
|---|---|
| Lenguaje / build | Java 25, Gradle (Groovy) |
| Framework | Spring Boot 4.1 (Spring Framework 7, Spring Security 7, Hibernate 7, Jackson 3) |
| Base de datos | PostgreSQL 17 + Flyway (migraciones versionadas) |
| Seguridad | JWT HS256 sin estado (Spring Security OAuth2 Resource Server), BCrypt |
| Documentación | OpenAPI 3 / Swagger UI (springdoc 3.x) |
| Pruebas | JUnit 5, AssertJ, MockMvc, Testcontainers (PostgreSQL real) |
| Despliegue | Docker (multi-stage), Docker Compose, GitHub Actions |

## Arquitectura

Monolito modular: cada módulo agrupa su API, casos de uso, dominio e infraestructura.

```
com.chrisdev.supporthub
├── common/        contrato de errores (ApiError), paginación, excepciones, utilidades
├── auth/          login, emisión de JWT, configuración de seguridad
├── user/          usuarios, roles, administración y selector
├── ticket/        tickets, comentarios, historial, adjuntos
├── asset/         inventario, asignaciones, ciclo de vida
├── notification/  notificaciones internas
├── audit/         auditoría de acciones sensibles
├── dashboard/     KPIs agregados por rol
└── file/          puerto FileStorage + adaptador local + validación de archivos
```

Capas dentro de cada módulo: `api` (controllers y DTOs) → `application` (casos de uso, transacciones) → `domain` (entidades y reglas) → `infrastructure` (repositorios, adaptadores).

Decisiones de simplificación (deliberadas):

- Las entidades JPA hacen también de modelo de dominio; los DTOs son `record` separados.
- Los servicios de aplicación devuelven DTOs de respuesta directamente (sin capa de mappers).
- Las consultas de agregación (dashboard, auditoría) usan `JdbcClient` con SQL explícito, que corre en la misma transacción que JPA.

## Puesta en marcha

### Requisitos

JDK 25, Docker (para PostgreSQL y para los tests de integración).

### Desarrollo local

```bash
docker compose up -d          # solo PostgreSQL
./gradlew bootRun             # perfil "local" por defecto
```

- API: `http://localhost:8080`
- Swagger UI: `http://localhost:8080/swagger-ui.html` (botón *Authorize*: pega el `accessToken` del login)
- Salud: `http://localhost:8080/actuator/health`

El perfil `local` crea usuarios demo (contraseña `Demo1234!`):

| Email | Rol |
|---|---|
| `admin@supporthub.local` | ADMIN |
| `agent@supporthub.local`, `agent2@supporthub.local` | AGENT |
| `user@supporthub.local` | USER |
| `assets@supporthub.local` | ASSET_MANAGER |

### Con Docker (stack completo)

```bash
cp .env.example .env          # y completa JWT_SECRET y BOOTSTRAP_ADMIN_PASSWORD
docker compose --profile app up --build -d
```

En producción no hay usuarios demo: el primer administrador se crea con `BOOTSTRAP_ADMIN_EMAIL` / `BOOTSTRAP_ADMIN_PASSWORD` (solo si todavía no existe un admin activo).

### Variables de entorno

| Variable | Obligatoria | Descripción |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | prod | `local` (por defecto), `test` o `prod` |
| `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD` | prod | Conexión a PostgreSQL |
| `JWT_SECRET` | prod | Mínimo 32 caracteres. En `prod` **no tiene valor por defecto**: sin ella la aplicación no arranca |
| `CORS_ALLOWED_ORIGINS` | prod | Orígenes permitidos, separados por coma |
| `FILE_STORAGE_PATH` | no | Carpeta de adjuntos (volumen persistente en Docker) |
| `BOOTSTRAP_ADMIN_EMAIL`, `BOOTSTRAP_ADMIN_PASSWORD`, `BOOTSTRAP_ADMIN_NAME` | primer arranque | Crean el primer ADMIN |

## Roles y permisos

| Capacidad | USER | AGENT | ASSET_MANAGER | ADMIN |
|---|---|---|---|---|
| Crear tickets y comentar | ✓ | ✓ | ✓ | ✓ |
| Ver tickets | propios | todos | propios | todos |
| Asignar tickets | — | tomarlos | — | cualquiera |
| Cambiar estado de un ticket | cancelar, cerrar o reabrir los suyos | los asignados a él | — | todos |
| Ver inventario | — | ✓ | ✓ | ✓ |
| Gestionar activos (crear, asignar, transferir, devolver, baja) | — | — | ✓ | ✓ |
| Buscar usuarios (selector) | — | ✓ | ✓ | ✓ |
| Administrar usuarios y roles | — | — | — | ✓ |
| Consultar auditoría | — | — | — | ✓ |

## Reglas de negocio

**Tickets** — máquina de estados:

| Desde | Hacia |
|---|---|
| `OPEN` | `IN_PROGRESS` (solo al asignar un agente), `CANCELLED` |
| `IN_PROGRESS` | `WAITING`, `RESOLVED`, `CANCELLED` |
| `WAITING` | `IN_PROGRESS`, `CANCELLED` |
| `RESOLVED` | `CLOSED`, `IN_PROGRESS` (reapertura, con nota obligatoria) |
| `CLOSED`, `CANCELLED` | — (finales: no se edita, comenta, asigna ni adjunta) |

**Activos**: `AVAILABLE → ASSIGNED` (asignar), `ASSIGNED → AVAILABLE` (devolver), `AVAILABLE ↔ MAINTENANCE`, `AVAILABLE/MAINTENANCE → RETIRED` (baja lógica, final). Un activo asignado debe devolverse antes de pasar a mantenimiento o baja. Invariante: *`ASSIGNED` ⇔ tiene exactamente una asignación vigente*.

**Usuarios**: un admin no puede quitarse su rol, desactivarse ni resetearse; no se desactiva a quien tenga activos o tickets en curso; siempre queda al menos un admin activo.

## Concurrencia

| Escenario | Estrategia |
|---|---|
| Dos personas asignan el mismo activo a la vez | **Índice único parcial** en PostgreSQL (`WHERE returned_at IS NULL`): gana una, la otra recibe `409 ASSET_ALREADY_ASSIGNED` |
| Dos ediciones simultáneas de un ticket, activo o usuario | **Bloqueo optimista** (`@Version`) + `version` explícito en el `PATCH` para detectar formularios desactualizados |
| Dos admins se desactivan entre sí (el sistema quedaría sin admins) | **Bloqueo pesimista** (`SELECT ... FOR UPDATE`) sobre la fila del rol `ADMIN`, con relectura del actor tras el bloqueo |
| Cifras del dashboard coherentes entre sí | Transacción de solo lectura `REPEATABLE READ` (una sola foto de los datos) |

## Seguridad

- JWT sin estado (60 min), contraseñas con BCrypt (`DelegatingPasswordEncoder`).
- Respuesta idéntica ante usuario inexistente y contraseña errónea; comparación de relleno para igualar tiempos.
- Autorización por rol (`@PreAuthorize`) **y** por recurso (política de visibilidad de tickets). Un ticket ajeno responde `404`, no `403`.
- Adjuntos: lista blanca por **contenido** (bytes iniciales), no por nombre ni `Content-Type`; nombre de almacenamiento generado (sin *path traversal*); descarga siempre como `attachment`; límites de tamaño y cantidad.
- Errores sin filtrar detalles internos; contrato único `ApiError`.
- Auditoría (`audit_logs`) atómica con la operación; los accesos denegados y logins fallidos se registran aparte, en una transacción propia. Nunca se guardan contraseñas, tokens ni textos de comentarios.
- Secretos solo por entorno; Swagger deshabilitado en `prod`; contenedor sin privilegios de root.

## API (`/api/v1`)

| Módulo | Endpoints principales |
|---|---|
| Auth | `POST /auth/login`, `GET /auth/me` |
| Usuarios | `GET /users/lookup`, `GET·POST /users`, `GET·PATCH /users/{id}`, `PUT /users/{id}/roles`, `POST /users/{id}/activate·deactivate·password`, `POST /users/me/password` |
| Tickets | `GET·POST /tickets`, `GET·PATCH /tickets/{id}`, `POST /tickets/{id}/assign·status·comments`, `GET /tickets/{id}/comments·history`, `GET /categories` |
| Adjuntos | `POST·GET /tickets/{id}/attachments`, `GET …/{attachmentId}/download`, `DELETE …/{attachmentId}` |
| Activos | `GET·POST /assets`, `GET /assets/mine`, `GET·PATCH /assets/{id}`, `GET /assets/{id}/assignments`, `POST /assets/{id}/assign·transfer·return·status`, `GET /asset-types` |
| Notificaciones | `GET /notifications`, `GET /notifications/unread-count`, `POST /notifications/{id}/read`, `POST /notifications/read-all` |
| Dashboard | `GET /dashboard/summary?days=30` |
| Auditoría | `GET /audit-logs` (solo ADMIN) |

Todas las listas paginadas usan `page`, `size` (máximo 100) y `sort`. La documentación interactiva está en Swagger UI.

**Contrato de error**

```json
{
  "timestamp": "2026-10-07T15:04:05Z",
  "status": 409,
  "code": "ASSET_ALREADY_ASSIGNED",
  "message": "The asset is already assigned to Lucía Usuaria.",
  "path": "/api/v1/assets/…/assign",
  "fieldErrors": []
}
```

## Pruebas

```bash
./gradlew test               # unitarias (sin Docker): máquinas de estado, detector de archivos, saneamiento
./gradlew integrationTest    # integración con PostgreSQL real (Testcontainers; requiere Docker)
./gradlew check              # todo
```

Las pruebas de integración cubren: autenticación y autorización por rol, el flujo completo de tickets, adjuntos (incluyendo archivos disfrazados), el dashboard y dos pruebas de concurrencia reales (asignación simultánea de un activo y administradores que se desactivan entre sí). Al levantar el contexto contra PostgreSQL también se valida que las migraciones y el mapeo JPA coincidan (`ddl-auto: validate`).

## Limitaciones conocidas y mejoras futuras

- **JWT sin revocación**: tras desactivar a un usuario o cambiarle el rol, su token sigue vigente hasta expirar. Se mitiga en las escrituras y en la administración de usuarios (se relee al actor), pero no en las lecturas. Siguiente paso: refresh token con rotación y lista de revocación.
- **Sin límite de intentos de login** ni *rate limiting*: pendiente en el borde (proxy/gateway).
- **Sin recuperación de contraseña por email** (no hay servidor de correo): un admin la restablece.
- **Adjuntos**: sin antivirus; solo PNG, JPEG, PDF y texto UTF-8; límite de cantidad "blando" bajo concurrencia. Almacenamiento local detrás del puerto `FileStorage` (migrable a S3/MinIO con otro adaptador).
- **Notificaciones**: solo internas (sin email ni tiempo real). Los mensajes se guardan en inglés; el frontend puede traducir por `type`.
- **Dashboard**: tiempos en horas naturales (sin horario laboral ni pausas por `WAITING`); sin caché.
- **Búsqueda** con `LIKE`: para volúmenes grandes, `pg_trgm` o `tsvector`.
- **Orden por prioridad** de tickets (CRITICAL antes que LOW) no soportado aún.
