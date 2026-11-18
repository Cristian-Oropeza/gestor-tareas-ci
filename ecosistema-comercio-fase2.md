# Ecosistema de Comercio Gestopago — Fase 2 (Web + Móvil)

> Documento de contexto para continuar la implementación en un chat nuevo de Claude.
> Autor del proyecto: Cristian Oropeza (`oropezacristian40@gmail.com`).
> Materia: Integración de APIs con Front End y APP móvil — 100 puntos.

---

## 1. Resumen ejecutivo

Se está construyendo un **ecosistema de comercio digital** sobre un backend ya existente,
compuesto por tres piezas:

1. **Backend** (Spring Boot 3.3.6, Java 17) — **ya existe**, se extenderá
2. **Web** (Next.js con App Router) — **a crear**
3. **Móvil** (Flutter con Clean Architecture) — **a crear**

Ambos frontends consumen los mismos endpoints REST. Features críticas:
autenticación con **2FA (TOTP)**, **sesión única** server-side entre web y móvil,
catálogo de productos, compras con **idempotencia**, historial de operaciones.

---

## 2. Estado actual del backend (lo que YA existe)

### Proyecto
- Repo: `C:\PWA\Ejercicios\prueba\prueba` — rama `Dev`
- Deploy: Render (`https://pwa-prueba-1zpt.onrender.com`) con PostgreSQL managed
- Grupo: `com.proyecto.servicios`
- Paquete raíz: `com.proyecto.servicios.onboarding`

### Módulos existentes
- `onboarding/entity/` — `Cliente`, `Cuenta`, `Usuario`, `Domicilio`, enums
- `onboarding/service/` — `ClienteService`, `CuentaService`, `UsuarioService`, `AuthService`
- `onboarding/controller/` — `ClienteController`, `CuentaController`, `UsuarioController`, `AuthController`
- `onboarding/security/` — `JwtService`, `JwtAuthenticationFilter`, `SecurityConfig` (JWT opcional via `JWT_ENABLED`)
- `onboarding/exception/` — `GlobalExceptionHandler` centralizado, excepciones base
- `onboarding/transaccion/` — **módulo nuevo construido en Fase 1**:
  - Integración REAL con GestoPago/PuntoRed: login + AES-256-CBC + XML `<RESPONSE>`
  - Tabla `transacciones` con idempotency_key UNIQUE
  - Bloqueo optimista en `cuentas.version`
  - Estados: PENDIENTE, EN_CONFIRMACION, APLICADA, RECHAZADA, NO_ENVIADA
  - Scheduler de conciliación (confirmTx.do) con flag off
  - Tests: ~90 casos pasando, cobertura 86.7% en SonarQube

### Endpoints existentes
```
POST /api/v1/auth/login
POST /api/v1/clientes/registrar | /consultar | /actualizar | /eliminar
POST /api/v1/cuentas/crear | /consultar | /actualizar
POST /api/v1/usuarios/consultar | /actualizar
POST /api/v1/transacciones            (ejecutar pago)
POST /api/v1/transacciones/consulta   (por referencia_operacion)
```

### Variables de entorno críticas (Render)
```
GESTOPAGO_ENABLED=true
GESTOPAGO_AUTH_PASSWORD=12345678
GESTOPAGO_CIFRADO_LLAVE=HPo7OLqB4Fkk4E2yGOtwqw8H5fHR9kNx67OR4g4UdlA=
GESTOPAGO_CIFRADO_IV=p5ldmBPdd/9pjC0bDC/nSg==
SPRING_DATASOURCE_URL=jdbc:postgresql://<host>/<db>?sslmode=require
SPRING_PROFILES_ACTIVE=prod
JWT_SECRET=<generar uno seguro>
JWT_ENABLED=false (debe pasarse a true en Fase 2)
```

---

## 3. Lo que se construye en Fase 2

### 3.1 Backend — ampliaciones necesarias

#### A. Catálogo de productos (nuevo endpoint)
**Fuente de datos: HÍBRIDO** — scheduler que sincroniza una vez al día desde
`getProductList.do` de GestoPago (máx 3 llamadas/día permitidas) y guarda en tabla local.
El frontend siempre lee de local.

