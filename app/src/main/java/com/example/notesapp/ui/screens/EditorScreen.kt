package com.example.notesapp.ui.screens

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
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
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AlarmOn
import androidx.compose.material.icons.filled.AlarmOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.example.notesapp.NotesApplication
import com.example.notesapp.data.DocumentBlock
import com.example.notesapp.data.Note
import com.example.notesapp.data.RichDocumentCodec
import com.example.notesapp.storage.NoteImageStore
import com.example.notesapp.ui.components.ColorPicker
import com.example.notesapp.ui.components.RichDocumentEditor
import com.example.notesapp.ui.components.RichDocumentSaver
import com.example.notesapp.ui.theme.liquidGlassSurface
import com.example.notesapp.ui.theme.noteCardColors
import com.example.notesapp.ui.viewmodel.NotesViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 编辑页底部浮动颜色条的总占位高度。
 * 颜色条实际高度 = 内容 48dp(IconButton) + 内边距 10dp*2 + 外边距 10dp*2 = 88dp，
 * 再额外预留 8dp 视觉间隔，确保正文最后一行不被颜色条遮挡。
 */
private val ColorBarReservedHeight = 96.dp

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
    // 图文混排文档：文字块与图片块按顺序排列。
    // 用 RichDocumentSaver 序列化保存，旋转屏幕后图片位置与尺寸不会丢。
    var blocks by rememberSaveable(stateSaver = RichDocumentSaver) {
        mutableStateOf(listOf<DocumentBlock>(DocumentBlock.text("")))
    }
    // 纯文本版本（搜索 / 分享 / 首页预览用），由块列表派生，始终与图文内容一致
    val content = remember(blocks) { RichDocumentCodec.plainText(blocks) }
    var selectedColor by rememberSaveable { mutableStateOf(0) }
    var isPinned by rememberSaveable { mutableStateOf(false) }
    var noteLoaded by rememberSaveable { mutableStateOf(false) }

    // 提醒相关状态（全部 rememberSaveable，旋转屏后保留）
    var reminderAt by rememberSaveable { mutableStateOf(0L) }
    var showTimePicker by rememberSaveable { mutableStateOf(false) }
    var pickedYear by rememberSaveable { mutableStateOf(0) }
    var pickedMonth by rememberSaveable { mutableStateOf(0) }
    var pickedDay by rememberSaveable { mutableStateOf(0) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val imageStore = remember(context) {
        (context.applicationContext as NotesApplication).imageStore
    }

    // 插入位置：点击「在此处插入图片」时记录目标下标，选图返回后按该下标插入。
    // 用 rememberSaveable：系统选图器可能触发 Activity 重建（转屏、内存回收），
    // 下标若丢失会导致选完图后无处可插。
    var pendingInsertIndex by rememberSaveable { mutableStateOf(-1) }

    // 需要自动聚焦的文字块 id：新插入的文字段落要能立刻打字
    var focusBlockId by rememberSaveable { mutableStateOf<String?>(null) }

    val pickImageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        val insertAt = pendingInsertIndex
        pendingInsertIndex = -1
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val imageBlock = imageStore.importImage(uri)
            if (imageBlock == null) {
                Toast.makeText(
                    context,
                    context.getString(R.string.image_import_failed),
                    Toast.LENGTH_SHORT
                ).show()
                return@launch
            }
            // 下标失效（-1，或期间文档被改动）时退化为追加到末尾，
            // 而不是默默丢弃用户刚选好的图片
            val target = if (insertAt in 0..blocks.size) insertAt else blocks.size
            blocks = blocks.toMutableList().also { it.add(target, imageBlock) }
        }
    }

    fun requestInsertImage(index: Int) {
        pendingInsertIndex = index
        pickImageLauncher.launch(arrayOf("image/*"))
    }

    /**
     * 在指定位置插入一个空文字块并聚焦。
     * 没有这个入口，结构只能是「一段文字 + 若干图片」，
     * 图片下方再也写不了字，图文混排就不成立。
     */
    fun insertTextAt(index: Int) {
        val newBlock = DocumentBlock.text("")
        val target = index.coerceIn(0, blocks.size)
        blocks = blocks.toMutableList().also { it.add(target, newBlock) }
        focusBlockId = newBlock.id
    }

    // 退出标志：返回触发后停止自动保存，避免与 saveAndExit 竞态导致重复保存
    var isExiting by rememberSaveable { mutableStateOf(false) }

    // 日期选择器触发计数器：每次需要打开日期选择器时自增，确保 LaunchedEffect 重新触发
    var datePickerTrigger by rememberSaveable { mutableStateOf(0) }

    // 通知权限请求（Android 13+）
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            // 权限通过，触发日期选择器
            datePickerTrigger++
        } else {
            Toast.makeText(
                context,
                context.getString(R.string.notification_permission_denied),
                Toast.LENGTH_LONG
            ).show()
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
        datePickerTrigger++
    }

    // 日期选择器（原生 DatePickerDialog），由 datePickerTrigger 计数器驱动，每次自增都重新弹出
    LaunchedEffect(datePickerTrigger) {
        if (datePickerTrigger == 0) return@LaunchedEffect
        val cal = Calendar.getInstance()
        android.app.DatePickerDialog(
            context,
            { _, year, month, day ->
                pickedYear = year
                pickedMonth = month
                pickedDay = day
                showTimePicker = true
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).apply {
            datePicker.minDate = System.currentTimeMillis()
        }.show()
    }

    // key 含 existingNote：冷启动经通知深链直达编辑页时，allActiveNotes 首帧为空，
    // existingNote 为 null；待 DB 发射、existingNote 变非空后 effect 需重跑加载
    LaunchedEffect(noteId, existingNote) {
        if (!noteLoaded && existingNote != null) {
            existingNote.let {
                title = it.title
                // 旧笔记 richContent 为空，会退回为「单段纯文本」，因此历史内容不丢
                blocks = RichDocumentCodec.decode(it.richContent, it.content)
                selectedColor = it.color
                isPinned = it.isPinned
                reminderAt = it.reminderAt
                noteLoaded = true
            }
        }
    }

    val focusRequester = remember { FocusRequester() }
    // 新建笔记时自动聚焦正文（交给编辑器按 focusBlockId 挂载并聚焦）
    LaunchedEffect(Unit) {
        if (noteId == 0L) {
            focusBlockId = blocks.firstOrNull()?.id
        }
    }

    fun buildNote(): Note {
        // 新建笔记时按首页当前选中的类型（备忘录/代办）创建
        // 即使 existingNote 在编辑期间被外部回收，也保留 noteId，避免退化为新建
        val base = existingNote ?: Note(id = noteId, type = currentType)
        return base.copy(
            title = title.trim(),
            content = content,
            richContent = RichDocumentCodec.encode(blocks),
            color = selectedColor,
            isPinned = isPinned,
            type = base.type,
            reminderAt = reminderAt,
            updatedAt = System.currentTimeMillis()
        )
    }

    /** 笔记是否有内容：纯文本非空，或至少含一张图片。 */
    fun hasContent(note: Note): Boolean =
        note.title.isNotBlank() || note.content.isNotBlank() ||
            blocks.any { it is DocumentBlock.Image }

    fun saveAndExit() {
        if (isExiting) return  // 防止重复触发
        isExiting = true
        val note = buildNote()
        if (hasContent(note)) {
            viewModel.saveNote(note) { onBack() }
        } else {
            onBack()
        }
    }

    BackHandler { saveAndExit() }

    // 自动保存：仅在编辑现有笔记且未退出时触发，避免与返回保存重复。
    // 以 blocks 为 key 触发，因此插入图片、拖拽改尺寸同样会触发自动保存。
    //
    // 必须带 noteLoaded：冷启动/深链进入时 DB 首帧是空的，existingNote 尚为 null，
    // 此时若抢先保存会把空白内容写入（并覆盖）已有笔记。
    LaunchedEffect(title, blocks, selectedColor, isPinned, reminderAt) {
        if (noteId != 0L && !isExiting && noteLoaded) {
            delay(1500)
            val note = buildNote()
            if (hasContent(note)) {
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
                            // 先置退出标志：否则这次删除会与挂起的自动保存（1500ms 后写入）
                            // 竞态，笔记可能被重新写回而「复活」。
                            isExiting = true
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
                        .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 6.dp),
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

                // 提醒状态显示行（移到 Scaffold 内部，紧跟标题下方，避免重叠 TopAppBar）
                if (reminderAt > 0L) {
                    val reminderText = SimpleDateFormat("MM月dd日 HH:mm", Locale.getDefault())
                        .format(Date(reminderAt))
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

                RichDocumentEditor(
                    blocks = blocks,
                    onBlocksChange = { blocks = it },
                    imageStore = imageStore,
                    onInsertImage = { index -> requestInsertImage(index) },
                    onInsertText = { index -> insertTextAt(index) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 8.dp),
                    textColor = MaterialTheme.colorScheme.onBackground,
                    cursorColor = MaterialTheme.colorScheme.primary,
                    hintText = stringResource(R.string.content_hint),
                    focusBlockId = focusBlockId,
                    focusRequester = focusRequester
                )
                // 为浮动颜色条预留空间，避免最后一行被遮挡。
                // 颜色条实际高度 = 内容 48dp(IconButton) + 内边距 10dp*2 + 外边距 10dp*2 = 88dp，
                // 这里再多留 8dp 视觉间隔，避免正文最后一行紧贴颜色条。
                Spacer(modifier = Modifier.height(ColorBarReservedHeight))
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
                    // 在正文末尾插入图片；也可点击正文中块与块之间的入口插入到指定位置
                    IconButton(onClick = { requestInsertImage(blocks.size) }) {
                        Icon(
                            imageVector = Icons.Default.AddPhotoAlternate,
                            contentDescription = stringResource(R.string.insert_image),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
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
                }
            }
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
                    } else {
                        Toast.makeText(
                            context,
                            context.getString(R.string.reminder_in_past),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    showTimePicker = false
                }) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}
