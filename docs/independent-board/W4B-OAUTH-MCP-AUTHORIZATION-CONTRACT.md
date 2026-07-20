# 独董会 W4b OAuth / MCP 授权合同

状态：`contract_locked_design_only`

品牌：福帮手 / FBSir

产品：独董会

产品版本：`26.7.20`

适用仓库：`u3w-independent-board-control-plane`

## 1. 目标、状态与禁止推断

W4b 在已验证的 W4a 权威 Connector 绑定之上，建立 WorkBuddy Connector 所需的
OAuth 授权码、PKCE S256、受保护资源发现、短寿命访问令牌、refresh-token family
轮换与 MCP Resource Server 合同。

本合同当前只证明协议、威胁模型、数据模型、状态机和实施门禁已锁定。它不证明 OAuth
端点已经开放、WorkBuddy 已联调、Connector 已上架、VIP 已真实连接或生产域名已经部署。
在本合同负向矩阵成为可执行测试并通过以前：

- `/oauth2/register`、`/oauth2/authorize`、`/oauth2/token`、`/oauth2/revoke` 和
  `/fbs-mcp/mcp` 公共路由必须保持不存在或关闭；
- AS metadata 不得发布未实现端点；PRM 不得引导客户端进入未实现流程；
- me/admin 的 OAuth、Connector 详细页面不得进入生产实现，必须先提交详细原型并取得
  用户明确确认；
- 旧若依 JWT、Gitee OAuth token、FBS API Key、权益兑换码均不得充当本合同的 OAuth
  code、access token 或 refresh token。

