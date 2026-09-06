# Estructura del Proyecto — Folder AI

Este archivo describe la organización de carpetas y módulos del proyecto, abarcando la capa Android en Kotlin, la capa nativa en C/C++ con Lua 5.4 original, y el motor en Rust.

---

```
.
├── .github/
│   └── workflows/
│       ├── build-debug-apk.yml          # GitHub Action para compilación limpia de APK Debug sin caché y firma en runner
│       └── override-commit.yml          # GitHub Action para sobreescritura de commits desde commit_message.txt
├── scripts/
│   └── ensure-debug-keystore.sh         # Script generador/verificador de debug.keystore desde cero para CI/CD sin bloqueos
├── app/
│   ├── build.gradle.kts                 # Configuración de Gradle, NDK 26.1, CMake, PDFBox y tareas Rust
│   ├── proguard-rules.pro               # Reglas de ofuscación y preservación JNI
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml      # Declaración de actividades, insets y permisos
│       │   ├── cpp/                     # Capa nativa C / C++ y Lua original
│       │   │   ├── CMakeLists.txt       # Script de compilación nativo CMake
│       │   │   ├── native-bridge.cpp    # Puente JNI C++17 hacia Lua (nativeExecuteLuaScript) y motor de edición (nativeEditFilePart)
│       │   │   └── lua/                 # Distribución ANSI C original de Lua 5.4
│       │   │       ├── lua.h            # Header oficial de Lua con definiciones de hooks e inspección
│       │   │       ├── lauxlib.h        # Biblioteca auxiliar de Lua
│       │   │       ├── lualib.h         # Bibliotecas estándar de Lua
│       │   │       └── lua_core.c       # Implementación ANSI C del runtime de Lua y hooks de instrucción
│       │   ├── rust/                    # Motor de análisis seguro en Rust
│       │   │   ├── Cargo.toml           # Manifiesto de dependencias (JNI, cdylib/staticlib)
│       │   │   └── src/
│       │   │       └── lib.rs           # Código del motor de seguridad y prevención de traversal
│       │   ├── java/com/example/        # Código fuente Kotlin (UI y Lógica)
│       │   │   ├── MainActivity.kt      # Actividad principal con Scaffold y manejo de insets
│       │   │   ├── ai/                  # Clientes de IA, Function Calling y streaming
│       │   │   │   ├── GeminiSandboxClient.kt     # Integración Gemini con Function Calling nativo y streaming SSE
│       │   │   │   └── OpenRouterSandboxClient.kt # Integración OpenRouter con modelos gratuitos (:free) y Function Calling OpenAI
│       │   │   ├── model/               # Modelos de datos
│       │   │   │   ├── SandboxModels.kt    # Workspace, ChatMessage, ToolExecution, SubagentInfo, ToolIconType y ToolStatus
│       │   │   │   └── NativeEngineBridge.kt # Interfaz JNI externa para C++, Lua (nativeExecuteLuaScript) y nativeEditFilePart
│       │   │   ├── sandbox/             # Administrador de sandbox y herramientas de archivos
│       │   │   │   └── SandboxManager.kt   # Clonador, lector multiformato (.txt, .md, .pdf, .lua), creador, editor, borrador y ejecutor Lua
│       │   │   ├── ui/
│       │   │   │   ├── components/      # Componentes modulares Jetpack Compose
│       │   │   │   │   ├── AiChatSection.kt       # Chat, chips rápidos (archivos, Lua, subagentes) y tarjetas ToolExecutionCard
│       │   │   │   │   ├── ApiKeyDialog.kt        # Diálogo multi-proveedor (OpenRouter / Gemini) con disclaimer y enlace a keys
│       │   │   │   │   ├── FileViewerDialog.kt    # Visor de código monoespaciado
│       │   │   │   │   ├── MarkdownText.kt        # Soporte para *cursiva* y **negrita**
│       │   │   │   │   ├── WorkspaceFolderCard.kt # Tarjeta de estado y conteo de archivos en el sandbox
│       │   │   │   │   └── WorkspaceHeader.kt     # Cabecera con estado de aislamiento
│       │   │   │   └── theme/           # Sistema de diseño Material 3
│       │   │   │       ├── Color.kt     # Paleta de ciberseguridad (cian, índigo, pizarra)
│       │   │   │       ├── Theme.kt     # Configuración de tema claro/oscuro
│       │   │   │       └── Type.kt      # Jerarquía tipográfica
│       │   │   └── viewmodel/           # Máquina de estados
│       │   │       └── SandboxViewModel.kt # ViewModel reactivo para clonación, chat, ejecución de herramientas y refresco del sandbox
│       │   └── res/                     # Recursos Android
│       │       ├── mipmap-*/            # Icono adaptativo personalizado
│       │       └── values/strings.xml   # Etiquetas de texto y app_name
│       └── test/                        # Pruebas unitarias y de arquitectura
│           └── java/com/example/
│               └── ExampleRobolectricTest.kt # Pruebas JVM locales con Robolectric
├── gradle/                              # Catálogo de versiones
│   └── libs.versions.toml               # Dependencias centralizadas (Compose, NDK, Room, PDFBox, LeakCanary)
├── .gitignore                           # Exclusiones de Git para C, C++, Rust, Lua y Android
├── commit_message.txt                   # Mensaje de commit controlado por el usuario (en español)
├── AI_CONTEXT.md                        # Contexto para modelos LLM y agentes de IA
├── AGENTS.md                            # Guía y flujo de trabajo para agentes
├── ROADMAP.md                           # Fases presentes y futuras del proyecto
└── README.md                            # Documentación general y guía de inicio rápido
```
