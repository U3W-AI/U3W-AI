# W1 闭环冲刺运行手册：API2 部署 → 自然同绑定链 → admin 六维读回

状态：U3W 已部署默认关闭 + 观测激活；API2 W1 发布器已在 `fbss-phase1` 生产版本 `20260725-181736` 激活。当前仍无自然同绑定证据。
本手册覆盖从 API2 部署到取得可追溯同绑定闭环证据的全序列。

## 前置条件

- U3W 生产：release 13c203a5 已上线，观测开关已激活（writer/classifier/admin-read=true，credit/public-route=false）
- 事件账本：1 PROBE journey / 3 PROBE events，0 NATURAL（干净基线）
- 官方专家包：fbsir-eight-seat-board@26.7.21 字节未变（sha 57443e8f…）
- API2 候选：fubangshou/FBSAI@ce68425846e2b0271ede3569ec7cf2a4e457848c 已提交推送；生产 sourceGitHead 为 `74aed6fe95826bc4b55cde5042994d28bac4ba91`；active release critical 1279 文件字节已回读并与候选集合重算一致。
- U3W 候选：U3W-AI@be231f229b316472864d40704e66cb0b34ce7e19；线上仍为旧 JAR `5b3582e2e4f97ab8a09845a1b6940e334cb2452a2c1970a01aeaf40dd1cc5cc1`，候选锁后重放修复尚未部署。
- 生产 SSH：`ssh api2`（root@api2.u3w.com，密钥 id_ed25519_api2）
- GitHub SSH：`ssh git@github.com`（密钥 id_ed25519_github_codex）

## 阶段一：API2 W1 发布器 active release 只读读回

### 1.1 准备候选包
```powershell
# 在 U3W 仓内，从 API2 净室候选准备部署包
# 候选源：fubangshou/FBSAI@ce68425846e2b0271ede3569ec7cf2a4e457848c (codex/w1-official-experts-publisher)
# 已有净室：work/api2-cleanroom/p1-005-candidate-20260722/
```

### 1.2 部署到生产
```bash
# 生产变更已在授权发布窗口完成；本轮仅允许只读回读，不重复部署或重启。
ssh api2 "systemctl is-active fbss-phase1.service && curl -fsS http://127.0.0.1:8080/api/fbss/health"
```

### 1.3 active release 文件哈希读回
```bash
# 逐文件 SHA-256 对比部署后文件与候选包
ssh api2 "readlink -f /opt/fbss/phase1/current && systemctl show -p ActiveState fbss-phase1.service"
# 与候选包的 manifest 对比：packageManifestSha256=
# 683865A7134CD5B9FE3B80D9FEF033461606407E7337076E51719A90B77A7351
# criticalDeploySnapshotSha256=
# 921001A0FFA1A41D17763F75F642135A43DB44377403F8AB72B464CAB1E8886B
```

### 1.4 验证 outbox 与归因模块
```bash
ssh api2 "curl -fsS http://127.0.0.1:8080/api/fbss/health | jq '.independentBoardAttributionPublisher'"
# 当前事实：publisher ready/workerRunning=true，但 pending/delivered=0；严格 outbox 与 Host Receipt 仍未就绪。
```

## 阶段二：合同 pin 推进

### 2.1 从部署提交重跑就绪评估器
```powershell
# U3W 部署源提交是 13c203a5；HEAD 6528a7a8 仅追加脚本
# 选项A：从 13c203a5 重跑（需 git checkout 13c203a5）
# 选项B：从 6528a7a8 重跑（需先证明 JAR 字节等价，然后新建 release plan）

# 已知 SHA 参数（来自 work/production-readiness/w1a-production-readiness-latest.json）：
$ExpectedBackupReceiptSha256 = "2d7499eedebba97cf968189a497ff0c1822619c956b1d520b871625b3ef3ea55"
$ExpectedLegacyBaselineReceiptDigest = "742fe3ab0aac3dc745b42fdd5b196132c9285964d23d8cbc516d554cfa484ac1"
$ExpectedAdminRootDependencyAdoptionReceiptSha256 = "891b6f3856597c37ad2b9b0bb877eb8d7ccf627395c1229ac889ce8ebd34acf4"
$ExpectedConfigurationReceiptSha256 = "69d36fca3304bda79830a9ceea363fa540cf959f249c92e4c769584ebd99dd57"
$ExpectedReleasePlanReceiptSha256 = "8ad8e26c3d8a7652f5048c592be9a82c4c53201176686ed10837e504eb0759f7"
$ExpectedDeploymentReceiptSha256 = "8f7167018a99ba5740418e7693f302a6dbadaae1ac84efe1510bcaab7b1e7731"

powershell -NoProfile -ExecutionPolicy Bypass `
  -File scripts/verify-independent-board-production-readiness.ps1 `
  -ExpectedCommit <commit> `
  -ExpectedBackupReceiptSha256 $ExpectedBackupReceiptSha256 `
  -ExpectedLegacyBaselineReceiptDigest $ExpectedLegacyBaselineReceiptDigest `
  -ExpectedAdminRootDependencyAdoptionReceiptSha256 $ExpectedAdminRootDependencyAdoptionReceiptSha256 `
  -ExpectedConfigurationReceiptSha256 $ExpectedConfigurationReceiptSha256 `
  -ExpectedReleasePlanReceiptSha256 $ExpectedReleasePlanReceiptSha256 `
  -ExpectedDeploymentReceiptSha256 $ExpectedDeploymentReceiptSha256 `
  -RequiredStage DEPLOYED_DEFAULT_OFF -RequireReady
