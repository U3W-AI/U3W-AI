# 独董会 W4a 权威 Connector 绑定合同

状态：`verified_local`  
品牌：福帮手 / FBSir  
产品：独董会  
产品版本：`26.7.20`  
适用仓库：`u3w-independent-board-control-plane`

## 1. 目标与边界

W4a 只关闭一个可运行纵向切片：把已授予的 `BOARD_VIP` 权益从
`PENDING_CONNECTOR` 安全推进到 `ACTIVE`，并确保过期、撤销、跨租户、弱证据、
配置漂移和并发 scope 变更均 fail closed。

W4a 不开放 OAuth、DCR、token、PRM 或 MCP 公共 HTTP 路由。W4b 在本合同的
内部端口上实现 OAuth 2.1、PKCE、资源服务器和 MCP；W4c 再交付 WorkBuddy
Connector、Marketplace 本地 Override 联调和审核材料。

本仓库是唯一开发真源；运行资产不得依赖其它目录。审核中的
`fbsir-eight-seat-board` 26.7.20 包保持冻结，W4a 不修改该目录。

## 2. 唯一入口与固定协议

- 唯一 WorkBuddy Marketplace 入口仍为 `fbs-connector`，不得另建“独董会 VIP Connector”。
- `source_code = WORKBUDDY`。
- 固定资源 URI：`https://api2.u3w.com/fbs-mcp/mcp`。
- issuer 必须由部署环境显式配置；空值、非 HTTPS、非 ASCII、超过 512 字符或非规范 URI
  均使 current-read 返回未连接，并使激活写入稳定返回 503。
- client id 必须为 1–191 个可打印 ASCII 字符。
- 首版 scope 必须与以下四项完全相等，不得缺少、增加或使用前缀匹配：
  `identity.read`、`entitlement.read`、`board.meeting.reserve`、
  `board.receipt.write`。

资源、issuer、tenant、member、user、product、source、connector、client、subject 摘要
和 scope 共同组成一个权威绑定；任一项不等都不能激活 VIP。

## 3. 激活证据

OAuth 同意、授权码签发、token 签发、Connector 安装或 HTTP 200 均不能激活 VIP。
只有 W4b 资源服务器完成 bearer、issuer、resource/audience、tenant、client、subject、
scope、时效和撤销校验，并实际处理 WorkBuddy 的首个受保护请求后，才能调用 W4a
内部证明端口。W4a 只接受：

- `MCP_INITIALIZE`
- `MCP_TOOLS_LIST`

证明对象只能由服务端内部代码构造，不提供接收客户端 JSON 的 Controller。数据库和日志
只保存 SHA-256 摘要，不保存授权码、access token、refresh token、Cookie 或其它凭据原文。

W4a 回执证据等级仅为 `ACTION_COMPLETED`；不得推断为 `PROVIDER_ACCEPTED`、
`DELIVERED`、`INTERACTED` 或 `BUSINESS_CONFIRMED`。

## 4. 权威数据模型

W4a 新增三张表：

- `fbs_connector_binding`：保存精确身份、协议常量、验证方式、证据摘要、生命周期、
  有效期、最后访问时间和乐观锁版本。
- `fbs_connector_binding_scope`：每个 scope 独立成行，以复合主键消除重复。
- `fbs_connector_binding_receipt`：保存 `CONNECTOR_BINDING_VERIFIED` 或
  `CONNECTOR_BINDING_REVOKED` 的租户绑定回执。

`fbs_product_entitlement.connector_binding_id` 与 `connector_verified_at` 是历史预留列，
不参与权威 current-read。

绑定唯一业务键为 tenant + member + product + source + connector。状态机为：

```text
首次真实受保护请求 -> ACTIVE -> REVOKED | COMPROMISED
```

`REVOKED` 与 `COMPROMISED` 均为终态，普通请求不能隐式恢复。重新授权由 W4b 的独立
合同处理。

绑定变更与回执必须在同一事务提交；回执失败必须回滚绑定变更。数据库仅在
`fbs_connector_binding_receipt` 上通过 BEFORE UPDATE/DELETE trigger 阻止普通修改和
删除。生产运行账户对回执表仅具备 `SELECT/INSERT`，对 binding 表仅具备受控
`SELECT/INSERT/UPDATE`，对 scope 表仅具备 `SELECT/INSERT`，三表均不得授予 DDL 或
不必要的 DELETE 权限。该机制不声称能抵抗 DBA 执行 `TRUNCATE` 或 `DROP`；迁移账户与
运行账户分离是 W6 发布前置条件。

## 5. current-read

权威 current-read 必须同时满足：

