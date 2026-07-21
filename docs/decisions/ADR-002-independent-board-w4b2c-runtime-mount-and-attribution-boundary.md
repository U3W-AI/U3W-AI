# ADR-002：独董会 W4b.2c 默认关闭运行挂载与归因证据边界

- 状态：Accepted
- 日期：2026-07-22
- 范围：W4b.2c 候选运行挂载、API2 归因观察
- 前置：ADR-001 的 W4b.2b 只读边界继续有效；本 ADR 仅更新已被运行态证据推翻的局部假设

## 背景

W4b.2a/b 已证明默认关闭的前端源码和后端 JWT 只读投影，但页面仍未经过真实动态菜单挂载，admin 也缺少全局系统管理员可用的租户检索。浏览器验证同时暴露两项跨层问题：

1. 仅有候选 Controller 的 404/405 保护不足以保证整个 DispatcherServlet 的真实 404；未映射路径可能被通用异常处理成传输成功语义。
2. API2 官方入口流量很大，但产品、版本、权威 binding 和 verified receipt 在边缘投影中丢失，不能把 HTTP 成功或 connector 总量提升为独董会自然使用。

冻结的 `fbsir-eight-seat-board@26.7.20` 不属于本仓库可写面，以上问题必须由 control plane、连接器和服务侧合同解决。

## 决策

### 默认关闭的运行挂载

1. 新增独立候选菜单迁移，五个候选菜单默认 `status='1'`，只把会员 Connector 菜单绑定到既有 `user` 角色；管理菜单不自动绑定角色。迁移使用 named lock、预检、事务和精确漂移失败关闭，并由 manifest 标为 manual migration。
2. 前端继续使用 RuoYi 的后端动态菜单。只有构建时 feature flag 开启、菜单身份精确匹配且菜单自身已启用时，候选路由才进入 permission store；关闭态删除候选路由，不能只在组件里隐藏。
3. admin 新增只读 `/business/independent-board/tenants`，仅允许全局系统管理员与大小写精确的 `board:tenant:query`；query/status/cursor 严格白名单，累计读取上限和 cursor 合同失败关闭。委派租户管理员仍未实现。
4. 现有 RuoYi JWT 是候选 Portal 的唯一认证链。重复 Authorization、嵌套 Bearer、空白 token 和 OAuth-bearer-shaped 凭证不得被宽松解析或记录；不新增 W4b.3 AS/MCP SecurityFilterChain。
5. 全局未映射 Controller 和静态资源缺失统一返回真实 HTTP 404。该修复有独立全局回归测试，不改变已映射业务异常。它取代 ADR-001 中“完全不修改 GlobalExceptionHandler”的实现手段，但不放宽候选边界。
6. security-event GET、所有写动作、公开 OAuth/MCP 路由和生产域部署继续关闭。

### API2 归因证据

1. 独董会唯一身份是 `fbsir-eight-seat-board@26.7.20`；任何其他身份、多产品数组、董秘助手或“私董会”文本均阻断整 binding。
2. 离线输入只能生成 unsigned report-only candidate，权威产品 credit 恒为 0；候选默认关闭。
3. 候选必须同时满足：受信 natural authority、严格 JSON server receipt、字段级 production binding、成功阶段精确白名单、`whoami < scene_pack < consume`、channel/terminal/host/client-version 连续、快照摘要/运行发布/内嵌发布/报告时间全部对齐。
4. first value 是 consume 的结果维度，不替代 consume 工具阶段；closure 必须是 consume 之后的明确成功事件并继承同一宿主路线。
5. 自由文本、主体标识和未知维度不进入报告；非 allowlist 字段进入 redacted/unknown debt。
6. official-entry、应用 request、access 行和 business event 是不同计数层，禁止相加或跨层补数。

## 被否决的方案

- 直接创建静态候选路由：会绕过 RuoYi 菜单/权限事实源。
- 仅靠 `VITE_*` 在组件内隐藏：关闭态路由仍可探测。
- 给 admin 角色自动绑定管理菜单：会在委派租户权限模型未实现时扩大访问。
- 继续容忍全局未映射路径的 200 传输：会掩盖关闭态和路由漂移。
- 从 official HTTP 200、connector product 或客户端 `natural` 声明推导独董会使用：缺少产品和同 binding 证据。
- 用任意字符串 binding、子串工具名或 receipt observed 拼链：可造成跨用户/跨产品假阳性。

## 后果

- 正向：候选页面可以在本地等效环境真实挂载并保持生产默认关闭；租户选择不再依赖手填 ID；关闭态和 Bearer 隔离可由 HTTP/浏览器证明；归因器对多产品、混淆文本、失败事件和跨宿主拼接失败关闭。
- 代价：归因候选门槛严格，当前 API2 旧投影会得到 0/held，而不是猜测性转化；宿主和服务侧必须补齐产品、binding、回执与 26 小时不可变证据。
- 保留边界：本波不是生产上线，不开放 OAuth/MCP，不修改冻结专家包。

## 验证与证据

- `reports/independent-board/w4b2c-default-off-runtime-mount-verification-20260721.json`
- `reports/independent-board/api2-independent-board-24h-traffic-attribution-20260721.json`
- `scripts/independent-board-traffic-attribution.test.mjs`
- `FBSir-ui/scripts/verify-independent-board-w4b2c-runtime-mount.mjs`
- `scripts/run-independent-board-menu-migration-it.ps1`
- `IndependentBoardPortalReadHttpSecurityIntegrationTest`
- MySQL Community 8.0.30 与 8.4.8 的完整事务、菜单迁移、collision/drift 与清理门禁
