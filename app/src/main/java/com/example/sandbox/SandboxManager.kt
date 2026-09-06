package com.example.sandbox

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.example.model.ClonedFile
import com.example.model.NativeEngineBridge
import com.example.model.SandboxWorkspace
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.Locale

class SandboxManager(private val context: Context) {

    init {
        try {
            PDFBoxResourceLoader.init(context)
        } catch (_: Throwable) {}
    }

    val workspaceDir: File
        get() = File(context.filesDir, "sandbox_workspace").apply {
            if (!exists()) {
                mkdirs()
            }
        }

    fun clearWorkspace(): Boolean {
        return try {
            val dir = File(context.filesDir, "sandbox_workspace")
            if (dir.exists()) {
                dir.deleteRecursively()
            }
            dir.mkdirs()
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun cloneFromDocumentTree(
        treeUri: Uri,
        onProgress: suspend (fileName: String, count: Int) -> Unit
    ): SandboxWorkspace = withContext(Dispatchers.IO) {
        val rootDoc = DocumentFile.fromTreeUri(context, treeUri)
            ?: throw IllegalArgumentException("No se pudo acceder a la carpeta seleccionada")

        val folderName = rootDoc.name ?: "Carpeta_Dispositivo"

        // Limpiar el sandbox para tener una copia limpia y aislada
        clearWorkspace()
        val targetRoot = workspaceDir

        var copiedCount = 0
        var totalBytes = 0L

        suspend fun copyRecursive(sourceDoc: DocumentFile, currentDestDir: File, relativeParent: String) {
            val children = sourceDoc.listFiles()
            for (child in children) {
                val childName = child.name ?: "archivo_sin_nombre"
                val relativePath = if (relativeParent.isEmpty()) childName else "$relativeParent/$childName"

                if (child.isDirectory) {
                    val subDir = File(currentDestDir, childName)
                    subDir.mkdirs()
                    copyRecursive(child, subDir, relativePath)
                } else {
                    val targetFile = File(currentDestDir, childName)
                    var stream: InputStream? = null
                    var outStream: FileOutputStream? = null
                    try {
                        stream = context.contentResolver.openInputStream(child.uri)
                        if (stream != null) {
                            outStream = FileOutputStream(targetFile)
                            val buffer = ByteArray(8192)
                            var read: Int
                            while (stream.read(buffer).also { read = it } != -1) {
                                outStream.write(buffer, 0, read)
                                totalBytes += read
                            }
                            copiedCount++
                            onProgress(childName, copiedCount)
                        }
                    } catch (e: Exception) {
                        // Continuar con los demás archivos si uno falla
                    } finally {
                        try { stream?.close() } catch (_: Exception) {}
                        try { outStream?.close() } catch (_: Exception) {}
                    }
                }
            }
        }

        copyRecursive(rootDoc, targetRoot, "")

        val clonedFiles = scanDirectory(targetRoot, targetRoot)

        SandboxWorkspace(
            folderName = folderName,
            sourceUriString = treeUri.toString(),
            localPath = targetRoot.absolutePath,
            totalFiles = clonedFiles.count { !it.isDirectory },
            totalSizeBytes = totalBytes,
            clonedTimestamp = System.currentTimeMillis(),
            files = clonedFiles
        )
    }

    fun getClonedFiles(): List<ClonedFile> {
        val root = workspaceDir
        if (!root.exists()) return emptyList()
        return scanDirectory(root, root)
    }

    private fun scanDirectory(current: File, root: File): List<ClonedFile> {
        val result = mutableListOf<ClonedFile>()
        val files = current.listFiles() ?: return result

        for (file in files.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase(Locale.ROOT) }))) {
            val relativePath = file.relativeTo(root).path
            val isDir = file.isDirectory
            val isText = if (isDir) false else isTextOrCodeFile(file.name)

            result.add(
                ClonedFile(
                    relativePath = relativePath,
                    name = file.name,
                    sizeBytes = if (isDir) 0L else file.length(),
                    isDirectory = isDir,
                    lastModified = file.lastModified(),
                    isTextFile = isText
                )
            )

            if (isDir) {
                result.addAll(scanDirectory(file, root))
            }
        }
        return result
    }

    fun readFileContent(relativePath: String, maxChars: Int = 50_000): String? {
        val file = File(workspaceDir, relativePath)
        if (!file.exists() || file.isDirectory) return null
        return try {
            val content = file.readText(Charsets.UTF_8)
            if (content.length > maxChars) {
                content.take(maxChars) + "\n\n... [Contenido truncado para la IA]"
            } else {
                content
            }
        } catch (e: Exception) {
            "Error al leer archivo: ${e.localizedMessage}"
        }
    }

    fun writeSandboxFile(relativePath: String, content: String): Boolean {
        val file = File(workspaceDir, relativePath)
        return try {
            file.parentFile?.mkdirs()
            file.writeText(content, Charsets.UTF_8)
            true
        } catch (e: Exception) {
            false
        }
    }

    // ==========================================
    // HERRAMIENTAS DIRECTAS PARA EL AGENTE IA
    // ==========================================

    /**
     * Herramienta 1: Ver estructura de archivos y carpetas del workspace aislado.
     */
    fun toolListStructure(subpath: String? = null): String {
        val target = if (subpath.isNullOrBlank()) {
            workspaceDir
        } else {
            val sanitized = sanitizeRelativePath(subpath)
            File(workspaceDir, sanitized)
        }

        if (!target.exists()) {
            return "Error: La ruta especificada no existe en el sandbox: ${subpath ?: ""}"
        }

        val allCloned = scanDirectory(target, workspaceDir)
        if (allCloned.isEmpty()) {
            return "El directorio está vacío (no contiene archivos ni subcarpetas)."
        }

        val sb = StringBuilder()
        sb.appendLine("Estructura de archivos y subcarpetas (${allCloned.size} elementos):")
        for (item in allCloned) {
            val prefix = if (item.isDirectory) "📁 [DIR] " else "📄 [FILE]"
            val sizeInfo = if (item.isDirectory) "" else " - ${formatBytes(item.sizeBytes)}"
            sb.appendLine("$prefix ${item.relativePath}$sizeInfo")
        }
        return sb.toString().trimEnd()
    }

    /**
     * Herramienta 2: Leer el contenido de un archivo (.txt, .md, .pdf, código y texto).
     */
    fun toolReadFile(relativePath: String): String {
        val sanitized = sanitizeRelativePath(relativePath)
        val file = File(workspaceDir, sanitized)

        if (!file.exists()) {
            return "Error: El archivo no existe en el sandbox: $relativePath"
        }
        if (file.isDirectory) {
            return "Error: La ruta especificada es un directorio, no un archivo: $relativePath. Usa toolListStructure para ver su contenido."
        }

        val ext = file.extension.lowercase(Locale.ROOT)

        // Soporte específico para PDF mediante PDFBox
        if (ext == "pdf") {
            return try {
                val document = PDDocument.load(file)
                try {
                    val pageCount = document.numberOfPages
                    val stripper = PDFTextStripper()
                    val extractedText = stripper.getText(document)
                    val textTrimmed = extractedText.trim()
                    if (textTrimmed.isBlank()) {
                        "Documento PDF ($pageCount páginas): El archivo no contiene texto seleccionable o es un documento escaneado/imágenes."
                    } else {
                        val maxChars = 20_000
                        val preview = if (textTrimmed.length > maxChars) {
                            textTrimmed.take(maxChars) + "\n\n... [Texto PDF truncado a los primeros 20.000 caracteres]"
                        } else {
                            textTrimmed
                        }
                        "--- DOCUMENTO PDF: $relativePath ($pageCount páginas) ---\n$preview"
                    }
                } finally {
                    document.close()
                }
            } catch (e: Exception) {
                "Error al extraer texto del archivo PDF: ${e.localizedMessage}"
            }
        }

        // Archivos de texto, Markdown y código
        return try {
            val content = file.readText(Charsets.UTF_8)
            val maxChars = 30_000
            if (content.length > maxChars) {
                content.take(maxChars) + "\n\n... [Contenido truncado a los primeros 30.000 caracteres]"
            } else {
                content
            }
        } catch (e: Exception) {
            "Error al leer el archivo como texto: ${e.localizedMessage}"
        }
    }

    /**
     * Herramienta 3: Crear un nuevo archivo (.txt, .md o .lua).
     */
    fun toolCreateFile(relativePath: String, content: String): Result<String> {
        val sanitized = sanitizeRelativePath(relativePath)
        val ext = sanitized.substringAfterLast('.', "").lowercase(Locale.ROOT)
        if (ext != "txt" && ext != "md" && ext != "lua") {
            return Result.failure(
                IllegalArgumentException("Se permite crear archivos con extensión .txt, .md o .lua (especificaste: .$ext)")
            )
        }

        val targetFile = File(workspaceDir, sanitized)
        if (targetFile.exists()) {
            return Result.failure(
                IllegalStateException("El archivo ya existe en el sandbox: $sanitized. Si deseas modificarlo usa edit_file_part.")
            )
        }

        return try {
            targetFile.parentFile?.mkdirs()
            targetFile.writeText(content, Charsets.UTF_8)
            Result.success("Archivo creado con éxito: $sanitized (${formatBytes(targetFile.length())})")
        } catch (e: Exception) {
            Result.failure(Exception("Error al crear archivo: ${e.localizedMessage}"))
        }
    }

    /**
     * Herramienta 4: Modificar una parte específica de un archivo (.txt, .md o .lua).
     */
    fun toolEditFilePart(relativePath: String, targetText: String, replacementText: String): Result<String> {
        val sanitized = sanitizeRelativePath(relativePath)
        val ext = sanitized.substringAfterLast('.', "").lowercase(Locale.ROOT)
        if (ext != "txt" && ext != "md" && ext != "lua") {
            return Result.failure(
                IllegalArgumentException("Se permite modificar archivos con extensión .txt, .md o .lua (especificaste: .$ext)")
            )
        }

        val targetFile = File(workspaceDir, sanitized)
        if (!targetFile.exists()) {
            return Result.failure(
                IllegalArgumentException("El archivo a modificar no existe en el sandbox: $sanitized")
            )
        }
        if (targetFile.isDirectory) {
            return Result.failure(
                IllegalArgumentException("La ruta es un directorio: $sanitized")
            )
        }

        if (targetText.isEmpty()) {
            return Result.failure(
                IllegalArgumentException("targetText no puede estar vacío.")
            )
        }

        // Usar motor C++ nativo si está cargado
        var nativeSuccess = false
        if (NativeEngineBridge.isLoaded()) {
            try {
                nativeSuccess = NativeEngineBridge.nativeEditFilePart(
                    targetFile.absolutePath,
                    targetText,
                    replacementText
                )
            } catch (_: Throwable) {}
        }

        if (nativeSuccess) {
            return Result.success("Parte del archivo modificada exitosamente usando el motor nativo C++: $sanitized")
        }

        // Fallback seguro en Kotlin si la llamada nativa no estuviera disponible
        return try {
            val content = targetFile.readText(Charsets.UTF_8)
            if (!content.contains(targetText)) {
                return Result.failure(
                    IllegalArgumentException("No se encontró el fragmento 'targetText' exacto en el archivo $sanitized.")
                )
            }
            val updated = content.replaceFirst(targetText, replacementText)
            targetFile.writeText(updated, Charsets.UTF_8)
            Result.success("Parte del archivo modificada con éxito: $sanitized")
        } catch (e: Exception) {
            Result.failure(Exception("Error al editar archivo: ${e.localizedMessage}"))
        }
    }

    /**
     * Herramienta 6: Ejecutar código o script Lua 5.4 de forma nativa y aislada.
     */
    fun toolExecuteLuaScript(code: String): String {
        if (code.isBlank()) {
            return "Error: El código Lua está vacío."
        }
        return try {
            if (NativeEngineBridge.isLoaded()) {
                NativeEngineBridge.nativeExecuteLuaScript(workspaceDir.absolutePath, code)
            } else {
                "Error: El motor nativo de Lua 5.4 no está enlazado en este dispositivo."
            }
        } catch (e: Exception) {
            "Error al ejecutar Lua: ${e.localizedMessage}"
        }
    }

    /**
     * Ejecutar un archivo .lua existente del sandbox.
     */
    fun toolRunLuaFile(relativePath: String): String {
        val sanitized = sanitizeRelativePath(relativePath)
        val file = File(workspaceDir, sanitized)
        if (!file.exists()) {
            return "Error: El archivo Lua no existe en el sandbox: $sanitized"
        }
        return try {
            val code = file.readText(Charsets.UTF_8)
            toolExecuteLuaScript(code)
        } catch (e: Exception) {
            "Error al leer archivo Lua: ${e.localizedMessage}"
        }
    }

    /**
     * Herramienta 5: Eliminar cualquier archivo del sandbox.
     */
    fun toolDeleteFile(relativePath: String): Result<String> {
        val sanitized = sanitizeRelativePath(relativePath)
        val targetFile = File(workspaceDir, sanitized)

        if (!targetFile.exists()) {
            return Result.failure(
                IllegalArgumentException("El archivo a eliminar no existe en el sandbox: $sanitized")
            )
        }
        if (targetFile.isDirectory) {
            return Result.failure(
                IllegalArgumentException("La ruta es un directorio, solo se permite eliminar archivos individuales: $sanitized")
            )
        }

        return try {
            val name = targetFile.name
            val deleted = targetFile.delete()
            if (deleted) {
                Result.success("Archivo eliminado con éxito del sandbox: $sanitized ($name)")
            } else {
                Result.failure(Exception("No se pudo eliminar el archivo $sanitized del almacenamiento."))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Error al eliminar archivo: ${e.localizedMessage}"))
        }
    }

    /**
     * Sanitiza la ruta relativa para evitar path traversal.
     */
    private fun sanitizeRelativePath(path: String): String {
        return path.replace("\\", "/")
            .trimStart('/')
            .split("/")
            .filter { it.isNotEmpty() && it != "." && it != ".." }
            .joinToString("/")
    }

    companion object {
        fun isTextOrCodeFile(fileName: String): Boolean {
            val ext = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
            return ext in setOf(
                "txt", "md", "json", "xml", "kt", "kts", "java", "py", "js", "ts", "html",
                "css", "csv", "yaml", "yml", "gradle", "properties", "sh", "sql", "c", "cpp",
                "h", "rs", "go", "php", "rb", "dart", "log", "env", "conf", "ini"
            )
        }

        fun formatBytes(bytes: Long): String {
            if (bytes <= 0) return "0 B"
            val units = arrayOf("B", "KB", "MB", "GB")
            val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
            val value = bytes / Math.pow(1024.0, digitGroups.toDouble())
            return String.format(Locale.getDefault(), "%.1f %s", value, units[digitGroups])
        }
    }
}
