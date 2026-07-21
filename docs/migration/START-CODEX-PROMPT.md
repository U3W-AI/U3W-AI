# Codex 续接提示

请从当前仓库继续福帮手（FBSir）独董会控制面开发。先执行只读冷启动，不要从聊天历史猜测当前事实：

1. 确认仓库根、分支、HEAD、远端和干净工作树；
2. 完整阅读 `.fbs-engineering/contract.json`、`docs/independent-board/AUTHORITATIVE-ROOT.md`、
   `docs/independent-board/implementation-status.json`、`docs/independent-board/taskboard.json`、
   `docs/independent-board/W4B-OAUTH-MCP-AUTHORIZATION-CONTRACT.md` 和
   `reports/independent-board/w4b-oauth-refresh-security-verification-20260721.json`；
3. 运行 Contract 门禁；环境依赖满足后再运行 All、数据库事务和菜单迁移门禁；
4. 把所有含源电脑绝对路径的 host truth 报告标为 `source_machine_snapshot`，不得晋级为目标电脑 current-read；
5. 不修改已官方上架并冻结的 26.7.20 独董会专家包，不依赖本仓库外的旧源码目录；
6. 当前产品下一切片是 W4b.2：在现有 U3W-AI 若依壳内实现默认关闭的 me/admin OAuth 与 Connector 候选页面；
7. 在 WorkBuddy 单飞刷新、原子 token 存储、丢响应歧义处理、`invalid_grant` 重授权体验、限流告警和 HTTP 负向矩阵完成前，所有公共 OAuth/MCP 路由保持关闭；
8. 官方上架、目标机宿主加载、自然调用、same-binding 归因、服务侧闭环和 `BUSINESS_CONFIRMED` 必须分别记录，不得跨层推断。

完成冷启动后，先给出当前事实、环境缺口、验证结果和一个最小下一步，再进行任何写入。
