# W1A 生产就绪只读门禁

## 目的

在上传 JAR、执行数据库迁移、修改 Nginx、重启服务或切流之前，以同一份可机读结果证明：

1. 本地源码为干净且精确的 40 位 Git HEAD；
2. 生产访问权限被限定到已钉住主机密钥的 `fbsir-admin.service`；
3. 当前运行 JAR、服务状态和摘要可回读；
4. 生产库运行在由当前迁移 SHA、官方身份、追加/重放/不可变性和 Schema 指纹回执共同证明的精确 MySQL 构建上；当前已验证 `8.0.30`、生产同版 `8.0.45` 与 `8.4.8`。Schema 基线只接受两种模式：
   - `CANONICAL_MANIFEST`：具备描述精确匹配且以 `APPLIED:` 开头的 `public_init_035` 至 `public_init_042`；
   - `LEGACY_ADOPTED_W1A_V2`：不伪造历史回执；受控 runner 只创建规范迁移账本、V2 采纳回执表和一条 `legacy_w1a_baseline_20260724_001` 记录。collector 必须独立重算并逐项匹配实时 Schema、JAR、采用前备份、最终备份、恢复、审批、runner、worker 与源码提交；
   若已应用 043，还必须精确匹配双版本验证共同产出的列、索引、约束、CHECK 和触发器正文指纹；
5. 数据库备份非空、摘要一致，且恢复步骤已有独立验证回执；
6. W1A 四个兼容主开关、四个 Spring relaxed-binding 规范主开关和四个纵向切片开关，共十二项均显式为 `false`；
7. 事件验签密钥和 same-binding 密钥已经托管，但门禁不输出密钥值或摘要；
8. 本地 strict-HEAD Build 和 24 小时内有效的只读发布 Plan 已独立验证，并与实时 unit、活动 JAR、env、Nginx 和 release/current 拓扑一致；
9. 上传后再验证 staged 回执；
10. 切换后再验证默认关闭部署、应用真实回滚演练与数据库 forward-only 安全回执。
   部署回执中的后端、前端、runner 和两个回滚回执必须位于固定 release 根目录并重新计算实际文件摘要，不能依赖自报布尔值或摘要格式。

准备门禁、目标 staging 和默认关闭部署分为 `PREPARED_FOR_STAGE`、`STAGED_FOR_SWITCH`、
`DEPLOYED_DEFAULT_OFF`；准备门禁任一条件不满足时结果固定为
`NOT_READY_FOR_PRODUCTION_RELEASE`。部署后证据不再反向制造上传前门禁的循环依赖。

approval JSON 只记录用户已经明确授权的精确变更范围和锚点，不自证授权，也不是数字签名。执行权限由当前交互授权、严格 HEAD、固定 SSH 密钥与固定主机指纹共同约束。

## 命令

只读采集并生成报告：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/verify-independent-board-production-readiness.ps1
```

发布命令必须追加 `-RequireReady`。未满足全部门禁时进程失败退出：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass `
  -File scripts/verify-independent-board-production-readiness.ps1 `
  -ExpectedCommit <clean-pushed-40-hex-head> `
  -ExpectedBackupReceiptSha256 <out-of-band-sha256> `
  -ExpectedLegacyBaselineReceiptDigest <out-of-band-sha256> `
  -ExpectedConfigurationReceiptSha256 <out-of-band-sha256> `
  -ExpectedReleasePlanReceiptSha256 <out-of-band-sha256> `
  -RequiredStage PREPARED_FOR_STAGE `
  -RequireReady
```

准备合同与精确 MySQL 8.0.45 控制层验形：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass `
  -File scripts/verify-u3w-preparation-contract.ps1

powershell -NoProfile -ExecutionPolicy Bypass `
  -File scripts/run-u3w-legacy-baseline-control-shape-mysql-it.ps1 `
  -AllowDestructiveTest
```

## 安全边界

- 脚本只执行 `systemctl show`、文件摘要、JAR 条目统计、HTTP GET 和数据库 `SELECT`。
- 数据库密码只在远端子进程环境中使用，不进入命令行、标准输出或报告。
- 环境文件只输出所需配置项是否存在、开关是否显式为 false；不输出任何配置值。
- `application-connector.yml` 只允许已审阅的
  `fbsir.connector-insights.ingest-enabled` 布尔语义树；目标端固定使用
  PyYAML 6.0.1 SafeLoader、多文档递归展开并拒绝重复键。任何 Spring
  import、inline map 扩展或独董会属性都会阻断发布。
- Spring 默认搜索面只能存在这一份已审阅文件；工作目录中的其他
  `application*.yml/yaml/properties`、`config/` 子树扩展、大小写或
  relaxed-binding 重名环境变量、任意 `SPRING_*` 和 Java options 覆盖均阻断。
- 生产目标、服务单元、SSH 公钥和主机指纹在脚本内固定；不能用调用参数降级。
- 不提供离线快照放行入口；`-RequireReady` 必须执行实时 SSH 采集并提供相应的外部回执摘要锚点。
- 配置、遗留基线与最终备份必须共享同一准备提交。若它与当前 strict HEAD 不同，只允许经本地 Git 证明为祖先，且对应 runner、worker 与恢复 verifier 的提交字节 SHA-256 全部仍与当前 HEAD 相同；否则门禁拒绝。
- MySQL 版本不是手写白名单；门禁只接受仓内真实隔离集成回执中、与当前 `public_init_043` SHA 和精确 Schema 指纹绑定的版本。
- standalone `public_init_043` 兼容回执只关闭 `w1a_043_mysql_compatibility`，不能替代完整 canonical 基线，也不能替代旧库 adoption 回执。
- V2 旧库接管采用固定 canonical 控制覆盖，首次 DDL 前验证 `BACKUP_ADMIN`，显式使用 `REPEATABLE READ`，在事务内和提交后重复验形；中断后只能通过新的、绑定原审批与既有回执摘要的 `Recover` 审批补证。任何路径都不得补写 `public_init_001` 至 `public_init_042`。
- 持久化报告使用字段白名单，不透传远端或输入对象中的未知字段。
- 本门禁不是发布脚本，不会创建备份、上传工件、执行迁移、切换软链接、修改 Nginx、重启服务或切流。
- `PREPARED_FOR_STAGE` 仅代表允许进入独立 staging 命令，不代表已上传、已部署、已激活或已取得真实同绑定证据。

## 当前生产结论

2026-07-23 的隔离集成已消除生产 MySQL `8.0.45` 的版本兼容性未知项。采用前生产备份与隔离恢复已经完成并取得仓外锚点；V2 记录式基线、默认关闭配置、密钥托管、最终备份、Build/Plan 和 PREPARED 门禁 runner 已通过本地合同与精确 MySQL 集成，但尚未在生产依次 Apply，因此当前仍是 `NOT_READY_FOR_PRODUCTION_RELEASE`。

`PREPARED_FOR_STAGE` 之前不会上传 JAR、执行 `public_init_043`、修改 Nginx、重启服务或切流。官方 Experts 包未被读取后写回，更未被修改。完整顺序、审批字段与恢复路径见
`docs/independent-board/W1A-PREPARATION-RUNBOOK.md`。
