const BOARD_PLAN_CODES = Object.freeze(['BOARD_FREE', 'BOARD_VIP'])
const ENTITLEMENT_STATUSES = Object.freeze([
  'ACTIVE', 'INACTIVE', 'SUSPENDED', 'REVOKED', 'EXPIRED'
])
const ACTIVATION_STATES = Object.freeze([
  'FREE', 'ACTIVE', 'PENDING_CONNECTOR', 'INVALID_ENTITLEMENT', 'EXPIRED', 'REVOKED'
])
const OPERATION_STATUSES = Object.freeze([
  'PENDING', 'RESERVED', 'COMPLETED', 'REJECTED', 'RELEASED', 'FAILED', 'UNKNOWN'
])

const ENTITLEMENT_KEYS = Object.freeze([
  'tenantId', 'memberId', 'userId', 'planCode', 'entitlementStatus',
  'activationState', 'validFrom', 'validUntil', 'version', 'updatedAt'
])
const OPERATION_KEYS = Object.freeze([
  'operationId', 'tenantId', 'memberId', 'userId', 'status',
  'effectivePlanCode', 'bucketDate', 'agendaCount', 'seatCount',
  'remainingCount', 'createdAt', 'updatedAt', 'completedAt'
])
const ENTITLEMENT_RECEIPT_KEYS = Object.freeze([
  'receiptId', 'tenantId', 'actorUserId', 'targetMemberId',
  'action', 'evidenceLevel', 'createdAt'
])
const ENTITLEMENT_RECEIPT_ACTIONS = Object.freeze([
  'ENTITLEMENT_GRANTED', 'ENTITLEMENT_UPDATED', 'ENTITLEMENT_REVOKED'
])
const PLAN_KEYS = Object.freeze([
  'productCode', 'planCode', 'planName', 'vip', 'connectorRequired',
  'dailyMeetingLimit', 'agendaLimit', 'seatLimit', 'secretaryEnabled',
  'status', 'version', 'updatedAt'
])

function fail(message) {
  throw new Error(message)
}

function isPlainObject(value) {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
}

function isPositiveSafeInteger(value) {
  return Number.isSafeInteger(value) && value > 0
}

function isNonNegativeSafeInteger(value) {
  return Number.isSafeInteger(value) && value >= 0
}

function isNonBlankString(value) {
  return typeof value === 'string' && value.trim().length > 0
}

function assertExactKeys(value, expectedKeys, label) {
  if (!isPlainObject(value)) fail(`${label}不是对象`)
  const actual = Object.keys(value).sort()
  const expected = [...expectedKeys].sort()
  if (actual.length !== expected.length
      || actual.some((key, index) => key !== expected[index])) {
    fail(`${label}字段集合不符合安全合同`)
  }
}

function parseBoardDateTime(value, label, nullable = false) {
  if (nullable && value === null) return null
  if (!isNonBlankString(value)) fail(`${label}不是有效时间`)
  const normalized = value.includes('T') || /(?:Z|[+-]\d{2}:\d{2})$/.test(value)
    ? value
    : value.replace(' ', 'T')
  const zoned = /(?:Z|[+-]\d{2}:\d{2})$/.test(normalized)
    ? normalized
    : `${normalized}+08:00`
  if (Number.isNaN(Date.parse(zoned))) fail(`${label}不是有效时间`)
  return value
}

function parseDateOnly(value, label) {
  if (typeof value !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(value)) {
    fail(`${label}不是有效日期`)
  }
  const date = new Date(`${value}T00:00:00+08:00`)
  if (Number.isNaN(date.getTime()) || toShanghaiDate(date) !== value) {
    fail(`${label}不是有效日期`)
  }
  return value
}

function parseOperationId(value) {
  if (typeof value !== 'string'
      || !/^[A-Za-z0-9][A-Za-z0-9._:-]{7,127}$/.test(value)) {
    fail('操作编号格式不正确')
  }
  return value
}

