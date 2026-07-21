# 目标电脑冷启动检查表

## 包与 Git

- [ ] ZIP SHA-256 与同名 `.sha256` 一致。
- [ ] `restore-and-verify.ps1` 返回 `PASS`。
- [ ] HEAD、分支和远端与 `handoff/package-manifest.json` 一致。
- [ ] `git fsck --full` 通过，`git status --short` 为空。

## 环境

- [ ] Git、JDK 17、Maven 3.8+、Node 18+、npm 9+ 可用。
- [ ] `npm.cmd ci` 可从 lockfile 重建前端依赖。
- [ ] MySQL 精确构建已安装；非标准路径设置 `U3W_MYSQL_BIN`。
- [ ] 完整登录闭环需要时，Redis 6+ 可用。
- [ ] `FBSIR_FILE_PATH` 指向目标机可写的数据目录。
- [ ] MySQL login-path、测试凭据和环境变量在目标机重新创建，未从源机复制。

## 仓库门禁

- [ ] Contract 门禁通过。
- [ ] All 门禁通过（当前证据基线：179 suites / 838 tests + frontend production build）。
- [ ] 专用临时 MySQL 事务门禁通过（55 direct + 3 refresh + 36 canonical receipts）。
- [ ] 菜单迁移 13 阶段门禁通过。

## 运行时事实重建

- [ ] 源机 host truth 报告已标记为历史快照。
- [ ] 目标机 WorkBuddy/插件 current-read 已重新采集。
- [ ] 官方入口加载、自然调用、probe、synthetic traffic 分开记录。
- [ ] 未把上架事实推断为业务归因或服务侧闭环。

## 安全与边界

- [ ] 未复制 token、Cookie、私钥、数据库数据目录或浏览器 profile。
- [ ] 26.7.20 冻结专家包未被修改。
- [ ] 公共 OAuth/MCP 路由保持关闭。
- [ ] W4b.2 页面候选保持默认关闭并复用现有若依体系。
