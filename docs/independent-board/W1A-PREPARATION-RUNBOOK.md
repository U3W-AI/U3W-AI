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

## 2026-07-24 当前执行序列（覆盖下文历史首次配置顺序）

当前生产库已经存在精确的“独董会管理”根菜单与受控依赖记录，配置 v2、遗留
baseline v2 和采用前 backup/restore v1 也已经完成。本轮不得重做首次配置或声称
重新执行了历史数据库变更；严格顺序为：

1. 用独立 runner 先以只读 `Plan` 复算“现存且精确”的管理根依赖，再根据
   Plan 的数据库身份、实时事实和代码摘要创建审批并以同一 RunId 执行 `Adopt`，生成
   `fbsir.u3wLegacyAdminRootDependencyAdoptionReceipt.v2`。回执必须绑定数据库
   server UUID、遗留 baseline SHA、采用前 backup SHA，并证明
   `public_init_001..042`、`public_init_043`、内部 043、归因表、触发器和归因权限
   均未出现。
2. 配置 Reconcile 只允许采纳已经存在且相对 v2 唯一新增的
   `FBSIR_ENGINE_TOKEN`。本轮没有 Engine/Hub 对端，因此禁止新建 token，并在 v3
   回执中固定 `engineCounterpartClosureClaimed=false`；不得宣称 Engine 闭环。
3. 在上述采纳完成后生成新的 backup v3、restore v3 和 bundle v2，并由隔离恢复
   独立复算同一根依赖与 043 缺席事实。采用前备份不能充当本轮最终备份。
4. Build 与固定 Plan 绑定同一 strict HEAD；从 PREPARED 门禁开始，PREPARED、
   Stage、Apply、Verify 全部绑定同一个根依赖采纳回执 SHA。官方 Experts 包始终
   只读。

根依赖采纳命令：

```powershell
$commit = (git rev-parse HEAD).Trim()
$dependencyRunId = '<admin-root-dependency-run-id>'

# 零生产落库、零生产持久化文件写入的实时 Plan；runner 会把本地证据
# 不可变保存到仓外 evidence 目录。
powershell -NoProfile -ExecutionPolicy Bypass `
  -File scripts/run-u3w-admin-root-dependency.ps1 `
  -Mode Plan `
  -ExpectedCommit $commit `
  -ExpectedBaselineReceiptSha256 <legacy-adoption-receipt-sha256> `
  -ExpectedBackupReceiptSha256 <predecessor-backup-bundle-receipt-sha256> `
  -RunId $dependencyRunId

$dependencyPlanPath =
  "<production-evidence>/$dependencyRunId-admin-root-dependency-plan.json"
$dependencyPlanSha = (
  Get-FileHash -LiteralPath $dependencyPlanPath -Algorithm SHA256
).Hash.ToLowerInvariant()

# approval 必须逐项绑定上述 Plan 的原始字节 SHA；然后以同一 RunId 执行 Adopt。
powershell -NoProfile -ExecutionPolicy Bypass `
  -File scripts/run-u3w-admin-root-dependency.ps1 `
  -Mode Adopt `
  -ExpectedCommit $commit `
  -ExpectedBaselineReceiptSha256 <legacy-adoption-receipt-sha256> `
  -ExpectedBackupReceiptSha256 <predecessor-backup-bundle-receipt-sha256> `
  -RunId $dependencyRunId `
  -PlanReceiptPath $dependencyPlanPath `
  -ExpectedPlanReceiptSha256 $dependencyPlanSha `
  -ApprovalReceiptPath <approval-json>
```

其 approval action 必须是
`ADOPT_W1A_043_LEGACY_ADMIN_ROOT_DEPENDENCY`，只允许生产文件系统写入回执，不
允许数据库、服务或官方包变化。`Plan` 和 `Adopt` 都在共享主机锁、迁移命名锁、
`REPEATABLE READ` 一致性快照及控制表全范围行锁/元数据锁窗口内复算事实；
审批同时显式禁止并发 DDL。Plan 不创建生产目录、生产锁文件、生产回执或业务
仓外锚点；允许 runner 在本地仓外 evidence 目录保存不可变 Plan 证据。当前环境
账号没有全局 `BACKUP_ADMIN`，因此不得伪称使用实例级 backup lock。
approval 还必须把 Plan 返回的 `liveFactsSha256` 写入
`expectedLiveFactsSha256`，并将 `expectedDatabaseProtectionMode` 精确设为 Plan
返回值，同时把本地 Plan 文件的 SHA-256 写入 `expectedPlanReceiptSha256`；
Adopt 必须验证并传入同一份 Plan 原始字节，在持锁窗口内重算后不一致即拒绝，
不允许把“两次都满足 exact”误当成同一组已审批事实。

配置采纳使用新的 RunId，并同时绑定当前环境 SHA 与 v2 前序回执 SHA：

```powershell
$configurationRunId = '<configuration-v3-run-id>'

# 先只读证明当前环境相对 v2 只有一个规范追加的既有 Engine 凭据差量。
powershell -NoProfile -ExecutionPolicy Bypass `
  -File scripts/run-u3w-default-off-configuration.ps1 `
  -Mode Plan -ExpectedCommit $commit `
  -ExpectedPredecessorConfigurationReceiptSha256 `
    <configuration-v2-receipt-sha256> `
  -RunId $configurationRunId

# 根据 Plan 创建 Reconcile approval 后执行：
powershell -NoProfile -ExecutionPolicy Bypass `
  -File scripts/run-u3w-default-off-configuration.ps1 `
  -Mode Reconcile -ExpectedCommit $commit `
  -ExpectedEnvironmentSha256 <current-environment-sha256> `
  -ExpectedPredecessorConfigurationReceiptSha256 `
    <configuration-v2-receipt-sha256> `
  -RunId $configurationRunId `
  -ApprovalReceiptPath <approval-json>
