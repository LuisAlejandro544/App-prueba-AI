package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.BuildConfig
import com.example.model.CloneStatus
import com.example.ui.components.ApiKeyDialog
import com.example.ui.components.FileViewerDialog
import com.example.ui.components.WorkspaceFolderCard
import com.example.ui.components.WorkspaceHeader
import com.example.ui.components.AiChatSection
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.SandboxViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: SandboxViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                MainScreen(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun MainScreen(viewModel: SandboxViewModel) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val workspace by viewModel.workspace.collectAsStateWithLifecycle()
    val cloneStatus by viewModel.cloneStatus.collectAsStateWithLifecycle()
    val chatMessages by viewModel.chatMessages.collectAsStateWithLifecycle()
    val isAiThinking by viewModel.isAiThinking.collectAsStateWithLifecycle()
    val customApiKey by viewModel.customApiKey.collectAsStateWithLifecycle()
    val aiProvider by viewModel.aiProvider.collectAsStateWithLifecycle()
    val openRouterApiKey by viewModel.openRouterApiKey.collectAsStateWithLifecycle()
    val openRouterModel by viewModel.openRouterModel.collectAsStateWithLifecycle()
    val showApiKeyDialog by viewModel.showApiKeyDialog.collectAsStateWithLifecycle()
    val selectedFileForPreview by viewModel.selectedFileForPreview.collectAsStateWithLifecycle()

    // SAF Document Tree Launcher
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            try {
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, takeFlags)
            } catch (_: Exception) {
                // Algunos proveedores no soportan persistencia, pero el stream sigue disponible durante el ciclo de vida
            }
            viewModel.cloneSelectedFolder(uri)
        }
    }

    LaunchedEffect(cloneStatus) {
        when (val status = cloneStatus) {
            is CloneStatus.Error -> snackbarHostState.showSnackbar("Error al clonar carpeta: ${status.message}")
            is CloneStatus.Success -> snackbarHostState.showSnackbar("Clonados ${status.count} archivos en el sandbox aislado")
            else -> {}
        }
    }

    val hasEffectiveApiKey = if (aiProvider == "openrouter") {
        openRouterApiKey.isNotBlank()
    } else {
        customApiKey.isNotBlank() || try {
            BuildConfig.GEMINI_API_KEY.isNotBlank() && BuildConfig.GEMINI_API_KEY != "MY_GEMINI_API_KEY"
        } catch (_: Throwable) {
            false
        }
    }

    val providerLabel = if (aiProvider == "openrouter") {
        com.example.ai.OpenRouterModels.getShortLabel(openRouterModel)
    } else {
        "Gemini"
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            // Cabecera con estado de aislamiento y botón de API Key
            WorkspaceHeader(
                hasApiKey = hasEffectiveApiKey,
                providerLabel = providerLabel,
                onApiKeyClick = { viewModel.setApiKeyDialogVisible(true) }
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Tarjeta de clonación de carpeta y estado del Sandbox
            WorkspaceFolderCard(
                workspace = workspace,
                cloneStatus = cloneStatus,
                onPickFolderClick = { folderPickerLauncher.launch(null) },
                onClearClick = { viewModel.clearSandbox() },
                onFileClick = { file -> viewModel.inspectFile(file) }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Sección de Chat y Análisis con IA
            AiChatSection(
                messages = chatMessages,
                isAiThinking = isAiThinking,
                onSendMessage = { prompt -> viewModel.askAi(prompt) },
                modifier = Modifier.weight(1f)
            )
        }

        // Diálogo para configurar proveedor de IA, modelos OpenRouter y claves
        if (showApiKeyDialog) {
            ApiKeyDialog(
                currentProvider = aiProvider,
                currentOpenRouterKey = openRouterApiKey,
                currentOpenRouterModel = openRouterModel,
                currentGeminiKey = customApiKey,
                onDismiss = { viewModel.setApiKeyDialogVisible(false) },
                onSave = { provider, orKey, orModel, geminiKey ->
                    viewModel.saveAiSettings(provider, orKey, orModel, geminiKey)
                }
            )
        }

        // Visor de archivo para inspeccionar su contenido en el sandbox
        selectedFileForPreview?.let { (file, content) ->
            FileViewerDialog(
                file = file,
                content = content,
                onDismiss = { viewModel.dismissFilePreview() }
            )
        }
    }
}
