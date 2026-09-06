# Roadmap de Desarrollo — Folder AI

Este documento traza las etapas evolutivas de Folder AI, desde la implementación base del sandbox hasta el ecosistema de computación nativa multi-lenguaje.

---

## 📍 Fase 1: Sandbox y Asistente IA (Completada ✅)
- [x] Conexión con *Storage Access Framework* (SAF) para selección segura de carpetas.
- [x] Copia recursiva y clonación hacia almacenamiento privado de la aplicación.
- [x] Interfaz en Jetpack Compose con paleta de ciberseguridad (Material 3).
- [x] Integración con API de Gemini con **Streaming Real (SSE)** token a token.
- [x] Visor de código con tipografía monoespaciada para inspección rápida en el teléfono.
- [x] Soporte para formato de texto enriquecido en mensajes (`*cursiva*`, `**negrita**`, `` `código` ``).
- [x] Ajuste dinámico de teclado virtual para eliminar espacios en blanco innecesarios.
- [x] Automatización CI/CD con GitHub Action para gestión de mensajes de commit mediante `commit_message.txt`.

---

## 📍 Fase 2: Herramientas del Agente IA y Ecosistema Nativo (Completada ✅)
- [x] Integración de CMake 3.22 y Android NDK 26.1 en el sistema de compilación Gradle.
- [x] Inclusión del código fuente original de **Lua 5.4** en ANSI C puro (sin dependencias ni wrappers intermediarios).
- [x] Puente JNI en C++17 (`native-bridge.cpp`) enlazado a la biblioteca nativa compartida (`libfolderai_native.so`).
- [x] Estructura base del motor en **Rust** (`app/src/main/rust`) con crate `sandbox_engine` y verificación en ciclo de build.
- [x] Implementación de **Function Calling en tiempo real** con herramientas nativas de inspección y manipulación:
  - `list_workspace_files`: Listado recursivo de la estructura inicial de la carpeta, nombres y tamaños de archivos y subdirectorios.
  - `read_file`: Lectura con soporte multiformato para `.txt`, `.md`, `.pdf` (extracción nativa con PDFBox Android), `.lua` y código fuente.
  - `create_file`: Creación de archivos `.txt`, `.md` y `.lua` con generación automática de subdirectorios intermedios.
  - `edit_file_part`: Reemplazo de fragmentos específicos en `.txt`, `.md` y `.lua` con motor acelerado en C++17 (`nativeEditFilePart`).
  - `delete_file`: Eliminación de archivos en el sandbox aislado.
  - `execute_lua`: Motor de ejecución directa de scripts Lua 5.4 en la máquina virtual ANSI C con captura de stdout de `print(...)` y límite de instrucciones para protección anti-bucles infinitos.
  - `spawn_subagent`: Sistema de delegación multi-agente donde la IA genera subagentes especializados (Arquitecto, Constructor, Detective, Crítico, Optimizador, Escudo, Narrador) con objetivos, tareas y reportes técnicos dedicados.
- [x] Componente visual `ToolExecutionCard` con iconos de Material Symbols, estados en tiempo real (En ejecución, Listo, Fallo), badges de subagentes y resultados técnicos expandibles.
- [x] Sincronización reactiva del explorador de archivos del sandbox tras operaciones de creación, edición o borrado.
- [x] Configuración exhaustiva de `.gitignore` para omitir artefactos de C, C++, Rust y Lua.
- [x] Integración de herramienta de depuración en vivo **LeakCanary 2.14** (`debugImplementation`) para análisis de memoria e interfaz de leaks independiente en el teléfono.
- [x] Flujo de CI/CD automatizado en GitHub Actions (`build-debug-apk.yml`) para compilar APKs Debug descargando dependencias de C++, NDK, CMake, Rust y Lua, con compilación sin caché y generación dinámica de keystore (`scripts/ensure-debug-keystore.sh`).

---

## 📍 Fase 3: Operaciones de Kernel y Fast-I/O (Próxima)
- [ ] Implementación de clonación en C++ usando llamadas directas `copy_file_range` y `sendfile` de Linux.
- [ ] Indexación masiva de directorios con millones de archivos mediante `mmap` sin impacto en el Garbage Collector de Android.
- [ ] Generación de hashes criptográficos (SHA-256 / BLAKE3) desde Rust para detectar archivos modificados o corruptos.

---

## 📍 Fase 4: Entorno de Automatización Avanzado con Lua 5.4 y C/C++
- [x] Ejecución de scripts Lua generados por la IA en tiempo real (`execute_lua`).
- [x] Sandbox estricto en Lua: restricción de llamadas al sistema operativo (`os.execute`, `os.exit`, `os.remove`).
- [ ] Exposición de biblioteca JNI extendida en Lua para operaciones de transformación por lotes y formateo automático de código.
- [ ] Integración de perfiles de rendimiento y métricas de memoria de la máquina virtual Lua.

---

## 📍 Fase 5: Análisis Estático y Detección de Amenazas con Rust
- [ ] Análisis de seguridad y detección de credenciales expuestas (.env, claves privadas, tokens) en código importado.
- [ ] Motor de búsqueda ultrarrápido en memoria inspirado en *ripgrep*.
- [ ] Extracción de AST (Árboles Sintácticos) mediante Tree-sitter embebido en la capa nativa.
