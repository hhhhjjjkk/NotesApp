package com.example.notesapp.ui.screens.editor

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.example.notesapp.NotesApplication
import com.example.notesapp.R
import com.example.notesapp.data.DocumentBlock
import com.example.notesapp.storage.NoteImageStore
import kotlinx.coroutines.launch

/**
 * 编辑页的图片选取与插入逻辑（行为与重构前完全一致，仅做结构搬运）。
 *
 * 职责：打开系统选图器、导入图片到应用内部存储、把图片块（及可选的
 * 跟随文字块）插入文档指定位置、插入空文字块并聚焦。
 *
 * 持有的两个「选图中」状态用 mutableStateOf 保存在实例里：
 * 它们只在「选图器在前台」的极短窗口内有意义，选完图立即被消费或复位，
 * 因此即使进程重建丢失，也只会退化为「插到文档末尾」（原实现同样如此，
 * pendingInsertIndex 失效时本来就按追加处理），不会丢用户数据。
 */
class EditorImagePicker(
    private val state: EditorState,
    private val imageStore: NoteImageStore,
    private val context: android.content.Context,
    /** 选图回调是异步 IO（复制文件），用组合级 scope 保证随界面销毁自动取消。 */
    private val scope: kotlinx.coroutines.CoroutineScope
) {
    /** 目标插入下标；-1 表示无效，选图返回后失效时按「追加到末尾」处理。 */
    var pendingInsertIndex by mutableStateOf(-1)
        private set

    /**
     * 选图返回后是否在其后补一个文字块：
     * 工具栏插入 = 补（用户多半想继续写字）；
     * 长按在图片「前」插入 = 不补（前方通常已有文字，再插空块反而多余）。
     */
    var pendingFollowWithText by mutableStateOf(true)
        private set

    /** 请求在 [index] 位置插入图片。会启动系统选图器。 */
    fun requestInsert(launcher: ActivityResultLauncher<Array<String>>, index: Int, followWithText: Boolean = true) {
        pendingInsertIndex = index
        pendingFollowWithText = followWithText
        launcher.launch(arrayOf("image/*"))
    }

    /** 长按图片块：在它前面插入一张图片（两张图片之间也能插图）。 */
    fun insertBefore(launcher: ActivityResultLauncher<Array<String>>, imageId: String) {
        val index = state.blocks.indexOfFirst { it.id == imageId }
        if (index < 0) return
        requestInsert(launcher, index, followWithText = false)
    }

    /** 在指定位置插入一个空文字块并聚焦。 */
    fun insertTextAt(index: Int) {
        val newBlock = DocumentBlock.text("")
        val target = index.coerceIn(0, state.blocks.size)
        state.blocks = state.blocks.toMutableList().also { it.add(target, newBlock) }
        state.focusBlockId = newBlock.id
    }

    /**
     * 点击图片块后，在其后插入一个空文字块并聚焦。
     * 这是「图片下方直接写字」的主入口，用户不需要去找任何按钮。
     */
    fun insertTextAfterImage(imageId: String) {
        val index = state.blocks.indexOfFirst { it.id == imageId }
        if (index < 0) return
        insertTextAt(index + 1)
    }

    /** 选图器返回后执行：导入 + 插入 + 可选的跟随文字块。 */
    fun onImagePicked(uri: Uri?) {
        val insertAt = pendingInsertIndex
        val followWithText = pendingFollowWithText
        pendingInsertIndex = -1
        pendingFollowWithText = true
        if (uri == null) return

        scope.launch {
            val imageBlock = imageStore.importImage(uri)
            if (imageBlock == null) {
                Toast.makeText(context, context.getString(R.string.image_import_failed), Toast.LENGTH_SHORT).show()
                return@launch
            }
            // 下标失效（-1，或期间文档被改动）时退化为追加到末尾，
            // 而不是默默丢弃用户刚选好的图片
            val target = if (insertAt in 0..state.blocks.size) insertAt else state.blocks.size
            val updated = state.blocks.toMutableList().also { it.add(target, imageBlock) }
            if (followWithText) {
                // 图片后面补一个空文字块并聚焦：插完图可以直接继续写字，
                // 不需要再去找任何「插入文字」按钮。
                val textBlock = DocumentBlock.text("")
                updated.add(target + 1, textBlock)
                state.blocks = updated
                state.focusBlockId = textBlock.id
            } else {
                state.blocks = updated
            }
            // 插图提示：告诉用户长按图片可在其前插入
            Toast.makeText(context, context.getString(R.string.image_inserted_hint), Toast.LENGTH_SHORT).show()
        }
    }
}

/**
 * 创建图片插入控制器并挂接系统选图器。
 *
 * @return (picker, launcher)：picker 供业务调用；launcher 由
 *         「工具栏插图按钮」与「长按图片块」两条路径共用。
 */
@Composable
fun rememberEditorImagePicker(
    state: EditorState,
    imageStore: NoteImageStore
): Pair<EditorImagePicker, androidx.activity.result.ActivityResultLauncher<Array<String>>> {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val picker = EditorImagePicker(state, imageStore, context, scope)

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        picker.onImagePicked(uri)
    }

    return picker to launcher
}
