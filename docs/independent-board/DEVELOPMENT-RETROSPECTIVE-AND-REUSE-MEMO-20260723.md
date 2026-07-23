# 福帮手“独董会”配套平台开发复盘与复用备忘录

更新时间：2026-07-23（Asia/Shanghai）

状态：**停止继续延伸开发，保留已提交成果，封存未完成草案**

适用对象：后续产品、研发、测试、运维、发布负责人及接续智能体

## 1. 备忘录目的

本备忘录收口 2026-07-20 至 2026-07-23 围绕福帮手“独董会”配套平台开展的全部主线工作，记录：

- 总目标、边界和不可破坏约束；
- 已完成的代码、迁移、合同、验证和工程治理；
- 尚未完成或尚未证明的生产闭环；
- 关键架构决策、失败案例和可复用方法；
- 停止时的精确 Git、工件和草案状态；
- 后续恢复时唯一安全的接管顺序。

本备忘录不是发布授权，不替代任务板、实施状态、验证报告、迁移清单或生产回执。后续判断当前事实时，应优先读取本节列出的真源。

## 2. 当前权威事实

| 项目 | 当前事实 |
|---|---|
| 仓库 | `D:\ddh\DDH-Codex-Handoff-20260721-f28f8ad1\workspace\u3w-independent-board-control-plane` |
| Git 远端 | `git@github.com:U3W-AI/U3W-AI.git` |
| 当前分支 | `codex/w4b2-default-off-candidate` |
| 已提交 HEAD | `53c8e690cc408a9b428100733f7a0e37b267cfa1` |
| 远端分支 | 与上述 HEAD 对齐 |
| 工作树 | 编写本备忘录前已清洁；本备忘录是停止决定后的唯一新增文件 |
| 平台状态版本 | `0.4.9-dev` |
| 总体状态 | 本地工程候选持续完善，生产 `releaseReady=false` |
| 生产变更 | W4B5E 回执证明 `productionChanged=false` |
| 已上架专家包 | 用户已确认独董会 26.7.20 审核通过并上架 |
| 冻结包路径 | `D:\Spg719\fbsir-eight-seat-board-26.7.20.zip` |
| 冻结包 SHA-256 | `d2380072556c0dcf429604ae33713668c509f74a0303f7bca2baceebf32c78cd` |
| 冻结包写回 | 禁止；开发期间始终保持未修改 |
| 当前任务板 SHA-256 | `5e0ee704c922c3abf51d0b447552a587464c46892b8c0e5b2ecd336d7d44e9af` |
| 当前实施状态 SHA-256 | `43bcd758c7baf15046407fab08b450e931c5c5b5bf5d137d6ce22faca4a79e48` |

首要真源：

1. [AUTHORITATIVE-ROOT.md](./AUTHORITATIVE-ROOT.md)
2. [taskboard.json](./taskboard.json)
3. [implementation-status.json](./implementation-status.json)
4. [.fbs-engineering/contract.json](../../.fbs-engineering/contract.json)
5. [sql/init-manifest.json](../../sql/init-manifest.json)
6. [reports/independent-board](../../reports/independent-board)

## 3. 总目标与始终保持的边界

总目标是在不修改已上架 26.7.20 独董会包的前提下，形成：

- `me.u3w.com` VIP 用户后台；
- `admin.u3w.com` 系统管理后台；
- U3W-AI、MCP OAuth、连接器、Webhook、积分与会员权益的统一控制面；
- Apple Watch / FBSir Hub 云资产控制能力；
- 合同驱动、可测试、可分波上线、可回滚、可追溯、可扩展的工程底座。

贯穿全程的硬边界：

1. **冻结包与平台分离。** 已上架包只作为产品身份和运行时观察对象，配套平台开发不得回写冻结包。
2. **证据分层。** 源码通过、本地候选、真实 MySQL、浏览器夹具、生产部署、自然流量和同绑定业务闭环必须分别命名。
3. **默认关闭。** 未取得生产迁移、权限、域名、回滚和人工批准回执前，候选 HTTP、菜单、写者和外部 worker 均不得默认启用。
4. **失败关闭。** 身份、租户、会员、套餐、权限、幂等、迁移元数据和外部结果不确定时，不允许猜测成功。
5. **不可变回执。** 历史回执不得被新切片重写；共享源码变化必须由后继回执接管并回归前驱验证器。
6. **真实版本合同。** 数据库并发和迁移只以明确允许的真实 MySQL 版本为证据，不以 H2、MariaDB 或 mock 替代。
7. **连接器可选。** Connector/MCP 是增强能力，不得成为独董会首值、基础权益或自然业务闭环的必选前置。