```

Reconcile Plan 必须返回 `adminEngineCredentialPresent=true`、
`exactExistingAdminEngineDeltaValid=true`、精确的 v2 前序回执 SHA，并固定
`engineCounterpartClosureClaimed=false`；否则不得创建 approval 或生产 run
directory。其 approval action 必须是
`ADOPT_EXISTING_ADMIN_ENGINE_CREDENTIAL_DELTA`；如果当前环境没有精确的既存
token，runner 必须失败，不得降级为生成新 token。

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
7. Windows PowerShell 调用 `ssh.exe` 时，所有 Python bootstrap 必须先转义双引号，并由合同测试逐处计数；未经真实远端只读启动验证，不得只凭本地静态语法通过进入生产变更。
8. 若准备完成后仅修复发布门禁自身，配置、基线、最终备份回执可以共同绑定同一个祖先准备提交，但必须同时证明：该提交是当前 strict HEAD 的 Git 祖先；三类回执的 source commit 完全一致；配置、基线、备份 runner/worker/restore verifier 与当前提交逐字节 SHA-256 一致。任一条件不满足即拒绝，不以“仅文档变化”或人工说明代替。

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
重新执行生产备份与隔离恢复。旧的 85 张静态推算已经过时；2026-07-24 只读能力
探针观察到 87 张 BASE TABLE 和 16 个 VIEW，但仍必须以执行时实时 Plan 为准，
不得手工改写回执。

生产备份使用共享主机锁、迁移命名锁、只读一致性快照，以及对 Plan 时全部现存
BASE TABLE/VIEW 持有到事务结束的元数据锁；同时在 dump 前后复算身份、对象、行数
摘要和活动 JAR，并要求 approval 的 `concurrentDdlProhibited=true`。这不是
`LOCK INSTANCE FOR BACKUP` 的替代性宣称；它是当前最小权限下经线上能力探针验证
可执行的保护合同。备份 Plan 只输出 `plannedDdlProtectionMode`，同时明确
`databaseProtectionActive=false` 和 `sourceSnapshotExactlyMatched=false`；只有
实际 Backup 回执可以使用 `ddlProtectionMode`。

```powershell
powershell -NoProfile -ExecutionPolicy Bypass `
  -File scripts/run-u3w-production-backup-restore.ps1 `
  -Mode Plan -ExpectedCommit $commit -RunId <final-backup-run-id> `
  -ExpectedAdminRootDependencyAdoptionReceiptSha256 `
    <admin-root-dependency-adoption-receipt-sha256>

# 创建 DATABASE_BACKUP_AND_ISOLATED_RESTORE approval 后：
powershell -NoProfile -ExecutionPolicy Bypass `
  -File scripts/run-u3w-production-backup-restore.ps1 `
  -Mode All -ExpectedCommit $commit -RunId <final-backup-run-id> `
  -ExpectedAdminRootDependencyAdoptionReceiptSha256 `
    <admin-root-dependency-adoption-receipt-sha256> `
  -ApprovalReceiptPath <approval-json>
```

最终备份回执文件 `/opt/fbsir/admin/backups/latest/receipt.json` 本身的
SHA-256 是发布就绪门禁的 `ExpectedBackupReceiptSha256`；它不是备份数据文件
或仓外证据 bundle 的摘要，也不得继续使用采用前回执摘要代替。

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
24 小时有效期。Plan 返回的权威路径是只读内容寻址副本
`work/release-plans/by-sha256/<release-plan-sha256>.json`；readiness 会从预期
SHA-256 推导并只读取该副本。`latest` 仅用于展示，不作为 Stage、Apply 或
readiness 的权威输入；仓外副本与内容寻址副本必须保持逐字节一致。

`Stage/Apply/Rollback/Verify` 均执行当前 committed worker，并绑定同一 Build、
Plan、最终备份、遗留基线和配置回执。未取得 `PREPARED_FOR_STAGE` 前，
`Stage` 不会上传工件。

## 顺序五：只读 PREPARED 门禁

准备好四个仓外 SHA-256 锚点后执行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass `
  -File scripts/verify-independent-board-production-readiness.ps1 `
  -ExpectedCommit $commit `
  -ExpectedBackupReceiptSha256 <final-backup-receipt-file-sha256> `
  -ExpectedLegacyBaselineReceiptDigest <legacy-adoption-receipt-sha256> `
  -ExpectedAdminRootDependencyAdoptionReceiptSha256 `
    <admin-root-dependency-adoption-receipt-sha256> `
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
  -ExpectedBackupReceiptSha256 <final-backup-receipt-file-sha256> `
  -ExpectedLegacyBaselineReceiptDigest <legacy-adoption-receipt-sha256> `
  -ExpectedAdminRootDependencyAdoptionReceiptSha256 `
    <admin-root-dependency-adoption-receipt-sha256> `
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
  -ExpectedBackupReceiptSha256 <final-backup-receipt-file-sha256> `
  -ExpectedLegacyBaselineReceiptDigest <legacy-adoption-receipt-sha256> `
  -ExpectedAdminRootDependencyAdoptionReceiptSha256 `
    <admin-root-dependency-adoption-receipt-sha256> `
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
  -ExpectedBackupReceiptSha256 <final-backup-receipt-file-sha256> `
  -ExpectedDeploymentReceiptSha256 <deployment-receipt-sha256> `
  -ExpectedLegacyBaselineReceiptDigest <legacy-adoption-receipt-sha256> `
  -ExpectedAdminRootDependencyAdoptionReceiptSha256 `
    <admin-root-dependency-adoption-receipt-sha256> `
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
