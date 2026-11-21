# Guía de ejecución — gestor-tareas-ci

Paso a paso del ejercicio. Cada parte dice qué capturar (Evidencia 1 a 6) y qué anotar en el reporte (Word).

Repo: https://github.com/Cristian-Oropeza/gestor-tareas-ci

---

## Requisitos

- Docker Desktop corriendo
- Git
- Navegador para ver Grafana y GitHub Actions
- No necesitas Node instalado; usamos contenedores

> **Nota importante para PowerShell**: curl.exe tiene problemas con las comillas dobles dentro de JSON cuando lo mandas desde PowerShell. Para peticiones POST usa `Invoke-RestMethod` (es nativo de PowerShell). Está indicado abajo.

---

## Parte 1 — Pruebas y contenedor (local)

Desde la raíz del proyecto:

```powershell
# 1. Instalar dependencias y correr pruebas en un contenedor temporal
docker run --rm -v "${pwd}:/app" -w /app node:22-alpine npm install
docker run --rm -v "${pwd}:/app" -w /app node:22-alpine npm test
```

Debe salir **Tests: 10 passed** y cobertura ≥ 80 %. Esto genera `package-lock.json`, que **sí se sube al repo** (lo usa CI con `npm ci`).

**→ Captura 1 de Evidencia 1**: la salida del `npm test` con la tabla de cobertura.

```powershell
# 2. Construir la imagen y probar los endpoints
docker build -t gestor-tareas:local .
docker run --rm -d --name api -p 3000:3000 gestor-tareas:local

# health
curl.exe http://localhost:3000/health

# crear tarea (Invoke-RestMethod en vez de curl para evitar bug de comillas)
Invoke-RestMethod -Uri http://localhost:3000/tasks -Method Post -ContentType 'application/json' -Body '{"title":"Mi primera tarea"}'

# listar
curl.exe http://localhost:3000/tasks

# metrics (con -s para que no se vea la barra de progreso)
curl.exe -s http://localhost:3000/metrics | Select-Object -First 20
```

**→ Captura 2 de Evidencia 1**: la salida de `curl.exe -s http://localhost:3000/metrics | Select-Object -First 20`.

Al terminar:
```powershell
docker stop api
```

Mensaje de commit sugerido: `feat: API de tareas con pruebas y Dockerfile`

---

## Parte 2 — CI con GitHub Actions + falla controlada

### 2.1 Primer push (todo verde)

```powershell
git init
git add .
git commit -m "feat: API de tareas con pruebas y Dockerfile"
git branch -M main
git remote add origin https://github.com/Cristian-Oropeza/gestor-tareas-ci.git
git push -u origin main
```

Entra a la pestaña **Actions** en GitHub. Los dos jobs (`pruebas` e `imagen-docker`) deben estar en verde. En **Summary** aparece la tabla de resumen; más abajo el artefacto `reportes-pruebas`.

**→ Evidencia 2**: captura de la ejecución en verde con la tabla de resumen visible.

### 2.2 Falla controlada

```powershell
git checkout -b prueba/fallo-controlado
```

Edita `tests/tasks.test.js`, en la prueba **GET /health responde ok**, cambia:
```js
expect(res.status).toBe(200);
```
por:
```js
expect(res.status).toBe(201);
```

Sube la rama y abre un **pull request** hacia `main`:
```powershell
git commit -am "test: falla controlada para seguimiento"
git push -u origin prueba/fallo-controlado
```

En GitHub abres PR. CI se pone en rojo. Entra a **Details** del paso "Ejecutar pruebas con cobertura" y localiza: prueba que falló, valor esperado, valor recibido, línea del archivo. El job `imagen-docker` debe aparecer **omitido** (porque depende de `pruebas`).

**→ Captura 1 de Evidencia 3**: PR con CI en rojo y el detalle del error.

Corrige la prueba (déjala en `toBe(200)`), haz commit y push de nuevo:
```powershell
git commit -am "fix: regresar prueba de /health a 200"
git push
```

CI vuelve a verde. Haz el **merge** del PR.

**→ Captura 2 de Evidencia 3**: PR con CI en verde tras la corrección.

### 2.3 Proteger la rama (recomendado)

En GitHub: **Settings → Rules → Rulesets** (o Branches → Branch protection rules). Crea regla para `main` que exija PR y que el check `pruebas` pase antes del merge.

### 2.4 Bitácora (se llena en el Word)

Tienes la tabla de 10 casos de prueba y la bitácora de 3 ejecuciones en el Word. Rellena las columnas "Resultado en CI" y las fechas/commits reales.

---

## Parte 3 — Monitoreo con Prometheus y Grafana

```powershell
docker compose up -d --build
docker compose ps
```

Los tres servicios (`api`, `prometheus`, `grafana`) deben estar `running`. Si alguno se cae: `docker compose logs <servicio>`.

### 3.1 Confirmar Prometheus

1. Abre http://localhost:9090/targets → el objetivo `gestor-tareas` debe estar en **UP**.
2. En http://localhost:9090/graph ejecuta la consulta `up`; debe devolver 1.
3. Genera tráfico de prueba en PowerShell:
   ```powershell
   for ($i=1; $i -le 30; $i++) { curl.exe -s http://localhost:3000/tasks > $null; curl.exe -s http://localhost:3000/reporte > $null }
   ```
4. En Prometheus ejecuta `http_request_duration_seconds_count` y confirma series para `/tasks` y `/reporte`.

### 3.2 Tablero en Grafana

