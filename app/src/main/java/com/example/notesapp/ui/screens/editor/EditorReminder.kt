package com.example.notesapp.ui.screens.editor

import android.Manifest
import android.app.DatePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlarmOn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.notesapp.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 编辑页的提醒（闹钟）逻辑与 UI。
 *
 * 流程：点击闹钟按钮 →（Android 13+ 先请求通知权限）→ 选日期 → 选时间 →
 * 把时间戳写进 [EditorState.reminderAt]，由保存链路调度真正的闹钟。
 *
 * 权限被拒时**必须**放弃本次设置并提示，否则选完时间却收不到通知，
 * 用户会以为应用「坏了」，这是比不设提醒更糟的体验。
 */
class EditorReminderController internal constructor(
    private val state: EditorState,
    private val context: android.content.Context
) {
    /** 权限请求 launcher，由 [rememberEditorReminder] 注入后 onAlarmClicked 才可用。 */
    internal var permissionLauncher: androidx.activity.result.ActivityResultLauncher<String>? = null

    /** 日期选择器触发计数器：每次需要打开日期选择器时自增，确保 LaunchedEffect 重新触发。 */
    var datePickerTrigger by mutableStateOf(0)
        private set

    /** 时间选择器可见性（选完日期后打开）。 */
    var showTimePicker by mutableStateOf(false)
        private set

    internal var pickedYear by mutableStateOf(0)
    internal var pickedMonth by mutableStateOf(0)
    internal var pickedDay by mutableStateOf(0)

    /** 通知权限请求（Android 13+）。授权后继续打开日期选择器。 */
    fun requestNotificationPermissionAndOpenPicker(permissionLauncher: androidx.activity.result.ActivityResultLauncher<String>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        datePickerTrigger++
    }

    /** 闹钟按钮的统一入口：已有提醒时再次点击 = 取消。 */
    fun onAlarmClicked() {
        val launcher = permissionLauncher
        if (state.reminderAt > 0L) {
            state.reminderAt = 0L
        } else if (launcher != null) {
            requestNotificationPermissionAndOpenPicker(launcher)
        }
    }

    /** 权限授予结果。 */
    fun onPermissionResult(granted: Boolean) {
        if (granted) {
            datePickerTrigger++
        } else {
            Toast.makeText(
                context,
                context.getString(R.string.notification_permission_denied),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    /** 选定日期后进入时间选择。 */
    fun onDatePicked(year: Int, month: Int, day: Int) {
        pickedYear = year
        pickedMonth = month
        pickedDay = day
        showTimePicker = true
    }

    /** 选定时间后落盘。时间在过去时拒绝设置并提示。 */
    fun onTimePicked(hour: Int, minute: Int) {
        val c = Calendar.getInstance()
        c.set(pickedYear, pickedMonth, pickedDay, hour, minute, 0)
        c.set(Calendar.MILLISECOND, 0)
        val target = c.timeInMillis
        if (target > System.currentTimeMillis()) {
            state.reminderAt = target
        } else {
            Toast.makeText(
                context,
                context.getString(R.string.reminder_in_past),
                Toast.LENGTH_SHORT
            ).show()
        }
        showTimePicker = false
    }

    fun dismissTimePicker() {
        showTimePicker = false
    }
}

/** 挂接提醒控制器：创建权限 launcher、驱动日期对话框、绘制时间对话框与提醒横幅。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberEditorReminder(state: EditorState): EditorReminderController {
    val context = LocalContext.current
    val controller = EditorReminderController(state, context)

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> controller.onPermissionResult(granted) }
    controller.permissionLauncher = permissionLauncher

    // 日期选择器（原生 DatePickerDialog），由 trigger 计数器驱动，
    // 每次自增都重新弹出——不能用 boolean 开关，因为取消后需要能再次打开。
    LaunchedEffect(controller.datePickerTrigger) {
        if (controller.datePickerTrigger == 0) return@LaunchedEffect
        val cal = Calendar.getInstance()
        DatePickerDialog(
            context,
            { _, year, month, day -> controller.onDatePicked(year, month, day) },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).apply {
            datePicker.minDate = System.currentTimeMillis()
        }.show()
    }

    return controller
}

/** 提醒状态显示行：紧跟标题下方，展示已设置的提醒时间，并提供一键取消。 */
@Composable
fun ReminderBanner(state: EditorState, modifier: Modifier = Modifier) {
    if (state.reminderAt <= 0L) return
    val reminderText = SimpleDateFormat("MM月dd日 HH:mm", Locale.getDefault())
        .format(Date(state.reminderAt))
    Row(
        modifier = modifier
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
            modifier = Modifier.clickable { state.reminderAt = 0L }
        )
    }
}

/** 时间选择器对话框（Compose Material3 TimePicker），由 controller.showTimePicker 驱动。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorTimePickerDialog(controller: EditorReminderController) {
    if (!controller.showTimePicker) return
    val timeState = rememberTimePickerState(
        initialHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
        initialMinute = Calendar.getInstance().get(Calendar.MINUTE),
        is24Hour = true
    )
    AlertDialog(
        onDismissRequest = { controller.dismissTimePicker() },
        title = { Text(stringResource(R.string.set_reminder)) },
        text = {
            androidx.compose.foundation.layout.Box(
                contentAlignment = Alignment.Center
            ) {
                TimePicker(state = timeState)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                controller.onTimePicked(timeState.hour, timeState.minute)
            }) { Text(stringResource(R.string.confirm)) }
        },
        dismissButton = {
            TextButton(onClick = { controller.dismissTimePicker() }) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