## 4. 总体完成度快照

停止前的加权评估为 **约 62%（误差约 ±4%）**：

| 维度 | 估计完成度 | 说明 |
|---|---:|---|
| 本地工程与合同底座 | 约 78% | W0/W1 完成，W2/W3/W4 已有大量本地验证候选 |
| 可部署准备度 | 约 45% | 有迁移、门禁、回滚合同，但缺生产目标回执 |
| 真实生产闭环 | 约 20% | me/admin、公开 OAuth、外部交付、Watch/Hub、生产切流尚未闭环 |

该比例只用于组合判断，不替代逐项门禁。大量单测或本地 MySQL 通过不能把生产完成度自动抬高。

## 5. 开发过程与阶段成果

本轮独董会控制面自 `2fd1a38b` 建立真源起，到 `53c8e690` 共形成 71 个提交。历史仓库本身更早存在；此处只统计独董会控制面阶段。该阶段累计触及 486 个文件，包含仓库迁移、代码、SQL、验证器、报告与文档，不能把全部文件变化理解为新业务代码。

### 5.1 W0：独立真源与工程底座

起点提交：`2fd1a38b chore: establish independent board control plane truth root`

完成内容：

- 从交接目录建立可独立工作的仓库真源；
- 建立 `.fbs-engineering/contract.json`、任务板、实施状态和域名清单；
- 固化冻结包身份及不可写边界；
- 对齐 GitHub 远端、分支、基础构建和数据库清单；
- 处理跨机器长路径、迁移真源路径和行尾一致性。

关键经验：

- 名义 cwd 可能只是交接入口，必须先证明源码、包、运行时和报告真源；
- 行尾、编码、路径长度和生成报告字节都会影响不可变回执，不能视为无关格式问题。

### 5.2 W1：首个可验证垂直切片

代表提交：

- `7622a20a feat: add independent board entitlement vertical slice`
- `22f23cfe fix: close independent board application integration gate`

完成内容：

- 服务端派生登录主体，禁止客户端自报用户身份；
- VIP 权益失败关闭；
- 会议配额原子预留、幂等重放和租户隔离；
- me/admin 读模型权限分离；
- 首次迁移、重放、失败释放、schema drift、32 路竞争验证；
- Spring MVC、JWT、方法权限与真实 MySQL 事务矩阵。

关键证据：

- [w1-application-integration-verification-20260720.json](../../reports/independent-board/w1-application-integration-verification-20260720.json)
- 全仓当时 510 个普通 Maven 测试通过；
- MySQL 8.4.8 修复后两轮各 10/10 通过；
- 同键死锁被真实复现，修正为失败事务回滚后由独立 `REQUIRES_NEW` 当前读重放。

### 5.3 W2：me.u3w.com 用户门户本地候选

代表提交：`0ad68ea3 feat: deliver independent board user portal core`

完成内容：

- 用户门户权益、额度、会议和最近历史；
- 服务端派生企业上下文；
- 企业与会员双有效约束；
- 精确、不泄露的预留读回；
- 不确定提交保留相同 operationId 和冻结载荷；
- 动态路由授权完成后才允许 host-aware 默认入口；
- 普通用户一级菜单迁移；
- Connector 只展示待连接，不伪造激活；
- Webhook、Watch 仅作为状态占位，未假装已实现。

关键证据：

- [w2-user-portal-verification-20260720.json](../../reports/independent-board/w2-user-portal-verification-20260720.json)
- 当时全仓 556 个后端测试通过；
- 20 个真实 MySQL 事务用例通过；
- 前端生产构建通过。

未完成边界：

- 真实 `me.u3w.com` 生产部署和浏览器读回；
- Connector OAuth 自助授权；
- Webhook、Watch 深页面。

### 5.4 W3：admin.u3w.com 管理治理主线

#### W3A/W3B：权益治理与不可变撤销回执

完成内容：

- 仅全局系统管理员加细粒度权限可进入；
- 企业、会员和权益状态锁定校验；
- 严格四字段撤销命令；
- ACTIVE → REVOKED 比较并交换；
- 回执写入失败时完整回滚；
- 已撤销权益不能被隐式恢复；
- 历史回执按租户绑定且有界读取。