1. enterprise 与 member 当前有效，且 tenant/member/user 精确一致；
2. entitlement 是同一用户当前有效的 `BOARD_VIP`；
3. `BOARD_VIP` plan 仍精确满足 VIP、Connector、5 次/日、30 议题、全员席位和秘书能力合同；
4. binding 为 `ACTIVE`，未撤销，`last_seen_at` 不在未来且未超过 `valid_until`；
5. source、connector、issuer、resource、client 与 subject 摘要满足当前信任策略；
6. 验证方式为首个真实受保护 MCP 请求；
7. scope 集合与四项首版 scope 完全相等。

缺少有效绑定时，用户快照返回 `PENDING_CONNECTOR` 和免费策略。数据库结构损坏、
不可能状态或 plan 合同漂移返回服务错误，不得静默升级。管理员 Connector 批量读取
使用一次有界查询并联结 entitlement 与 plan，不产生 Connector N+1。

## 6. 锁序与并发

所有写事务遵循：

```text
active enterprise/member
  -> product entitlement
  -> connector binding
  -> connector scope rows
  -> future OAuth token family/token rows
  -> immutable receipt
```

- 首次验证依次锁定 active member、entitlement、binding 和 scope。
- 会议预约读取 VIP 策略时使用同一锁序；scope 使用 `FOR UPDATE`，并发删除提交后必须
  读取最新状态，不能保留 RR 旧快照。
- entitlement 撤销即使 member/enterprise 已停用仍可联动撤销 binding。
- entitlement 从 VIP 调整为免费 plan 时也必须在同一事务撤销 binding；之后再次升级 VIP
  仍保持 `PENDING_CONNECTOR`，不得复用旧终态绑定绕过重新授权。
- W4b 只能在 binding 之后锁 token family/token，禁止反向锁序。
- 显式 binding revoke 在 W4a 仅为内部端口；W4b 暴露任何入口前，必须增加“本人断开”
  或具名管理权限校验。

## 7. 迁移合同

- 最低数据库版本为 MySQL `8.0.29`，因为迁移使用
  `CREATE TRIGGER IF NOT EXISTS`。当前真实证据仅覆盖 MySQL `8.4.8`；不能据此宣称
  已验证全部 8.0 版本。
- 单文件迁移采用 prepare/finalize 两阶段，并在同一连接上持有 64 字符命名锁：
  prepare 创建/精确审计三表，顶层创建两个不可变 trigger，finalize 再审计 trigger、
  写入内部回执、提交并释放锁。
- 完成态重放必须精确校验 3 表、36 列、7 个默认合同、11 个显式可见升序索引、
  3 个同库外键/8 个列链接、15 个已启用 CHECK 和 2 个 trigger。
- 已存在表但无精确内部回执、已有回执但结构不完整、CHECK/索引/FK/trigger 漂移均
  fail closed；异常连接必须释放命名锁。
- public initializer 只接受精确 `APPLIED:<description>`。若 runner 在内部提交之后发生
  模糊失败，只能在完整精确 current-read 通过后清理迁移过程并调和为 APPLIED。

## 8. W4a 验证矩阵

- 单元：身份、固定资源、scope、摘要、client、时效、终态、plan 漂移、配置漂移和防御性复制。
- Mapper：方法/XML 对齐；tenant/user/product/source/connector 精确过滤；批量读取有界且无 Connector N+1。
- 真实 MySQL：首次、完成态重放、部分态、CHECK 漂移、缺失 trigger、锁释放、32 路首绑、
  跨租户、过期/撤销、VIP 降级后旧绑定不可复用、回执失败回滚、数据库回执不可变和
  RR scope 删除竞争。
- HTTP：无 binding 仍为 `PENDING_CONNECTOR`；客户端不能提交 binding 或 VIP 状态。
- 全量：后端、前端、数据库 manifest、菜单迁移及 W1–W3b 证据继续通过。

## 9. 延后项

- W4b：OAuth 2.1、PKCE S256、AS/PRM metadata、受限 DCR 或预登记客户端、精确
  resource/audience、token-family 轮换与重放整族撤销、MCP 资源服务器和登录/同意页。
- W4c：仓库内 Connector 源目录、OAuth 配置、本地 Override、WorkBuddy 实机联调、
  审核包和真实 binding 回执。
- W5/W6：积分、Webhook、Apple Watch、秘书写操作、委派租户管理员、运行/迁移账户分离、
  生产域名发布与发布后观察。

W4a 本地验证通过不等于 OAuth 完成、Connector 已上架、生产数据库已迁移或 VIP 商业闭环。
