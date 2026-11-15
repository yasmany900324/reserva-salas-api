# Requerimientos: API REST de reserva de salas

API REST con Java 17 y Spring Boot para reservar salas de reuniones. El dominio tiene tres modelos: **Sala**, **Reserva** y **Responsable**.

> **Decisión propia (no está en el enunciado):** gestionar **Responsable** como entidad con sus propios endpoints. El enunciado solo dice que cada reserva tiene un responsable. Así el responsable no es un texto libre que se repite en cada reserva, y se pueden consultar las reservas de una persona.
>
> **Alcance:** como la prueba es de unas 3 horas, primero se hace la gestión mínima de Responsable (crear y listar). Modificar y eliminar quedan para el final, si sobra tiempo (ver D-1 y D-8).

Los puntos marcados con **(D-n)** son decisiones que el enunciado no fija. Están resueltas en la sección 5.

---

## 1. Modelos

```
Responsable 1 ──── * Reserva * ──── 1 Sala
```

### 1.1 Sala

| Campo    | Tipo   | Restricciones                                         |
|----------|--------|-------------------------------------------------------|
| `id`     | Long   | Lo genera la base de datos.                           |
| `codigo` | String | Obligatorio, único, máximo 20 caracteres (p. ej. `A-101`). |
| `nombre` | String | Obligatorio, máximo 100 caracteres.                   |

### 1.2 Reserva

Cada reserva tiene sala, responsable, inicio y fin.

| Campo         | Tipo           | Restricciones                                          |
|---------------|----------------|--------------------------------------------------------|
| `id`          | Long           | Lo genera la base de datos.                            |
| `sala`        | Sala           | Obligatoria; si no existe → `404` (D-4).               |
| `responsable` | Responsable    | Obligatorio; si no existe → `404` (D-4).               |
| `inicio`      | OffsetDateTime | Obligatorio, ISO-8601 con zona; no puede estar en el pasado (D-3, D-6). |
| `fin`         | OffsetDateTime | Obligatorio; debe ser posterior a `inicio`.            |
| `estado`      | Enum           | `ACTIVA` o `CANCELADA`. Al crearla vale `ACTIVA`.      |

### 1.3 Responsable *(decisión propia)*

| Campo    | Tipo   | Restricciones                               |
|----------|--------|---------------------------------------------|
| `id`     | Long   | Lo genera la base de datos.                 |
| `nombre` | String | Obligatorio, máximo 100 caracteres.         |

### 1.4 Sobre las listas de reservas

El borrador ponía `reservas: List<Reserva>` dentro de Sala y de Responsable. La relación existe, pero propongo guardarla solo del lado de la Reserva, que tiene las claves foráneas a Sala y a Responsable. Las reservas de una sala o de un responsable se obtienen con el listado filtrado (`GET /reservas?salaId=…`).

Motivos para no poner la lista en la entidad:
- Una relación bidireccional en JPA provoca problemas de N+1 consultas y ciclos al serializar a JSON.
- Una lista sin paginar crece sin límite, y el enunciado pide paginación.

---

## 2. Requerimientos funcionales

### RF-1. Crear y listar salas

| Método | Ruta          | Autenticación | Éxito              | Errores                     |
|--------|---------------|---------------|--------------------|-----------------------------|
| POST   | `/salas`      | Sí (JWT)      | `201` + `Location` | `400`, `401`, `409` si el código ya existe |
| GET    | `/salas`      | No            | `200` (paginado)   | `400` si la paginación no es válida |
| GET    | `/salas/{id}` | No            | `200`              | `404`                       |

### RF-2. Crear, listar y cancelar reservas

| Método | Ruta                       | Autenticación | Éxito              | Errores                                   |
|--------|----------------------------|---------------|--------------------|-------------------------------------------|
| POST   | `/reservas`                | Sí (JWT)      | `201` + `Location` | `400`, `401`, `404` si la sala o el responsable no existen, `409` si hay solapamiento |
| GET    | `/reservas`                | No            | `200` (paginado)   | `400` si los filtros no son válidos       |
| GET    | `/reservas/{id}`           | No            | `200`              | `404`                                     |
| PATCH  | `/reservas/{id}/cancelar`  | Sí (JWT)      | `200` con la reserva cancelada | `401`, `404`, `409` si ya estaba cancelada o ya terminó |

Cuerpo de `POST /reservas`:

```json
{ "salaId": 1, "responsableId": 2, "inicio": "2026-11-20T10:00:00-03:00", "fin": "2026-11-20T11:00:00-03:00" }
```

**Validación (errores 400 claros):**
- Todos los campos son obligatorios.
- `fin` tiene que ser posterior a `inicio`.
- Las fechas tienen que venir en ISO-8601 con zona horaria (D-3).
- `inicio` no puede estar en el pasado (D-6). No hay límite de duración.
- El error indica qué campo falla y por qué, por ejemplo: `"fin": "debe ser posterior a inicio"`.

**Sala ocupada (409):**
- Una sala no puede tener dos reservas `ACTIVA` que se crucen en el tiempo.
- Hay cruce con una reserva existente cuando `inicio < finExistente` **y** `fin > inicioExistente`.
- Los intervalos son semiabiertos, `[inicio, fin)`: una reserva que empieza justo cuando otra termina (10:00–11:00 y 11:00–12:00) **no** choca.
- Las reservas `CANCELADA` no cuentan, así que dejan el hueco libre.
- El 409 indica con qué reserva choca.
- Frente a dos peticiones simultáneas, la regla también se aplica en PostgreSQL con una restricción de exclusión, así que nunca quedan dos reservas activas cruzadas (D-7).