关键合同：

- [W3B-ENTITLEMENT-LIFECYCLE-CONTRACT.md](./W3B-ENTITLEMENT-LIFECYCLE-CONTRACT.md)

#### W3C/W3D/W3E：套餐读取、旧积分入口关闭与 API Key 范围

完成内容：

- 版本化套餐/额度 current-read；
- 关闭旧直接积分与 Skill earn 写入口；
- 旧入口返回真实 410，匿名请求仍由安全链返回 401；
- API Key 强制绑定用户和场景包；
- 仅数据库 `NULL` 表示全局范围，空字符串失败关闭；
- 使用记录结束前核验 owner、pack 和终态 CAS；
- 二进制精确、窄范围、歧义失败的 pack identity 查询。

#### W3F/W3G：默认关闭的用户全局积分影子账本和管理 UI

代表提交：

- `d06b29b6 feat: add default-off independent board credit ledger`
- `ade75482 feat: add default-off independent board credit admin`

完成内容：

- `USER_GLOBAL / FBS_POINTS` 不可变账本；
- grant、reversal、audit 和安全投影；
- account version 防跨设备丢更新；
- 同键等待者回滚后精确重放；
- 浏览器全局 pending slot 与 Web Locks；
- 默认关闭的菜单、权限和 UI；
- 真实双 MySQL 首次、重放、漂移与并发矩阵。

关键证据：

- [w3f-credit-ledger-verification-20260722.json](../../reports/independent-board/w3f-credit-ledger-verification-20260722.json)
- [w3g-credit-admin-ui-verification-20260722.json](../../reports/independent-board/w3g-credit-admin-ui-verification-20260722.json)
- W3G 当时全仓 663/663 通过；
- 前端生产构建和真实浏览器夹具通过；
- 独立多智能体复核最终 P0/P1 均为 0。

#### W3H/W3I/W3J/W3K：策略治理、审计血缘和发布门禁

完成内容：

- 版本化套餐策略 head、不可变修订回执和 operation lineage；
- 严格幂等重放、版本冲突、补偿回滚和 `no-store`；
- 会议审计绑定消费当时的不可变策略回执；
- 默认关闭候选的预生产 release receipt 检查器；
- 数据库单调链：head + 1、前驱摘要、只允许向前；
- 受控存储过程 authority 和双账户直接 DML/DDL 拒绝矩阵。

关键合同：

- [W3H-PLAN-POLICY-REVISION-CONTRACT.md](./W3H-PLAN-POLICY-REVISION-CONTRACT.md)
- [W3I-MEETING-AUDIT-POLICY-LINEAGE-CONTRACT.md](./W3I-MEETING-AUDIT-POLICY-LINEAGE-CONTRACT.md)
- [W3J-CREDIT-CANDIDATE-RELEASE-READINESS-CONTRACT.md](./W3J-CREDIT-CANDIDATE-RELEASE-READINESS-CONTRACT.md)
- [W3K-PLAN-POLICY-MONOTONIC-CHAIN-CONTRACT.md](./W3K-PLAN-POLICY-MONOTONIC-CHAIN-CONTRACT.md)
- [W3K-PLAN-POLICY-CONTROLLED-AUTHORITY-CONTRACT.md](./W3K-PLAN-POLICY-CONTROLLED-AUTHORITY-CONTRACT.md)

生产仍未授权：

- 未执行生产迁移；
- 未授予生产 definer/execute 最小权限；
- 未收集生产 grant/revoke/current-read/rollback 和人工批准回执。

### 5.5 W4：MCP OAuth、连接器和门户运行候选

#### W4A：权威连接器绑定

代表提交：`0a55bb42 feat(independent-board): add authoritative connector binding`

完成内容：

- 三表权威绑定与不可变回执；
- 固定四 scope；
- VIP 降级终止绑定，重新升级必须重新授权；
- 批量 current-read，无 Connector N+1；
- 真实 MySQL 首次、重放、partial state、trigger drift 和 32 路首次绑定。

合同：

- [W4A-AUTHORITATIVE-CONNECTOR-BINDING-CONTRACT.md](./W4A-AUTHORITATIVE-CONNECTOR-BINDING-CONTRACT.md)

#### W4B1：内部 OAuth/MCP 安全链

代表提交：

