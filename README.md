# 手势识别

支持的类别：数字 1~9、差评、拳头、点赞、单手比心、OK，其它归为「其他手势」。

## 功能

- 摄像头实时预览，点「识别」抓取当前这一帧
- 一次可检测多个手势，逐个画框
- 每个手势输出中文类别 + 概率
- 点一下画面回到实时预览

## 实现原理

### 整体流程

```
摄像头抓帧（JPEG）
   │
   ▼
ImageSource.fromJpeg()  ──▶  Bitmap（最长边 ≤ 2048，已旋正）
   │
   ▼
GestureEngine.scan()
   ├── MLFrame.Creator().setBitmap(bitmap).create()
   ├── analyzer.asyncAnalyseFrame(frame)
   │     │
   │     ▼
   │   华为 HMS 端侧手势识别（先做手部关键点检测，再分类手势）
   │
   ▼
List<MLGesture>
   ├── getRect()      ──▶ 手势框
   ├── getCategory()  ──▶ 类别编号
   └── getScore()     ──▶ 概率
   │
   ▼
类别编号 ──▶ 中文名 ──▶ List<Detect> ──▶ BoxPainter 画框 ──▶ 预览层 + 结果文本
```

### 1. 华为 HMS

手势识别要在「手部关键点」的基础上做分类，自己搭要两套模型加一套关键点后处理。华为 HMS Core ML Kit 的 `MLGestureAnalyzer` 一次调用直接给出手势框、类别编号和概率。

用的是 `MLGestureAnalyzer`，配置就是默认值：

```java
new MLGestureAnalyzerSetting.Factory().create()
```

### 2. 类别编号映射

模型输出的是数字编号，本工程按下面的表转成中文：

| 编号 | 显示 |
|---|---|
| 0 ~ 8 | 数字 1 ~ 数字 9 |
| 9 | 差评 |
| 10 | 拳头 |
| 11 | 点赞 |
| 12 | 单手比心 |
| 13 | OK |
| 其它 | 其他手势 |

数字编号到具体手势的对应关系由华为的模型决定，本工程只做展示层映射。

### 3. 概率怎么用

`getScore()` 是模型对这一类别给出的概率（0~1），直接显示在结果文本里，不设阈值——低于某个值的结果也照样展示，由使用者自己判断。

## 代码架构

```
app/src/main/java/com/shizi/gesture/
├── MainActivity.java        178 行   摄像头预览、抓帧、结果展示
├── img/
│   └── ImageSource.java      74 行   JPEG 解码 + 降采样 + 旋正
├── scan/
│   ├── Scanner.java          13 行   引擎接口
│   ├── Detect.java           17 行   一条结果
│   ├── ScanOutcome.java      17 行   结果集合 + 汇总文本
│   ├── BoxPainter.java       84 行   画框与标签
│   └── GestureEngine.java   100 行   ★ HMS 手势识别封装
└── util/
    └── UiKit.java            11 行   Toast
```

### 各文件职责

| 文件 | 职责 |
|---|---|
| `MainActivity` | 相机权限 → CameraX 预览 → `ImageCapture` 抓帧 → 调引擎 → 显示标注图和文字 |
| `ImageSource` | JPEG 解码、降采样到最长边 2048、按元数据旋转角旋正 |
| `Scanner` | 引擎接口，`scan(bitmap, callback)` + `close()` |
| `Detect` | 一条结果：`label`（手势中文名）、`score`（概率）、`box`（位置） |
| `ScanOutcome` | 结果列表 + 汇总多行文本 |
| `BoxPainter` | 把每个 `Detect` 的框和标签画到图片副本上 |
| `GestureEngine` | **核心**：HMS 分析器调用、类别映射、把 `MLGesture` 转成 `Detect` |
| `UiKit` | Toast 封装 |

### 核心代码位置

| 位置 | 作用 |
|---|---|
| `GestureEngine.scan()` | 建 `MLFrame`、异步分析、回调 |
| `GestureEngine.name()` | **核心**：类别编号转中文名 |
| `GestureEngine.describe()` | 拼接结果文本 |
| `GestureEngine.close()` | 释放分析器 |

## 环境要求

| 项 | 版本 |
|---|---|
| Android Studio | 2021.3.1（Dolphin）及以上 |
| JDK | 11 或 17（Android Studio 自带的 JBR 即可） |
| Android Gradle Plugin | 7.3.1 |
| Gradle | 7.4 |
| compileSdk / targetSdk | 34 |
| minSdk | 24（Android 7.0） |
| NDK / CMake | **不需要** |
| 设备 | 真机 + 摄像头 |
| 网络 | **首次构建需要联网**，从华为 maven 下载依赖 |

`settings.gradle` 里必须声明华为仓库，否则解析不到依赖：

```groovy
maven { url 'https://developer.huawei.com/repo/' }
```

## 编译运行

1. 用 Android Studio 打开本目录
2. 首次 Sync 会联网从华为 maven 拉依赖
3. 首次打开会自动生成 `local.properties`（记录本机 SDK 路径），不要提交到版本库
4. 连接真机，点 Run
5. 首次启动请求相机权限，允许后看到实时画面
6. 把手势放进画面，点「识别」

命令行构建：

```bash
./gradlew assembleDebug
```

产物在 `app/build/outputs/apk/debug/app-debug.apk`。

> 工程放在中文路径下时 AGP 会报非 ASCII 路径错误，本工程已在 `gradle.properties` 加了一行 `android.overridePathCheck=true`。

### 常见编译问题

| 现象 | 原因与处理 |
|---|---|
| `Could not find com.huawei.hms:ml-computer-vision-handkeypoint` | `settings.gradle` 里没加华为 maven 仓库 |
| `Manifest merger failed : Attribute application@allowBackup` | HMS 的 aar 也声明了该属性，本工程已通过不声明 `allowBackup` 规避 |
| 首次构建超时 | 华为仓库在国内速度不稳定，多试几次或挂代理 |

## 依赖

| 依赖 | 用途 | 许可 |
|---|---|---|
| `com.huawei.hms:ml-computer-vision-handkeypoint:3.11.0.301` | 手部关键点 + 手势识别主 SDK | 华为开发者协议（闭源） |
| `...-handkeypoint-model:3.7.0.301` | 手部关键点模型 | 同上 |
| `...-gesture-model:3.7.0.301` | 手势分类模型 | 同上 |
| CameraX 1.3.0（core / camera2 / lifecycle / view） | 摄像头预览与抓帧 | Apache-2.0 |
| AndroidX AppCompat、Activity、Core | 基础 UI | Apache-2.0 |

> 华为 HMS Core ML Kit 是**闭源 SDK**，构建时从华为官方 maven 下载，**不随本仓库分发**。
>

端侧手势识别不需要 AppGallery Connect 配置（不需要 `agconnect-services.json`），华为官方的 ML Kit 示例仓库同样没有配置该文件。

## 说明

- 手离镜头太远或太近都会掉识别率，手掌大致占画面 1/4 时效果最好
- 数字手势（1~9）之间容易混淆，尤其是 6 和 9
- 概率值不设阈值，低置信度结果也会显示
- 代码偏教学向，不追求工程质量
