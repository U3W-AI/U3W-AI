# OpenSpec #3 测试方案

> **Change ID**: `add-enterprise-scene-pack-ops`
> **范围**: 企业侧场景包与成员分发（阶段 1–6，已完成实施）
> **生成日期**: 2026-04-10

---

## 一、现状分析

### 1.1 已有测试文件与覆盖情况

| 测试类 | 用例数 | 覆盖范围 |
|--------|--------|----------|
| `FbsEnterpriseBusinessServiceTest` | 8 | 创建/查询/禁用（部分） |
| `FbsEnterprisePackBusinessServiceTest` | 9 | 分发/撤销/详情（部分） |
| `FbsEnterpriseMemberBusinessServiceTest` | 5 | 添加/移除成员（部分） |
| `RightsCheckServiceTest` | 5 企业用例 | 校验链路（企业路径） |
| `SkillConsumeServiceTest` | 7 企业用例 | 消费链路（企业路径） |
| **合计** | **34** | |

### 1.2 缺口分析

以 `spec-delta.md` 中的 Scenario 为基准逐一核对，发现以下 scenario **在规范中有定义但未落入单元测试**：

| # | 缺失 Scenario | 所在规范 | 风险等级 |
|---|-------------|----------|----------|
| 1 | 企业名称重复 → 拒绝创建 | 企业组织管理 | P1 |
| 2 | 禁用已禁用企业 → Fail-Closed | 企业组织管理 | P1 |
| 3 | 禁用不存在企业 → Fail-Closed | 企业组织管理 | P1 |
| 4 | 重新授权（已撤销→恢复，usedQuota归零） | 企业场景包分发 | P1（已有服务层，缺断言） |
| 5 | revoke 时企业包不存在 → Fail-Closed | 企业场景包分发 | P1 |
| 6 | revoke 时企业包已撤销 → Fail-Closed（status=4 不可重复撤销） | 企业场景包分发 | P1（已有用例，条件写错） |
| 7 | 幂等分发（status=1 时不更新） | 企业场景包分发 | P1 |
| 8 | 重新激活已移除成员（status=2→1） | 企业成员管理 | P1（已有用例，mock 不完整） |
| 9 | 查询成员包列表，remainQuota 实时计算 | 成员场景包授权查询 | P2 |
| 10 | 成员配额用尽时 failReason 正确 | 成员场景包授权查询 | P1 |
| 11 | 成员无授权记录 → Fail-Closed | 成员场景包授权查询 | P1 |
| 12 | 成员包级撤销时（企业包被 revoke） | 成员场景包授权查询 | P2 |
| 13 | addMember 企业不存在 → 抛异常 | 企业成员管理 | P1（已有服务层，mock 不完整） |
| 14 | addMember 批量写 memberPack 参数正确 | 企业成员管理 | P2 |
| 15 | addMember 企业已禁用 → 抛异常 | 企业成员管理 | P2 |
| 16 | 禁用企业不级联修改 memberPack | 企业组织管理 | P2 |
| 17 | revoke 幂等（status!=4 不重复撤销）| 企业场景包分发 | P1 |
| 18 | revoke 撤销时级联成员包（status=4） | 企业场景包分发 | P1（已有用例，预期值错） |
| 19 | 撤销已撤销包（status=3）→ Fail-Closed（按新条件 status!=4） | 企业场景包分发 | P1 |
| 20 | getEnterprisePage enterpriseName 筛选透传 | 企业组织管理 | P1（Bug 修复后缺覆盖） |
| 21 | Controller 层 enterprisePack list status 透传 | 企业场景包分发（Controller） | P1 |
| 22 | getMemberPackList 校验级联 packName/enterpriseName | 成员场景包授权查询 | P2 |

---

## 二、新增测试用例

> 格式：`§章节.小节 ID 描述 [P等级]`

---

### §E1 企业组织管理（`FbsEnterpriseBusinessServiceTest` 增补）

#### §E1.1 `createEnterprise` — 企业名称重复，抛出 `IllegalArgumentException` ✅ 已覆盖

> 现有用例 `createDuplicateName`，通过。

---

#### §E1.2 `disableEnterprise` — 已禁用企业重复禁用返回 false [P1]

**现有问题**：现有用例 `disableAlreadyDisabled` 用的 `buildEnterprise(2)`，但根据 `FbsEnterpriseBusinessServiceImpl` 的实际逻辑：

```java
// 当前实现
if (ent.getStatus() == 2) return false; // 已禁用
if (ent.getStatus() != 1) return false; // 非正常状态（实际包含 2/3/4）
```

**实际 Fail-Closed 条件是 `status != 1`**，即 status=2（已禁用）和 status=3（已删除）都应被拒绝。

**增补测试**：

```java
@Test
@DisplayName("§E1.2 disableEnterprise — status=3（已删除）重复禁用返回 false")
void disableAlreadyDeleted() {
    // GIVEN 企业 status=3
    when(enterpriseMapper.selectById(ENT_ID)).thenReturn(buildEnterprise(3));
    // WHEN 调用禁用
    boolean result = service.disableEnterprise(ENT_ID, CREATED_BY);
    // THEN 返回 false，不写库
    assertFalse(result);
    verify(enterpriseMapper, never()).updateEnterprise(any());
}
```

