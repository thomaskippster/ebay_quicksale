@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ebayquicksale

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ebayquicksale.api.CategoryInfo
import com.example.ebayquicksale.api.EbayRetrofitClient
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
            QuicksaleApp(settingsManager, ebayAuthManager)
        }
    }
}

@Composable
fun QuicksaleApp(settingsManager: SettingsManager, ebayAuthManager: EbayAuthManager) {
    val navController = rememberNavController()
    val viewModel: QuicksaleViewModel = viewModel()
    val coroutineScope = rememberCoroutineScope()
    
    val disclaimerAccepted by settingsManager.disclaimerAccepted.collectAsState(initial = true)
    var showDisclaimer by remember { mutableStateOf(false) }

    LaunchedEffect(disclaimerAccepted) {
        if (!disclaimerAccepted) {
            showDisclaimer = true
        }
    }

    if (showDisclaimer) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Haftungsausschluss") },
            text = { Text("Diese App ist ein Werkzeug zur Erleichterung von eBay-Verkäufen. Der Entwickler übernimmt keine Haftung für die Richtigkeit der von der KI generierten Texte, die Einhaltung von eBay-Richtlinien oder steuerliche Pflichten des Nutzers. Die Nutzung erfolgt auf eigene Gefahr.\n\nGemäß § 19 UStG wird keine Umsatzsteuer berechnet.") },
            confirmButton = {
                Button(onClick = {
                    coroutineScope.launch {
                        settingsManager.saveDisclaimerAccepted(true)
                        showDisclaimer = false
                    }
                }) { Text("Ich akzeptiere") }
            }
        )
    }

    LaunchedEffect(Unit) {
        viewModel.loadDraftFromStorage(settingsManager)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Quicksale - KI eBay Assistent") },
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
            composable("settings") { SettingsScreen(settingsManager, ebayAuthManager, viewModel) }
        }
    }
}

