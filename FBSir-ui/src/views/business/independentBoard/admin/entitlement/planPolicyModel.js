import { parseProductPlanCatalog } from '../model.js'

const RECEIPT_KEYS = Object.freeze([
  'receiptId', 'planCode', 'policyVersion', 'previousReceiptId',
  'rollbackOfReceiptId', 'action', 'actorType', 'actorUserId',
  'planName', 'dailyMeetingLimit', 'agendaLimit', 'seatLimit',
  'secretaryEnabled', 'previousPolicyDigest', 'policyDigest',
  'evidenceLevel', 'createdAt'
])
const PAYLOAD_KEYS = Object.freeze([
  'planCode', 'expectedVersion', 'planName', 'dailyMeetingLimit',
  'agendaLimit', 'seatLimit', 'secretaryEnabled',
  'rollbackOfReceiptId', 'idempotencyKey'
])
const DRAFT_KEYS = Object.freeze([
  'planCode', 'expectedVersion', 'planName', 'dailyMeetingLimit',
  'agendaLimit', 'seatLimit', 'secretaryEnabled'
])
const BUILD_INPUT_KEYS = Object.freeze([
  'catalog', 'draft', 'rollbackOfReceiptId', 'idempotencyKey'
])
const ROLLBACK_INPUT_KEYS = Object.freeze([
  'catalog', 'receipt', 'idempotencyKey'
])
const PENDING_KEYS = Object.freeze([
  'schema', 'actorUserId', 'payload', 'createdAtMs'
])
const PLAN_CODES = Object.freeze(['BOARD_FREE', 'BOARD_VIP'])
const RECEIPT_ACTIONS = Object.freeze([
  'PLAN_POLICY_BASELINED', 'PLAN_POLICY_REVISED', 'PLAN_POLICY_ROLLED_BACK'
])
const IDENTIFIER = /^[A-Za-z0-9][A-Za-z0-9._:-]{15,127}$/
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i
const SHA256 = /^[0-9a-f]{64}$/
const FORBIDDEN_TEXT = /[\p{Cc}\p{Cf}\p{Cs}]/u
const PENDING_SCHEMA = 'fbsir.independent-board-plan-policy-pending/v1'

function fail(message) {
  throw new Error(message)
}

function isPlainObject(value) {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
}

function assertExactKeys(value, expected, label) {
  if (!isPlainObject(value)) fail(`${label}必须是对象`)
  const actual = Object.keys(value).sort()
  const wanted = [...expected].sort()
  if (actual.length !== wanted.length
      || actual.some((key, index) => key !== wanted[index])) {
    fail(`${label}字段集合不符合安全合同`)
  }
}

function isPositiveSafeInteger(value) {
  return Number.isSafeInteger(value) && value > 0
}

function parseIdentifier(value, label, nullable = false) {
  if (nullable && value === null) return null
  if (typeof value !== 'string' || !IDENTIFIER.test(value)) {
    fail(`${label}格式不符合安全合同`)
  }
  return value
}

function parseDigest(value, label, nullable = false) {
  if (nullable && value === null) return null
  if (typeof value !== 'string' || !SHA256.test(value)) {
    fail(`${label}不是规范 SHA-256 摘要`)
  }
  return value
}

function parseDateTime(value, label) {
  if (typeof value !== 'string' || value.trim() !== value || value.length === 0) {
    fail(`${label}不是有效时间`)
  }
  const normalized = value.includes('T') || /(?:Z|[+-]\d{2}:\d{2})$/.test(value)
    ? value
    : value.replace(' ', 'T')
  const zoned = /(?:Z|[+-]\d{2}:\d{2})$/.test(normalized)
    ? normalized
    : `${normalized}+08:00`
  if (Number.isNaN(Date.parse(zoned))) fail(`${label}不是有效时间`)
  return value
}

function parsePlanName(value) {
  if (typeof value !== 'string') fail('套餐名称必须是字符串')
  const codePointLength = [...value].length
  if (codePointLength < 1 || codePointLength > 128 || value.trim() !== value
      || FORBIDDEN_TEXT.test(value)) {
    fail('套餐名称必须是 1–128 个非控制字符')
  }
  return value
}

