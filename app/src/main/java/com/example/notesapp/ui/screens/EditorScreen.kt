package com.example.notesapp.ui.screens

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AlarmOn
import androidx.compose.material.icons.filled.AlarmOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.notesapp.R
import com.example.notesapp.data.Note
import com.example.notesapp.ui.components.ColorPicker
import com.example.notesapp.ui.theme.liquidGlassSurface
import com.example.notesapp.ui.theme.noteCardColors
import com.example.notesapp.ui.viewmodel.NotesViewModel
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    viewModel: NotesViewModel,
    noteId: Long,
    onBack: () -> Unit
) {
    val notes by viewModel.allActiveNotes.collectAsStateWithLifecycle()
    val currentType by viewModel.noteType.collectAsStateWithLifecycle()
    val existingNote = remember(noteId, notes) {
        notes.find { it.id == noteId }
    }

    var title by rememberSaveable { mutableStateOf("") }
    var content by rememberSaveable { mutableStateOf("") }
    var selectedColor by rememberSaveable { mutableStateOf(0) }
    var isPinned by rememberSaveable { mutableStateOf(false) }
    var noteLoaded by rememberSaveable { mutableStateOf(false) }
    var showMarkdownHelp by remember { mutableStateOf(false) }
    val markdownHelpSheetState = rememberModalBottomSheetState()

    // 提醒相关状态
    var reminderAt by rememberSaveable { mutableStateOf(0L) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var pickedYear by remember { mutableStateOf(0) }
    var pickedMonth by remember { mutableStateOf(0) }
    var pickedDay by remember { mutableStateOf(0) }
    val context = LocalContext.current

    // 通知权限请求（Android 13+）
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            // 权限通过，继续弹出日期选择
            showDatePicker = true
        }
    }

    fun requestNotificationPermissionAndOpenPicker() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        showDatePicker = true
    }

    LaunchedEffect(existingNote) {
        if (!noteLoaded) {
            existingNote?.let {
                title = it.title
                content = it.content
                selectedColor = it.color
                isPinned = it.isPinned
                reminderAt = it.reminderAt
                noteLoaded = true
            }
        }
    }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(80)
        focusRequester.requestFocus()
    }

    fun buildNote(): Note {
        // 新建笔记时按首页当前选中的类型（备忘录/代办）创建
        val base = existingNote ?: Note(type = currentType)
        return base.copy(
            title = title.trim(),
            content = content,
            color = selectedColor,
            isPinned = isPinned,
            type = base.type,
            reminderAt = reminderAt,
            updatedAt = System.currentTimeMillis()
        )
    }

    fun saveAndExit() {
        val note = buildNote()
        if (note.title.isNotBlank() || note.content.isNotBlank()) {
            viewModel.saveNote(note) { onBack() }
        } else {
            onBack()
        }
    }

    BackHandler { saveAndExit() }

    LaunchedEffect(title, content, selectedColor, isPinned, reminderAt) {
        if (noteId != 0L) {
            delay(1500)
            val note = buildNote()
            if (note.title.isNotBlank() || note.content.isNotBlank()) {
                viewModel.saveNote(note) {}
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(if (noteId == 0L) R.string.new_note else R.string.edit_note),
                        style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Medium)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { saveAndExit() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { isPinned = !isPinned }) {
                        Icon(
                            imageVector = Icons.Default.PushPin,
                            contentDescription = stringResource(R.string.pin),
                            tint = if (isPinned) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (noteId != 0L) {
                        IconButton(onClick = {
                            existingNote?.let { viewModel.deleteNote(it) }
                            onBack()
                        }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = stringResource(R.string.delete),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Text(
                        text = stringResource(R.string.word_count, content.length + title.length),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        // 依据当前配色判断明暗，供液态玻璃材质使用（兼容强制主题）
        val bgColor = MaterialTheme.colorScheme.background
        val isDark =
            (0.299f * bgColor.red + 0.587f * bgColor.green + 0.114f * bgColor.blue) < 0.5f

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
            ) {
                BasicTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 6.dp)
                        .focusRequester(focusRequester),
                    textStyle = TextStyle(
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    ),
                    singleLine = true,
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    decorationBox = { innerTextField ->
                        if (title.isEmpty()) {
                            Text(
                                text = stringResource(R.string.title_hint),
                                style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        innerTextField()
                    }
                )
                BasicTextField(
                    value = content,
                    onValueChange = { content = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 8.dp),
                    textStyle = TextStyle(
                        fontSize = 16.sp,
                        lineHeight = 24.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    decorationBox = { innerTextField ->
                        if (content.isEmpty()) {
                            Text(
                                text = stringResource(R.string.content_hint),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        innerTextField()
                    }
                )
                // 为浮动颜色条预留空间，避免最后一行被遮挡
                Spacer(modifier = Modifier.height(84.dp))
            }

            // 浮动颜色调节条：通过 imePadding 跟随键盘自动浮起
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .imePadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .shadow(
                        elevation = 10.dp,
                        shape = RoundedCornerShape(28.dp),
                        ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                        spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
                    )
                    .background(
                        MaterialTheme.colorScheme.surface,
                        RoundedCornerShape(28.dp)
                    )
                    .liquidGlassSurface(
                        shape = RoundedCornerShape(28.dp),
                        isDark = isDark,
                        borderWidth = 1.dp
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ColorPicker(
                        selectedColor = noteCardColors.find { it.toArgb() == selectedColor }
                            ?: noteCardColors.first(),
                        onColorSelected = { selectedColor = it.toArgb() },
                        modifier = Modifier.weight(1f)
                    )
                    // 提醒闹钟按钮
                    IconButton(onClick = {
                        if (reminderAt > 0L) {
                            // 已有提醒，再次点击取消
                            reminderAt = 0L
                        } else {
                            requestNotificationPermissionAndOpenPicker()
                        }
                    }) {
                        Icon(
                            imageVector = if (reminderAt > 0L) Icons.Default.AlarmOn
                            else Icons.Default.AlarmOff,
                            contentDescription = stringResource(R.string.reminder),
                            tint = if (reminderAt > 0L) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { showMarkdownHelp = true }) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Markdown 语法",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    // 提醒时间显示文本
    val reminderText = if (reminderAt > 0L) {
        SimpleDateFormat("MM月dd日 HH:mm", Locale.getDefault()).format(Date(reminderAt))
    } else null

    // 日期选择器（使用原生 DatePickerDialog）
    if (showDatePicker) {
        LaunchedEffect(showDatePicker) {
            val cal = Calendar.getInstance()
            android.app.DatePickerDialog(
                context,
                { _, year, month, day ->
                    pickedYear = year
                    pickedMonth = month
                    pickedDay = day
                    showDatePicker = false
                    showTimePicker = true
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
            ).apply {
                datePicker.minDate = System.currentTimeMillis()
                setOnCancelListener { showDatePicker = false }
            }.show()
        }
    }

    // 时间选择器 Dialog（Compose Material3 TimePicker）
    if (showTimePicker) {
        val timeState = rememberTimePickerState(
            initialHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
            initialMinute = Calendar.getInstance().get(Calendar.MINUTE),
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text(stringResource(R.string.set_reminder)) },
            text = {
                Box(contentAlignment = Alignment.Center) {
                    TimePicker(state = timeState)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val c = Calendar.getInstance()
                    c.set(pickedYear, pickedMonth, pickedDay, timeState.hour, timeState.minute, 0)
                    c.set(Calendar.MILLISECOND, 0)
                    val target = c.timeInMillis
                    if (target > System.currentTimeMillis()) {
                        reminderAt = target
                    }
                    showTimePicker = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // 提醒状态显示行（当有提醒时在标题下方显示）
    if (reminderAt > 0L) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.AlarmOn,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = 8.dp)
            )
            Text(
                text = "提醒：$reminderText",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "取消",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.clickable { reminderAt = 0L }
            )
        }
    }

    // Markdown 语法提示弹窗
    if (showMarkdownHelp) {
        ModalBottomSheet(
            onDismissRequest = { showMarkdownHelp = false },
            sheetState = markdownHelpSheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .padding(bottom = 24.dp)
            ) {
                Text(
                    text = "Markdown 语法速查",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                MarkdownHelpItem("# 标题", "一级标题")
                MarkdownHelpItem("## 小标题", "二级标题")
                MarkdownHelpItem("- 列表项", "无序列表")
                MarkdownHelpItem("1. 列表项", "有序列表")
                MarkdownHelpItem("**加粗文本**", "加粗显示")
                MarkdownHelpItem("*斜体文本*", "斜体显示")

                Spacer(modifier = Modifier.padding(top = 16.dp))
                Text(
                    text = "提示：在首页卡片中会自动渲染这些语法。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun MarkdownHelpItem(syntax: String, description: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Text(
            text = syntax,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