function toShanghaiParts(value) {
  const date = value instanceof Date ? value : new Date(value)
  if (Number.isNaN(date.getTime())) fail('时间格式不正确')
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Shanghai',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hourCycle: 'h23'
  }).formatToParts(date)
  return Object.fromEntries(parts.map(part => [part.type, part.value]))
}

function toShanghaiDate(value) {
  const parts = toShanghaiParts(value)
  return `${parts.year}-${parts.month}-${parts.day}`
}

export function toBoardDateTimeInput(value) {
  if (!value) return null
  const parts = toShanghaiParts(toZonedDateTime(value))
  return `${parts.year}-${parts.month}-${parts.day}T${parts.hour}:${parts.minute}:${parts.second}`
}

export function parseEnterprisePage(response) {
  if (!isPlainObject(response) || !Array.isArray(response.rows)
      || !isNonNegativeSafeInteger(response.total)) {
    fail('企业列表响应格式不正确')
  }
  if (response.total < response.rows.length) fail('企业列表总数不一致')
  const seen = new Set()
  const records = response.rows.map((item, index) => {
    if (!isPlainObject(item)
        || !isPositiveSafeInteger(item.id)
        || !isNonBlankString(item.enterpriseName)
        || ![1, 2].includes(item.status)) {
      fail(`第${index + 1}条企业数据不完整`)
    }
    if (seen.has(item.id)) fail('企业列表包含重复企业')
    seen.add(item.id)
    return Object.freeze({
      id: item.id,
      enterpriseName: item.enterpriseName.trim(),
      status: item.status
    })
  })
  return Object.freeze({
    records: Object.freeze(records),
    total: response.total,
    truncated: response.total > records.length
  })
}

export function parseMemberPage(response, tenantId) {
  if (!isPositiveSafeInteger(tenantId)) fail('企业上下文无效')
  if (!isPlainObject(response) || !Array.isArray(response.rows)
      || !isNonNegativeSafeInteger(response.total)) {
    fail('成员列表响应格式不正确')
  }
  if (response.total < response.rows.length) fail('成员列表总数不一致')
  const seenMembers = new Set()
  const seenUsers = new Set()
  const records = response.rows.map((item, index) => {
    if (!isPlainObject(item)
        || !isPositiveSafeInteger(item.id)
        || item.enterpriseId !== tenantId
        || !isPositiveSafeInteger(item.userId)
        || ![1, 2].includes(item.status)
        || !isNonBlankString(item.role)) {
      fail(`第${index + 1}条成员数据未与当前企业安全绑定`)
    }
    if (seenMembers.has(item.id) || seenUsers.has(item.userId)) {
      fail('成员列表包含重复身份')
    }
    seenMembers.add(item.id)
    seenUsers.add(item.userId)
    return Object.freeze({
      id: item.id,
      enterpriseId: item.enterpriseId,
      userId: item.userId,
      userName: isNonBlankString(item.userName) ? item.userName.trim() : '',
      role: item.role.trim(),
      status: item.status
    })
  })
  return Object.freeze({
    records: Object.freeze(records),
    total: response.total,
    truncated: response.total > records.length
  })
}

