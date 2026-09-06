# Guía y Reglas para Agentes de Desarrollo — Folder AI

Este documento rige la conducta, metodología y reglas de trabajo de los agentes de IA que colaboren en el desarrollo de Folder AI.

---

## 🗺️ Flujo de Trabajo en 7 Fases

Cada tarea técnica debe encajar rigurosamente en una de las fases del ciclo de construcción de software:

1. **01. El Arquitecto (Planificación y Diseño)**: Antes de escribir código, definir estructura técnica, dependencias, modelo de datos y justificar cada decisión.
2. **02. El Constructor (Generación de Código)**: Código limpio, modular, tipado y listo para producción con validación y manejo exhaustivo de errores.
3. **03. El Detective (Debugging)**: Diagnóstico metódico de fallos usando razonamiento paso a paso (*Chain of Thought*): hipótesis, causa raíz demostrada y prevención.
4. **04. El Crítico (Code Review)**: Revisión de seguridad, rendimiento, patrones limpios y edge cases.
5. **05. El Optimizador (Refactoring)**: Mejorar rendimiento y legibilidad sin alterar el comportamiento observable.
6. **06. El Escudo (Testing)**: Cobertura de camino feliz (*happy path*), valores límite, errores controlados y mocks.
7. **07. El Narrador (Documentación)**: Documentación directa, concisa y técnica (README, API docs, inline comments).

---

## 📱 Contexto del Usuario y Plataforma
- **Dispositivo**: El usuario opera la aplicación desde un teléfono móvil. Las interfaces deben estar optimizadas ergonómicamente para interacción táctil con una sola mano, respetando insets del sistema y evitando que el teclado virtual tape campos de entrada o deje espacios vacíos.
- **Distribución**: La aplicación se distribuye a través de tiendas alternativas y APKs independientes (Uptodown / instalación directa), no a través de Google Play Store.
- **Peso del APK y Dependencias**: El peso final del APK no es una restricción limitante. Se prioriza que las dependencias sean 100% funcionales, robustas y de producción. Evitar soluciones frágiles "sin dependencias" si existe una librería estándar que resuelva el problema.
- **Inclusión de Tecnologías Nativas**: Si se usan lenguajes como C, C++, Rust o Lua en el proyecto, **deben estar integrados en la compilación de Gradle** (`build.gradle.kts`, CMake, NDK). No se deben crear funciones fallback en Kotlin si se solicitó o preparó un framework o módulo específico.
- **Marcas Protegidas**: Evitar incluir nombres o marcas registradas en identificadores de paquetes, rutas o recursos que puedan comprometer la distribución del usuario.
- **Commits**: Si existe un archivo `commit_message.txt`, la información debe redactarse siempre en español y no modificarse a menos que el usuario lo solicite explícitamente. Este archivo es leído por el flujo `.github/workflows/override-commit.yml` para reescribir de forma automatizada los mensajes de los commits en el repositorio remoto.
- **Restricción de Propiedades del Sistema**: Nunca utilizar propiedades `persist.sys.*`.
