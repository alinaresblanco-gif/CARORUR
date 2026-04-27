# CARORUR Android

Contenedor Android nativo para ejecutar la web de CARORUR dentro de la app y exponer funciones nativas de audio local.

## Que incluye

- WebView nativo que carga la app web desde assets locales del APK
- Copia automatica de index/js/css/vistas/iconos/imagenes al paquete Android en build
- Puente JavaScript `CarorurNativeMusic` para:
	- Seleccionar audio del movil con selector nativo (URI persistente)
	- Reproducir audio local dentro de la app por URI
	- Detener reproduccion nativa

## Como probar

1. Abre Android Studio.
2. Open y selecciona esta carpeta: android-foreground-service.
3. Espera sincronizacion de Gradle.
4. Ejecuta en un telefono Android real.
5. Ejecuta la app Android y abre la vista de Playlist Colaborativa.
6. Pulsa seleccionar audio del movil (Android nativo).
7. Elige una cancion del telefono y guardala en la playlist.
8. La app guarda solo la referencia URI local, no el archivo en memoria web.

## Permisos requeridos

- READ_MEDIA_AUDIO (Android 13+)
- READ_EXTERNAL_STORAGE (Android 12 o inferior)
- INTERNET

## Nota importante

En Android moderno, incluso con foreground service, algunos fabricantes aplican ahorro agresivo.
Para maxima continuidad:

- Excluir la app de optimizacion de bateria.
- Permitir actividad en segundo plano en ajustes del dispositivo.

## Limitaciones actuales

- Las canciones locales se guardan por URI del dispositivo actual; ese archivo no se sincroniza a web/servidor.
- En este entorno no se ha podido ejecutar una compilacion porque el repo no incluye gradlew.bat ni hay Gradle disponible en PATH.
