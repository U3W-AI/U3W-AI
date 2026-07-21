import assert from 'node:assert/strict'
import fs from 'node:fs'

import {
  activationMeta,
  buildEntitlementGrantPayload,
  buildEntitlementRevokeConfirmation,
  buildEntitlementRevokePayload,
  createLatestRequestGuard,
  entitlementReceiptActionMeta,
  expectedSavedEntitlementVersion,
  isCasConflict,
  operationStatusMeta,
  parseEnterprisePage,
  parseEntitlement,
  parseEntitlementList,
  parseEntitlementReceiptEnvelope,
  parseMemberPage,
  parseOperationEnvelope,
  parseProductPlanCatalog,
  toBoardDateTimeInput,
  verifyEntitlementRevokeReadback
} from '../src/views/business/independentBoard/admin/model.js'

const tenantId = 7
const productPlans = [
  {
    productCode: 'FBSIR_INDEPENDENT_BOARD',
    planCode: 'BOARD_FREE',
    planName: '独董会免费版',
    vip: false,
    connectorRequired: false,
    dailyMeetingLimit: 1,
    agendaLimit: 5,
    seatLimit: 3,
    secretaryEnabled: false,
    status: 'ACTIVE',
    version: 3,
    updatedAt: '2026-07-20T09:00:00+08:00'
  },
  {
    productCode: 'FBSIR_INDEPENDENT_BOARD',
    planCode: 'BOARD_VIP',
    planName: '独董会 VIP 版',
    vip: true,
    connectorRequired: true,
    dailyMeetingLimit: 5,
    agendaLimit: 30,
    seatLimit: null,
    secretaryEnabled: true,
    status: 'ACTIVE',
    version: 7,
    updatedAt: '2026-07-20T10:00:00+08:00'
  }
]
const parsedPlans = parseProductPlanCatalog(productPlans)
assert.equal(parsedPlans.length, 2)
assert.equal(parsedPlans[0].planCode, 'BOARD_FREE')
assert.equal(parsedPlans[1].planName, '独董会 VIP 版')
assert.equal(Object.isFrozen(parsedPlans), true)
assert.throws(() => parseProductPlanCatalog([productPlans[0]]), /必须包含且仅包含/)
assert.throws(() => parseProductPlanCatalog([
  productPlans[0], { ...productPlans[1], planCode: 'BOARD_FREE' }
]), /重复套餐|能力与套餐代码不一致/)
assert.throws(() => parseProductPlanCatalog([
  productPlans[0], { ...productPlans[1], connectorRequired: false }
]), /能力与套餐代码不一致/)
assert.throws(() => parseProductPlanCatalog([
  { ...productPlans[0], dailyMeetingLimit: 999 }, productPlans[1]
]), /能力与套餐代码不一致/)
assert.throws(() => parseProductPlanCatalog([
  productPlans[0], { ...productPlans[1], agendaLimit: 1 }
]), /能力与套餐代码不一致/)
assert.throws(() => parseProductPlanCatalog([
  { ...productPlans[0], planName: `免费版${String.fromCharCode(0x85)}` }, productPlans[1]
]), /非法值/)
assert.throws(() => parseProductPlanCatalog([
  { ...productPlans[0], internalId: 1 }, productPlans[1]
]), /安全合同/)

const enterpriseResponse = {
  rows: [
    { id: tenantId, enterpriseName: '福帮手测试企业', status: 1, contactPhone: 'hidden' },
    { id: 8, enterpriseName: '停用企业', status: 2 }
  ],
  total: 2
}
const enterprisePage = parseEnterprisePage(enterpriseResponse)
assert.equal(enterprisePage.records.length, 2)
assert.deepEqual(Object.keys(enterprisePage.records[0]), ['id', 'enterpriseName', 'status'])
assert.equal(enterprisePage.truncated, false)
assert.equal(parseEnterprisePage({ ...enterpriseResponse, total: 3 }).truncated, true)
assert.throws(() => parseEnterprisePage({
  rows: [enterpriseResponse.rows[0], enterpriseResponse.rows[0]],
  total: 2
}), /重复企业/)