@Composable
fun MainScreen(viewModel: QuicksaleViewModel, settingsManager: SettingsManager) {
    val notes by viewModel.notes.collectAsState()
    val imagePaths by viewModel.imagePaths.collectAsState()
    var photoUri by remember { mutableStateOf<Uri?>(null) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    
    val geminiApiKey by settingsManager.geminiApiKey.collectAsState(initial = "")
    val ebayAccessToken by settingsManager.ebayAccessToken.collectAsState(initial = null)
    val defaultListingFormat by settingsManager.ebayListingFormat.collectAsState(initial = "AUCTION")
    
    val uiState by viewModel.uiState.collectAsState()
    val uploadState by viewModel.uploadState.collectAsState()

    val ebayMarketplaceId by settingsManager.ebayMarketplaceId.collectAsState(initial = "EBAY_DE")
    
    val kiDisclaimerAccepted by settingsManager.kiDisclaimerAccepted.collectAsState(initial = true)
    var showKiDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uploadState) {
        if (uploadState is UploadUiState.Error) {
            scrollState.animateScrollTo(0)
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && photoUri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(photoUri!!)
                val fullBitmap = BitmapFactory.decodeStream(inputStream)
                if (fullBitmap != null) {
                    val resizedBitmap = ImageUtils.resizeBitmap(fullBitmap)
                    viewModel.addImage(context, resizedBitmap)
                    Toast.makeText(context, "Bild erfolgreich hinzugefügt", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Fehler beim Laden des Bildes", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            if (!kiDisclaimerAccepted) {
                showKiDialog = true
            } else {
                val uri = createImageUri(context)
                photoUri = uri
                cameraLauncher.launch(uri)
            }
        }
    }

    if (showKiDialog) {
        AlertDialog(
            onDismissRequest = { showKiDialog = false },
            title = { Text("DSGVO-Hinweis zur KI") },
            text = { Text("Datenschutz-Hinweis: Ihre Fotos werden zur Analyse an Google Gemini übertragen. Mit der Nutzung erklären Sie sich damit einverstanden.") },
            confirmButton = {
                Button(onClick = {
                    coroutineScope.launch {
                        settingsManager.saveKiDisclaimerAccepted(true)
                        showKiDialog = false
                        val uri = createImageUri(context)
                        photoUri = uri
                        cameraLauncher.launch(uri)
                    }
                }) { Text("Einverstanden") }
            },
            dismissButton = {
                TextButton(onClick = { showKiDialog = false }) { Text("Abbrechen") }
            }
        )
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty() && !kiDisclaimerAccepted) {
            showKiDialog = true
        } else {
            uris.forEach { uri ->
                try {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    if (bitmap != null) {
                        val resized = ImageUtils.resizeBitmap(bitmap)
                        viewModel.addImage(context, resized)
                        Toast.makeText(context, "Bild erfolgreich hinzugefügt", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {}
            }
        }
    }
    
    val statFs = android.os.StatFs(Environment.getDataDirectory().path)
    val availableMB = statFs.availableBlocksLong * statFs.blockSizeLong / (1024 * 1024)
    val isStorageCritical = availableMB < 100

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (isStorageCritical) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer), modifier = Modifier.fillMaxWidth()) {
                Text(text = "⚠️ Speicherplatz kritisch ($availableMB MB). Bitte Platz schaffen.", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(12.dp), fontWeight = FontWeight.Bold)
            }
        }

        if (uiState !is QuicksaleUiState.Success) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { 
                        when (PackageManager.PERMISSION_GRANTED) {
                            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) -> {
                                if (!kiDisclaimerAccepted) {
                                    showKiDialog = true
                                } else {
                                    val uri = createImageUri(context)
                                    photoUri = uri
                                    cameraLauncher.launch(uri)
                                }
                            }
                            else -> {
                                permissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        }
                    },
                    modifier = Modifier.weight(1f).height(56.dp)
                ) {
                    Text(if (imagePaths.isEmpty()) "Foto" else "Weiteres")
                }
                
                Button(
                    onClick = { galleryLauncher.launch("image/*") },
                    modifier = Modifier.weight(1f).height(56.dp)
                ) {
                    Text("Galerie")
                }
            }
        }

        if (imagePaths.isNotEmpty()) {
            LazyRow(
                modifier = Modifier.fillMaxWidth().height(140.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(imagePaths) { path ->
                    val bitmap = remember(path) { BitmapFactory.decodeFile(path) }
                    if (bitmap != null) {
                        Card(
                            modifier = Modifier.width(120.dp).fillMaxHeight(),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Box {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                                Column(
                                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.Black.copy(alpha = 0.5f))
                                ) {
                                    TextButton(
                                        onClick = { viewModel.moveImageToFront(path) },
                                        modifier = Modifier.fillMaxWidth().height(36.dp)
                                    ) {
                                        Text("Galeriebild", color = Color.White, fontSize = 10.sp)
                                    }
                                }
                                IconButton(
                                    onClick = { viewModel.removeImage(path) },
                                    modifier = Modifier.align(Alignment.TopEnd).size(32.dp).padding(4.dp)
                                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f), shape = androidx.compose.foundation.shape.CircleShape)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
        
        SafeTextField(
            value = notes,
            onValueChange = { viewModel.updateNotes(it) },
            label = "Mängel & Notizen",
            modifier = Modifier.fillMaxWidth(),
            singleLine = false
        )

        if (uiState !is QuicksaleUiState.Success) {
            Button(
                onClick = { viewModel.generateDraft(geminiApiKey, ebayAccessToken, defaultListingFormat, ebayMarketplaceId, settingsManager) },
                enabled = imagePaths.isNotEmpty() && geminiApiKey.isNotBlank() && uiState !is QuicksaleUiState.Loading && !isStorageCritical,
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                if (uiState is QuicksaleUiState.Loading) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(24.dp))
                } else {
                    Text("Angebot generieren (Gemini)")
                }
            }
        }

        when (uiState) {
            is QuicksaleUiState.Success -> {
                val draft = (uiState as QuicksaleUiState.Success).draft
                DraftDisplay(draft, viewModel, ebayAccessToken ?: "", ebayMarketplaceId, settingsManager, context)
            }
            is QuicksaleUiState.Loading -> { }
            is QuicksaleUiState.Error -> {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer), modifier = Modifier.fillMaxWidth()) {
                    Text(text = (uiState as QuicksaleUiState.Error).message, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))
                }
            }
            else -> {}
        }

        when (uploadState) {
            is UploadUiState.Success -> {
                val listingId = (uploadState as UploadUiState.Success).listingId
                val url = "https://www.ebay.de/itm/$listingId"
                val uriHandler = LocalUriHandler.current
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Erfolgreich gelistet!", fontWeight = FontWeight.Bold)
                        Button(onClick = { uriHandler.openUri(url) }) { Text("Auf eBay ansehen") }
                        Button(onClick = { viewModel.resetAll(settingsManager, context) }) { Text("Nächster Artikel") }
                    }
                }
            }
            is UploadUiState.Loading -> {
                val state = uploadState as UploadUiState.Loading
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
                    Text(state.message, style = MaterialTheme.typography.bodySmall)
                }
            }
            is UploadUiState.Error -> {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer), modifier = Modifier.padding(top = 8.dp).fillMaxWidth()) {
                    Text(text = (uploadState as UploadUiState.Error).message, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))
                }
            }
            else -> {}
        }

        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = "Quicksale ist kein offizielles Produkt von eBay Inc. Alle Marken gehören ihren jeweiligen Eigentümern.\nGemäß § 19 UStG wird keine Umsatzsteuer berechnet.",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray,
            modifier = Modifier.padding(top = 16.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

private fun createImageUri(context: Context): Uri {
    val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    val file = File.createTempFile("JPEG_${timeStamp}_", ".jpg", context.cacheDir)
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

@Composable
fun SettingsScreen(settingsManager: SettingsManager, ebayAuthManager: EbayAuthManager, viewModel: QuicksaleViewModel) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    
    val geminiApiKey by settingsManager.geminiApiKey.collectAsState(initial = "")
    val ebayStartPrice by settingsManager.ebayStartPrice.collectAsState(initial = "1.00")
    val ebayStartTime by settingsManager.ebayStartTime.collectAsState(initial = "")
    val ebayAccessToken by settingsManager.ebayAccessToken.collectAsState(initial = null)
    val ebayClientId by settingsManager.ebayClientId.collectAsState(initial = "")
    val ebayDevId by settingsManager.ebayDevId.collectAsState(initial = "")
    val ebayClientSecret by settingsManager.ebayClientSecret.collectAsState(initial = "")
    val ebayRuName by settingsManager.ebayRuName.collectAsState(initial = "")
    val ebayUseSandbox by settingsManager.ebayUseSandbox.collectAsState(initial = false)

    val merchantLocation by settingsManager.ebayMerchantLocation.collectAsState(initial = "")
    val fulfillmentPolicy by settingsManager.ebayFulfillmentPolicy.collectAsState(initial = "")
    val paymentPolicy by settingsManager.ebayPaymentPolicy.collectAsState(initial = "")
    val returnPolicy by settingsManager.ebayReturnPolicy.collectAsState(initial = "")
    val ebayListingFormat by settingsManager.ebayListingFormat.collectAsState(initial = "AUCTION")
    val isFetchingSettings by viewModel.isFetchingSettings.collectAsState()
    val defaultLegalNotice by settingsManager.defaultLegalNotice.collectAsState(initial = "")
    val ebayMarketplaceId by settingsManager.ebayMarketplaceId.collectAsState(initial = "EBAY_DE")
    val appendLegalNotice by settingsManager.appendLegalNotice.collectAsState(initial = true)

    var showImpressum by remember { mutableStateOf(false) }
    var showPrivacy by remember { mutableStateOf(false) }

    val authLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.let { ebayAuthManager.handleAuthResponse(it, ebayClientSecret, ebayUseSandbox) { _, _ -> } }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Einstellungen", style = MaterialTheme.typography.headlineMedium)
        
        Text("Sicherheitshinweis: Ihre API-Keys und Passwörter werden ausschließlich lokal auf Ihrem Gerät gespeichert. Es findet keine Übertragung an den App-Entwickler statt. Die Bilder werden verschlüsselt an Google Gemini und eBay übertragen.", 
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)

        SafeTextField(value = geminiApiKey, onValueChange = { coroutineScope.launch { settingsManager.saveGeminiApiKey(it) } }, label = "Gemini API Key", modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
        HorizontalDivider()
        Text("eBay Credentials", style = MaterialTheme.typography.titleMedium)
        
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Switch(checked = ebayUseSandbox, onCheckedChange = { coroutineScope.launch { settingsManager.saveEbayUseSandbox(it) } })
            Text("Sandbox Modus (Test-Umgebung)", modifier = Modifier.padding(start = 8.dp))
        }

        SafeTextField(value = ebayClientId, onValueChange = { coroutineScope.launch { settingsManager.saveEbayClientId(it) } }, label = "Client ID (App ID)", modifier = Modifier.fillMaxWidth())
        SafeTextField(value = ebayDevId, onValueChange = { coroutineScope.launch { settingsManager.saveEbayDevId(it) } }, label = "Dev ID (optional)", modifier = Modifier.fillMaxWidth())
        SafeTextField(value = ebayClientSecret, onValueChange = { coroutineScope.launch { settingsManager.saveEbayClientSecret(it) } }, label = "Client Secret (Cert ID)", modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
        SafeTextField(value = ebayRuName, onValueChange = { coroutineScope.launch { settingsManager.saveEbayRuName(it) } }, label = "RuName (Redirect URL name)", modifier = Modifier.fillMaxWidth())
        
        val marketplaces = listOf("EBAY_DE", "EBAY_AT", "EBAY_GB", "EBAY_US")
        var marketExpanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(expanded = marketExpanded, onExpandedChange = { marketExpanded = !marketExpanded }) {
            OutlinedTextField(value = ebayMarketplaceId, onValueChange = {}, label = { Text("Marktplatz") }, readOnly = true, modifier = Modifier.fillMaxWidth().menuAnchor(), trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = marketExpanded) })
            ExposedDropdownMenu(expanded = marketExpanded, onDismissRequest = { marketExpanded = false }) {
                marketplaces.forEach { mp -> DropdownMenuItem(text = { Text(mp) }, onClick = { coroutineScope.launch { settingsManager.saveEbayMarketplaceId(mp) }; marketExpanded = false }) }
            }
        }

        Button(onClick = { authLauncher.launch(ebayAuthManager.createAuthIntent(ebayClientId, ebayRuName, ebayUseSandbox)) }, enabled = ebayClientId.isNotBlank() && ebayClientSecret.isNotBlank() && ebayRuName.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Mit eBay verbinden") }
        Button(onClick = { ebayAccessToken?.let { viewModel.fetchEbaySettings(it, ebayMarketplaceId, settingsManager) { msg -> Toast.makeText(context, msg, Toast.LENGTH_LONG).show() } } }, enabled = ebayAccessToken != null && !isFetchingSettings, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)) {
            if (isFetchingSettings) CircularProgressIndicator(modifier = Modifier.size(24.dp)) else Text("Policies automatisch laden")
        }
        HorizontalDivider()
        
        Text("Rechtliches", style = MaterialTheme.typography.titleMedium)
        ListItem(
            headlineContent = { Text("Impressum") },
            modifier = Modifier.clickable { showImpressum = true }
        )
        ListItem(
            headlineContent = { Text("Datenschutzerklärung") },
            modifier = Modifier.clickable { showPrivacy = true }
        )
        
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Händler-Status (EU-DSA)", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Text("Diese App wird als kommerzielles Produkt angeboten. Der Anbieter ist als Händler registriert.", style = MaterialTheme.typography.bodySmall)
            }
        }

        HorizontalDivider()
        Text("eBay Policies", style = MaterialTheme.typography.titleMedium)
        
        val locations by viewModel.locations.collectAsState()
        var locExpanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(expanded = locExpanded, onExpandedChange = { locExpanded = !locExpanded }) {
            OutlinedTextField(value = merchantLocation, onValueChange = {}, label = { Text("Standort") }, readOnly = true, modifier = Modifier.fillMaxWidth().menuAnchor(), trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = locExpanded) })
            ExposedDropdownMenu(expanded = locExpanded, onDismissRequest = { locExpanded = false }) {
                locations.forEach { loc -> DropdownMenuItem(text = { Text(loc.name) }, onClick = { coroutineScope.launch { settingsManager.saveEbayMerchantLocation(loc.merchantLocationKey) }; locExpanded = false }) }
            }
        }

        val fullPolicies by viewModel.fulfillmentPolicies.collectAsState()
        var fullExpanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(expanded = fullExpanded, onExpandedChange = { fullExpanded = !fullExpanded }) {
            OutlinedTextField(value = fulfillmentPolicy, onValueChange = {}, label = { Text("Versand") }, readOnly = true, modifier = Modifier.fillMaxWidth().menuAnchor(), trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = fullExpanded) })
            ExposedDropdownMenu(expanded = fullExpanded, onDismissRequest = { fullExpanded = false }) {
                fullPolicies.forEach { p -> DropdownMenuItem(text = { Text(p.name) }, onClick = { coroutineScope.launch { settingsManager.saveEbayFulfillmentPolicy(p.policyId) }; fullExpanded = false }) }
            }
        }

        SafeTextField(value = paymentPolicy, onValueChange = { coroutineScope.launch { settingsManager.saveEbayPaymentPolicy(it) } }, label = "Zahlung Policy ID", modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        SafeTextField(value = returnPolicy, onValueChange = { coroutineScope.launch { settingsManager.saveEbayReturnPolicy(it) } }, label = "Rückgabe Policy ID", modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))

        HorizontalDivider()
        Text("Standard-Angebotsdaten", style = MaterialTheme.typography.titleMedium)
        SafeTextField(value = ebayStartPrice, onValueChange = { coroutineScope.launch { settingsManager.saveEbayStartPrice(it) } }, label = "Preis (€)", modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        SafeTextField(value = ebayStartTime, onValueChange = { coroutineScope.launch { settingsManager.saveEbayStartTime(it) } }, label = "Startzeit (SOFORT oder yyyy-MM-dd HH:mm)", modifier = Modifier.fillMaxWidth())
        
        val runtimes = listOf("DAYS_7", "GTC")
        var runtimeExpanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(expanded = runtimeExpanded, onExpandedChange = { runtimeExpanded = !runtimeExpanded }) {
            OutlinedTextField(value = if (ebayListingFormat == "DAYS_7") "7 Tage" else "GTC (Gültig bis auf Widerruf)", onValueChange = {}, label = { Text("Standard-Laufzeit") }, readOnly = true, modifier = Modifier.fillMaxWidth().menuAnchor(), trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = runtimeExpanded) })
            ExposedDropdownMenu(expanded = runtimeExpanded, onDismissRequest = { runtimeExpanded = false }) {
                runtimes.forEach { r -> DropdownMenuItem(text = { Text(if (r == "DAYS_7") "7 Tage" else "GTC") }, onClick = { coroutineScope.launch { settingsManager.saveEbayListingFormat(r) }; runtimeExpanded = false }) }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = appendLegalNotice, onCheckedChange = { coroutineScope.launch { settingsManager.saveAppendLegalNotice(it) } })
            Text("Standard-Rechtstext für Privatverkauf anhängen", modifier = Modifier.padding(start = 8.dp))
        }
        Text("Achtung: Dieser Text dient nur als Vorlage für Privatverkäufe. Prüfen Sie Ihren Verkäuferstatus (privat vs. gewerblich) eigenverantwortlich.", 
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)

        SafeTextField(value = defaultLegalNotice, onValueChange = { coroutineScope.launch { settingsManager.saveDefaultLegalNotice(it) } }, label = "Eigener Rechtstext (optional)", modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp), singleLine = false)

        Button(onClick = { ImageUtils.clearInternalImageStorage(context); Toast.makeText(context, "Cache geleert", Toast.LENGTH_SHORT).show() }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Cache manuell leeren") }
        
        Text("App-Version 1.0.0", style = MaterialTheme.typography.bodySmall, modifier = Modifier.align(Alignment.CenterHorizontally), color = Color.Gray)
    }

    if (showImpressum) {
        AlertDialog(
            onDismissRequest = { showImpressum = false },
            title = { Text("Impressum") },
            text = {
                Column {
                    Text("Anbieter:", fontWeight = FontWeight.Bold)
                    Text("Thomas Kipp")
                    Text("Anschrift: [Deine Straße, PLZ Ort]")
                    Text("E-Mail: deine.email@beispiel.de")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Umsatzsteuer-ID: [Falls vorhanden]")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Gemäß § 19 UStG wird keine Umsatzsteuer berechnet.")
                }
            },
            confirmButton = { TextButton(onClick = { showImpressum = false }) { Text("Schließen") } }
        )
    }

    if (showPrivacy) {
        AlertDialog(
            onDismissRequest = { showPrivacy = false },
            title = { Text("Datenschutzerklärung") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text("Datenschutz-Informationen", fontWeight = FontWeight.Bold)
                    Text("1. Datenverarbeitung: Diese App speichert API-Keys und Zugangsdaten ausschließlich lokal auf Ihrem Gerät.")
                    Text("2. Bildanalyse: Zur automatischen Beschreibungserstellung werden Ihre Fotos an Google Gemini (Google Cloud) übertragen. Google verarbeitet diese Daten gemäß deren Datenschutzbestimmungen für KI-Dienste.")
                    Text("3. eBay-Schnittstelle: Zur Erstellung von Angeboten werden Daten an die eBay-API übertragen.")
                    Text("4. Tracking: Wir erfassen keine persönlichen Nutzungsdaten.")
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = { uriHandler.openUri("https://www.google.com/policies/privacy/") }) { Text("Google Datenschutz") }
                }
            },
            confirmButton = { TextButton(onClick = { showPrivacy = false }) { Text("Schließen") } }
        )
    }
}

