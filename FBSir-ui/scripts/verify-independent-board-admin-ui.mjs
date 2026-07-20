import assert from 'node:assert/strict'
import fs from 'node:fs'

import {
  activationMeta,
  buildEntitlementGrantPayload,
  createLatestRequestGuard,
  expectedSavedEntitlementVersion,
  isCasConflict,
  operationStatusMeta,
  parseEnterprisePage,
  parseEntitlement,
  parseEntitlementList,
  parseMemberPage,
  parseOperationEnvelope,
  toBoardDateTimeInput
} from '../src/views/business/independentBoard/admin/model.js'

const tenantId = 7
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
const modelSource = fs.readFileSync(
  new URL('../src/views/business/independentBoard/admin/model.js', import.meta.url),
  'utf8'
)
const apiSource = fs.readFileSync(
  new URL('../src/api/business/independentBoard/admin.js', import.meta.url),
  'utf8'
)

for (const source of [entitlementPageSource, auditPageSource]) {
  assert.match(source, /listEnterprise\(\{ pageNum: 1, pageSize: 1000 \}\)/)
  assert.match(source, /listEnterpriseMember\(\{ enterpriseId: requestedTenantId, pageNum: 1, pageSize: 1000 \}\)/)
  assert.match(source, /createLatestRequestGuard\(\)/)
  assert.match(source, /selectedTenantId\.value !== requestedTenantId/)
  assert.match(source, /@media \(max-width: 768px\)/)
  assert.doesNotMatch(source, /<el-input\b[^>]*(?:tenantId|memberId|userId)/)
}

assert.match(entitlementPageSource, /v-hasPermi="\['board:entitlement:grant'\]"/)
assert.match(entitlementPageSource, /expectedSavedVersion/)
assert.match(entitlementPageSource, /expectedSavedEntitlementVersion\(original\?\.version \?\? null\)/)
assert.match(entitlementPageSource, /待连接器认证/)
assert.doesNotMatch(entitlementPageSource, /connectorVerifiedAt|OAuth|撤销/)
assert.match(auditPageSource, /operationResponse\.data/)
assert.match(auditPageSource, /auditEnvelope\.truncated/)
assert.match(auditPageSource, /最多返回 \$\{auditEnvelope\.limit\} 条/)
assert.doesNotMatch(auditPageSource, /<el-table-column\s+label="操作"(?:\s|>)/)
assert.doesNotMatch(auditPageSource, /requestDigest|productCode|metricCode|\bunits\b/)
assert.match(modelSource, /'updatedAt', 'completedAt'/)
assert.match(modelSource, /字段集合不符合安全合同/)
assert.match(apiSource, /\/business\/independent-board\/entitlements/)
assert.match(apiSource, /\/business\/independent-board\/operations/)

console.log('Independent Board admin UI verification passed: strict DTO, CAS, tenant-race, permission and responsive matrices are green.')
