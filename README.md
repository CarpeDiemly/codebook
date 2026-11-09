# CodeBook（密码本）

<img src="docs/assets/app-icon.png" width="96" alt="CodeBook 图标">

离线安卓密码管理器：账号、密码、密码类型、备注都存在手机本地的一个**加密文件**里。
应用**不申请 INTERNET 权限**，因此系统内核层面就无法建立网络连接——不是"代码里没写请求"。

图标：白色密码本 + 挖空钥匙孔 + 青绿色书签，深蓝渐变底。自适应图标（API 26+）并附带
Android 13+ 主题图标的单色层；矢量绘制，无位图，设计源文件在 `design/icon.svg`，
预览可用 `python tools/render_icon_preview.py` 重新生成。

## 当前进度

| 阶段 | 内容 | 状态 |
|---|---|---|
| P0 | 工具链、工程骨架、签名、冒烟构建 | ✅ |
| P1 | 加密核心 + 建库 / 解锁 / 列表 | ✅ |
| P2 | 条目增删改查、搜索、复制自动清空、自动锁定 | ✅ |
| P3 | JSON / HTML 导入导出 | ⏳ |
| P4 | 指纹解锁、改主密码、双语切换、设置页 | ⏳ |
| P5 | 文档、CI、GitHub 发布 | ⏳ |

## 技术栈

- Kotlin 2.4.21 + Jetpack Compose（Material 3），AGP 9.5.1 / Gradle 9.9
- 内置 Kotlin（AGP 9 特性，无需 `kotlin-android` 插件）
- minSdk 26（Android 8.0）、targetSdk 36、compileSdk 37.0
- 无数据库、无网络库、无统计/崩溃上报

## 安全设计（摘要）

- 主密码 → PBKDF2-HMAC-SHA256（60 万次 + 随机盐）→ KEK
- 随机 DEK 加密全部条目（AES-256-GCM），DEK 由 KEK 包装后存入 `files/vault.cb`
- 文件写入：临时文件 + fsync + 原子替换，并保留 `.bak`
- 关闭系统备份、`FLAG_SECURE` 防截屏、条目密码默认打码
- 更完整说明见 `docs/SECURITY.md`（待补）

## 构建

工具链全部安装在项目内的 `.tools/`（已 gitignore），不污染系统环境：

```powershell
. .\tools\env.ps1                       # 设置 JAVA_HOME / ANDROID_HOME / GRADLE_USER_HOME
.\.tools\gradle\bin\gradle.bat :app:assembleDebug
.\.tools\gradle\bin\gradle.bat :app:testDebugUnitTest
```

产物：`app/build/outputs/apk/debug/app-debug.apk`，同时会复制一份到 `dist/`。

## 安装到手机

方式一（推荐）：手机开启「开发者选项 → USB 调试」后连接电脑：

```powershell
adb install -r dist\CodeBook-0.1.0-debug.apk
```

方式二：把 `dist/` 里的 APK 拷到手机，点击安装（需允许"安装未知来源应用"）。

## 签名

`keys/codebook-release.jks` 与 `keystore.properties` **不入库**，请自行备份。
签名一旦更换，已安装的 App 只能卸载重装（数据会丢失，除非先导出）。

## 许可证

本项目以 [GNU General Public License v3.0](LICENSE) 发布。

Copyright (C) 2026 yangli

这意味着你可以自由使用、修改和分发本软件，但衍生作品必须同样以 GPLv3 开源，且不提供任何担保。
