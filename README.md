# Marcador Automático CRM (v4)

App de marcado automático para tus propios leads. Analiza un Excel, detecta negocio/teléfono/
sitio web/calificación/reseñas, organiza cada Excel importado como su propia carpeta, llama
uno por uno respetando la SIM que elijas, y sincroniza todo con tu propio Google Sheets y
Google Calendar — sin ningún servidor intermedio.

## Qué hay en esta versión (v11)

### Lo nuevo de esta vuelta: animaciones
- **Al cambiar de pestaña** (Carpetas/Interesados/Programados/Ajustes): la sección entra con
  un fundido + deslizamiento suave hacia arriba, y las tarjetas de la lista aparecen una tras
  otra (escalonadas), no todas de golpe.
- **Cuando el estado de un contacto cambia de verdad** (ej: de "Llamando" a "Interesado"), su
  insignia de estado hace un pequeño "pop" (crece y vuelve a su tamaño) para que se note. Solo
  anima cuando el estado REALMENTE cambió para ese contacto — no cada vez que se redibuja la
  lista por scroll o por otro motivo.
- **Pantalla de "Analizando tu Excel"**: ahora es una de verdad, con un ícono circular girando
  (antes solo era un mensaje rápido tipo aviso). Cubre la pantalla mientras la IA analiza y
  desaparece sola al terminar.

### Lo nuevo de esta vuelta: límite diario de interesados
- **Límite de "Interesados" por día** (Ajustes, 0 = sin límite): cuenta TODAS tus carpetas
  juntas. Al llegar al número que pongas, la cola se pausa sola — usando el mismo mecanismo
  de Pausar/Reanudar de siempre, así que la llamada en curso no se interrumpe.
- **Se queda pausado hasta que TÚ le des "Reanudar"** — no se reanuda solo al día siguiente.
  Y si decides reanudar el mismo día aunque ya hayas llegado al límite, la app respeta tu
  decisión y no te vuelve a interrumpir por esto el resto del día.
- **Orden al reanudar**: los contactos que YA tienen hora programada (Programados, día 2/3)
  ahora se llaman antes que los pendientes nuevos del Excel — aplica siempre, no solo después
  de una pausa por límite.

### Lo nuevo de esta vuelta: pantalla de bienvenida
- **Primera vez que abres la app**: aparece tu logo con una animación (entra con un pequeño
  rebote), y luego el nombre "Samuel Web Leads Dialer" se escribe letra por letra. Después pasa
  a una pantalla que invita a iniciar sesión con Google — con un botón "Ahora no" para saltarla
  (sigue siendo 100% opcional, igual que en el resto de la app; puedes conectarla después
  desde Ajustes cuando quieras).
- **De ahí en adelante**: el splash es rapidísimo (medio segundo o menos) — logo y nombre
  aparecen juntos con un fundido corto, y entra directo a Carpetas. Ya no vuelve a mostrar la
  pantalla de bienvenida.
- Técnicamente: se agregaron `SplashActivity` (la que abre primero ahora) y `OnboardingActivity`
  (solo primera vez); `MainActivity` sigue igual, solo que ya no es la pantalla de arranque.

### Lo nuevo de esta vuelta
- **"Programados" separa por día, incluido el día 3**: "🗓 Segundo intento (día 2)" arriba,
  luego "⚠️ Tercer intento (día 3 — el definitivo)" con un aviso de que si vuelve a fallar ahí
  se descarta solo, y abajo "Todos los programados" con la lista completa. El número de días
  se lee de Ajustes (si lo cambias de 3 a otro número, esta sección se ajusta sola).
- **Se corrigió un bug real de compilación**: `item_batch.xml` e `item_contact.xml` usaban
  atributos `app:` (de Material Design) sin declarar ese namespace — eso habría hecho fallar
  la compilación en Android Studio. Ya quedó arreglado, y de paso revisé y validé la sintaxis
  de TODOS los archivos XML del proyecto.

### Lo nuevo de esta vuelta
- **Ficha completa por contacto**: tocas cualquier contacto (en Carpetas o Interesados) y ves
  todo — teléfono, sitio web, calificación, WhatsApp si lo diste, TODAS las columnas del Excel
  que no se usaron para nada más, y el historial completo de cada intento con fecha y hora.
- **Carpetas archivadas**: cuando una carpeta ya no tiene NINGÚN contacto pendiente (todos
  contestados, descartados, etc.), se mueve sola a una sección aparte "Carpetas archivadas",
  debajo de las que todavía tienen trabajo pendiente.
- **Columna nueva en Sheets: "Carpeta completa"** (G): dice "Sí" o "No". Se pone en "Sí" en
  TODAS las filas de esa carpeta automáticamente, el momento en que ya no queda nadie
  pendiente — para que lo puedas filtrar directamente desde Google Sheets.