- `112b1967 feat(board): add W4b OAuth foundation and internal DCR`
- `34e296ef feat(board): seal W4b1 internal OAuth chain`
- `249b78fc feat(board): verify OAuth refresh security foundation`

完成内容：

- MCP 2025-11-25 和发布 OAuth 资源合同；
- profile-constrained DCR、loopback redirect、PKCE S256；
- opaque digest-only access/refresh token family；
- authorization code 审批、拒绝、单次交换；
- refresh rotation、replay family containment；
- 首个受保护 MCP 请求后才允许激活绑定；
- 所有安全判定使用加锁后的最终时间；
- 过期终态允许显式重新授权。

关键证据：

- [w4b-oauth-refresh-security-verification-20260721.json](../../reports/independent-board/w4b-oauth-refresh-security-verification-20260721.json)
- 当时全仓 838 个测试通过；
- MySQL Community 8.0.30 / 8.4.8 分别验证；
- 公开 OAuth/MCP 路由始终关闭。

#### W4B2：me/admin OAuth 与 Connector 默认关闭候选

完成内容：

- 六个前端候选页面；
- GET-only adapter 和严格安全投影；
- 四个默认关闭 JWT 读端点；
- user/role/permission/membership 每次读取重验；
- Connector binding、token family、receipt 和安全事件投影；
- 菜单、router、真实 404、单 Bearer 语义；
- 默认关闭和启用浏览器夹具。

合同与原型：

- [PORTAL-PROTOTYPE-SPEC.md](./PORTAL-PROTOTYPE-SPEC.md)
- [ADR-001-independent-board-default-off-portal-read-boundary.md](../decisions/ADR-001-independent-board-default-off-portal-read-boundary.md)

#### W4B2D：API2 归因与签名证据边界

完成内容：

- 固定 24h 归因报告；
- 报告型候选，不伪造生产业务；
- 签名 receipt、快照、阶段前驱和 nonce/expiry 合同；
- P1-005 cleanroom readiness 和 closure 捕获；
- Java 静态证据验证器。

已观测事实：

- 固定窗口内可归因独董会实际使用为 0；
- 该结果只能说明当时没有可证明归因，不能说明产品没有访问，也不能转化为生产闭环。

未完成：

- API2 精确活动版本、干净发布来源和签名宿主登记回执；
- 真实同绑定产品身份与自然业务归因；
- 生产 API2 部署闭包。

### 5.6 上架后观察

用户已确认 26.7.20 审核通过并上架。现有报告记录了 official entry 的部分宿主事实，但仍需区分：

- 用户确认上架；
- 宿主可验证 listing URL/receipt；
- 当前会话 official entry runtime load；
- 自然调用；
- same-binding 服务端闭环。

不得以用户确认上架直接替代后三类证据。

相关报告：

- [host-truth-chain-summary-20260721.json](../../reports/independent-board/host-truth-chain-summary-20260721.json)
- [official-entry-reverify-20260721.json](../../reports/independent-board/official-entry-reverify-20260721.json)
- [post-listing-cleanup-gate-20260721.json](../../reports/independent-board/post-listing-cleanup-gate-20260721.json)

### 5.7 W4B5A—W4B5E：Skill 消费积分主线

代表提交：

- `7a60eb5c`：规范化 Skill consume command；
- `95e5a686`：默认关闭的内部 writer；
- `4a96483c`：双 MySQL writer 证明；
- `67051f41`：宿主默认关闭接线；
- `53c8e690`：激活事务边界加固。

已完成：

- 042 Skill consume v2 迁移；
- 标准化命令、usage record 幂等锚点和 request digest；
- `REQUIRES_NEW` fresh transaction；
- operation、entry、account、bridge、user projection、usage terminal CAS 原子提交；
- 旧 v1 与 v2 authority 双向互斥；
- 宿主双开关同时开启才进入 v2；
- v2 失败不回退旧写者；
- 宿主 dispatcher 非事务，legacy executor 保持事务；
- v2 遇到 ambient transaction 失败关闭；
- nullable host session 使用域分离的 ABSENT digest；
- MySQL Community 8.0.30 / 8.4.8 各 6/6；
- 聚焦回归 52/52。

关键合同：