export function parseEntitlement(value, expectedTenantId) {
  assertExactKeys(value, ENTITLEMENT_KEYS, '权益记录')
  if (!isPositiveSafeInteger(expectedTenantId)
      || value.tenantId !== expectedTenantId
      || !isPositiveSafeInteger(value.memberId)
      || !isPositiveSafeInteger(value.userId)
      || !BOARD_PLAN_CODES.includes(value.planCode)
      || !ENTITLEMENT_STATUSES.includes(value.entitlementStatus)
      || !ACTIVATION_STATES.includes(value.activationState)
      || !isPositiveSafeInteger(value.version)) {
    fail('权益记录包含非法值或跨企业数据')
  }
  parseBoardDateTime(value.validFrom, '权益开始时间')
  parseBoardDateTime(value.validUntil, '权益截止时间', true)
  parseBoardDateTime(value.updatedAt, '权益更新时间')
  if (value.entitlementStatus === 'REVOKED' && value.validUntil === null) {
    fail('已撤销权益必须包含撤销生效时间')
  }
  if (value.validUntil !== null) {
    const validFrom = Date.parse(toZonedDateTime(value.validFrom))
    const validUntil = Date.parse(toZonedDateTime(value.validUntil))
    const invalidOrder = value.entitlementStatus === 'REVOKED'
      ? validUntil < validFrom
      : validUntil <= validFrom
    if (invalidOrder) fail('权益有效期前后不一致')
  }
  if (value.activationState === 'ACTIVE'
      && (value.planCode !== 'BOARD_VIP' || value.entitlementStatus !== 'ACTIVE')) {
    fail('VIP生效状态与授予记录不一致')
  }
  if (value.activationState === 'PENDING_CONNECTOR' && value.planCode !== 'BOARD_VIP') {
    fail('待连接状态只能用于VIP权益')
  }
  if ((value.activationState === 'REVOKED') !== (value.entitlementStatus === 'REVOKED')) {
    fail('权益撤销状态与授予记录不一致')
  }
  if (value.planCode === 'BOARD_VIP' && value.activationState === 'FREE') {
    fail('VIP授予记录不能声明为免费版生效状态')
  }
  return Object.freeze({ ...value })
}

export function parseEntitlementReceiptEnvelope(value, expectedTenantId) {
  assertExactKeys(value, ['records', 'limit', 'truncated'], '权益回执审计响应')
  if (!isPositiveSafeInteger(expectedTenantId)
      || !Array.isArray(value.records)
      || value.limit !== 500
      || typeof value.truncated !== 'boolean'
      || value.records.length > value.limit) {
    fail('权益回执审计响应边界不正确')
  }
  const seen = new Set()
  const records = value.records.map((item, index) => {
    assertExactKeys(item, ENTITLEMENT_RECEIPT_KEYS, `第${index + 1}条权益回执`)
    if (typeof item.receiptId !== 'string'
        || !/^[A-Za-z0-9][A-Za-z0-9._:-]{7,127}$/.test(item.receiptId)
        || item.tenantId !== expectedTenantId
        || !isPositiveSafeInteger(item.actorUserId)
        || !isPositiveSafeInteger(item.targetMemberId)
        || !ENTITLEMENT_RECEIPT_ACTIONS.includes(item.action)
        || item.evidenceLevel !== 'ACTION_COMPLETED') {
      fail(`第${index + 1}条权益回执包含非法值或跨企业数据`)
    }
    parseBoardDateTime(item.createdAt, '权益回执创建时间')
    if (seen.has(item.receiptId)) fail('权益回执审计响应包含重复回执编号')
    seen.add(item.receiptId)
    return Object.freeze({ ...item })
  })
  return Object.freeze({
    records: Object.freeze(records),
    limit: value.limit,
    truncated: value.truncated
  })
}

export function parseEntitlementList(value, expectedTenantId) {
  if (!Array.isArray(value)) fail('权益列表响应格式不正确')
  const seenMembers = new Set()
  return Object.freeze(value.map(item => {
    const entitlement = parseEntitlement(item, expectedTenantId)
    if (seenMembers.has(entitlement.memberId)) fail('同一成员存在重复独董会权益')
    seenMembers.add(entitlement.memberId)
    return entitlement
  }))
}

