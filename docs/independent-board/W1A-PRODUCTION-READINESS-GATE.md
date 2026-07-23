# W1A 生产就绪只读门禁

## 目的

在上传 JAR、执行数据库迁移、修改 Nginx、重启服务或切流之前，以同一份可机读结果证明：

1. 本地源码为干净且精确的 40 位 Git HEAD；
2. 生产访问权限被限定到已钉住主机密钥的 `fbsir-admin.service`；
3. 当前运行 JAR、服务状态和摘要可回读；
4. 生产库运行在由当前迁移 SHA、官方身份、追加/重放/不可变性和 Schema 指纹回执共同证明的精确 MySQL 构建上；当前已验证 `8.0.30`、生产同版 `8.0.45` 与 `8.4.8`。Schema 基线只接受两种模式：
   - `CANONICAL_MANIFEST`：具备描述精确匹配且以 `APPLIED:` 开头的 `public_init_035` 至 `public_init_042`；
   - `LEGACY_ADOPTED_W1A_V1`：不伪造历史回执；只有受控 runner 可中断恢复，且 collector 独立重算并逐项匹配实时 Schema、JAR、备份、恢复、runner 与源码提交后才可接受。当前实现固定不放行该模式；
   若已应用 043，还必须精确匹配双版本验证共同产出的列、索引、约束、CHECK 和触发器正文指纹；
5. 数据库备份非空、摘要一致，且恢复步骤已有独立验证回执；
6. W1A 四个主开关和四个纵向切片开关均显式为 `false`；
7. 事件验签密钥和 same-binding 密钥已经托管，但门禁不输出密钥值或摘要；
8. 本地 strict-HEAD 发布计划与回滚契约先独立验证；上传后再验证 staged 回执；切换后再验证默认关闭部署与应用/数据库联合回滚回执。
   部署回执中的后端、前端、runner 和两个回滚回执必须位于固定 release 根目录并重新计算实际文件摘要，不能依赖自报布尔值或摘要格式。

准备门禁、目标 staging 和默认关闭部署分为 `PREPARED_FOR_STAGE`、`STAGED_FOR_SWITCH`、
`DEPLOYED_DEFAULT_OFF`；准备门禁任一条件不满足时结果固定为
`NOT_READY_FOR_PRODUCTION_RELEASE`。部署后证据不再反向制造上传前门禁的循环依赖。

## 命令

只读采集并生成报告：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/verify-independent-board-production-readiness.ps1
```

发布命令必须追加 `-RequireReady`。未满足全部门禁时进程失败退出：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass `
  -File scripts/verify-independent-board-production-readiness.ps1 `
  -ExpectedBackupReceiptSha256 <out-of-band-sha256> `
  -ExpectedDeploymentReceiptSha256 <out-of-band-sha256> `
  -ExpectedLegacyBaselineReceiptDigest <out-of-band-sha256> `
  -RequireReady
```

纯规则测试：

```text
node --test scripts/independent-board-production-readiness.test.mjs
```

## 安全边界

- 脚本只执行 `systemctl show`、文件摘要、JAR 条目统计、HTTP GET 和数据库 `SELECT`。
- 数据库密码只在远端子进程环境中使用，不进入命令行、标准输出或报告。
- 环境文件只输出所需配置项是否存在、开关是否显式为 false；不输出任何配置值。
- 生产目标、服务单元、SSH 公钥和主机指纹在脚本内固定；不能用调用参数降级。
- 不提供离线快照放行入口；`-RequireReady` 必须执行实时 SSH 采集并提供相应的外部回执摘要锚点。
- MySQL 版本不是手写白名单；门禁只接受仓内真实隔离集成回执中、与当前 `public_init_043` SHA 和精确 Schema 指纹绑定的版本。
- standalone `public_init_043` 兼容回执只关闭 `w1a_043_mysql_compatibility`，不能替代完整 canonical 基线，也不能替代旧库 adoption 回执。
- 旧库接管 runner 尚未达到可提交标准；在独立实时事实交叉验证和逐 DDL 中断恢复测试完成前，门禁固定拒绝该模式。未来路径也不得补写 `public_init_001` 至 `public_init_042`。
- 持久化报告使用字段白名单，不透传远端或输入对象中的未知字段。
- 本门禁不是发布脚本，不会创建备份、上传工件、执行迁移、切换软链接、修改 Nginx、重启服务或切流。
- `PREPARED_FOR_STAGE` 仅代表允许进入独立 staging 命令，不代表已上传、已部署、已激活或已取得真实同绑定证据。

## 当前生产结论

2026-07-23 的隔离集成已消除生产 MySQL `8.0.45` 的版本兼容性未知项，但只读执行结果仍为 `NOT_READY_FOR_PRODUCTION_RELEASE`：生产库没有
`u3w_schema_migration`，备份/恢复回执不存在，W1A 显式关闭配置与密码学材料尚未托管，
严格 HEAD 发布及应用/数据库联合回滚回执也不存在。官方 Experts 包未被读取后写回，
更未被修改。
