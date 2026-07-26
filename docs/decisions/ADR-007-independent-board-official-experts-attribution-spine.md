# ADR-007：当前官方 Experts 归因纵向主干

状态：已接受，生产激活待证据。

## 背景

WorkBuddy 当前上架包是 `fbsir-eight-seat-board@26.7.21`，其内容树内仍带有 `26.7.20` 合同元数据。历史 `public_init_037` 和 P1-005 固定在旧上架身份及 Connector 四阶段链，不能表达当前基础首值，也不能修改其历史字节和回执。

API2 Git 真源已定位为 `fubangshou/FBSAI`，当前开发基线为
`agent/board-no-package-same-binding-v1-20260719@a0834ea5d4c1c3f95be9d25d26913c2d973e09d0`。
线上健康接口声明的源提交仍为 `c01891a0ca3e11db0a0fe51828276fab33b862b5`，
但尚缺活动 release 逐文件字节回读，因此不能把 Git 声明当成生产字节证明。
直接热补 release 仍会破坏 strict-HEAD、回滚和后续维护。

## 决策

1. 当前产品身份只接受上架版本 `26.7.21`；`26.7.20` 仅保留为嵌入合同元数据。
2. 以 `ENTRY_OBSERVED → INTENT_CLASSIFIED → FIRST_VALUE_COMPLETED` 为基础链；
   API2 分别从已持久化的 `whoami_emitted`、`scene_pack_resolved`、
   `first_value_completed` 事实生成事件，禁止在首值完成后回填前两段。
   这些是服务侧观测映射，不把 Connector/MCP 变成产品首值前置。
3. 新建 `public_init_043` successor，不修改 `public_init_037`。
4. 使用“可变 journey head + 不可变 event ledger”；只锁 head。
5. API2 保留服务器已验证的 `serverBindingId` 且提交空
   `sameBindingKey`；U3W 使用专用密钥内部派生同绑定键。API2 事件签名与
   U3W 同绑定派生使用分离密钥。
6. admin 只读窗口上限 24 小时，六维与五类流量分拆，全路径 `no-store`。
7. W1 产品积分永久默认关闭；观测故障对用户体验 fail-open，对业务信用 fail-closed。
8. API2 Publisher 只允许在上述 `a0834ea5` 精确基线建立的独立候选分支中
   实现；候选必须提交、推送、打包并与服务侧逐文件回读对齐，禁止继续扩展旧
   P1-005 cleanroom 或直接热补线上 release。
9. 短 TTL 只认证每次运输信封。业务 canonical 和 `eventDigest` 排除
   `issuedAt`、`expiresAt`、`nonce`、`keyId`，允许持久 outbox 在保留窗内
   重签同一业务事件；U3W 的 `occurredAt` 受独立 retention 约束。

## 后果

正面影响：

- 当前上架身份、历史合同元数据和旧证据不再混为一谈；
- 第一阶段不再被 Connector、MCP、积分或后续门户功能阻塞；
- 可分别验证签名、幂等、顺序、流量分母和 admin 读回；
- 迁移、发布和回滚具备可追溯边界。

代价与剩余风险：

- 在 API2 候选提交、推送、闭包打包和生产逐文件回读完成前，不能宣称
  strict-HEAD 可发布；
- 本地与双 MySQL 通过仍不能证明真实自然流量；
- 若服务侧无法从现有 WorkBuddy 请求上下文取得稳定 journey/binding，缺失的身份载体归入
  `fbs_connector`，解析、签名、same-binding 与账本能力归入 `fbs_service_side`；若必须新增
  工具绑定，则另建 `fbs_expert_package` 候选需求。WorkBuddy 只作为外部约束和验收面，
  不成为升级 owner、backlog 或交付落点。

## 不采用的方案

- 修改 `public_init_037`：会破坏历史 SHA 和回执链。
- 兼容 26.7.20 上架版本：违背当前唯一官方基线。
- 用 UI 名称或旧 ZIP 推断身份：不可审计且容易误归因。
- 直接在 active release 热补 Publisher：绕过已定位 Git 真源和 strict-HEAD 回滚依据。
- 把 Connector/MCP 纳入基础首值：延迟第一阶段闭环并扩大失败面。
