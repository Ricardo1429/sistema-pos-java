# Restobar POS

Sistema POS (punto de venta) para restaurante-bar. Spring Boot 3.5.16 + Java 21 + Spring MVC (Thymeleaf) + Spring Data JPA + Spring Security.

---

## Requisitos

| Componente | Versión mínima | Notas |
|---|---|---|
| JDK | 21+ | Compilado con `maven.compiler.release=21`. Verificado con **JDK 26.0.2** (`C:\Program Files\Java\jdk-26.0.2`), que es el JDK real instalado en esta máquina. |
| Maven | 3.8+ | **No hace falta instalarlo**: el proyecto incluye Maven Wrapper. Usa `.\mvnw.cmd` (Windows) o `./mvnw` (Linux/macOS). |
| Base de datos | PostgreSQL (dev y cloud) | Driver `org.postgresql:postgresql:42.7.14` incluido como dependencia `runtime`. |

El perfil `dev` requiere un servidor PostgreSQL local con la base de datos `restobar` creada.

> **Si `JAVA_HOME` apunta a un JRE o a una ruta inexistente**, Maven falla antes de compilar. Comprueba con `echo $env:JAVA_HOME`; debe ser la raíz de un **JDK** (no `jre...`), por ejemplo `C:\Program Files\Java\jdk-26.0.2`.

---

## Ejecución

### Perfil `dev` (desarrollo local)

```bash
mvn -B spring-boot:run
# o bien:
java -jar target/pos-1.0.0.jar --spring.profiles.active=dev
```

Si no tienes Maven instalado, usa el wrapper que ya viene en el repositorio:

```bash
.\mvnw.cmd -B clean verify      # Windows
./mvnw -B clean verify          # Linux / macOS
.\mvnw.cmd -B spring-boot:run
```

- **Puerto: `http://localhost:8081`** (`server.port=8081`). Se eligió 8081 porque XAMPP/Tomcat ocupa el 8080 en esta máquina; si necesitas 8080, cambia `server.port` en `src/main/resources/application.properties`.
- El goal `spring-boot:run` ya activa el perfil `dev` por su cuenta (configurado en el `pom.xml`), así que basta con `mvn spring-boot:run`. **El `java -jar` NO hereda ese perfil**: sin `--spring.profiles.active` falla de forma intencionada.
- Base de datos: servicio **PostgreSQL local** con la BD `restobar` creada. `DB_URL` (default `jdbc:postgresql://localhost:5432/restobar`) y `DB_USER` (default `postgres`) son opcionales; `ddl-auto=update`.
- **`DB_PASSWORD` se lee automáticamente** de `%USERPROFILE%\.config\restobar\pos-dev.properties` (fichero fuera del repo, importado por `application-dev.properties`). Así arranca igual desde Eclipse/STS, la terminal o el jar, sin exportar variables. Si defines `DB_PASSWORD` como variable de entorno, esa tiene prioridad. Si el fichero y la variable faltan, el arranque falla (fail-fast).
- `SEED_PASSWORD` es **opcional**: si no se define se usa el default de desarrollo `restobar2026`.

```powershell
.\mvnw.cmd -B spring-boot:run
```

Alternativa en esta máquina: `.\arrancar.ps1` fija el JDK y usa el mismo fichero de password.

### Perfil `cloud` (producción)

```bash
export DB_URL='jdbc:postgresql://host:5432/restobar'
export DB_USER='usuario'
export DB_PASSWORD='clave'
export SEED_PASSWORD='clave-de-sistema'

java -jar target/pos-1.0.0.jar --spring.profiles.active=cloud
```

Las cuatro variables son **obligatorias**. Si falta cualquiera de ellas el arranque **falla de forma inmediata** (fail-fast), no se crea ninguna base de datos ni usuario con claves conocidas:

- `SEED_PASSWORD` → `PlaceholderResolutionException: Could not resolve placeholder 'SEED_PASSWORD' in value "${SEED_PASSWORD}"`.
- `DB_URL` / `DB_USER` / `DB_PASSWORD` → `Error creating bean with name 'dataSource' ... Failed to instantiate [com.zaxxer.hikari.HikariDataSource]: Factory method 'dataSource' threw exception with message: 'url' must start with "jdbc"`.

Notas del perfil `cloud`:

- `spring.jpa.hibernate.ddl-auto=validate`: la aplicación **no** crea ni modifica el esquema. El esquema debe existir y estar migrado **por fuera** (scripts o herramienta de migración) antes de arrancar; si no, el arranque falla con el error de validación de Hibernate.
- Consola H2 deshabilitada; el pool Hikari está acotado (`maximum-pool-size=5`, `minimum-idle=1`).

### Sin perfil explícito (no soportado)

Arrancar sin `--spring.profiles.active` **falla deliberadamente**:

```
APPLICATION FAILED TO START
Description:
Failed to configure a DataSource: no embedded datasource could be configured.
Reason: Failed to determine a suitable driver class
```

