package com.example.ui.inventory

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.PosViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryImportExportDialog(
    viewModel: PosViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(0) } // 0 = Exportar, 1 = Importar
    var exportFormat by remember { mutableStateOf("CSV") } // "CSV" o "JSON"
    var jsonInputText by remember { mutableStateOf("") }
    var replaceAllMode by remember { mutableStateOf(false) }
    var isImporting by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var showCsvImportDialog by remember { mutableStateOf(false) }

    val products by viewModel.allProducts.collectAsStateWithLifecycle()

    val exportData = remember(exportFormat, products) {
        if (exportFormat == "CSV") {
            viewModel.exportProductsToCsv()
        } else {
            viewModel.exportProductsToJson()
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 640.dp)
                .fillMaxHeight(0.9f)
                .padding(vertical = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp)
            ) {
                // Header with Close 'X' button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Filled.ImportExport,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "Exportar e Importar Inventario",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "Cerrar", modifier = Modifier.size(20.dp))
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Navigation Tabs
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    indicator = {},
                    divider = {},
                    modifier = Modifier.clip(RoundedCornerShape(12.dp))
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Filled.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text("Exportar (${products.size})", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Filled.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text("Cargar / Importar", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (selectedTab == 0) {
                    // --- EXPORT TAB ---
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            "Selecciona el formato de exportación:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = exportFormat == "CSV",
                                onClick = { exportFormat = "CSV" },
                                label = { Text("📊 CSV (Para Excel y Hojas de Cálculo)", fontWeight = FontWeight.SemiBold) },
                                leadingIcon = {
                                    if (exportFormat == "CSV") Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = exportFormat == "JSON",
                                onClick = { exportFormat = "JSON" },
                                label = { Text("⚙️ JSON (Para Migración y Apps)", fontWeight = FontWeight.SemiBold) },
                                leadingIcon = {
                                    if (exportFormat == "JSON") Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // Preview box
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 180.dp, max = 260.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(10.dp)
                                    .horizontalScroll(rememberScrollState())
                                    .verticalScroll(rememberScrollState())
                            ) {
                                Text(
                                    text = exportData.ifBlank { "No hay productos en inventario para exportar." },
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        // Export Actions
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("Inventario_${exportFormat}", exportData)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, "¡Inventario $exportFormat copiado al portapapeles!", Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Copiar Texto")
                            }

                            Button(
                                onClick = {
                                    val sendIntent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        putExtra(Intent.EXTRA_TEXT, exportData)
                                        type = if (exportFormat == "CSV") "text/csv" else "application/json"
                                    }
                                    val shareIntent = Intent.createChooser(sendIntent, "Compartir Inventario ($exportFormat)")
                                    context.startActivity(shareIntent)
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Compartir / Enviar")
                            }
                        }
                    }
                } else {
                    // --- IMPORT TAB ---
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Banner de importación CSV directa
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF059669).copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, Color(0xFF059669).copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Filled.UploadFile, contentDescription = null, tint = Color(0xFF059669))
                                    Column {
                                        Text("¿Deseas importar desde un archivo .CSV?", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF059669))
                                        Text("Carga tu archivo con validación de columnas y cálculo de márgenes", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Button(
                                    onClick = { showCsvImportDialog = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Cargar CSV", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Text(
                            "O pega aquí la estructura de datos en formato JSON para actualizar tu inventario:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = clipboard.primaryClip
                                    if (clip != null && clip.itemCount > 0) {
                                        val pasteText = clip.getItemAt(0).text?.toString() ?: ""
                                        if (pasteText.isNotBlank()) {
                                            jsonInputText = pasteText
                                            Toast.makeText(context, "Texto pegado desde el portapapeles", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            ) {
                                Icon(Icons.Filled.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Pegar del Portapapeles", fontSize = 12.sp)
                            }

                            if (jsonInputText.isNotBlank()) {
                                TextButton(onClick = { jsonInputText = "" }) {
                                    Text("Limpiar", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }

                        OutlinedTextField(
                            value = jsonInputText,
                            onValueChange = { jsonInputText = it },
                            placeholder = {
                                Text(
                                    "[\n  {\n    \"code\": \"PROD01\",\n    \"name\": \"Harina PAN\",\n    \"category\": \"Víveres\",\n    \"priceUsd\": 1.25,\n    \"stock\": 50\n  }\n]",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp
                                )
                            },
                            textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 160.dp, max = 220.dp)
                        )

                        // Mode selection
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Modo de Importación:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    RadioButton(
                                        selected = !replaceAllMode,
                                        onClick = { replaceAllMode = false }
                                    )
                                    Column(modifier = Modifier.padding(start = 4.dp)) {
                                        Text("Combinar y Actualizar (Recomendado)", fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp)
                                        Text("Actualiza productos existentes por código y añade los nuevos.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    RadioButton(
                                        selected = replaceAllMode,
                                        onClick = { replaceAllMode = true }
                                    )
                                    Column(modifier = Modifier.padding(start = 4.dp)) {
                                        Text("Reemplazar Inventario Completo", fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, color = MaterialTheme.colorScheme.error)
                                        Text("Borra los productos actuales y carga únicamente los del JSON.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }

                        if (statusMessage != null) {
                            Text(
                                text = statusMessage!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (statusMessage!!.startsWith("Se importaron")) Color(0xFF059669) else MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Button(
                            onClick = {
                                if (jsonInputText.isBlank()) {
                                    statusMessage = "Por favor ingresa o pega el texto JSON con los productos."
                                    return@Button
                                }
                                isImporting = true
                                viewModel.importProductsFromJson(
                                    jsonString = jsonInputText,
                                    replaceAll = replaceAllMode
                                ) { success, count, msg ->
                                    isImporting = false
                                    statusMessage = msg
                                    if (success) {
                                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            enabled = !isImporting && jsonInputText.isNotBlank(),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isImporting) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Procesando y Sincronizando...")
                            } else {
                                Icon(Icons.Filled.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Cargar Inventario y Sincronizar en la Nube")
                            }
                        }
                    }
                }
            }
        }

        if (showCsvImportDialog) {
            CsvInventoryImportDialog(
                viewModel = viewModel,
                onDismiss = { showCsvImportDialog = false },
                onImportCompleted = { msg ->
                    statusMessage = msg
                }
            )
        }
    }
}
