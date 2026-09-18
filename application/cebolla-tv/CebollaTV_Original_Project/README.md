# Segovia TV - estructura modular

Esta versión reorganiza la aplicación sin cambiar la lógica visual de las grillas actuales.

## Conservado
- Inicio y su grilla horizontal.
- Grilla de Películas de 8 columnas.
- Grilla de Kids mezclando contenido de Kids tal como llega de Drive.
- Menú e iconos Segovia TV.
- Navegación por DPAD/control remoto.
- Caché local de películas, Kids y series.
- Escaneo de Google Drive y URLs del proxy existentes.

## Nuevo
- Series separada en su propia pantalla de detalle.
- Detalle de Series con banner grande, información lateral, temporadas y capítulos sin fotos de capítulo.
- Foco de temporadas/capítulos en gris oscuro y amarillo al recibir foco.
- Reproducción exclusivamente mediante reproductor externo Android.
- ExoPlayer/Media3 eliminado del proyecto.

## Credenciales de Google Drive
Por seguridad, la clave privada que estaba escrita dentro del MainActivity original NO se copia al ZIP.
Crear `secrets.properties` a partir de `secrets.properties.example` y completar:

DRIVE_SERVICE_EMAIL=...
DRIVE_PRIVATE_KEY=-----BEGIN PRIVATE KEY-----\n...\n-----END PRIVATE KEY-----

Si no se configura, la app puede arrancar con la caché local existente, pero no podrá renovar el acceso ni escanear Drive.

## Reproductor externo
Al pulsar REPRODUCIR se envía la URL como `video/*` mediante ACTION_VIEW. Android usará el reproductor externo compatible instalado; si hay varios y ninguno fue elegido, mostrará el selector del sistema.

## Nota
No se incluyen las fotos tomadas al televisor como recursos de la app: fueron usadas como referencia visual y los posters/banners continúan viniendo de las URLs configuradas en Drive.
