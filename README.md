# SupportHub — Backend

API REST para gestionar **tickets de soporte** y **activos tecnológicos** (inventario y asignaciones) de una organización.
Monolito modular con Spring Boot 4, sesión por cookies `HttpOnly` con refresh rotativo, reglas de negocio explícitas y garantías de concurrencia respaldadas por PostgreSQL.

> El frontend (Angular) vive en un repositorio aparte y consume esta API.

## Stack

| Área | Tecnología |
|---|---|
| Lenguaje / build | Java 25, Gradle (Groovy) |
| Framework | Spring Boot 4.1 (Spring Framework 7, Spring Security 7, Hibernate 7, Jackson 3) |
| Base de datos | PostgreSQL 17 + Flyway (migraciones versionadas) |
| Seguridad | JWT HS256 de vida corta en cookie `HttpOnly`, refresh token opaco y rotativo, protección CSRF, BCrypt |
| Documentación | OpenAPI 3 / Swagger UI (springdoc 3.x) |
| Pruebas | JUnit 5, AssertJ, MockMvc, Testcontainers (PostgreSQL real) |
| Despliegue | Docker (multi-stage), Docker Compose, GitHub Actions |

## Arquitectura

Monolito modular: cada módulo agrupa su API, casos de uso, dominio e infraestructura.

```
com.chrisdev.supporthub
├── common/        contrato de errores (ApiError), paginación, excepciones, utilidades
├── auth/          login, sesión (cookies, refresh, CSRF), configuración de seguridad
├── user/          usuarios, roles, administración y selector
├── ticket/        tickets, comentarios, historial, adjuntos y política de acceso
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
- Las consultas de agregación y de solo-anexar (dashboard, auditoría, refresh tokens) usan `JdbcClient` con SQL explícito, en la misma transacción que JPA.
- Las reglas de permiso viven en **una sola política** (`TicketAccessPolicy`): la usan los servicios para validar y la API para calcular `actions` (qué botones mostrar).

## Puesta en marcha

### Requisitos

JDK 25, Docker (para PostgreSQL y para los tests de integración).

### Desarrollo local

```bash
docker compose up -d          # solo PostgreSQL
./gradlew bootRun             # perfil "local" por defecto
```

- API: `http://localhost:8080`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- Salud: `http://localhost:8080/actuator/health`

El perfil `local` crea usuarios demo (contraseña `Demo1234!`) y, **solo en local y en test**, devuelve además el `accessToken` en el cuerpo del login para poder usar Swagger y `curl` con `Authorization: Bearer`. En producción el token vive únicamente en la cookie.

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

Para desarrollar un frontend contra el backend en Docker, el override `docker-compose.dev.yml` lo arranca con el perfil `local`. **No usarlo fuera de tu equipo.**

### Variables de entorno

| Variable | Obligatoria | Descripción |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | prod | `local` (por defecto), `test` o `prod` |
| `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD` | prod | Conexión a PostgreSQL |
| `JWT_SECRET` | prod | Mínimo 32 caracteres. En `prod` **no tiene valor por defecto**: sin ella la aplicación no arranca |
| `CORS_ALLOWED_ORIGINS` | prod | Orígenes permitidos, separados por coma |
| `COOKIE_SECURE` | no | `true` por defecto (cookies solo por HTTPS). `local` lo desactiva; ponlo en `false` solo para probar `prod` por `http://localhost` |
| `FILE_STORAGE_PATH` | no | Carpeta de adjuntos (volumen persistente en Docker) |
| `BOOTSTRAP_ADMIN_EMAIL`, `BOOTSTRAP_ADMIN_PASSWORD`, `BOOTSTRAP_ADMIN_NAME` | primer arranque | Crean el primer ADMIN |

Cualquier propiedad se puede sobrescribir por entorno (por ejemplo `APP_SECURITY_JWT_EXPIRATION_MINUTES=1` para probar la renovación de sesión con un token de un minuto).

## Roles y permisos