---

#### §E1.3 `disableEnterprise` — 不存在企业禁用返回 false [P1] ✅ 已覆盖

> 现有用例 `disableNotFound`，通过。

---

#### §E1.4 `disableEnterprise` — 禁用企业不级联修改 memberPack 记录 [P2]

**规范**：D-6，禁用企业仅修改 `fbs_enterprise.status`，不批量修改 `fbs_member_pack` 状态。

```java
@Test
@DisplayName("§E1.4 disableEnterprise — 仅修改企业状态，不操作 memberPack")
void disableDoesNotCascadeMemberPack() {
    when(enterpriseMapper.selectById(ENT_ID)).thenReturn(buildEnterprise(1));
    when(enterpriseMapper.updateEnterprise(any(FbsEnterprise.class))).thenReturn(1);

    service.disableEnterprise(ENT_ID, CREATED_BY);

    // 不调用 memberPackMapper
    verify(memberPackMapper, never()).updateStatusByEnterprisePackId(anyLong(), anyInt(), anyString());
    verify(memberPackMapper, never()).updateMemberPackStatusByMemberId(anyLong(), anyInt(), anyString());
}
```

> **注意**：`FbsEnterpriseBusinessServiceImpl` 当前并未注入 `FbsMemberPackMapper`，如需此断言需先确认是否应注入。若不注入则无需此测试用例（当前通过"不调用注入的 mapper"隐式保证）。

---

#### §E1.5 `getEnterprisePage` — enterpriseName 筛选透传到 Mapper [P1]

**背景**：2026-04-10 下午 Bug1 修复后，`getEnterprisePage` 现已支持 enterpriseName 筛选，但缺单元测试覆盖。

```java
@Test
@DisplayName("§E1.5 getEnterprisePage — enterpriseName 筛选透传正确")
void getEnterprisePageWithNameFilter() {
    FbsEnterprise ent = buildEnterprise(1);
    when(enterpriseMapper.selectEnterpriseList(any(FbsEnterprise.class)))
        .thenReturn(Arrays.asList(ent));

    EnterprisePageRequest request = new EnterprisePageRequest();
    request.setEnterpriseName("悟空科技"); // Bug1 修复前漏掉这行
    request.setStatus(1);

    List<FbsEnterprise> result = service.getEnterprisePage(request);

    assertEquals(1, result.size());
    ArgumentCaptor<FbsEnterprise> captor = ArgumentCaptor.forClass(FbsEnterprise.class);
    verify(enterpriseMapper).selectEnterpriseList(captor.capture());
    assertEquals("悟空科技", captor.getValue().getEnterpriseName());
    assertEquals(1, captor.getValue().getStatus());
}

@Test
@DisplayName("§E1.6 getEnterprisePage — 仅 enterpriseName（无 status）")
void getEnterprisePageNameOnly() {
    when(enterpriseMapper.selectEnterpriseList(any(FbsEnterprise.class)))
        .thenReturn(Collections.emptyList());

    EnterprisePageRequest request = new EnterprisePageRequest();
    request.setEnterpriseName("不存在的企业");

    List<FbsEnterprise> result = service.getEnterprisePage(request);

    assertTrue(result.isEmpty());
    ArgumentCaptor<FbsEnterprise> captor = ArgumentCaptor.forClass(FbsEnterprise.class);
    verify(enterpriseMapper).selectEnterpriseList(captor.capture());
    assertEquals("不存在的企业", captor.getValue().getEnterpriseName());
    assertNull(captor.getValue().getStatus()); // status 不过滤
}
```

---

### §E2 企业场景包分发（`FbsEnterprisePackBusinessServiceTest` 增补）

#### §E2.1 `grantPackToEnterprise` — 幂等分发（status=1 已授权不更新）[P1]

**现有问题**：现有用例 `grantIdempotent` 验证了"返回已有 ID"，但**未验证不调用 `updateEnterprisePack`**。

```java
@Test
@DisplayName("§E2.1 grantPackToEnterprise — status=1 时幂等，不调用 update")
void grantIdempotentDoesNotUpdate() {
    // GIVEN 已授权记录 status=1，packQuota=100，usedQuota=30
    FbsEnterprisePack existing = buildEnterprisePack(1);
    existing.setPackQuota(100);
    existing.setUsedQuota(30);
    when(enterpriseMapper.selectById(ENT_ID)).thenReturn(buildEnterprise());
    when(scenePackMapper.selectByPackCode("PACK_BOOK_WRITER")).thenReturn(buildScenePack());
    when(enterprisePackMapper.selectByEnterpriseAndPack(ENT_ID, PACK_ID)).thenReturn(existing);

    var request = new EnterprisePackGrantRequest();
    request.setEnterpriseId(ENT_ID);
    request.setPackCode("PACK_BOOK_WRITER");
    request.setPackQuota(200); // 故意传新配额

    Long id = service.grantPackToEnterprise(request, CREATED_BY);

    // 返回已有 ID
    assertEquals(EPACK_ID, id);
    // 关键：不调用 update（配额不变）
    verify(enterprisePackMapper, never()).updateEnterprisePack(any());
    verify(enterprisePackMapper, never()).insertEnterprisePack(any());
}
```