export function parseProductPlanCatalog(value) {
  if (!Array.isArray(value) || value.length !== BOARD_PLAN_CODES.length) {
    fail('套餐目录必须包含且仅包含当前受支持的独董会套餐')
  }
  const seen = new Set()
  const plans = value.map((item, index) => {
    assertExactKeys(item, PLAN_KEYS, `第${index + 1}条套餐策略`)
    if (item.productCode !== 'FBSIR_INDEPENDENT_BOARD'
        || !BOARD_PLAN_CODES.includes(item.planCode)
        || !isNonBlankString(item.planName)
        || item.planName.length > 128
        || /[\u0000-\u001f\u007f-\u009f]/.test(item.planName)
        || typeof item.vip !== 'boolean'
        || typeof item.connectorRequired !== 'boolean'
        || !isPositiveSafeInteger(item.dailyMeetingLimit)
        || !isPositiveSafeInteger(item.agendaLimit)
        || !(item.seatLimit === null || isPositiveSafeInteger(item.seatLimit))
        || typeof item.secretaryEnabled !== 'boolean'
        || item.status !== 'ACTIVE'
        || !isNonNegativeSafeInteger(item.version)) {
      fail(`第${index + 1}条套餐策略包含非法值`)
    }
    parseBoardDateTime(item.updatedAt, '套餐策略更新时间')
    if (seen.has(item.planCode)) fail('套餐目录包含重复套餐')
    seen.add(item.planCode)
    const isVip = item.planCode === 'BOARD_VIP'
    if (item.vip !== isVip
        || item.connectorRequired !== isVip
        || item.secretaryEnabled !== isVip
        || item.dailyMeetingLimit !== (isVip ? 5 : 1)
        || item.agendaLimit !== (isVip ? 30 : 5)
        || (isVip ? item.seatLimit !== null : item.seatLimit !== 3)) {
      fail('套餐策略能力与套餐代码不一致')
    }
    return Object.freeze({ ...item, planName: item.planName.trim() })
  })
  if (BOARD_PLAN_CODES.some(code => !seen.has(code))) {
    fail('套餐目录缺少受支持的独董会套餐')
  }
  plans.sort((left, right) => left.planCode.localeCompare(right.planCode))
  return Object.freeze(plans)
}

export function parseOperationEnvelope(value, expectedTenantId) {
  assertExactKeys(value, ['records', 'limit', 'truncated'], '会议额度审计响应')
  if (!Array.isArray(value.records)
      || value.limit !== 500
      || typeof value.truncated !== 'boolean'
      || value.records.length > value.limit) {
    fail('会议额度审计响应边界不正确')
  }
  const seen = new Set()
  const records = value.records.map((item, index) => {
    assertExactKeys(item, OPERATION_KEYS, `第${index + 1}条额度操作`)
    if (item.tenantId !== expectedTenantId
        || !isPositiveSafeInteger(item.memberId)
        || !isPositiveSafeInteger(item.userId)
        || !OPERATION_STATUSES.includes(item.status)
        || !BOARD_PLAN_CODES.includes(item.effectivePlanCode)
        || !isPositiveSafeInteger(item.agendaCount) || item.agendaCount > 30
        || !isPositiveSafeInteger(item.seatCount)
        || !isNonNegativeSafeInteger(item.remainingCount)) {
      fail(`第${index + 1}条额度操作包含非法值或跨企业数据`)
    }
    parseOperationId(item.operationId)
    parseDateOnly(item.bucketDate, '额度日期')
    parseBoardDateTime(item.createdAt, '额度操作创建时间')
    parseBoardDateTime(item.updatedAt, '额度操作更新时间')
    parseBoardDateTime(item.completedAt, '额度处理时间', true)
    if (seen.has(item.operationId)) fail('额度审计响应包含重复操作编号')
    seen.add(item.operationId)
    return Object.freeze({ ...item })
  })
  return Object.freeze({
    records: Object.freeze(records),
    limit: value.limit,
    truncated: value.truncated
  })
}

