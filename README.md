# Folder AI — Sandbox Aislado y Asistente de Código

Folder AI es una aplicación móvil Android diseñada para aislar carpetas de proyectos y permitir que la inteligencia artificial analice, inspeccione y consulte archivos de forma 100% segura, sin riesgo de corrupción o alteración accidental en los archivos originales del teléfono.

---

## 🚀 Características Principales

- **Aislamiento en Sandbox (SAF)**: Clona recursivamente carpetas mediante el *Storage Access Framework* de Android hacia un almacenamiento privado protegido (`files/sandbox_workspace`).
- **Asistente IA Contextual con Streaming Real**: Integración con Gemini API mediante *Server-Sent Events* (SSE) que transmite respuestas token a token en tiempo real, sin demoras ni buffers estáticos.
- **Herramientas de IA con Ejecución en Tiempo Real (Function Calling)**:
  - 📂 **Estructura del workspace (`list_workspace_files`)**: Inspecciona la jerarquía inicial de la carpeta, nombres de archivos, subdirectorios y tamaños.
  - 📄 **Lectura de archivos (`read_file`)**: Soporte para lectura de archivos `.txt`, `.md`, documentos `.pdf` (mediante extracción de texto nativa con PDFBox Android) y código fuente.
  - 📝 **Creación de archivos (`create_file`)**: Creación controlada de nuevos archivos `.txt` y `.md` dentro del sandbox, generando subdirectorios intermedios de forma automática.
  - ✏️ **Modificación de fragmentos (`edit_file_part`)**: Edición quirúrgica de partes de archivos `.txt`, `.md` y `.lua` mediante reemplazo de fragmentos exactos, acelerada nativamente en C++17 vía JNI (`nativeEditFilePart`).
  - 🗑️ **Eliminación de archivos (`delete_file`)**: Borrado seguro de archivos dentro del sandbox sin afectar el almacenamiento original.
  - 🌙 **Ejecución de Scripts en Lua 5.4 (`execute_lua`)**: La IA puede escribir y ejecutar sus propios scripts de Lua en la máquina virtual nativa en C con captura de consola `print(...)`, límites de instrucciones de seguridad y sandbox de sistema operativo.
  - 🤖 **Generación de Subagentes Especializados (`spawn_subagent`)**: Capacidad del agente orquestador para generar y delegar tareas complejas a subagentes con roles delimitados (Arquitecto, Constructor, Detective, Crítico, Optimizador, Escudo, Narrador), objetivos concretos y recepción de reportes técnicos.
- **Tarjetas Interactivas de Herramientas (`ToolExecutionCard`)**: Cada invocación de herramienta se visualiza en tiempo real en la interfaz de chat con su icono temático correspondiente, badge de estado animado (*En ejecución*, *Listo*, *Fallo*), detalles de rol y meta de subagentes, y panel expandible de resultados técnicos.
- **Actualización Reactiva**: El listado y el conteo del sandbox se actualizan automáticamente en pantalla cada vez que la IA crea, modifica o borra un archivo.
- **Soporte de Texto Enriquecido**: Formato nativo en mensajes para cursiva (`*texto*`), negrita (`**texto**`), cursiva/negrita (`***texto***`) y bloques de código monoespaciado (`` `código` ``).
- **Cimientos Nativos de Alto Rendimiento**:
  - **C++17**: Puente JNI y operaciones de archivos de baja latencia (`nativeEditFilePart`).
  - **Lua 5.4 Original**: Motor embebido en ANSI C puro (sin wrappers externos) para futuras automatizaciones y scripts de análisis.
  - **Rust**: Crate para validación criptográfica de rutas, prevención de *directory traversal* y sandboxing seguro en memoria.
- **Automatización CI/CD con GitHub Actions**: Workflow para sobrescribir y estandarizar mensajes de commit desde `commit_message.txt`.

---

## 🛠️ Stack Tecnológico

| Capa | Tecnología | Propósito |
| :--- | :--- | :--- |
| **Frontend / UI** | Kotlin + Jetpack Compose (Material 3) | Interfaz moderna, adaptativa y fluida con tarjetas de herramientas en tiempo real |
| **Arquitectura** | MVVM + StateFlow + Coroutines | Gestión de estado reactivo y separación de responsabilidades |
| **Persistencia** | Android Room Database | Historial de auditoría y métricas de clonación |
| **IA / LLM** | Gemini API (Function Calling + SSE) | Agente con ejecución de herramientas y streaming continuo |
| **Procesamiento PDF** | PDFBox Android (`com.tom-roush:pdfbox-android`) | Extracción de texto y soporte para documentos PDF en el sandbox |
| **Capa Nativa** | C++17 + Lua 5.4 ANSI C | Puente de ejecución, sustitución atómica de texto y runtime Lua |
| **Seguridad** | Rust Crate (`sandbox_engine`) | Validación estricta de rutas y sanitización de sandbox |

---

## 📋 Requisitos Previos

- **Android SDK**: API 36 (Mínimo Android 7.0 / API 24).
- **Android NDK**: Versión `26.1.10909125`.
- **CMake**: Versión `3.22.1`.
- **Gradle**: 9.x con Kotlin 2.2.x.

---

## 🔧 Compilación y Ejecución

Compilar el APK en modo depuración:
```bash
gradle :app:assembleDebug
```

Ejecutar las pruebas unitarias y de arquitectura:
```bash
gradle :app:testDebugUnitTest
```

---

## 🔑 Variables de Entorno y Configuración

La API Key de Gemini puede configurarse:
1. Desde la propia aplicación tocando el icono de **Llave** en la cabecera superior.
2. Mediante el panel de secretos en `.env`:
```env
GEMINI_API_KEY=tu_clave_de_gemini_aqui
```

---

## 🛡️ Política de Seguridad

La IA únicamente tiene acceso a los archivos clonados en el directorio privado y aislado de la aplicación (`files/sandbox_workspace`). En ningún caso puede escribir, alterar o eliminar los archivos originales seleccionados por el usuario en su teléfono.
