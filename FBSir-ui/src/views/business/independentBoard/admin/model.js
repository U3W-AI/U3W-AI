const BOARD_PLAN_CODES = Object.freeze(['BOARD_FREE', 'BOARD_VIP'])
const ENTITLEMENT_STATUSES = Object.freeze([
  'ACTIVE', 'INACTIVE', 'SUSPENDED', 'REVOKED', 'EXPIRED'
])
const ACTIVATION_STATES = Object.freeze([
  'FREE', 'ACTIVE', 'PENDING_CONNECTOR', 'INVALID_ENTITLEMENT', 'EXPIRED'
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
  if (value.validUntil !== null
      && Date.parse(toZonedDateTime(value.validUntil)) <= Date.parse(toZonedDateTime(value.validFrom))) {
    fail('权益有效期前后不一致')
  }
  if (value.activationState === 'ACTIVE'
      && (value.planCode !== 'BOARD_VIP' || value.entitlementStatus !== 'ACTIVE')) {
    fail('VIP生效状态与授予记录不一致')
  }
  if (value.activationState === 'PENDING_CONNECTOR' && value.planCode !== 'BOARD_VIP') {
    fail('待连接状态只能用于VIP权益')
  }
  if (value.planCode === 'BOARD_VIP' && value.activationState === 'FREE') {
    fail('VIP授予记录不能声明为免费版生效状态')
  }
  return Object.freeze({ ...value })
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
    EXPIRED: { label: '已过期', type: 'danger' }
  }
  return table[state] || { label: '未知状态', type: 'danger' }
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