- [W4B5A-SKILL-CONSUME-COMMAND-CONTRACT.md](./W4B5A-SKILL-CONSUME-COMMAND-CONTRACT.md)
- [W4B5B-SKILL-CONSUME-TRANSACTION-WRITER-CONTRACT.md](./W4B5B-SKILL-CONSUME-TRANSACTION-WRITER-CONTRACT.md)
- [W4B5C-SKILL-CONSUME-DUAL-MYSQL-TRANSACTION-CONTRACT.md](./W4B5C-SKILL-CONSUME-DUAL-MYSQL-TRANSACTION-CONTRACT.md)
- [W4B5D-SKILL-CONSUME-HOST-WIRING-CONTRACT.md](./W4B5D-SKILL-CONSUME-HOST-WIRING-CONTRACT.md)
- [W4B5E-SKILL-CONSUME-ACTIVATION-SAFETY-CONTRACT.md](./W4B5E-SKILL-CONSUME-ACTIVATION-SAFETY-CONTRACT.md)

最终已提交证据：

- [skill-consume-activation-safety-verification-20260723.json](../../reports/independent-board/skill-consume-activation-safety-verification-20260723.json)
- 回执 SHA-256：`3bc33034659fec0671ecce083588fd132229a071276e1fda252327699d824b0a`
- `releaseReady=false`
- `externalDeliveryExactlyOnce=false`
- Commercial Hub outbox 仍为开放阻断项。

## 6. 暂停的 W4B5F 草案

### 6.1 封存位置

未完成草案已从工作树移入可恢复 Git stash：

- stash 对象：`fe0ece6a2140459e35eb86e8cb74b8be8bd0e7c0`
- 创建说明：`W4B5F stopped draft before development retrospective 2026-07-23`
- 原分支：`codex/w4b2-default-off-candidate`

查看但不恢复：

```powershell
git show --stat fe0ece6a2140459e35eb86e8cb74b8be8bd0e7c0
git stash show --include-untracked --stat stash@{0}
git stash show --include-untracked -p stash@{0}
```

如果未来恢复，必须先创建隔离分支，并用对象 SHA 而不是易漂移的 stash 序号：

```powershell
git switch -c codex/w4b5f-commercial-hub-outbox 53c8e690cc408a9b428100733f7a0e37b267cfa1
git stash apply fe0ece6a2140459e35eb86e8cb74b8be8bd0e7c0
```

不要直接把该 stash 应用到生产、主干或发布 cleanroom。

### 6.2 草案包含的文件

已修改：

- `SkillConsumeCreditCommand.java`
- `SkillConsumeCreditWriter.java`
- `SkillConsumeServiceImpl.java`
- `SkillConsumeCreditCommandTest.java`
- `SkillConsumeCreditTransactionServiceTest.java`

新增：

- `CommercialHubOutboxPayload.java`
- `CommercialHubOutboxPayloadFactory.java`
- `CommercialHubOutboxPayloadFactoryTest.java`

草案意图：

- 为 command 增加 `packCode`、`packType`；
- 生成确定性 provider record key；
- 冻结 Commercial Hub 24 字段 payload 和 SHA-256；
- 为后续 043 outbox 准备载荷工厂。

### 6.3 已知不可接受问题

该草案**不能编译，也不能作为完成成果使用**：

1. `SkillConsumeCreditWriter.consume` 从 9 参变为 11 参后，13 个测试调用点未同步：
   - `SkillConsumeServiceTest.java`：6 处；
   - `SkillConsumeCreditWriterTest.java`：6 处；
   - `SkillConsumeCreditLedgerV2MysqlIT.java`：1 处。
2. `packCode`、`packType` 尚未进入 `requestDigest`，同 usageId 下不同商业事实可能被误判为精确重放。
3. `packType` 草案使用 `0..2`，但既有实体与 SQL 合同是 `1=平台包、2=企业包、3=自定义包`。
4. payload factory 同时接受 command、operation、余额和时间，却只校验 operationId，存在分裂事实。
5. `source` 被硬编码为 `WORKBUDDY`，与现有 `WORKBUDDY/STANDALONE/API → SKILL_API` 语义不一致。
6. 没有 golden JSON/digest、locale/timezone/ObjectMapper 漂移和 operation mismatch 测试。
7. 生产事务没有调用 payload factory；没有 outbox mapper、043 表、worker 或 gateway。
8. 草案修改了被 W4B5B 历史回执绑定的测试文件，旧合同门禁会正确报告字节漂移。未来必须创建 W4B5F 后继回执并回归前驱，禁止重写历史回执。

停止前最后一次 Maven 结果为预期失败：

