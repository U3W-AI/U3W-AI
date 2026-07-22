# W3j 独董会积分候选 HTTP 激活预检合同

状态：`IMPLEMENTATION_CANDIDATE`（只读预检；不是部署、迁移或激活授权）

## 1. 目标与事实边界

W3f/W3g 已证明影子账本、默认关闭 HTTP、候选菜单/UI 和 loopback 浏览器 fixture；它们不证明 `api2.u3w.com`、`admin.u3w.com` 的生产版本、数据库或角色授权。W3j 提供一个可执行的、失败关闭的 release-receipt 预检器，使后续人工候选激活必须携带同一 release 的提交、构建、数据库、权限、JWT 读回和回滚证据。

它只读取 JSON receipt、域名清单、冻结包与本地 Git 状态。它绝不连接数据库、调用写接口、设置服务端/前端 flag、启用菜单、绑定角色、上传工件或修改 `D:\Spg719\fbsir-eight-seat-board-26.7.20.zip`。

当前事实假设：`api2.u3w.com` 仅可达但版本未绑定，`admin.u3w.com` 未部署；所以本切片的成功结果只能是预检器与合成 receipt 通过，不是生产候选已激活。

## 2. 输入合同

预检器接受一个严格 JSON receipt，所有字段必填且禁止未知字段：

```text
schema = fbsir.independent-board.credit-candidate-release-readiness/v1
phase = CANDIDATE_ACTIVATION
sourceCommit = 40 位小写 Git SHA
frozenPackage.sha256 = 冻结包 SHA-256
target.backendHost = api2.u3w.com
target.adminHost = admin.u3w.com
target.backendBuildSha256 / frontendBuildSha256 / domainInventorySha256 = 64 位小写 SHA-256
database = public_init_038 + 两个精确 MySQL profile + firstApply/replay/currentRead=PASS + sysUserPointsProjection=ZERO_DRIFT
access = 候选菜单迁移、已启用菜单、1..3 个显式管理员角色绑定及绑定摘要
flags = backendCandidateEnabled=true + frontendCandidateEnabled=true
readback = JWT 401/403/200/409、旧入口 410、回滚后 404 均为 PASS
rollback = 两个 flag 已可关闭、菜单可禁用、角色可解绑
```

receipt 只记录摘要、状态和固定 host，禁止记录 token、密码、连接串、用户身份、角色名称、请求 body 或自由文本日志。解析器会拒绝任意对象中的重复键、缺失键和未知键。`sourceCommit` 必须与调用方显式给出的 `-ExpectedCommit` 一致；该 SHA 必须是本地可解析的 commit，且必须等于当前 `HEAD`。预检器也会要求当前工作树干净，防止把未提交字节部署为候选。域名清单不可由调用方替换：它固定为当前检出版本中的 `config/deployment/u3w-domain-inventory.json`，并以 `target.domainInventorySha256` 绑定到 receipt。

仓库内的 `.example.json` 是不含真实构建或访问证据的结构样板，不能作为激活回执。`-SelfTest` 会仅在内存中把样板的提交与域名清单摘要替换为当前检出值，以验证门禁逻辑；真正回执必须在候选构建、数据库和 JWT/回滚读回均完成后由负责方生成，并在同一干净 checkout 上复核。

## 3. 执行与退出语义

```powershell
powershell -NoProfile -ExecutionPolicy Bypass `
  -File scripts/verify-independent-board-credit-candidate-release-readiness.ps1 `
  -ReceiptPath <release-receipt.json> `
  -ExpectedCommit <40-lowercase-sha>

powershell -NoProfile -ExecutionPolicy Bypass `
  -File scripts/verify-independent-board-credit-candidate-release-readiness.ps1 -SelfTest
```

成功时输出唯一的脱敏 JSON 结论，包含 receipt SHA-256、commit、目标 host、迁移 ID 和 `readyForHumanActivationReview=true`。任何字段遗漏、未知字段、重复 JSON 键、hash/commit 不匹配、非清洁工作树、非清单域名、未证明的数据库/JWT/回滚项或冻结包漂移都会以非零退出失败关闭。

## 4. 实施边界与验收

- 必做：严格对象键（包括重复键拒绝）/枚举/格式校验；比对不可替换的 `config/deployment/u3w-domain-inventory.json` 及其 receipt SHA-256；比对本地冻结哈希、当前 `HEAD` 和 Git clean 状态；自测一个有效 receipt 和至少六个负向向量。
- 先征求人工 release 负责人确认：实际数据库迁移、目标服务部署、任何 flag/menu/role 变更、真实 JWT 写读、切流和回滚。
- 禁止：把 receipt 预检通过表述为生产上线；自动连接或写生产；在 receipt/报告中存储密钥或身份数据；修改冻结提审包。

验收为脚本自测、JSON 负向用例、`git diff --check` 与中央 Contract 门禁通过。真实激活还必须先满足 W3f/W3g 合同的 production migration、版本绑定、最小角色授权、实际 JWT 读回和回滚回执。
