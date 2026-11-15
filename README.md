# API de reserva de salas

API REST con **Java 17** y **Spring Boot 3.5** para reservar salas de reuniones: crear y listar salas, crear, listar y cancelar reservas, y gestionar a los responsables de cada reserva.

**Stack:**
- Spring Web, Validation y Data JPA.
- PostgreSQL 16, con el esquema versionado mediante Flyway.
- Spring Security como resource server JWT (HS256).
- springdoc-openapi (Swagger UI).
- Testcontainers para los tests de integración.

**Tiempo invertido:** unas **2 horas** (1 h 52 min de trabajo, medidos con la grabación de la sesión).

---

## Cómo ejecutarlo

### Requisitos

- Java 17 o superior. No hace falta tener Maven instalado: el proyecto trae el Maven Wrapper (`mvnw`).
- Docker, que se usa para PostgreSQL y para los tests de integración.

### Arranque

```bash
# 1. Base de datos (PostgreSQL 16 en el puerto 5434 del host)
docker compose up -d

# 2. Aplicación (puerto 8090)
./mvnw spring-boot:run          # Linux / macOS
mvnw.cmd spring-boot:run        # Windows
```

Flyway crea las tablas al arrancar.

- Swagger UI: **http://localhost:8090/swagger-ui.html**
- Documento OpenAPI: http://localhost:8090/v3/api-docs

### Tests

```bash
./mvnw test        # Windows: mvnw.cmd test
```

Hay 16 tests: 11 unitarios y 5 de integración. Los de integración levantan su propio PostgreSQL con Testcontainers, así que necesitan Docker pero no tocan la base de desarrollo.

### Configuración

Todo tiene valores por defecto para que funcione en local sin configurar nada. Se cambia con variables de entorno:

| Variable | Por defecto | Uso |
|---|---|---|
| `SERVER_PORT` | `8090` | Puerto HTTP |
| `DB_URL` / `DB_USER` / `DB_PASSWORD` | `jdbc:postgresql://localhost:5434/reservas` / `reservas` / `reservas` | Conexión a PostgreSQL |
| `AUTH_USER` / `AUTH_PASSWORD` | `admin` / `admin123` | Único usuario que puede pedir token |
| `JWT_SECRET` | clave de desarrollo | Clave HS256, de 32 caracteres como mínimo (se valida al arrancar) |
| `JWT_EXPIRATION` | `1h` | Caducidad del token |
| `APP_ZONA_FILTRO_FECHA` | `UTC` | Zona en la que se interpretan los filtros por día |

> Los valores por defecto de usuario, contraseña y clave son **solo para desarrollo**. Fuera de local hay que definirlos.

---

## Uso

### Autenticación

Las consultas (`GET`) son públicas. Crear y cancelar exige un JWT:

```bash
curl -X POST http://localhost:8090/auth/login \
     -H "Content-Type: application/json" \
     -d '{"usuario":"admin","password":"admin123"}'
# {"accessToken":"eyJ...","tokenType":"Bearer","expiresIn":3600}
```

El token se envía en la cabecera `Authorization: Bearer <token>`. En Swagger UI basta con pulsar **Authorize** y pegar el token.

### Endpoints

| Método | Ruta | Token | Descripción |
|---|---|---|---|
| POST | `/auth/login` | No | Devuelve un JWT |
| POST | `/salas` | Sí | Crea una sala (`409` si el código ya existe) |
| GET | `/salas`, `/salas/{id}` | No | Lista paginada (por código) y detalle |
| POST | `/responsables` | Sí | Crea un responsable |
| GET | `/responsables`, `/responsables/{id}` | No | Lista paginada (por nombre) y detalle |
| POST | `/reservas` | Sí | Crea una reserva |
| GET | `/reservas`, `/reservas/{id}` | No | Lista paginada con filtros y detalle |
| PATCH | `/reservas/{id}/cancelar` | Sí | Cancela una reserva |

Ejemplo de reserva. Las fechas van en **ISO-8601 con zona horaria**:

```bash
curl -X POST http://localhost:8090/reservas \
     -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
     -d '{"salaId":1,"responsableId":1,"inicio":"2026-12-01T10:00:00Z","fin":"2026-12-01T11:00:00Z"}'
```

Filtros de `GET /reservas`, todos opcionales y combinables:

