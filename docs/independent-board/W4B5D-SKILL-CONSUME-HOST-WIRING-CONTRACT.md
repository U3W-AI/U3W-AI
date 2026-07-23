# W4B5D：技能消费 v2 默认关闭宿主接线合同

## 目标与不可越界范围

本切片把 W4B5C 已通过双 MySQL 验证的 `SkillConsumeCreditWriter` 接入
`SkillConsumeServiceImpl` 的个人付费消费主链，但仍是默认关闭的本地候选：

- 只有 `credit-ledger-candidate.enabled=true` 与
  `skill-consume-credit-writer.enabled=true` 同时成立时才选择 v2；
- 两个开关的仓库默认值继续为 `false`，任一开关单独开启仍走既有 legacy 路径；
- 免费包与 `ENTERPRISE` 配额路径不进入 v2 writer；
- 不新增 HTTP、MCP、菜单或管理员写入口，不执行生产迁移、部署、切流或开关启用；
- 不修改已上架并冻结的独董会 26.7.20 包。

## 宿主委派合同

个人付费请求必须先完成参数、已发布场景包、启用积分规则和
`comprehensiveCheck`。双开分支位于任何宿主 legacy usage 或 points 写入之前：

1. 宿主类型按既有兼容语义执行 `trim + uppercase` 后传给内部命令；
2. writer bean 缺失时返回 `SKILL_CONSUME_CREDIT_WRITER_NOT_READY`；
3. writer 成功或失败结果均原样返回，失败不得回退到 `IPointsService`；
4. 宿主层不得调用 legacy `usageRecordMapper`、`IPointsService` 或非幂等企微同步；
5. writer 仍可在自己的事务内写一条兼容 usage、v2 财务表和 `sys_user.points`
   投影，这不属于宿主 legacy fallback；
6. `Integer.MIN_VALUE` 积分规则不得被 `Math.abs` 溢出伪装成免费消费，必须在权益
   与写者调用前返回 `SKILL_POINTS_RULE_AMOUNT_INVALID`。

企微 Commercial Hub 同步没有稳定 operation-id 幂等/outbox 合同，本切片不把它接到
v2 精确重放之后，避免同一 usage 重放产生重复外部副作用。该能力必须在后继切片中通过
operation-id outbox 实现，或获得明确的产品排除决定。

## 真实数据库门禁

`run-independent-board-skill-consume-credit-ledger-v2-mysql-it.ps1` 必须：

- 仅在显式 `-AllowDestructiveTest` 下，使用 `127.0.0.1` 随机非 3306 端口和唯一
  `w3l_*` schema；
- 顺序运行精确 MySQL Community `8.0.30`、`8.4.8`，使用真实 Connector/J、
  Spring 事务代理、MyBatis 与 InnoDB `REPEATABLE-READ`；
- 每版精确执行 5 个固定测试名，其中宿主用例必须通过 Spring 代理调用
  `SkillConsumeServiceImpl`，并用禁止访问的宿主 legacy usage mapper 与 mock
  `IPointsService` 证明零 legacy fallback；
- 同时保留首次/精确重放、32 路同命令、异摘要、余额竞争、终态 CAS 全回滚以及
  038/042 双向权威互斥；
- 成功或失败都停止专用实例并清理本轮临时目录。

通过结果为 `PASS_LOCAL_DUAL_MYSQL_HOST_PATH_MATRIX`，只属于
`application runtime passed` 的本地证据，不能提升为 `production verified`。

## 历史证据接续

W4B4、W4B5A、W4B5B、W4B5C、W3L 和 W3K 历史回执保持原字节不变。W4B5D 新回执
必须：

- 固定 W4B4 fence、W4B5C 双 MySQL和 W3K authority 三个前驱回执的全文件 SHA-256；
- 固定被本切片接管的宿主 service/test、writer、MySQL IT、runner、runner contract、
  中央 verifier、FBS contract、taskboard 与 implementation-status 的前驱和当前字节；
- 明确 `writerWiredIntoLegacyConsume=true` 仅表示本地默认关闭接线；
- 同时保持 `releaseReady=false`、`productionChanged=false`、
  `productionConnectionUsed=false` 和 `frozenListedPackageModified=false`。

## 尚未解除的生产阻断

宿主 `consume` 外层事务在进入 writer 的 `REQUIRES_NEW` 前可能已经占用一个连接。
高并发接近连接池上限时可能形成每请求占两连接的池饥饿。本切片不以单请求
DriverManager 真库测试掩盖该风险。生产启用前必须完成以下后继门禁：

1. 拆分为非事务 dispatcher + 独立 legacy 事务 bean，或用受控真实连接池并发矩阵证明
   明确的容量与超时策略；
2. 用真实宿主流量证明 `hostSessionId` 非空，或在不削弱幂等边界的前提下对齐公共接口“可空”
   与 v2 命令“必填”的兼容策略；
3. 建立 operation-id 幂等 Commercial Hub outbox，或形成经批准的功能排除；
4. 重新生成绑定精确提交、042 迁移 current-read、两个开关、回滚和人工批准的激活回执。

因此本切片结束后仍不得启用生产。