```text
testCompile failed
13 call sites do not match SkillConsumeCreditWriter.consume
```

## 7. W4B5F 已收敛但尚未实现的设计合同

未来如恢复，应沿用以下边界，不要重新回到事务内直连或盲重试。

### 7.1 043 operation-bound outbox

- 只能新增 `public_init_043`，不得修改 042 或历史回执；
- outbox 与 `fbs_skill_credit_operation_v2.operation_id` 唯一绑定并建立 FK；
- 在 `consumeFresh` 内、usage terminal CAS 成功后、事务提交前插入；
- outbox 写入失败必须回滚 operation、entry、余额、bridge、user projection 和 usage；
- 精确 replay 必须验证既有 outbox identity、payload version、原始字节 digest；
- 不允许从当前可变资料重建旧 payload；
- 如果 043 激活时发现已有 042 operation 且无法精确回填，迁移应失败关闭。

建议状态：

```text
PENDING
  -> LEASED(DELIVER)
     -> DELIVERED
     -> RETRY_WAIT       仅明确未发出
     -> RECONCILE_WAIT   结果不确定
     -> DEAD

RECONCILE_WAIT
  -> LEASED(RECONCILE)
     -> DELIVERED
     -> RECONCILE_WAIT
     -> CONFLICT/DEAD
```

### 7.2 租约和事务边界

- claim 使用独立 `REQUIRES_NEW + READ_COMMITTED` 短事务；
- `SELECT ... FOR UPDATE SKIP LOCKED` 后写入随机 lease token；
- 网络调用严格在数据库事务外；
- ack/retry/reconcile/dead 必须以 `id + LEASED + lease_token` fencing；
- 过期 `LEASED(DELIVER)` 只能转为 read-back，不允许自动再次 add；
- worker 崩溃于“远端成功、本地 finalize 前”时，后继只能先对账。

### 7.3 专用 Gateway

需要独立、类型化接口：

```text
deliverOnce(command)
readBack(query)
```

结果至少区分：

- Confirmed；
- NotAttempted / 明确未发送；
- Rejected；
- Uncertain；
- ReadBack Matched / AbsentComplete / Conflict / Uncertain。

硬规则：

- side-effecting add 每次最多启动一次 CLI；
- 不复用现有通用 `NET_*` 自动重试；
- 进程 exit 0 不能直接判成功；
- 必须校验 MCP `isError`、provider `errcode`、记录数、业务键、native row id 和回显；
- stdout/stderr 分离且有界；
- 只持久化脱敏错误码、native id 和响应摘要；
- 不保存 authCode、host session、CLI 凭证或原始 provider 响应。

### 7.4 现有 WeCom 链的已知风险

当前 legacy 链：

```text
WecomBusinessSyncServiceImpl
  -> WecomWriteServiceImpl
  -> WecomCliServiceImpl
  -> doc smartsheet_add_records
```

风险：

- business record_id 使用随机 UUID；
- payload 时间在 dispatch 时生成；
- 网络失败被业务层吞掉；
- write 只按进程结果累计成功，不验证 provider receipt；
- CLI exit 0 即可能被标记 success；
- `NET_*` 自动重试可能重复非幂等 add；
- executor 已 unwrap，而 read service 又期待 outer MCP envelope，存在双重解包错位；
- 仓库没有锁定目标 CLI 路径、版本、SHA、doc/sheet identity 或 worker 开关；
- 本机停止时没有可用 `wecom-cli`。

因此，现有链只能作为字段与调用方式参考，不能直接宣称 exactly-once。

外部参考只能指导实现，不能代替目标运行时实证：

- MySQL locking reads / `SKIP LOCKED`：<https://dev.mysql.com/doc/refman/8.4/en/innodb-locking-reads.html>
- WeCom CLI 官方仓库：<https://github.com/WecomTeam/wecom-cli>
- WeCom CLI 参考：<https://github.com/WecomTeam/wecom-cli/blob/main/docs/cli-reference.md>
- 历史兼容性问题：<https://github.com/WecomTeam/wecom-cli/issues/89>

## 8. 未完成和未证明清单

### 8.1 生产平台

- 生产数据库迁移；
- `me.u3w.com` 生产运行；
- `admin.u3w.com` 生产运行；
- 两个真实域名的浏览器/JWT/权限/current-read 读回；
- 候选菜单、HTTP 和写者人工激活；
- cleanroom build、切流、回滚和发布后观察。

