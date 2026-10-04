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
| 当前版本 | versionCode 25，versionName 3.4 |

## 功能特性

### 核心记事
- 快速新建笔记，沉浸式无边框编辑
- 标题与正文分离，自动保存
- 实时字数统计
- 轻量 Markdown 渲染（标题、列表、**粗体**、*斜体*）

### 图文混排
- 正文任意段落之间可插入图片，支持多张
- 图片右下角**拖拽手柄缩放**，始终保持原图比例，不会拉变形
- 单张图片可一键删除
- 首页卡片自动显示首图缩略图
- 图片复制到应用内部目录保存：相册原图被删或移动后，笔记图片依然有效
- 图片纳入系统云备份与换机直传

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
│   │   ├── Note.kt              # Room 实体（含标签、置顶、回收站、提醒、图文文档字段）
│   │   ├── RichDocument.kt      # 图文文档模型：DocumentBlock 与编解码、拖拽缩放数学
│   │   ├── NoteDao.kt           # 数据访问对象
│   │   ├── NoteDatabase.kt      # Room 数据库（v2→v3→v4→v5 迁移）
│   │   ├── NoteRepository.kt    # 仓库
│   │   └── DataStoreManager.kt  # 主题 / 背景等偏好存储
│   ├── storage/                 # 笔记图片的内部存储
│   │   └── NoteImageStore.kt    # 导入 / 降采样读取 / 路径穿越防护 / 孤儿清理
│   ├── notification/            # 提醒通知
│   │   ├── NotificationHelper.kt    # 通知渠道与通知构建
│   │   ├── NotificationScheduler.kt # AlarmManager 调度 / 取消
│   │   ├── ReminderReceiver.kt      # 闹钟触发后发通知
│   │   └── BootReceiver.kt          # 开机后恢复闹钟并补发
│   ├── ui/
│   │   ├── components/          # 可复用组件（卡片/搜索栏/滑块/取色器/图文编辑器…）
│   │   ├── navigation/          # 导航路由与转场动画
│   │   ├── screens/             # 页面（首页/编辑/设置/回收站）
│   │   ├── theme/               # 主题、液态玻璃、背景、Markdown 解析
│   │   └── viewmodel/           # NotesViewModel / SettingsViewModel
│   ├── MainActivity.kt
│   └── NotesApplication.kt      # Application（通知渠道、数据库预热、清理、图片存储）
└── test/java/com/example/notesapp/   # 单元测试（40 个用例）
    ├── data/RichDocumentCodecTest.kt    # 图文编解码（12）
    ├── data/DocumentBlockResizeTest.kt  # 拖拽缩放数学（9）
    ├── data/NoteTagListTest.kt          # 标签解析（6）
    └── ui/theme/MarkdownParserTest.kt   # Markdown 解析（13）