| Capacidad | USER | AGENT | ASSET_MANAGER | ADMIN |
|---|---|---|---|---|
| Crear tickets y comentar | ✓ | ✓ | ✓ | ✓ |
| Ver tickets | propios | todos | propios | todos |
| Vincular un equipo a su ticket | solo los suyos | cualquiera | cualquiera | cualquiera |
| Asignar tickets | — | tomarlos | — | cualquiera |
| Cambiar estado de un ticket | cancelar, cerrar o reabrir los suyos | los asignados a él | — | todos |
| Ver inventario | — | ✓ | ✓ | ✓ |
| Gestionar activos (crear, asignar, transferir, devolver, baja) | — | — | ✓ | ✓ |
| Buscar usuarios (selector) | — | ✓ | ✓ | ✓ |
| Administrar usuarios y roles | — | — | — | ✓ |
| Consultar auditoría | — | — | — | ✓ |

Un cliente web **no debe duplicar estas reglas**: cada ticket y cada activo devuelve `actions` con lo que quien consulta puede hacer.

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

## Sesión y seguridad

### Sesión por cookies

| Cookie | Contenido | Atributos |
|---|---|---|
| `SH_ACCESS` | JWT de 15 min | `HttpOnly`, `SameSite=Strict`, `Path=/api`, `Secure` (salvo local) |
| `SH_REFRESH` | token opaco aleatorio (7 días de inactividad, 30 de vida máxima) | `HttpOnly`, `SameSite=Strict`, `Path=/api/v1/auth`, `Secure` (salvo local) |
| `XSRF-TOKEN` | valor aleatorio para CSRF | **no** `HttpOnly` (el frontend lo lee), `SameSite=Strict`, `Path=/` |

- **Login** (`POST /auth/login`) crea una *familia* de refresh tokens. **Refresh** (`POST /auth/refresh`) entrega un par nuevo e invalida el usado. **Logout** (`POST /auth/logout`) revoca la familia y limpia las cookies.
- En la base de datos solo se guarda el **hash SHA-256** del refresh token. Marcarlo como usado es una operación atómica (`UPDATE ... WHERE used_at IS NULL`).
- **Detección de robo**: presentar un refresh token ya consumido revoca toda su familia y deja un registro de auditoría. Si ocurre dentro de 10 s se interpreta como una carrera entre pestañas (`409 CONCURRENT_REFRESH`) y no revoca nada.
- **Revocación inmediata**: cada usuario tiene un `security_stamp` que viaja dentro del JWT y se comprueba en cada petición. Cambiar roles, contraseña o estado lo rota: los tokens ya emitidos dejan de valer, y si solo cambiaron los roles el navegador renueva con su refresh y recibe los nuevos.
- **CSRF**: doble envío de cookie (cabecera `X-XSRF-TOKEN` igual a la cookie `XSRF-TOKEN`), exigido solo a las escrituras autenticadas por cookie. El CSRF de Spring Security se desactiva porque se anula solo ante cualquier token bearer, incluido el que se lee de la cookie; lo reemplaza un filtro propio (`CookieCsrfFilter`).
- **Clientes sin navegador** (Swagger, `curl`, pruebas) pueden usar `Authorization: Bearer`, que no necesita CSRF porque ningún navegador lo añade solo.
- **Límite de intentos**: 5 fallos por correo y 30 por IP en 15 minutos devuelven `429 TOO_MANY_ATTEMPTS` con `Retry-After`, incluso con la contraseña correcta.

### Resto de medidas

- Contraseñas con BCrypt (`DelegatingPasswordEncoder`); respuesta idéntica ante usuario inexistente y contraseña errónea, con comparación de relleno para igualar tiempos.
- Autorización por rol (`@PreAuthorize`) **y** por recurso (política de visibilidad). Un ticket ajeno responde `404`, no `403`.
- Adjuntos: lista blanca por **contenido** (bytes iniciales), no por nombre ni `Content-Type`; nombre de almacenamiento generado (sin *path traversal*); descarga siempre como `attachment`; límites de tamaño y cantidad.
- Errores sin filtrar detalles internos; contrato único `ApiError`.
- Auditoría atómica con la operación; accesos denegados, CSRF inválido y logins fallidos se registran aparte, en una transacción propia. Nunca se guardan contraseñas, tokens ni textos de comentarios.
- Secretos solo por entorno; Swagger deshabilitado en `prod`; contenedor sin privilegios de root.

## Concurrencia