---

#### §E2.2 `grantPackToEnterprise` — 重新授权（status=3 已撤销→重新授权）✅ 已覆盖

> 现有用例 `grantReauthorize` 存在，但断言不完整，需补充：

```java
// 补充断言（非新建记录）
verify(enterprisePackMapper, never()).insertEnterprisePack(any()); // 不新建
verify(enterprisePackMapper).updateEnterprisePack(any());           // 更新已有
verify(memberPackMapper).insertMemberPackBatch(anyList());          // 重新批量授权
```

---

#### §E2.3 `grantPackToEnterprise` — 场景包不存在，抛出异常 [P1]

```java
@Test
@DisplayName("§E2.3 grantPackToEnterprise — 场景包不存在，抛出 IllegalArgumentException")
void grantPackNotFound() {
    when(enterpriseMapper.selectById(ENT_ID)).thenReturn(buildEnterprise());
    when(scenePackMapper.selectByPackCode("INVALID_PACK")).thenReturn(null);

    var request = new EnterprisePackGrantRequest();
    request.setEnterpriseId(ENT_ID);
    request.setPackCode("INVALID_PACK");
    request.setPackQuota(100);

    IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
        () -> service.grantPackToEnterprise(request, CREATED_BY));
    assertTrue(ex.getMessage().contains("场景包不存在") ||
                ex.getMessage().contains("not found"));
}
```

---

#### §E2.4 `grantPackToEnterprise` — 新增分发时批量写 memberPack 参数正确 [P2]

**验证** `insertMemberPackBatch` 写入的实体属性：

```java
@Test
@DisplayName("§E2.4 grantPackToEnterprise — 新增分发批量写 memberPack，字段正确")
void grantNewMemberPackFields() {
    FbsEnterpriseMember m1 = buildMember(); m1.setId(1L);
    FbsEnterpriseMember m2 = buildMember(); m2.setId(2L);
    when(enterpriseMapper.selectById(ENT_ID)).thenReturn(buildEnterprise());
    when(scenePackMapper.selectByPackCode("PACK_BOOK_WRITER")).thenReturn(buildScenePack());
    when(enterprisePackMapper.selectByEnterpriseAndPack(ENT_ID, PACK_ID)).thenReturn(null);
    when(memberMapper.selectActiveByEnterpriseId(ENT_ID)).thenReturn(Arrays.asList(m1, m2));
    doAnswer(inv -> {
        FbsEnterprisePack ep = inv.getArgument(0);
        ep.setId(EPACK_ID);
        return 1;
    }).when(enterprisePackMapper).insertEnterprisePack(any());

    var request = new EnterprisePackGrantRequest();
    request.setEnterpriseId(ENT_ID);
    request.setPackCode("PACK_BOOK_WRITER");
    request.setPackQuota(100);

    service.grantPackToEnterprise(request, CREATED_BY);

    ArgumentCaptor<List<FbsMemberPack>> captor = ArgumentCaptor.forClass(List.class);
    verify(memberPackMapper).insertMemberPackBatch(captor.capture());
    List<FbsMemberPack> batch = captor.getValue();
    assertEquals(2, batch.size());
    for (FbsMemberPack mp : batch) {
        assertEquals(EPACK_ID, mp.getEnterprisePackId()); // 企业包 ID
        assertEquals(PACK_ID, mp.getPackId());              // 场景包 ID
        assertEquals(1, mp.getStatus());                    // 授权状态
        assertNotNull(mp.getMemberId());
    }
}
```

---

#### §E2.5 `revokePackFromEnterprise` — 企业包不存在，返回 false [P1]

```java
@Test
@DisplayName("§E2.5 revokePackFromEnterprise — 企业包不存在，返回 false")
void revokeNotFound() {
    when(enterprisePackMapper.selectById(999L)).thenReturn(null);

    boolean result = service.revokePackFromEnterprise(999L, CREATED_BY);

    assertFalse(result);
    verify(enterprisePackMapper, never()).updateEnterprisePack(any());
}
```

---

#### §E2.6 `revokePackFromEnterprise` — 撤销成功，级联成员包 status=4 [P1]

**现有问题**：现有用例 `revokeSuccess` 断言 `captor.getValue().getStatus() == 3`，但根据规范应为 **status=4（已撤销）**，且需验证成员包级联参数为 4。

```java
@Test
@DisplayName("§E2.6 revokePackFromEnterprise — 撤销成功，fbs_enterprise_pack.status=4，成员包级联为 4")
void revokeSuccessStatus4() {
    FbsEnterprisePack ep = buildEnterprisePack(1);
    when(enterprisePackMapper.selectById(EPACK_ID)).thenReturn(ep);
    when(enterprisePackMapper.updateEnterprisePack(any(FbsEnterprisePack.class))).thenReturn(1);
    when(memberPackMapper.updateStatusByEnterprisePackId(anyLong(), anyInt(), anyString())).thenReturn(1);

    service.revokePackFromEnterprise(EPACK_ID, CREATED_BY);

    ArgumentCaptor<FbsEnterprisePack> captor = ArgumentCaptor.forClass(FbsEnterprisePack.class);
    verify(enterprisePackMapper).updateEnterprisePack(captor.capture());
    assertEquals(4, captor.getValue().getStatus()); // status=4 已撤销
    verify(memberPackMapper).updateStatusByEnterprisePackId(eq(EPACK_ID), eq(4), eq(CREATED_BY));
}
```

