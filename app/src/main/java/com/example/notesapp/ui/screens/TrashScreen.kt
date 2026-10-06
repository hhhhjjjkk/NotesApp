package com.example.notesapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.notesapp.R
import com.example.notesapp.data.Note
import com.example.notesapp.data.ThemeMode
import com.example.notesapp.ui.screens.trash.TrashItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(
    viewModel: com.example.notesapp.ui.viewmodel.NotesViewModel,
    settingsViewModel: com.example.notesapp.ui.viewmodel.SettingsViewModel,
    onBack: () -> Unit
) {
    val trashedNotes by viewModel.trashedNotes.collectAsStateWithLifecycle()
    val themeMode by settingsViewModel.themeMode.collectAsStateWithLifecycle()
    val isSystemDark = isSystemInDarkTheme()
    val isDark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemDark
    }

    var showClearDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf<Note?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.trash),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Medium)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null
                        )
                    }
                },
                actions = {
                    if (trashedNotes.isNotEmpty()) {
                        TextButton(onClick = { showClearDialog = true }) {
                            Text(text = stringResource(R.string.trash_clear_all))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        if (trashedNotes.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.trash_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 16.dp,
                    vertical = 8.dp
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(trashedNotes, key = { it.id }) { note ->
                    TrashItem(
                        note = note,
                        isDark = isDark,
                        onRestore = { viewModel.restoreFromTrash(note) },
                        onDelete = { showDeleteDialog = note }
                    )
                }
            }
        }
    }

    // 清空回收站确认
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(stringResource(R.string.trash_clear_title)) },
            text = { Text(stringResource(R.string.trash_clear_message)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearTrashed()
                    showClearDialog = false
                }) {
                    Text(
                        stringResource(R.string.trash_clear_all),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // 单条永久删除确认
    showDeleteDialog?.let { note ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text(stringResource(R.string.trash_delete_title)) },
            text = { Text(stringResource(R.string.trash_delete_message)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.permanentlyDelete(note)
                    showDeleteDialog = null
                }) {
                    Text(
                        stringResource(R.string.delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}
