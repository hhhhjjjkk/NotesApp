package com.example.notesapp.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.notesapp.R
import com.example.notesapp.data.DocumentBlock
import com.example.notesapp.data.RichDocumentCodec
import com.example.notesapp.storage.NoteImageStore
import kotlinx.coroutines.delay

/**
 * [DocumentBlock] 列表的 rememberSaveable 保存器。
 * 复用 [RichDocumentCodec] 的字符串格式，旋转屏幕后图文内容不丢。
 */
val RichDocumentSaver: Saver<List<DocumentBlock>, String> = Saver(
    save = { RichDocumentCodec.encode(it) },
    restore = { RichDocumentCodec.decode(it) }
)

/** 图片加载状态。区分「加载中」与「失败」，避免缺图时永远显示"加载中"。 */
sealed interface NoteImageState {
    data object Loading : NoteImageState
    data class Ready(val bitmap: Bitmap) : NoteImageState
    data object Failed : NoteImageState
}

/**
 * 图文混排编辑器：文字块与图片块按顺序纵向排列。
 *
 * 插入交互（刻意不设块间按钮行）：
 * - 文字：点击任意图片块，就会紧随其后插入一个文字块并聚焦，直接继续输入；
 * - 图片：由编辑页底部工具栏的插图按钮负责，插入后自动补一个文字块并聚焦。
 * 这样图片下方永远可以直接写字，不需要先去点某个小按钮。
 *
 * 图片块右下角有拖拽手柄，按住拖动即可缩放；因为始终按 [DocumentBlock.Image.aspectRatio]
 * 保持原始宽高比，所以拖拽只改变宽度，高度随之等比变化，不会把图拉变形。
 *
 * @param onAddTextAfterImage 在指定图片块之后插入文字块（由调用方负责生成 id 并聚焦）
 * @param focusBlockId 需要自动聚焦的文字块 id
 */