> **注意**：需先确认 `FbsEnterprisePackBusinessServiceImpl.revokePackFromEnterprise` 中 update 用的是 status=3 还是 status=4。如实现用 3，需同步修正实现。

---

#### §E2.7 `revokePackFromEnterprise` — status=3（已撤销）重复撤销失败 [P1]

**现有问题**：Review #3 将 Fail-Closed 条件从 `status == 3` 改为 `status != 4`，覆盖 status=3 和 status=4 两种情况。

```java
@Test
@DisplayName("§E2.7 revokePackFromEnterprise — status=3（已撤销）重复撤销失败")
void revokeAlreadyRevokedStatus3() {
    when(enterprisePackMapper.selectById(EPACK_ID)).thenReturn(buildEnterprisePack(3));

    boolean result = service.revokePackFromEnterprise(EPACK_ID, CREATED_BY);

    assertFalse(result);
    verify(enterprisePackMapper, never()).updateEnterprisePack(any());
    verify(memberPackMapper, never()).updateStatusByEnterprisePackId(anyLong(), anyInt(), anyString());
}
```

---

#### §E2.8 `getEnterprisePackPage` — status 筛选透传到 Mapper [P1]

**背景**：2026-04-10 Bug2 修复后新增此方法，需覆盖。

```java
@Test
@DisplayName("§E2.8 getEnterprisePackPage — status 筛选透传正确")
void getEnterprisePackPageWithStatus() {
    FbsEnterprisePack ep = buildEnterprisePack(1);
    when(enterprisePackMapper.selectEnterprisePackList(any(FbsEnterprisePack.class)))
        .thenReturn(Arrays.asList(ep));

    EnterprisePackPageRequest request = new EnterprisePackPageRequest();
    request.setEnterpriseId(ENT_ID);
    request.setStatus(1);

    List<FbsEnterprisePack> result = service.getEnterprisePackPage(request);

    assertEquals(1, result.size());
    ArgumentCaptor<FbsEnterprisePack> captor = ArgumentCaptor.forClass(FbsEnterprisePack.class);
    verify(enterprisePackMapper).selectEnterprisePackList(captor.capture());
    assertEquals(ENT_ID, captor.getValue().getEnterpriseId());
    assertEquals(1, captor.getValue().getStatus());
}

@Test
@DisplayName("§E2.9 getEnterprisePackPage — enterpriseId 为空返回空列表")
void getEnterprisePackPageNullEnterpriseId() {
    EnterprisePackPageRequest request = new EnterprisePackPageRequest();
    // enterpriseId = null

    List<FbsEnterprisePack> result = service.getEnterprisePackPage(request);

    assertTrue(result.isEmpty());
    verify(enterprisePackMapper, never()).selectEnterprisePackList(any());
}
```

---

### §E3 企业成员管理（`FbsEnterpriseMemberBusinessServiceTest` 增补）

#### §E3.1 `addMember` — 企业已禁用，抛出异常 [P2]

```java
@Test
@DisplayName("§E3.1 addMember — 企业已禁用（status=2），抛出 IllegalArgumentException")
void addMemberEnterpriseDisabled() {
    FbsEnterprise disabled = buildEnterprise();
    disabled.setStatus(2);
    when(enterpriseMapper.selectById(ENT_ID)).thenReturn(disabled);

    var request = new EnterpriseMemberAddRequest();
    request.setEnterpriseId(ENT_ID);
    request.setUserId(USER_ID);

    assertThrows(IllegalArgumentException.class,
        () -> service.addMember(request, CREATED_BY));
    verify(memberMapper, never()).insertMember(any());
}
```

---

#### §E3.2 `addMember` — 重新激活已移除成员，验证 update 参数正确 [P1]

**现有问题**：现有用例 `addMemberReactivate` 验证了 status=1 和返回 ID，但**未验证 update 参数**，且 mock 不完整（缺少 `memberPackMapper`）。

```java
@Test
@DisplayName("§E3.2 addMember — 重新激活已移除成员（status=2→1），调用 updateMember")
void addMemberReactivateUpdate() {
    FbsEnterpriseMember removed = buildMember(2);
    when(enterpriseMapper.selectById(ENT_ID)).thenReturn(buildEnterprise());
    when(sysUserMapper.selectUserById(USER_ID)).thenReturn(buildSysUser());
    when(memberMapper.selectByEnterpriseAndUser(ENT_ID, USER_ID)).thenReturn(removed);
    when(enterprisePackMapper.selectByEnterpriseId(ENT_ID)).thenReturn(Collections.emptyList());
    when(memberMapper.updateMember(any(FbsEnterpriseMember.class))).thenReturn(1);

    var request = new EnterpriseMemberAddRequest();
    request.setEnterpriseId(ENT_ID);
    request.setUserId(USER_ID);

    Long id = service.addMember(request, CREATED_BY);

    assertEquals(MEMBER_ID, id);
    ArgumentCaptor<FbsEnterpriseMember> captor = ArgumentCaptor.forClass(FbsEnterpriseMember.class);
    verify(memberMapper).updateMember(captor.capture());
    assertEquals(1, captor.getValue().getStatus());    // 重新激活
    assertEquals(MEMBER_ID, captor.getValue().getId()); // 更新同一记录
    // 不调用 insert（复用已有记录）
    verify(memberMapper, never()).insertMember(any());
}
```