export function buildEntitlementGrantPayload({
  tenantId,
  member,
  planCode,
  validUntil,
  existingEntitlement = null,
  now = Date.now()
}) {
  if (!isPositiveSafeInteger(tenantId)
      || !isPlainObject(member)
      || member.enterpriseId !== tenantId
      || !isPositiveSafeInteger(member.id)
      || !isPositiveSafeInteger(member.userId)
      || member.status !== 1) {
    fail('只能为当前企业的有效成员配置权益')
  }
  if (!BOARD_PLAN_CODES.includes(planCode)) fail('请选择有效的独董会套餐')
  let expectedVersion = 0
  if (existingEntitlement !== null) {
    const existing = parseEntitlement(existingEntitlement, tenantId)
    if (existing.memberId !== member.id || existing.userId !== member.userId) {
      fail('成员身份与当前权益记录不一致')
    }
    if (existing.entitlementStatus === 'REVOKED') {
      fail('已撤销权益不能通过调整操作隐式恢复')
    }
    expectedVersion = existing.version
  }
  if (validUntil !== null && validUntil !== undefined && validUntil !== '') {
    parseBoardDateTime(validUntil, '权益截止时间')
    if (Date.parse(toZonedDateTime(validUntil)) <= now) {
      fail('权益截止时间必须晚于当前时间')
    }
  }
  return Object.freeze({
    tenantId,
    memberId: member.id,
    userId: member.userId,
    planCode,
    validUntil: validUntil || null,
    expectedVersion
  })
}

export function buildEntitlementRevokePayload({ tenantId, entitlement }) {
  const current = parseEntitlement(entitlement, tenantId)
  if (current.entitlementStatus !== 'ACTIVE') {
    fail('只能撤销当前仍处于授予有效状态的权益')
  }
  return Object.freeze({
    tenantId,
    memberId: current.memberId,
    userId: current.userId,
    expectedVersion: current.version
  })
}

export function buildEntitlementRevokeConfirmation({
  enterprise,
  member = null,
  entitlement,
  planCatalog
}) {
  assertExactKeys(enterprise, ['id', 'enterpriseName', 'status'], '撤销确认企业')
  if (!isPositiveSafeInteger(enterprise.id)
      || !isNonBlankString(enterprise.enterpriseName)
      || ![1, 2].includes(enterprise.status)) {
    fail('撤销确认企业上下文无效')
  }
  const current = parseEntitlement(entitlement, enterprise.id)
  let currentPlanName = current.planCode
  let freePlanName = 'BOARD_FREE'
  try {
    const plans = parseProductPlanCatalog(planCatalog)
    currentPlanName = plans.find(plan => plan.planCode === current.planCode).planName
    freePlanName = plans.find(plan => plan.planCode === 'BOARD_FREE').planName
  } catch {
    // Revocation is a risk-closing path. Catalog display drift must not block it.
  }
  let targetMemberLabel = `历史成员 #${current.memberId}`
  if (member !== null) {
    assertExactKeys(member,
      ['id', 'enterpriseId', 'userId', 'userName', 'role', 'status'], '撤销确认成员')
    if (member.id !== current.memberId
        || member.enterpriseId !== current.tenantId
        || member.userId !== current.userId
        || !isNonBlankString(member.role)
        || ![1, 2].includes(member.status)
        || typeof member.userName !== 'string') {
      fail('撤销确认成员与权益记录不一致')
    }
    targetMemberLabel = memberLabel(member)
  }
  return Object.freeze({
    title: '确认撤销权益',
    message: `企业：${enterprise.enterpriseName.trim()}（企业 #${enterprise.id}）；成员：${targetMemberLabel}；用户 ID：${current.userId}；授予计划：${currentPlanName}；当前版本：${current.version}。撤销生效后该成员将按${freePlanName}运行，并写入不可变审计回执。`,
    confirmButtonText: '确认撤销'
  })
}

export function verifyEntitlementRevokeReadback({ tenantId, original, saved }) {
  const before = parseEntitlement(original, tenantId)
  const after = parseEntitlement(saved, tenantId)
  if (before.entitlementStatus !== 'ACTIVE'
      || after.memberId !== before.memberId
      || after.userId !== before.userId
      || after.planCode !== before.planCode
      || Date.parse(toZonedDateTime(after.validFrom))
        !== Date.parse(toZonedDateTime(before.validFrom))
      || after.entitlementStatus !== 'REVOKED'
      || after.activationState !== 'REVOKED'
      || after.validUntil === null
      || after.version !== expectedSavedEntitlementVersion(before.version)) {
    fail('撤销回读与确认时的权益记录不一致')
  }
  return after
}

