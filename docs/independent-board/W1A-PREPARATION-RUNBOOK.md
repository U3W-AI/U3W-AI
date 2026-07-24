# W1A 默认关闭发布准备运行手册

## 目标与边界

本手册把精确的官方身份
`fbsir-eight-seat-board@26.7.21 / board-convener / experts`
按顺序推进到 `PREPARED_FOR_STAGE`、`STAGED_FOR_SWITCH` 和
`DEPLOYED_DEFAULT_OFF`。前三种状态都不等于已经取得真实自然流量同绑定证据；
只有完成后续 WorkBuddy 官方入口联调和六维账本读回，才可声明第一阶段业务闭环。

官方 Experts 内容树为冻结只读输入，任何审批均必须包含
`officialExpertsPackageChange=false`。所有 W1A 主开关和纵向切片开关保持显式
`false`。门禁管理十二项：四个兼容主开关、四个 Spring relaxed-binding
规范主开关和四个纵向切片开关。

## 已冻结的采用前备份

遗留库采用只允许使用以下已经独立恢复验证的采用前锚点：

- RunId：`w1a-20260723T171002Z-1b39c60f77cc`
- 源提交：`e059daeacb149b83f85a260bb13bcf1ea705e437`
- 外部 bundle 回执 SHA-256：
  `28ec6019a402e195a86bd67db1c5717367212957c63b9ea9c78f9643f043a733`
- 加密备份 SHA-256：
  `5e1f6028c98eace401c6703391e049a56cd5e5258c3701af2b49eb5e867defcd`
- 加密备份大小：`412045051` 字节
- 恢复事实：83 张 BASE TABLE、16 个 VIEW、0 个 trigger/routine/event；
  恢复后隔离数据已删除。

本锚点只授权记录式遗留基线采用。采用完成后必须生成新的最终备份；两者不能互换。

## 共同前置条件

1. 当前分支、HEAD、upstream 与 GitHub 远端分支完全一致，工作树无任何未跟踪或修改文件。
2. SSH 私钥现场派生出的公钥指纹、目标主机指纹均与 runner 内固定值一致。
3. 所有 approval 放在仓库外的证据目录，采用 UTF-8 JSON、精确字段集合、最长 24 小时有效期。
4. approval 只能在对应 `Plan` 返回实时前态后创建；不得提前猜测数据库 UUID、环境摘要或代码摘要。
5. approval JSON 是本次操作范围和锚点的审计回执，不是可脱离当前会话使用的密码学授权。实际授权边界是用户的明确指令，加上固定 Git 提交、固定 SSH 密钥和固定主机指纹的共同保管；不得把 JSON SHA-256 描述成数字签名或可转授权凭据。
6. 每个 mutating runner 的输出和外部锚点都保存到
   `deliverables/production-evidence`，禁止写入官方 Experts 包。

准备合同：

```powershell
$env:U3W_NODE_EXE = '<verified-node.exe>'
$env:U3W_PYTHON_EXE = '<verified-python.exe>'
powershell -NoProfile -ExecutionPolicy Bypass `
  -File scripts/verify-u3w-preparation-contract.ps1

powershell -NoProfile -ExecutionPolicy Bypass `
  -File scripts/run-u3w-legacy-baseline-control-shape-mysql-it.ps1 `
  -AllowDestructiveTest
```

## 顺序一：默认关闭配置与密钥托管

先固定一个 `w1a-config-<UTC>-<12hex>` RunId，以当前 clean/pushed HEAD 执行：

```powershell
$commit = (git rev-parse HEAD).Trim()
powershell -NoProfile -ExecutionPolicy Bypass `
  -File scripts/run-u3w-default-off-configuration.ps1 `
  -Mode Plan -ExpectedCommit $commit -RunId <config-run-id>
