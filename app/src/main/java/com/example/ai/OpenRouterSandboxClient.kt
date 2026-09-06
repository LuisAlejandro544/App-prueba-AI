package com.example.ai

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

object OpenRouterModels {
    const val POOLSIDE_LAGUNA = "poolside/laguna-s-2.1:free"
    const val NVIDIA_NEMOTRON = "nvidia/nemotron-3.5-lightning:free"
    const val THINKINGMACHINES_INKLING = "thinkingmachines/inkling:free"

    const val DEFAULT_MODEL = POOLSIDE_LAGUNA

    val ALL_FREE_MODELS = listOf(
        POOLSIDE_LAGUNA,
        NVIDIA_NEMOTRON,
        THINKINGMACHINES_INKLING
    )

    fun getDisplayName(modelId: String): String {
        return when (modelId) {
            POOLSIDE_LAGUNA -> "Poolside Laguna S 2.1 (Free)"
            NVIDIA_NEMOTRON -> "NVIDIA Nemotron 3.5 Lightning (Free)"
            THINKINGMACHINES_INKLING -> "Thinking Machines Inkling (Free)"
            else -> modelId
        }
    }

    fun getShortLabel(modelId: String): String {
        return when (modelId) {
            POOLSIDE_LAGUNA -> "laguna-s-2.1"
            NVIDIA_NEMOTRON -> "nemotron-3.5"
            THINKINGMACHINES_INKLING -> "inkling"
            else -> modelId.substringAfterLast("/").removeSuffix(":free")
        }
    }
}