No hay perfil por defecto: sin `dev` ni `cloud` no existe datasource configurado y no se permite el arranque con una base de datos embebida efímera.

---

## Cuentas demo

Creadas por `DataSeeder` en el primer arranque sobre una base de datos vacía. Todas comparten la misma contraseña: la de `SEED_PASSWORD` (en `dev`, el default `restobar2026`).

| Usuario | Nombre | Rol | Permisos principales |
|---|---|---|---|
| `admin` | Administrador | `ADMIN` | Acceso total (incluye `/menu`, `/caja`). |
| `supervisor1` | Marta Supervisora | `SUPERVISOR` | `/menu`, `/cocina`, `/caja`, `/mesas`, `/ordenes`. |
| `mesero1` | Juan Mesero | `MESERO` | `/mesas`, `/ordenes`. |
| `cocina1` | Ana Cocina | `COCINERO_BARMAN` | `/cocina`. |
| `cajero1` | Carlos Cajero | `CAJERO` | `/caja`, `/mesas`, `/ordenes`. |

---

## Endpoints principales

| Método | Ruta | Descripción | Acceso |
|---|---|---|---|
| GET | `/login` | Formulario de login | público |
| POST | `/login` | Autenticación (Spring Security form login) | público |
| GET | `/` | Dashboard | autenticado |
| GET | `/mesas` | Mapa de mesas por zona | `ADMIN`, `MESERO`, `CAJERO`, `SUPERVISOR` |
| POST | `/mesas/nueva`, `/mesas/{id}/abrir`, `/mesas/{id}/por-cobrar`, `/mesas/{id}/liberar`, `/mesas/{id}/eliminar` | Gestión de mesas y apertura/ciclo de órdenes | `ADMIN`, `MESERO`, `CAJERO`, `SUPERVISOR` |
| GET | `/ordenes` | Listado de órdenes | `ADMIN`, `MESERO`, `CAJERO`, `SUPERVISOR` |
| GET | `/ordenes/{id}/pos` | Terminal POS de la orden | `ADMIN`, `MESERO`, `CAJERO`, `SUPERVISOR` |
| GET/POST | `/ordenes/nueva`, `/ordenes/{id}/agregar`, `/ordenes/{id}/enviar-cocina`, `/ordenes/{id}/anular`, `/ordenes/{id}/detalles/{detalleId}/anular` | Ciclo de la orden | `ADMIN`, `MESERO`, `CAJERO`, `SUPERVISOR` |
| GET | `/cocina` | KDS de cocina | `ADMIN`, `COCINERO_BARMAN`, `SUPERVISOR` |
| POST | `/cocina/detalles/{id}/iniciar`, `/listo`, `/entregado`, `/cocina/ordenes/{id}/lista` | Estados de preparación | `ADMIN`, `COCINERO_BARMAN`, `SUPERVISOR` |
| GET | `/caja` | Panel de facturación (`?ordenId=`) | `ADMIN`, `CAJERO`, `SUPERVISOR` |
| POST | `/caja/turno/abrir`, `/caja/turno/cerrar`, `/caja/cobrar` | Turno de caja y cobro | `ADMIN`, `CAJERO`, `SUPERVISOR` |
| GET | `/caja/ticket/{id}` | Tirilla de venta | `ADMIN`, `CAJERO`, `SUPERVISOR` |
| GET | `/menu`, `/menu/nuevo`, `/menu/{id}/editar` | Consulta y edición del menú | `ADMIN`, `SUPERVISOR` |
| POST | `/menu/guardar`, `/menu/{id}/eliminar`, `/menu/{id}/reponer`, `/menu/{id}/disponibilidad` | Escritura del menú y stock | `ADMIN`, `SUPERVISOR` |

CSRF está habilitado (todas las peticiones `POST` requieren el token `_csrf`).

---

## Arquitectura

**Rich Domain Model sin capa de *service*.** No existe una capa `service/` intermedia: la lógica de negocio vive íntegramente en las entidades de `model/`, que son objetos con comportamiento (métodos de transición de estado y reglas). Los controladores orquestan y delegan; los repositorios (`repository/`) acceden a la persistencia.

```
src/main/java/com/restobar/pos/
├── model/        Entidades JPA = modelo de dominio con reglas (Rich Domain Model)
├── repository/   Spring Data JPA (7 repositorios)
├── controller/   Spring MVC (@Controller + Thymeleaf)
├── config/       SecurityConfig, DataSeeder, GlobalExceptionHandler, resolvers
└── exception/    ReglaNegocioException
```

