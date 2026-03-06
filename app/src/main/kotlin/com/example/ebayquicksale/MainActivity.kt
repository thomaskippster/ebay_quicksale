package com.example.ebayquicksale

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.launch
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            QuiksaleApp()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuiksaleApp() {
    val navController = rememberNavController()
    
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
            composable("main") { MainScreen() }
            composable("settings") { SettingsScreen() }
        }
    }
}

@Composable
fun MainScreen() {
    var notes by remember { mutableStateOf("") }
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val context = LocalContext.current

    // Kamera-Launcher für das Vorschaubild
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            capturedBitmap = bitmap
        }
    }

    // Permission-Launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            cameraLauncher.launch()
        } else {
            Toast.makeText(context, "Kamera-Berechtigung verweigert", Toast.LENGTH_SHORT).show()
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Button(
            onClick = { 
                when (PackageManager.PERMISSION_GRANTED) {
                    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) -> {
                        cameraLauncher.launch()
                    }
                    else -> {
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
        ) {
            Text("Kamera starten")
        }

        // Preview des aufgenommenen Bildes anzeigen
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
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            minLines = 5
        )
    }
}

@Composable
fun SettingsScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text("Einstellungen (Platzhalter)", style = MaterialTheme.typography.headlineMedium)
    }
}