```

### 2.2 协调更新合同 pin 与真源
需要同步更新的文件（按依赖顺序）：
1. `scripts/verify-w1a-current-observation.ps1` — 当前观测阶段门禁；旧 `verify-independent-board-control-plane.ps1` 保留为准备阶段历史门禁
2. `reports/independent-board/w1a-production-readiness-latest.json` — 用新评估器输出覆盖
3. `reports/independent-board/w1a-production-readonly-audit-latest.json` — 重跑只读审计
4. `docs/independent-board/implementation-status.json` — w1a.state, candidateCommit, featureFlags, notProven
5. `docs/independent-board/taskboard.json` — W1 wave state, completedSubset, remaining
6. `reports/independent-board/w1a-official-experts-attribution-verification-latest.json` — status, u3w.*, liveReadback, proves/notProven

### 2.3 当前观测门禁必须通过
```powershell
Set-ExecutionPolicy -Scope Process Bypass -Force
.\scripts\verify-w1a-current-observation.ps1
# 必须输出 schema=fbsir.w1aCurrentObservationVerification.v1, status=PASS
```

## 阶段三：自然同绑定链（用户真实会话）

### 3.1 会话前预检
- [ ] 确认 fbsir-eight-seat-board 专家在 WorkBuddy 专家中心可见且可启动
- [ ] 确认 fbs-connector 已连接（连接器状态：connected）
- [ ] 确认 U3W 观测开关已激活（writer/classifier/admin-read=true）
- [ ] 确认 API2 W1 发布器已部署且 outbox 就绪
- [ ] 记录会话前账本基线：PROBE journey=1, PROBE events=3, NATURAL=0

### 3.2 用户执行真实会话（约 5 分钟）
1. 在 WorkBuddy 左侧栏打开「专家」→ 找到「独董会」(fbsir-eight-seat-board@26.7.21)
2. 开始对话，完成一次首值链：
   - **ENTRY_OBSERVED**：专家会话启动（API2 whoami_emitted 事实产生）
   - **INTENT_CLASSIFIED**：用户表达意图（API2 scene_pack_resolved 事实产生）
   - **FIRST_VALUE_COMPLETED**：首值完成（API2 first_value_completed 事实产生）
3. 三段事件由 API2 签名后推送到 U3W 内部入口 → 追加账本

### 3.3 会话后验证
```bash
# 查询账本：应新增 1 NATURAL journey / 3 NATURAL events
ssh api2 'U=$(grep "^WXFBSIR_MYSQL_USERNAME=" /etc/u3w/fbsir-admin.env | cut -d= -f2); \
  P=$(grep "^WXFBSIR_MYSQL_PASSWORD=" /etc/u3w/fbsir-admin.env | cut -d= -f2); \
  mysql -u"$U" -p"$P" fbsir -N -e \
  "SELECT traffic_class, event_type, COUNT(*) FROM fbs_board_attr_event_v1 \
   GROUP BY traffic_class, event_type ORDER BY 1,2;"'
# 预期：PROBE 3 events + NATURAL 3 events，分母不合并
```

### 3.4 admin 六维读回
```bash
# 通过 admin 只读 API 查询（需 admin 角色和 board:attribution:query 权限）
# 窗口：会话时间前后 1 小时
# mode=NATURAL → 应返回 1 个聚合组合，3 事件
# mode=PROBE → 应返回 1 个聚合组合，3 事件
# mode=ALL → 2 个聚合组合，6 事件
# 关键验证：probe 与 natural 分母分离，productCredit=0
```

## 完成定义

闭环证据要求：
1. API2 active release 文件哈希读回回执 ✅
2. 一次真实官方 experts 同绑定三事件回执（NATURAL traffic_class）❌ 未取得
3. admin 六维读回定位该会话，probe/natural 分母分离 ❌ 未取得自然会话
4. authoritative_product_credit = 0 ✅（fail-closed）
5. 官方 experts 包字节未变 ✅
6. 合同 pin 推进到部署提交，合同门禁通过 ❌ U3W 候选仍待发布验证
7. taskboard/implementation-status/verification 报告同步更新 ✅