### 8.2 OAuth / MCP / Connector

- 公开 OAuth metadata、DCR、authorize、token 和 MCP 路由；
- WorkBuddy OAuth 端到端；
- 真实 Connector VIP binding；
- 单飞 refresh、原子 token store 和 invalid_grant 重新授权 UX；
- API2 精确活动 release、签名宿主登记和干净来源闭包。

### 8.3 积分与 Skill 消费

- Commercial Hub 043 outbox 和专用 Gateway；
- uncertain-result read-back；
- 生产 writer authority promotion；
- 旧积分 rule/menu seed 退役；
- API Key 生命周期与数据库不可变 packCode；
- 企业 member 快照、API key instance 和 server-verified host identity；
- 目标 CLI、目标表格、字段类型、分页、唯一性和一致性实证。

### 8.4 W5 与 W6

W5 整体仍为 pending：

- lease-fenced delivery outbox；
- Webhook receipt/reconciliation；
- Watch approval challenge；
- FBSir Hub 资产清单和安全命令。

W6 整体仍为 pending：

- cleanroom build；
- 数据库迁移演练；
- 本地等效联调；
- 真实域名路由配置；
- 回滚和观察回执。

### 8.5 上架后自然闭环

- 宿主可验证 listing receipt；
- 当前 official entry 同会话 runtime load；
- listed runtime 自然调用；
- same-binding 业务归因；
- 服务端闭环。

## 9. 可复用工程方法

### 9.1 合同先行但必须落到代码

有效模式：

```text
目标
  -> 事实真源
  -> proof gap
  -> RED 用例
  -> 最小实现
  -> 真实 MySQL/浏览器/HTTP
  -> 后继回执
  -> 任务板和状态刷新
```

无代码的方案不计进度；没有覆盖目标语义的测试也不能为广泛结论背书。

### 9.2 幂等和并发

- 幂等键输家必须让失败事务完整回滚，再开启独立当前读事务查看 winner；
- MySQL `REPEATABLE READ` 下，等待结束不代表普通读能看见 winner；
- mutable owner row 才加锁，不要为一致性锁住不可变历史回执；
- 所有不同 key 并发写要统一锁顺序；
- 状态 `version + 1` 只有进入 `WHERE expected_version` 才能称为 CAS；
- 外部副作用不能只靠数据库 lease token，必须另有业务幂等键、回执和读回。

### 9.3 迁移

- 精确版本 allowlist 先于文件执行；
- 首次、重放、partial state、metadata drift、trigger drift、lock release 都必须验证；
- current-read 复用 manifest advisory lock，且成功/失败都不留 helper 或伪回执；
- 每个破坏性漂移向量使用 fresh DB；
- 历史 SQL 和历史回执不可就地重写。

### 9.4 前后端安全

- 身份由服务端派生；
- 每个读写动作重验 live user、role、permission、membership；
- DTO 和投影采用 allowlist；
- 成功、401、403、400、409、500、默认关闭 404 都要保持 `no-store`；
- 默认关闭应表现为真实路由/Bean/菜单不存在，而非前端隐藏后仍可访问；
- 不确定写结果必须保留同一请求身份和冻结载荷，禁止自动换 key。

### 9.5 证据治理

- 报告记录 source digest、前驱回执、环境版本、测试数量和未证明项；
- 共享源码后继变化由新回执接管；
- 不允许因为“最新 verifier 通过”就改写历史回执；
- MySQL 两个版本的 Surefire/summary 必须分别保存，防止后跑版本覆盖先跑证据；
- 本地测试、宿主显示、生产流量和自然业务必须分别记账。

## 10. FBS 编排器经验

本项目对 FBS 编排器形成了以下可复用反馈：

1. **路由关键词可能抢占真实主线。** 通用 migration 关键词曾把全平台目标误路由到 workspace migration，后来收窄为必须出现更具体的迁移语义。
2. **当前工件高于旧路由。** taskboard 明确 W4B5F 时，即使编排器给出 assess/submit 阶段冲突，也不应改走相邻主线。
3. **版本字段契约有漂移。** 编排器读取 `implementation-status.json` 时曾得到 `version=null`，实际文件使用 `platformVersion`。未来应修正编排器 versionSource 解析，而不是伪造项目版本。
4. **历史回执漂移是正确失败。** W4B5F 草案改变 W4B5B 已绑定测试字节后，中央 verifier 正确失败；解决方式是后继证据链，不是放宽旧门禁。
5. **多智能体适合只读分面。** 门户、数据库、运行时/集成可并行审计；写操作仍应由主智能体统一持有，避免共享工作树互相覆盖。
6. **外部目标未锁定时保持 No-Go。** 官方文档可用于设计，但动态 CLI schema、真实凭证、目标表格和 provider 一致性必须以 exact target 回执为准。

