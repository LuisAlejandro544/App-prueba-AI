package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.GeminiSandboxClient
import com.example.model.ChatMessage
import com.example.model.CloneStatus
import com.example.model.ClonedFile
import com.example.model.MessageRole
import com.example.model.SandboxWorkspace
import com.example.sandbox.SandboxManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class SandboxViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("sandbox_prefs", Context.MODE_PRIVATE)
    val sandboxManager = SandboxManager(application)
    private val geminiClient = GeminiSandboxClient(sandboxManager)

    private val _workspace = MutableStateFlow<SandboxWorkspace?>(null)
    val workspace: StateFlow<SandboxWorkspace?> = _workspace.asStateFlow()

    private val _cloneStatus = MutableStateFlow<CloneStatus>(CloneStatus.Idle)
    val cloneStatus: StateFlow<CloneStatus> = _cloneStatus.asStateFlow()

    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    private val _isAiThinking = MutableStateFlow(false)
    val isAiThinking: StateFlow<Boolean> = _isAiThinking.asStateFlow()

    private val _customApiKey = MutableStateFlow(prefs.getString("gemini_api_key", "") ?: "")
    val customApiKey: StateFlow<String> = _customApiKey.asStateFlow()

    private val _showApiKeyDialog = MutableStateFlow(false)
    val showApiKeyDialog: StateFlow<Boolean> = _showApiKeyDialog.asStateFlow()

    private val _selectedFileForPreview = MutableStateFlow<Pair<ClonedFile, String>?>(null)
    val selectedFileForPreview: StateFlow<Pair<ClonedFile, String>?> = _selectedFileForPreview.asStateFlow()

    init {
        // Cargar si ya había archivos clonados previamente en el sandbox
        val existingFiles = sandboxManager.getClonedFiles()
        if (existingFiles.isNotEmpty()) {
            val totalBytes = existingFiles.sumOf { it.sizeBytes }
            _workspace.value = SandboxWorkspace(
                folderName = "Workspace Local",
                sourceUriString = "",
                localPath = sandboxManager.workspaceDir.absolutePath,
                totalFiles = existingFiles.count { !it.isDirectory },
                totalSizeBytes = totalBytes,
                clonedTimestamp = System.currentTimeMillis(),
                files = existingFiles
            )
        }

        // Mensaje inicial de bienvenida explicativo
        _chatMessages.value = listOf(
            ChatMessage(
                id = UUID.randomUUID().toString(),
                role = MessageRole.AI,
                text = "👋 ¡Hola! Soy tu asistente IA con herramientas activas sobre el Sandbox seguro.\n\n" +
                        "🔒 **Tus archivos originales están 100% protegidos** (SAF + Sandbox privado).\n\n" +
                        "🛠️ **Herramientas que puedo ejecutar en tiempo real:**\n" +
                        "- 📂 **Ver estructura:** Analizar carpetas, subcarpetas y jerarquías.\n" +
                        "- 📄 **Lectura de archivos:** Ver contenido de .txt, .pdf, .md y código.\n" +
                        "- 📝 **Crear archivos:** Generar nuevos archivos .txt y .md en el sandbox.\n" +
                        "- ✏️ **Modificar fragmentos:** Editar partes específicas de tus .txt y .md.\n" +
                        "- 🗑️ **Eliminar archivos:** Borrar archivos del sandbox cuando lo solicites."
            )
        )
    }

    fun cloneSelectedFolder(uri: Uri) {
        viewModelScope.launch {
            _cloneStatus.value = CloneStatus.InProgress("Iniciando copia segura...", 0)
            try {
                val result = sandboxManager.cloneFromDocumentTree(uri) { currentFileName, count ->
                    _cloneStatus.value = CloneStatus.InProgress(currentFileName, count)
                }
                _workspace.value = result
                _cloneStatus.value = CloneStatus.Success(result.totalFiles, result.totalSizeBytes)

                val fileTypes = result.files.filter { !it.isDirectory }.map { it.name.substringAfterLast('.', "otro") }.distinct()
                val welcomeWorkspace = "✅ **¡Carpeta clonada con éxito en el sandbox aislado!**\n\n" +
                        "- **Carpeta:** ${result.folderName}\n" +
                        "- **Archivos clonados:** ${result.totalFiles} (${SandboxManager.formatBytes(result.totalSizeBytes)})\n" +
                        "- **Extensiones detectadas:** ${fileTypes.joinToString(", ")}\n" +
                        "- **Herramientas IA:** Listar estructura, leer (.txt, .md, .pdf), crear (.txt, .md), modificar fragmentos y eliminar archivos."

                _chatMessages.value = _chatMessages.value + ChatMessage(
                    id = UUID.randomUUID().toString(),
                    role = MessageRole.AI,
                    text = welcomeWorkspace
                )
            } catch (e: Exception) {
                _cloneStatus.value = CloneStatus.Error(e.localizedMessage ?: "Error al clonar carpeta")
            }
        }
    }

    fun askAi(prompt: String) {
        val trimmed = prompt.trim()
        if (trimmed.isBlank() || _isAiThinking.value) return

        val userMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            role = MessageRole.USER,
            text = trimmed
        )
        val aiMessageId = UUID.randomUUID().toString()
        val initialAiMessage = ChatMessage(
            id = aiMessageId,
            role = MessageRole.AI,
            text = "",
            toolExecutions = emptyList()
        )

        _chatMessages.value = _chatMessages.value + userMessage + initialAiMessage
        _isAiThinking.value = true

        viewModelScope.launch {
            val files = _workspace.value?.files ?: sandboxManager.getClonedFiles()
            val responseAccumulator = StringBuilder()

            try {
                geminiClient.streamAiWithTools(
                    userPrompt = trimmed,
                    customKey = _customApiKey.value,
                    files = files,
                    onToolExecutionUpdate = { execution ->
                        _chatMessages.value = _chatMessages.value.map { msg ->
                            if (msg.id == aiMessageId) {
                                val existingIndex = msg.toolExecutions.indexOfFirst { it.callId == execution.callId }
                                val updatedList = if (existingIndex >= 0) {
                                    msg.toolExecutions.toMutableList().apply { set(existingIndex, execution) }
                                } else {
                                    msg.toolExecutions + execution
                                }
                                msg.copy(toolExecutions = updatedList)
                            } else msg
                        }
                    },
                    onFilesChanged = {
                        refreshWorkspaceFiles()
                    }
                ).collect { chunk ->
                    responseAccumulator.append(chunk)
                    val currentText = responseAccumulator.toString()
                    _chatMessages.value = _chatMessages.value.map { msg ->
                        if (msg.id == aiMessageId) msg.copy(text = currentText) else msg
                    }
                }

                if (responseAccumulator.isEmpty()) {
                    _chatMessages.value = _chatMessages.value.map { msg ->
                        if (msg.id == aiMessageId) {
                            if (msg.toolExecutions.isNotEmpty()) {
                                msg.copy(text = "✅ Herramienta(s) ejecutadas exitosamente.")
                            } else {
                                msg.copy(
                                    text = "⚠️ La IA devolvió una respuesta vacía.",
                                    isError = true
                                )
                            }
                        } else msg
                    }
                }
            } catch (e: Exception) {
                val errorText = "⚠️ **Error:** ${e.localizedMessage ?: "Ocurrió un problema al consultar la IA."}\n\n" +
                        "Si no has configurado tu clave API de Gemini, puedes tocar el icono de llave 🔑 arriba a la derecha."
                _chatMessages.value = _chatMessages.value.map { msg ->
                    if (msg.id == aiMessageId) msg.copy(text = errorText, isError = true) else msg
                }
            } finally {
                _isAiThinking.value = false
            }
        }
    }

    private fun refreshWorkspaceFiles() {
        val currentFiles = sandboxManager.getClonedFiles()
        val currentWs = _workspace.value
        if (currentWs != null) {
            _workspace.value = currentWs.copy(
                files = currentFiles,
                totalFiles = currentFiles.count { !it.isDirectory },
                totalSizeBytes = currentFiles.filter { !it.isDirectory }.sumOf { it.sizeBytes }
            )
        }
    }

    fun inspectFile(file: ClonedFile) {
        if (file.isDirectory) return
        viewModelScope.launch {
            val content = sandboxManager.readFileContent(file.relativePath, maxChars = 30_000)
                ?: "(Archivo vacío o no legible como texto)"
            _selectedFileForPreview.value = file to content
        }
    }

    fun dismissFilePreview() {
        _selectedFileForPreview.value = null
    }

    fun clearSandbox() {
        viewModelScope.launch {
            sandboxManager.clearWorkspace()
            _workspace.value = null
            _cloneStatus.value = CloneStatus.Idle
            _chatMessages.value = _chatMessages.value + ChatMessage(
                id = UUID.randomUUID().toString(),
                role = MessageRole.SYSTEM,
                text = "🧹 Sandbox limpiado. No quedan archivos en el entorno aislado."
            )
        }
    }

    fun setApiKeyDialogVisible(visible: Boolean) {
        _showApiKeyDialog.value = visible
    }

    fun saveCustomApiKey(key: String) {
        val trimmed = key.trim()
        prefs.edit().putString("gemini_api_key", trimmed).apply()
        _customApiKey.value = trimmed
        _showApiKeyDialog.value = false
    }
}