---

#### §E3.3 `removeMember` — 成员不存在，返回 false [P1]

```java
@Test
@DisplayName("§E3.3 removeMember — 成员不存在，返回 false")
void removeMemberNotFound() {
    when(memberMapper.selectById(999L)).thenReturn(null);

    boolean result = service.removeMember(999L, CREATED_BY);

    assertFalse(result);
    verify(memberMapper, never()).updateMember(any());
}
```

---

#### §E3.4 `getMemberPackList` — remainQuota 实时计算正确 [P2]

**规范**：`remainQuota = fbs_enterprise_pack.packQuota - fbs_enterprise_pack.usedQuota`，实时计算。

> 需确认 `FbsEnterpriseMemberBusinessServiceImpl` 是否有此方法。如有，需补充测试。

```java
@Test
@DisplayName("§E3.4 getMemberPackList — remainQuota = packQuota - usedQuota，实时计算")
void getMemberPackListRemainQuota() {
    FbsEnterprisePack epack = new FbsEnterprisePack();
    epack.setId(EPACK_ID);
    epack.setPackQuota(100);
    epack.setUsedQuota(35);
    epack.setStatus(1);
    FbsMemberPack mp = buildMemberPack(1);
    mp.setEnterprisePackId(EPACK_ID);
    mp.setPackId(PACK_ID);
    when(memberMapper.selectById(MEMBER_ID)).thenReturn(buildMember(1));
    when(memberPackMapper.selectByMemberId(MEMBER_ID)).thenReturn(Arrays.asList(mp));
    when(enterprisePackMapper.selectById(EPACK_ID)).thenReturn(epack);
    when(scenePackMapper.selectById(PACK_ID)).thenReturn(buildScenePack());

    List<MemberPackResponse> result = service.getMemberPackList(MEMBER_ID);

    assertEquals(1, result.size());
    assertEquals(65, result.get(0).getRemainQuota()); // 100 - 35
}
```

---

#### §E3.5 `addMember` — 批量写 memberPack 覆盖企业所有已授权包 [P2]

**验证**：新成员加入时，继承企业**当前所有** status=1 的企业包。

```java
@Test
@DisplayName("§E3.5 addMember — 自动继承企业所有已授权包（批量写 memberPack）")
void addMemberInheritsAllActivePacks() {
    FbsEnterprisePack ep1 = buildEnterprisePack(); ep1.setId(1L);
    FbsEnterprisePack ep2 = buildEnterprisePack(); ep2.setId(2L);
    ep2.setStatus(3); // 已撤销，不应继承
    when(enterpriseMapper.selectById(ENT_ID)).thenReturn(buildEnterprise());
    when(sysUserMapper.selectUserById(USER_ID)).thenReturn(buildSysUser());
    when(memberMapper.selectByEnterpriseAndUser(ENT_ID, USER_ID)).thenReturn(null);
    when(enterprisePackMapper.selectByEnterpriseId(ENT_ID)).thenReturn(Arrays.asList(ep1, ep2));
    doAnswer(inv -> {
        FbsEnterpriseMember m = inv.getArgument(0);
        m.setId(MEMBER_ID);
        return 1;
    }).when(memberMapper).insertMember(any());

    var request = new EnterpriseMemberAddRequest();
    request.setEnterpriseId(ENT_ID);
    request.setUserId(USER_ID);

    service.addMember(request, CREATED_BY);

    ArgumentCaptor<List<FbsMemberPack>> captor = ArgumentCaptor.forClass(List.class);
    verify(memberPackMapper).insertMemberPackBatch(captor.capture());
    List<FbsMemberPack> batch = captor.getValue();
    // 只继承 status=1 的包（ep1），ep2（已撤销）不应在其中
    assertEquals(1, batch.size());
    assertEquals(1L, batch.get(0).getEnterprisePackId());
}
```

---

### §E4 消费链路集成验证（`SkillConsumeServiceTest` / `RightsCheckServiceTest` 增补）

> 以下用例验证 OpenSpec #3 与 OpenSpec #1 的**边界隔离**：hostType=ENTERPRISE 不 fallback 到个人路径。

#### §E4.1 `consume` — ENTERPRISE 配额充足，usedQuota 原子递增后 remainPoints 正确返回 [P1]

**现有问题**：现有用例 `enterpriseQuotaSufficient` 只验证了 `incrementUsedQuota` 被调用，**未验证更新后 remainPoints（= 100 - 6 = 94）正确返回**。

