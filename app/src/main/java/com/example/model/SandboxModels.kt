package com.example.model

data class ClonedFile(
    val relativePath: String,
    val name: String,
    val sizeBytes: Long,
    val isDirectory: Boolean,
    val lastModified: Long,
    val isTextFile: Boolean
)

data class SandboxWorkspace(
    val folderName: String,
    val sourceUriString: String,
    val localPath: String,
    val totalFiles: Int,
    val totalSizeBytes: Long,
    val clonedTimestamp: Long,
    val files: List<ClonedFile>
)

enum class MessageRole {
    USER,
    AI,
    SYSTEM
}

enum class ToolIconType {
    STRUCTURE,  // list_workspace_files
    READ,       // read_file (.txt, .md, .pdf, código)
    CREATE,     // create_file (.txt, .md, .lua)
    EDIT,       // edit_file_part (.txt, .md)
    DELETE,     // delete_file
    LUA,        // execute_lua_script
    SUBAGENT    // spawn_subagent
}

enum class ToolStatus {
    RUNNING,
    SUCCESS,
    ERROR
}

data class SubagentInfo(
    val role: String,
    val goal: String,
    val task: String,
    val relevantFiles: String? = null
)

data class ToolExecution(
    val callId: String,
    val toolName: String,
    val displayName: String,
    val argumentsSummary: String,
    val iconType: ToolIconType,
    val status: ToolStatus,
    val resultOutput: String? = null,
    val subagentInfo: SubagentInfo? = null
)

data class ChatMessage(
    val id: String,
    val role: MessageRole,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isError: Boolean = false,
    val toolExecutions: List<ToolExecution> = emptyList()
)

sealed interface CloneStatus {
    object Idle : CloneStatus
    data class InProgress(val currentFile: String, val count: Int, val total: Int = 0) : CloneStatus
    data class Success(val count: Int, val totalBytes: Long) : CloneStatus
    data class Error(val message: String) : CloneStatus
}
