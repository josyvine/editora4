package com.vineyard.aivideostudio.ui.screens.projects

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vineyard.aivideostudio.core.model.PipelineStatus
import com.vineyard.aivideostudio.core.result.AppResult
import com.vineyard.aivideostudio.core.util.StorageUtils
import com.vineyard.aivideostudio.core.util.TimeUtils
import com.vineyard.aivideostudio.ui.components.AppTopBar
import com.vineyard.aivideostudio.ui.components.ConfirmationDialog
import com.vineyard.aivideostudio.ui.components.StatusChip
import com.vineyard.aivideostudio.ui.theme.BorderSubtle
import com.vineyard.aivideostudio.ui.theme.RoseError
import com.vineyard.aivideostudio.ui.theme.StudioDarkBg
import com.vineyard.aivideostudio.ui.theme.StudioSurface
import com.vineyard.aivideostudio.ui.theme.StudioSurfaceElevated
import com.vineyard.aivideostudio.ui.theme.TextPrimary
import com.vineyard.aivideostudio.ui.theme.TextSecondary
import com.vineyard.aivideostudio.ui.theme.TextTertiary
import com.vineyard.aivideostudio.ui.theme.VioletAccent
import java.io.File

@Composable
fun ProjectsScreen(
    viewModel: ProjectsViewModel,
    onOpenProject: (String) -> Unit
) {
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var projectToDelete by remember { mutableStateOf<String?>(null) }

    if (projectToDelete != null) {
        ConfirmationDialog(
            title = "Delete Project",
            message = "Are you sure you want to permanently delete this project and all its intermediate render files?",
            confirmText = "Delete",
            onConfirm = {
                projectToDelete?.let { viewModel.deleteProject(it) }
                projectToDelete = null
            },
            onDismiss = { projectToDelete = null }
        )
    }

    Scaffold(
        topBar = {
            AppTopBar(title = "Projects", subtitle = "${projects.size} studio projects")
        },
        containerColor = StudioDarkBg
    ) { padding ->
        if (projects.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Filled.FolderOpen,
                        contentDescription = null,
                        tint = TextTertiary,
                        modifier = Modifier.size(54.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No projects yet",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary
                    )
                    Text(
                        text = "Import a video from the Create tab to start",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item { Spacer(modifier = Modifier.height(4.dp)) }
                items(projects, key = { it.id }) { project ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenProject(project.id) }
                            .testTag("project_item_${project.id}"),
                        colors = CardDefaults.cardColors(containerColor = StudioSurface),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Filled.Movie,
                                        contentDescription = null,
                                        tint = VioletAccent,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = project.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = TextPrimary
                                        )
                                        Text(
                                            text = TimeUtils.formatTimestamp(project.updatedAt),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextTertiary
                                        )
                                    }
                                }
                                StatusChip(status = project.status)
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Duration: ${TimeUtils.formatDurationShort(project.metadata.durationSeconds)} • ${project.targetAspectRatio}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary
                                )

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (project.status == PipelineStatus.COMPLETED && project.finalVideoUri != null) {
                                        // 1. One-Tap Save to Gallery Button
                                        IconButton(
                                            onClick = {
                                                val finalUriStr = project.finalVideoUri ?: project.currentVideoUri
                                                val file = if (finalUriStr.startsWith("file://")) {
                                                    File(Uri.parse(finalUriStr).path ?: "")
                                                } else {
                                                    File(finalUriStr)
                                                }

                                                if (file.exists() && file.length() > 0L) {
                                                    val cleanName = project.name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
                                                    val result = StorageUtils.saveVideoToGallery(
                                                        context = context,
                                                        sourceFile = file,
                                                        displayName = "$cleanName.mp4"
                                                    )
                                                    when (result) {
                                                        is AppResult.Success -> {
                                                            Toast.makeText(
                                                                context,
                                                                "Saved to Gallery (Movies/Editora)",
                                                                Toast.LENGTH_SHORT
                                                            ).show()
                                                        }
                                                        is AppResult.Error -> {
                                                            Toast.makeText(
                                                                context,
                                                                "Export failed: ${result.error.message}",
                                                                Toast.LENGTH_LONG
                                                            ).show()
                                                        }
                                                    }
                                                } else {
                                                    Toast.makeText(
                                                        context,
                                                        "Video file not found in storage",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Download,
                                                contentDescription = "Save to Gallery",
                                                tint = VioletAccent
                                            )
                                        }

                                        // 2. Share Video Button
                                        IconButton(
                                            onClick = {
                                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                    type = "video/mp4"
                                                    putExtra(Intent.EXTRA_STREAM, Uri.parse(project.finalVideoUri))
                                                }
                                                context.startActivity(Intent.createChooser(shareIntent, "Share Video"))
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Share,
                                                contentDescription = "Share final video",
                                                tint = TextPrimary
                                            )
                                        }
                                    }

                                    // 3. Delete Project Button
                                    IconButton(
                                        onClick = { projectToDelete = project.id }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Delete,
                                            contentDescription = "Delete project",
                                            tint = RoseError.copy(alpha = 0.8f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                item { Spacer(modifier = Modifier.height(24.dp)) }
            }
        }
    }
}