```

`Plan` 必须证明 API2 事件密钥前态为 `ABSENT`，并返回：

- 当前 `/etc/u3w/fbsir-admin.env` SHA-256；
- runner 与 worker SHA-256；
- `InvocationID`、`ExecMainStartTimestampMonotonic`、`NRestarts`；
- 目标目录和环境文件托管事实。

据此创建 action 为
`CONFIGURE_W1A_DEFAULT_OFF_CRYPTO_CUSTODY` 的精确 approval，绑定
`expectedEnvironmentSha256`、`expectedApi2EventKeyState=ABSENT`、
runner/worker SHA。权限范围只能是：

- `productionFilesystemWrite=true`
- `productionDatabaseWrite=false`
- `productionServiceChange=false`
- `officialExpertsPackageChange=false`

然后使用同一 RunId 和 Plan 返回的环境摘要执行 `Apply`。若环境文件已经替换但本地锚点
尚未完成，只能使用新的 action
`RECOVER_W1A_DEFAULT_OFF_CONFIGURATION_ANCHOR` approval 进入 `Recover`；
恢复审批还必须绑定已配置环境 SHA 和原 approval SHA。

成功回执必须证明：

- 十二个开关均显式为 `false`；
- U3W event key 是同一原始 API2 key 的 `base64:` 表示；
- same-binding secret 独立生成；
- 环境备份与活文件均为 root:root、0600、单硬链接；
- 服务启动身份与执行前一致，`configurationLoaded=false`；
- `productionFilesystemChanged=true`，数据库、服务和官方包均未变化。

## 顺序二：遗留库记录式基线采用

使用新的 `w1a-baseline-<UTC>-<12hex>` RunId 和冻结的采用前 bundle 锚点执行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass `
  -File scripts/run-u3w-legacy-baseline.ps1 `
  -Mode Plan `
  -ExpectedCommit $commit `
  -ExpectedBackupBundleReceiptSha256 `
    28ec6019a402e195a86bd67db1c5717367212957c63b9ea9c78f9643f043a733 `
  -RunId <baseline-run-id>
```

以 Plan 返回的 `databaseEndpoint`、`databaseServerUuid`、runner/worker SHA 创建 action
为 `ADOPT_LEGACY_SCHEMA_BASELINE` 的 approval。该审批只允许创建：

- 规范 `u3w_schema_migration`；
- `u3w_legacy_schema_baseline_receipt_v2`；
- 一条 `legacy_w1a_baseline_20260724_001` 记录和对应不可变回执。

不得补写或声称执行 `public_init_001` 至 `public_init_042`，不得改动既有业务投影。
runner 在首个 DDL 前验证 `BACKUP_ADMIN`，使用 `LOCK INSTANCE FOR BACKUP`、
显式 `REPEATABLE READ`、控制行与 gap 锁，并在事务内和提交后重算业务投影及
`T/C/I/K/U/F/H/G/P` 控制覆盖。

若数据库已经提交但远端或本地锚点中断，只能以 action
`RECOVER_LEGACY_SCHEMA_BASELINE_ANCHOR` 的新 approval 执行 `Recover`；恢复审批必须
绑定既有 adoption receipt SHA 和原 approval SHA，且
`productionDatabaseWrite=false`。

## 顺序三：采用后的最终备份与隔离恢复

基线采用完成后，使用当前同一 clean/pushed HEAD 和新的 `w1a-<UTC>-<12hex>` RunId
重新执行生产备份与隔离恢复。此时预期 BASE TABLE 数为 85；以实时 Plan 为准，
不得手工改写回执。

```powershell
powershell -NoProfile -ExecutionPolicy Bypass `
  -File scripts/run-u3w-production-backup-restore.ps1 `
  -Mode Plan -ExpectedCommit $commit -RunId <final-backup-run-id>

# 创建 DATABASE_BACKUP_AND_ISOLATED_RESTORE approval 后：
powershell -NoProfile -ExecutionPolicy Bypass `
  -File scripts/run-u3w-production-backup-restore.ps1 `
  -Mode All -ExpectedCommit $commit -RunId <final-backup-run-id> `
  -ApprovalReceiptPath <approval-json>
