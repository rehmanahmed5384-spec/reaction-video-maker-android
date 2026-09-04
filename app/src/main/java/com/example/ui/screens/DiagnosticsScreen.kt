package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.hardware.HardwareCapabilityDetector
import com.example.core.model.DeviceDiagnostics
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val diagnostics = remember { HardwareCapabilityDetector.detectCapabilities(context) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Hardware Diagnostics", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("Real Codec & Camera Inspection", style = MaterialTheme.typography.bodySmall, color = StudioOnSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("diag_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Button(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Ahmed Reaction Studio Diagnostics", diagnostics.toFormattedReport())
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Hardware report copied to clipboard", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = StudioPrimaryContainer),
                        modifier = Modifier.padding(end = 8.dp).testTag("copy_diagnostics_button")
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Copy Report", color = StudioOnPrimaryContainer, fontSize = 12.sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = StudioSurface,
                    titleContentColor = StudioOnSurface
                )
            )
        },
        containerColor = StudioBackground
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                DeviceOverviewCard(diagnostics)
            }

            item {
                CodecCapabilitiesCard(diagnostics)
            }

            item {
                CameraCapabilitiesCard(diagnostics)
            }

            item {
                RawReportCard(diagnostics)
            }
        }
    }
}

@Composable
fun DeviceOverviewCard(diag: DeviceDiagnostics) {
    Card(
        colors = CardDefaults.cardColors(containerColor = StudioSurface),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PhoneAndroid, contentDescription = null, tint = StudioPrimary)
                Spacer(Modifier.width(8.dp))
                Text("System & Compute", fontWeight = FontWeight.Bold, color = StudioOnSurface, fontSize = 16.sp)
            }
            HorizontalDivider(color = StudioOutline.copy(alpha = 0.5f))

            DiagRow("Device", "${diag.manufacturer} ${diag.deviceModel}")
            DiagRow("Android OS", "Android ${diag.osVersion} (API ${diag.apiLevel})")
            DiagRow("Total RAM", "${diag.totalRamMb} MB")
            DiagRow("Available Storage", "${diag.availableStorageMb} MB")
        }
    }
}

@Composable
fun CodecCapabilitiesCard(diag: DeviceDiagnostics) {
    Card(
        colors = CardDefaults.cardColors(containerColor = StudioSurface),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.MovieCreation, contentDescription = null, tint = StudioSecondary)
                Spacer(Modifier.width(8.dp))
                Text("MediaCodec Acceleration", fontWeight = FontWeight.Bold, color = StudioOnSurface, fontSize = 16.sp)
            }
            HorizontalDivider(color = StudioOutline.copy(alpha = 0.5f))

            Text("H.264 / AVC Codecs (Compatibility Standard)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = StudioPrimary)
            diag.h264Encoder?.let {
                CodecStatusRow("Encoder", it.codecName, it.isHardwareAccelerated, "${it.maxSupportedWidth}x${it.maxSupportedHeight}")
            } ?: CodecStatusRow("Encoder", "Unavailable", false, "N/A")

            diag.h264Decoder?.let {
                CodecStatusRow("Decoder", it.codecName, it.isHardwareAccelerated, "Supported")
            } ?: CodecStatusRow("Decoder", "Unavailable", false, "N/A")

            Spacer(Modifier.height(6.dp))

            Text("H.265 / HEVC Codecs (High Efficiency Option)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = StudioSecondary)
            diag.hevcEncoder?.let {
                CodecStatusRow("Encoder", it.codecName, it.isHardwareAccelerated, "${it.maxSupportedWidth}x${it.maxSupportedHeight}")
            } ?: CodecStatusRow("Encoder", "Hardware encoder not found (Smart mode will fallback to H.264)", false, "N/A")

            diag.hevcDecoder?.let {
                CodecStatusRow("Decoder", it.codecName, it.isHardwareAccelerated, "Supported")
            } ?: CodecStatusRow("Decoder", "Unavailable", false, "N/A")
        }
    }
}

@Composable
fun CameraCapabilitiesCard(diag: DeviceDiagnostics) {
    val cam = diag.cameraInfo
    Card(
        colors = CardDefaults.cardColors(containerColor = StudioSurface),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Videocam, contentDescription = null, tint = StudioPrimary)
                Spacer(Modifier.width(8.dp))
                Text("CameraX & Sensor Capabilities", fontWeight = FontWeight.Bold, color = StudioOnSurface, fontSize = 16.sp)
            }
            HorizontalDivider(color = StudioOutline.copy(alpha = 0.5f))

            DiagRow("Total Cameras Detected", "${cam.totalCameras}")
            DiagRow("Front Camera", if (cam.hasFrontCamera) "Available" else "Not present")
            DiagRow("Back Camera", if (cam.hasBackCamera) "Available" else "Not present")
            DiagRow(
                "Rear Hardware Torch",
                if (cam.backCameraHasTorch) "Hardware Unit Present" else "No Flash Unit"
            )
            DiagRow(
                "Front Flash Support",
                if (cam.frontCameraHasTorch) "Physical Unit" else "Software Screen Illumination Fallback"
            )
            DiagRow(
                "Concurrent Dual Camera",
                if (cam.supportsConcurrentCameras) "Supported by Device" else "Device Constraint: Single Active Camera"
            )
        }
    }
}

@Composable
fun RawReportCard(diag: DeviceDiagnostics) {
    Card(
        colors = CardDefaults.cardColors(containerColor = StudioSurfaceVariant),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp)) {
            Text("Console Diagnostic Output", style = MaterialTheme.typography.labelMedium, color = StudioOnSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Text(
                diag.toFormattedReport(),
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = StudioOnSurface.copy(alpha = 0.85f),
                lineHeight = 15.sp
            )
        }
    }
}

@Composable
fun DiagRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = StudioOnSurfaceVariant, fontSize = 13.sp)
        Text(value, color = StudioOnSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun CodecStatusRow(role: String, name: String, isHw: Boolean, extra: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(StudioSurfaceVariant.copy(alpha = 0.4f))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("$role: $name", fontSize = 12.sp, color = StudioOnSurface, fontWeight = FontWeight.Medium)
            Text("Max: $extra", fontSize = 10.sp, color = StudioOnSurfaceVariant)
        }
        AssistChip(
            onClick = {},
            label = { Text(if (isHw) "Hardware" else "Software", fontSize = 10.sp) },
            colors = AssistChipDefaults.assistChipColors(
                containerColor = if (isHw) StudioPrimaryContainer.copy(alpha = 0.5f) else StudioSurfaceVariant,
                labelColor = if (isHw) StudioPrimary else StudioOnSurfaceVariant
            ),
            modifier = Modifier.height(24.dp)
        )
    }
}
