# Compilar VLC Segovia TV con GitHub Actions

Este proyecto está preparado para compilarse desde GitHub sin Code On The Go.

## Desde el celular

1. Crea un repositorio nuevo en GitHub.
2. Sube **todo el contenido de esta carpeta** al repositorio.
3. Abre la pestaña **Actions**.
4. Selecciona **Build VLC Segovia TV**.
5. Pulsa **Run workflow**.
6. Espera a que termine el trabajo.
7. En la ejecución terminada, entra en **Artifacts** y descarga `VLC-Segovia-TV-debug`.

El workflow instala Java 17, Android SDK 36 y NDK 21.4.7075529, inicializa las fuentes auxiliares de VLC y usa el Gradle 9.3.1 que requiere este árbol de VLC.

El APK se compila como `Debug`; no requiere una clave de firma propia.