@Composable
fun RichDocumentEditor(
    blocks: List<DocumentBlock>,
    onBlocksChange: (List<DocumentBlock>) -> Unit,
    imageStore: NoteImageStore,
    onAddTextAfterImage: (imageId: String) -> Unit,
    onInsertImageBefore: (imageId: String) -> Unit,
    modifier: Modifier = Modifier,
    textColor: Color,
    cursorColor: Color,
    hintText: String,
    focusBlockId: String? = null,
    focusRequester: FocusRequester? = null,
    accentColor: Color = MaterialTheme.colorScheme.primary
) {
    val density = LocalDensity.current

    // blocks 是组合期的快照参数。所有写处理器都通过它派生新列表，
    // 若同一帧内发生两次写（例如一边打字一边拖拽图片，多指或桌面模式），
    // 后一次写会基于旧快照覆盖前一次，丢掉刚输入的内容。
    // 用 rememberUpdatedState 保证处理器在「被调用时」读到的是最新值，
    // 而不是当前这次组合传入的快照。
    val currentBlocks by rememberUpdatedState(blocks)

    BoxWithConstraints(modifier = modifier) {
        // 可用宽度（px）用于把拖拽位移换算成宽度比例
        val availableWidthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)

        // 新插入或首次进入时自动聚焦目标文字块。
        // 这里延迟一帧，确保 focusRequester 已经挂到该块上。
        LaunchedEffect(focusBlockId) {
            if (focusBlockId != null && focusRequester != null) {
                delay(60)
                runCatching { focusRequester.requestFocus() }
            }
        }

        // LazyColumn：只有进入可视区域的块才参与组合与位图解码，
        // 图片很多时不会再把全部 Bitmap 同时驻留内存（此前是普通 Column，有 OOM 风险）。
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            // 底部留出空间，避免最后一块内容被编辑页的浮动颜色条遮挡
            contentPadding = PaddingValues(bottom = 96.dp)
        ) {
            items(blocks, key = { it.id }) { block ->
                // 用 id 作为 key：否则在中间插入块时后续槽位会被复用，
                // 导致图片重新解码闪「加载中」、输入框的选区/输入法状态被邻块继承
                    when (block) {
                        is DocumentBlock.Text -> {
                            BasicTextField(
                                value = block.text,
                                onValueChange = { newText ->
                                    // 按 id 定位，避免下标漂移改错块；从 currentBlocks 读最新列表
                                    onBlocksChange(
                                        currentBlocks.map {
                                            if (it.id == block.id) block.copy(text = newText) else it
                                        }
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .then(
                                        if (block.id == focusBlockId && focusRequester != null) {
                                            Modifier.focusRequester(focusRequester)
                                        } else {
                                            Modifier
                                        }
                                    ),
                                textStyle = TextStyle(
                                    fontSize = 16.sp,
                                    lineHeight = 24.sp,
                                    color = textColor
                                ),
                                cursorBrush = SolidColor(cursorColor),
                                decorationBox = { innerTextField ->
                                    // 仅当全文还没有任何内容时显示占位提示，避免每个空块都提示
                                    if (block.text.isEmpty() && blocks.size == 1) {
                                        Text(
                                            text = hintText,
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    innerTextField()
                                }
                            )
                        }

                        is DocumentBlock.Image -> {
                            EditableImageBlock(
                                block = block,
                                imageStore = imageStore,
                                availableWidthPx = availableWidthPx,
                                accentColor = accentColor,
                                textColor = textColor,
                                // 点图片本身：在其后插入文字块并聚焦，直接继续写字
                                onClick = { onAddTextAfterImage(block.id) },
                                onLongPress = { onInsertImageBefore(block.id) },
                                onWidthFractionChange = { newFraction ->
                                    // 按 id 匹配（而非下标），并在 EditableImageBlock 内部用
                                    // rememberUpdatedState 读取本回调的最新版本；
                                    // 列表本身也从 currentBlocks 读，避免用到旧快照
                                    onBlocksChange(
                                        currentBlocks.map {
                                            if (it.id == block.id && it is DocumentBlock.Image) {
                                                it.copy(widthFraction = newFraction)
                                            } else {
                                                it
                                            }
                                        }
                                    )
                                },
                                onRemove = {
                                    onBlocksChange(currentBlocks.filterNot { it.id == block.id })
                                }
                            )
                        }
                }
            }
        }
    }
}

/**
 * 可拖拽缩放的图片块。
 *
 * 交互：
 * - 点击图片主体：在其后插入文字块并聚焦（图片下方直接继续写字，无需额外按钮）
 * - 右上角 × ：删除该图片
 * - 右下角手柄拖拽：缩放（1:1 跟随横向位移）
 *
 * 拖拽逻辑：只取右下角手柄的横向位移，与宽度变化 1:1 对应。
 * 因为显示时锁定原图宽高比，宽度是唯一自由度，横向位移直接就是宽度变化，
 * 图片边缘会精确跟随手指；纵向位移会被外层纵向滚动接管，也不参与计算。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EditableImageBlock(
    block: DocumentBlock.Image,
    imageStore: NoteImageStore,
    availableWidthPx: Float,
    accentColor: Color,
    textColor: Color,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onWidthFractionChange: (Float) -> Unit,
    onRemove: () -> Unit
) {
    val density = LocalDensity.current
    val imageState by rememberNoteImageState(imageStore, block.fileName)

    // ⚠️ 这三个值必须在 pointerInput 内部通过 rememberUpdatedState 读取最新版本。
    //
    // 原因：Modifier.pointerInput 的 equals 只比较 key、不比较 handler，key 不变时
    // 该 modifier 节点不会被更新，节点里保存的始终是「首次组合时」的那个闭包。
    // 若直接捕获 block/blocks，拖拽就会把旧的 blocks 快照写回状态 ——
    // 表现为：打字后拖图片手柄，刚输入的文字回退；插入新图片块后拖旧图片，新块消失。
    // 这类问题被 1.5s 后的自动保存固化后，就成了真实的数据丢失。
    val currentFraction by rememberUpdatedState(block.widthFraction)
    val currentWidthPx by rememberUpdatedState(availableWidthPx)
    val currentOnWidthFractionChange by rememberUpdatedState(onWidthFractionChange)

    val widthDp = with(density) { (availableWidthPx * block.widthFraction).toDp() }

    Box(
        modifier = Modifier
            .width(widthDp)
            .aspectRatio(block.aspectRatio)
            .clip(RoundedCornerShape(12.dp))
            .background(accentColor.copy(alpha = 0.06f))
            // 点击图片主体 = 在其后插入文字块；长按 = 在其前插入图片。
            // 手柄和删除按钮在图片之上，各自消费自己的事件，不会触发这里的 clickable。
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongPress
            ),
        contentAlignment = Alignment.Center
    ) {
        when (val state = imageState) {
            is NoteImageState.Ready -> {
                Image(
                    bitmap = state.bitmap.asImageBitmap(),
                    contentDescription = stringResource(R.string.note_image),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
            NoteImageState.Loading -> {
                Text(
                    text = stringResource(R.string.image_loading),
                    style = MaterialTheme.typography.bodySmall,
                    color = textColor.copy(alpha = 0.5f)
                )
            }
            NoteImageState.Failed -> {
                Text(
                    text = stringResource(R.string.image_missing),
                    style = MaterialTheme.typography.bodySmall,
                    color = textColor.copy(alpha = 0.5f)
                )
            }
        }

        // 删除按钮（右上角） — 扩大触摸热区到 48dp 最小尺寸（Material 3 标准）
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)  // 减小内边距，配合 44dp 容器达到 48dp 触摸区域
                .size(44.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.35f))
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.delete_image),
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }

        // 右下角缩放拖拽手柄 — 扩大触摸热区到 48dp
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(4.dp)
                .size(44.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.35f))
                .pointerInput(block.id) {
                    var working = currentFraction
                    detectDragGestures(
                        onDragStart = { working = currentFraction }
                    ) { change, dragAmount ->
                        // 消费事件，避免父级纵向滚动抢走手势
                        change.consume()
                        // 换算逻辑抽在 DocumentBlock.resizeWidthFraction，已被单元测试覆盖
                        working = DocumentBlock.resizeWidthFraction(
                            startFraction = working,
                            dragX = dragAmount.x,
                            availableWidthPx = currentWidthPx
                        )
                        currentOnWidthFractionChange(working)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.OpenInFull,
                contentDescription = stringResource(R.string.resize_image),
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
        }
    }

    Spacer(modifier = Modifier.height(2.dp))
}

/**
 * 异步读取图片并给出明确状态。
 * 失败（文件缺失、解码失败等）会落到 [NoteImageState.Failed]，由调用方显示「图片已丢失」，
 * 而不是永远停在「加载中」。
 */
@Composable
fun rememberNoteImageState(
    imageStore: NoteImageStore,
    fileName: String,
    maxDimension: Int = NoteImageStore.DEFAULT_MAX_DIMENSION
): State<NoteImageState> =
    produceState<NoteImageState>(initialValue = NoteImageState.Loading, fileName, maxDimension) {
        val bitmap = if (fileName.isBlank()) {
            null
        } else {
            imageStore.loadBitmap(fileName, maxDimension)
        }
        value = if (bitmap != null) NoteImageState.Ready(bitmap) else NoteImageState.Failed
    }

/**
 * 只读图片缩略图，用于首页卡片。
 *
 * 刻意在小尺寸下解码（[maxDimension]），列表里多张卡片同时显示时不会把内存吃满。
 * 加载失败时不渲染任何内容——卡片上不该出现错误提示。
 */
@Composable
fun NoteImageThumbnail(
    imageStore: NoteImageStore,
    image: DocumentBlock.Image,
    modifier: Modifier = Modifier,
    maxDimension: Int = 512
) {
    val state by rememberNoteImageState(imageStore, image.fileName, maxDimension)
    val ready = state as? NoteImageState.Ready ?: return
    Image(
        bitmap = ready.bitmap.asImageBitmap(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier
    )
}
