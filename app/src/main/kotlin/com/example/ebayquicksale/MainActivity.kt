@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ebayquicksale

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
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
            QuiksaleApp(settingsManager, ebayAuthManager)
        }
    }
}

@Composable
fun QuiksaleApp(settingsManager: SettingsManager, ebayAuthManager: EbayAuthManager) {
    val navController = rememberNavController()
    val viewModel: QuiksaleViewModel = viewModel()
    
    LaunchedEffect(Unit) {
        viewModel.loadDraftFromStorage(settingsManager)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Quicksale") },
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
fun MainScreen(viewModel: QuiksaleViewModel, settingsManager: SettingsManager) {
    val notes by viewModel.notes.collectAsState()
    val imagePaths by viewModel.imagePaths.collectAsState()
    var photoUri by remember { mutableStateOf<Uri?>(null) }
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    
    val geminiApiKey by settingsManager.geminiApiKey.collectAsState(initial = "")
    val ebayAccessToken by settingsManager.ebayAccessToken.collectAsState(initial = null)
    val defaultListingFormat by settingsManager.ebayListingFormat.collectAsState(initial = "AUCTION")
    
    val uiState by viewModel.uiState.collectAsState()
    val uploadState by viewModel.uploadState.collectAsState()

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
            val uri = createImageUri(context)
            photoUri = uri
            cameraLauncher.launch(uri)
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        uris.forEach { uri ->
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                if (bitmap != null) {
                    val resized = ImageUtils.resizeBitmap(bitmap)
                    viewModel.addImage(context, resized)
                }
            } catch (e: Exception) {}
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (uiState !is QuiksaleUiState.Success) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                modifier = Modifier.fillMaxWidth().height(120.dp),
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

        if (uiState !is QuiksaleUiState.Success) {
            Button(
                onClick = { viewModel.generateDraft(geminiApiKey, ebayAccessToken, defaultListingFormat, settingsManager) },
                enabled = imagePaths.isNotEmpty() && geminiApiKey.isNotBlank() && uiState !is QuiksaleUiState.Loading,
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                if (uiState is QuiksaleUiState.Loading) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(24.dp))
                } else {
                    Text("Angebot generieren (Gemini)")
                }
            }
        }

        when (uiState) {
            is QuiksaleUiState.Success -> {
                val draft = (uiState as QuiksaleUiState.Success).draft
                DraftDisplay(draft, viewModel, ebayAccessToken ?: "", settingsManager)
            }
            is QuiksaleUiState.Loading -> { }
            is QuiksaleUiState.Error -> {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer), modifier = Modifier.fillMaxWidth()) {
                    Text(text = (uiState as QuiksaleUiState.Error).message, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))
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
    }
}