const member = Object.freeze({
  id: 42,
  enterpriseId: tenantId,
  userId: 99,
  userName: '测试成员',
  role: 'MEMBER',
  status: 1
})
const memberResponse = {
  rows: [{ ...member, requestDigest: 'must-not-propagate' }],
  total: 1
}
const memberPage = parseMemberPage(memberResponse, tenantId)
assert.deepEqual(memberPage.records[0], member)
assert.throws(() => parseMemberPage({
  rows: [{ ...member, enterpriseId: 8 }],
  total: 1
}, tenantId), /安全绑定/)
assert.throws(() => parseMemberPage({
  rows: [{ ...member }, { ...member, id: 43 }],
  total: 2
}, tenantId), /重复身份/)

const pendingEntitlement = Object.freeze({
  tenantId,
  memberId: member.id,
  userId: member.userId,
  planCode: 'BOARD_VIP',
  entitlementStatus: 'ACTIVE',
  activationState: 'PENDING_CONNECTOR',
  validFrom: '2026-07-20T08:00:00+08:00',
  validUntil: '2027-07-20T08:00:00+08:00',
  version: 3,
  updatedAt: '2026-07-20T09:00:00+08:00'
})
assert.deepEqual(parseEntitlement(pendingEntitlement, tenantId), pendingEntitlement)
assert.equal(activationMeta('PENDING_CONNECTOR').label, '待连接器认证')
assert.equal(activationMeta('ACTIVE').label, 'VIP已生效')
const revokedEntitlement = Object.freeze({
  ...pendingEntitlement,
  entitlementStatus: 'REVOKED',
  activationState: 'REVOKED',
  version: 4,
  updatedAt: '2026-07-20T10:00:00+08:00'
})
assert.deepEqual(parseEntitlement(revokedEntitlement, tenantId), revokedEntitlement)
assert.throws(() => parseEntitlement({
  ...revokedEntitlement,
  validUntil: null
}, tenantId), /必须包含撤销生效时间/)
assert.deepEqual(parseEntitlement({
  ...revokedEntitlement,
  validFrom: '2026-07-20T10:00:00+08:00',
  validUntil: '2026-07-20T10:00:00+08:00'
}, tenantId).validUntil, '2026-07-20T10:00:00+08:00')
assert.throws(() => parseEntitlement({
  ...revokedEntitlement,
  validFrom: '2026-07-20T10:00:00+08:00',
  validUntil: '2026-07-20T09:59:59+08:00'
}, tenantId), /有效期前后/)
assert.equal(activationMeta('REVOKED').label, '已撤销')
assert.throws(() => parseEntitlement({
  ...revokedEntitlement,
  activationState: 'EXPIRED'
}, tenantId), /撤销状态/)
assert.throws(() => parseEntitlement({
  ...pendingEntitlement,
  activationState: 'REVOKED'
}, tenantId), /撤销状态/)
assert.throws(() => parseEntitlement({
  ...pendingEntitlement,
  connectorVerifiedAt: '2026-07-20T09:00:00+08:00'
}, tenantId), /安全合同/)
assert.throws(() => parseEntitlement({
  ...pendingEntitlement,
  activationState: 'ACTIVE',
  planCode: 'BOARD_FREE'
}, tenantId), /VIP生效状态/)
assert.throws(() => parseEntitlement({
  ...pendingEntitlement,
  activationState: 'FREE'
}, tenantId), /VIP授予记录/)
assert.throws(() => parseEntitlement({
  ...pendingEntitlement,
  tenantId: 8
}, tenantId), /跨企业/)
assert.throws(() => parseEntitlement({
  ...pendingEntitlement,
  version: 0
}, tenantId), /非法值/,
'persisted entitlement version 0 must be rejected; it is only valid in a create request')
assert.throws(() => parseEntitlement({
  ...pendingEntitlement,
  validUntil: '2026-07-19T08:00:00+08:00'
}, tenantId), /有效期前后/)
assert.throws(() => parseEntitlementList([
  pendingEntitlement,
  { ...pendingEntitlement, userId: 100 }
], tenantId), /重复独董会权益/)

