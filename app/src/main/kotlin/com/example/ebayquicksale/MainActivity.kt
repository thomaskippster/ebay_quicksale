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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.layout.ContentScale
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
    private lateinit var ebayAuthManager: EbayAuthManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsManager = SettingsManager(this)
        ebayAuthManager = EbayAuthManager(this, settingsManager)
        
        setContent {
            QuiksaleApp(settingsManager, ebayAuthManager)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuiksaleApp(settingsManager: SettingsManager, ebayAuthManager: EbayAuthManager) {
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
            composable("main") { MainScreen(viewModel, settingsManager, ebayAuthManager) }
            composable("settings") { SettingsScreen(settingsManager, ebayAuthManager) }
        }
    }
}

@Composable
fun MainScreen(viewModel: QuiksaleViewModel, settingsManager: SettingsManager, ebayAuthManager: EbayAuthManager) {
    val notes by viewModel.notes.collectAsState()
    val capturedBitmaps by viewModel.bitmaps.collectAsState()
    var photoUri by remember { mutableStateOf<Uri?>(null) }
    val context = LocalContext.current
    
    val geminiApiKey by settingsManager.geminiApiKey.collectAsState(initial = "")
    val ebayAccessToken by settingsManager.ebayAccessToken.collectAsState(initial = null)
    val ebayClientId by settingsManager.ebayClientId.collectAsState(initial = "")
    val ebayClientSecret by settingsManager.ebayClientSecret.collectAsState(initial = "")
    val ebayStartPrice by settingsManager.ebayStartPrice.collectAsState(initial = "1.00")
    val imgurClientId by settingsManager.imgurClientId.collectAsState(initial = "")
    
    val merchantLocation by settingsManager.ebayMerchantLocation.collectAsState(initial = "")
    val paymentPolicy by settingsManager.ebayPaymentPolicy.collectAsState(initial = "")
    val fulfillmentPolicy by settingsManager.ebayFulfillmentPolicy.collectAsState(initial = "")
    val returnPolicy by settingsManager.ebayReturnPolicy.collectAsState(initial = "")

    val uiState by viewModel.uiState.collectAsState()
    val uploadState by viewModel.uploadState.collectAsState()

    // Kamera-Launcher für das hochauflösende Bild
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && photoUri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(photoUri!!)
                val fullBitmap = BitmapFactory.decodeStream(inputStream)
                if (fullBitmap != null) {
                    val resizedBitmap = ImageUtils.resizeBitmap(fullBitmap)
                    viewModel.addBitmap(resizedBitmap)
                }
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
        if (uiState !is QuiksaleUiState.Success) {
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
                Text(if (capturedBitmaps.isEmpty()) "Foto aufnehmen" else "Weiteres Foto aufnehmen")
            }
        }

        if (capturedBitmaps.isNotEmpty()) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) {
                items(capturedBitmaps) { bitmap ->
                    Card(
                        modifier = Modifier
                            .width(120.dp)
                            .fillMaxHeight(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Aufgenommenes Foto",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }
        }
        
        OutlinedTextField(
            value = notes,
            onValueChange = { viewModel.updateNotes(it) },
            label = { Text("Mängel & Notizen") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            enabled = uiState !is QuiksaleUiState.Success
        )

        if (uiState !is QuiksaleUiState.Success) {
            Button(
                onClick = { 
                    viewModel.generateDraft(geminiApiKey, ebayAccessToken)
                },
                enabled = capturedBitmaps.isNotEmpty() && geminiApiKey.isNotBlank() && uiState !is QuiksaleUiState.Loading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                if (uiState is QuiksaleUiState.Loading) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(24.dp))
                } else {
                    Text("Entwurf generieren (${capturedBitmaps.size} Bilder)")
                }
            }
        }

        // Status Anzeige
        when (uiState) {
            is QuiksaleUiState.Success -> {
                val draft = (uiState as QuiksaleUiState.Success).draft
                
                Text(
                    text = "Zustand: ${draft.condition}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary
                )

                Text("Vorschlag für eBay Titel:", style = MaterialTheme.typography.titleMedium)
                SelectionContainer {
                    Text(
                        text = draft.title,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        ),
                        modifier = Modifier.fillMaxWidth().padding(4.dp)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Empfohlener Preis:", style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = if (draft.suggestedPrice.contains("€")) draft.suggestedPrice else "${draft.suggestedPrice} €",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Kategorie ID:", style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = if (draft.categoryId.isNotBlank()) draft.categoryId else draft.categoryKeywords,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
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
                            text = draft.descriptionHtml,
                            modifier = Modifier
                                .padding(8.dp)
                                .verticalScroll(rememberScrollState()),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (uploadState !is UploadUiState.Success) {
                    Button(
                        onClick = { 
                            ebayAuthManager.getValidAccessToken(ebayClientId, ebayClientSecret) { validToken ->
                                if (validToken != null) {
                                    viewModel.uploadToEbay(
                                        draft = draft,
                                        bitmaps = capturedBitmaps,
                                        token = validToken,
                                        imgurId = imgurClientId,
                                        defaultPrice = ebayStartPrice,
                                        merchantLocation = merchantLocation,
                                        paymentId = paymentPolicy,
                                        fulfillmentId = fulfillmentPolicy,
                                        returnId = returnPolicy
                                    )
                                } else {
                                    Toast.makeText(context, "Fehler: Kein gültiger eBay-Token. Bitte neu einloggen.", Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        enabled = ebayAccessToken != null && imgurClientId.isNotBlank() && uploadState !is UploadUiState.Loading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiary
                        )
                    ) {
                        if (uploadState is UploadUiState.Loading) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.onTertiary,
                                modifier = Modifier.size(24.dp)
                            )
                        } else {
                            Text("Als Entwurf zu eBay hochladen")
                        }
                    }
                }

                when (uploadState) {
                    is UploadUiState.Success -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                "Erfolgreich als Entwurf hochgeladen! ✅",
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                ),
                                color = androidx.compose.ui.graphics.Color(0xFF2E7D32),
                                modifier = Modifier.padding(top = 8.dp)
                            )
                            
                            Button(
                                onClick = {
                                    viewModel.resetAll()
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                            ) {
                                Text("Nächsten Artikel scannen")
                            }
                        }
                    }
                    is UploadUiState.Error -> {
                        Text(
                            (uploadState as UploadUiState.Error).message,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    else -> {}
                }

                if (ebayAccessToken == null) {
                    Text(
                        "Bitte verbinde dich zuerst in den Einstellungen mit eBay.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                } else if (imgurClientId.isBlank()) {
                    Text(
                        "Bitte trage deine Imgur Client ID in den Einstellungen ein.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
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
    val storageDir = context.cacheDir
    val file = File.createTempFile("JPEG_${timeStamp}_", ".jpg", storageDir)
    
    return FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )
}

@Composable
fun SettingsScreen(settingsManager: SettingsManager, ebayAuthManager: EbayAuthManager) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    
    // Collect all settings as state
    val geminiApiKey by settingsManager.geminiApiKey.collectAsState(initial = "")
    val ebayStartPrice by settingsManager.ebayStartPrice.collectAsState(initial = "1.00")
    val ebayStartTime by settingsManager.ebayStartTime.collectAsState(initial = "")
    val ebayAccessToken by settingsManager.ebayAccessToken.collectAsState(initial = null)
    val ebayClientId by settingsManager.ebayClientId.collectAsState(initial = "")
    val ebayClientSecret by settingsManager.ebayClientSecret.collectAsState(initial = "")
    val merchantLocation by settingsManager.ebayMerchantLocation.collectAsState(initial = "")
    val paymentPolicy by settingsManager.ebayPaymentPolicy.collectAsState(initial = "")
    val fulfillmentPolicy by settingsManager.ebayFulfillmentPolicy.collectAsState(initial = "")
    val returnPolicy by settingsManager.ebayReturnPolicy.collectAsState(initial = "")
    val imgurClientId by settingsManager.imgurClientId.collectAsState(initial = "")

    val authLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        if (data != null) {
            ebayAuthManager.handleAuthResponse(data, ebayClientSecret) { token, error ->
                if (token != null) {
                    Toast.makeText(context, "Erfolgreich mit eBay verbunden!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Fehler: $error", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Einstellungen", style = MaterialTheme.typography.headlineMedium)

        Text("Gemini & Imgur Konfiguration", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = geminiApiKey,
            onValueChange = { coroutineScope.launch { settingsManager.saveGeminiApiKey(it) } },
            label = { Text("Gemini API Key") },
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
        )

        OutlinedTextField(
            value = imgurClientId,
            onValueChange = { coroutineScope.launch { settingsManager.saveImgurClientId(it) } },
            label = { Text("Imgur Client ID") },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Client-ID von api.imgur.com") }
        )

        HorizontalDivider()

        Text("eBay Developer Credentials", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = ebayClientId,
            onValueChange = { coroutineScope.launch { settingsManager.saveEbayClientId(it) } },
            label = { Text("eBay Client ID (App ID)") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = ebayClientSecret,
            onValueChange = { coroutineScope.launch { settingsManager.saveEbayClientSecret(it) } },
            label = { Text("eBay Client Secret (Cert ID)") },
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
        )

        Button(
            onClick = { authLauncher.launch(ebayAuthManager.createAuthIntent(ebayClientId)) },
            enabled = ebayClientId.isNotBlank() && ebayClientSecret.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Mit eBay verbinden")
        }

        if (ebayAccessToken != null) {
            Text("Status: Mit eBay verbunden ✅", color = MaterialTheme.colorScheme.primary)
        }

        HorizontalDivider()

        Text("eBay Business Policies & Location", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = merchantLocation,
            onValueChange = { coroutineScope.launch { settingsManager.saveEbayMerchantLocation(it) } },
            label = { Text("Merchant Location Key") },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("z.B. Berlin_12345") }
        )

        OutlinedTextField(
            value = paymentPolicy,
            onValueChange = { coroutineScope.launch { settingsManager.saveEbayPaymentPolicy(it) } },
            label = { Text("Payment Policy ID") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = fulfillmentPolicy,
            onValueChange = { coroutineScope.launch { settingsManager.saveEbayFulfillmentPolicy(it) } },
            label = { Text("Fulfillment Policy ID") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = returnPolicy,
            onValueChange = { coroutineScope.launch { settingsManager.saveEbayReturnPolicy(it) } },
            label = { Text("Return Policy ID") },
            modifier = Modifier.fillMaxWidth()
        )

        HorizontalDivider()

        Text("eBay Standard-Angebotsdaten", style = MaterialTheme.typography.titleMedium)
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