**Cancelar:**
- La cancelación es un cambio de estado de `ACTIVA` a `CANCELADA`, no un borrado. La reserva se conserva como histórico.
- Cancelar una reserva ya cancelada, o una ya terminada, devuelve `409` con un mensaje claro (D-5).

### RF-3. Listado de reservas con filtro por sala y fecha, paginado

`GET /reservas` acepta estos parámetros, todos opcionales y combinables:

| Parámetro | Ejemplo        | Efecto                                                     |
|-----------|----------------|------------------------------------------------------------|
| `salaId`  | `1`            | Solo las reservas de esa sala.                             |
| `fecha`   | `2026-11-20`   | Solo las reservas que ocupan algún momento de ese día, en la zona horaria configurada (D-3). |
| `responsableId` | `2`      | Solo las de ese responsable (extra, por el modelo Responsable). |
| `page`    | `0`            | Página, empezando en 0. Por defecto 0.                     |
| `size`    | `20`           | Tamaño de página. Por defecto 20, máximo 100.              |
| `sort`    | `inicio,asc`   | Orden. Por defecto `inicio` ascendente.                    |

La respuesta incluye el contenido de la página y sus metadatos: número de página, tamaño, total de elementos y total de páginas.

### RF-4. Gestión de responsables *(decisión propia)*

Primera versión (D-1): **crear y listar**.

| Método | Ruta                 | Autenticación | Éxito              | Errores                     |
|--------|----------------------|---------------|--------------------|-----------------------------|
| POST   | `/responsables`      | Sí (JWT)      | `201` + `Location` | `400`, `401`                |
| GET    | `/responsables`      | No            | `200` (paginado)   |                             |
| GET    | `/responsables/{id}` | No            | `200`              | `404`                       |

**Al final, si sobra tiempo: CRUD completo.** Añade `PUT` y `DELETE`. Al eliminar hay que comprobar que no queden reservas colgando de ese responsable: si tiene alguna reserva, activa o histórica, se responde `409` y no se borra (D-8).

### RF-5. Autenticación

- Los endpoints que **crean, modifican o cancelan** exigen un JWT en la cabecera `Authorization: Bearer <token>`. Los de **consulta** (GET) son públicos.
- El token se obtiene con `POST /auth/login`, que es público, enviando usuario y contraseña. Devuelve un JWT firmado con caducidad (por ejemplo, 1 hora).
- Sin token, o con un token inválido o caducado → `401`.
- Hay un único usuario, configurado por variables de entorno (D-2).

---

## 3. Requerimientos no funcionales

- **Formato de error uniforme:** todos los errores usan *Problem Details* (RFC 9457), con `status`, `title`, `detail` y, en los 400, una lista `errors` de `{campo, mensaje}`.
- **Base de datos:** PostgreSQL 16 con migraciones Flyway. Hibernate solo valida el esquema (`ddl-auto: validate`).
- **Ejecución local:** `docker compose up -d` levanta Postgres en el puerto 5434, y la app escucha en el 8090.
- **Tests (mínimo 3, uno de integración):**
  1. Unitario: detección de solapamiento, incluido el caso de reservas que se tocan en el borde y no chocan.
  2. Unitario: validación de `fin > inicio` y cancelación (estado y doble cancelación).
  3. Integración con Testcontainers y PostgreSQL real, vía MockMvc:
     - crear una reserva con JWT → `201`;
     - repetirla en el mismo horario → `409`;
     - crear sin token → `401`;
     - listar sin token → `200`.

---

## 4. README (entregable)

Debe incluir:
- **Cómo ejecutarlo:** requisitos (Java 17+, Docker), `docker compose up -d`, `mvnw spring-boot:run`, cómo obtener un token y ejemplos con curl.
- **Decisiones tomadas:** las de la sección 5, Responsable como entidad propia, cancelación como cambio de estado, intervalos semiabiertos y puertos.
- **Qué haría con más tiempo.**
- **Cómo se usó la IA:** qué se le pidió, qué se aceptó y qué se corrigió. Ejemplos de esta sesión:
  - corregir la versión de Maven del wrapper;
  - cambiar el puerto de Postgres por el conflicto con el servicio local;
  - fijar la app en el 8090;
  - revisar el código antes de cada commit.
- **Tiempo invertido.**

---

## 5. Decisiones tomadas

| #   | Pregunta | Decisión |
|-----|----------|----------|
| D-1 | Alcance de Responsable | Primero solo crear y listar. El CRUD completo, al final, si sobra tiempo. |
| D-2 | Usuarios para el login | Un usuario fijo, configurado por variables de entorno. |
| D-3 | Formato de fechas | ISO-8601 con zona horaria (`OffsetDateTime`), guardado como `timestamptz`. El filtro `fecha` se interpreta en una zona configurable. |
| D-4 | Sala o responsable inexistentes al crear una reserva | `404`, con un mensaje que dice cuál no existe. |
| D-5 | Cancelar dos veces, o cancelar una reserva ya terminada | `409` en los dos casos. |
| D-6 | Validaciones extra | No se permiten reservas en el pasado. Sin límite de duración. |
| D-7 | Concurrencia en el solapamiento | Comprobación en Java más una restricción de exclusión en PostgreSQL (`btree_gist` sobre el rango, por sala y solo reservas activas). |
| D-8 | Eliminar un responsable con reservas | `409`, para que no queden reservas colgando. Solo aplica cuando se haga el CRUD completo. |
| D-9 | ¿El responsable es el usuario autenticado? | No: se indica con `responsableId` en la petición. |

---

## 6. Fuera de alcance

- Modificar o eliminar salas, y modificar reservas. Para cambiar una reserva se cancela y se crea otra.
- Reservas recurrentes, capacidad de las salas y notificaciones.
