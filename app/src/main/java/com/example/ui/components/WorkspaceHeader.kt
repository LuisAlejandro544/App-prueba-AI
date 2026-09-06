package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun WorkspaceHeader(
    hasApiKey: Boolean,
    onApiKeyClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showInfoDialog by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = "Folder AI",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Sandbox Aislado Seguro",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = { showInfoDialog = true },
                modifier = Modifier.testTag("info_help_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Información del Sandbox",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(
                onClick = onApiKeyClick,
                modifier = Modifier.testTag("api_key_button")
            ) {
                Box {
                    Icon(
                        imageVector = Icons.Default.Key,
                        contentDescription = "Configurar API Key",
                        tint = if (hasApiKey) Color(0xFF10B981) else MaterialTheme.colorScheme.error
                    )
                    // Pequeño indicador de punto verde o naranja
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (hasApiKey) Color(0xFF10B981) else Color(0xFFF59E0B))
                            .align(Alignment.TopEnd)
                    )
                }
            }
        }
    }

    if (showInfoDialog) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            icon = {
                Icon(
                    Icons.Default.Shield,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = {
                Text("¿Por qué un Sandbox?")
            },
            text = {
                Text(
                    "Cuando conectas una carpeta, la app realiza una copia íntegra hacia su almacenamiento privado protegido.\n\n" +
                            "Esto crea una muralla digital: la Inteligencia Artificial lee y opera sobre los archivos clonados, impidiendo que modifique, corrompa o borre por accidente tus archivos personales originales del teléfono.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = { showInfoDialog = false },
                    modifier = Modifier.testTag("dismiss_info_dialog_button")
                ) {
                    Text("Entendido")
                }
            }
        )
    }
}
