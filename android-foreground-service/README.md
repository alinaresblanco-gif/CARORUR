# CARORUR Android

Modulo Android nativo base para CARORUR con GPS en foreground y acceso a musica local del dispositivo.

## Que incluye

- Servicio foreground de ubicacion: LocationForegroundService
- Notificacion persistente con accion de parada
- Solicitud de permisos en runtime desde MainActivity
- Reintento por ciclo START_STICKY
- Lectura nativa de audio del dispositivo desde MediaStore
- Creacion de playlists nativas guardadas en SharedPreferences
- Reproductor de audio local dentro de la propia app

## Como probar

1. Abre Android Studio.
2. Open y selecciona esta carpeta: android-foreground-service.
3. Espera sincronizacion de Gradle.
4. Ejecuta en un telefono Android real.
5. Pulsa Iniciar GPS para el seguimiento en foreground.
6. Pulsa Cargar musica del dispositivo para conceder permiso de audio y listar canciones locales.
7. Marca canciones, guarda una playlist nativa y reproduce dentro de la app.

## Permisos requeridos

- ACCESS_FINE_LOCATION
- ACCESS_COARSE_LOCATION
- ACCESS_BACKGROUND_LOCATION
- FOREGROUND_SERVICE
- FOREGROUND_SERVICE_LOCATION
- POST_NOTIFICATIONS (Android 13+)
- READ_MEDIA_AUDIO (Android 13+)
- READ_EXTERNAL_STORAGE (Android 12 o inferior)

## Nota importante

En Android moderno, incluso con foreground service, algunos fabricantes aplican ahorro agresivo.
Para maxima continuidad:

- Excluir la app de optimizacion de bateria.
- Permitir actividad en segundo plano en ajustes del dispositivo.

## Limitaciones actuales

- Las playlists nativas de este modulo guardan referencias a audios locales del dispositivo actual, no sincronizan esos archivos con la parte web.
- En este entorno no se ha podido ejecutar una compilacion porque el repo no incluye gradlew.bat ni hay Gradle disponible en PATH.