```java
@Test
@DisplayName("§E4.1 consume ENTERPRISE — usedQuota 原子递增后 remainPoints = packQuota - newUsedQuota")
void enterpriseConsumeRemainPoints() {
    // GIVEN usedQuota=5, packQuota=100
    FbsEnterprisePack ep = buildEnterprisePack(1, 100, 5);
    FbsEnterprisePack epAfter = buildEnterprisePack(1, 100, 6);
    when(enterpriseMemberMapper.selectActiveByUserId(USER_ID))
            .thenReturn(Arrays.asList(buildMember(1)));
    when(enterpriseMapper.selectById(ENT_ID)).thenReturn(buildEnterprise(1));
    when(enterprisePackMapper.selectByEnterpriseAndPack(ENT_ID, PACK_ID)).thenReturn(ep);
    when(memberPackMapper.selectActiveByMemberIdAndPackId(MEMBER_ID, PACK_ID))
            .thenReturn(buildMemberPack(1));
    doAnswer(inv -> {
        FbsEnterprisePack p = inv.getArgument(0);
        p.setUsedQuota(p.getUsedQuota() + 1);
        return 1;
    }).when(enterprisePackMapper).incrementUsedQuota(EPACK_ID);
    when(enterprisePackMapper.selectById(EPACK_ID)).thenReturn(epAfter);

    var result = service.consume(USER_ID, PACK_CODE, 1, HOST_TYPE_ENT, "task-001");

    assertTrue(result.isSuccess());
    // remainPoints = 100 - 6 = 94（不是 95！）
    assertEquals(94, result.getRemainPoints());
    assertEquals("ENTERPRISE", result.getHostType());
}
```

---

#### §E4.2 `consume` — ENTERPRISE 配额已用尽，failReason 包含"企业配额已用尽" [P1]

```java
@Test
@DisplayName("§E4.2 consume ENTERPRISE — 配额用尽 failReason='企业配额已用尽'，记录 status=2")
void enterpriseQuotaExhaustedFailReason() {
    FbsEnterprisePack ep = buildEnterprisePack(1, 100, 100);
    when(enterpriseMemberMapper.selectActiveByUserId(USER_ID))
            .thenReturn(Arrays.asList(buildMember(1)));
    when(enterpriseMapper.selectById(ENT_ID)).thenReturn(buildEnterprise(1));
    when(enterprisePackMapper.selectByEnterpriseAndPack(ENT_ID, PACK_ID)).thenReturn(ep);
    when(memberPackMapper.selectActiveByMemberIdAndPackId(MEMBER_ID, PACK_ID))
            .thenReturn(buildMemberPack(1));
    when(usageRecordMapper.insertSkillUsageRecord(any())).thenReturn(1);

    var result = service.consume(USER_ID, PACK_CODE, 1, HOST_TYPE_ENT, "task-001");

    assertFalse(result.isSuccess());
    assertTrue(result.getFailReason().contains("企业配额已用尽"));
    verify(enterprisePackMapper, never()).incrementUsedQuota(anyLong());
    verify(usageRecordMapper).insertSkillUsageRecord(
        argThat(record -> record.getStatus() == 2 // 失败
                        && "ENTERPRISE".equals(record.getHostType())));
}
```

---

#### §E4.3 `consume` — ENTERPRISE 企业包 status=3（已撤销）时 Fail-Closed [P2]

**场景**：企业分发记录被撤销后，成员即使有 memberPack 授权记录也不能使用。

```java
@Test
@DisplayName("§E4.3 consume ENTERPRISE — 企业包 status=3（已撤销），Fail-Closed")
void enterprisePackRevoked() {
    FbsEnterprisePack ep = buildEnterprisePack(3, 100, 50); // status=3 已撤销
    when(enterpriseMemberMapper.selectActiveByUserId(USER_ID))
            .thenReturn(Arrays.asList(buildMember(1)));
    when(enterpriseMapper.selectById(ENT_ID)).thenReturn(buildEnterprise(1));
    when(enterprisePackMapper.selectByEnterpriseAndPack(ENT_ID, PACK_ID)).thenReturn(ep);

    var result = service.consume(USER_ID, PACK_CODE, 1, HOST_TYPE_ENT, "task-001");

    assertFalse(result.isSuccess());
    verify(enterprisePackMapper, never()).incrementUsedQuota(anyLong());
}
```

---

#### §E4.4 `consume` — 同一用户 hostType=WORKBUDDY，走个人授权路径不受企业配额限制 [P1]

**规范**：D-5，两套路径互不 fallback。

```java
@Test
@DisplayName("§E4.4 consume WORKBUDDY — 用户是企业成员但走个人路径，不受企业配额限制")
void workbuddyPathIgnoresEnterpriseQuota() {
    // GIVEN 用户是企业成员
    when(enterpriseMemberMapper.selectActiveByUserId(USER_ID))
            .thenReturn(Arrays.asList(buildMember(1)));
    // 但调用方用 WORKBUDDY
    FbsUserPack userPack = new FbsUserPack();
    userPack.setId(1L);
    userPack.setUserId(USER_ID);
    userPack.setPackId(PACK_ID);
    userPack.setAvailable(10);
    userPack.setStatus(1);
    when(userPackMapper.selectByUserAndPack(USER_ID, PACK_ID)).thenReturn(userPack);
    when(pointsMapper.selectByUserId(USER_ID)).thenReturn(buildPoints(100L));
    when(pointsMapper.deductPoints(eq(USER_ID), eq(10L))).thenReturn(true);
    when(packMapper.selectById(PACK_ID)).thenReturn(buildScenePack());
    doAnswer(inv -> {
        FbsUserPack p = inv.getArgument(0);
        p.setAvailable(p.getAvailable() - 10);
        return 1;
    }).when(userPackMapper).decrementAvailable(anyLong(), anyInt());

    var result = service.consume(USER_ID, PACK_CODE, 10, "WORKBUDDY", "task-001");

    assertTrue(result.isSuccess());
    // 走个人路径，扣积分，不动企业配额
    verify(enterprisePackMapper, never()).incrementUsedQuota(anyLong());
    verify(pointsMapper).deductPoints(USER_ID, 10L);
}
```

