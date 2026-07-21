# 独董会 Codex 迁移包：先读这里

本包用于把福帮手（FBSir）独董会控制面开发工作迁移到另一台 Windows 电脑。
它携带完整 Git 历史、当前已验证源码、受 Git 管理的关键证据、恢复校验工具和 Codex 续接提示，
不携带可重建缓存、数据库数据、凭据、浏览器登录态或源电脑的 WorkBuddy 用户目录。

## 1. 恢复

1. 将整个 ZIP 和同名 `.sha256` 文件复制到目标电脑。
2. 校验 ZIP：

   ```powershell
   $zip = Resolve-Path .\DDH-Codex-Handoff-*.zip
   Get-FileHash -LiteralPath $zip -Algorithm SHA256
   Get-Content -LiteralPath ($zip.Path + '.sha256')
   ```

3. 解压 ZIP，在解压目录运行：

   ```powershell
   powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\restore-and-verify.ps1 `
     -DestinationRoot D:\CodexWork
   ```

4. 用 Codex 打开恢复后的 `D:\CodexWork\u3w-independent-board-control-plane`。
5. 将 `START-CODEX-PROMPT.md` 的内容作为第一个任务发送给 Codex。

恢复脚本会校验包内文件、Git Bundle、提交、分支、完整对象库、远端地址、干净工作树和关键入口。
它不会安装软件、复制凭据、修改系统配置、部署服务或连接生产环境。

## 2. 目标电脑基线

- Windows 11 x64 与 PowerShell 5.1 或更高版本；
- Git 2.4x 或更高版本；
- JDK 17；
- Maven 3.8+（仓库没有 Maven Wrapper）；
- Node.js 18+ 与 npm 9+；
- MySQL Community 8.0.30 和/或 8.4.8，用于精确构建验证；
- Redis 6+，仅完整登录和会话闭环需要。

MySQL 不在标准目录时，设置：

```powershell
$env:U3W_MYSQL_BIN = 'D:\Tools\mysql-8.4.8\bin'
```

文件上传目录必须指向目标电脑可写位置：

```powershell
$env:FBSIR_FILE_PATH = 'D:\U3WData\uploads'
```

不要复制 `.mylogin.cnf`、数据库密码、OAuth token、Cookie、SSH 私钥或浏览器 profile。
需要真库测试时，在目标电脑重新创建 MySQL login-path 和仅测试用途的凭据。

## 3. 首次验证顺序

```powershell
git status --short
git log -1 --oneline --decorate
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\verify-independent-board-control-plane.ps1 -Mode Contract
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\verify-independent-board-control-plane.ps1 -Mode All
```

数据库破坏性门禁只允许在专用临时实例上运行，并必须显式传入 `-AllowDestructiveTest`：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\run-independent-board-mysql-transaction-it.ps1 -AllowDestructiveTest
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\run-independent-board-menu-migration-it.ps1 -AllowDestructiveTest
```

## 4. 事实边界

- 官方上架事实来自用户确认；26.7.20 专家包已经冻结，不在本控制面仓库内回写。
- 仓库中的宿主扫描报告是源电脑在 2026-07-21 的快照。迁移后只能作为历史证据，不能当作目标电脑 current-read。
- 目标电脑尚未证明 WorkBuddy 同会话官方入口加载、自然调用、same-binding 归因、服务侧闭环或生产部署。
- 当前 W4b.1 只证明本地内部 OAuth refresh security；所有公共 OAuth/MCP 路由继续关闭。

## 5. Codex 编排技能

包内 `codex\skills\fbs-engineering-orchestrator` 是生成包时的只读快照，不是构建或运行依赖。
如目标电脑尚未安装该私有技能，可人工复制到目标用户的 `.codex\skills` 后重启 Codex；
也可以先不安装，直接按仓库合同、状态和任务板继续开发。