@Composable
fun CategorySearchDialog(onDismiss: () -> Unit, onCategorySelected: (String) -> Unit, ebayToken: String, treeId: String) {
    var query by remember { mutableStateOf("") }
    var categories by remember { mutableStateOf<List<CategoryInfo>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Kategorie suchen") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("Suchbegriff") }, modifier = Modifier.fillMaxWidth(), trailingIcon = {
                IconButton(onClick = { if (query.isNotBlank()) { loading = true; scope.launch { try { categories = EbayRetrofitClient.ebayApiService.getCategorySuggestions(treeId, query, "Bearer $ebayToken").categorySuggestions?.map { it.category } ?: emptyList() } catch (e: Exception) {} finally { loading = false } } } }) { Icon(Icons.Default.Settings, contentDescription = null) }
            })
            if (loading) CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            Box(modifier = Modifier.heightIn(max = 200.dp)) {
                androidx.compose.foundation.lazy.LazyColumn {
                    items(categories) { cat ->
                        ListItem(headlineContent = { Text(cat.categoryName) }, supportingContent = { Text("ID: ${cat.categoryId}") }, modifier = Modifier.clickable { onCategorySelected(cat.categoryId) })
                    }
                }
            }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("Schließen") } })
}

@Composable
fun SafeTextField(value: String, onValueChange: (String) -> Unit, label: String, modifier: Modifier = Modifier, singleLine: Boolean = true, isError: Boolean = false, supportingText: @Composable (() -> Unit)? = null, keyboardOptions: KeyboardOptions = KeyboardOptions.Default) {
    var localText by remember { mutableStateOf(value) }
    LaunchedEffect(value) { if (value != localText) localText = value }
    OutlinedTextField(value = localText, onValueChange = { localText = it; onValueChange(it) }, label = { Text(label) }, modifier = modifier, singleLine = singleLine, isError = isError, supportingText = supportingText, keyboardOptions = keyboardOptions)
}

