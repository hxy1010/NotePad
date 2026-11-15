# 我的记事本（NotePad）

一款纯本地运行的 Android 记事应用，由 Google 经典的 NotePad 示例扩展而来。在保留 ContentProvider + SQLite 教学骨架的基础上，新增了笔记本分类、照片与录音附件、独立待办清单、日/夜模式毛玻璃界面，以及可直接勾选待办的桌面小组件。

不依赖任何第三方库，仅使用 Android 平台原生 API。

## 功能特性

### 笔记
- 按「笔记本」分类管理笔记，笔记本支持新建、重命名、删除（级联删除其中的笔记）
- 笔记支持新建、编辑、删除、修改标题、复制 / 粘贴、导出为文件
- 支持按标题或内容实时搜索
- 支持排序：最近修改优先、最早修改优先、标题 A → Z、标题 Z → A（选择会记忆）
- 笔记内可插入照片、录制语音，附件可预览、播放、删除

### 待办
- 独立的待办清单页面，与笔记解耦
- 新增、勾选完成、删除待办，并显示创建时间

### 界面
- 毛玻璃风格：渐变背景 + 半透明圆角卡片 + 高光描边
- 日间 / 夜间模式一键切换，选择持久化保存
- 统一的手绘风可爱矢量图标
- 底部导航在「笔记」与「待办」之间切换

### 桌面便签（AppWidget）
- 4×3 桌面小组件，列出未完成的待办（最多 5 条）
- 每条待办前带小方框，点击整行即可在桌面直接打勾完成
- 底部显示「共 X 项 · 已完成 Y 项」
- 点击组件空白区域打开待办清单页面
- 数据变更后即时刷新，另有 30 分钟定时刷新兜底

## 界面结构

```
NotebookList（启动页：我的笔记簿）
    └── NotesList（某个笔记本下的笔记列表：搜索、排序、删除）
            └── NoteEditor（编辑笔记：正文、照片、录音）

TodoList（待办清单，与笔记并列）
```

## 技术实现

| 项目 | 说明 |
| --- | --- |
| 语言 | Java（无 Kotlin、无第三方依赖） |
| 最低版本 | minSdk 11（Android 3.0） |
| 编译 / 目标版本 | compileSdk 23、targetSdk 23 |
| 构建工具 | Android Gradle Plugin 8.12.2、Gradle 8.13 |
| 数据存储 | ContentProvider + SQLite（数据库版本 5） |
| 组件 | Activity、ContentProvider、AppWidgetProvider（RemoteViews） |
| 主题 | 自定义 `NotePadTheme`（Holo.Light）+ `values-night` 夜间配色 |

### 数据模型

数据库共 4 张表：

- `notebooks`：笔记簿，包含名称、分类（学习 / 工作 / 生活 / 其他）、创建与修改时间
- `notes`：笔记，包含标题、正文、所属笔记簿 id、创建与修改时间
- `attachments`：附件，包含所属笔记 id、类型（照片 / 录音）、文件绝对路径、创建时间
- `todos`：待办，包含内容、完成状态、创建时间

所有表均通过统一的 `NotePad` 契约类暴露 URI 与列名，ContentProvider 负责 CRUD、级联删除与数据库升级。

### 关键类

| 文件 | 职责 |
| --- | --- |
| `NotePad.java` | 数据契约：表名、列名、Content URI、MIME 类型 |
| `NotePadProvider.java` | ContentProvider 实现：多表 CRUD、级联删除、数据变更广播 |
| `NotebookList.java` | 启动页，笔记本列表与增删改 |
| `NotesList.java` | 笔记列表：搜索、排序、行内删除 |
| `NoteEditor.java` | 笔记编辑：正文、照片、录音、附件管理 |
| `TitleEditor.java` | 修改笔记标题弹窗 |
| `TodoList.java` | 待办清单页面 |
| `NoteWidgetProvider.java` | 桌面便签 AppWidget：展示待办、桌面打勾、点击跳转 |
| `ThemeUtils.java` | 日/夜模式与主题包装 |
| `BottomNav.java` | 底部导航栏绑定与高亮 |

## 目录结构

```
NotePad/
├── app/
│   └── src/main/
│       ├── java/com/example/android/notepad/   # 全部业务代码
│       ├── res/
│       │   ├── drawable/                       # 毛玻璃背景、手绘图标
│       │   ├── layout/                         # 页面与组件布局
│       │   ├── menu/                           # 各页面菜单
│       │   ├── values/                         # 字符串、颜色、样式
│       │   ├── values-night/                   # 夜间模式配色
│       │   └── xml/note_widget_info.xml        # 桌面小组件配置
│       └── AndroidManifest.xml
├── build.gradle
└── settings.gradle
```

## 构建与运行

1. 使用 Android Studio 打开工程，等待 Gradle 同步完成
2. 编译 Debug 包：

   ```bash
   ./gradlew :app:assembleDebug
   ```

3. 安装到设备或模拟器：

   ```bash
   adb install -r --bypass-low-target-sdk-block app/build/outputs/apk/debug/app-debug.apk
   ```

   > 由于 `targetSdkVersion` 为 23，Android 14 及以上系统安装时需要 `--bypass-low-target-sdk-block` 参数，或通过 Android Studio 直接运行。

4. 首次录音时会请求麦克风权限，需要允许后使用。

## 使用说明

- **添加桌面便签**：长按桌面空白处 → 小部件 → 我的记事本 → 长按「桌面便签」拖到桌面
- **桌面打勾**：直接点击组件中的待办行，即标记为完成并从列表移除
- **切换日夜模式**：笔记簿页面右上角菜单中的「夜间模式 / 日间模式」

## 已知限制

- 项目沿用了较旧的 `targetSdkVersion 23`，在新版本 Android 上会以兼容模式运行
- 毛玻璃效果通过渐变与半透明图层模拟，并非真实的背景模糊
- 照片与录音保存在应用私有目录，卸载应用后附件会一并丢失

