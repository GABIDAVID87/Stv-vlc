# Integración VLC + Cebolla TV

Base: el proyecto VLC recibido por el usuario.

- El núcleo VLC no se sustituye.
- LibVLC, libvlcjni, MediaLibrary y VideoPlayerActivity permanecen en sus módulos originales.
- Cebolla TV se incorpora como módulo `application:cebolla-tv`.
- `com.segovia.tv.MainActivity` es el launcher de la aplicación final.
- `ExternalPlayerLauncher` dirige películas y series a `org.videolan.vlc.gui.video.VideoPlayerActivity` dentro del mismo APK.
- Las versiones de compilación de Cebolla se toman de la raíz VLC: AGP 9.1.1, Kotlin 2.2.10, KSP 2.3.2, compile/target SDK 36, Build Tools 36.0.0, Java/JVM target 1.8 y JDK 17 para el entorno de compilación.
- Los archivos originales de configuración de Cebolla se conservan como archivos normales en `application/cebolla-tv/CebollaTV_Original_Project/`; no se guarda un ZIP de Cebolla dentro del proyecto integrado.