| Parámetro | Efecto |
|---|---|
| `salaId`, `responsableId` | Filtra por sala o por responsable |
| `fecha=2026-12-01` | Reservas que ocupan algún momento de ese día (UTC) |
| `desde=…&hasta=…` | Lo mismo para un rango de días, ambos incluidos. Se puede usar solo uno de los dos |
| `estado=ACTIVA\|CANCELADA` | Filtra por estado. Sin este filtro salen las dos |

Además admite `page`, `size` (20 por defecto, máximo 100) y `sort` (por defecto `inicio,asc`).

### Errores

Todos los errores usan **Problem Details (RFC 9457)**. Los `400` de validación incluyen el detalle de cada campo:

```json
{
  "title": "Datos inválidos",
  "status": 400,
  "detail": "La petición tiene campos inválidos",
  "instance": "/reservas",
  "errors": [{ "campo": "fin", "mensaje": "debe ser posterior a inicio" }]
}
```

| Código | Cuándo |
|---|---|
| `400` | Campos obligatorios o con formato inválido, `fin <= inicio`, reserva en el pasado, fecha sin zona horaria, filtros o paginación inválidos |
| `401` | Falta el token o no es válido (también un login fallido) |
| `404` | No existe la sala, el responsable o la reserva |
| `409` | La sala ya está ocupada en ese horario (se indica con qué reserva choca), código de sala repetido, o cancelar una reserva ya cancelada o ya terminada |

> **Nota para curl en Windows:** la consola puede no enviar el cuerpo en UTF-8, y entonces un nombre con tildes da `400`. Hay que enviar el cuerpo desde un archivo UTF-8 (`--data-binary @cuerpo.json`) o usar Swagger.

---

## Decisiones tomadas

El análisis completo está en [`Requirements.md`](Requirements.md). Lo principal:

- **Responsable como entidad propia.** Es una ampliación mía sobre el enunciado, que solo decía que cada reserva tiene un responsable. Evita repetir texto libre y permite filtrar las reservas por persona. Para no desviar el tiempo, por ahora solo tiene crear y consultar.
- **Solapamiento con intervalos semiabiertos `[inicio, fin)`.** Una reserva de 10:00 a 11:00 y otra de 11:00 a 12:00 no chocan. Las reservas canceladas no ocupan la sala.
- **Doble protección contra las reservas dobles:**
  - El servicio comprueba antes de guardar, para dar un `409` que dice con qué reserva choca.
  - Además, una **restricción de exclusión de PostgreSQL** (`btree_gist` sobre el rango de fechas, por sala y solo para reservas activas) impide que dos peticiones simultáneas reserven el mismo hueco. Esa violación también se traduce a `409`. Lo comprobé lanzando 10 peticiones a la vez: entra una y las otras nueve reciben `409`, sin ningún `500`.
- **Cancelar es un cambio de estado, no un borrado.** Se conserva el histórico y el hueco queda libre. Cancelar dos veces, o cancelar una reserva que ya terminó, da `409`.
- **Fechas en ISO-8601 con zona horaria, guardadas en UTC** (`timestamptz`). Así el instante no es ambiguo, sea cual sea la zona del cliente. Las respuestas van siempre en UTC. Una fecha sin zona se rechaza con un `400` claro en vez de adivinarla. Los filtros por día se interpretan en UTC, aunque es configurable.
- **Reloj inyectable** (`Clock`), para poder probar "en el pasado" y "ya terminó" de forma determinista.
- **JWT emitido por la propia API**, con un usuario fijo configurable y la contraseña comparada con BCrypt. Spring Security valida el token como resource server, con `MacAlgorithm.HS256` fijado, sin librerías de terceros. Un login fallido no revela si falló el usuario o la contraseña.
- **Esquema gestionado solo por Flyway.** Hibernate está en modo `validate`.
- **Sin colecciones inversas en las entidades** (no hay `List<Reserva>` en Sala ni en Responsable). Las reservas de una sala se obtienen con el listado filtrado y paginado. Así se evitan las consultas N+1 y los ciclos al serializar. El listado carga sala y responsable con `@EntityGraph`.
- **Lombok solo `@Getter`** en las entidades, sin `@Data` ni setters, para que id, código y estado no se puedan cambiar desde fuera.
- **Puertos 8090 (app) y 5434 (Postgres)**, para no chocar con servicios habituales en el 8080 y el 5432.

---

## Qué haría con más tiempo

