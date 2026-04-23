# CARORUR Android Foreground Service

Modulo Android nativo con servicio foreground para ubicacion continua.

## Que incluye

- Servicio foreground de ubicacion: LocationForegroundService
- Notificacion persistente con accion de parada
- Solicitud de permisos en runtime desde MainActivity
- Reintento por ciclo START_STICKY

## Como probar

1. Abre Android Studio.
2. Open y selecciona esta carpeta: android-foreground-service.
3. Espera sincronizacion de Gradle.
4. Ejecuta en un telefono Android real.
5. Pulsa Iniciar servicio GPS.

## Permisos requeridos

- ACCESS_FINE_LOCATION
- ACCESS_COARSE_LOCATION
- ACCESS_BACKGROUND_LOCATION
- FOREGROUND_SERVICE
- FOREGROUND_SERVICE_LOCATION
- POST_NOTIFICATIONS (Android 13+)

## Nota importante

En Android moderno, incluso con foreground service, algunos fabricantes aplican ahorro agresivo.
Para maxima continuidad:

- Excluir la app de optimizacion de bateria.
- Permitir actividad en segundo plano en ajustes del dispositivo.