function parsePolicyValues(value, label) {
  if (!PLAN_CODES.includes(value.planCode)) fail(`${label}套餐代码不受支持`)
  const planName = parsePlanName(value.planName)
  if (!isPositiveSafeInteger(value.dailyMeetingLimit)
      || value.dailyMeetingLimit > 10000) {
    fail(`${label}日会议额度必须是 1–10000 的整数`)
  }
  if (!isPositiveSafeInteger(value.agendaLimit) || value.agendaLimit > 30) {
    fail(`${label}议题额度必须是 1–30 的整数`)
  }
  if (!(value.seatLimit === null
      || (isPositiveSafeInteger(value.seatLimit) && value.seatLimit <= 100))) {
    fail(`${label}有限席位额度必须是 1–100 的整数`)
  }
  if (value.planCode === 'BOARD_FREE' && value.seatLimit === null) {
    fail('免费套餐必须设置有限席位额度')
  }
  if (typeof value.secretaryEnabled !== 'boolean') {
    fail(`${label}秘书能力必须是布尔值`)
  }
  return Object.freeze({
    planName,
    dailyMeetingLimit: value.dailyMeetingLimit,
    agendaLimit: value.agendaLimit,
    seatLimit: value.seatLimit,
    secretaryEnabled: value.secretaryEnabled
  })
}

function policyValuesEqual(left, right) {
  return left.planName === right.planName
    && left.dailyMeetingLimit === right.dailyMeetingLimit
    && left.agendaLimit === right.agendaLimit
    && left.seatLimit === right.seatLimit
    && left.secretaryEnabled === right.secretaryEnabled
}

export function parsePlanPolicyCatalog(value) {
  const catalog = parseProductPlanCatalog(value)
  const free = catalog.find(plan => plan.planCode === 'BOARD_FREE')
  const vip = catalog.find(plan => plan.planCode === 'BOARD_VIP')
  if (!catalog.every(plan => isPositiveSafeInteger(plan.version))) {
    fail('当前套餐策略版本必须是正整数')
  }
  parsePolicyValues(free, '免费套餐')
  parsePolicyValues(vip, 'VIP 套餐')
  requireCatalogInvariants(free, vip)
  return catalog
}

function requireCatalogInvariants(free, vip) {
  if (vip.dailyMeetingLimit < free.dailyMeetingLimit
      || vip.agendaLimit < free.agendaLimit
      || (vip.seatLimit !== null && vip.seatLimit < free.seatLimit)
      || (free.secretaryEnabled && !vip.secretaryEnabled)) {
    fail('完整套餐目录不满足 VIP 能力不得低于免费套餐的约束')
  }
}

export function isPlanPolicyCandidateEnabled(env = {}) {
  return env?.VITE_FBSIR_BOARD_PLAN_POLICY_CANDIDATE === 'true'
}

export function parsePlanPolicyReceipt(value, index = 0) {
  assertExactKeys(value, RECEIPT_KEYS, `第${index + 1}条套餐策略回执`)
  parseIdentifier(value.receiptId, '套餐策略回执编号')
  if (!PLAN_CODES.includes(value.planCode)
      || !isPositiveSafeInteger(value.policyVersion)
      || !RECEIPT_ACTIONS.includes(value.action)
      || !['SYSTEM_MIGRATION', 'ADMIN_USER'].includes(value.actorType)
      || value.evidenceLevel !== 'ACTION_COMPLETED') {
    fail(`第${index + 1}条套餐策略回执包含非法身份或状态`)
  }
  parseIdentifier(value.previousReceiptId, '前序回执编号', true)
  parseIdentifier(value.rollbackOfReceiptId, '补偿目标回执编号', true)
  parseDigest(value.previousPolicyDigest, '前序策略摘要', true)
  parseDigest(value.policyDigest, '策略摘要')
  parseDateTime(value.createdAt, '策略回执创建时间')
  const policy = parsePolicyValues(value, `第${index + 1}条套餐策略回执`)

  if (value.action === 'PLAN_POLICY_BASELINED') {
    if (value.policyVersion !== 1
        || value.actorType !== 'SYSTEM_MIGRATION'
        || value.actorUserId !== null
        || value.previousReceiptId !== null
        || value.rollbackOfReceiptId !== null
        || value.previousPolicyDigest !== null) {
      fail('基线回执的版本、操作者或前序链不一致')
    }
  } else {
    if (value.policyVersion <= 1
        || value.actorType !== 'ADMIN_USER'
        || !isPositiveSafeInteger(value.actorUserId)
        || value.previousReceiptId === null
        || value.previousPolicyDigest === null
        || (value.action === 'PLAN_POLICY_REVISED'
          ? value.rollbackOfReceiptId !== null
          : value.rollbackOfReceiptId === null)) {
      fail('管理员策略回执的操作者、前序链或补偿语义不一致')
    }
  }
  return Object.freeze({ ...value, ...policy })
}

