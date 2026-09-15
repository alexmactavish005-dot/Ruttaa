# RUTTA — Android MVP

Proyecto Android nativo pensado para delivery particular.

## Prioridades de esta versión
- Interfaz limpia y separada, sin 4 botones amontonados en la parte inferior.
- Cámara real con CameraX.
- OCR local con Google ML Kit Text Recognition (sin API de pago).
- Campos editables y confirmación antes de guardar.
- Normalización inicial de abreviaciones: AV/AVENIDA, PSJ/PJE/PASAJE, CALLE.
- Pedidos, ganancias y gastos.
- Ruta del día con lista de destinos y acceso a Google Maps.

## Nota importante sobre OCR manuscrito
ML Kit funciona muy bien con texto impreso, pero la escritura manuscrita puede variar mucho. Por eso RUTTA muestra el resultado antes de guardar y permite corregirlo. La siguiente mejora debería ser un parser más especializado para comandas chilenas y validación de direcciones/teléfonos.

## Compilar
Abrir la carpeta en Android Studio y sincronizar Gradle. No se incluye un APK porque este entorno no tiene Android SDK/Gradle cacheado para garantizar una compilación local aquí.