```

最终外部 bundle SHA 是发布就绪门禁的
`ExpectedBackupReceiptSha256`；不得继续使用采用前 bundle SHA 代替。

## 顺序四：Build 与只读发布 Plan

以同一 `w1a-release-<commit12>-<UTC>` ReleaseId 先 Build，再用 Build 返回的回执路径
与外部计算的回执 SHA 执行 Plan：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass `
  -File scripts/deploy-independent-board-default-off.ps1 `
  -Mode Build -ExpectedCommit $commit -ReleaseId <release-id>

powershell -NoProfile -ExecutionPolicy Bypass `
  -File scripts/deploy-independent-board-default-off.ps1 `
  -Mode Plan -ExpectedCommit $commit -ReleaseId <release-id> `
  -BuildReceiptPath <build-receipt-json> `
  -ExpectedBuildReceiptSha256 <build-receipt-sha256>
```

Build 回执必须绑定后端 JAR、前端树、验证日志、打包日志和当前 committed runner。
Plan 必须绑定活动 JAR、systemd unit、env 托管、Nginx、release/current 拓扑与生成后
24 小时有效期。

`Stage/Apply/Rollback/Verify` 均执行当前 committed worker，并绑定同一 Build、
Plan、最终备份、遗留基线和配置回执。未取得 `PREPARED_FOR_STAGE` 前，
`Stage` 不会上传工件。

## 顺序五：只读 PREPARED 门禁

准备好四个仓外 SHA-256 锚点后执行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass `
  -File scripts/verify-independent-board-production-readiness.ps1 `
  -ExpectedCommit $commit `
  -ExpectedBackupReceiptSha256 <final-backup-bundle-sha256> `
  -ExpectedLegacyBaselineReceiptDigest <legacy-adoption-receipt-sha256> `
  -ExpectedConfigurationReceiptSha256 <configuration-receipt-sha256> `
  -ExpectedReleasePlanReceiptSha256 <release-plan-receipt-sha256> `
  -RequiredStage PREPARED_FOR_STAGE `
  -RequireReady
```

报告默认写入被 Git 忽略的
`work/production-readiness/w1a-production-readiness-latest.json`。门禁会在同一个共享生产锁
窗口中重新采集线上事实，并再次检查本地 strict HEAD、计划有效期和目标漂移。

## 顺序六：独立 Stage

创建 action 为 `STAGE_W1A_DEFAULT_OFF_RELEASE` 的精确 approval。它必须绑定
Build、Plan、最终备份、遗留基线、配置、runner 和 worker SHA；Stage 与 Deployment
锚点均为 64 个 `0`，权限只能是：

- `productionFilesystemWrite=true`
- `productionDatabaseWrite=false`
- `productionServiceChange=false`
- `officialExpertsPackageChange=false`

执行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass `
  -File scripts/deploy-independent-board-default-off.ps1 `
  -Mode Stage -ExpectedCommit $commit -ReleaseId <release-id> `
  -BuildReceiptPath <build-receipt-json> `
  -ExpectedBuildReceiptSha256 <build-receipt-sha256> `
  -PlanReceiptPath <plan-receipt-json> `
  -ExpectedReleasePlanReceiptSha256 <release-plan-receipt-sha256> `
  -ExpectedBackupReceiptSha256 <final-backup-bundle-sha256> `
  -ExpectedLegacyBaselineReceiptDigest <legacy-adoption-receipt-sha256> `
  -ExpectedConfigurationReceiptSha256 <configuration-receipt-sha256> `
  -ApprovalReceiptPath <stage-approval-json>
```

runner 会先重新关闭 `PREPARED_FOR_STAGE` 门禁，再创建隔离 incoming 目录、上传精确
JAR/前端/043/committed runner 与 worker、在目标端重算摘要并原子完成 release
目录。Stage 只生成应用回滚“装配”回执，不声称已经切换、重启或执行回滚。

## 顺序七：默认关闭 Apply 与实时回滚演练

以 Stage 返回的实际回执 SHA 创建 action 为
`APPLY_W1A_DEFAULT_OFF_RELEASE` 的新 approval；Deployment 锚点仍为 64 个 `0`，
且 filesystem/database/service 三项写权限均为 `true`。执行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass `
  -File scripts/deploy-independent-board-default-off.ps1 `
  -Mode Apply -ExpectedCommit $commit -ReleaseId <release-id> `
  -BuildReceiptPath <build-receipt-json> `
  -ExpectedBuildReceiptSha256 <build-receipt-sha256> `
  -PlanReceiptPath <plan-receipt-json> `
  -ExpectedReleasePlanReceiptSha256 <release-plan-receipt-sha256> `
  -ExpectedBackupReceiptSha256 <final-backup-bundle-sha256> `
  -ExpectedLegacyBaselineReceiptDigest <legacy-adoption-receipt-sha256> `
  -ExpectedConfigurationReceiptSha256 <configuration-receipt-sha256> `
  -ExpectedStageReceiptSha256 <stage-receipt-sha256> `
  -ApprovalReceiptPath <apply-approval-json>
```