- `model/`: `Orden`, `Mesa`, `Producto`, `Usuario`, `TurnoCaja`, `TicketVenta`, `DetalleOrden` y sus enums (`EstadoOrden`, `EstadoMesa`, `EstadoTurno`, `EstadoItem`, `Rol`, `CanalOrden`, `CategoriaProducto`, `MetodoPago`, `ZonaMesa`).
- Las reglas de negocio (RN-01..RN-05) están en las entidades y se validan con el harness `src/test/java/com/restobar/pos/harness/SDDVerificationHarnessTest.java` (22 pruebas).
- `config/GlobalExceptionHandler`: traduce las excepciones a respuestas 4xx/5xx. Entrada no válida (tipo no convertible, parámetro ausente, cuerpo JSON ilegible) → **400** con mensaje genérico; el detalle se registra solo en el log del servidor.

### Configuración por perfil

| Fichero | Contenido |
|---|---|
| `src/main/resources/application.properties` | Base común. Sin perfil por defecto, sin datasource, sin password de seed, `spring.h2.console.enabled=false`. |
| `src/main/resources/application-dev.properties` | PostgreSQL local vía `DB_URL`/`DB_USER` (defaults `jdbc:postgresql://localhost:5432/restobar` y `postgres`), `DB_PASSWORD` importado de `%USERPROFILE%\.config\restobar\pos-dev.properties` (fail-fast si falta), `ddl-auto=update`, `app.seed.password=${SEED_PASSWORD:restobar2026}`. |
| `src/main/resources/application-cloud.properties` | PostgreSQL vía `DB_URL`/`DB_USER`/`DB_PASSWORD`, `ddl-auto=validate`, `app.seed.password=${SEED_PASSWORD}` (sin default → fail-fast). |

---

## Riesgos de seguridad aceptados

Los siguientes riesgos han sido evaluados y **aceptados conscientemente** (no se pueden resolver sin un salto mayor de versión o un rediseño fuera del alcance actual):

1. **Spring Framework 6.2.19 sin parche para CVE-2026-47884 (XsltView) y CVE-2026-47890 (SSE).**
   No existe versión corregida en la línea 6.2.x; el parche se publica solo en Spring Boot 4 (7.0.9), lo que implicaría un salto mayor de plataforma. **No son explotables en esta configuración**: la aplicación no usa `XsltView` (la capa de vistas es Thymeleaf, sin transformaciones XSLT) ni Server-Sent Events (no hay ningún endpoint SSE). Se acepta el riesgo con el compromiso de migrar a Spring Boot 4 en la siguiente fase mayor.
2. **Ausencia de rate limiting en el login.**
   El formulario de login de Spring Security no tiene límite de intentos, por lo que es susceptible a fuerza bruta de credenciales. No se ha añadido rate limiting porque queda fuera del alcance de esta iteración y exigiría dependencias adicionales. Mitigación actual: contraseñas con BCrypt y limitación de intentos por mecanismo de infraestructura (proxy/WAF) en el despliegue real.
3. **Modelo de recursos compartidos.**
   No hay aislamiento por zona ni por turno: cualquier `CAJERO` o `SUPERVISOR` autenticado puede ver las órdenes de salón (`/ordenes`) y todos los tickets de venta. No se implementa control de acceso por recurso/orden. Se acepta como decisión de diseño del negocio (recepción compartida), con la trazabilidad de auditoría como mitigación.

Riesgos ya mitigados en esta iteración: password de seed por defecto embebida en el jar (F-01), arranque sin perfil con consola H2 abierta (F-02), dependencias con CVEs abiertos de Tomcat y PostgreSQL (F-03) y respuestas 500 en lugar de 400 ante entrada no validada (F-04).

---

## Troubleshooting

**`[ERROR] Unknown lifecycle phase "mvn"`**
Maven recibió la palabra `mvn` como si fuera una fase del ciclo de vida. El primer argumento tras el ejecutable debe ser una fase (`clean`, `test`, `package`, `verify`, …) o un goal con formato `plugin:goal`. Causas habituales:
- El ejecutable se duplicó: `mvn mvn clean verify` o `& '…\mvn.cmd' mvn -B clean verify`.
- El comando se pegó en un campo de "argumentos" de un IDE/run configuration que ya prefija `mvn`.
- Un parámetro `-Dclave=valor` se partió por un quoting incorrecto del shell (el valor se interpreta como fase).

Forma correcta en PowerShell (si Maven no está en el PATH, usa el wrapper):

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-26.0.2'
cd C:\restaurante-bar
.\mvnw.cmd -B clean verify
```

**`Failed to determine a suitable driver class` / `no embedded datasource could be configured`**
Falta `--spring.profiles.active=dev` (o `cloud`). No hay perfil por defecto por diseño.

**`Could not resolve placeholder 'DB_PASSWORD'` (perfil `dev`)**
En `dev`, `DB_PASSWORD` se importa de `%USERPROFILE%\.config\restobar\pos-dev.properties` (línea `DB_PASSWORD=...`). Si ese fichero no existe, define la variable en el entorno (`$env:DB_PASSWORD='<password-local>'`). Comprueba también que el servicio PostgreSQL local esté corriendo y que la BD `restobar` exista; `DB_URL` y `DB_USER` ya tienen defaults locales.
