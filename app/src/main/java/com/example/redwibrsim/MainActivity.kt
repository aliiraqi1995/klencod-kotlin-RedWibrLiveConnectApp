package com.example.redwibrsim

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val darkRedScheme = darkColorScheme(
                primary = Color(0xFFFF3B30),
                onPrimary = Color.White,
                background = Color(0xFF121212),
                onBackground = Color.White,
                surface = Color(0xFF232325),
                onSurface = Color.White
            )

            MaterialTheme(colorScheme = darkRedScheme) {
                RealWifiTestScreen()
            }
        }
    }
}

@Composable
fun RealWifiTestScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val redAccent = Color(0xFFFF3B30)
    val darkBg = Color(0xFF121212)
    val panelColor = Color(0xFF232325)

    var ssidInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    var statusText by remember { mutableStateOf("Ready") }
    var isTesting by remember { mutableStateOf(false) }
    var attemptsCount by remember { mutableIntStateOf(0) }
    var activeCallback by remember { mutableStateOf<ConnectivityManager.NetworkCallback?>(null) }
    var timeoutJob by remember { mutableStateOf<Job?>(null) }

    val currentSystemWifi = remember(isTesting, attemptsCount) {
        getCurrentNetworkSummary(context)
    }

    val timeAdded = remember(attemptsCount) {
        SimpleDateFormat("yyyy/MM/dd hh:mm:ss a", Locale.getDefault()).format(Date())
    }

    fun stopCurrentTest(newStatus: String = "Stopped") {
        timeoutJob?.cancel()
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        activeCallback?.let { cb ->
            runCatching { cm.unregisterNetworkCallback(cb) }
        }
        activeCallback = null
        isTesting = false
        statusText = newStatus
    }

    fun startRealWifiConnectionTest() {
        val cleanSsid = ssidInput.trim()
        if (cleanSsid.isEmpty()) {
            statusText = "Enter SSID first"
            return
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            statusText = "Requires Android 10 (API 29) or higher"
            return
        }

        stopCurrentTest("Connecting...")
        attemptsCount++
        isTesting = true

        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val specifierBuilder = WifiNetworkSpecifier.Builder()
            .setSsid(cleanSsid)

        if (passwordInput.isNotEmpty()) {
            specifierBuilder.setWpa2Passphrase(passwordInput)
        }

        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .setNetworkSpecifier(specifierBuilder.build())
            .build()

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                timeoutJob?.cancel()
                isTesting = false
                statusText = "Connected to $cleanSsid"
            }

            override fun onUnavailable() {
                timeoutJob?.cancel()
                isTesting = false
                statusText = "Failed / Unavailable ($cleanSsid)"
            }

            override fun onLost(network: Network) {
                if (!isTesting) {
                    statusText = "Disconnected from $cleanSsid"
                }
            }
        }

        activeCallback = callback
        runCatching {
            cm.requestNetwork(request, callback)
        }.onFailure {
            isTesting = false
            statusText = "Request error: ${it.localizedMessage ?: "failed"}"
        }

        timeoutJob = scope.launch {
            delay(15_000L)
            if (isTesting) {
                stopCurrentTest("Timeout (15s)")
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { stopCurrentTest("Closed") }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(darkBg)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "W+",
                color = redAccent,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "WIBR+ Live Connect",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        HorizontalDivider(thickness = 2.dp, color = redAccent)

        Spacer(modifier = Modifier.height(16.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = statusText,
                color = redAccent,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (isTesting) {
                CircularProgressIndicator(
                    color = Color.White,
                    trackColor = Color.DarkGray,
                    modifier = Modifier.size(32.dp),
                    strokeWidth = 2.5.dp
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            OutlinedTextField(
                value = ssidInput,
                onValueChange = { ssidInput = it },
                label = { Text("SSID (اسم الشبكة)", color = redAccent) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = passwordInput,
                onValueChange = { passwordInput = it },
                label = { Text("Password (كلمة المرور)", color = redAccent) },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        LinearProgressIndicator(
            progress = { if (isTesting) 0.65f else if (attemptsCount > 0) 1f else 0f },
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp),
            color = redAccent,
            trackColor = Color(0xFF2C2C2E)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            InfoRow("Target SSID:", ssidInput.ifBlank { "-" }, redAccent)
            InfoRow("Attempts:", "$attemptsCount", redAccent)
            InfoRow("Last action:", timeAdded, redAccent)
            InfoRow("Device network:", currentSystemWifi, redAccent)
        }

        Spacer(modifier = Modifier.height(22.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = { startRealWifiConnectionTest() },
                shape = MaterialTheme.shapes.extraSmall,
                colors = ButtonDefaults.buttonColors(
                    containerColor = panelColor,
                    contentColor = redAccent
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
            ) {
                Text(
                    text = if (isTesting) "Retry" else "Test Connect",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Button(
                onClick = { stopCurrentTest("Stopped") },
                shape = MaterialTheme.shapes.extraSmall,
                colors = ButtonDefaults.buttonColors(
                    containerColor = panelColor,
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
            ) {
                Text(
                    text = "Stop",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
    valueColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = valueColor, fontSize = 15.sp)
        Text(text = value, color = valueColor, fontSize = 15.sp)
    }
}

private fun getCurrentNetworkSummary(context: Context): String {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val active = cm.activeNetwork ?: return "Offline"
    val caps = cm.getNetworkCapabilities(active) ?: return "No capabilities"
    return when {
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi Active"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile Data"
        else -> "Other Network"
    }
}