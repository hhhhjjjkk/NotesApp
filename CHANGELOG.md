# Changelog

## v3.9.0 (versionCode 52)

- **液态侧滑删除**：重写侧滑交互（参考鸿蒙通知中心）——卡片轻微跟随、删除圆球 1:1 跟手、卡片与圆球间 metaball 液态粘连（超过 56dp 撕裂）、松手不足阈值低阻尼弹簧 Q 弹回弹、超过卡片宽 32% 触发删除；移除旧红色渐变底板；粒子飘散方向保留
- 移除 SwipeToDismissBox 依赖

## v3.8.1 (versionCode 51)

- 退出备忘录动画改为与退出设置一致的水平滑动（编辑页右滑出、首页揭示）；首页淡入与滑动退场严格对齐，根治「双重影像」

## v3.8.0 (versionCode 50)

- **修复输入法要连点多次**：项目从未调用过 SoftwareKeyboardController。正文块点击、标题点击、自动聚焦 effect 全部补上显式 show()；自动聚焦改为 show→等帧→再 show 以重试转场期无效调用

## v3.7.9 (versionCode 49)

- 打开备忘录缩放改为 1.06→1.0 收拢落定；退场时长 0.82 倍、独立加速曲线

## v3.7.8 (versionCode 48)

- 美化滑动删除红色底板：渐变 + smoothstep 进度 + 实心圆底白图标 + 随进度缩放

## v3.7.7 (versionCode 47)

- 修正 v3.7.6 粒子过小不可见的问题：数量 260~350、半径 0.55~1.25dp、亮度下限 0.68；方向漂移加大至 0.20~0.45 倍卡宽

## v3.7.6 (versionCode 46)

- 粒子更小更密；滑动方向决定飘散方向（NoteCard 开启双向滑动、回传方向、删除图标随侧）

## v3.7.5 (versionCode 45)

- **新增删除粒子飘散**：卡片原位散出 70~114 颗星尘（~0.5s 淡尽），覆盖左滑/多选/待办完成入口；纯视觉层，撤销不受影响

## v3.7.4 (versionCode 44)

- **根治底栏上浮动画「向上飞」**（v3.7.0~3.7.3 反复出现）：移除手动 Animatable/snapTo 机制改回原生 AnimatedVisibility + slideInVertically；以 remember(replayKey) 重建 TransitionState 重播（不再翻转 targetState）；ON_RESUME 补发去重

## v3.7.3 (versionCode 43)

- 编辑页返回首页淡入对齐退场时长消除双影；滑块吸附弹簧 NoBouncy；按压反馈统一 0.97

## v3.7.2 (versionCode 42)

- 底栏上浮改为内容层单次动画 + 合并护栏（当时的最优修复，后由 v3.7.4 根治）

## v3.7.1 (versionCode 41) / v3.7.0 (versionCode 40)

- 视频对齐的柔和编辑页转场；首页侧转场多次调整（后证实根因在重播机制）

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
