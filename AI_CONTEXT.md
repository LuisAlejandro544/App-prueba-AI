# AI Context — Folder AI

Este archivo proporciona contexto técnico para cualquier modelo de Inteligencia Artificial o asistente que opere sobre el repositorio de Folder AI.

---

## 🎯 Propósito del Proyecto
Folder AI es un sandbox de aislamiento para Android que permite al usuario seleccionar cualquier carpeta de su teléfono mediante SAF (*Storage Access Framework*). La aplicación replica íntegramente esa carpeta en su espacio privado interno (`context.filesDir/sandbox_workspace`), creando una barrera física donde la IA puede leer, inspeccionar, crear, editar y eliminar archivos de forma 100% segura sin riesgo de corromper ni borrar los archivos originales del usuario.

---

## 🛠️ Herramientas Nativas Disponibles para la IA
La IA dispone de herramientas registradas vía Function Calling en `GeminiSandboxClient.kt`, ejecutadas en `SandboxManager.kt` y renderizadas en tiempo real en `AiChatSection.kt`:

1. **`list_workspace_files(subpath: String?)`**:
   - Inspecciona la estructura del sandbox, listando archivos, subdirectorios y tamaños.
   - Icono visual: `AccountTree` (`ToolIconType.STRUCTURE`).
2. **`read_file(path: String)`**:
   - Lectura de archivos con soporte para `.txt`, `.md`, `.lua`, documentos `.pdf` (extracción de texto con PDFBox Android) y código fuente.
   - Icono visual: `Description` (`ToolIconType.READ`).
3. **`create_file(path: String, content: String)`**:
   - Creación de nuevos archivos `.txt`, `.md` y `.lua` con generación de directorios intermedios.
   - Icono visual: `NoteAdd` (`ToolIconType.CREATE`).
4. **`edit_file_part(path: String, targetText: String, replacementText: String)`**:
   - Reemplazo preciso de fragmentos en archivos `.txt`, `.md` y `.lua`.
   - Acelerado en C++17 mediante JNI (`nativeEditFilePart`) con fallback en Kotlin.
   - Icono visual: `AutoFixHigh` (`ToolIconType.EDIT`).
5. **`delete_file(path: String)`**:
   - Eliminación de archivos dentro del sandbox aislado.
   - Icono visual: `DeleteForever` (`ToolIconType.DELETE`).
6. **`execute_lua(script: String)`**:
   - Ejecuta scripts de Lua 5.4 nativos directamente en la máquina virtual ANSI C (`nativeExecuteLuaScript`).
   - Captura llamadas a `print(...)`, aísla el entorno de sistema operativo del host y previene cuelgues o bucles infinitos con un limitador estricto de instrucciones (hook de conteo).
   - Icono visual: `Code` (`ToolIconType.LUA`).
7. **`spawn_subagent(role: String, goal: String, task: String, relevantFiles: String?)`**:
   - Despliega un subagente autónomo con perfil técnico especializado (Arquitecto, Constructor, Detective, Crítico, Optimizador, Escudo, Narrador).
   - El subagente ejecuta su tarea en un contexto focalizado y retorna un informe técnico estructurado al agente orquestador principal.
   - Icono visual: `SmartToy` (`ToolIconType.SUBAGENT`).

---

## 🔒 Reglas Críticas de Seguridad
1. **Intactitud del Sistema de Archivos Original**: La app nunca debe intentar escribir en la URI original del usuario. Toda operación de lectura, edición, creación o eliminación ocurre exclusivamente sobre los archivos clonados en el sandbox interno.
2. **Sin comandos de sistema peligrosos**: No utilizar propiedades `persist.sys.*` ni requerir acceso root. La aplicación opera bajo el modelo de permisos estándar de Android.
3. **Manejo de Secretos**: La clave de Gemini no debe hardcodearse en el código fuente. Se recupera dinámicamente de `BuildConfig.GEMINI_API_KEY` o de la preferencia local configurada por el usuario en tiempo de ejecución.
4. **Validación de Rutas en la Capa Nativa**: Cualquier acceso a archivos a través de C++, Lua o Rust debe validar que la ruta no intente escapar del sandbox mediante ataques de *Directory Traversal* (`../`).

---

## 🏗️ Convenciones de Arquitectura
- **UI en Jetpack Compose**: Componentes desacoplados y modulares en `ui/components/`. Renderizado de `ToolExecutionCard` con estados en tiempo real (En ejecución, Listo, Fallo) y panel expandible.
- **Inyección de Dependencias**: Inyección por constructor limpia y ViewModel estándar de AndroidX.
- **Flujos Reactivos y Streaming Real**: Uso de `StateFlow` y `collectAsStateWithLifecycle`. Respuestas emitidas como `Flow<String>` mediante SSE y eventos de herramientas en tiempo real hacia el ViewModel.
- **Sincronización Automática**: Cualquier mutación del sandbox (`create_file`, `edit_file_part`, `delete_file`) desencadena la actualización del estado de archivos en pantalla.
- **Capa Nativa Real**: El código nativo en `app/src/main/cpp` y `app/src/main/rust` está integrado en el ciclo de compilación de Gradle mediante CMake y NDK 26.1.
- **Lua 5.4 ANSI C**: El código de Lua es el estándar original en C, sin intermediarios ni wrappers empaquetados.
- **Gestión de Commits**: La información del archivo `commit_message.txt` siempre debe redactarse en español y solo se modifica cuando el usuario lo solicita explícitamente. Es consumido por el flujo de GitHub Actions para mantener el historial sincronizado.