export function parsePlanPolicyReceiptEnvelope(value) {
  assertExactKeys(value, ['records', 'limit', 'truncated'], '套餐策略审计响应')
  if (!Array.isArray(value.records)
      || value.limit !== 100
      || typeof value.truncated !== 'boolean'
      || value.records.length > value.limit
      || (value.truncated && value.records.length !== value.limit)) {
    fail('套餐策略审计响应必须遵守 100 条安全边界')
  }
  const seen = new Set()
  const records = value.records.map((item, index) => {
    const receipt = parsePlanPolicyReceipt(item, index)
    if (seen.has(receipt.receiptId)) fail('套餐策略审计响应包含重复回执编号')
    seen.add(receipt.receiptId)
    return receipt
  })
  return Object.freeze({
    records: Object.freeze(records),
    limit: value.limit,
    truncated: value.truncated
  })
}

export function parsePlanPolicyRevisionPayload(value) {
  assertExactKeys(value, PAYLOAD_KEYS, '套餐策略修订请求')
  if (!isPositiveSafeInteger(value.expectedVersion)) {
    fail('套餐策略预期版本必须是正安全整数')
  }
  const policy = parsePolicyValues(value, '套餐策略修订请求')
  parseIdentifier(value.rollbackOfReceiptId, '补偿目标回执编号', true)
  parseIdentifier(value.idempotencyKey, '套餐策略幂等键')
  return Object.freeze({
    planCode: value.planCode,
    expectedVersion: value.expectedVersion,
    ...policy,
    rollbackOfReceiptId: value.rollbackOfReceiptId,
    idempotencyKey: value.idempotencyKey
  })
}

export function buildPlanPolicyRevisionPayload(input) {
  assertExactKeys(input, BUILD_INPUT_KEYS, '套餐策略修订参数')
  assertExactKeys(input.draft, DRAFT_KEYS, '套餐策略完整替换草稿')
  const catalog = parsePlanPolicyCatalog(input.catalog)
  const current = catalog.find(plan => plan.planCode === input.draft.planCode)
  if (!current || input.draft.expectedVersion !== current.version) {
    fail('套餐策略草稿不是由当前服务器版本完整填充')
  }
  const payload = parsePlanPolicyRevisionPayload({
    planCode: input.draft.planCode,
    expectedVersion: input.draft.expectedVersion,
    planName: input.draft.planName,
    dailyMeetingLimit: input.draft.dailyMeetingLimit,
    agendaLimit: input.draft.agendaLimit,
    seatLimit: input.draft.seatLimit,
    secretaryEnabled: input.draft.secretaryEnabled,
    rollbackOfReceiptId: input.rollbackOfReceiptId,
    idempotencyKey: input.idempotencyKey
  })
  if (policyValuesEqual(current, payload)) fail('套餐策略没有实质变化，不能生成修订回执')

  const candidate = catalog.map(plan => plan.planCode === payload.planCode
    ? { ...plan, ...payload }
    : plan)
  const free = candidate.find(plan => plan.planCode === 'BOARD_FREE')
  const vip = candidate.find(plan => plan.planCode === 'BOARD_VIP')
  requireCatalogInvariants(free, vip)
  return payload
}