---

#### §E4.5 `consume` — ENTERPRISE 成员无 memberPack 授权记录，Fail-Closed [P1]

**场景**：企业包已分发，但新成员加入前该包已分发，成员授权记录未包含此包。

```java
@Test
@DisplayName("§E4.5 consume ENTERPRISE — 成员无 memberPack 授权，Fail-Closed")
void enterpriseMemberNoPackAuth() {
    when(enterpriseMemberMapper.selectActiveByUserId(USER_ID))
            .thenReturn(Arrays.asList(buildMember(1)));
    when(enterpriseMapper.selectById(ENT_ID)).thenReturn(buildEnterprise(1));
    when(enterprisePackMapper.selectByEnterpriseAndPack(ENT_ID, PACK_ID))
            .thenReturn(buildEnterprisePack(1, 100, 10));
    when(memberPackMapper.selectActiveByMemberIdAndPackId(MEMBER_ID, PACK_ID))
            .thenReturn(null); // 成员无此包授权

    var result = service.consume(USER_ID, PACK_CODE, 1, HOST_TYPE_ENT, "task-001");

    assertFalse(result.isSuccess());
    verify(enterprisePackMapper, never()).incrementUsedQuota(anyLong());
}
```

---

#### §E4.6 `consume` — ENTERPRISE 成员 memberPack status=3（已撤销），Fail-Closed [P2]

```java
@Test
@DisplayName("§E4.6 consume ENTERPRISE — memberPack status=3（已撤销），Fail-Closed")
void enterpriseMemberPackRevoked() {
    when(enterpriseMemberMapper.selectActiveByUserId(USER_ID))
            .thenReturn(Arrays.asList(buildMember(1)));
    when(enterpriseMapper.selectById(ENT_ID)).thenReturn(buildEnterprise(1));
    when(enterprisePackMapper.selectByEnterpriseAndPack(ENT_ID, PACK_ID))
            .thenReturn(buildEnterprisePack(1, 100, 10));
    when(memberPackMapper.selectActiveByMemberIdAndPackId(MEMBER_ID, PACK_ID))
            .thenReturn(buildMemberPack(3)); // 已撤销

    var result = service.consume(USER_ID, PACK_CODE, 1, HOST_TYPE_ENT, "task-001");

    assertFalse(result.isSuccess());
}
```

---

#### §E4.7 `consume` — ENTERPRISE usedQuota 递增，sys_user.points 不变 [P2]

```java
@Test
@DisplayName("§E4.7 consume ENTERPRISE — usedQuota 递增，sys_user.points 不变，不走 wx_points_rule")
void enterpriseConsumeNoPointsDeduction() {
    FbsEnterprisePack ep = buildEnterprisePack(1, 100, 5);
    FbsEnterprisePack epAfter = buildEnterprisePack(1, 100, 6);
    when(enterpriseMemberMapper.selectActiveByUserId(USER_ID))
            .thenReturn(Arrays.asList(buildMember(1)));
    when(enterpriseMapper.selectById(ENT_ID)).thenReturn(buildEnterprise(1));
    when(enterprisePackMapper.selectByEnterpriseAndPack(ENT_ID, PACK_ID)).thenReturn(ep);
    when(memberPackMapper.selectActiveByMemberIdAndPackId(MEMBER_ID, PACK_ID))
            .thenReturn(buildMemberPack(1));
    doAnswer(inv -> {
        FbsEnterprisePack p = inv.getArgument(0);
        p.setUsedQuota(p.getUsedQuota() + 1);
        return 1;
    }).when(enterprisePackMapper).incrementUsedQuota(EPACK_ID);
    when(enterprisePackMapper.selectById(EPACK_ID)).thenReturn(epAfter);

    service.consume(USER_ID, PACK_CODE, 1, HOST_TYPE_ENT, "task-001");

    // 核心断言：不动个人积分
    verify(pointsMapper, never()).deductPoints(anyLong(), anyLong());
    verify(userPackMapper, never()).decrementAvailable(anyLong(), anyInt());
    // 写入的使用记录 host_type = ENTERPRISE
    verify(usageRecordMapper).insertSkillUsageRecord(
        argThat(record -> "ENTERPRISE".equals(record.getHostType())));
}
```

---

#### §E4.8 `consume` — ENTERPRISE 成员状态 status=2（已移除），Fail-Closed [P2]

