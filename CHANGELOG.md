# Changelog

## v3.5.0 (versionCode 27)

### 新增
- **EXIF 方向处理**：引入 `androidx.exifinterface`，修复 API 26/27 设备上竖拍照片显示旋转的问题；ImageDecoder（API 28+）路径保持自动处理，BitmapFactory 回退路径手动读取方向、交换宽高比并旋转位图
- **插图提示**：插入图片后显示 Toast 提示（点击在当前块后插入，长按在当前块前插入）
- **图片孤儿清理**：`NoteImageStore.cleanupOrphans()` 已接线，在应用启动（10 分钟宽限期）与笔记保存时清理不再被引用的图片文件，避免磁盘泄漏
- **通知深链数据安全**：`PendingIntent` 使用 `FLAG_IMMUTABLE`，深链 intent 增加校验

### 修复
- **滑块旧闭包**：`LiquidSegmentedSlider` 的拖动取消回调通过 `rememberUpdatedState` 读取最新选中值，修复切换标签后状态不一致
- **图片触控热区**：编辑器中图片删除按钮与缩放手柄容器扩大至 48dp，符合无障碍触摸目标规范
- **回收站只读提示去重**：移除编辑器中重复渲染的"已放入回收站"横幅（3 处重复 → 1 处）
- **回收站笔记恢复**：恢复按钮改用 `trashedNoteRef`，修复回收站笔记无法恢复的问题

### 改进
- **防御性 Room 迁移**：1→2 迁移增加异常兜底，旧表结构不符时保留空新表而不崩溃
- **编辑器 LazyColumn 化**：`RichDocumentEditor` 采用 `LazyColumn` 渲染块，长文档滚动更流畅
- **版本号**：versionCode 27 / versionName 3.5.0

### 测试
- 新增 `DocumentBlockSanitizeTest`（8 项：宽度比例、宽高比 sanitize 与边界夹取）
- 新增 `NoteImageStoreExifTest`（3 项：EXIF 方向映射、侧向判断、宽高交换）
- 全量单元测试 42+ 项通过；`assembleDebug` / `bundleRelease` 构建验证通过

## 之前版本
见 git 历史。