export function buildPlanPolicyRollbackPayload(input) {
  assertExactKeys(input, ROLLBACK_INPUT_KEYS, '套餐策略补偿回滚参数')
  const catalog = parsePlanPolicyCatalog(input.catalog)
  const receipt = parsePlanPolicyReceipt(input.receipt)
  const current = catalog.find(plan => plan.planCode === receipt.planCode)
  if (!current) fail('补偿目标回执不属于当前受支持套餐')
  return buildPlanPolicyRevisionPayload({
    catalog,
    draft: {
      planCode: receipt.planCode,
      expectedVersion: current.version,
      planName: receipt.planName,
      dailyMeetingLimit: receipt.dailyMeetingLimit,
      agendaLimit: receipt.agendaLimit,
      seatLimit: receipt.seatLimit,
      secretaryEnabled: receipt.secretaryEnabled
    },
    rollbackOfReceiptId: receipt.receiptId,
    idempotencyKey: input.idempotencyKey
  })
}

export function canRollbackPlanPolicyReceipt(catalog, receipt) {
  try {
    const safeCatalog = parsePlanPolicyCatalog(catalog)
    const safeReceipt = parsePlanPolicyReceipt(receipt)
    const current = safeCatalog.find(plan => plan.planCode === safeReceipt.planCode)
    return Boolean(current && !policyValuesEqual(current, safeReceipt))
  } catch {
    return false
  }
}

export function parsePlanPolicyRevisionResult(value) {
  const receipt = parsePlanPolicyReceipt(value)
  if (receipt.action === 'PLAN_POLICY_BASELINED') {
    fail('套餐策略修订接口不能返回系统基线回执')
  }
  return receipt
}

export function verifyPlanPolicyRevisionResult({ payload, result, actorUserId }) {
  const safePayload = parsePlanPolicyRevisionPayload(payload)
  const safeResult = parsePlanPolicyRevisionResult(result)
  const expectedAction = safePayload.rollbackOfReceiptId === null
    ? 'PLAN_POLICY_REVISED'
    : 'PLAN_POLICY_ROLLED_BACK'
  if (!isPositiveSafeInteger(actorUserId)
      || safeResult.actorUserId !== actorUserId
      || safeResult.planCode !== safePayload.planCode
      || safeResult.policyVersion !== safePayload.expectedVersion + 1
      || safeResult.action !== expectedAction
      || safeResult.rollbackOfReceiptId !== safePayload.rollbackOfReceiptId
      || !policyValuesEqual(safeResult, safePayload)) {
    fail('套餐策略提交回执与请求套餐、版本或完整替换值不一致')
  }
  return safeResult
}

export function reconcilePlanPolicyRevisionCatalog(catalog, result) {
  const safeCatalog = parsePlanPolicyCatalog(catalog)
  const safeResult = parsePlanPolicyRevisionResult(result)
  const observed = safeCatalog.find(plan => plan.planCode === safeResult.planCode)
  if (!observed || observed.version < safeResult.policyVersion) {
    fail('当前套餐 head 版本落后于已提交回执，提交结果仍不明确')
  }
  if (observed.version > safeResult.policyVersion) return 'SUPERSEDED'
  if (!policyValuesEqual(observed, safeResult)) {
    fail('当前套餐 head 与同版本已提交回执发生漂移')
  }
  return 'CURRENT'
}

export function createPlanPolicyIdempotencyKey(
  kind,
  randomUuid = globalThis.crypto?.randomUUID?.bind(globalThis.crypto)
) {
  if (!['revise', 'rollback'].includes(kind) || typeof randomUuid !== 'function') {
    fail('无法生成受支持的套餐策略幂等键')
  }
  const uuid = randomUuid()
  if (typeof uuid !== 'string' || !UUID.test(uuid)) {
    fail('浏览器未生成有效的套餐策略幂等标识')
  }
  return parseIdentifier(`board-plan-${kind}:${uuid.toLowerCase()}`, '套餐策略幂等键')
}

export function createPlanPolicyPendingSnapshot({
  actorUserId,
  payload,
  nowMs = Date.now()
}) {
  return validatePendingSnapshot({
    schema: PENDING_SCHEMA,
    actorUserId,
    payload,
    createdAtMs: nowMs
  }, actorUserId, nowMs)
}

export function parsePlanPolicyPendingSnapshot(raw, expectedActorUserId, nowMs = Date.now()) {
  if (typeof raw !== 'string' || raw.length === 0 || raw.length > 4096) {
    fail('套餐策略未决恢复数据不是有界 JSON')
  }
  let value
  try {
    value = JSON.parse(raw)
  } catch {
    fail('套餐策略未决恢复数据不是有效 JSON')
  }
  return validatePendingSnapshot(value, expectedActorUserId, nowMs)
}

