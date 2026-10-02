# GitHub 上传检查清单

上传前请逐项确认：

- [ ] 当前操作目录是 `github-release`，不是原项目根目录
- [ ] 不存在 `signing`、`local.properties`、`.jks` 或 `.keystore` 文件
- [ ] 不存在 APK、AAB、数据库、CSV、JSON 备份、照片或日志
- [ ] `git status` 中只有计划公开的源码和说明文件
- [ ] 仓库可见性已经按需要设置为 Public 或 Private
- [ ] 正式 APK 只通过 GitHub Releases 单独发布，并已确认签名身份

首次上传可在本目录执行：

```powershell
git init
git add .
git status
git commit -m "Initial open-source release"
git branch -M main
git remote add origin https://github.com/你的用户名/你的仓库名.git
git push -u origin main
```

执行 `git add .` 后务必先查看 `git status`，确认没有敏感文件再提交。
