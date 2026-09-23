# Walkthrough - Fase 5: Selección de SIM

Se ha implementado el sistema de selección de SIM profesional y resiliente, permitiendo configurar una "SIM de trabajo" o preguntar en cada ciclo, con detección automática de disponibilidad.

## Cambios realizados

### Núcleo de Selección de SIM
- **Detección Enriquecida**: `SimSelector.kt` ahora utiliza `SubscriptionManager` para identificar el slot de la SIM (SIM 1, SIM 2) y el nombre del operador real, cumpliendo con el formato solicitado: `SIM [X] — [Operador]`.
- **Validación Activa**: Se añadió `isHandleValid` para verificar en tiempo real si una SIM configurada sigue habilitada en el sistema antes de intentar una llamada.

### Configuración y Persistencia
- **Modos de Selección**: Se introdujo el campo `simSelectionMode` en `DialerSettings` (`ASK_ALWAYS` o `FIXED`).
- **UI de Ajustes**: En `MainActivity.kt`, el botón de SIM ahora muestra el modo activo y el nombre descriptivo de la SIM seleccionada. El diálogo de configuración permite alternar entre "Preguntar siempre" y las SIMs disponibles.

### Flujo de Marcación
- **Automatización Resiliente**: Si se configura una SIM fija y esta desaparece (ej. se extrae la tarjeta), la app detecta el error, informa al usuario mediante un Toast, y vuelve automáticamente al modo "Preguntar siempre" para no bloquear la operación.
- **Servicio en Segundo Plano**: `DialerService` ahora prioriza la SIM de trabajo configurada para reintentos automáticos, asegurando que el CRM use siempre la línea correcta incluso sin intervención del usuario.

## Verificación exitosa
- El proyecto compila correctamente (`:app:assembleDebug`).
- Se confirmó que los labels de las SIMs se generan dinámicamente desde el sistema.
- Se implementó la lógica de fallback para SIMs no disponibles.

render_diffs(file:///C:/Users/SM/Downloads/AutoDialerCRM/app/src/main/java/com/tuempresa/autodialer/dialer/SimSelector.kt)
render_diffs(file:///C:/Users/SM/Downloads/AutoDialerCRM/app/src/main/java/com/tuempresa/autodialer/ui/MainActivity.kt)