- Completar el CRUD de responsables (modificar y eliminar). Al eliminar, `409` si tiene reservas, para no dejarlas huérfanas.
- Modificar reservas. Ahora se cancela y se crea otra.
- Usuarios en base de datos con roles; por ejemplo, que solo el creador o un admin pueda cancelar.
- Tests de controlador más finos (`@WebMvcTest`) para salas y responsables, y medir la cobertura con JaCoCo.
- Pipeline de CI (GitHub Actions) que ejecute `mvnw verify` con Testcontainers.
- Dockerfile de la app, para levantar todo con un solo `docker compose up`.
- Reglas de negocio adicionales: duración mínima y máxima, horario laboral, capacidad de la sala y reservas recurrentes.
- Observabilidad: Actuator (health y métricas) y logs estructurados.
- Bloqueo optimista (`@Version`) en Reserva frente a cancelaciones concurrentes.
- Silenciar el aviso de Mockito en JDK 21+, configurándolo como agente de Java en Maven.

---

## Cómo usé la IA

Desarrollé el proyecto con **Claude Code**, como pareja de programación. Yo decidía el alcance y el orden, revisaba cada paso y aprobaba cada commit. La IA proponía, implementaba y verificaba. Los commits en los que participó llevan el trailer `Co-Authored-By`.

**Cómo trabajamos**
- Regla fija: **antes de cada commit, la IA me resumía qué había hecho y yo revisaba el código.** No se hacía commit sin mi OK explícito. Por eso hay commits pequeños y progresivos.
- Cada paso se probó a mano antes del commit: curl contra la API y PostgreSQL reales, y pruebas desde Swagger UI. Las evidencias de Swagger están en [`tests/evidencias-pruebas-swagger.pdf`](tests/evidencias-pruebas-swagger.pdf).

**Qué le pedí**
- El esqueleto del proyecto, el modelo, la migración, los endpoints, la seguridad JWT, Swagger y los tests.
- Que mejorara y profundizara mi borrador de requisitos (`Requirements.md`), en el que yo había identificado los modelos Sala, Reserva y Responsable.
- Explicaciones de las decisiones. Por ejemplo, por qué ISO-8601 con zona horaria.

**Qué decidí yo**
- El stack: JWT, PostgreSQL, y Testcontainers con un Postgres real para la integración. También trabajar en local.
- Los tres modelos y gestionar **Responsable** como entidad con su propia gestión (idea mía), limitada al principio a crear y listar por el riesgo de alcance.
- Añadir **Swagger** al proyecto.
- Documentar con un PDF las evidencias de cada escenario probado a mano.
- Probar "cancelar una reserva ya terminada" con una reserva real de 2 minutos. La IA había dicho que eso no se podía probar a mano y solo lo iba a cubrir con un test.
- Añadir el filtro por **rango de fechas** (`desde`/`hasta`), a raíz de mi pregunta sobre si solo admitía un día.

**Qué propuso la IA y acepté**
- El esqueleto y las dependencias.
- Las nueve decisiones abiertas del diseño (D-1 a D-9 en `Requirements.md`), presentadas con opciones y una recomendación.
- La restricción de exclusión en PostgreSQL contra las reservas dobles.
- Los valores por defecto de desarrollo para las credenciales.
- El plan de tests y el puerto 5434 para Postgres.

**Qué corregí de lo que propuso**
- El **puerto 8090** para la app, en vez del 8080.
- **Lombok** en vez de getters escritos a mano.
- El PDF de evidencias en `tests/`, en vez de en `docs/`.
- **UTC** como zona del filtro por fecha, en vez de America/Montevideo.

**Errores y problemas que se detectaron y corrigieron**
- El Maven Wrapper quedó apuntando a una versión inexistente de Maven, porque PowerShell partió el argumento `-Dmaven=3.9.11`. Se corrigió la URL de la distribución.
- La app no conectaba a la base: había un PostgreSQL local en el 5432 y otro contenedor en el 5433. Se movió el Postgres del proyecto al 5434.
- Las pruebas manuales destaparon dos fallos de paginación, que se corrigieron antes del commit:
  - `@EnableSpringDataWebSupport` desactivaba el límite de 100 elementos por página;
  - `@PageableDefault` forzaba páginas de 10 en vez de 20.
- Una tanda de capturas para las evidencias salió ilegible y hubo que repetirla.
- Las altas con tilde fallaban con curl en Windows por la codificación de la consola, no por la API. Quedó documentado arriba.