private fun createImageUri(context: Context): Uri {
    val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    val file = File.createTempFile("JPEG_${timeStamp}_", ".jpg", context.cacheDir)
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

@Composable
fun SettingsScreen(settingsManager: SettingsManager, ebayAuthManager: EbayAuthManager, viewModel: QuiksaleViewModel) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val geminiApiKey by settingsManager.geminiApiKey.collectAsState(initial = "")
    val ebayStartPrice by settingsManager.ebayStartPrice.collectAsState(initial = "1.00")
    val ebayStartTime by settingsManager.ebayStartTime.collectAsState(initial = "")
    val ebayAccessToken by settingsManager.ebayAccessToken.collectAsState(initial = null)
    val ebayClientId by settingsManager.ebayClientId.collectAsState(initial = "")
    val ebayClientSecret by settingsManager.ebayClientSecret.collectAsState(initial = "")
    val merchantLocation by settingsManager.ebayMerchantLocation.collectAsState(initial = "")
    val fulfillmentPolicy by settingsManager.ebayFulfillmentPolicy.collectAsState(initial = "")
    val isFetchingSettings by viewModel.isFetchingSettings.collectAsState()

    val authLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.let { ebayAuthManager.handleAuthResponse(it, ebayClientSecret) { _, _ -> } }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Einstellungen", style = MaterialTheme.typography.headlineMedium)
        SafeTextField(value = geminiApiKey, onValueChange = { coroutineScope.launch { settingsManager.saveGeminiApiKey(it) } }, label = "Gemini API Key", modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
        HorizontalDivider()
        Text("eBay Credentials", style = MaterialTheme.typography.titleMedium)
        SafeTextField(value = ebayClientId, onValueChange = { coroutineScope.launch { settingsManager.saveEbayClientId(it) } }, label = "Client ID", modifier = Modifier.fillMaxWidth())
        SafeTextField(value = ebayClientSecret, onValueChange = { coroutineScope.launch { settingsManager.saveEbayClientSecret(it) } }, label = "Client Secret", modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
        Button(onClick = { authLauncher.launch(ebayAuthManager.createAuthIntent(ebayClientId)) }, enabled = ebayClientId.isNotBlank() && ebayClientSecret.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Mit eBay verbinden") }
        Button(onClick = { ebayAccessToken?.let { viewModel.fetchEbaySettings(it, settingsManager) { msg -> Toast.makeText(context, msg, Toast.LENGTH_LONG).show() } } }, enabled = ebayAccessToken != null && !isFetchingSettings, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)) {
            if (isFetchingSettings) CircularProgressIndicator(modifier = Modifier.size(24.dp)) else Text("Policies automatisch laden")
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

        HorizontalDivider()
        Text("Standard-Angebotsdaten", style = MaterialTheme.typography.titleMedium)
        SafeTextField(value = ebayStartPrice, onValueChange = { coroutineScope.launch { settingsManager.saveEbayStartPrice(it) } }, label = "Preis (€)", modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        SafeTextField(value = ebayStartTime, onValueChange = { coroutineScope.launch { settingsManager.saveEbayStartTime(it) } }, label = "Startzeit (SOFORT oder yyyy-MM-dd HH:mm)", modifier = Modifier.fillMaxWidth())
    }
}

@Composable
fun CategorySearchDialog(onDismiss: () -> Unit, onCategorySelected: (String) -> Unit, ebayToken: String) {
    var query by remember { mutableStateOf("") }
    var categories by remember { mutableStateOf<List<CategoryInfo>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Kategorie suchen") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("Suchbegriff") }, modifier = Modifier.fillMaxWidth(), trailingIcon = {
                IconButton(onClick = { if (query.isNotBlank()) { loading = true; scope.launch { try { categories = EbayRetrofitClient.ebayApiService.getCategorySuggestions(query, "Bearer $ebayToken").categorySuggestions?.map { it.category } ?: emptyList() } catch (e: Exception) {} finally { loading = false } } } }) { Icon(Icons.Default.Settings, contentDescription = null) }
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
fun DraftDisplay(draft: EbayDraft, viewModel: QuiksaleViewModel, ebayToken: String, settingsManager: SettingsManager) {
    var showCategoryDialog by remember { mutableStateOf(false) }
    val uploadState by viewModel.uploadState.collectAsState()
    val context = LocalContext.current
    
    val ebayStartPrice by settingsManager.ebayStartPrice.collectAsState(initial = "1.00")
    val merchantLocation by settingsManager.ebayMerchantLocation.collectAsState(initial = "")
    val paymentPolicy by settingsManager.ebayPaymentPolicy.collectAsState(initial = "")
    val fulfillmentPolicy by settingsManager.ebayFulfillmentPolicy.collectAsState(initial = "")
    val returnPolicy by settingsManager.ebayReturnPolicy.collectAsState(initial = "")
    val ebayStartTime by settingsManager.ebayStartTime.collectAsState(initial = "")

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        HorizontalDivider()
        Text("Generierter Entwurf", style = MaterialTheme.typography.titleLarge)
        
        SafeTextField(value = draft.title, onValueChange = { viewModel.updateDraft(draft.copy(title = it), settingsManager) }, label = "Titel", modifier = Modifier.fillMaxWidth())
        
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SafeTextField(value = draft.categoryId, onValueChange = { viewModel.updateDraft(draft.copy(categoryId = it), settingsManager) }, label = "Kategorie ID", modifier = Modifier.weight(1f))
            IconButton(onClick = { showCategoryDialog = true }) { Icon(Icons.Default.Settings, contentDescription = "Suchen") }
        }

        SafeTextField(value = draft.descriptionHtml, onValueChange = { viewModel.updateDraft(draft.copy(descriptionHtml = it), settingsManager) }, label = "Beschreibung", modifier = Modifier.fillMaxWidth(), singleLine = false)
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val formats = listOf("AUCTION", "FIXED_PRICE")
            formats.forEach { format ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.selectable(selected = draft.listingFormat == format, onClick = { viewModel.updateDraft(draft.copy(listingFormat = format), settingsManager) }, role = Role.RadioButton)) {
                    RadioButton(selected = draft.listingFormat == format, onClick = null)
                    Text(text = if (format == "AUCTION") "Auktion" else "Sofort-Kauf", modifier = Modifier.padding(start = 4.dp))
                }
            }
        }

        Button(
            onClick = { 
                viewModel.uploadToEbay(
                    draft = draft,
                    token = ebayToken,
                    defaultPrice = ebayStartPrice,
                    merchantLocation = merchantLocation,
                    paymentId = paymentPolicy,
                    fulfillmentId = fulfillmentPolicy,
                    returnId = returnPolicy,
                    startTimeText = ebayStartTime,
                    settingsManager = settingsManager,
                    context = context
                )
            },
            enabled = ebayToken.isNotBlank() && uploadState !is UploadUiState.Loading,
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            if (uploadState is UploadUiState.Loading) CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary) else Text("Auf eBay listen")
        }
    }

    if (showCategoryDialog) {
        CategorySearchDialog(onDismiss = { showCategoryDialog = false }, onCategorySelected = { viewModel.updateDraft(draft.copy(categoryId = it), settingsManager); showCategoryDialog = false }, ebayToken = ebayToken)
    }
}
