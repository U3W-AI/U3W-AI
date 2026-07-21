# ADR-001：独董会默认关闭的 Portal 只读边界

- 状态：Accepted
- 日期：2026-07-21
- 范围：W4b.2b 后端只读候选

## 背景

W4b.2a 已形成 `me.u3w.com` 与 `admin.u3w.com` 的前端源码候选，但没有菜单、路由或运行入口。W4b.2b 需要提供 OAuth client、token family、完整 Connector binding 和会员侧 Connector 的安全投影，同时必须满足以下不变量：

- 不修改已提审并冻结的独董会 26.7.20 专家包；
- 默认关闭，关闭时没有可探测的候选接口；
- 只接受现有 RuoYi JWT 登录态，不开放 OAuth/MCP 公网协议面；
- 管理读取和会员读取都必须绑定当前数据库权限及租户成员关系；
- 任何不完整、重复、过期或不一致的 lineage 都不得被投影成正常或 VIP 状态；
- 不改变仓库既有 Controller 的全局 HTTP/异常语义。

## 决策

1. 新建独立的候选 Controller、Service、Mapper 与 DTO，不复用 W2/W3 的现有业务 Controller，也不向它们增加隐式分支。
2. 使用 `fbsir.independent-board.portal-candidate.enabled=false` 作为默认关闭开关；配置 Bean、Controller 与候选异常 Advice 仅在值为 `true` 时创建。
3. 候选路径专用 Filter 始终注册，以固定关闭态 `404`、已保留路径 `404` 和启用态非 GET `405`，并让其他现有路径继续走原有处理链。
4. `@PreAuthorize` 只作为第一道 JWT/权限门禁。Service 在每次投影前重新读取 `sys_user`、`sys_user_role`、`sys_role`、`sys_role_menu` 和 `sys_menu` 的当前状态，避免 Redis `LoginUser` 权限快照过期后继续放行。
5. 管理端 family/binding 读取必须显式提供 `tenantId`；会员端必须同时满足当前企业、成员和用户绑定。未知、缺失、重复或越租户数据统一 fail closed。
6. ACTIVE/PENDING family、binding、entitlement 和 receipt 必须以 subject digest、intent、actor、时间、版本、scope 及因果回执形成完整链路后才输出正常状态；浏览器仅获得安全引用，不获得 token、code、state、verifier 或原始 digest。
7. 仅在候选 Controller 范围内把已知错误映射为固定 400/403/500/503 响应并禁用缓存；不修改 `GlobalExceptionHandler`。
8. security-event GET、所有写动作、菜单/路由、公开 OAuth/MCP 端点及生产部署继续关闭，分别由后续波次解锁。

## 被否决的方案

- 仅依赖 Redis `LoginUser`：无法证明用户禁用、角色撤销或菜单权限回收会立即生效。
- 修改全局异常处理器以获得统一 404/405：会改变非候选 Controller 的历史行为，扩大默认关闭候选的影响面。
- 直接使用 entitlement 中保留的 `connector_binding_id` / `connector_verified_at`：W4a 合同将其定义为历史保留字段，不能代替当前 binding 与回执链。
- 直接挂载 W4b.2a 页面：会在生产 SecurityFilterChain、租户检索分页和浏览器交互尚未证明时扩大可达面。

## 后果

- 正向：关闭态对现网近似零影响；权限即时失效；投影合同可由单元、HTTP 与真实 MySQL 三层证明；后续可独立接入菜单和协议面。
- 代价：每次读取增加一次当前权限查询；W4b.2c 必须证明现有生产 RuoYi SecurityFilterChain 对候选 JWT 路径与 OAuth-bearer-shaped 凭证的隔离，并完成默认关闭的菜单/路由运行态与浏览器验证；本波不提前新增 W4b.3 的 AS/MCP SecurityFilterChain。

## 验证与证据

- `reports/independent-board/w4b2-backend-read-projection-verification-20260721.json`
- `IndependentBoardPortalReadServiceTest`
- `IndependentBoardPortalReadMapperContractTest`
- `IndependentBoardPortalReadHttpSecurityIntegrationTest`
- MySQL Community 8.0.30 与 8.4.8 的 `56/56` direct、`3/3` refresh-security、`36/36` canonical 及全部清理门禁