const newGrant = buildEntitlementGrantPayload({
  tenantId,
  member,
  planCode: 'BOARD_VIP',
  validUntil: '2027-07-20T08:00:00',
  now: Date.parse('2026-07-20T08:00:00+08:00')
})
assert.deepEqual(newGrant, {
  tenantId,
  memberId: member.id,
  userId: member.userId,
  planCode: 'BOARD_VIP',
  validUntil: '2027-07-20T08:00:00',
  expectedVersion: 0
})
const adjustedGrant = buildEntitlementGrantPayload({
  tenantId,
  member,
  planCode: 'BOARD_FREE',
  validUntil: null,
  existingEntitlement: pendingEntitlement
})
assert.equal(adjustedGrant.expectedVersion, pendingEntitlement.version)
assert.equal(adjustedGrant.userId, member.userId)
assert.equal(expectedSavedEntitlementVersion(), 1, 'first grant must read back at version 1')
assert.equal(expectedSavedEntitlementVersion(3), 4, 'CAS update must increment the stored version')
assert.throws(() => expectedSavedEntitlementVersion(-1), /并发版本无效/)
assert.notEqual(expectedSavedEntitlementVersion(), newGrant.expectedVersion,
  'create request version 0 and first stored version 1 are distinct contract values')
assert.throws(() => buildEntitlementGrantPayload({
  tenantId,
  member: { ...member, status: 2 },
  planCode: 'BOARD_FREE'
}), /有效成员/)
assert.throws(() => buildEntitlementGrantPayload({
  tenantId,
  member,
  planCode: 'BOARD_VIP',
  existingEntitlement: { ...pendingEntitlement, userId: 100 }
}), /成员身份/)
assert.throws(() => buildEntitlementGrantPayload({
  tenantId,
  member,
  planCode: 'BOARD_VIP',
  existingEntitlement: revokedEntitlement
}), /不能通过调整操作隐式恢复/)
const revokePayload = buildEntitlementRevokePayload({
  tenantId,
  entitlement: pendingEntitlement
})
assert.deepEqual(revokePayload, {
  tenantId,
  memberId: member.id,
  userId: member.userId,
  expectedVersion: pendingEntitlement.version
})
assert.deepEqual(Object.keys(revokePayload), [
  'tenantId', 'memberId', 'userId', 'expectedVersion'
])
assert.throws(() => buildEntitlementRevokePayload({
  tenantId,
  entitlement: revokedEntitlement
}), /只能撤销/)
const revokeConfirmation = buildEntitlementRevokeConfirmation({
  enterprise: enterprisePage.records[0],
  member,
  entitlement: pendingEntitlement,
  planCatalog: productPlans
})
assert.deepEqual(Object.keys(revokeConfirmation), ['title', 'message', 'confirmButtonText'])
assert.equal(revokeConfirmation.title, '确认撤销权益')
for (const expectedContent of [
  '企业：福帮手测试企业（企业 #7）',
  '成员：测试成员 · 成员 #42',
  '用户 ID：99',
  '授予计划：独董会 VIP 版',
  '当前版本：3'
]) {
  assert.match(revokeConfirmation.message, new RegExp(expectedContent.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')))
}
const reboundConfirmation = buildEntitlementRevokeConfirmation({
  enterprise: { ...enterprisePage.records[0], enterpriseName: '另一家企业' },
  member: { ...member, id: 43, userId: 100, userName: '另一成员' },
  entitlement: {
    ...pendingEntitlement,
    memberId: 43,
    userId: 100,
    planCode: 'BOARD_FREE',
    activationState: 'FREE',
    version: 8
  },
  planCatalog: productPlans
})
for (const expectedContent of [
  '企业：另一家企业（企业 #7）',
  '成员：另一成员 · 成员 #43',
  '用户 ID：100',
  '授予计划：独董会免费版',
  '当前版本：8'
]) {
  assert.match(reboundConfirmation.message, new RegExp(expectedContent.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')))
}
assert.notEqual(reboundConfirmation.message, revokeConfirmation.message,
  'confirmation content must be bound to the exact row being revoked')
const fallbackRevokeConfirmation = buildEntitlementRevokeConfirmation({
  enterprise: enterprisePage.records[0],
  member,
  entitlement: pendingEntitlement,
  planCatalog: []
})
assert.match(fallbackRevokeConfirmation.message, /授予计划：BOARD_VIP/)
assert.match(fallbackRevokeConfirmation.message, /按BOARD_FREE运行/)
assert.throws(() => buildEntitlementRevokeConfirmation({
  enterprise: enterprisePage.records[0],
  member: { ...member, userId: 100 },
  entitlement: pendingEntitlement,
  planCatalog: productPlans
}), /成员与权益记录不一致/)
assert.throws(() => buildEntitlementRevokeConfirmation({
  enterprise: { ...enterprisePage.records[0], internalCode: 'unsafe' },
  member,
  entitlement: pendingEntitlement,
  planCatalog: productPlans
}), /安全合同/)

assert.deepEqual(verifyEntitlementRevokeReadback({
  tenantId,
  original: pendingEntitlement,
  saved: revokedEntitlement
}), revokedEntitlement)
for (const unsafeReadback of [
  { ...revokedEntitlement, planCode: 'BOARD_FREE' },
  { ...revokedEntitlement, validFrom: '2026-07-20T08:00:01+08:00' },
  { ...revokedEntitlement, version: 5 },
  { ...revokedEntitlement, memberId: 43 },
  { ...revokedEntitlement, validUntil: null },
  { ...revokedEntitlement, validUntil: '2026-07-20T07:59:59+08:00' },
  { ...revokedEntitlement, internalId: 1 }
]) {
  assert.throws(() => verifyEntitlementRevokeReadback({
    tenantId,
    original: pendingEntitlement,
    saved: unsafeReadback
  }))
}
assert.equal(toBoardDateTimeInput('2026-07-20T08:01:02+08:00'), '2026-07-20T08:01:02')
assert.equal(toBoardDateTimeInput('2026-07-20T08:01:02'), '2026-07-20T08:01:02')
assert.equal(isCasConflict({ response: { status: 409 } }), true)
assert.equal(isCasConflict(new Error('OPTIMISTIC_LOCK_CONFLICT')), true)
assert.equal(isCasConflict(new Error('network unavailable')), false)

const guard = createLatestRequestGuard()
const firstRequest = guard.next()
const secondRequest = guard.next()
assert.equal(guard.isCurrent(firstRequest), false, 'late tenant response must be discarded')
assert.equal(guard.isCurrent(secondRequest), true)

const reservedOperation = Object.freeze({
  operationId: 'board-20260720-audit-0001',
  tenantId,
  memberId: member.id,
  userId: member.userId,
  status: 'RESERVED',
  effectivePlanCode: 'BOARD_VIP',
  bucketDate: '2026-07-20',
  agendaCount: 5,
  seatCount: 3,
  remainingCount: 2,
  createdAt: '2026-07-20T08:00:00+08:00',
  updatedAt: '2026-07-20T08:00:01+08:00',
  completedAt: '2026-07-20T08:00:01+08:00'
})
const envelope = parseOperationEnvelope({
  records: [reservedOperation],
  limit: 500,
  truncated: false
}, tenantId)
assert.deepEqual(envelope.records[0], reservedOperation)
assert.equal(operationStatusMeta('RESERVED').label, '额度已预留')
assert.notEqual(operationStatusMeta('RESERVED').label, '会议已完成')
assert.throws(() => parseOperationEnvelope({
  records: [{ ...reservedOperation, requestDigest: 'unsafe' }],
  limit: 500,
  truncated: false
}, tenantId), /安全合同/)

const entitlementReceipt = Object.freeze({
  receiptId: 'receipt-20260720-0001',
  tenantId,
  actorUserId: 1,
  targetMemberId: member.id,
  action: 'ENTITLEMENT_REVOKED',
  evidenceLevel: 'ACTION_COMPLETED',
  createdAt: '2026-07-20T10:00:00+08:00'
})
const receiptEnvelope = parseEntitlementReceiptEnvelope({
  records: [entitlementReceipt],
  limit: 500,
  truncated: false
}, tenantId)
assert.deepEqual(receiptEnvelope.records[0], entitlementReceipt)
assert.equal(entitlementReceiptActionMeta('ENTITLEMENT_REVOKED').label, '权益已撤销')
assert.throws(() => parseEntitlementReceiptEnvelope({
  records: [{ ...entitlementReceipt, payloadDigest: 'unsafe' }],
  limit: 500,
  truncated: false
}, tenantId), /安全合同/)
assert.throws(() => parseEntitlementReceiptEnvelope({
  records: [{ ...entitlementReceipt, id: 1 }],
  limit: 500,
  truncated: false
}, tenantId), /安全合同/)
assert.throws(() => parseEntitlementReceiptEnvelope({
  records: [{ ...entitlementReceipt, tenantId: 8 }],
  limit: 500,
  truncated: false
}, tenantId), /跨企业/)
assert.throws(() => parseEntitlementReceiptEnvelope({
  records: [entitlementReceipt],
  limit: 501,
  truncated: true
}, tenantId), /边界/)
assert.throws(() => parseEntitlementReceiptEnvelope({
  records: [entitlementReceipt, entitlementReceipt],
  limit: 500,
  truncated: false
}, tenantId), /重复回执编号/)
assert.throws(() => parseEntitlementReceiptEnvelope({
  records: [entitlementReceipt],
  limit: 500,
  truncated: false,
  total: 1
}, tenantId), /安全合同/)
assert.throws(() => parseOperationEnvelope({
  records: [{ ...reservedOperation, id: 1 }],
  limit: 500,
  truncated: false
}, tenantId), /安全合同/)
assert.throws(() => parseOperationEnvelope({
  records: [{ ...reservedOperation, tenantId: 8 }],
  limit: 500,
  truncated: false
}, tenantId), /跨企业/)
assert.throws(() => parseOperationEnvelope({
  records: [{ ...reservedOperation, updatedAt: null }],
  limit: 500,
  truncated: false
}, tenantId), /更新时间/)
assert.throws(() => parseOperationEnvelope({
  records: [reservedOperation],
  limit: 501,
  truncated: true
}, tenantId), /边界/)
assert.throws(() => parseOperationEnvelope({
  records: [reservedOperation],
  limit: 499,
  truncated: false
}, tenantId), /边界/)
assert.throws(() => parseOperationEnvelope({
  records: [reservedOperation, reservedOperation],
  limit: 500,
  truncated: false
}, tenantId), /重复操作编号/)
assert.throws(() => parseOperationEnvelope({
  records: [reservedOperation],
  limit: 500,
  truncated: false,
  total: 501
}, tenantId), /安全合同/)

const entitlementPageSource = fs.readFileSync(
  new URL('../src/views/business/independentBoard/admin/entitlement/index.vue', import.meta.url),
  'utf8'
)
const auditPageSource = fs.readFileSync(
  new URL('../src/views/business/independentBoard/admin/meetingAudit/index.vue', import.meta.url),
  'utf8'
)
const receiptPageSource = fs.readFileSync(
  new URL('../src/views/business/independentBoard/admin/entitlementReceipt/index.vue', import.meta.url),
  'utf8'
)
const modelSource = fs.readFileSync(
  new URL('../src/views/business/independentBoard/admin/model.js', import.meta.url),
  'utf8'
)
const apiSource = fs.readFileSync(
  new URL('../src/api/business/independentBoard/admin.js', import.meta.url),
  'utf8'
)

for (const source of [entitlementPageSource, auditPageSource, receiptPageSource]) {
  assert.match(source, /listEnterprise\(\{ pageNum: 1, pageSize: 1000 \}\)/)
  assert.match(source, /createLatestRequestGuard\(\)/)
  assert.match(source, /selectedTenantId\.value !== requestedTenantId/)
  assert.match(source, /@media \(max-width: 768px\)/)
  assert.doesNotMatch(source, /<el-input\b[^>]*(?:tenantId|memberId|userId)/)
}
for (const source of [entitlementPageSource, auditPageSource]) {
  assert.match(source, /listEnterpriseMember\(\{ enterpriseId: requestedTenantId, pageNum: 1, pageSize: 1000 \}\)/)
}

assert.match(entitlementPageSource, /v-hasPermi="\['board:entitlement:grant'\]"/)
assert.match(entitlementPageSource, /v-hasPermi="\['board:entitlement:revoke'\]"/)
assert.match(entitlementPageSource, /ElMessageBox\.confirm\(/)
assert.match(entitlementPageSource, /本次不会自动重试撤销/)
assert.match(entitlementPageSource, /revokingMemberId/)
assert.match(entitlementPageSource, /revokeGuardMemberId/)
assert.match(entitlementPageSource, /if \(isMutating\.value \|\| !canRevoke\(entitlement\)\) return/)
assert.match(entitlementPageSource, /buildEntitlementRevokeConfirmation\(/)
assert.match(entitlementPageSource, /verifyEntitlementRevokeReadback\(/)
assert.match(entitlementPageSource,
  /ElMessageBox\.confirm\(\s*confirmation\.message,\s*confirmation\.title,/)
assert.match(entitlementPageSource, /confirmButtonText: confirmation\.confirmButtonText/)
assert.match(entitlementPageSource, /item\.planCode === 'BOARD_VIP' && item\.entitlementStatus === 'ACTIVE'/)
assert.match(entitlementPageSource, /expectedSavedVersion/)
assert.match(entitlementPageSource, /expectedSavedEntitlementVersion\(original\?\.version \?\? null\)/)
assert.match(entitlementPageSource, /待连接器认证/)
assert.match(entitlementPageSource, /listIndependentBoardPlans\(\)/)
assert.match(entitlementPageSource, /parseProductPlanCatalog\(response\.data\)/)
assert.match(entitlementPageSource, /v-for="plan in planCatalog"/)
assert.match(entitlementPageSource, /currentPlanLabel\(scope\.row\.planCode\)/)
assert.match(entitlementPageSource, /planCatalog: planCatalog\.value/)
assert.doesNotMatch(entitlementPageSource, /<el-radio-button value="BOARD_(?:FREE|VIP)"/)
assert.doesNotMatch(entitlementPageSource, /connectorVerifiedAt|OAuth|reasonCode/)
assert.match(auditPageSource, /operationResponse\.data/)
assert.match(auditPageSource, /auditEnvelope\.truncated/)
assert.match(auditPageSource, /最多返回 \$\{auditEnvelope\.limit\} 条/)
assert.doesNotMatch(auditPageSource, /<el-table-column\s+label="操作"(?:\s|>)/)
assert.doesNotMatch(auditPageSource, /requestDigest|productCode|metricCode|\bunits\b/)
assert.match(receiptPageSource, /checkPermi\(\['board:entitlement:audit'\]\)/)
assert.match(receiptPageSource, /const canOperatePage = canListEnterprises && canAuditReceipts/)
assert.match(receiptPageSource, /void applyMemberEnrichment\(requestToken, requestedTenantId\)/)
assert.match(receiptPageSource, /const receiptResponse = await listIndependentBoardEntitlementReceipts\(requestedTenantId\)/)
assert.doesNotMatch(receiptPageSource, /Promise\.all\(\[\s*loadMemberEnrichment/)
assert.match(receiptPageSource, /不影响权益回执的安全读取/)
assert.match(receiptPageSource, /该表不含 productCode/)
assert.match(receiptPageSource, /不能证明服务端执行了结构化产品过滤/)
assert.doesNotMatch(receiptPageSource, /canOperatePage = canListEnterprises && canListMembers/)
assert.match(receiptPageSource, /receiptResponse\.data/)
assert.match(receiptPageSource, /receiptEnvelope\.truncated/)
assert.match(receiptPageSource, /最多返回 \$\{receiptEnvelope\.limit\} 条/)
assert.match(receiptPageSource, /只读审计/)
assert.doesNotMatch(receiptPageSource, /<el-table-column\s+label="操作"(?:\s|>)/)
assert.doesNotMatch(receiptPageSource, /payloadDigest|grantIndependentBoardEntitlement|revokeIndependentBoardEntitlement/)
assert.match(modelSource, /'updatedAt', 'completedAt'/)
assert.match(modelSource, /字段集合不符合安全合同/)
assert.match(apiSource, /\/business\/independent-board\/entitlements/)
assert.match(apiSource, /\/business\/independent-board\/entitlements\/revoke/)
assert.match(apiSource, /\/business\/independent-board\/entitlement-receipts/)
assert.match(apiSource, /\/business\/independent-board\/operations/)
assert.match(apiSource, /\/business\/independent-board\/plans/)

console.log('Independent Board admin UI verification passed: strict DTO, CAS, tenant-race, permission and responsive matrices are green.')
