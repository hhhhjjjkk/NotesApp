# 灵感笔记 (NotesApp)

> ⚠️ 本应用由 AI 自动生成（TRAE IDE / GLM），仅作技术演示用途。

极简风格的 Android 备忘录：瀑布流卡片、备忘录/待办双模式、图文混排、提醒通知、回收站、自定义主题与背景。

## 功能

- **记事**：标题 + 正文自动保存、字数统计、轻量 Markdown 渲染
- **图文混排**：段落间插图、拖拽缩放保持比例、首图缩略图、图片存内部目录（原图删除不影响笔记）、纳入云备份
- **双模式**：底部液态滑块切换备忘录/待办；待办点击圆圈即完成
- **个性化**：莫兰迪单卡换色、6 种主题色、深色/浅色/跟随系统、圆角/阴影/透明度可调、自定义背景图
- **组织检索**：实时搜索（SQL 过滤 + 防抖）、置顶、多选批量删除、长按菜单
- **提醒**：精确闹钟（Doze 下可触发）、开机补发错过提醒、点通知直达编辑页
- **动效**：删除时粒子飘散 + 液态圆球侧滑（跟手/粘连/Q 弹回弹）、页面转场速度可调
- **数据安全**：纯离线无网络权限、删除进回收站可撤销、30 天自动清理

## 技术栈

Kotlin 2.0 · Jetpack Compose + Material 3 (BOM 2024.06.00) · MVVM · Room 2.6.1 + DataStore · Haze 0.7.3 · minSdk 26 / targetSdk 35

## 项目结构

```
app/src/main/java/com/example/notesapp/
├── data/          # Room 实体/DAO/数据库/仓库、图文文档模型、DataStore
├── storage/       # 笔记图片内部存储（导入/降采样/孤儿清理）
├── notification/  # 提醒通知（渠道/调度/开机恢复）
└── ui/
    ├── components/  # 卡片、液态侧滑、滑块、图文编辑器等
    ├── navigation/  # 路由与转场动画
    ├── screens/     # 首页/编辑/设置/回收站
    ├── theme/       # 主题、液态玻璃、Markdown 解析
    └── viewmodel/
└── test/          # 单元测试 53 个用例（纯逻辑）
```

## 下载

[Releases](https://github.com/hhhhjjjkk/NotesApp/releases) 页面获取最新 APK（Release 签名，可直接安装）。

## 构建

```bash
./gradlew assembleDebug          # 构建
./gradlew testDebugUnitTest      # 测试
```

在 aarch64 主机上构建需 QEMU 模拟 x86-64 aapt2：
`./gradlew assembleDebug -Pandroid.aapt2FromMavenOverride=$ANDROID_HOME/build-tools/34.0.0/aapt2`
（详见 git 历史中 README 的构建排障章节；项目路径含中文时建议从 ASCII 符号链接路径构建。）

## 已知限制

- 无 UI / 仪器 / 数据库迁移测试（未导出 Room schema），仅 53 个纯逻辑单测
- 开发环境无设备，界面观感未经真机实测
- 搜索为子串匹配（`LIKE '%kw%'`），量极大时性能下降；不索引图片
- 数据库迁移链缺 `1→2`（历史无法考证），已加异常兜底

## 更新日志

见 [CHANGELOG.md](CHANGELOG.md) 与 [Releases](https://github.com/hhhhjjjkk/NotesApp/releases)。

## License

MIT
