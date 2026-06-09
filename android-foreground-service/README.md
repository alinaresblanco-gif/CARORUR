# CARORUR Android

Contenedor Android nativo para ejecutar la web de CARORUR dentro de la app.

## Que incluye

- WebView nativo que carga la app web desde assets locales del APK
- Copia automatica de index/js/css/vistas/iconos/imagenes al paquete Android en build
- Push notifications gratuitas con Firebase Cloud Messaging (FCM)

## Como probar

1. Abre Android Studio.
2. Open y selecciona esta carpeta: android-foreground-service.
3. Espera sincronizacion de Gradle.
4. Ejecuta en un telefono Android real.
5. Ejecuta la app Android y verifica que carga la web local correctamente.
6. Si usas URL de pruebas, cambiala desde el boton de la esquina superior derecha.

## Permisos requeridos

- INTERNET
- POST_NOTIFICATIONS (Android 13+)

## Configuracion gratuita de push (obligatoria)

1. Crea un proyecto en Firebase (plan gratuito Spark).
2. Registra la app Android con package: `com.carorur.tracker`.
3. Descarga `google-services.json` y guardalo en `android-foreground-service/app/google-services.json`.
4. En Firebase Console, genera la clave Server key para FCM legacy API.
5. En Google Apps Script, abre `Project Settings > Script properties` y crea:
	- `FCM_SERVER_KEY` = tu Server key de Firebase.
6. Publica una nueva version del Web App de Apps Script para aplicar los cambios.

Sin estos pasos, la app compila pero no enviara push.

## Nota importante

En Android moderno, incluso con foreground service, algunos fabricantes aplican ahorro agresivo.
Para maxima continuidad:

- Excluir la app de optimizacion de bateria.
- Permitir actividad en segundo plano en ajustes del dispositivo.

## Limitaciones actuales

- En este entorno no se ejecuto compilacion final del APK/AAB por limitaciones de toolchain local.
