# Entre páginas - Desafío práctico 3

Aplicación Android de seguimiento de lectura para DSM441, Universidad Don Bosco. Kotlin, vistas nativas, Firebase Authentication y SQLite. Parte del proyecto inicial proporcionado.

## Abrir y ejecutar

1. Extraer este proyecto y abrir la carpeta `DesafioBiblioteca` en Android Studio.
2. Usar JDK 21 (incluido en Android Studio), Android SDK 36.1 y aceptar la sincronización de Gradle.
3. Se conserva el `app/google-services.json` del proyecto original. En la consola del proyecto Firebase, verificar **Authentication > Sign-in method > Email/Password**. Debe estar habilitado. Si cambias de proyecto Firebase, reemplaza ese JSON con el de una app Android cuyo package sea `com.example.desafiobiblioteca`.
4. Ejecutar en un emulador o teléfono Android 7.0 / API 24 o superior. El registro y el inicio de sesión necesitan conexión a internet. La biblioteca funciona localmente con una sesión ya guardada.
5. Crear una cuenta con correo, contraseña de al menos seis caracteres y confirmación; después definir la meta y agregar libros.

El APK incluido es una compilación de depuración para instalación y evaluación, no una publicación en Play Store.

## Portadas opcionales (versión 1.1)

En Agregar libro o Editar libro, usa **Elegir o cambiar imagen**. Se abre el selector de documentos de Android, limitado a imágenes. Puedes guardar sin elegir ninguna; en ese caso se muestra un ícono de libro. **Quitar imagen** permite volver a guardar el libro sin portada.

La imagen se copia, ajusta de tamaño y orientación y se guarda como JPEG dentro del almacenamiento privado de su usuario; no depende de conservar la foto en la galería. No se piden permisos generales de fotos o almacenamiento. La portada se muestra en inicio, biblioteca y mejor lectura. Los campos y la portada elegida se conservan al recrear el formulario. Al cancelar el selector se conserva la selección anterior; al cancelar la edición se conserva la portada guardada.

SQLite se actualiza de versión 1 a 2 mediante ALTER TABLE, conservando libros, metas y progreso. No hace falta desinstalar ni borrar datos. La actualización mantiene la configuración Firebase y el identificador de la aplicación. Se retiró la marca B / del diseño.

## Funciones

- Registro, ingreso, conservación de sesión y cierre con Firebase Authentication.
- Inicio con saludo, meta del año actual, libros terminados, faltantes y lecturas en curso.
- Alta y edición de libros: título, autor, género, páginas totales, páginas leídas, estado y estrellas.
- Cuatro géneros: Novela, Ciencia ficción, Historia y Desarrollo personal.
- Validaciones por campo antes de guardar. Un libro terminado requiere 1-5 estrellas y todas las páginas leídas. Por leer establece cero páginas. Las estrellas se desactivan y se limpian en los otros estados.
- Fecha de finalización automática. Editar título o estrellas conserva la fecha; volver a Leyendo la elimina y terminar otra vez asigna una fecha nueva.
- Biblioteca desplazable con mensajes vacíos y acceso a edición.
- Estadísticas anuales: conteos de los cuatro géneros, género más leído (muestra todos si hay empate), páginas registradas y mejor libro. En empate de estrellas se elige el terminado primero; si también coincide la fecha se usa el ID.
- Menú lateral inspirado en la referencia y navegación inferior. Paleta principal #C7A1AD / #F7EEF1, secundaria #C89A66 / #F3E2C6 y textos #2B2F35 / #565C63.

## Persistencia y privacidad

`LibraryDb` crea `biblioteca.db` dentro del almacenamiento privado de Android:

- `books`: UID propietario, título, autor, género, páginas, estado, estrellas, fecha y ruta de portada opcional.
- `goals`: clave compuesta UID/año y objetivo positivo.
- `reading`: clave UID/libro/año y páginas registradas.

Todas las consultas y actualizaciones de usuario se filtran por el UID que proporciona FirebaseAuth. Cambiar de cuenta no mezcla bibliotecas ni metas. La información persiste al cerrar o reiniciar. SQLite es local: los datos no se sincronizan entre teléfonos y se eliminan si se desinstala la aplicación o se borran sus datos. No se guardan contraseñas en SQLite. Las copias de seguridad de la aplicación están desactivadas.

Las páginas del año son la suma de incrementos guardados en ese año, incluso en libros que todavía se leen. Un libro nuevo con 100 páginas leídas registra esas 100 en el año del alta. Si corriges el avance hacia abajo, se descuentan primero los avances más recientes. Guardar dos veces el mismo avance no duplica páginas. Los libros terminados y el mejor libro se filtran por la fecha de finalización del año actual del dispositivo.

## Estructura del código

- `MainActivity.kt`: acceso, inicio, formulario, biblioteca, estadísticas y menú.
- `LibraryDb.kt`: SQLite, transacciones, separación por UID y progreso anual.
- `Book.kt`: modelo, validaciones y cálculos reutilizables.
- `CoverStore.kt`: importación, reducción, orientación y almacenamiento privado de imágenes.
- `CoverStoreTest.kt`: archivos inválidos, persistencia de portadas, separación por usuario y migración desde SQLite v1.
- `BookRulesTest.kt`: límites de campos, estados, estrellas, porcentajes y desempate.
- `LibraryDbTest.kt`: persistencia, aislamiento, páginas por año, correcciones y fechas.
- `ReadingFlowTest.kt`: flujo de integración en emulador y capturas reales; crea una cuenta temporal en el Firebase configurado y la elimina al finalizar.

## Compilar y probar

En PowerShell dentro de la carpeta del proyecto:

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
# Con un emulador o dispositivo conectado:
.\gradlew.bat :app:connectedDebugAndroidTest
```

Para ejecutar solo la prueba local de SQLite sin crear una cuenta de Firebase:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.example.desafiobiblioteca.LibraryDbTest"
```

Los datos adicionales de las capturas son ejemplos de prueba, no una biblioteca precargada en la aplicación. Las pruebas retiran esos datos y la cuenta temporal al finalizar.

## Entrega académica

Se incluye un documento PDF con capturas del emulador y explicación de las funciones. La publicación del repositorio queda a cargo de la estudiante. Antes de entregar, agregar en el documento el nombre, carné y enlace real al repositorio. No se inventaron datos personales ni una URL de GitHub.

## Referencias técnicas

- Firebase Authentication con correo y contraseña: https://firebase.google.com/docs/auth/android/password-auth
- Persistencia SQLite en Android: https://developer.android.com/training/data-storage/sqlite

Las dependencias de autenticación Google y Realtime Database del proyecto inicial se retiraron porque no se usan. Firebase Auth 24.2.0 evita la incompatibilidad de metadatos Kotlin 2.4 de la versión 25.0.0 con el compilador integrado en AGP 9.1.1.