@Composable
fun DraftDisplay(draft: EbayDraft, viewModel: QuicksaleViewModel, ebayToken: String, marketplaceId: String, settingsManager: SettingsManager, context: Context) {
    var showCategoryDialog by remember { mutableStateOf(false) }
    val uploadState by viewModel.uploadState.collectAsState()
    
    val ebayStartPrice by settingsManager.ebayStartPrice.collectAsState(initial = "1.00")
    val merchantLocation by settingsManager.ebayMerchantLocation.collectAsState(initial = "")
    val paymentPolicy by settingsManager.ebayPaymentPolicy.collectAsState(initial = "")
    val fulfillmentPolicy by settingsManager.ebayFulfillmentPolicy.collectAsState(initial = "")
    val returnPolicy by settingsManager.ebayReturnPolicy.collectAsState(initial = "")
    val ebayStartTime by settingsManager.ebayStartTime.collectAsState(initial = "")

    val missingFields = mutableListOf<String>()
    if (ebayToken.isBlank()) missingFields.add("eBay Login")
    if (draft.categoryId.isBlank()) missingFields.add("Kategorie ID")
    if (draft.title.isBlank()) missingFields.add("Titel")
    if (draft.suggestedPrice.isBlank()) missingFields.add("Preis")
    if (merchantLocation.isBlank()) missingFields.add("Standort")
    if (paymentPolicy.isBlank()) missingFields.add("Zahlung")
    if (fulfillmentPolicy.isBlank()) missingFields.add("Versand")
    if (returnPolicy.isBlank()) missingFields.add("Rückgabe")

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        HorizontalDivider()
        
        AnimatedVisibility(visible = true) {
            Text("Generierter Entwurf", style = MaterialTheme.typography.titleLarge)
        }
        
        SafeTextField(
            value = draft.title, 
            onValueChange = { viewModel.updateDraft(draft.copy(title = it), settingsManager) }, 
            label = "Titel", 
            modifier = Modifier.fillMaxWidth(),
            supportingText = { Text("${draft.title.length}/80 Zeichen") }
        )
        if (draft.title.length < 20) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer), modifier = Modifier.fillMaxWidth()) {
                Text(text = "Warnung: Titel ist sehr kurz.", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(8.dp), style = MaterialTheme.typography.bodySmall)
            }
        }
        
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SafeTextField(value = draft.categoryId, onValueChange = { viewModel.updateDraft(draft.copy(categoryId = it), settingsManager) }, label = "Kategorie ID", modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            IconButton(onClick = { showCategoryDialog = true }) { Icon(Icons.Default.Settings, contentDescription = "Suchen") }
        }

        val conditions = listOf("NEW", "LIKE_NEW", "USED_EXCELLENT", "USED_GOOD", "USED_ACCEPTABLE", "FOR_PARTS_OR_NOT_WORKING")
        var condExpanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(expanded = condExpanded, onExpandedChange = { condExpanded = !condExpanded }) {
            OutlinedTextField(value = draft.condition, onValueChange = {}, label = { Text("Zustand") }, readOnly = true, modifier = Modifier.fillMaxWidth().menuAnchor(), trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = condExpanded) })
            ExposedDropdownMenu(expanded = condExpanded, onDismissRequest = { condExpanded = false }) {
                conditions.forEach { cond ->
                    DropdownMenuItem(text = { Text(cond) }, onClick = { viewModel.updateDraft(draft.copy(condition = cond), settingsManager); condExpanded = false })
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SafeTextField(value = draft.quantity.toString(), onValueChange = { viewModel.updateDraft(draft.copy(quantity = it.toIntOrNull() ?: 1), settingsManager) }, label = "Stückzahl", modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            SafeTextField(value = draft.weight, onValueChange = { viewModel.updateDraft(draft.copy(weight = it), settingsManager) }, label = "Gewicht (kg)", modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        }

        val shippers = listOf("DHL Paket", "Hermes Päckchen", "Deutsche Post Brief")
        var shipperExpanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(expanded = shipperExpanded, onExpandedChange = { shipperExpanded = !shipperExpanded }) {
            OutlinedTextField(value = draft.shippingService, onValueChange = {}, label = { Text("Versanddienstleister") }, readOnly = true, modifier = Modifier.fillMaxWidth().menuAnchor(), trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = shipperExpanded) })
            ExposedDropdownMenu(expanded = shipperExpanded, onDismissRequest = { shipperExpanded = false }) {
                shippers.forEach { s -> DropdownMenuItem(text = { Text(s) }, onClick = { viewModel.updateDraft(draft.copy(shippingService = s), settingsManager); shipperExpanded = false }) }
            }
        }

        if (draft.aspects.isNotEmpty()) {
            Text("Merkmale (Aspects)", style = MaterialTheme.typography.titleMedium)
            draft.aspects.forEach { (key, value) ->
                SafeTextField(value = value, onValueChange = { newVal -> 
                    val newAspects = draft.aspects.toMutableMap()
                    newAspects[key] = newVal
                    viewModel.updateDraft(draft.copy(aspects = newAspects), settingsManager) 
                }, label = key, modifier = Modifier.fillMaxWidth())
            }
        }

        var showPreview by remember { mutableStateOf(false) }
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f))) {
            Column(modifier = Modifier.padding(8.dp)) {
                TextButton(onClick = { showPreview = !showPreview }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (showPreview) "Code anzeigen" else "HTML-Vorschau anzeigen")
                }
                if (showPreview) {
                    androidx.compose.ui.viewinterop.AndroidView(
                        factory = { ctx -> android.webkit.WebView(ctx).apply { loadDataWithBaseURL(null, draft.descriptionHtml, "text/html", "utf-8", null) } },
                        update = { webView -> webView.loadDataWithBaseURL(null, draft.descriptionHtml, "text/html", "utf-8", null) },
                        modifier = Modifier.fillMaxWidth().height(250.dp)
                    )
                } else {
                    SafeTextField(value = draft.descriptionHtml, onValueChange = { viewModel.updateDraft(draft.copy(descriptionHtml = it), settingsManager) }, label = "HTML Code", modifier = Modifier.fillMaxWidth().heightIn(max = 250.dp), singleLine = false)
                }
            }
        }
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            SafeTextField(value = draft.suggestedPrice, onValueChange = { viewModel.updateDraft(draft.copy(suggestedPrice = it), settingsManager) }, label = "Preis (€)", modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            
            val formats = listOf("AUCTION", "FIXED_PRICE")
            formats.forEach { format ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.selectable(selected = draft.listingFormat == format, onClick = { viewModel.updateDraft(draft.copy(listingFormat = format), settingsManager) }, role = Role.RadioButton)) {
                    RadioButton(selected = draft.listingFormat == format, onClick = null)
                    Text(text = if (format == "AUCTION") "Auktion" else "Sofort-Kauf", modifier = Modifier.padding(start = 4.dp))
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Switch(checked = draft.bestOfferEnabled, onCheckedChange = { viewModel.updateDraft(draft.copy(bestOfferEnabled = it), settingsManager) })
            Text("Preisvorschlag erlauben", modifier = Modifier.padding(start = 8.dp))
        }

        if (missingFields.isNotEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer), modifier = Modifier.fillMaxWidth()) {
                Text(text = "⚠️ Bitte in den Einstellungen ausfüllen:\n${missingFields.joinToString(", ")}", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(12.dp), fontWeight = FontWeight.Bold)
            }
        }

        Button(
            onClick = { 
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
                @Suppress("DEPRECATION")
                vibrator?.vibrate(android.os.VibrationEffect.createOneShot(50, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                
                viewModel.uploadToEbay(
                    draft = draft,
                    token = ebayToken,
                    defaultPrice = ebayStartPrice,
                    merchantLocation = merchantLocation,
                    paymentId = paymentPolicy,
                    fulfillmentId = fulfillmentPolicy,
                    returnId = returnPolicy,
                    startTimeText = ebayStartTime,
                    marketplaceId = marketplaceId,
                    settingsManager = settingsManager
                )
            },
            enabled = missingFields.isEmpty() && uploadState !is UploadUiState.Loading,
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            if (uploadState is UploadUiState.Loading) CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary) else Text("Auf eBay listen")
        }

        OutlinedButton(onClick = { viewModel.resetAll(settingsManager, context) }, modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Text("Entwurf verwerfen", color = MaterialTheme.colorScheme.error)
        }
    }

    if (showCategoryDialog) {
        val treeId = viewModel.getTreeIdForMarketplace(marketplaceId)
        CategorySearchDialog(onDismiss = { showCategoryDialog = false }, onCategorySelected = { viewModel.updateDraft(draft.copy(categoryId = it), settingsManager); showCategoryDialog = false }, ebayToken = ebayToken, treeId = treeId)
    }
}