- **Editar la hora programada** desde la pestaña "Programados": antes solo se podía ver; ahora
  tocas el contacto y eliges una hora nueva ahí mismo, sin tener que esperar a que llegue la
  hora vieja.

### Lo nuevo de esta vuelta: control manual de la cola
- **Pausar / Reanudar** (botón nuevo, separado de "Detener"): pausa sin tocar la llamada que
  esté en curso — solo evita que arranque la SIGUIENTE. Al reanudar no vuelve a preguntar SIM
  ni permisos, sigue exactamente donde iba.
- **Saltar este contacto**: pasa al siguiente sin esperar a que termine de sonar. El contacto
  saltado vuelve a Pendiente (no gasta uno de sus 2 intentos del día).
- **Arreglo de un bug real**: antes, si tocabas "Detener" justo cuando había una llamada activa,
  ese contacto se quedaba trabado en "Llamando…" para siempre (nunca se sabía qué pasó). Ahora
  vuelve a Pendiente automáticamente, como cualquier otro. (Ojo: Android sigue sin dejar que
  ninguna app cuelgue la llamada de otra app — eso no se puede evitar; lo que se arregló es que
  el contacto ya no se pierde en el limbo.)

### Lo nuevo de esta vuelta
- **Elegir tu hoja de Sheets de una lista real** (Ajustes → Conectar Sheets): ya no hace falta
  pegar el enlace — se listan tus hojas de Google Drive de verdad (por nombre) y tocas la que
  quieras. Si prefieres, sigue estando la opción de pegar el enlace a mano.
- **Recordatorio de Calendar 100% manual**: se quitó lo automático de "30 min después". Ahora,
  al marcar "Interesado", te pregunta fecha y hora exactas (con la cifra de minutos de Ajustes
  solo como sugerencia inicial, no obligatoria) — la cola espera a que confirmes o canceles
  antes de seguir con el siguiente contacto.
- **Número alterno en vivo, durante la llamada**: si te contesta un empleado y te da el número
  directo del dueño, cambias a la app (la llamada sigue activa en segundo plano — así funciona
  Android) y aparece un panel abajo con dos campos separados:
  - **Número directo del dueño** → se guarda como contacto nuevo con prioridad, se sube a
    Sheets, y la cola lo marca automáticamente justo después de que cuelgues la llamada actual.
  - **WhatsApp** → no se llama; se guarda para usarlo en el recordatorio de Calendar si el
    cliente queda interesado. Si no escribes nada, el recordatorio usa el mismo número con el
    que colgaste.
- **Historial de sincronización** (Ajustes → Ver historial): cada intento de sincronizar con
  Sheets o Calendar queda registrado, con ✓ o ✗ y el motivo si falló.

### Nuevo: las 4 mejoras que pediste
- **Pantalla "en vivo" durante la llamada**: una tarjeta flotante (overlay del sistema) con el
  negocio, teléfono, sitio web, calificación, intento y un cronómetro — se dibuja ENCIMA de la
  app de llamadas normal de Android mientras hablas (no reemplaza el marcador, solo le pone un
  panel de información arriba). Se activa en Ajustes → "Activar tarjeta flotante", porque
  requiere el permiso especial "Mostrar sobre otras apps" (el usuario lo concede a mano en la
  configuración del sistema, Android no deja pedirlo con un diálogo normal). Si no lo activas,
  la app funciona igual, solo que sin la tarjeta.
- **Buscador dentro de una carpeta**: filtra en vivo por nombre de negocio o teléfono.
- **Ajustes editables desde la app**: segundos de espera, intentos por día, días de reintento,
  minutos del recordatorio de Calendar — todo con campos numéricos y un botón "Guardar cambios",
  sin tocar código.
- **Dashboard**: tarjeta arriba de "Carpetas" con el total de contactos activos, interesados,
  no interesados y la tasa de contestada — de todas tus carpetas juntas. Ojo: los contactos que
  ya se descartaron por la regla de los 3 días ya no cuentan aquí (siguen en tu Sheets como
  registro histórico, solo que la app local ya no los tiene).

### Navegación en 4 secciones (barra inferior)
- **Carpetas**: cada Excel que importas queda separado, con su nombre, cantidad de contactos
  e interesados. Entras a una para ver/llamar sus contactos, o la borras completa con el ícono
  de basura (esto NO borra nada de tu Sheets — solo la copia local).
- **Interesados**: los negocios marcados como interesados, de la carpeta que tengas abierta.
- **Programados**: los que están esperando que elijas hora, o ya tienen hora puesta para
  mañana, de la carpeta abierta.
- **Ajustes**: cuenta de Google, Sheets, restaurar datos, y un resumen de la configuración.

### Selector de SIM
Cada vez que tocas "Iniciar llamadas", si tu celular tiene más de una línea, te pregunta con
cuál marcar (usa `TelecomManager`, cubre las físicas y cualquier otra habilitada). Si solo hay
una, ni pregunta. Para los reintentos automáticos del día siguiente (que se disparan solos por
una alarma, sin nadie presente), se usa la última SIM que elegiste manualmente.