**场景**：成员被移除后，memberPack 记录保留（D-6），但消费时通过成员状态 fail-closed。

```java
@Test
@DisplayName("§E4.8 consume ENTERPRISE — 成员 status=2（已移除），Fail-Closed")
void enterpriseMemberRemoved() {
    FbsEnterpriseMember removed = buildMember(2); // status=2 已移除
    when(enterpriseMemberMapper.selectActiveByUserId(USER_ID))
            .thenReturn(Arrays.asList(removed));

    var result = service.consume(USER_ID, PACK_CODE, 1, HOST_TYPE_ENT, "task-001");

    assertFalse(result.isSuccess());
    verify(enterprisePackMapper, never()).incrementUsedQuota(anyLong());
}
```

---

## 三、测试执行计划

### 3.1 执行顺序

```
阶段 1: 编译验证（mvn compile）
    → 通过后再进入阶段 2

阶段 2: 单元测试（mvn test -pl WxFbsir-business -am）
    → 逐测试类执行，记录失败用例

阶段 3: 失败修复（identify → fix → re-run）
    → 循环直到全部通过

阶段 4: 覆盖率确认（mvn test -pl WxFbsir-business -am - jacoco）
    → 目标：企业侧核心路径覆盖率达到 85%+
```

### 3.2 预期结果

| 阶段 | 操作 | 预期 |
|------|------|------|
| 编译 | `mvn compile -pl WxFbsir-business -am -q` | BUILD SUCCESS |
| 单元测试 | `mvn test -pl WxFbsir-business -am` | 全部通过 |
| 覆盖率 | JaCoCo 报告 | 企业侧 >85% |

### 3.3 测试类-用例数预估

| 测试类 | 现有 | 新增 | 合计 |
|--------|------|------|------|
| `FbsEnterpriseBusinessServiceTest` | 8 | 3 (§E1) | **11** |
| `FbsEnterprisePackBusinessServiceTest` | 9 | 6 (§E2) | **15** |
| `FbsEnterpriseMemberBusinessServiceTest` | 5 | 5 (§E3) | **10** |
| `SkillConsumeServiceTest` | 7 ENT | 5 (§E4.odd) | **12** |
| `RightsCheckServiceTest` | 5 ENT | 3 (§E4.even) | **8** |
| **合计** | **34** | **22** | **56** |

---

## 四、风险与依赖

### 4.1 已知风险

1. **§E2.6 状态值争议**：`revokePackFromEnterprise` 中 update 用 status=3 还是 status=4，需先读实现确认
2. **§E3.4 方法存在性**：`getMemberPackList` 是否在 `FbsEnterpriseMemberBusinessServiceImpl` 中存在，需确认
3. **Mock 注入完整性**：`FbsEnterpriseBusinessServiceImpl` 是否注入了 `FbsMemberPackMapper`（用于 §E1.4 的断言）

### 4.2 执行前检查清单

- [ ] 确认 `FbsEnterprisePackBusinessServiceImpl.revokePackFromEnterprise` 中 update 的 status 值
- [ ] 确认 `FbsEnterpriseMemberBusinessServiceImpl` 是否有 `getMemberPackList` 方法
- [ ] 确认 `FbsEnterpriseBusinessServiceImpl` 是否注入 `FbsMemberPackMapper`
- [ ] 运行 `mvn test -pl WxFbsir-business -am` 确认 baseline 全绿

---

## 五、附录：缺口快速核对表

```
[ ] §E1.2  disable status=3（非1）Fail-Closed
[ ] §E1.4  禁用不级联 memberPack（D-6）
[ ] §E1.5  enterpriseName 筛选透传（Bug1 修复后）
[ ] §E1.6  enterpriseName 单独筛选
[ ] §E2.1  幂等分发不 update（已有用例，补断言）
[ ] §E2.2  重新授权断言补全
[ ] §E2.3  场景包不存在抛异常
[ ] §E2.4  批量写 memberPack 字段正确
[ ] §E2.5  revoke 包不存在返回 false
[ ] §E2.6  revoke status=4 + 级联成员包=4（确认实现值）
[ ] §E2.7  revoke status=3 重复撤销失败（Review#3 新条件）
[ ] §E2.8  status 筛选透传（Bug2 修复后）
[ ] §E2.9  enterpriseId=null 返回空
[ ] §E3.1  addMember 企业已禁用抛异常
[ ] §E3.2  重新激活 update 参数正确（补 mock）
[ ] §E3.3  removeMember 不存在返回 false
[ ] §E3.4  remainQuota 实时计算（确认方法存在）
[ ] §E3.5  addMember 只继承 status=1 的企业包
[ ] §E4.1  remainPoints = packQuota - newUsedQuota
[ ] §E4.2  failReason='企业配额已用尽' + status=2
[ ] §E4.3  企业包 status=3 Fail-Closed
[ ] §E4.4  WORKBUDDY 路径不受企业配额限制
[ ] §E4.5  成员无 memberPack 授权 Fail-Closed
[ ] §E4.6  memberPack status=3 Fail-Closed
[ ] §E4.7  sys_user.points 不变 + host_type=ENTERPRISE
[ ] §E4.8  成员 status=2（已移除）Fail-Closed
```