**Esquema sugerido** (agregar a la tabla `catalogo_productos` que ya existe):
```sql
ALTER TABLE catalogo_productos
    ADD COLUMN imagen_url VARCHAR(500),
    ADD COLUMN fecha_sincronizacion TIMESTAMPTZ;
```

**Endpoints**:
```
POST /api/v1/catalogo/consultar       (list con filtros por categoria, busqueda)
POST /api/v1/catalogo/sincronizar     (admin: fuerza llamada a Gestopago)
```

**Scheduler**: ya existe `ConciliacionScheduler`, agregar `CatalogoSyncScheduler`
con cron `0 0 6 * * *` (6am diario).

#### B. 2FA con TOTP (Google Authenticator)
**Librería**: `com.warrenstrange:googleauth:1.5.0` (gratis, sin dependencia externa)

**Esquema nuevo**:
```sql
CREATE TABLE usuario_totp (
    usuario_id      BIGINT PRIMARY KEY REFERENCES usuarios(id),
    secret_base32   VARCHAR(100) NOT NULL,  -- la seed del QR, NUNCA expuesta via API despues del setup
    activado        BOOLEAN NOT NULL DEFAULT FALSE,
    scratch_codes   TEXT,  -- codigos de respaldo, hash bcrypt separados por comas
    intentos_fallidos SMALLINT NOT NULL DEFAULT 0,
    bloqueado_hasta TIMESTAMPTZ,
    fecha_creacion  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    fecha_actualizacion TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

**Endpoints nuevos**:
```
POST /api/v1/auth/2fa/setup       (devuelve QR en data:image + scratch codes; usuario autenticado)
POST /api/v1/auth/2fa/activar     (body: {codigo}; marca activado=true tras validar)
POST /api/v1/auth/2fa/verificar   (body: {correo, codigo}; segunda etapa del login)
POST /api/v1/auth/2fa/desactivar  (body: {password}; requiere confirmacion)
```

**Flujo de login en 2 etapas**:
1. `POST /api/v1/auth/login` con correo+password → si 2FA activado, devuelve `pre_auth_token` (JWT corto, 5 min, scope=2fa-pending)
2. `POST /api/v1/auth/2fa/verificar` con el pre-auth token y código TOTP → devuelve `access_token` real

**Reglas**:
- 3 intentos fallidos → bloqueo 15 minutos
- Validar que el código no se reutilice en la misma ventana (replay protection)
- Secret base32 encriptado en BD (no almacenar en claro) — usar AES con llave en variable de entorno

#### C. Sesión única server-side
**Esquema nuevo**:
```sql
CREATE TABLE sesiones_activas (
    id              BIGSERIAL PRIMARY KEY,
    cliente_id      BIGINT NOT NULL REFERENCES clientes(id),
    jti             VARCHAR(50) UNIQUE NOT NULL,  -- JWT id
    dispositivo     VARCHAR(100),                  -- "web-chrome-windows" o "movil-flutter-android"
    user_agent      VARCHAR(500),
    ip_address      VARCHAR(50),
    fecha_creacion  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ultima_actividad TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    activa          BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE INDEX idx_sesiones_cliente_activa ON sesiones_activas (cliente_id, activa);
```

**Modificaciones a JwtService**:
- Agregar `jti` (JWT id) único por token generado
- Al validar JWT, verificar que el `jti` sigue en `sesiones_activas` con `activa=true`

**Flujo nuevo del login**:
1. Al hacer login (después de 2FA), verificar si existe `sesion activa` del mismo cliente
2. Si existe:
   - Si request viene con `header X-Force-Login: true` → invalidar sesión anterior y crear nueva
   - Si no → responder 409 CONFLICT con payload:
     ```json
     {
       "codigo": 409,
       "mensaje": "Existe una sesion activa en otro dispositivo",
       "detalles": [{"dispositivo":"movil-flutter-android", "ultimaActividad":"2026-11-10T22:00:00Z"}]
     }
     ```
3. El frontend muestra diálogo "¿Cerrar sesión en el otro dispositivo?" → reintenta con `X-Force-Login: true`

**Endpoints nuevos**:
```
POST /api/v1/auth/logout            (invalida la sesion actual via jti)
POST /api/v1/auth/sesiones/activas  (lista las sesiones activas del usuario)
POST /api/v1/auth/sesiones/cerrar   (body: {sesionId}; cierra una sesion especifica)
```

**Interceptor nuevo**: cada request autenticada actualiza `ultima_actividad` (async para no agregar latencia).

**Expiración automática**: scheduler cada 5 min marca `activa=false` las sesiones con `ultima_actividad > ahora - 30 min`.

#### D. Historial de compras (nuevo endpoint)
```
POST /api/v1/transacciones/mis-compras
```
Filtro obligatorio por `clienteId` del JWT (ignorar el que venga en el body).
Solo devolver estado=APLICADA, ordenadas por `fechaConfirmacion DESC`, paginadas.

---

### 3.2 Aplicación Web — Next.js 15 + App Router

**Stack**:
- **Framework**: Next.js 15 con App Router (no Pages Router)
- **Lenguaje**: TypeScript
- **UI**: Material UI v6 o Mantine v7 (ambas responsivas)
- **State**: Zustand (ligero) o Redux Toolkit (más empresarial)
- **HTTP**: `axios` via API routes de Next.js como proxy
- **Validación**: Zod + react-hook-form
- **Icons**: Lucide React

**Estructura de carpetas sugerida**:
```
app/
├── (auth)/
│   ├── login/page.tsx
│   ├── 2fa/page.tsx
│   └── layout.tsx                 (layout sin navbar)
├── (dashboard)/
│   ├── catalogo/page.tsx
│   ├── catalogo/[id]/page.tsx
│   ├── comprar/[productoId]/page.tsx
│   ├── historial/page.tsx
│   ├── sesiones/page.tsx
│   └── layout.tsx                 (layout con navbar + protegido)
├── api/
│   └── [...proxy]/route.ts        (proxy al backend Spring Boot)
├── middleware.ts                  (valida cookie httpOnly en rutas protegidas)
└── layout.tsx                     (raiz)
components/
lib/
├── auth.ts                        (helpers de cookies, TOTP QR renderer)
├── api-client.ts                  (fetch con credenciales)
└── types.ts                       (contratos TypeScript del backend)
```

**Seguridad frontend**:
- JWT en cookie **httpOnly + Secure + SameSite=Strict** (set por API route, no por JS)
- Middleware revisa cookie en rutas `(dashboard)/*`
- Nunca exponer URL del backend al cliente — todo via `/api/[...proxy]`
- CSP header en `next.config.ts`
- Zod valida TODA entrada del usuario antes de mandar al backend

**Deploy local**: `npm run dev` en puerto 3000.

---

### 3.3 Aplicación Móvil — Flutter con Clean Architecture

**Stack**:
- **Framework**: Flutter 3.24+ (Dart 3.5+)
- **Arquitectura**: Clean Architecture con 4 capas
- **State**: BLoC (`flutter_bloc`) — más empresarial y testeable
- **HTTP**: `dio` con interceptores
- **Storage seguro**: `flutter_secure_storage` para tokens
- **Routing**: `go_router` con protección de rutas
- **Validación**: `formz` o manual con TextFormField
- **QR 2FA**: `qr_flutter` para mostrar el QR (en setup)
- **TOTP input**: `pin_code_fields`

**Estructura de carpetas**:
```
lib/
├── core/
│   ├── config/           (constantes, URLs, env)
│   ├── errors/           (ServerException, CacheException, Failure types)
│   ├── network/          (dio client, interceptores de auth y errores)
│   └── utils/
├── features/
│   ├── auth/
│   │   ├── data/
│   │   │   ├── datasources/auth_remote_datasource.dart
│   │   │   ├── models/login_response_model.dart
│   │   │   └── repositories/auth_repository_impl.dart
│   │   ├── domain/
│   │   │   ├── entities/user.dart
│   │   │   ├── repositories/auth_repository.dart
│   │   │   └── usecases/login.dart, verify_2fa.dart, logout.dart
│   │   └── presentation/
│   │       ├── bloc/auth_bloc.dart
│   │       ├── pages/login_page.dart, two_factor_page.dart
│   │       └── widgets/
│   ├── catalogo/         (misma estructura data/domain/presentation)
│   ├── compra/
│   └── historial/
└── main.dart
```

**Seguridad en Flutter**:
- Tokens JWT SIEMPRE en `flutter_secure_storage` (nunca en `SharedPreferences`)
- Interceptor de Dio agrega `Authorization: Bearer $token` y maneja 401 (refresca o relogin)
- Ofuscación del APK en release (`flutter build apk --obfuscate --split-debug-info=/tmp/symbols`)
- No logs con `print()` de tokens o datos sensibles — usar `logger` package con filtro

**Deploy local**: emulador Android o dispositivo físico (APK debug).

---

## 4. Decisiones técnicas tomadas (resumen)

| Decisión | Elegido | Alternativas descartadas |
|---|---|---|
| Framework web | **Next.js 15 (App Router)** | React puro, Flutter Web |
| 2FA | **TOTP (Google Authenticator) con googleauth** | OTP por email, SMS |
| Sesión única | **Tabla `sesiones_activas` server-side con jti** | Solo client-side, no implementar |
| Backend | **Modificar/extender Spring Boot existente** | Consumir solo lo existente |
| Catálogo productos | **Híbrido: scheduler diario + tabla local** | Solo local, solo Gestopago en vivo |
| Imágenes | **Columna `imagen_url` con logos reales** | Placeholder genérico |
| Deploy | **Local-first, Render/Vercel al final** | Deploy continuo desde ya |
| Móvil state mgmt | **BLoC** | Riverpod, Provider |
| Web state mgmt | **Zustand** (recomendado) | Redux Toolkit |
| HTTP Flutter | **Dio con interceptores** | http package |
| Storage móvil | **flutter_secure_storage** | SharedPreferences |

---

## 5. Mapeo del enunciado del profe → implementación

| Requerimiento del profe | Dónde se implementa | Notas |
|---|---|---|
| Login mismo user web+móvil | Backend `AuthService` existente | ✅ Ya existe |
| 2FA obligatorio | Backend nuevo + web + móvil | TOTP con QR |
| Sesión única server-side | Backend `sesiones_activas` + interceptor | Middleware revisa jti activo |
| Expiración credenciales | JWT exp + scheduler cleanup | 30 min inactividad |
| Catálogo con búsqueda/filtros | Backend `POST /catalogo/consultar` + Specifications | Incluir categoría, texto |
| Paginación catálogo | `PaginacionRequest` reutilizable | Ya existe |
| Imágenes | Columna `imagen_url` | URL externas |
| Compra con idempotencia | Backend `/transacciones` | ✅ Ya existe |
| Control doble-click | Frontend: bloqueo botón + idempotencyKey única | Zustand/BLoC flag |
| Timeout ≠ rechazo | Backend ya responde 202 EN_CONFIRMACION | ✅ Ya existe |
| Historial de exitosas | Backend `POST /transacciones/mis-compras` | Filtrado por JWT |
| HTTPS | Render y Vercel lo dan nativo | ✅ |
| No credenciales en código | `.env` ignorado, Render env vars | ✅ Ya aplica |
| Pruebas unitarias | Backend >80% cobertura ya | Agregar para nuevos módulos |
| Pruebas integración | WireMock existente + tests nuevos | Agregar auth 2FA y sesiones |
| Documentación endpoints | Swagger/OpenAPI ya configurado | Agregar nuevos endpoints |
| Git branches | `Dev`, `featureIntegracionPagos`, etc. | Usar PR flow |

---

## 6. Plan de fases sugerido

### Fase 2A — Backend (prioridad, 1 semana)
1. Tabla `usuario_totp` + librería googleauth + endpoints 2FA
2. Tabla `sesiones_activas` + modificar JwtService (jti) + endpoints de sesión
3. Modificar AuthService: flujo 2-etapas + check de sesión única
4. Ampliar `catalogo_productos` (imagen_url, fecha_sync) + endpoint `/catalogo/consultar` + `CatalogoSyncScheduler`
5. Endpoint `/transacciones/mis-compras` filtrado por JWT
6. Tests: subir cobertura de los módulos nuevos ≥80%
7. Documentación OpenAPI/Swagger de endpoints nuevos
8. Deploy a Render con `JWT_ENABLED=true`

### Fase 2B — Web (en paralelo o después del backend, 1 semana)
1. Scaffold Next.js 15 + TypeScript + Material UI/Mantine
2. Login + flujo 2FA con QR scanner visual
3. Middleware de protección de rutas
4. Catálogo con búsqueda, filtros, paginación
5. Flujo de compra con idempotency key UUID generada en cliente
6. Historial
7. Página de sesiones activas
8. Deploy Vercel

### Fase 2C — Móvil (1-2 semanas)
1. Scaffold Flutter con Clean Architecture
2. Capa `core/` (dio, errors, config)
3. Feature `auth` completa (login + 2FA)
4. Feature `catalogo` con pagination
5. Feature `compra` con control anti-doble-click
6. Feature `historial`
7. Pantalla de sesiones
8. APK release con ofuscación

### Fase 2D — Testing final e integración (3-5 días)
1. Test funcional del escenario del profe (ver sección 7)
2. Pruebas de penetración básicas (XSS, CSRF, JWT manipulation)
3. Documentar decisiones técnicas en README
4. Grabar demo para la presentación

---

## 7. Escenarios de evaluación (los que el profe va a probar)

1. **Login simultáneo**: móvil → web → debe pedir cerrar la anterior
2. **Doble click en comprar**: 1 sola transacción en BD, misma `referencia_operacion`
3. **Interrupción de red durante compra**: estado queda en EN_CONFIRMACION, scheduler concilia
4. **Acceso sin auth a ruta protegida**: redirect a login en web, Guard en móvil
5. **Sesión expirada**: 401 → logout automático
6. **2FA sin código válido**: 3 intentos → bloqueo 15 min
7. **Consistencia catálogo web ↔ móvil**: misma lista, mismos precios
8. **Historial solo del usuario autenticado**: no leak de otros usuarios
9. **XSS en algún input**: Zod + sanitización
10. **Token robado de localStorage**: no debería existir — cookie httpOnly

---

## 8. Librerías Spring Boot nuevas a agregar

```gradle
// 2FA TOTP
implementation 'com.warrenstrange:googleauth:1.5.0'

// Generación de QR (para devolver en el response del setup 2FA)
implementation 'com.google.zxing:core:3.5.3'
implementation 'com.google.zxing:javase:3.5.3'

// Para cifrar el secret TOTP en BD (opcional, se puede usar Jasypt o custom)
implementation 'org.jasypt:jasypt:1.9.3'
```

---

## 9. Preguntas abiertas (resolver en el próximo chat)

Las siguientes decisiones las puede tomar el próximo chat de forma autónoma:

1. **Zustand vs Redux Toolkit** para la web — Zustand recomendado
2. **Material UI vs Mantine** — ambas sirven, UI principalmente
3. **BLoC vs Cubit** para Flutter — Cubit es BLoC simplificado, puede empezar por ahí
4. **Nombre del proyecto web** y **móvil** (si son repos separados o monorepo)
5. **Estrategia de versionado de API** — ¿agregar `/api/v2/` para endpoints nuevos o mantener v1?
6. **Qué email usar para pruebas de notificación** (si se agrega notificación de nuevo login por email)

---

## 10. Repos y referencias

- Backend actual: `C:\PWA\Ejercicios\prueba\prueba` (rama `Dev`)
- GitHub: `Cristian-Oropeza/Prueba-Oropeza_Yepiz`
- Deploy backend: https://pwa-prueba-1zpt.onrender.com
- SonarQube local: http://localhost:9000/dashboard?id=Proyecto-Transacciones-Gestopago
- Doc de la Fase 1 (transacciones Gestopago): el chat actual lo tiene todo

---

## 11. Checklist de inicio para el próximo chat

El próximo chat debe:

- [ ] Leer este documento completo
- [ ] Verificar estado del backend corriendo con `.\gradlew.bat bootRun`
- [ ] Verificar SonarQube al 86.7% de la Fase 1
- [ ] Preguntar al usuario con qué fase quiere empezar (recomendado: 2A backend)
- [ ] Antes de escribir código, listar los archivos nuevos que va a crear
- [ ] Mantener la cobertura ≥80% para código nuevo
- [ ] Hacer commits atómicos por feature

---

## Autor

Cristian Oropeza — Universidad Tecnológica del Norte de Guanajuato  
Materia: Aplicaciones Web Progresivas / Integración de APIs  
Profesor: Francisco Hasaní Barrera Portilla