## 11. 后续安全恢复顺序

如果未来决定恢复开发，建议严格按以下顺序：

1. 确认冻结包哈希仍为本备忘录记录值；
2. 确认 `53c8e690`、任务板和实施状态没有被其他分支替代；
3. 从 `53c8e690` 创建独立 W4B5F 分支；
4. 只读检查 stash `fe0ece6a…`，先理解问题再 apply；
5. 先修 command 合同：
   - `packType=1..3`；
   - `packCode/packType` 进入 request digest；
   - command/operation 共用字段严格一致；
6. 写 RED：
   - golden payload/digest；
   - operation mismatch；
   - fresh consume 原子一条 outbox；
   - outbox 失败全回滚；
   - exact replay 不重复 enqueue；
   - uncertain add 永不盲重投；
   - stale token 所有 finalize 均失败；
7. 新增 043、manifest、initializer current-read 和双 MySQL runner；
8. 实现 one-shot Gateway 和 read-back；
9. 先本地单元/集成，再 MySQL 8.0.30 / 8.4.8；
10. 创建 W4B5F 后继合同、验证报告和证据 succession；
11. 保持两个候选开关关闭；
12. 只有 exact target CLI、rollback、权限和人工批准回执齐全后，才讨论生产激活。

## 12. 恢复时建议运行的只读/验证命令

```powershell
git status --short --branch
git rev-parse HEAD
git rev-parse origin/codex/w4b2-default-off-candidate
git stash list
git show --stat fe0ece6a2140459e35eb86e8cb74b8be8bd0e7c0

powershell -ExecutionPolicy Bypass `
  -File C:\Users\10171\.codex\skills\fbs-engineering-orchestrator\scripts\assess_contracts.ps1 `
  --contract .fbs-engineering\contract.json --json

powershell -NoProfile -ExecutionPolicy Bypass `
  -File scripts\verify-independent-board-control-plane.ps1 -Mode Contract

powershell -NoProfile -ExecutionPolicy Bypass `
  -File scripts\verify-database-manifest.ps1 -Root . -Json
```

真实 MySQL destructive runner 只能在专用 loopback、随机端口、独立 datadir 和显式 destructive consent 下执行；不得读取生产数据源变量。

## 13. 禁止性清单

后续接手者不得：

- 修改或重打已上架 26.7.20 冻结包；
- 把 stash 草案称为已实现；
- 改写 042、历史 migration receipt 或历史验证报告；
- 用 H2、MariaDB、Mockito 替代真实 MySQL 并发证明；
- 在数据库事务里调用 CLI、HTTP、Webhook 或设备；
- 对不确定外部新增结果自动重投；
- 以进程 exit 0 直接判 provider 成功；
- 将 authCode、host session、token、Cookie 或凭证落入 outbox；
- 默认打开候选菜单、HTTP、writer 或 worker；
- 用用户确认上架替代宿主 receipt、自然调用或 same-binding 服务闭环；
- 在工作树不清洁时构建或发布 cleanroom。

## 14. 收口结论

截至停止决定：

- 已形成较完整的合同、身份、权益、积分、策略、OAuth、连接器和 Skill 消费本地工程底座；
- 已建立真实双 MySQL、HTTP/JWT、前端生产构建、浏览器夹具、证据 succession 和默认关闭机制；
- 已上架专家包保持冻结且未被平台开发回写；
- 主分支与远端在 `53c8e690` 对齐；
- W4B5F 未完成草案已可恢复封存，不再污染工作树；
- 生产部署、Commercial Hub 可靠交付、Webhook、Watch/Hub、真实域名和自然 same-binding 闭环仍明确未完成；
- 当前状态应被描述为：**本地默认关闭候选体系较成熟，生产仍 No-Go**。

如后续只需了解当前状态，先读本备忘录、`taskboard.json` 和 `implementation-status.json`；如需恢复 W4B5F，再读取 `53c8e690`、W4B5E 合同/回执和封存 stash，禁止从旧聊天记录直接猜测实现状态。