1. Entra a http://localhost:3001 con usuario `admin` y contraseña `admin` (puedes omitir el cambio de contraseña).
2. **Connections → Data sources → Prometheus → Test**: debe decir que la conexión fue exitosa.
3. Crea un tablero nuevo (**Dashboards → New → New dashboard**) llamado **Gestor de tareas**.
4. Agrega un panel por cada fila. Para cada uno: **Add visualization**, origen `Prometheus`, editor en modo **Code**, pega la consulta, escribe el título y configura la unidad.
5. Guarda con rango **Last 15 minutes** y refresh **5s**.

| Panel | Consulta PromQL | Unidad |
|---|---|---|
| Peticiones por segundo | `sum by (route) (rate(http_request_duration_seconds_count[1m]))` | req/s |
| Latencia p95 por ruta | `histogram_quantile(0.95, sum by (le, route) (rate(http_request_duration_seconds_bucket[1m])))` | segundos |
| Errores 5xx por segundo | `sum(rate(http_request_duration_seconds_count{status_code=~"5.."}[1m])) or vector(0)` | req/s |
| Uso de CPU del proceso | `rate(process_cpu_seconds_total[1m])` | núcleos |
| Retardo del bucle de eventos (p99) | `nodejs_eventloop_lag_p99_seconds` | segundos |
| Memoria residente | `process_resident_memory_bytes` | bytes |

**→ Captura 1 de Evidencia 4**: http://localhost:9090/targets con el objetivo en UP.
**→ Captura 2 de Evidencia 4**: tablero de Grafana con los 6 paneles mostrando datos.

---

## Parte 4 — Evaluación de rendimiento con k6 (medición "antes")

```powershell
docker compose up -d --build
docker compose restart api
Start-Sleep -Seconds 10
docker compose run --rm k6
```

Mientras corre, mantén abierto el tablero de Grafana y observa los paneles.

Al terminar, anota del resumen de k6:
- `http_req_duration` p(95) global y por tag `{ name:listar }` y `{ name:reporte }`
- `http_req_failed` (%)
- `http_reqs` (totales y por segundo)
- Sección `thresholds`: cuáles pasaron (✓) y cuáles no (✗)

Rellena la **Tabla "antes"** en el Word y responde las **3 preguntas de diagnóstico** + la **hipótesis**.

**→ Captura 1 de Evidencia 5**: resumen de k6 con los thresholds.
**→ Captura 2 de Evidencia 5**: tablero de Grafana durante la prueba de carga.

---

## Parte 5 — Plan de mejora

### 5.1 Plan en el Word

Llena la tabla M-01 / M-02 con tus datos reales (el p95 que mediste, etc.). M-03 se llena hasta el paso 5.5.

### 5.2 Prueba de regresión primero

```powershell
git checkout main
git pull
git checkout -b mejora/reporte-rapido
```

En `tests/tasks.test.js`, dentro del `describe('Reporte', ...)`, agrega:

```js
test('GET /reporte responde en menos de 50 ms (regresión de rendimiento)', async () => {
  const inicio = process.hrtime.bigint();
  const res = await request(app).get('/reporte');
  const ms = Number(process.hrtime.bigint() - inicio) / 1e6;
  expect(res.status).toBe(200);
  expect(ms).toBeLessThan(50);
});
```

Push y abre PR. **CI debe fallar en rojo** con el código actual (eso prueba que M-01 existe). Si en tu equipo la prueba pasa, baja el límite a 20 ms.

```powershell
git commit -am "test: regresion de rendimiento para /reporte"
git push -u origin mejora/reporte-rapido
```

Registra esta ejecución roja en la bitácora de la Parte 2 (en el Word).

### 5.3 Aplicar la mejora

En `src/app.js` reemplaza la función `calcularReporte` por:

```js
// Versión 2 del reporte: solo cuenta las tareas, sin el ciclo heredado.
function calcularReporte() {
  const total = tareas.length;
  const hechas = tareas.filter((t) => t.done).length;
  return { total, hechas, pendientes: total - hechas };
}
```

```powershell
git commit -am "perf: elimina ciclo innecesario de calcularReporte"
git push
```

CI se pone verde (incluyendo la prueba de regresión). Haz merge del PR y actualiza tu main local.

### 5.4 Medir "después" (misma condición que Parte 4)

```powershell
git checkout main
git pull
docker compose up -d --build
docker compose restart api
Start-Sleep -Seconds 10
docker compose run --rm k6
```

Rellena la **Tabla antes vs después** en el Word y actualiza el **Estado** de M-01 y M-02 a Cumplida / Parcial / No cumplida.

### 5.5 Nuevo ciclo (M-03)

Observa el tablero con la mejora aplicada y detecta un **hallazgo nuevo** (por ejemplo: la memoria sube mientras se acumulan tareas, o el tamaño de respuesta de /tasks crece). Regístralo como M-03 en el Word con su evidencia, causa probable y acción propuesta. **No es necesario implementarla.**

**→ Captura 1 de Evidencia 6**: resumen de k6 "después".
**→ Captura 2 de Evidencia 6**: tablero de Grafana con la mejora visible.

---

## Al terminar

Limpia el entorno local:
```powershell
docker compose down -v
```

Exporta el Word a PDF (Archivo → Exportar → Crear PDF) y súbelo junto con el link del repo.

---

## Resumen de mensajes de commit

| Momento | Mensaje |
|---|---|
| Primer push | `feat: API de tareas con pruebas y Dockerfile` |
| Falla controlada | `test: falla controlada para seguimiento` |
| Corregir falla | `fix: regresar prueba de /health a 200` |
| Prueba de regresión | `test: regresion de rendimiento para /reporte` |
| Mejora aplicada | `perf: elimina ciclo innecesario de calcularReporte` |
