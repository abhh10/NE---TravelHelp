package com.example.ne_travelhelp

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.ne_travelhelp.ui.theme.NETravelHelpTheme
import com.google.android.gms.location.*
import android.speech.RecognizerIntent
import androidx.compose.runtime.saveable.rememberSaveable

class MainActivity : ComponentActivity() {
    private lateinit var sharedPrefs: SharedPreferences
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var locationRequest: LocationRequest

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        sharedPrefs = getSharedPreferences("travel_help_prefs", Context.MODE_PRIVATE)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        
        setupLocationServices()
        
        setContent {
            NETravelHelpTheme {
                TravelHelpApp(
                    sharedPrefs = sharedPrefs,
                    onLocationPermissionGranted = { startLocationUpdates() },
                    onLocationPermissionDenied = { showLocationPermissionDenied() }
                )
            }
        }
    }

    private fun setupLocationServices() {
        locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000L)
            .setMinUpdateIntervalMillis(5000L)
            .setWaitForAccurateLocation(true)
            .build()
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    sharedPrefs.edit()
                        .putFloat("last_lat", location.latitude.toFloat())
                        .putFloat("last_lng", location.longitude.toFloat())
                        .putLong("last_location_time", System.currentTimeMillis())
                        .apply()
                }
            }
        }
        
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        }
    }

    private fun showLocationPermissionDenied() {
        Toast.makeText(this, "Location permission is required for safety features", Toast.LENGTH_LONG).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::locationCallback.isInitialized) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TravelHelpApp(
    sharedPrefs: SharedPreferences,
    onLocationPermissionGranted: () -> Unit,
    onLocationPermissionDenied: () -> Unit
) {
    var currentScreen by remember { mutableStateOf("home") }
    var isLoggedIn by remember { mutableStateOf(sharedPrefs.getBoolean("is_logged_in", false)) }
    var showPermissionDialog by remember { mutableStateOf(false) }
    
    val context = LocalContext.current
    
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val locationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        val notificationGranted = permissions[Manifest.permission.POST_NOTIFICATIONS] == true
        val audioGranted = permissions[Manifest.permission.RECORD_AUDIO] == true
        
        if (locationGranted) {
            onLocationPermissionGranted()
        } else {
            onLocationPermissionDenied()
        }
        
        if (!notificationGranted) {
            showPermissionDialog = true
        }
    }

    LaunchedEffect(Unit) {
        val hasLocation = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasNotification = ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        
        if (!hasLocation || !hasNotification) {
            permissionLauncher.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.POST_NOTIFICATIONS,
                Manifest.permission.RECORD_AUDIO
            ))
        }
    }

    if (!isLoggedIn) {
        LoginScreen(
            onLogin = { username, password ->
                sharedPrefs.edit().putBoolean("is_logged_in", true).apply()
                isLoggedIn = true
            }
        )
    } else {
        val snackbarHostState = remember { SnackbarHostState() }
        val haptics = LocalHapticFeedback.current
        var showGlobalSosDialog by rememberSaveable { mutableStateOf(false) }
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp
                ) {
                    val items = listOf(
                        Triple("home", Icons.Filled.Home, stringResource(id = R.string.nav_home)),
                        Triple("safety", Icons.Filled.Shield, stringResource(id = R.string.nav_safety)),
                        Triple("profile", Icons.Filled.Person, stringResource(id = R.string.nav_profile)),
                        Triple("settings", Icons.Filled.Settings, stringResource(id = R.string.nav_settings)),
                        Triple("help", Icons.Filled.Help, stringResource(id = R.string.nav_help))
                    )
                    items.forEach { (screen, icon, label) ->
                        NavigationBarItem(
                            icon = { Icon(icon, contentDescription = label) },
                            label = { Text(label) },
                            selected = currentScreen == screen,
                            onClick = { currentScreen = screen }
                        )
                    }
                }
            },
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = { showGlobalSosDialog = true },
                    icon = { Icon(Icons.Filled.Emergency, contentDescription = null) },
                    text = { Text(stringResource(id = R.string.action_sos)) }
                )
            }
        ) { paddingValues ->
            Box(modifier = Modifier.padding(paddingValues)) {
                when (currentScreen) {
                    "home" -> HomeScreen(
                        sharedPrefs = sharedPrefs,
                        onNavigateTo = { currentScreen = it },
                        snackbarHostState = snackbarHostState
                    )
                    "safety" -> SafetyScreen(sharedPrefs = sharedPrefs)
                    "map" -> MapScreen()
                    "profile" -> ProfileScreen(sharedPrefs = sharedPrefs)
                    "settings" -> SettingsScreen(sharedPrefs = sharedPrefs)
                    "help" -> HelpScreen()
                }
            }
        }

        if (showGlobalSosDialog) {
            AlertDialog(
                onDismissRequest = { showGlobalSosDialog = false },
                title = { Text(stringResource(id = R.string.dialog_sos_title)) },
                text = { Text(stringResource(id = R.string.dialog_sos_message)) },
                confirmButton = {
                    TextButton(onClick = { 
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        showGlobalSosDialog = false 
                    }) {
                        Text(stringResource(id = R.string.action_send))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showGlobalSosDialog = false }) {
                        Text(stringResource(id = R.string.action_cancel))
                    }
                }
            )
        }
    }

    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = { Text("Notification Permission Required") },
            text = { Text("Please enable notifications to receive safety alerts and emergency updates.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPermissionDialog = false
                        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        })
                    }
                ) {
                    Text("Open Settings")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionDialog = false }) {
                    Text("Later")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(onLogin: (String, String) -> Unit) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.primaryContainer
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Default.Security,
                contentDescription = "App Icon",
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.onPrimary
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "NE Travel Help",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                text = "Your Safety Companion",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
            )
            
            Spacer(modifier = Modifier.height(48.dp))
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Username") },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (passwordVisible) "Hide password" else "Show password"
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = if (passwordVisible) androidx.compose.ui.text.input.VisualTransformation.None 
                        else androidx.compose.ui.text.input.PasswordVisualTransformation()
                    )
                    
                    Button(
                        onClick = { onLogin(username, password) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("Continue", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun HomeScreen(
    sharedPrefs: SharedPreferences,
    onNavigateTo: (String) -> Unit,
    snackbarHostState: SnackbarHostState
) {
    var lastLat by rememberSaveable { mutableStateOf(sharedPrefs.getFloat("last_lat", 0f)) }
    var lastLng by rememberSaveable { mutableStateOf(sharedPrefs.getFloat("last_lng", 0f)) }
    var lastLocationTime by rememberSaveable { mutableStateOf(sharedPrefs.getLong("last_location_time", 0L)) }

    // Simple anomaly detection scaffold: inactivity and sudden jump
    var lastUpdateMs by rememberSaveable { mutableStateOf(lastLocationTime) }
    var lastAnomaly by rememberSaveable { mutableStateOf<String?>(null) }
    var showSosDialog by rememberSaveable { mutableStateOf(false) }
    var showAiDialog by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(lastLocationTime) {
        val now = System.currentTimeMillis()
        val minutes = (now - lastLocationTime) / (1000 * 60)
        if (lastLocationTime > 0 && minutes >= 30) {
            lastAnomaly = "Prolonged inactivity detected"
        }
        // Sudden jump heuristic (mock): if coords changed a lot (no previous location stored here)
        // This can be replaced by your AI model output via a repository
    }
    
    val safetyScore = remember(lastLat, lastLng) { 
        calculateSafetyScore(lastLat.toDouble(), lastLng.toDouble()) 
    }
    
    val isInHighRiskZone = remember(lastLat, lastLng) {
        isInHighRiskZone(lastLat.toDouble(), lastLng.toDouble())
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Welcome Back!",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold
                        )
                        if (lastAnomaly != null) {
                            AssistChip(
                                onClick = { showAiDialog = true },
                                label = { Text("AI Active") },
                                leadingIcon = { Icon(Icons.Default.AutoAwesome, contentDescription = null) }
                            )
                        }
                    }
                    Text(
                        text = "Your safety is our priority",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
            }
        }

        item {
            SafetyScoreCard(
                score = safetyScore,
                isInHighRiskZone = isInHighRiskZone,
                lastLocationTime = lastLocationTime
            )
        }

        item {
            Text(
                text = "Quick Actions",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                QuickActionCard(
                    title = "SOS",
                    icon = Icons.Default.Emergency,
                    color = MaterialTheme.colorScheme.error,
                    onClick = { showSosDialog = true }
                )
                QuickActionCard(
                    title = "Safety",
                    icon = Icons.Default.Security,
                    color = MaterialTheme.colorScheme.primary,
                    onClick = { onNavigateTo("safety") }
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                QuickActionCard(
                    title = "Profile",
                    icon = Icons.Default.Person,
                    color = MaterialTheme.colorScheme.secondary,
                    onClick = { onNavigateTo("profile") }
                )
                QuickActionCard(
                    title = "Map",
                    icon = Icons.Default.Map,
                    color = MaterialTheme.colorScheme.tertiary,
                    onClick = { onNavigateTo("map") }
                )
            }
        }

        item {
            AnimatedVisibility(visible = lastAnomaly != null, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Anomaly Detected", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(lastAnomaly ?: "", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }
        }
    }

    if (showSosDialog) {
        AlertDialog(
            onDismissRequest = { showSosDialog = false },
            title = { Text(stringResource(id = R.string.dialog_sos_title)) },
            text = { Text(stringResource(id = R.string.dialog_sos_message)) },
            confirmButton = {
                TextButton(onClick = { showSosDialog = false }) {
                    Text(stringResource(id = R.string.action_send))
                }
            },
            dismissButton = {
                TextButton(onClick = { showSosDialog = false }) {
                    Text(stringResource(id = R.string.action_cancel))
                }
            }
        )
    }

    if (showAiDialog) {
        val suggestions = remember(lastAnomaly, lastLat, lastLng) {
            generateAiSuggestions(
                anomaly = lastAnomaly,
                latitude = lastLat.toDouble(),
                longitude = lastLng.toDouble()
            )
        }
        AlertDialog(
            onDismissRequest = { showAiDialog = false },
            title = { Text(stringResource(id = R.string.ai_suggestions_title)) },
            text = {
                Column {
                    suggestions.forEach { line ->
                        Text("• $line", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(6.dp))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAiDialog = false }) {
                    Text(stringResource(id = R.string.action_close))
                }
            }
        )
    }
}

@Composable
fun SafetyScoreCard(
    score: Int,
    isInHighRiskZone: Boolean,
    lastLocationTime: Long
) {
    val scoreColor = when {
        score >= 80 -> MaterialTheme.colorScheme.primary
        score >= 60 -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.error
    }
    val animatedProgress by animateFloatAsState(targetValue = score / 100f, animationSpec = tween(durationMillis = 600), label = "score")

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isInHighRiskZone) MaterialTheme.colorScheme.errorContainer
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Safety Score",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "$score/100",
                    style = MaterialTheme.typography.headlineMedium,
                    color = scoreColor,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            LinearProgressIndicator(
                progress = animatedProgress,
                modifier = Modifier.fillMaxWidth(),
                color = scoreColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            if (isInHighRiskZone) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = "Warning",
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "You are in a high-risk zone",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            } else {
                Text(
                    text = "You are in a safe area",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
            
            if (lastLocationTime > 0) {
                Text(
                    text = "Last updated: ${formatTime(lastLocationTime)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
fun QuickActionCard(
    title: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                icon,
                contentDescription = title,
                modifier = Modifier.size(32.dp),
                tint = color
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = color,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun SafetyScreen(sharedPrefs: SharedPreferences) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Safety Features",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold
            )
        }

        item {
            SafetyFeatureCard(
                title = "Emergency SOS",
                description = "Send immediate help request with your location",
                icon = Icons.Default.Emergency,
                color = MaterialTheme.colorScheme.error,
                onClick = { /* Handle SOS */ }
            )
        }

        item {
            SafetyFeatureCard(
                title = "Geo-fencing Alerts",
                description = "Get notified when entering high-risk areas",
                icon = Icons.Default.LocationOn,
                color = MaterialTheme.colorScheme.primary,
                onClick = { /* Handle geo-fencing */ }
            )
        }

        item {
            SafetyFeatureCard(
                title = "Real-time Tracking",
                description = "Share your location with trusted contacts",
                icon = Icons.Default.GpsFixed,
                color = MaterialTheme.colorScheme.secondary,
                onClick = { /* Handle tracking */ }
            )
        }

        item {
            SafetyFeatureCard(
                title = "Safety Tips",
                description = "Learn how to stay safe while traveling",
                icon = Icons.Default.Lightbulb,
                color = MaterialTheme.colorScheme.tertiary,
                onClick = { /* Handle tips */ }
            )
        }
    }
}

@Composable
fun MapScreen() {
    // Mock heatmap pins using cards; integrate Maps SDK later
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(text = "Regional Heatmap (Mock)", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(text = "Areas with higher anomaly density appear at the top.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        }
        val mockAreas = listOf(
            Triple("Kaziranga Park", 0.82f, "Wildlife zone; keep to marked trails"),
            Triple("Shillong Peak", 0.65f, "Fog risk; avoid solo travel at night"),
            Triple("Guwahati Riverside", 0.40f, "Generally safe; watch belongings"),
            Triple("Tawang Monastery", 0.25f, "Safe; tourist-friendly area")
        )
        items(mockAreas.size) { idx ->
            val (name, intensity, note) = mockAreas[idx]
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(name, style = MaterialTheme.typography.titleMedium)
                        Text("Risk ${(intensity * 100).toInt()}%", color = if (intensity > 0.6f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(progress = intensity, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(6.dp))
                    Text(note, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
fun SafetyFeatureCard(
    title: String,
    description: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = title,
                modifier = Modifier.size(40.dp),
                tint = color
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = "Navigate",
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
fun ProfileScreen(sharedPrefs: SharedPreferences) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Profile",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = "Profile",
                        modifier = Modifier.size(60.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Tourist User",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Safety ID: #${sharedPrefs.getString("safety_id", "000000")}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
            }
        }

        item {
            ProfileOptionCard(
                title = "Digital ID",
                description = "Manage your tourist identification",
                icon = Icons.Default.Badge,
                onClick = { /* Handle Digital ID */ }
            )
        }

        item {
            ProfileOptionCard(
                title = "Emergency Contacts",
                description = "Add trusted contacts for emergencies",
                icon = Icons.Default.Contacts,
                onClick = { /* Handle contacts */ }
            )
        }

        item {
            ProfileOptionCard(
                title = "Travel History",
                description = "View your safety activity log",
                icon = Icons.Default.History,
                onClick = { /* Handle history */ }
            )
        }
    }
}

@Composable
fun ProfileOptionCard(
    title: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = title,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = "Navigate",
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(sharedPrefs: SharedPreferences) {
    val context = LocalContext.current
    var selectedLanguage by rememberSaveable { mutableStateOf(sharedPrefs.getString("pref_lang", "en") ?: "en") }
    val languages = listOf(
        "en" to "English",
        "hi" to "Hindi",
        "bn" to "Bengali",
        "ta" to "Tamil",
        "te" to "Telugu",
        "mr" to "Marathi",
        "gu" to "Gujarati",
        "pa" to "Punjabi",
        "ml" to "Malayalam",
        "kn" to "Kannada",
        "or" to "Odia"
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold
            )
        }

        item {
            SettingsSectionCard(
                title = "Privacy & Security",
                items = listOf(
                    "Location Services" to "Enable location tracking for safety",
                    "Notifications" to "Receive safety alerts and updates",
                    "Data Sharing" to "Control how your data is shared"
                )
            )
        }

        item {
            SettingsSectionCard(
                title = "App Preferences",
                items = listOf(
                    "Language" to "Choose your preferred language",
                    "Theme" to "Light or dark mode",
                    "Units" to "Metric or imperial measurements"
                )
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Language", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    var expanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded }
                    ) {
                        OutlinedTextField(
                            value = languages.firstOrNull { it.first == selectedLanguage }?.second ?: "English",
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            languages.forEach { (code, name) ->
                                DropdownMenuItem(
                                    text = { Text(name) },
                                    onClick = {
                                        selectedLanguage = code
                                        sharedPrefs.edit().putString("pref_lang", code).apply()
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            SettingsSectionCard(
                title = "About",
                items = listOf(
                    "Version" to "1.0.0",
                    "Privacy Policy" to "View our privacy policy",
                    "Terms of Service" to "View terms and conditions"
                )
            )
        }
    }
}

@Composable
fun SettingsSectionCard(
    title: String,
    items: List<Pair<String, String>>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            
            items.forEach { (itemTitle, itemDesc) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = itemTitle,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = itemDesc,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = "Navigate",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
                
                if (itemTitle != items.last().first) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    )
                }
            }
        }
    }
}

@Composable
fun HelpScreen() {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
    Text(
                text = "Help & Support",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold
            )
        }

        item {
            HelpSectionCard(
                title = "Getting Started",
                items = listOf(
                    "How to create a Digital ID",
                    "Setting up emergency contacts",
                    "Understanding safety scores"
                )
            )
        }

        item {
            HelpSectionCard(
                title = "Safety Features",
                items = listOf(
                    "Using the SOS button",
                    "Geo-fencing alerts",
                    "Real-time tracking"
                )
            )
        }

        item {
            HelpSectionCard(
                title = "Troubleshooting",
                items = listOf(
                    "Location not updating",
                    "Notifications not working",
                    "App crashes or freezes"
                )
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.Support,
                        contentDescription = "Support",
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Need More Help?",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "Contact our support team for assistance",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { /* Handle contact support */ },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("Contact Support")
                    }
                }
            }
        }
    }
}

@Composable
fun HelpSectionCard(
    title: String,
    items: List<String>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            
            items.forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.HelpOutline,
                        contentDescription = "Help",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = item,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

// Helper functions
private fun calculateSafetyScore(lat: Double, lng: Double): Int {
    if (lat == 0.0 && lng == 0.0) return 50
    val baseScore = 70
    val latRisk = if (lat > 26.0) 5 else 0
    val lngRisk = if (lng > 90.0) 5 else 0
    return (baseScore - latRisk - lngRisk).coerceIn(10, 99)
}

private fun isInHighRiskZone(lat: Double, lng: Double): Boolean {
    if (lat == 0.0 && lng == 0.0) return false
    val riskLat = 26.2006
    val riskLng = 92.9376
    val results = FloatArray(1)
    Location.distanceBetween(lat, lng, riskLat, riskLng, results)
    return results[0] < 1000
}

private fun formatTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    val minutes = diff / (1000 * 60)
    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "$minutes minutes ago"
        else -> "${minutes / 60} hours ago"
    }
}

// Mock AI suggestion generator. Replace with real model integration.
private fun generateAiSuggestions(anomaly: String?, latitude: Double, longitude: Double): List<String> {
    val list = mutableListOf<String>()
    if (anomaly != null) {
        list += "We detected: $anomaly"
        when {
            anomaly.contains("inactivity", ignoreCase = true) -> {
                list += "Consider taking a short break at a nearby safe spot."
                list += "Notify a trusted contact if you feel unwell."
            }
            anomaly.contains("deviat", ignoreCase = true) -> {
                list += "You seem off your planned route. Would you like safer alternatives?"
                list += "Opening map to guide you back to the main route."
            }
        }
    } else {
        list += "All normal. Explore nearby attractions safely."
    }
    list += "Nearest help center is approx 1.2 km from your location."
    val mapsLink = "https://maps.google.com/?q=$latitude,$longitude"
    list += "Open map: $mapsLink"
    return list
}

@Preview(showBackground = true)
@Composable
fun TravelHelpAppPreview() {
    NETravelHelpTheme {
        // Preview content
    }
}
