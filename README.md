# 我的书库

“我的书库”是一款轻量级 Android 个人藏书管理应用。应用无需注册或登录，书籍和设置数据保存在用户手机本地，不使用云端数据库。

## 主要功能

- 扫描 ISBN 条形码、联网检索或手动录入书籍
- 按书名或 ISBN 搜索书籍资料，并选择检索结果
- 管理封面、实拍照片、作者、出版社、出版年份、ISBN 和图书类型
- 分别记录阅读状态、购买状态、书籍成色、购入来源、分类和书架
- ISBN 重复检查，避免同一版本的书籍被重复录入
- 书库列表、筛选、统计和阅读概况
- CSV 导入与导出、完整数据备份与恢复
- 可选的本地应用打开密码

## 隐私说明

- 藏书记录保存在手机本地 SQLite 数据库中。
- 应用不提供账户系统，也不会把本地藏书数据库上传到云端。
- 只有在用户主动进行联网检索时，应用才会向图书信息来源发送搜索关键词或 ISBN。
- 相机权限用于扫描条形码和拍摄书籍状态照片。
- 请定期使用应用内的备份功能保存重要数据。

更完整的说明见 [PRIVACY.md](PRIVACY.md)。

## 开发环境

- Android Studio
- JDK 17
- Android SDK 36
- 最低支持 Android 8.0（API 26）

## 构建与运行

1. 使用 Android Studio 打开本目录。
2. 等待 Gradle 同步完成。
3. 连接 Android 手机或启动模拟器。
4. 选择 `app`，点击运行按钮。

也可以在 Windows PowerShell 中构建调试包：

```powershell
.\gradlew.bat assembleDebug
```

调试 APK 将生成在：

```text
app\build\outputs\apk\debug\app-debug.apk
```

公开源码不包含作者的正式签名文件。需要发布正式版本时，请自行创建签名密钥，并在本机或 GitHub Actions 的安全变量中配置；不要把密钥、密码或 `local.properties` 提交到仓库。

## 开源许可

本项目使用 Apache License 2.0，详情见 [LICENSE](LICENSE) 和 [NOTICE](NOTICE)。