Apply 的顺序固定为：追加应用 043、候选启动、候选到不可变前序版本的真实回滚、
验证 043 仍保留且默认关闭、再次启动候选、写部署回执。部署回执原子落盘成功是
提交点；提交前任何失败都必须先清除精确的未提交 marker 再恢复前序应用。提交后若
仅 `latest-receipt` 链接修复失败，不得把候选回退；应以同一 release 重跑 Apply
幂等修复链接。数据库策略始终是
`RETAIN_ADDITIVE_043_DORMANT_NO_DOWN`，不得宣称执行数据库 down。

目标进程必须证明精确 argv、活动与配置 JAR 一致、十二项进程环境开关为
`false`、无 Spring/Java 高优先级覆盖变量，且
`application-connector.yml` 只含已审阅的
`fbsir.connector-insights.ingest-enabled` 布尔语义树。该 YAML 由目标
PyYAML 6.0.1 SafeLoader 解析并拒绝重复键、多余文档语义和任何扩展属性。
Spring 工作目录及 `config/` 默认搜索面不得出现其他
`application*.yml/yaml/properties`；大小写或 relaxed-binding 重名环境变量、
任意 `SPRING_*` 与 Java options 覆盖同样阻断。

## 顺序八：部署验证与应急 Rollback

以实际 Deployment 回执 SHA 创建 action 为
`VERIFY_W1A_DEFAULT_OFF_RELEASE` 的只读 approval，四项变更权限均为 `false`，
执行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass `
  -File scripts/deploy-independent-board-default-off.ps1 `
  -Mode Verify -ExpectedCommit $commit -ReleaseId <release-id> `
  -ExpectedDeploymentReceiptSha256 <deployment-receipt-sha256> `
  -ApprovalReceiptPath <verify-approval-json>

powershell -NoProfile -ExecutionPolicy Bypass `
  -File scripts/verify-independent-board-production-readiness.ps1 `
  -ExpectedCommit $commit `
  -ExpectedBackupReceiptSha256 <final-backup-bundle-sha256> `
  -ExpectedDeploymentReceiptSha256 <deployment-receipt-sha256> `
  -ExpectedLegacyBaselineReceiptDigest <legacy-adoption-receipt-sha256> `
  -ExpectedConfigurationReceiptSha256 <configuration-receipt-sha256> `
  -ExpectedReleasePlanReceiptSha256 <release-plan-receipt-sha256> `
  -RequiredStage DEPLOYED_DEFAULT_OFF -RequireReady
```

`Rollback` 只用于部署后的应急恢复。其 approval action 为
`ROLLBACK_W1A_DEFAULT_OFF_RELEASE`，绑定实际 Deployment SHA，
filesystem/service 为 `true`、database 为 `false`；runner 会从部署回执恢复其余
锚点，因此不依赖当前 Build 输出或 Plan 有效期。回滚只恢复应用、Nginx 和 unit
拓扑，保留追加 043 为默认关闭状态。

## 完成定义与下一门

达到 `DEPLOYED_DEFAULT_OFF` 时，只允许声明：

- 默认关闭候选、两个 portal 和 043 已部署并由实际活动工件回读；
- 应用回滚已真实演练，数据库仅证明 forward-only 安全；
- 十二项默认关闭、密钥托管、最终可恢复备份和仓外回执均已验证；
- 官方 Experts 包未修改。

仍不得声明真实自然调用、真实意图分布或 admin 六维读回。下一门是部署 API2 当前
候选，并从 WorkBuddy 已上架官方入口发起同一 binding 的
`entry_opened → intent_observed → first_value_readback`，完成 U3W 追加账本、
admin 只读读回和自然/探针/诊断/合成流量隔离证据。
