# CARORUR Android

Contenedor Android nativo para ejecutar la web de CARORUR dentro de la app.

## Que incluye

- WebView nativo que carga la app web desde assets locales del APK
- Copia automatica de index/js/css/vistas/iconos/imagenes al paquete Android en build

## Como probar

1. Abre Android Studio.
2. Open y selecciona esta carpeta: android-foreground-service.
3. Espera sincronizacion de Gradle.
4. Ejecuta en un telefono Android real.
5. Ejecuta la app Android y verifica que carga la web local correctamente.
6. Si usas URL de pruebas, cambiala desde el boton de la esquina superior derecha.

## Permisos requeridos

- INTERNET

## Nota importante

En Android moderno, incluso con foreground service, algunos fabricantes aplican ahorro agresivo.
Para maxima continuidad:

- Excluir la app de optimizacion de bateria.
- Permitir actividad en segundo plano en ajustes del dispositivo.

## Limitaciones actuales

- En este entorno no se ha podido ejecutar una compilacion porque el repo no incluye gradlew.bat ni hay Gradle disponible en PATH.