```

## 下载

APK 下载见本仓库 [Releases](https://github.com/hhhhjjjkk/NotesApp/releases)。

最新版本：
```
https://github.com/hhhhjjjkk/NotesApp/releases/download/v3.4/notesapp-v3.4-debug.apk
```

> 均为 **debug 签名包**，可直接安装；若与已装版本签名冲突，请先卸载旧版。

## 构建

```bash
./gradlew assembleDebug
```

生成的 APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。

> **在 ARM64 主机（aarch64）上构建**：AGP 自带的 `aapt2` 是 x86-64 二进制，无法在 aarch64 上直接执行，
> 会在 `processDebugResources` 阶段报 `Daemon startup failed` / `AAPT2 Daemon startup failed`。
>
> 需要两步：
> 1. 安装 x86-64 用户态模拟器（提供 `qemu-x86_64-static`）：
>    ```bash
>    sudo apt-get install -y qemu-user-static
>    ```
> 2. 指定可执行的 aapt2（build-tools 里的 `aapt2` 是包装脚本，内部经 QEMU 调用 `aapt2.x86_64.real`）：
>    ```bash
>    ./gradlew assembleDebug -Pandroid.aapt2FromMavenOverride=$ANDROID_HOME/build-tools/34.0.0/aapt2
>    ```
>
> 验证方式：直接执行 `$ANDROID_HOME/build-tools/34.0.0/aapt2 version`，
> 能打印 `Android Asset Packaging Tool (aapt) 2.x` 即说明模拟器已就绪。

### 运行测试

```bash
./gradlew testDebugUnitTest
```

## 更新日志

### v3.4 (versionCode 25)

新增**图文混排**功能：

- 正文任意位置可插入图片，段落之间、文首文末均可；图片下方还能继续写文字。
- 图片右下角**拖拽手柄缩放**，始终保持原图比例，不会拉变形；每张图可单独删除。
- 首页卡片显示首图缩略图，便于在列表中辨认。
- 图片复制到应用内部 `files/note_images/` 保存——相册原图被删除或移动后，笔记图片依然有效。
- 图片已纳入云备份与换机直传规则（`backup_rules.xml` / `data_extraction_rules.xml`）。
- 数据结构：`Note` 新增 `richContent` 字段存放块列表，`Note.content` 继续保存纯文本（搜索 / 分享用）；
  Room 版本 4 → 5，新增 `MIGRATION_4_5`，旧笔记按「单段纯文本」回退读取，历史内容不丢。
- 新增 21 个单元测试（编解码 12 + 拖拽数学 9），累计 40 个。

> ⚠️ **已知取舍与未验证项**：
> - 拖拽缩放的手感、图片显示效果**未在真机 / 模拟器上验证**（本环境无设备）。
> - 编辑页是非懒加载 `Column`，**大量图片同时打开时存在内存压力**；单张已限制长边 ≤1280px。
> - 删除笔记后其图片文件不会被立即回收（避免撤销/回收站场景误删），**孤儿图片会占用存储**；
>   `NoteImageStore.cleanupOrphans()` 已实现但尚未接入 UI。

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

- **测试覆盖有限**：目前 40 个单元测试，覆盖 Markdown 解析、标签解析、图文编解码与拖拽缩放数学等纯逻辑，**无 UI 测试、无仪器测试、无数据库迁移测试**。
  迁移测试缺失的根因是 `NoteDatabase` 使用 `exportSchema = false` 且仓库无 `app/schemas` 目录——没有导出的 schema JSON，就无法使用 Room 的 `MigrationTestHelper`。若需补齐迁移测试，需先开启 schema 导出。
- **未做界面验证**：本项目开发环境无模拟器 / 真机，界面改动仅经过编译与静态分析，**观感层面未经实测**；图文混排的显示效果与拖拽手感同样未实测。
- **图片占用存储**：图片不参与自动回收。删除单张图片或整篇笔记后，对应文件仍留在 `files/note_images/`，需要手动清理或接入 `NoteImageStore.cleanupOrphans()`。
- **多图内存**：编辑页用普通 `Column` 渲染全部图片块，图片较多且都较大时存在 OOM 风险（单张长边已限制 ≤1280px，但仍非 LruCache 方案）。
- **搜索为子串匹配**：`LIKE '%关键字%'` 无法使用索引，笔记量极大时性能会下降。搜索只匹配文字，不含图片。
- **标签字段未接入 UI**：`Note.tags` 及其解析逻辑已实现并有测试，但界面上暂无标签编辑入口。
- **数据库迁移链存在缺口（未经证实的影响）**：当前 `@Database(version = 5)`，但只注册了 `2→3`、`3→4`、`4→5` 三条迁移，**缺少 `1→2`**；且使用的是 `fallbackToDestructiveMigrationOnDowngrade()`，仅在降级时销毁数据，不覆盖升级路径。
  若历史上确实发布过数据库版本为 1 的构建，则从该版本升级时会因找不到迁移路径而抛异常。**本仓库历史为单次压缩提交，无法确认早期版本使用的数据库版本，因此该影响未能证实**，此处如实记录以待核实。如你从很旧的版本升级遇到问题，请反馈。
- **通知深链到已删除笔记**：若通知指向的笔记已进入回收站，编辑页会以空白状态打开，此时输入内容保存会覆盖同一 id 的记录。属既有问题，尚未修复。

## License

MIT