function toZonedDateTime(value) {
  const normalized = value.includes('T') ? value : value.replace(' ', 'T')
  return /(?:Z|[+-]\d{2}:\d{2})$/.test(normalized)
    ? normalized
    : `${normalized}+08:00`
}

export function createLatestRequestGuard() {
  let current = 0
  return Object.freeze({
    next() {
      current += 1
      return current
    },
    isCurrent(token) {
      return token === current
    }
  })
}

export function expectedSavedEntitlementVersion(existingVersion = null) {
  if (existingVersion === null) return 1
  if (!isNonNegativeSafeInteger(existingVersion)
      || existingVersion === Number.MAX_SAFE_INTEGER) {
    fail('权益并发版本无效')
  }
  return existingVersion + 1
}

export function activationMeta(state) {
  const table = {
    FREE: { label: '免费版', type: 'info' },
    ACTIVE: { label: 'VIP已生效', type: 'success' },
    PENDING_CONNECTOR: { label: '待连接器认证', type: 'warning' },
    INVALID_ENTITLEMENT: { label: '权益配置异常', type: 'danger' },
    EXPIRED: { label: '已过期', type: 'danger' },
    REVOKED: { label: '已撤销', type: 'danger' }
  }
  return table[state] || { label: '未知状态', type: 'danger' }
}

export function entitlementReceiptActionMeta(action) {
  const table = {
    ENTITLEMENT_GRANTED: { label: '权益已授予', type: 'success' },
    ENTITLEMENT_UPDATED: { label: '权益已调整', type: 'primary' },
    ENTITLEMENT_REVOKED: { label: '权益已撤销', type: 'danger' }
  }
  return table[action] || { label: '动作异常', type: 'danger' }
}

export function entitlementStatusMeta(status) {
  const table = {
    ACTIVE: { label: '授予有效', type: 'success' },
    INACTIVE: { label: '授予无效', type: 'info' },
    SUSPENDED: { label: '授予暂停', type: 'warning' },
    REVOKED: { label: '授予已撤销', type: 'danger' },
    EXPIRED: { label: '授予已过期', type: 'danger' }
  }
  return table[status] || { label: '状态异常', type: 'danger' }
}

export function operationStatusMeta(status) {
  const table = {
    PENDING: { label: '额度处理中', type: 'warning' },
    RESERVED: { label: '额度已预留', type: 'success' },
    COMPLETED: { label: '额度操作已完成', type: 'primary' },
    REJECTED: { label: '额度申请未通过', type: 'danger' },
    RELEASED: { label: '额度已释放', type: 'info' },
    FAILED: { label: '额度操作失败', type: 'danger' },
    UNKNOWN: { label: '状态待核验', type: 'warning' }
  }
  return table[status] || { label: '状态异常', type: 'danger' }
}

export function planLabel(planCode) {
  if (planCode === 'BOARD_VIP') return 'VIP版'
  if (planCode === 'BOARD_FREE') return '免费版'
  return '未知套餐'
}

export function memberLabel(member) {
  if (!member) return '成员信息待核验'
  const name = member.userName || `用户 #${member.userId}`
  return `${name} · 成员 #${member.id}`
}

export function formatBoardDateTime(value) {
  if (!value) return '未设置截止时间'
  const date = new Date(toZonedDateTime(value))
  if (Number.isNaN(date.getTime())) return '--'
  return new Intl.DateTimeFormat('zh-CN', {
    timeZone: 'Asia/Shanghai',
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', second: '2-digit',
    hour12: false
  }).format(date)
}

export function isCasConflict(error) {
  const status = error?.response?.status
  const message = String(error?.response?.data?.msg || error?.message || error || '')
  return status === 409 || /(?:409|VERSION|OPTIMISTIC|CONFLICT|STALE)/i.test(message)
}
