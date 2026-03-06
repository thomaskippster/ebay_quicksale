package com.example.ebayquicksale

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    private lateinit var settingsManager: SettingsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsManager = SettingsManager(this)
        setContent {
            QuiksaleApp(settingsManager)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuiksaleApp(settingsManager: SettingsManager) {
    val navController = rememberNavController()
    val viewModel: QuiksaleViewModel = viewModel()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Quiksale") },
                actions = {
                    IconButton(onClick = { navController.navigate("main") }) {
                        Icon(Icons.Default.Home, contentDescription = "Home")
                    }
                    IconButton(onClick = { navController.navigate("settings") }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.primary,
                )
            )
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "main",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("main") { MainScreen(viewModel, settingsManager) }
            composable("settings") { SettingsScreen(settingsManager) }
        }
    }
}

@Composable
fun MainScreen(viewModel: QuiksaleViewModel, settingsManager: SettingsManager) {
    var notes by remember { mutableStateOf("") }
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var photoUri by remember { mutableStateOf<Uri?>(null) }
    val context = LocalContext.current
    
    val geminiApiKey by settingsManager.geminiApiKey.collectAsState(initial = "")
    val uiState by viewModel.uiState.collectAsState()

    // Kamera-Launcher für das hochauflösende Bild
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && photoUri != null) {
            // Bitmap aus der Uri laden für Vorschau und ViewModel
            try {
                val inputStream = context.contentResolver.openInputStream(photoUri!!)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                capturedBitmap = bitmap
            } catch (e: Exception) {
                Toast.makeText(context, "Fehler beim Laden des Bildes", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Permission-Launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val uri = createImageUri(context)
            photoUri = uri
            cameraLauncher.launch(uri)
        } else {
            Toast.makeText(context, "Kamera-Berechtigung verweigert", Toast.LENGTH_SHORT).show()
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Button(
            onClick = { 
                when (PackageManager.PERMISSION_GRANTED) {
                    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) -> {
                        val uri = createImageUri(context)
                        photoUri = uri
                        cameraLauncher.launch(uri)
                    }
                    else -> {
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text("Foto aufnehmen")
        }

        capturedBitmap?.let { bitmap ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Vorschau des aufgenommenen Fotos",
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        
        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text("Mängel & Notizen") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3
        )

        Button(
            onClick = { 
                capturedBitmap?.let { 
                    viewModel.generateDraft(it, notes, geminiApiKey)
                }
            },
            enabled = capturedBitmap != null && geminiApiKey.isNotBlank() && uiState !is QuiksaleUiState.Loading,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text("Entwurf generieren")
        }

        // Status Anzeige
        when (uiState) {
            is QuiksaleUiState.Loading -> {
                CircularProgressIndicator()
            }
            is QuiksaleUiState.Success -> {
                val successState = uiState as QuiksaleUiState.Success
                
                Text("Vorschlag für eBay Titel:", style = MaterialTheme.typography.titleMedium)
                SelectionContainer {
                    Text(
                        text = successState.title,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                    )
                }

                Text("Generierte Beschreibung (HTML):", style = MaterialTheme.typography.titleMedium)
                SelectionContainer {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text(
                            text = successState.htmlContent,
                            modifier = Modifier
                                .padding(8.dp)
                                .verticalScroll(rememberScrollState()),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp
                        )
                    }
                }
            }
            is QuiksaleUiState.Error -> {
                Text(
                    text = (uiState as QuiksaleUiState.Error).message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            else -> {}
        }
    }
}

private fun createImageUri(context: Context): Uri {
    val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
    val file = File.createTempFile("JPEG_${timeStamp}_", ".jpg", storageDir)
    
    return FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )
}

@Composable
fun SettingsScreen(settingsManager: SettingsManager) {
    val coroutineScope = rememberCoroutineScope()
    
    val geminiApiKey by settingsManager.geminiApiKey.collectAsState(initial = "")
    val ebayStartPrice by settingsManager.ebayStartPrice.collectAsState(initial = "1.00")
    val ebayStartTime by settingsManager.ebayStartTime.collectAsState(initial = "")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Einstellungen", style = MaterialTheme.typography.headlineMedium)

        OutlinedTextField(
            value = geminiApiKey,
            onValueChange = { coroutineScope.launch { settingsManager.saveGeminiApiKey(it) } },
            label = { Text("Gemini API Key") },
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
        )

        OutlinedTextField(
            value = ebayStartPrice,
            onValueChange = { coroutineScope.launch { settingsManager.saveEbayStartPrice(it) } },
            label = { Text("Standard-Startpreis (€)") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )

        OutlinedTextField(
            value = ebayStartTime,
            onValueChange = { coroutineScope.launch { settingsManager.saveEbayStartTime(it) } },
            label = { Text("Standard-Startzeit") },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("z.B. Donnerstag, 18:00 Uhr") }
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Daten werden automatisch beim Tippen gespeichert.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary
        )
    }
}
