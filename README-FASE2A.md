# Fase 2A — Backend (Spring Boot)

Features agregadas sobre el backend existente:
- 2FA TOTP (Google Authenticator) con setup/activar/desactivar/verificar
- Sesion unica server-side con jti y revocacion en tiempo real
- Login en 2 etapas (password -> preAuthToken -> codigo TOTP -> accessToken)
- Rate limiting por IP (Bucket4j) y account lockout
- Catalogo con sincronizacion diaria desde GestoPago (cron 6am MX)
- Historial /transacciones/mis-compras filtrado por JWT
- OpenAPI/Swagger con esquema bearerAuth

## Correr en local

```powershell
docker-compose up -d
# Copia .env.example a .env y llena los secretos (minimo TOTP_SECRET_ENCRYPTION_KEY)
# Para generar la llave: openssl rand -base64 32
.\gradlew.bat bootRun
```

Swagger: http://localhost:8081/swagger-ui.html

## Deploy en Render

Variables de entorno obligatorias en Render:
- `SPRING_PROFILES_ACTIVE=prod`
- `SPRING_DATASOURCE_URL=jdbc:postgresql://<host>/<db>?sslmode=require`
- `SPRING_DATASOURCE_USERNAME` y `SPRING_DATASOURCE_PASSWORD`
- `JWT_ENABLED=true`
- `JWT_SECRET` (>= 64 chars)
- `TOTP_SECRET_ENCRYPTION_KEY` (32 bytes base64, generar con `openssl rand -base64 32`)
- `GESTOPAGO_ENABLED=true` + credenciales GestoPago

Opcionales (valores default razonables):
- `SESION_INACTIVIDAD_MINUTOS=30`
- `RATE_LIMIT_LOGIN_PER_15MIN=5`
- `LOCKOUT_MAX_INTENTOS=5`
- `TOTP_ISSUER="GestoPago Comercio PWA Oropeza"`

Push a la rama `Dev` activa auto-deploy en Render.

## Endpoints clave (ver Swagger para todos)

| Metodo | Path | Descripcion |
|---|---|---|
| POST | /api/v1/clientes/registrar | Alta de cliente + usuario + cuenta |
| POST | /api/v1/auth/login | Etapa 1: password -> token o preAuthToken |
| POST | /api/v1/auth/2fa/verificar | Etapa 2: preAuthToken + codigo -> token |
| POST | /api/v1/auth/2fa/setup | QR + scratch codes (auth req) |
| POST | /api/v1/auth/2fa/activar | Activa 2FA (auth req) |
| POST | /api/v1/auth/2fa/desactivar | Desactiva 2FA con password (auth req) |
| POST | /api/v1/auth/logout | Revoca la sesion actual |
| POST | /api/v1/auth/sesiones/activas | Lista sesiones del usuario |
| POST | /api/v1/auth/sesiones/cerrar | Cierra una sesion por id |
| POST | /api/v1/catalogo/consultar | Lista productos con filtros |
| POST | /api/v1/catalogo/sincronizar | Fuerza sync desde GestoPago (admin) |
| POST | /api/v1/transacciones | Ejecuta compra |
| POST | /api/v1/transacciones/mis-compras | Historial APLICADAS del usuario |
| POST | /api/v1/transacciones/consulta | Estado por referencia_operacion |