### Regla de los 3 días
Cada contacto tiene hasta 2 intentos por día. Si no contesta:
- Día 1 sin respuesta → eliges la hora de mañana (día 2).
- Día 2 sin respuesta → eliges la hora del día 3 (el definitivo).
- Día 3 sin respuesta → **se descarta automáticamente de la app**, sin preguntar nada — ya
  cumplió su ciclo y no ocupa espacio para tus contactos nuevos. Su fila en Sheets se queda
  con el estado "descartado" como registro histórico (no se borra de ahí).

Cambiás cuántos días son en `DialerSettings.maxRetryDays` (3 por defecto) si luego quieres otro número.

### La cola ya no se desordena
Antes, mientras elegías la hora de reintento de un contacto, el servicio seguía llamando a
los demás en paralelo. Ahora el servicio **espera** a que resuelvas cada contacto (Interesado/
No interesado, u hora de mañana) antes de marcar al siguiente — todo pasa en orden, uno a la vez.

### Restaurar desde Sheets
En Ajustes hay un botón "Restaurar mis datos desde Sheets": lee lo que ya esté guardado en tu
hoja conectada y lo vuelve a cargar en la app. Pensado para cuando reinstalas la app o cambias
de celular — tu información vive en tu propia cuenta de Google, no en ningún servidor mío. Los
contactos ya "descartados" (que cumplieron sus 3 días) no se vuelven a traer a propósito; el
resto vuelve como Pendiente (no se puede reconstruir la hora exacta programada solo del texto
de la columna Estado, así que se van derecho a la cola de nuevo).

### Tus contactos nunca tocan tu libreta de contactos
Se marca directo por número (`tel:...`), sin usar el Content Provider de Contactos de Android
para nada. Cero mezcla con tu agenda personal.

## Cómo compilar

1. Instala [Android Studio](https://developer.android.com/studio).
2. Abre la carpeta `AutoDialerCRM/` completa como proyecto.
3. Deja que Gradle descargue las dependencias.
4. Conecta tu celular por USB con depuración activada, o usa un emulador.
5. Run ▶️.

No pude compilar esto por ti en este chat (no hay SDK de Android disponible aquí). Revisé a
mano cada referencia (IDs, layouts, strings, drawables) y no encontré nada roto, pero en un
proyecto de este tamaño es normal que aparezca algún detalle al compilar por primera vez —
pégame el error exacto y lo resolvemos.

## Configuración obligatoria para Sheets y Calendar (Google Cloud Console)

1. Crea un proyecto en [Google Cloud Console](https://console.cloud.google.com/).
2. Habilita **Google Sheets API**, **Google Calendar API** y **Google Drive API** (la de Drive
   es nueva en esta versión — hace falta para listar tus hojas por nombre).
3. Configura la **pantalla de consentimiento OAuth** en modo "Externo" + "Prueba", y agrégate
   a ti mismo como usuario de prueba.
4. Crea un **ID de cliente OAuth tipo Android**: paquete `com.tuempresa.autodialer` + tu SHA-1
   (sácalo con `./gradlew signingReport`).

Sin esto, "Conectar Google" da error — es la única parte que requiere tu propia cuenta y no se
puede generar solo con código.

## Estructura del proyecto

- `data/` — Room: contactos (carpeta, ronda de reintento, fila de Sheets, WhatsApp, prioridad
  de cola) + `SyncLogEntity`/`SyncLogDao` (historial de sincronización).
- `excel/` — `ExcelImporter` (filtro de país), `ColumnGuesser` (sugiere columnas),
  `WebsiteClassifier` (sitio propio vs. red social).
- `dialer/` — `DialerService` (cola secuencial por carpeta, ahora con prioridad para números
  alternos), `SimSelector`, `CallOverlayManager` (tarjeta flotante), `DialerEvents`.
- `auth/GoogleAuthManager.kt` — inicio de sesión opcional (Sheets + Calendar + Drive metadata).
- `sync/` — `SheetsSyncManager`, `CalendarReminderManager` (hora manual), `DriveFilePicker`
  (lista tus hojas reales), `SyncCoordinator` (punto único + historial).
- `scheduling/` — alarmas del día siguiente, conscientes de la carpeta y la SIM.
- `ui/` — `MainActivity` (4 secciones + panel de llamada en vivo), `BatchAdapter`,
  `ContactAdapter`, `ContactsViewModel`.

## Ideas para seguir mejorando (dime cuáles te interesan)

- Deshacer al borrar una carpeta por accidente.
- Editar el teléfono de un contacto si la IA se equivocó al importar.
- Historial de sincronización con filtros (solo fallos, por carpeta, etc.).
- Poder cancelar o editar un número alterno ya guardado antes de que se marque.

Dime qué sigue y seguimos construyendo.