| Escenario | Estrategia |
|---|---|
| Dos personas asignan el mismo activo a la vez | **Índice único parcial** en PostgreSQL (`WHERE returned_at IS NULL`): gana una, la otra recibe `409 ASSET_ALREADY_ASSIGNED` |
| Dos ediciones simultáneas de un ticket, activo o usuario | **Bloqueo optimista** (`@Version`) + `version` explícito en el `PATCH` para detectar formularios desactualizados |
| Dos admins se desactivan entre sí (el sistema quedaría sin admins) | **Bloqueo pesimista** (`SELECT ... FOR UPDATE`) sobre la fila del rol `ADMIN`, con relectura del actor tras el bloqueo |
| Dos pestañas renuevan la sesión a la vez | Uso del refresh token **atómico** + ventana de gracia de 10 s |
| Cifras del dashboard coherentes entre sí | Transacción de solo lectura `REPEATABLE READ` (una sola foto de los datos) |

## Contrato para clientes web

- **`actions`** en cada ticket (`editContent`, `editTriage`, `comment`, `attach`, `assignToSelf`, `assignToOthers`, `changeStatus[{status, noteRequired}]`) y en cada activo (`edit`, `assign`, `transfer`, `returnAsset`, `changeStatus`). Una prueba verifica que lo ofrecido es **exactamente** lo que el servidor acepta.
- **Filas de la lista de tickets** con `canAssignToSelf`; **adjuntos** con `canDelete`.
- **Listas**: `status` y `priority` admiten varios valores (`?status=OPEN&status=IN_PROGRESS` o `?status=OPEN,IN_PROGRESS`); `createdFrom` / `createdTo` (ISO-8601); `sort=priority` ordena por prioridad real (CRITICAL primero con `desc`); paginación con desempate estable.
- **Errores de campo**: `fieldErrors[{field, code, message}]`, donde `code` es la restricción (`NotBlank`, `Size`, `Pattern`…). El `message` está en inglés y **no debe mostrarse**: se traduce por `code` / `field`.
- **Historial**: `STATUS_CHANGED` y `ASSIGNED` no llevan texto automático; en `DETAILS_UPDATED`, `note` es el nombre del campo editado (`title`, `description`, `priority`, `category`, `asset`).
- **Notificaciones**: mensaje en español, con `ticketId` o `assetId` para navegar al recurso.
- **Dashboard**: `tickets.trend` trae un punto por día (en UTC, con los días sin movimiento) con `created` y `resolved`.
- **Equipos**: `GET /assets/{id}/tickets` (resumen de sus tickets, visible para quien gestiona inventario) y `GET /assets?assignedTo=`.
- **Usuarios**: `lastLoginAt` en la vista de administración.

## API (`/api/v1`)

| Módulo | Endpoints principales |
|---|---|
| Auth | `POST /auth/login·refresh·logout`, `GET /auth/me` |
| Usuarios | `GET /users/lookup`, `GET·POST /users`, `GET·PATCH /users/{id}`, `PUT /users/{id}/roles`, `POST /users/{id}/activate·deactivate·password`, `POST /users/me/password` |
| Tickets | `GET·POST /tickets`, `GET·PATCH /tickets/{id}`, `POST /tickets/{id}/assign·status·comments`, `GET /tickets/{id}/comments·history`, `GET /categories` |
| Adjuntos | `POST·GET /tickets/{id}/attachments`, `GET …/{attachmentId}/download`, `DELETE …/{attachmentId}` |
| Activos | `GET·POST /assets`, `GET /assets/mine`, `GET·PATCH /assets/{id}`, `GET /assets/{id}/assignments·tickets`, `POST /assets/{id}/assign·transfer·return·status`, `GET /asset-types` |
| Notificaciones | `GET /notifications`, `GET /notifications/unread-count`, `POST /notifications/{id}/read`, `POST /notifications/read-all` |
| Dashboard | `GET /dashboard/summary?days=30` |
| Auditoría | `GET /audit-logs` (solo ADMIN) |

Todas las listas paginadas usan `page`, `size` (máximo 100) y `sort`. La documentación interactiva está en Swagger UI.

**Contrato de error**

```json
{
  "timestamp": "2026-10-07T15:04:05Z",
  "status": 400,
  "code": "VALIDATION_ERROR",
  "message": "Validation failed.",
  "path": "/api/v1/tickets",
  "fieldErrors": [
    { "field": "title", "code": "NotBlank", "message": "must not be blank" }
  ]
}
```

