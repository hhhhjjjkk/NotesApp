# 灵感笔记 (NotesApp)

> ⚠️ **声明：本应用由 AI（TRAE IDE / GLM 5.2 模型）自动生成，非人工编写。**
>
> 代码、UI 设计、架构均由 AI 根据需求文档自动产出，仅作技术演示用途。
> 后续的缺陷修复与单元测试由 AI 协作完成（见 [更新日志](#更新日志)）。

一个极简风格的 Android 备忘录应用，支持瀑布流卡片、备忘录/待办双模式、提醒通知、回收站、自定义主题与背景等功能。

## 技术栈

| 项目 | 版本 |
|---|---|
| 开发语言 | Kotlin 2.0.0 |
| UI 框架 | Jetpack Compose + Material 3（BOM 2024.06.00） |
| 架构模式 | MVVM + Repository |
| 数据存储 | Room 2.6.1 + DataStore Preferences 1.1.1 |
| 导航 | Navigation Compose 2.7.7 |
| 视觉特效 | Haze 0.7.3（真实背景模糊） |
| 构建工具 | Gradle 8.7 + AGP 8.5.0（KSP 2.0.0-1.0.22） |
| 最低 / 目标 SDK | minSdk 26 / targetSdk 35 |
| 当前版本 | versionCode 24，versionName 3.3.1 |

## 功能特性

### 核心记事
- 快速新建笔记，沉浸式无边框编辑
- 标题与正文分离，自动保存
- 实时字数统计
- 轻量 Markdown 渲染（标题、列表、**粗体**、*斜体*）

### 备忘录 / 待办双模式
- 底部液态玻璃分段滑块切换，支持拖动与弹簧回弹
- 待办卡片右侧完成圆圈，点击即完成并移入回收站

### 视觉与个性化
- 瀑布流卡片展示（自适应列宽）
- 单卡片自定义换色（莫兰迪色系）
- 深色 / 浅色 / 跟随系统主题
- 全局主题色选择（6 种）
- 卡片圆角、阴影、透明度可调
- 自定义背景图（含遮罩强度调节）
- 动画速度可调（150ms~450ms）
- 设置页实时预览

### 组织与检索
- 实时全局搜索（SQL 层过滤 + 250ms 防抖）
- 卡片置顶
- 多选批量删除
- 长按快捷菜单（置顶 / 分享 / 复制 / 删除）

### 提醒通知
- 为笔记设置定时提醒
- 精确闹钟（Doze 模式下仍可触发；无权限时降级为非精确闹钟）
- 开机自动重排闹钟，并补发关机期间错过的提醒
- 点击通知直达对应笔记编辑页

### 数据安全
- 离线本地存储，无网络权限
- 删除移入回收站，支持 Snackbar 撤销
- 回收站恢复 / 永久删除 / 清空，超 30 天自动清理
- 已纳入系统云备份与换机直传（含 Room 数据库）

## 项目结构

```
app/src/
├── main/java/com/example/notesapp/
│   ├── data/                    # 数据层
│   │   ├── Note.kt              # Room 实体（含标签、置顶、回收站、提醒字段）
│   │   ├── NoteDao.kt           # 数据访问对象
│   │   ├── NoteDatabase.kt      # Room 数据库（含 v2→v3→v4 迁移）
│   │   ├── NoteRepository.kt    # 仓库
│   │   └── DataStoreManager.kt  # 主题 / 背景等偏好存储
│   ├── notification/            # 提醒通知
│   │   ├── NotificationHelper.kt    # 通知渠道与通知构建
│   │   ├── NotificationScheduler.kt # AlarmManager 调度 / 取消
│   │   ├── ReminderReceiver.kt      # 闹钟触发后发通知
│   │   └── BootReceiver.kt          # 开机后恢复闹钟并补发
│   ├── ui/
│   │   ├── components/          # 可复用组件（卡片/搜索栏/滑块/取色器…）
│   │   ├── navigation/          # 导航路由与转场动画
│   │   ├── screens/             # 页面（首页/编辑/设置/回收站）
│   │   ├── theme/               # 主题、液态玻璃、背景、Markdown 解析
│   │   └── viewmodel/           # NotesViewModel / SettingsViewModel
│   ├── MainActivity.kt
│   └── NotesApplication.kt      # Application（通知渠道、数据库预热、清理）
└── test/java/com/example/notesapp/   # 单元测试（19 个用例）
    ├── data/NoteTagListTest.kt
    └── ui/theme/MarkdownParserTest.kt
```

## 下载

APK 下载见本仓库 [Releases](https://github.com/hhhhjjjkk/NotesApp/releases)。

最新版本：
```
https://github.com/hhhhjjjkk/NotesApp/releases/download/v3.3.1/notesapp-v3.3.1-debug.apk
```

> 均为 **debug 签名包**，可直接安装；若与已装版本签名冲突，请先卸载旧版。

## 构建

```bash
./gradlew assembleDebug
```

生成的 APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。

> **在 ARM64 主机（aarch64）上构建**：AGP 自带的 `aapt2` 是 x86-64 二进制，无法在 aarch64 上执行，
> 会在 `processDebugResources` 阶段报 `Daemon startup failed`。需改用本机可执行的 aapt2：
> ```bash
> ./gradlew assembleDebug -Pandroid.aapt2FromMavenOverride=$ANDROID_HOME/build-tools/34.0.0/aapt2
> ```
> （该路径下的 `aapt2` 若为包装脚本、内部经 QEMU 调用 x86-64 二进制，同样可用。）

### 运行测试

```bash
./gradlew testDebugUnitTest
```

## 更新日志

### v3.3.1 (versionCode 24)

修复版本，重点解决数据丢失与显示异常：

- **修复 Markdown 行内标记丢字（会导致内容消失）**：原先使用 `Regex.split` 解析 `**粗体**` / `*斜体*`，而 split 会丢弃分隔符、被标记的正文恰好位于捕获组内，导致 `普通**加粗**普通` 被渲染成 `普通普通`。已改为基于 `Regex.findAll` 的匹配位置逐段拼接。
- **修复备份未包含笔记数据**：备份规则原先只包含 `sharedpref` 域，而笔记正文存放在 Room 数据库（`database` 域），导致云备份实际未保存任何笔记。现已补全，并排除 `-wal`/`-shm`/`-journal` 临时文件。
- **性能**：首页列表过滤下推至 SQL，不再把整表读入内存；搜索增加 250ms 防抖（空查询豁免防抖，避免冷启动首页空白）。
- **数据访问收敛**：不再散落地直接调用 DAO，统一经由 `NoteRepository` 访问。
- **界面修复**：搜索栏与列表卡片重叠；编辑页正文最后一行被底部颜色条遮挡。
- **修复返回首页时底部滑块不播放入场动画**（见下方说明）。
- 新增 19 个单元测试。

> ⚠️ **关于动画修复的说明**：该修复的机制已通过反编译 navigation-compose 2.7.7 核实，
> 但**尚未在真机 / 模拟器上验证**。另有一个已知取舍：重播时先压回不可见状态，
> 观感上可能表现为「轻微下沉后弹起」，而非纯上浮。如表现不符预期，欢迎反馈。

### v3.3 (versionCode 23)

- 底部「备忘录 / 待办」切换器升级为毛玻璃（frosted glass）效果：真实背景模糊、半透明磨砂染色 + 噪点、保留液态弹性拉伸。

## 已知限制

- **测试覆盖有限**：目前仅 19 个单元测试，覆盖 Markdown 解析与标签解析两处纯逻辑，**无 UI 测试、无仪器测试、无数据库迁移测试**。
- **未做界面验证**：本项目开发环境无模拟器 / 真机，界面改动仅经过编译与静态分析，**观感层面未经实测**。
- **搜索为子串匹配**：`LIKE '%关键字%'` 无法使用索引，笔记量极大时性能会下降。
- **标签字段未接入 UI**：`Note.tags` 及其解析逻辑已实现并有测试，但界面上暂无标签编辑入口。

## License

MIT
