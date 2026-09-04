package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.data.ProjectEntity
import com.example.core.data.ProjectRepository
import com.example.core.model.AspectRatio
import com.example.core.model.ProjectDocument
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    repository: ProjectRepository,
    onOpenProject: (String) -> Unit,
    onNavigateDiagnostics: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val projects by repository.allProjects.collectAsStateWithLifecycle(initialValue = emptyList())
    val exportJobs by repository.allExportJobs.collectAsStateWithLifecycle(initialValue = emptyList())

    var recoveredProject by remember { mutableStateOf<ProjectDocument?>(null) }
    var showNewProjectDialog by remember { mutableStateOf(false) }

    // Check for crash recovery snapshot
    LaunchedEffect(Unit) {
        val recovery = repository.loadRecoveryProject()
        if (recovery != null) {
            recoveredProject = recovery
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_studio_logo),
                            contentDescription = "Studio Logo",
                            modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp))
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("Ahmed Reaction Studio", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = StudioOnSurface)
                            Text("Native Video & PiP Reaction Suite", style = MaterialTheme.typography.labelSmall, color = StudioPrimary)
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = onNavigateDiagnostics,
                        modifier = Modifier.testTag("home_diagnostics_button")
                    ) {
                        Icon(Icons.Default.Speed, contentDescription = "Hardware Diagnostics", tint = StudioPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = StudioSurface)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showNewProjectDialog = true },
                containerColor = StudioPrimary,
                contentColor = StudioOnPrimary,
                modifier = Modifier.testTag("create_project_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "New Project")
            }
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
            // Crash Recovery Alert Banner (Specification Requirement: AC-20 & Section 38)
            recoveredProject?.let { recovery ->
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = StudioSecondaryContainer),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().testTag("recovery_banner")
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Restore, contentDescription = null, tint = StudioSecondary, modifier = Modifier.size(32.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Unfinished Session Recovered", fontWeight = FontWeight.Bold, color = StudioOnSecondaryContainer, fontSize = 14.sp)
                                Text("Last active project: ${recovery.name}", fontSize = 12.sp, color = StudioOnSecondaryContainer.copy(alpha = 0.8f))
                            }
                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        repository.saveProject(recovery)
                                        recoveredProject = null
                                        onOpenProject(recovery.id)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = StudioSecondary),
                                modifier = Modifier.testTag("restore_session_button")
                            ) {
                                Text("Restore", color = StudioOnSecondary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // Quick Create Aspect Ratio Presets
            item {
                Text("Start New Reaction Project", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = StudioOnSurface)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    QuickAspectCard(
                        title = "16:9 Landscape",
                        subtitle = "Gaming & YouTube",
                        aspect = AspectRatio.LANDSCAPE_16_9,
                        modifier = Modifier.weight(1f)
                    ) {
                        coroutineScope.launch {
                            val p = repository.createNewProject("Landscape Reaction", AspectRatio.LANDSCAPE_16_9)
                            onOpenProject(p.id)
                        }
                    }

                    QuickAspectCard(
                        title = "9:16 Portrait",
                        subtitle = "Shorts & Reels",
                        aspect = AspectRatio.PORTRAIT_9_16,
                        modifier = Modifier.weight(1f)
                    ) {
                        coroutineScope.launch {
                            val p = repository.createNewProject("Vertical Reaction", AspectRatio.PORTRAIT_9_16)
                            onOpenProject(p.id)
                        }
                    }

                    QuickAspectCard(
                        title = "1:1 Square",
                        subtitle = "Social Feed",
                        aspect = AspectRatio.SQUARE_1_1,
                        modifier = Modifier.weight(1f)
                    ) {
                        coroutineScope.launch {
                            val p = repository.createNewProject("Square Reaction", AspectRatio.SQUARE_1_1)
                            onOpenProject(p.id)
                        }
                    }
                }
            }

            // Projects List Section
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Saved Projects (${projects.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = StudioOnSurface)
                    if (projects.isNotEmpty()) {
                        Text("Touch to open studio", fontSize = 12.sp, color = StudioOnSurfaceVariant)
                    }
                }
            }

            if (projects.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = StudioSurface),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.MovieFilter, contentDescription = null, tint = StudioPrimary, modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(12.dp))
                            Text("No Projects Yet", fontWeight = FontWeight.Bold, color = StudioOnSurface, fontSize = 16.sp)
                            Text("Tap a format above or '+' to launch the studio editor", fontSize = 13.sp, color = StudioOnSurfaceVariant)
                        }
                    }
                }
            } else {
                items(projects, key = { it.id }) { proj ->
                    ProjectCardItem(
                        project = proj,
                        onOpen = { onOpenProject(proj.id) },
                        onDelete = {
                            coroutineScope.launch {
                                repository.deleteProject(proj.id)
                            }
                        }
                    )
                }
            }

            // Recent Exports Section
            if (exportJobs.isNotEmpty()) {
                item {
                    Text("Recent Render Jobs", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = StudioOnSurface)
                }
                items(exportJobs.take(3)) { job ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = StudioSurfaceVariant.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(job.projectName, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = StudioOnSurface)
                                Text("${job.codec} â€¢ ${job.resolution} â€¢ ${job.status}", fontSize = 11.sp, color = StudioOnSurfaceVariant)
                            }
                            AssistChip(
                                onClick = {},
                                label = { Text(job.status, fontSize = 10.sp) },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = if (job.status == "COMPLETED") StudioPrimaryContainer else StudioSurfaceVariant,
                                    labelColor = if (job.status == "COMPLETED") StudioPrimary else StudioOnSurfaceVariant
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    // New Project Custom Modal Dialog
    if (showNewProjectDialog) {
        var projectName by remember { mutableStateOf("") }
        var selectedAspect by remember { mutableStateOf(AspectRatio.LANDSCAPE_16_9) }

        AlertDialog(
            onDismissRequest = { showNewProjectDialog = false },
            containerColor = StudioSurface,
            title = { Text("Create New Reaction Project", fontWeight = FontWeight.Bold, color = StudioOnSurface) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = projectName,
                        onValueChange = { projectName = it },
                        label = { Text("Project Name") },
                        placeholder = { Text("e.g. Gaming Reaction #1") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("project_name_input")
                    )

                    Text("Canvas Orientation & Aspect Ratio", style = MaterialTheme.typography.labelMedium, color = StudioPrimary)
                    AspectRatio.values().forEach { aspect ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { selectedAspect = aspect }
                                .background(if (selectedAspect == aspect) StudioPrimaryContainer.copy(alpha = 0.4f) else Color.Transparent)
                            .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedAspect == aspect,
                                onClick = { selectedAspect = aspect },
                                colors = RadioButtonDefaults.colors(selectedColor = StudioPrimary)
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(aspect.displayName, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = StudioOnSurface)
                                Text("${aspect.defaultWidth}x${aspect.defaultHeight} Native Canvas", fontSize = 11.sp, color = StudioOnSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            val p = repository.createNewProject(projectName, selectedAspect)
                            showNewProjectDialog = false
                            onOpenProject(p.id)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StudioPrimary),
                    modifier = Modifier.testTag("dialog_create_project_confirm")
                ) {
                    Text("Create & Open Studio", color = StudioOnPrimary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewProjectDialog = false }) {
                    Text("Cancel", color = StudioOnSurfaceVariant)
                }
            }
        )
    }
}

@Composable
fun QuickAspectCard(
    title: String,
    subtitle: String,
    aspect: AspectRatio,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = StudioSurface),
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(StudioPrimaryContainer.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    when (aspect) {
                        AspectRatio.LANDSCAPE_16_9 -> Icons.Default.StayCurrentLandscape
                        AspectRatio.PORTRAIT_9_16 -> Icons.Default.StayCurrentPortrait
                        AspectRatio.SQUARE_1_1 -> Icons.Default.CropSquare
                    },
                    contentDescription = null,
                    tint = StudioPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(title, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = StudioOnSurface, maxLines = 1)
            Text(subtitle, fontSize = 9.sp, color = StudioOnSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
fun ProjectCardItem(
    project: ProjectEntity,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        onClick = onOpen,
        colors = CardDefaults.cardColors(containerColor = StudioSurface),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().testTag("project_item_${project.id}")
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Visual aspect icon thumbnail
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Brush.linearGradient(listOf(StudioPrimaryContainer, StudioSurfaceVariant))),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    when (project.aspectRatio) {
                        AspectRatio.PORTRAIT_9_16.name -> Icons.Default.StayCurrentPortrait
                        AspectRatio.SQUARE_1_1.name -> Icons.Default.CropSquare
                        else -> Icons.Default.StayCurrentLandscape
                    },
                    contentDescription = null,
                    tint = StudioPrimary
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(project.name, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = StudioOnSurface)
                Spacer(Modifier.height(2.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    AssistChip(
                        onClick = {},
                        label = { Text(project.aspectRatio.replace("_", " "), fontSize = 9.sp) },
                        modifier = Modifier.height(22.dp)
                    )
                    Text("${project.width}x${project.height}", fontSize = 11.sp, color = StudioOnSurfaceVariant)
                    Text("â€¢", fontSize = 11.sp, color = StudioOutline)
                    Text("${project.layerCount} Layers", fontSize = 11.sp, color = StudioSecondary)
                }
            }

            IconButton(onClick = onDelete) {
                Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = StudioOnSurfaceVariant)
            }
        }
    }
}
