package com.example.ai

import com.example.BuildConfig
import com.example.model.ClonedFile
import com.example.model.SubagentInfo
import com.example.model.ToolExecution
import com.example.model.ToolIconType
import com.example.model.ToolStatus
import com.example.sandbox.SandboxManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

class GeminiSandboxClient(
    private val sandboxManager: SandboxManager
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private fun resolveApiKey(customKey: String?): String {
        return when {
            !customKey.isNullOrBlank() -> customKey.trim()
            try {
                BuildConfig.GEMINI_API_KEY.isNotBlank() && BuildConfig.GEMINI_API_KEY != "MY_GEMINI_API_KEY"
            } catch (_: Throwable) { false } -> BuildConfig.GEMINI_API_KEY
            else -> ""
        }
    }

    /**
     * Declaración formal de las 5 herramientas autorizadas en el Sandbox Aislado.
     */
    private fun buildToolsDeclaration(): JSONArray {
        val declarations = JSONArray()

        // 1. list_workspace_files
        val listTool = JSONObject().apply {
            put("name", "list_workspace_files")
            put("description", "Obtiene la estructura inicial y completa de la carpeta, nombres de archivos y qué hay dentro de subcarpetas en el sandbox.")
            val params = JSONObject().apply {
                put("type", "OBJECT")
                val props = JSONObject().apply {
                    put("subpath", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Subcarpeta específica a inspeccionar (opcional, deja vacío para ver la raíz completa).")
                    })
                }
                put("properties", props)
            }
            put("parameters", params)
        }
        declarations.put(listTool)

        // 2. read_file
        val readTool = JSONObject().apply {
            put("name", "read_file")
            put("description", "Lee el contenido de un archivo del sandbox. Soporta archivos .txt, .pdf, .md, código fuente y documentos de texto.")
            val params = JSONObject().apply {
                put("type", "OBJECT")
                val props = JSONObject().apply {
                    put("path", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Ruta relativa del archivo a leer (ej: 'notas.txt', 'documentos/guia.pdf', 'README.md').")
                    })
                }
                put("properties", props)
                put("required", JSONArray().put("path"))
            }
            put("parameters", params)
        }
        declarations.put(readTool)

        // 3. create_file
        val createTool = JSONObject().apply {
            put("name", "create_file")
            put("description", "Crea un nuevo archivo (.txt, .md o .lua) dentro del sandbox aislado. Crea subcarpetas intermedias automáticamente.")
            val params = JSONObject().apply {
                put("type", "OBJECT")
                val props = JSONObject().apply {
                    put("path", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Ruta relativa del archivo a crear. Debe terminar en .txt, .md o .lua (ej: 'resumen.md', 'scripts/analisis.lua', 'notas.txt').")
                    })
                    put("content", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Contenido de texto o código que se escribirá en el nuevo archivo.")
                    })
                }
                put("properties", props)
                put("required", JSONArray().put("path").put("content"))
            }
            put("parameters", params)
        }
        declarations.put(createTool)

        // 4. edit_file_part
        val editTool = JSONObject().apply {
            put("name", "edit_file_part")
            put("description", "Modifica una parte específica de un archivo .txt, .md o .lua existente, reemplazando un texto exacto buscado por el nuevo texto.")
            val params = JSONObject().apply {
                put("type", "OBJECT")
                val props = JSONObject().apply {
                    put("path", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Ruta relativa del archivo a modificar (.txt, .md o .lua).")
                    })
                    put("targetText", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Texto exacto dentro del archivo que se desea buscar y reemplazar.")
                    })
                    put("replacementText", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Nuevo texto que sustituirá a targetText.")
                    })
                }
                put("properties", props)
                put("required", JSONArray().put("path").put("targetText").put("replacementText"))
            }
            put("parameters", params)
        }
        declarations.put(editTool)

        // 5. delete_file
        val deleteTool = JSONObject().apply {
            put("name", "delete_file")
            put("description", "Elimina cualquier archivo existente que esté en la carpeta del sandbox aislado.")
            val params = JSONObject().apply {
                put("type", "OBJECT")
                val props = JSONObject().apply {
                    put("path", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Ruta relativa del archivo a eliminar.")
                    })
                }
                put("properties", props)
                put("required", JSONArray().put("path"))
            }
            put("parameters", params)
        }
        declarations.put(deleteTool)

        // 6. execute_lua
        val luaTool = JSONObject().apply {
            put("name", "execute_lua")
            put("description", "Ejecuta un script o fragmento de código Lua 5.4 directamente en la máquina virtual nativa aislada en C. Útil para cálculos matemáticos, algoritmos, transformaciones de texto, estadísticas y scripts personalizados.")
            val params = JSONObject().apply {
                put("type", "OBJECT")
                val props = JSONObject().apply {
                    put("code", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Código fuente Lua 5.4 completo a ejecutar. Puede usar print(...) para emitir texto o 'return valor'.")
                    })
                }
                put("properties", props)
                put("required", JSONArray().put("code"))
            }
            put("parameters", params)
        }
        declarations.put(luaTool)

        // 7. spawn_subagent
        val subagentTool = JSONObject().apply {
            put("name", "spawn_subagent")
            put("description", "Genera un subagente especializado delegándole una tarea, objetivo y rol específicos (ej: Auditor de Seguridad, Refactorizador, Documentador). El subagente analiza el contexto y devuelve un reporte estructurado.")
            val params = JSONObject().apply {
                put("type", "OBJECT")
                val props = JSONObject().apply {
                    put("role", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Rol o especialidad del subagente (ej: 'El Detective - Debugger', 'El Constructor - Generador de Código', 'Auditor de Seguridad', 'Optimizador').")
                    })
                    put("goal", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Objetivo claro y medible que debe alcanzar el subagente.")
                    })
                    put("task", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Instrucciones detalladas de la tarea paso a paso que debe ejecutar el subagente.")
                    })
                    put("relevantFiles", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Rutas de archivos o contexto clave en el que debe enfocarse el subagente.")
                    })
                }
                put("properties", props)
                put("required", JSONArray().put("role").put("goal").put("task"))
            }
            put("parameters", params)
        }
        declarations.put(subagentTool)

        return JSONArray().put(JSONObject().put("functionDeclarations", declarations))
    }

    private fun buildInitialPrompt(userPrompt: String, files: List<ClonedFile>): String {
        val fileTreeOverview = buildString {
            appendLine("=== ESTRUCTURA DEL WORKSPACE AISLADO ===")
            if (files.isEmpty()) {
                appendLine("(No hay archivos en el sandbox aún)")
            } else {
                files.take(20).forEach { file ->
                    val type = if (file.isDirectory) "[DIR]" else "[FILE]"
                    val size = if (file.isDirectory) "" else " (${SandboxManager.formatBytes(file.sizeBytes)})"
                    appendLine("$type ${file.relativePath}$size")
                }
                if (files.size > 20) {
                    appendLine("... y ${files.size - 20} elementos más (usa la herramienta list_workspace_files para ver la estructura completa).")
                }
            }
        }

        return """
            $fileTreeOverview

            === INSTRUCCIÓN / PREGUNTA DEL USUARIO ===
            $userPrompt
        """.trimIndent()
    }

    private val systemPrompt = """
        Eres un asistente de Inteligencia Artificial que opera como Agente Orquestador con herramientas reales dentro de un Sandbox aislado y seguro en Android.
        Tienes disponibles las siguientes herramientas:
        1. list_workspace_files: Para ver la estructura inicial de la carpeta, nombres de archivos y subcarpetas.
        2. read_file: Para leer archivos (.txt, .md, .pdf, código fuente).
        3. create_file: Para crear nuevos archivos (.txt, .md o .lua).
        4. edit_file_part: Para modificar partes específicas de un archivo (.txt, .md o .lua) reemplazando texto exacto.
        5. delete_file: Para eliminar cualquier archivo dentro del sandbox.
        6. execute_lua: Para ejecutar scripts y código en la máquina virtual nativa de Lua 5.4 aislada en C. Puedes escribir tus propios scripts de Lua para procesar datos, hacer cálculos o automatizar tareas.
        7. spawn_subagent: Para generar y delegar subtareas complejas a subagentes especializados (Arquitecto, Constructor, Detective, Crítico, Optimizador, Escudo, Narrador), definiendo su rol, objetivo y tarea puntual.

        REGLAS DE SEGURIDAD Y OPERACIÓN:
        - Las operaciones ocurren dentro del almacenamiento aislado de la app y no dañan los originales del usuario.
        - Si el usuario te pide crear, leer, modificar o eliminar archivos, o consultar la estructura, invoca directamente la herramienta correspondiente.
        - Si una tarea es compleja o multifacética, puedes desplegar un subagente especializado usando spawn_subagent.
        - Si necesitas realizar cálculos o algoritmos rápidos, puedes redactar y ejecutar un script Lua mediante execute_lua.
        - Explica siempre al usuario con amabilidad y en español lo que has realizado mediante las herramientas.
        - Formatea tu respuesta con Markdown limpio.
    """.trimIndent()

    /**
     * Flujo interactivo con soporte de herramientas (Function Calling) en tiempo real
     * y streaming Server-Sent Events (SSE) para la respuesta textual final.
     */
    fun streamAiWithTools(
        userPrompt: String,
        customKey: String?,
        files: List<ClonedFile>,
        onToolExecutionUpdate: (ToolExecution) -> Unit,
        onFilesChanged: () -> Unit
    ): Flow<String> = flow {
        val apiKey = resolveApiKey(customKey)
        if (apiKey.isBlank()) {
            throw IllegalStateException("Se requiere una API Key de Gemini. Configúrala desde el botón de llave 🔑 en la parte superior.")
        }

        val toolsJson = buildToolsDeclaration()
        val contentsArray = JSONArray()

        // Mensaje inicial del usuario
        val initialUserMsg = JSONObject().apply {
            put("role", "user")
            put("parts", JSONArray().put(JSONObject().put("text", buildInitialPrompt(userPrompt, files))))
        }
        contentsArray.put(initialUserMsg)

        var iteration = 0
        val maxToolIterations = 5

        while (iteration < maxToolIterations) {
            iteration++

            val requestJson = JSONObject().apply {
                put("contents", contentsArray)
                put("tools", toolsJson)
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().put(JSONObject().put("text", systemPrompt)))
                })
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.3)
                })
            }

            val requestUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"
            val requestBody = requestJson.toString().toRequestBody(jsonMediaType)
            val request = Request.Builder().url(requestUrl).post(requestBody).build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                val errorMsg = try {
                    val errJson = JSONObject(responseBody)
                    errJson.optJSONObject("error")?.optString("message") ?: "Error HTTP ${response.code}"
                } catch (_: Exception) {
                    "Error HTTP ${response.code}: $responseBody"
                }
                throw Exception(errorMsg)
            }

            val respJson = JSONObject(responseBody)
            val candidates = respJson.optJSONArray("candidates")
            if (candidates == null || candidates.length() == 0) {
                break
            }

            val firstCandidate = candidates.getJSONObject(0)
            val candidateContent = firstCandidate.optJSONObject("content") ?: break
            val parts = candidateContent.optJSONArray("parts") ?: break

            // Verificar si hay llamadas a funciones (functionCall)
            val functionCalls = mutableListOf<JSONObject>()
            val textParts = StringBuilder()

            for (i in 0 until parts.length()) {
                val part = parts.getJSONObject(i)
                if (part.has("functionCall")) {
                    functionCalls.add(part.getJSONObject("functionCall"))
                }
                if (part.has("text")) {
                    textParts.append(part.getString("text"))
                }
            }

            // Si NO hay llamadas a funciones, la IA completó su tarea o respondió directamente
            if (functionCalls.isEmpty()) {
                // Emitir el texto directamente si ya lo tenemos
                val finalText = textParts.toString().trim()
                if (finalText.isNotEmpty()) {
                    emit(finalText)
                }
                return@flow
            }

            // Guardar en el historial la respuesta del modelo que solicita la herramienta
            contentsArray.put(candidateContent)

            // Ejecutar cada herramienta solicitada por la IA
            for (call in functionCalls) {
                val toolName = call.optString("name", "")
                val args = call.optJSONObject("args") ?: JSONObject()
                val callId = UUID.randomUUID().toString()

                val (iconType, displayName, summary) = when (toolName) {
                    "list_workspace_files" -> {
                        val sub = args.optString("subpath", "")
                        Triple(
                            ToolIconType.STRUCTURE,
                            "Estructura del workspace",
                            if (sub.isNotBlank()) "Subcarpeta: $sub" else "Directorio raíz"
                        )
                    }
                    "read_file" -> {
                        val path = args.optString("path", "")
                        Triple(ToolIconType.READ, "Lectura de archivo", path)
                    }
                    "create_file" -> {
                        val path = args.optString("path", "")
                        Triple(ToolIconType.CREATE, "Creación de archivo", path)
                    }
                    "edit_file_part" -> {
                        val path = args.optString("path", "")
                        Triple(ToolIconType.EDIT, "Modificación de fragmento", path)
                    }
                    "delete_file" -> {
                        val path = args.optString("path", "")
                        Triple(ToolIconType.DELETE, "Eliminación de archivo", path)
                    }
                    "execute_lua" -> {
                        val code = args.optString("code", "")
                        val snippet = if (code.length > 40) code.take(37) + "..." else code
                        Triple(ToolIconType.LUA, "Ejecución Lua 5.4 Nativa", snippet.replace("\n", " "))
                    }
                    "spawn_subagent" -> {
                        val role = args.optString("role", "Subagente Especializado")
                        val goal = args.optString("goal", "")
                        Triple(ToolIconType.SUBAGENT, "Subagente: $role", goal)
                    }
                    else -> Triple(ToolIconType.STRUCTURE, toolName, "Ejecutando")
                }

                val subagentInfo = if (toolName == "spawn_subagent") {
                    SubagentInfo(
                        role = args.optString("role", "Subagente Especializado"),
                        goal = args.optString("goal", ""),
                        task = args.optString("task", ""),
                        relevantFiles = args.optString("relevantFiles", "").ifBlank { null }
                    )
                } else null

                // Notificar en tiempo real: herramienta en progreso
                var currentExecution = ToolExecution(
                    callId = callId,
                    toolName = toolName,
                    displayName = displayName,
                    argumentsSummary = summary,
                    iconType = iconType,
                    status = ToolStatus.RUNNING,
                    resultOutput = null,
                    subagentInfo = subagentInfo
                )
                onToolExecutionUpdate(currentExecution)

                // Ejecución real dentro del sandbox
                var toolResultText = ""
                var isSuccess = true

                try {
                    when (toolName) {
                        "list_workspace_files" -> {
                            val subpath = args.optString("subpath", "").ifBlank { null }
                            toolResultText = sandboxManager.toolListStructure(subpath)
                        }
                        "read_file" -> {
                            val path = args.optString("path", "")
                            toolResultText = sandboxManager.toolReadFile(path)
                        }
                        "create_file" -> {
                            val path = args.optString("path", "")
                            val content = args.optString("content", "")
                            val res = sandboxManager.toolCreateFile(path, content)
                            if (res.isSuccess) {
                                toolResultText = res.getOrThrow()
                                onFilesChanged()
                            } else {
                                isSuccess = false
                                toolResultText = res.exceptionOrNull()?.message ?: "Error al crear archivo"
                            }
                        }
                        "edit_file_part" -> {
                            val path = args.optString("path", "")
                            val target = args.optString("targetText", "")
                            val replacement = args.optString("replacementText", "")
                            val res = sandboxManager.toolEditFilePart(path, target, replacement)
                            if (res.isSuccess) {
                                toolResultText = res.getOrThrow()
                                onFilesChanged()
                            } else {
                                isSuccess = false
                                toolResultText = res.exceptionOrNull()?.message ?: "Error al modificar archivo"
                            }
                        }
                        "delete_file" -> {
                            val path = args.optString("path", "")
                            val res = sandboxManager.toolDeleteFile(path)
                            if (res.isSuccess) {
                                toolResultText = res.getOrThrow()
                                onFilesChanged()
                            } else {
                                isSuccess = false
                                toolResultText = res.exceptionOrNull()?.message ?: "Error al eliminar archivo"
                            }
                        }
                        "execute_lua" -> {
                            val code = args.optString("code", "")
                            toolResultText = sandboxManager.toolExecuteLuaScript(code)
                            if (toolResultText.startsWith("Error")) {
                                isSuccess = false
                            }
                        }
                        "spawn_subagent" -> {
                            val role = args.optString("role", "Subagente")
                            val goal = args.optString("goal", "")
                            val task = args.optString("task", "")
                            val relevantFiles = args.optString("relevantFiles", "").ifBlank { null }
                            toolResultText = executeSubagentTask(role, goal, task, relevantFiles, apiKey)
                        }
                        else -> {
                            isSuccess = false
                            toolResultText = "Herramienta desconocida: $toolName"
                        }
                    }
                } catch (e: Exception) {
                    isSuccess = false
                    toolResultText = "Error al ejecutar herramienta: ${e.localizedMessage}"
                }

                // Notificar en tiempo real: herramienta completada (éxito o error)
                currentExecution = currentExecution.copy(
                    status = if (isSuccess) ToolStatus.SUCCESS else ToolStatus.ERROR,
                    resultOutput = toolResultText
                )
                onToolExecutionUpdate(currentExecution)

                // Agregar el resultado de la función para el próximo turno de Gemini
                val functionResponseObj = JSONObject().apply {
                    put("name", toolName)
                    put("response", JSONObject().apply {
                        put("result", toolResultText)
                        put("status", if (isSuccess) "success" else "error")
                    })
                }

                val toolResponseMsg = JSONObject().apply {
                    put("role", "function")
                    put("parts", JSONArray().put(JSONObject().put("functionResponse", functionResponseObj)))
                }
                contentsArray.put(toolResponseMsg)
            }
        }

        // Si tras las herramientas deseamos una respuesta fluida final:
        // Hacemos streaming SSE con el contexto acumulado para una redacción final en tiempo real
        val finalPayload = JSONObject().apply {
            put("contents", contentsArray)
            put("systemInstruction", JSONObject().apply {
                put("parts", JSONArray().put(JSONObject().put("text", systemPrompt)))
            })
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.4)
            })
        }

        val sseUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:streamGenerateContent?alt=sse&key=$apiKey"
        val sseRequestBody = finalPayload.toString().toRequestBody(jsonMediaType)
        val sseRequest = Request.Builder().url(sseUrl).post(sseRequestBody).build()

        val sseResponse = client.newCall(sseRequest).execute()
        if (sseResponse.isSuccessful) {
            val source = sseResponse.body?.byteStream()
            source?.bufferedReader()?.use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val currentLine = line?.trim() ?: continue
                    if (currentLine.startsWith("data:")) {
                        val jsonData = currentLine.removePrefix("data:").trim()
                        if (jsonData.isNotEmpty() && jsonData != "[DONE]") {
                            try {
                                val chunkJson = JSONObject(jsonData)
                                val cands = chunkJson.optJSONArray("candidates")
                                if (cands != null && cands.length() > 0) {
                                    val cand = cands.getJSONObject(0)
                                    val cont = cand.optJSONObject("content")
                                    val prts = cont?.optJSONArray("parts")
                                    if (prts != null) {
                                        for (pIdx in 0 until prts.length()) {
                                            val p = prts.getJSONObject(pIdx)
                                            val t = p.optString("text", "")
                                            if (t.isNotEmpty()) {
                                                emit(t)
                                            }
                                        }
                                    }
                                }
                            } catch (_: Exception) {}
                        }
                    }
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Ejecuta una consulta aislada y especializada para un subagente delegado.
     */
    private suspend fun executeSubagentTask(
        role: String,
        goal: String,
        task: String,
        relevantFiles: String?,
        apiKey: String
    ): String = withContext(Dispatchers.IO) {
        val prompt = """
            Eres un subagente altamente especializado en Folder AI.
            ROL ASIGNADO: $role
            OBJETIVO: $goal
            TAREA ESPECÍFICA: $task
            ARCHIVOS/CONTEXTO RELACIONADO: ${relevantFiles ?: "Entorno general del sandbox"}

            Instrucciones para el subagente:
            - Concéntrate exclusivamente en el rol y la meta asignada.
            - Sé técnico, directo y riguroso.
            - Devuelve un reporte o solución concisa que el Agente Orquestador principal pueda utilizar.
        """.trimIndent()

        val reqJson = JSONObject().apply {
            put("contents", JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("parts", JSONArray().put(JSONObject().put("text", prompt)))
            }))
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.2)
                put("maxOutputTokens", 1200)
            })
        }

        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"
        val req = Request.Builder()
            .url(url)
            .post(reqJson.toString().toRequestBody(jsonMediaType))
            .build()

        try {
            val resp = client.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            if (resp.isSuccessful) {
                val json = JSONObject(body)
                val candidates = json.optJSONArray("candidates")
                val first = candidates?.optJSONObject(0)
                val content = first?.optJSONObject("content")
                val parts = content?.optJSONArray("parts")
                parts?.optJSONObject(0)?.optString("text")
                    ?: "Subagente completó la subtarea sin observaciones adicionales."
            } else {
                "Error al ejecutar subagente (${resp.code})"
            }
        } catch (e: Exception) {
            "Fallo de comunicación del subagente: ${e.localizedMessage}"
        }
    }
}