W4b 实现目标是 [MCP Authorization 2025-11-25](https://modelcontextprotocol.io/specification/2025-11-25/basic/authorization)
和基于已发布 OAuth RFC 的安全授权码配置文件；行为与
[`draft-ietf-oauth-v2-1-15`](https://datatracker.ietf.org/doc/html/draft-ietf-oauth-v2-1-15)
对齐，但截至 2026-07-20 OAuth 2.1 仍是 Internet-Draft，因此不得宣称“OAuth 2.1 RFC
合规”。

## 2. 规范优先级与输入证据

线协议语义冲突按以下顺序处理：

1. 已发布的 OAuth RFC 与 MCP 2025-11-25；
2. 不与上位规范冲突的当前仓库安全收窄合同；
3. WorkBuddy 当前真实握手兼容要求；
4. 当前仓库代码、迁移和可重复验证所证明的实现状态；
5. 外部开发资料和历史示例。

核心标准为 RFC 6749、6750、7009、7591、7636、8252、8414、8707、9207、9700、9728。
DPoP、mTLS、PAR、OIDC、CIMD 和通用第三方 DCR 不在首发范围；没有端到端实现和验证时，
metadata 中不得发布相关能力。

四份外部资料只作为只读设计来源，不是运行时或构建依赖。固定摘要见
`W4B-INPUT-EVIDENCE.json`。其中：

- WorkBuddy Connector 对接资料确认宿主采用 PKCE S256、loopback callback 和 DCR；
- 旧《MCP OAuth 交互流程》中的 token JSON 请求、缺失 `resource`、metadata 缺失时猜测
  端点等描述已过时，本合同分别改为 form-urlencoded、全流程精确 resource 和发现失败即
  fail closed；
- `mcp-oauth-demo.zip` 仅可用于理解交互顺序；其 HTTP、内存 token、宽松 DCR、无
  audience/tenant/VIP/token-family 的实现不得复制；
- WorkBuddy+专家开发规范 2.3 继续约束专家包，但不替代 OAuth/MCP 安全协议。

## 3. 固定拓扑与端点

首发唯一 issuer、resource、Connector 和前端同意页固定为：

| 对象 | 固定值 |
|---|---|
| issuer | `https://api2.u3w.com` |
| MCP resource | `https://api2.u3w.com/fbs-mcp/mcp` |
| PRM | `https://api2.u3w.com/.well-known/oauth-protected-resource/fbs-mcp/mcp` |
| AS metadata | `https://api2.u3w.com/.well-known/oauth-authorization-server` |
| profile-constrained DCR | `https://api2.u3w.com/oauth2/register` |
| authorize | `https://api2.u3w.com/oauth2/authorize` |
| token | `https://api2.u3w.com/oauth2/token` |
| revoke | `https://api2.u3w.com/oauth2/revoke` |
| MCP HTTP | `https://api2.u3w.com/fbs-mcp/mcp` |
| consent UI | `https://me.u3w.com/oauth/consent` |
| Marketplace entry | `fbs-connector` |

服务端始终发布并持久化表中固定的 canonical URI。入站 URI 只按 RFC 3986 将 scheme 与
host 大小写规范化后比较；path、port、query、fragment、尾斜线和别名不得修正。不得接受
HTTP 降级或运行时从 Host/Forwarded header 猜测 issuer。反向代理只接受部署白名单中的
可信转发头；配置缺失或漂移必须 fail closed。

Spring Boot `3.5.4` 的 BOM 管理 Spring Authorization Server `1.5.1` 和 Spring Security
`6.5.2`。公开端点实现阶段应使用该受管理版本承载标准协议语法与安全过滤链，不得私自
漂移版本。opaque token、摘要存储、受配置约束 DCR、W4a 绑定与实时 VIP 校验采用仓库内的
定制持久化和领域服务。

## 4. PRM、AS metadata 与挑战响应

PRM 只在资源服务器真实可用后发布，最低响应为：

```json
{
  "resource": "https://api2.u3w.com/fbs-mcp/mcp",
  "authorization_servers": ["https://api2.u3w.com"],
  "scopes_supported": [
    "identity.read",
    "entitlement.read",
    "board.meeting.reserve",
    "board.receipt.write"
  ],
  "bearer_methods_supported": ["header"],
  "resource_name": "福帮手 独董会 Connector"
}
```

AS metadata 只发布已经实现且通过测试的字段。W4b 完整实现后的最低集合为 issuer、
authorization endpoint、token endpoint、revocation endpoint、profile-constrained registration
endpoint、`response_types_supported=["code"]`、
`grant_types_supported=["authorization_code","refresh_token"]`、
`code_challenge_methods_supported=["S256"]`、四项 scope、
`token_endpoint_auth_methods_supported=["none"]`、
`revocation_endpoint_auth_methods_supported=["none"]` 和
`authorization_response_iss_parameter_supported=true`。opaque token 不发布 `jwks_uri`；
同进程服务端校验不伪装成外部 introspection endpoint。

首次无凭据 MCP 请求返回：

```http
HTTP/1.1 401 Unauthorized
WWW-Authenticate: Bearer resource_metadata="https://api2.u3w.com/.well-known/oauth-protected-resource/fbs-mcp/mcp", scope="identity.read entitlement.read board.meeting.reserve board.receipt.write"
Cache-Control: no-store
Pragma: no-cache
```

metadata 发现失败、issuer 不一致或返回未受信端点时，客户端和服务端都必须停止流程，
不得按路径惯例猜测端点。

## 5. WorkBuddy 兼容的受配置约束 DCR

通用匿名 DCR 的攻击面较大，但现有 WorkBuddy 握手资料明确包含无 Initial Access Token
的 DCR。没有 IAT、可验签 software statement 或其它宿主证明时，服务端无法依据请求形状
认证调用者就是 WorkBuddy。W4b 因此提供“未认证 public client 的受配置约束 RFC 7591
注册”，而不是“仅 WorkBuddy 可用的可信注册”，也不得扩张为通用 OAuth 平台。

`source_code=WORKBUDDY` 在首发只表示用户选择了 WorkBuddy 兼容产品路径，不表示完成宿主
密码学认证；DCR `client_id` 也不是宿主证明。W4a/W4b 回执继续停留在
`ACTION_COMPLETED`。如果未来需要可信宿主品牌，必须由 WorkBuddy 提供一次性 IAT、可验证
宿主声明或等效信任锚，并以新合同和负向测试升级；不得把共享在 public client 中的 secret
当成证明。

注册线协议为 HTTPS `POST application/json`。未知 client metadata 按 RFC 7591 忽略且
不得触发 URL 抓取；重复的安全关键字段、错误类型或下列已知字段不匹配时拒绝。注册请求
必须同时满足：

- `token_endpoint_auth_method` 精确为 `none`；客户端是 public client，不签发 secret；
- `grant_types` 精确为 `authorization_code`、`refresh_token`；
- `response_types` 精确为 `code`；
- 恰好一个 `redirect_uri`，且精确匹配
  `http://127.0.0.1:{1024..65535}/oauth/callback`；
- 禁止 `localhost` 主机名、IPv6、通配符、前缀匹配、userinfo、query、fragment、路径变体、
  私网任意地址和远程 redirect；
- 不获取客户端提供的 logo、policy、JWKS 或其它 URL，不允许 metadata SSRF；
- 客户端名称不作为可信身份。服务端同意页固定显示“未验证的本地公共客户端”，并另行说明
  当前由用户选择的是 WorkBuddy 兼容连接路径；必须清晰展示完整 redirect URI，至少让
  用户直接看到 `127.0.0.1` hostname 和实际端口；不渲染客户端提供的 HTML；
- 单 IP 摘要、时间窗和全局容量实施有界限流；只存 IP 摘要，不存原始 IP；
- client id 使用至少 256 bit CSPRNG，31 天绝对有效期，不因调用自动滑动延长；
- client 过期、撤销或不再匹配唯一 redirect 时，授权和刷新均 fail closed。

DCR 成功返回 `201 application/json`、`Cache-Control: no-store`、`Pragma: no-cache`，
并包含 `client_id`、Unix 秒 `client_id_issued_at`、Unix 秒 `client_id_expires_at` 以及服务端
最终登记的 `redirect_uris`、`grant_types`、`response_types`、
`token_endpoint_auth_method` 和中性 `client_name`。客户端必须在过期后重新注册；family
期限不得超过 client 期限。`client_id_expires_at` 是 U3W 响应扩展，不是 RFC 7591 标准
字段；W4c 必须验证 WorkBuddy 会忽略未知响应字段，并能在 `invalid_client` 后重新注册，
否则不得启用 client 过期策略。错误使用 RFC 7591 的 `invalid_redirect_uri` 或
`invalid_client_metadata`，不得反射不受信 HTML/URL。注册端点未通过负向矩阵前不得出现
在 AS metadata 中。

## 6. 授权码、PKCE 与同意

授权请求必须包含：`response_type=code`、受配置约束 DCR 返回的 `client_id`、精确
`redirect_uri`、`code_challenge`、`code_challenge_method=S256`、非空 `state`、固定
四项 scope 和固定 `resource`。

- verifier 使用每次事务独立的至少 256 bit CSPRNG，长度 43–128，字符集为 RFC 7636
  unreserved；challenge 是精确 SHA-256 base64url（无 padding）；`plain` 永远拒绝。
- 服务端强制 challenge 形状；无法从 challenge 判断客户端熵，因此客户端熵属于 W4c
  联调验收项。
- `state` 由 WorkBuddy 生成，建议至少 128 bit 熵；服务端限制为 16–512 个可打印 ASCII
  字符，将摘要绑定到授权事务，并仅以仓库密钥引用控制的 AES-GCM 密文临时保存返回值。
- 授权请求句柄同样使用至少 256 bit CSPRNG，只保存 SHA-256 摘要，5 分钟单次有效。
- me 同意 API 只接受已登录若依用户的服务端主体和显式企业上下文；浏览器不得提交
  tenant/member/user、scope、resource、client 或 redirect 的权威值。
- 同意时实时锁读 active enterprise/member 和精确 VIP plan/entitlement；非 VIP、
  `PENDING_CONNECTOR` 以外的不合法状态或身份漂移均拒绝且零授权写入。
- W4b 不实现 OIDC，因此不签发 ID token、不提供 userinfo，也不使用 OIDC nonce。
- 授权响应带原始 `state` 和 RFC 9207 `iss=https://api2.u3w.com`；WorkBuddy 必须同时
  精确验证 state 与 iss。
- authorization code 使用至少 256 bit CSPRNG，数据库只存 SHA-256 摘要，60 秒有效，
  只能兑换一次并绑定 client、redirect、PKCE、resource、scope、tenant/member/user。
- `client_id` 或 `redirect_uri` 缺失、无效或不匹配时，授权服务器必须在本地错误页停止，
  绝不能向该 URI 重定向；只有 redirect 已被精确验证后，才可按 OAuth 错误响应重定向。

详细 consent、连接、断开和管理审计页面属于独立 UI 原型门禁。此合同允许实现内部
request/approve 服务和测试，不允许绕过原型确认直接实现或上线页面。

## 7. Token profile

Token endpoint 仅接受 HTTPS `POST application/x-www-form-urlencoded`，拒绝 JSON、query
token、Cookie token和重复参数；按 RFC 6749 忽略无法识别的请求参数。授权码兑换必须
提交精确 client、verifier 和 resource；为兼容 WorkBuddy，允许再次提交 redirect，存在时
必须精确匹配，按 OAuth 2.1 draft-15 省略时也可接受。刷新必须提交精确 client、refresh
token 和 resource，且不得扩大 scope。

首发采用 opaque bearer token：

| 对象 | 原文长度/熵 | 有效期 | 持久化 |
|---|---|---|---|
| request handle | >= 256 bit | 5 分钟 | SHA-256 摘要 |
| authorization code | >= 256 bit | 60 秒 | SHA-256 摘要 |
| access token | >= 256 bit | 10 分钟 | SHA-256 摘要 |
| refresh token | >= 256 bit | family 剩余期 | SHA-256 摘要 |
| token family | 不适用 | 最长 30 天绝对期限，且不超过 client 期限 | 服务端状态 |
| DCR client | >= 256 bit client id | 31 天绝对期限 | client id 非秘密，可明文 |

U3W 服务端对 raw code、access token 和 refresh token 仅作单次签发或请求校验，并且只
持久化摘要；不得写入数据库、Redis、URL query、日志、异常、指标 label 或回执。
WorkBuddy 必须在一次授权事务内临时保管 verifier，并在 OS 安全凭据存储中保管 access/
refresh token；不得使用 Web localStorage、普通配置文件或日志。本合同未启用 DPoP/mTLS，
因此不得声称 access token 防重放；10 分钟寿命与实时授权读取仅缩短泄露窗口。

每次 refresh 成功必须：

1. 锁定 family 和当前 refresh token；
2. 将旧 refresh token 原子标记为 `USED`；
3. 生成新 access token 和新 refresh token；
4. generation 加一，但 family 的既定绝对期限不延长；
5. 同事务写入不可变轮换回执。

任何已使用 refresh token 的再次提交，包括并发重复刷新，都把整个 family 标为
`COMPROMISED`，撤销所有活跃 token，并在同事务撤销对应 W4a binding。W4c 必须证明
WorkBuddy 不会并发刷新；该实机证据是公开 refresh endpoint 的前置门禁。若宿主确实并发
刷新，必须重新设计串行协调或发送方约束，不得通过“宽限复用窗口”掩盖竞争。

授权码和刷新成功均返回 `200 application/json`、`Cache-Control: no-store`、
`Pragma: no-cache`。响应包含 `access_token`、`token_type=Bearer`、`expires_in=600`、
新的 `refresh_token`、固定 `scope`；不得返回内部 tenant/member/binding/family 主键。

RFC 7009 revoke 只接受 HTTPS `POST application/x-www-form-urlencoded`：`token` 必需，
`token_type_hint` 可选，未知 hint 必须忽略并跨支持的 token 类型查找；public client 必须
提交有效、未过期的 `client_id`。服务端在产生撤销副作用前必须确认 token 属于该 client。
有效 client 提交未知、已撤销或属于其它 client 的 token 均返回 200 且零副作用；无效或
过期 client 返回标准 `invalid_client`。成功响应是 200 空 body 并带 `no-store/no-cache`；
临时失败返回 503，调用方不得把 503 记为已经撤销。VIP 降级、权益撤销、member/enterprise
停用、用户主动断开、管理员授权撤销和 refresh 重放均必须联动 family 与 binding。VIP
再次升级不得复活旧 family 或旧 binding。

## 8. 固定身份、scope 与实时授权

唯一 scope 集合为：

```text
identity.read
entitlement.read
board.meeting.reserve
board.receipt.write
```

授权、code、family、token 和受保护请求的 scope 集合必须与这四项完全相等；不得使用
前缀、子串、大小写修正、缺项或增项。W4a 已把四项定义为首发不可拆分的最小产品能力簇，
同意页必须逐项展示；未来若要做更小权限，必须新增版本化 plan/scope 合同和数据库迁移，
不能在本版本静默改变。唯一 audience/resource 为固定 MCP resource。

除下一节定义的首次激活窄路径外，每个受保护请求都必须完成：opaque token 摘要命中、
token ACTIVE、family ACTIVE、family 未过期/撤销/compromised、精确 issuer/resource/
client/scope、tenant/member/user、principal subject digest、binding id 和 binding version
一致，以及 W4a 的实时 enterprise/member/VIP plan/entitlement/binding current-read。
密码学或摘要有效不能替代实时 VIP 权益。`PENDING_BINDING` family 只能调用
`initialize` 或 `tools/list` 进入下一节的原子激活流程；其它 MCP 方法一律拒绝。

token 只能通过 `Authorization: Bearer` 传输；同一请求在 header、body、query 或 Cookie
重复携带即 `400 invalid_request`。MCP 不得把本 token 透传给其它上游或下游服务；下游
调用必须取得独立 audience 的凭据。

## 9. 数据模型与不变量

W4b 使用新的 `public_init_033`，不修改 W4a 三表的列、索引、CHECK、FK 或 trigger，避免
破坏 W4a 完成态重放。最小六表为：

1. `fbs_oauth_client`：受限 WorkBuddy public client、唯一精确 loopback redirect、状态、
   metadata 摘要、注册来源摘要和 31 天绝对期限；
2. `fbs_oauth_authorization_request`：请求句柄摘要、client、redirect、challenge、state
   摘要与密文、resource/scope、同意主体和 PENDING/APPROVED/DENIED/CONSUMED/EXPIRED；
3. `fbs_oauth_authorization_code`：code 摘要、request/client/identity/PKCE/resource/scope、
   ACTIVE/USED/REVOKED/EXPIRED、60 秒期限和兑换出的 family；
4. `fbs_oauth_token_family`：identity、client、W4a binding、subject 摘要、resource/scope、
   `PENDING_BINDING/ACTIVE/REVOKED/COMPROMISED/EXPIRED`、当前 refresh generation、
   最长 30 天且不超过 client 的绝对期限和乐观锁版本；
5. `fbs_oauth_token`：family、ACCESS/REFRESH、token 摘要、generation、
   ACTIVE/USED/REVOKED/EXPIRED、签发/使用/撤销/期限；
6. `fbs_oauth_receipt`：授权、family 激活/重授权、轮换、撤销、重放检测等不可变
   `ACTION_COMPLETED` 事件，不保存秘密原文。

固定四 scope 使用 canonical ASCII 字符串加摘要，不另建可漂移 scope 表。数据库必须用
ascii_bin、CHECK、FK 和唯一键约束固定常量与状态；token/code 摘要为 32-byte binary 或
64 字符小写十六进制，不接受弱散列。不可变 receipt 由 UPDATE/DELETE trigger 与生产
运行账户 `SELECT/INSERT` 最小权限共同保护；运行账户不得有 DDL/TRUNCATE/DROP。

family 以 nullable slot 唯一键保证同一 tenant/member/product/source/connector 最多一个
`ACTIVE` 和一个 `PENDING_BINDING`。终态行的 slot 为 NULL。新 code 兑换会原子撤销旧
pending family；在新 family 首个受保护请求成功以前，旧 active family 不被隐式替换。

## 10. 首次受保护请求与显式重授权

OAuth 同意、code 签发、token 签发、DCR 成功或普通 HTTP 200 均不能激活 VIP。
family 在 token 兑换后保持 `PENDING_BINDING`。只有 bearer 全部通过且 MCP 方法是
`initialize` 或 `tools/list` 时，服务端才能构造不可由客户端提交的 W4a attestation。

首次激活必须在一个事务中：

1. 锁 active enterprise/member；
2. 锁精确 entitlement 与 VIP plan；
3. 锁 W4a binding 与 scope；
4. 锁旧 active family、新 pending family 及相关 token；
5. 新建或显式重授权 W4a binding，使 `valid_until` 等于 family 绝对期限；
6. 终态化旧 active family，激活新 family，并记录绑定版本；
7. 最后写 W4a 和 W4b 不可变回执；任一失败全部回滚。

W4a 普通 confirm 继续禁止 REVOKED/COMPROMISED 隐式恢复。W4b 必须新增具名的显式
reauthorize 内部路径：它只接受新的 PENDING family、完整 bearer 验证和首次受保护请求，
更新同一唯一 binding 行并产生新一代证据。W4a receipt 可继续使用语义准确的
`CONNECTOR_BINDING_VERIFIED`；W4b receipt 另记 `TOKEN_FAMILY_REAUTHORIZED`，无需修改
W4a 表合同。

公开路由前必须先完成两项 W4a 重构：

- `BoardConnectorBindingPort` 暴露受保护请求确认/显式重授权的正式内部端口；
- 将 W4a 回执写入推迟到 family/token 变更之后，确保统一锁序和同事务回滚。

## 11. 锁序、并发与状态机

统一锁序为：

```text
enterprise/member
  -> entitlement/plan
  -> connector binding/scope
  -> oauth client/request/code（存在时）
  -> old active family
  -> new pending family
  -> oauth tokens
  -> W4a receipt
  -> W4b receipt
```

授权前阶段没有 binding/family 时，按上述序列跳过不存在对象，不得反向锁定。code 兑换
使用状态 CAS，只有一个并发者可以从 ACTIVE 变为 USED；成功兑换后再次使用同一 code
必须返回 `invalid_grant`，若已关联 family 则按安全事件撤销/compromise 该 family。

状态机为：

```text
client: ACTIVE -> REVOKED | EXPIRED
request: PENDING -> APPROVED | DENIED | EXPIRED -> CONSUMED
code: ACTIVE -> USED | REVOKED | EXPIRED
family: PENDING_BINDING -> ACTIVE -> REVOKED | COMPROMISED | EXPIRED
token: ACTIVE -> USED(refresh only) | REVOKED | EXPIRED
binding: absent -> ACTIVE -> REVOKED | COMPROMISED
         terminal --explicit new-family reauthorization only--> ACTIVE
```

## 12. SecurityFilterChain 与若依隔离

OAuth AS、MCP Resource Server 与现有若依登录使用三种不同信任边界：

- 高优先级且路径限定的 OAuth AS chain 处理 metadata/register/authorize/token/revoke；
- 独立的 MCP chain 只处理 `/fbs-mcp/mcp` 和 PRM，并验证本合同 opaque bearer；
- 现有若依 chain 继续处理 me/admin JWT 登录和权限。

现有 `JwtAuthenticationTokenFilter` 不得抢先解析 OAuth bearer。OAuth/MCP chain 不调用
现有 `TokenService`，若依 JWT 也不得访问 MCP。CSRF、session 和 CORS 按链分别配置：
token/register/revoke 是非 Cookie API；authorize/consent 的浏览器交接必须绑定一次性请求
与已登录主体。不得全局放开匿名 URL 或宽松 CORS。

禁止复用现有 Gitee OAuth 的日志、URL token 回传和 state 处理，禁止复用明文
`fbs_auth_code`，禁止使用关闭 TLS/hostname 校验的 HTTP 工具，禁止把历史无鉴权 MCP/SSE
示例计作 W4b 实现。

## 13. 错误、日志与隐私

所有 MCP 401 响应都必须携带 Bearer `WWW-Authenticate` 和固定 PRM 的
`resource_metadata`；无效 token 额外携带 `error="invalid_token"`。所有 scope 不足的 403
响应都必须在 Bearer challenge 中携带 `error="insufficient_scope"`、所需 `scope` 和同一
`resource_metadata`。

| 场景 | 外部结果 |
|---|---|
| MCP 无 token | 401 Bearer challenge，不带 `error`，带 `resource_metadata` |
| token 无效/过期/撤销/受众错误 | 401 Bearer challenge，带 `error=invalid_token` 与 `resource_metadata` |
| scope 不足 | 403 Bearer challenge，带 `error=insufficient_scope`、所需 `scope` 与 `resource_metadata` |
| token 重复位置或请求畸形 | 400 `invalid_request` |
| authorize 的 resource 缺失/不匹配且 redirect 已验证 | redirect OAuth error `invalid_target`；redirect 未验证则本地停止 |
| token/refresh 的 resource 缺失、别名或不匹配 | 400 JSON `invalid_target` |
| code/PKCE/redirect/client 无效 | token endpoint `invalid_grant` 或 `invalid_client`，不泄露细节 |
| scope 扩大 | `invalid_scope` |
| revoke 未知或重复 token | 200 |
| revoke 无法完成 | 503，不产生成功回执 |

OAuth 响应使用 `no-store/no-cache`；安全响应不反射不受信输入。日志和 tracing 仅记录
correlation id、固定 reason code、端点、结果、耗时与截断后的非秘密标识摘要。必须通过
自动测试扫描 Authorization header、code、token、verifier、state、Cookie 和密钥泄漏。

## 14. 最小生产权限与密钥

- migration 账户与 runtime 账户分离；runtime 对 token/family/request/client 表只获得业务
  所需 SELECT/INSERT/UPDATE，不授予 DELETE/DDL；receipt 仅 SELECT/INSERT；
- state AES-GCM 密钥只以环境/密钥管理引用注入，数据库不保存密钥；缺失、轮换不明或解密
  失败均 fail closed；
- CSPRNG 使用 JDK `SecureRandom`，摘要使用 SHA-256，秘密比较使用常量时间；
- 不把 OAuth client secret 作为 public client 安全控制；
- 运维清理只终态化和保留有界审计，不通过业务运行账户硬删除安全证据。

## 15. 可执行负向矩阵

公开路由门禁至少覆盖：

- N01–N07：PRM/resource/authorization_servers/issuer/HTTPS/SSRF/S256 metadata；
- N08–N15：缺 PKCE、plain、verifier 形状/散列/降级、code 过期/重放、state/iss、redirect
  精确匹配；
- N16–N23：授权/兑换/刷新缺 resource、audience 别名或多受众、issuer/time 无效、无 token、
  scope 缺失、token 多位置/query；
- N24–N29：refresh 重放整族撤销、scope/resource 扩大、VIP 降级、再升级不复活、revoke
  幂等和 503；
- N30–N36：token passthrough、危险 URI scheme、redirect/DNS rebinding、step-up 有界失败、
  未启用能力不发布、未来 DPoP 预留测试、秘密日志扫描；
- N37：DCR redirect 为 localhost/IPv6/私网/远程/带 query/fragment/双 redirect，全部拒绝；
- N38：DCR 未知 metadata 被忽略且零下游访问；已知 URL metadata 不取回；已知字段错误或
  容量/速率超限稳定拒绝；
- N39：旧若依 JWT、Gitee token、FBS API Key 请求 MCP，全部 401；
- N40：OAuth bearer 请求普通若依 API，不能被识别为后台登录；
- N41：code 两路并发仅一方成功，另一方不能得到 token；
- N42：refresh 两路并发触发整族 compromise 和 binding 撤销；
- N43：family 激活中 W4a/W4b 任一回执失败，binding/family/token 全回滚；
- N44：active/pending slot、锁顺序、RR 竞争、VIP 降级竞争无死锁或越权副作用；
- N45：未确认 OAuth 页面原型时，代码库无生产 consent/connector 深页和公开路由。

## 16. 实施分段与证据边界

1. **W4b.1 合同与内部底座**：本合同、来源摘要、`public_init_033`、内部领域服务和负向
   单元/真实 MySQL 竞争测试；公共路由关闭。
2. **W4b.2 页面原型**：me 连接/同意/断开和 admin client/family/安全事件审计原型；取得
   用户明确确认。
3. **W4b.3 协议端点**：独立 SecurityFilterChain、PRM/metadata、受配置约束 DCR、authorize、
   token、revoke、MCP initialize/tools-list；HTTP 负向矩阵全通过后才允许本地开启。
4. **W4c 宿主联调**：仓库内自包含 Connector、Marketplace Override、真实 WorkBuddy
   loopback/DCR/PKCE/refresh 串行行为、首个受保护请求和可撤销绑定证据。

每段分别记录 `design_locked`、`source_verified`、`mysql_verified`、`http_local_verified`、
`workbuddy_host_verified`、`production_verified`，不得跨层推断。W4b.1 完成不等于 OAuth
端到端完成；W4c 本地联调通过不等于 Marketplace 上架或生产 VIP 商业闭环。
