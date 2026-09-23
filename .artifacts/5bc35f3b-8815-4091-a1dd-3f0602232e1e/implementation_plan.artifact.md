# Fase 6: Automatización (Decision Engine)

Implementación del motor de decisiones y automatización basado en una máquina de estados reactiva, respetando todos los parámetros de configuración y el Modo Vacaciones.

## User Review Required

> [!IMPORTANT]
> El "Modo Vacaciones" y la "Cuota Diaria" ahora detendrán el servicio de marcado de forma inmediata mediante la cancelación del Job de corrutina, asegurando que no se inicie ninguna llamada nueva si el estado cambia mientras la app está en background.

## Proposed Changes

### [Core Logic]

#### [NEW] [DecisionEngine.kt](file:///C:/Users/SM/Downloads/AutoDialerCRM/app/src/main/java/com/tuempresa/autodialer/domain/DecisionEngine.kt)
- Crear una clase dedicada para encapsular la lógica de "Qué hacer a continuación".
- Métodos para calcular retardos aleatorios basados en Ajustes.
- Lógica para determinar si un contacto debe reintentarse hoy, programarse para mañana o descartarse.

#### [MODIFY] [DialerService.kt](file:///C:/Users/SM/Downloads/AutoDialerCRM/app/src/main/java/com/tuempresa/autodialer/dialer/DialerService.kt)
- Refactorizar el `loopJob` para que sea conducido por un flujo de estados.
- Añadir observadores para `settings.isVacationModeActive` y la cuota diaria que disparen la detención inmediata.
- Integrar `DecisionEngine` para manejar las pausas entre llamadas y reintentos.

#### [MODIFY] [AutoDialerInCallService.kt](file:///C:/Users/SM/Downloads/AutoDialerCRM/app/src/main/java/com/tuempresa/autodialer/dialer/AutoDialerInCallService.kt)
- Asegurar que `autoHangupSeconds` se lee de los ajustes en tiempo real al iniciar el temporizador.

---

### [Data]

#### [MODIFY] [ContactDao.kt](file:///C:/Users/SM/Downloads/AutoDialerCRM/app/src/main/java/com/tuempresa/autodialer/data/ContactDao.kt)
- Asegurar que las consultas de "siguiente en cola" respetan estrictamente los timestamps y estados.

## Verification Plan

### Automated Tests
- Ejecutar `gradle_build(":app:assembleDebug")` para asegurar integridad.

### Manual Verification
- **Escenario 1 (Tiempos de espera)**: Configurar espera de 20s -> Iniciar llamada -> Confirmar que la app cuelga automáticamente a los 20s si no hay respuesta.
- **Escenario 2 (Rangos de Delay)**: Configurar transición entre 5s y 10s -> Realizar 3 llamadas -> Confirmar con cronómetro que los tiempos son variables dentro del rango.
- **Escenario 3 (Modo Vacaciones Inmediato)**: Activar marcado automático -> Durante la espera para la siguiente llamada, activar Modo Vacaciones -> Confirmar que el servicio se detiene al instante.
- **Escenario 4 (Cuota Diaria)**: Configurar cuota de 1 -> Marcar un contacto como "Interesado" -> Confirmar que la automatización se detiene y muestra el aviso de cuota.
- **Escenario 5 (Máximo de Intentos)**: Configurar 2 intentos por día -> Confirmar que tras fallar el segundo intento, el contacto se programa para el día siguiente (si quedan días de reintento) o se descarta.