Códigos de operación: `VALIDATION_ERROR`, `MALFORMED_REQUEST`, `UNAUTHORIZED`, `FORBIDDEN`, `RESOURCE_NOT_FOUND`, `CONFLICT`, `INTERNAL_ERROR`, `INVALID_CREDENTIALS`, `TOO_MANY_ATTEMPTS`, `CSRF_TOKEN_INVALID`, `CONCURRENT_REFRESH`, `CONCURRENT_MODIFICATION`, `INVALID_STATUS_TRANSITION`, `TICKET_ALREADY_ASSIGNED`, `TICKET_NOT_ASSIGNABLE`, `TICKET_LOCKED`, `INVALID_ASSIGNEE`, `ASSET_ALREADY_ASSIGNED`, `ASSET_NOT_ASSIGNABLE`, `ASSET_NOT_ASSIGNED`, `ASSET_RETIRED`, `INVALID_ASSET_STATUS_TRANSITION`, `DUPLICATE_SERIAL_NUMBER`, `EMAIL_ALREADY_USED`, `CANNOT_MODIFY_SELF`, `LAST_ADMIN_REQUIRED`, `USER_HAS_ACTIVE_ASSIGNMENTS`, `INVALID_CURRENT_PASSWORD`, `UNSUPPORTED_FILE_TYPE`, `FILE_TOO_LARGE`, `ATTACHMENT_LIMIT_REACHED`. Los errores del propio framework usan el nombre del estado HTTP (`NOT_FOUND`, `METHOD_NOT_ALLOWED`, `UNSUPPORTED_MEDIA_TYPE`, `PAYLOAD_TOO_LARGE`…).

## Pruebas

```bash
./gradlew test               # unitarias (sin Docker): máquinas de estado, detector de archivos, límite de intentos, acciones
./gradlew integrationTest    # integración con PostgreSQL real (Testcontainers; requiere Docker)
./gradlew check              # todo
```

Las pruebas de integración cubren autenticación y autorización por rol, el ciclo completo de la sesión por cookies (CSRF, rotación, detección de reutilización, revocación inmediata), el flujo de tickets, la **coherencia entre `actions` y lo que el servidor acepta** (para cada estado y cada rol), adjuntos (incluyendo archivos disfrazados), filtros y orden, el dashboard y tres pruebas de concurrencia reales (asignación simultánea de un activo, administradores que se desactivan entre sí y renovación simultánea de sesión). Al levantar el contexto contra PostgreSQL también se valida que las migraciones y el mapeo JPA coincidan (`ddl-auto: validate`).

## Limitaciones conocidas y mejoras futuras

- **Contador de intentos de login en memoria**: con varias instancias del backend cada una cuenta por separado. Para escalar haría falta un almacén compartido (Redis o la base de datos).
- **Una consulta por petición** para comprobar el sello de seguridad (por clave primaria). Si importara, se cachea unos segundos a costa de una ventana de revocación.
- **Sin gestión de sesiones por el usuario** (listar y cerrar dispositivos), **sin 2FA**, **sin recuperación de contraseña por correo** y **sin cambio obligatorio en el primer acceso**: un admin crea o restablece la contraseña y se la comunica a la persona.
- **CSRF por doble envío sin firmar**: suficiente con cookies de host (`SameSite=Strict`, sin `Domain`), pero no protege frente a un atacante que controle un subdominio.
- **Adjuntos**: sin antivirus; solo PNG, JPEG, PDF y texto UTF-8; el límite de cantidad es "blando" bajo concurrencia. Almacenamiento local detrás del puerto `FileStorage` (migrable a S3/MinIO con otro adaptador).
- **Notificaciones**: solo internas (sin correo ni tiempo real) y fijadas en español.
- **Dashboard**: tiempos en horas naturales (sin horario laboral ni pausas por `WAITING`); la tendencia agrupa por día en UTC; sin caché.
- **Búsqueda** con `LIKE`: para volúmenes grandes, `pg_trgm` o `tsvector`.
- **Tickets relacionados de un equipo** (resumen visible para quien gestiona inventario): se muestra aunque quien consulta no pueda abrir el ticket completo.