class OpenRouterSandboxClient(
    private val sandboxManager: SandboxManager
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val openRouterApiUrl = "https://openrouter.ai/api/v1/chat/completions"

    /**
     * Declaración formal de herramientas para OpenRouter (especificación compatible con OpenAI Function Calling).
     */
    private fun buildToolsDeclaration(): JSONArray {
        val toolsArray = JSONArray()

        // 1. list_workspace_files
        val listTool = JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "list_workspace_files")
                put("description", "Obtiene la estructura inicial y completa de la carpeta, nombres de archivos y qué hay dentro de subcarpetas en el sandbox.")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("subpath", JSONObject().apply {
                            put("type", "string")
                            put("description", "Subcarpeta específica a inspeccionar (opcional, deja vacío para ver la raíz completa).")
                        })
                    })
                })
            })
        }
        toolsArray.put(listTool)

        // 2. read_file
        val readTool = JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "read_file")
                put("description", "Lee el contenido de un archivo del sandbox. Soporta archivos .txt, .pdf, .md, .lua, código fuente y documentos de texto.")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("path", JSONObject().apply {
                            put("type", "string")
                            put("description", "Ruta relativa del archivo a leer (ej: 'notas.txt', 'documentos/guia.pdf', 'README.md').")
                        })
                    })
                    put("required", JSONArray().put("path"))
                })
            })
        }
        toolsArray.put(readTool)

        // 3. create_file
        val createTool = JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "create_file")
                put("description", "Crea un nuevo archivo (.txt, .md o .lua) dentro del sandbox aislado. Crea subcarpetas intermedias automáticamente.")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("path", JSONObject().apply {
                            put("type", "string")
                            put("description", "Ruta relativa del archivo a crear. Debe terminar en .txt, .md o .lua (ej: 'resumen.md', 'scripts/analisis.lua', 'notas.txt').")
                        })
                        put("content", JSONObject().apply {
                            put("type", "string")
                            put("description", "Contenido de texto o código que se escribirá en el nuevo archivo.")
                        })
                    })
                    put("required", JSONArray().put("path").put("content"))
                })
            })
        }
        toolsArray.put(createTool)

        // 4. edit_file_part
        val editTool = JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "edit_file_part")
                put("description", "Modifica una parte específica de un archivo .txt, .md o .lua existente, reemplazando un texto exacto buscado por el nuevo texto.")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("path", JSONObject().apply {
                            put("type", "string")
                            put("description", "Ruta relativa del archivo a modificar (.txt, .md o .lua).")
                        })
                        put("targetText", JSONObject().apply {
                            put("type", "string")
                            put("description", "Fragmento de texto EXACTO que se desea buscar dentro del archivo para sustituir.")
                        })
                        put("replacementText", JSONObject().apply {
                            put("type", "string")
                            put("description", "Nuevo texto que reemplazará al fragmento encontrado.")
                        })
                    })
                    put("required", JSONArray().put("path").put("targetText").put("replacementText"))
                })
            })
        }
        toolsArray.put(editTool)

        // 5. delete_file
        val deleteTool = JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "delete_file")
                put("description", "Elimina permanentemente un archivo dentro del sandbox aislado. No afecta los archivos originales del usuario.")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("path", JSONObject().apply {
                            put("type", "string")
                            put("description", "Ruta relativa del archivo dentro del sandbox que se desea eliminar.")
                        })
                    })
                    put("required", JSONArray().put("path"))
                })
            })
        }
        toolsArray.put(deleteTool)

        // 6. execute_lua
        val luaTool = JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "execute_lua")
                put("description", "Ejecuta un script Lua 5.4 nativo en C con acceso completo y directo al sistema de archivos del sandbox aislado. El directorio de trabajo actual (CWD) es la carpeta del sandbox. Puedes usar io.open('nombre.txt', 'w') para crear/escribir archivos directamente, o la biblioteca sandbox (sandbox.write_file, sandbox.read_file, sandbox.list_files, sandbox.delete_file). Utiliza SIEMPRE print(...) dentro del script para informar claramente de los archivos creados o los resultados obtenidos.")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("code", JSONObject().apply {
                            put("type", "string")
                            put("description", "Código fuente Lua 5.4 completo a ejecutar. Para crear archivos usa io.open('archivo.txt', 'w') o sandbox.write_file('archivo.txt', contenido), y llama a print(...) para reportar el resultado.")
                        })
                    })
                    put("required", JSONArray().put("code"))
                })
            })
        }
        toolsArray.put(luaTool)

        // 7. spawn_subagent
        val subagentTool = JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "spawn_subagent")
                put("description", "Despliega un subagente autónomo con un rol técnico especializado para resolver una subtarea compleja (Arquitecto, Constructor, Detective, Crítico, Optimizador, Escudo, Narrador). El subagente ejecutará la subtarea y devolverá sus conclusiones técnicas.")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("role", JSONObject().apply {
                            put("type", "string")
                            put("description", "Rol técnico del subagente: 'Arquitecto', 'Constructor', 'Detective', 'Crítico', 'Optimizador', 'Escudo' o 'Narrador'.")
                        })
                        put("goal", JSONObject().apply {
                            put("type", "string")
                            put("description", "Objetivo general de alto nivel asignado al subagente.")
                        })
                        put("task", JSONObject().apply {
                            put("type", "string")
                            put("description", "Instrucción o tarea puntual y detallada que debe resolver el subagente.")
                        })
                        put("relevantFiles", JSONObject().apply {
                            put("type", "string")
                            put("description", "Nombres de archivos o rutas clave relevantes para la tarea (opcional).")
                        })
                    })
                    put("required", JSONArray().put("role").put("goal").put("task"))
                })
            })
        }
        toolsArray.put(subagentTool)

        return toolsArray
    }

    private fun buildSystemPrompt(files: List<ClonedFile>): String {
        val nonDirFiles = files.filter { !it.isDirectory }
        val fileListing = if (nonDirFiles.isEmpty()) {
            "El sandbox está actualmente vacío (no hay archivos clonados aún)."
        } else {
            nonDirFiles.take(50).joinToString("\n") { f ->
                "- ${f.relativePath} (${SandboxManager.formatBytes(f.sizeBytes)})"
            } + if (nonDirFiles.size > 50) "\n... y ${nonDirFiles.size - 50} archivos más." else ""
        }

        return """
        Eres un Asistente de Inteligencia Artificial en OpenRouter integrado en Folder AI, un entorno Sandbox de seguridad para Android.
        
        ESTADO ACTUAL DEL SANDBOX AISLADO:
        $fileListing
        
        HERRAMIENTAS DISPONIBLES:
        1. list_workspace_files: Para ver qué archivos y subdirectorios hay.
        2. read_file: Para leer el contenido completo de cualquier archivo (.txt, .md, .pdf, .lua o código).
        3. create_file: Para crear nuevos archivos (.txt, .md o .lua).
        4. edit_file_part: Para modificar partes específicas de un archivo (.txt, .md o .lua) reemplazando texto exacto.
        5. delete_file: Para eliminar cualquier archivo dentro del sandbox.
        6. execute_lua: Para ejecutar scripts y código en la máquina virtual nativa de Lua 5.4 aislada en C con acceso directo al sistema de archivos del workspace. Puedes usar io.open('archivo.txt', 'w') o la biblioteca sandbox (sandbox.write_file, sandbox.read_file, sandbox.list_files, sandbox.delete_file) para generar, transformar o manipular archivos en el sandbox, usando siempre print(...) para reportar el resumen de los datos generados.
        7. spawn_subagent: Para generar y delegar subtareas complejas a subagentes especializados (Arquitecto, Constructor, Detective, Crítico, Optimizador, Escudo, Narrador), definiendo su rol, objetivo y tarea puntual.

        REGLAS DE SEGURIDAD Y OPERACIÓN:
        - Todas las herramientas operan de forma 100% aislada en la copia del sandbox; jamás modifican los archivos originales del teléfono.
        - Si el usuario te pide crear, leer, modificar o eliminar archivos, o consultar la estructura, invoca directamente la herramienta correspondiente.
        - Si el usuario te pide expresamente usar comandos o scripts de Lua para generar o automatizar archivos, utiliza execute_lua escribiendo un script completo en Lua 5.4 que cree o abra el archivo (usando io.open o sandbox.write_file('nombre.txt', contenido)), escriba los datos y use print(...) informando con precisión el nombre y contenido del archivo creado.
        - Si una tarea es compleja o multifacética, puedes desplegar un subagente especializado usando spawn_subagent.
        - Si necesitas realizar cálculos o algoritmos rápidos, puedes redactar y ejecutar un script Lua mediante execute_lua.
        - Explica siempre al usuario con amabilidad y en español lo que has realizado mediante las herramientas.
        """.trimIndent()
    }

    /**
     * Ejecución del flujo interactivo con OpenRouter y gestión de herramientas.
     */
    fun streamAiWithTools(
        userPrompt: String,
        apiKey: String,
        model: String = OpenRouterModels.DEFAULT_MODEL,
        files: List<ClonedFile>,
        onToolExecutionUpdate: (ToolExecution) -> Unit,
        onFilesChanged: () -> Unit
    ): Flow<String> = flow {
        val cleanKey = apiKey.trim()
        if (cleanKey.isBlank()) {
            throw Exception("Se requiere una API Key de OpenRouter para consultar este modelo. Toca el icono de llave 🔑 arriba a la derecha para configurarla.")
        }

        val messagesArray = JSONArray()
        messagesArray.put(JSONObject().apply {
            put("role", "system")
            put("content", buildSystemPrompt(files))
        })
        messagesArray.put(JSONObject().apply {
            put("role", "user")
            put("content", userPrompt)
        })

        val tools = buildToolsDeclaration()
        var loopCount = 0
        val maxLoops = 6
        var finalContentFound = false

        while (loopCount < maxLoops && !finalContentFound) {
            loopCount++

            val requestJson = JSONObject().apply {
                put("model", model)
                put("messages", messagesArray)
                put("tools", tools)
                put("tool_choice", "auto")
            }

            val request = Request.Builder()
                .url(openRouterApiUrl)
                .addHeader("Authorization", "Bearer $cleanKey")
                .addHeader("Content-Type", "application/json; charset=utf-8")
                .addHeader("HTTP-Referer", "https://folderai.app")
                .addHeader("X-Title", "Folder AI")
                .post(requestJson.toString().toRequestBody(jsonMediaType))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                val errorDetail = try {
                    val errJson = JSONObject(responseBody)
                    errJson.optJSONObject("error")?.optString("message") ?: "HTTP ${response.code}"
                } catch (_: Exception) {
                    "HTTP ${response.code}: $responseBody"
                }

                // Manejo de modelos que no admitan parámetro 'tools'
                if (response.code == 400 && (errorDetail.contains("tool", ignoreCase = true) || errorDetail.contains("function", ignoreCase = true))) {
                    emit("⚠️ El modelo $model no soporta llamadas a herramientas automáticas. Respondiendo en modo conversacional directo...\n\n")
                    val fallbackReq = JSONObject().apply {
                        put("model", model)
                        put("messages", messagesArray)
                    }
                    val req2 = Request.Builder()
                        .url(openRouterApiUrl)
                        .addHeader("Authorization", "Bearer $cleanKey")
                        .addHeader("Content-Type", "application/json; charset=utf-8")
                        .addHeader("HTTP-Referer", "https://folderai.app")
                        .addHeader("X-Title", "Folder AI")
                        .post(fallbackReq.toString().toRequestBody(jsonMediaType))
                        .build()
                    val resp2 = client.newCall(req2).execute()
                    val body2 = resp2.body?.string() ?: ""
                    if (resp2.isSuccessful) {
                        val json2 = JSONObject(body2)
                        val text = json2.optJSONArray("choices")
                            ?.optJSONObject(0)
                            ?.optJSONObject("message")
                            ?.optString("content", "") ?: ""
                        emit(text)
                        finalContentFound = true
                        break
                    }
                }

                throw Exception("Error de OpenRouter ($errorDetail). Verifica tu clave en el icono de llave 🔑.")
            }

            val respJson = JSONObject(responseBody)
            val choices = respJson.optJSONArray("choices")
            if (choices == null || choices.length() == 0) {
                break
            }

            val firstChoice = choices.getJSONObject(0)
            val assistantMessage = firstChoice.optJSONObject("message") ?: break
            val contentText = assistantMessage.optString("content", "")
            val toolCalls = assistantMessage.optJSONArray("tool_calls")

            // Agregamos la respuesta del asistente a la conversación
            messagesArray.put(assistantMessage)

            if (toolCalls != null && toolCalls.length() > 0) {
                for (i in 0 until toolCalls.length()) {
                    val call = toolCalls.getJSONObject(i)
                    val callId = call.optString("id", UUID.randomUUID().toString())
                    val functionObj = call.optJSONObject("function") ?: continue
                    val toolName = functionObj.optString("name", "")
                    val argsString = functionObj.optString("arguments", "{}")
                    val callArgs = try {
                        JSONObject(argsString)
                    } catch (_: Exception) {
                        JSONObject()
                    }

                    val (iconType, title, summary, subagentInfo) = resolveToolVisualMetadata(toolName, callArgs)

                    val runningExecution = ToolExecution(
                        callId = callId,
                        toolName = toolName,
                        displayName = title,
                        argumentsSummary = summary,
                        iconType = iconType,
                        status = ToolStatus.RUNNING,
                        resultOutput = null,
                        subagentInfo = subagentInfo
                    )
                    onToolExecutionUpdate(runningExecution)

                    var toolResultText: String
                    var isSuccess = true

                    try {
                        when (toolName) {
                            "list_workspace_files" -> {
                                val subpath = callArgs.optString("subpath", "").ifBlank { null }
                                toolResultText = sandboxManager.toolListStructure(subpath)
                            }
                            "read_file" -> {
                                val path = callArgs.optString("path", "")
                                toolResultText = sandboxManager.toolReadFile(path)
                                if (toolResultText.startsWith("Error")) isSuccess = false
                            }
                            "create_file" -> {
                                val path = callArgs.optString("path", "")
                                val content = callArgs.optString("content", "")
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
                                val path = callArgs.optString("path", "")
                                val target = callArgs.optString("targetText", "")
                                val replacement = callArgs.optString("replacementText", "")
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
                                val path = callArgs.optString("path", "")
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
                                val code = callArgs.optString("code", "")
                                toolResultText = sandboxManager.toolExecuteLuaScript(code)
                                if (toolResultText.startsWith("Error")) {
                                    isSuccess = false
                                } else {
                                    onFilesChanged()
                                }
                            }
                            "spawn_subagent" -> {
                                val role = callArgs.optString("role", "Subagente")
                                val goal = callArgs.optString("goal", "")
                                val task = callArgs.optString("task", "")
                                val relevantFiles = callArgs.optString("relevantFiles", "").ifBlank { null }

                                toolResultText = executeSubagent(
                                    apiKey = cleanKey,
                                    model = model,
                                    role = role,
                                    goal = goal,
                                    task = task,
                                    relevantFiles = relevantFiles,
                                    files = files
                                )
                            }
                            else -> {
                                toolResultText = "Herramienta '$toolName' no reconocida en este entorno."
                                isSuccess = false
                            }
                        }
                    } catch (e: Exception) {
                        toolResultText = "Error al ejecutar '$toolName': ${e.localizedMessage}"
                        isSuccess = false
                    }

                    val finishedExecution = runningExecution.copy(
                        status = if (isSuccess) ToolStatus.SUCCESS else ToolStatus.ERROR,
                        resultOutput = toolResultText
                    )
                    onToolExecutionUpdate(finishedExecution)

                    // Añadir respuesta de herramienta en formato OpenAI Tool Response
                    val toolMsg = JSONObject().apply {
                        put("role", "tool")
                        put("tool_call_id", callId)
                        put("name", toolName)
                        put("content", toolResultText)
                    }
                    messagesArray.put(toolMsg)
                }
            } else {
                // No hay tool_calls: emitir el contenido final devuelto
                if (contentText.isNotBlank()) {
                    emit(contentText)
                }
                finalContentFound = true
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun resolveToolVisualMetadata(
        toolName: String,
        args: JSONObject
    ): Tuple4<ToolIconType, String, String, SubagentInfo?> {
        return when (toolName) {
            "list_workspace_files" -> {
                val sub = args.optString("subpath")
                val summary = if (sub.isNotBlank()) "Subcarpeta: $sub" else "Raíz completa del sandbox"
                Tuple4(ToolIconType.STRUCTURE, "Inspeccionar Estructura", summary, null)
            }
            "read_file" -> {
                val path = args.optString("path")
                Tuple4(ToolIconType.READ, "Leer Archivo", path, null)
            }
            "create_file" -> {
                val path = args.optString("path")
                Tuple4(ToolIconType.CREATE, "Crear Archivo", path, null)
            }
            "edit_file_part" -> {
                val path = args.optString("path")
                Tuple4(ToolIconType.EDIT, "Modificar Fragmento", path, null)
            }
            "delete_file" -> {
                val path = args.optString("path")
                Tuple4(ToolIconType.DELETE, "Eliminar Archivo", path, null)
            }
            "execute_lua" -> {
                Tuple4(ToolIconType.LUA, "Ejecutar Script Lua 5.4", "Máquina Virtual C nativa", null)
            }
            "spawn_subagent" -> {
                val role = args.optString("role", "Subagente")
                val goal = args.optString("goal", "")
                val task = args.optString("task", "")
                Tuple4(
                    ToolIconType.SUBAGENT,
                    "Subagente: $role",
                    "Meta: $goal",
                    SubagentInfo(role = role, goal = goal, task = task)
                )
            }
            else -> Tuple4(ToolIconType.STRUCTURE, toolName, "Ejecutando...", null)
        }
    }

    private data class Tuple4<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

    /**
     * Ejecuta una subtarea delegada a un subagente utilizando OpenRouter.
     */
    private suspend fun executeSubagent(
        apiKey: String,
        model: String,
        role: String,
        goal: String,
        task: String,
        relevantFiles: String?,
        files: List<ClonedFile>
    ): String = withContext(Dispatchers.IO) {
        val prompt = """
        Eres un SUBAGENTE TÉCNICO especializado en Folder AI con el rol de: $role.
        Tu objetivo general: $goal.
        Archivos relevantes: ${relevantFiles ?: "Todo el proyecto"}.
        
        TAREA PUNTUAL:
        $task
        
        INSTRUCCIONES DEL SUBAGENTE:
        - Responde de forma directa, técnica y profesional en español.
        - Sé concreto, sin rodeos ni saludos innecesarios.
        - Entrega tu diagnóstico, código, análisis o solución según corresponda a tu rol.
        """.trimIndent()

        val reqJson = JSONObject().apply {
            put("model", model)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", "Eres un subagente de ingeniería de software con alta especialización técnica.")
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            })
        }

        try {
            val req = Request.Builder()
                .url(openRouterApiUrl)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json; charset=utf-8")
                .addHeader("HTTP-Referer", "https://folderai.app")
                .addHeader("X-Title", "Folder AI")
                .post(reqJson.toString().toRequestBody(jsonMediaType))
                .build()

            val resp = client.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            if (resp.isSuccessful) {
                val json = JSONObject(body)
                val choices = json.optJSONArray("choices")
                val first = choices?.optJSONObject(0)
                val msg = first?.optJSONObject("message")
                msg?.optString("content") ?: "Subagente completó la subtarea sin observaciones adicionales."
            } else {
                "Error al ejecutar subagente (${resp.code})"
            }
        } catch (e: Exception) {
            "Fallo de comunicación del subagente: ${e.localizedMessage}"
        }
    }
}