function validatePendingSnapshot(value, expectedActorUserId, nowMs) {
  assertExactKeys(value, PENDING_KEYS, '套餐策略未决恢复数据')
  if (value.schema !== PENDING_SCHEMA
      || !isPositiveSafeInteger(expectedActorUserId)
      || value.actorUserId !== expectedActorUserId
      || !Number.isSafeInteger(value.createdAtMs)
      || !Number.isSafeInteger(nowMs)
      || value.createdAtMs > nowMs + 5000) {
    fail('套餐策略未决恢复数据不属于当前操作者身份或时间边界无效')
  }
  return Object.freeze({
    ...value,
    payload: parsePlanPolicyRevisionPayload(value.payload)
  })
}

export function payloadMatchesPlanPolicyPending(pending, payload) {
  try {
    const safePending = validatePendingSnapshot(
      pending, pending?.actorUserId, Math.max(Date.now(), pending?.createdAtMs || 0))
    const safePayload = parsePlanPolicyRevisionPayload(payload)
    return JSON.stringify(safePending.payload) === JSON.stringify(safePayload)
  } catch {
    return false
  }
}

export function planPolicyPendingStorageKey(actorUserId) {
  if (!isPositiveSafeInteger(actorUserId)) {
    fail('套餐策略未决存储缺少有效操作者身份')
  }
  return `fbsir.independent-board-plan-policy.pending.v1.actor.${actorUserId}`
}

export function isPlanPolicyConflict(error) {
  return error?.response?.status === 409
}

export function isAmbiguousPlanPolicyFailure(error) {
  const status = error?.response?.status
  return !Number.isInteger(status) || status === 408 || status >= 500
}

export function createPlanPolicyFallbackMutex() {
  const queues = new Map()
  return Object.freeze({
    async runExclusive(name, run) {
      if (typeof name !== 'string' || name.length === 0 || typeof run !== 'function') {
        fail('套餐策略降级互斥参数无效')
      }
      const previous = queues.get(name) || Promise.resolve()
      let release
      const current = new Promise(resolve => { release = resolve })
      queues.set(name, current)
      await previous
      try {
        return await run()
      } finally {
        release()
        if (queues.get(name) === current) queues.delete(name)
      }
    }
  })
}

export async function withPlanPolicyMutationLock({
  actorUserId,
  run,
  lockManager = globalThis.navigator?.locks,
  fallbackMutex = null
}) {
  if (!isPositiveSafeInteger(actorUserId) || typeof run !== 'function') {
    fail('套餐策略写入互斥参数无效')
  }
  const name = `fbsir.independent-board-plan-policy.pending.actor.${actorUserId}`
  if (lockManager && typeof lockManager.request === 'function') {
    return lockManager.request(name, { mode: 'exclusive' }, async lock => {
      if (!lock) fail('浏览器未授予套餐策略写入互斥锁')
      return run()
    })
  }
  if (!fallbackMutex || typeof fallbackMutex.runExclusive !== 'function') {
    fail('当前浏览器无法建立套餐策略写入串行降级')
  }
  return fallbackMutex.runExclusive(name, run)
}

export function planPolicyActionLabel(action) {
  const labels = {
    PLAN_POLICY_BASELINED: '系统基线',
    PLAN_POLICY_REVISED: '策略修订',
    PLAN_POLICY_ROLLED_BACK: '补偿回滚'
  }
  return labels[action] || '未知动作'
}

export function formatPlanPolicyDateTime(value) {
  parseDateTime(value, '展示时间')
  const normalized = value.includes('T') || /(?:Z|[+-]\d{2}:\d{2})$/.test(value)
    ? value
    : value.replace(' ', 'T')
  const zoned = /(?:Z|[+-]\d{2}:\d{2})$/.test(normalized)
    ? normalized
    : `${normalized}+08:00`
  return new Intl.DateTimeFormat('zh-CN', {
    timeZone: 'Asia/Shanghai',
    dateStyle: 'medium',
    timeStyle: 'medium'
  }).format(new Date(zoned))
}
