# 灵感笔记 (NotesApp)

> ⚠️ **声明：本应用由 AI（TRAE IDE / MiniMax-M3 模型）自动生成，非人工编写。**
>
> 代码、UI 设计、架构均由 AI 根据需求文档自动产出，仅作技术演示用途。

一个极简风格的 Android 备忘录应用，支持瀑布流卡片、自定义主题、深色模式等功能。

## 技术栈

- **开发语言**：Kotlin
- **UI 框架**：Jetpack Compose + Material 3
- **架构模式**：MVVM
- **数据存储**：Room 数据库 + DataStore
- **构建工具**：Gradle 8.14.4

## 功能特性

### 核心记事
- 快速新建笔记，沉浸式无边框编辑
- 标题与正文分离，自动保存
- 实时字数统计

### 视觉与个性化
- 瀑布流卡片展示，错落有致
- 单卡片自定义换色（莫兰迪色系）
- 深色/浅色/跟随系统主题
- 全局主题色选择
- 卡片圆角与阴影可调
- 设置页实时预览

### 组织与检索
- 实时全局搜索
- 卡片置顶
- 长按快捷菜单（置顶/分享/复制/删除）

### 数据安全
- 离线本地存储
- 删除撤销（Snackbar Undo）
- 原生文本分享

## 项目结构

```
app/src/main/java/com/example/notesapp/
├── data/                    # 数据层
│   ├── Note.kt              # Room 实体
│   ├── NoteDao.kt           # 数据访问对象
│   ├── NoteDatabase.kt      # Room 数据库
│   ├── NoteRepository.kt    # 仓库
│   └── DataStoreManager.kt  # 主题配置存储
├── ui/
│   ├── components/          # 可复用组件
│   ├── navigation/          # 导航配置
│   ├── screens/             # 页面（首页/编辑/设置）
│   ├── theme/               # 主题（颜色/字体/形状）
│   └── viewmodel/           # ViewModel
├── MainActivity.kt
└── NotesApplication.kt      # Application（数据库预热）
```

## 下载

APK 下载见 [Releases](https://github.com/hhhhjjjkk/notesapp-release/releases)。

国内加速下载：
```
https://gh-proxy.com/https://github.com/hhhhjjjkk/notesapp-release/releases/download/v1.0/notesapp-debug.apk
```

## 构建

```bash
./gradlew assembleDebug
```

生成的 APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。

## License

MIT
