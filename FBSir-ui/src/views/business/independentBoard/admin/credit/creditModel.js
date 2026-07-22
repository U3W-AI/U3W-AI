const ACCOUNT_KEYS = Object.freeze([
  'userId', 'accountScope', 'currencyCode', 'openingBalance', 'balance',
  'version', 'updatedAt', 'records', 'limit', 'truncated'
])
const OPERATION_KEYS = Object.freeze([
  'operationId', 'operationType', 'delta', 'reasonCode', 'actorUserId',
  'reversalOfOperationId', 'balanceBefore', 'balanceAfter', 'sequenceNo', 'createdAt'
])
const COMMAND_RESULT_KEYS = Object.freeze([
  'operationId', 'operationType', 'userId', 'delta', 'balanceAfter',
  'reversalOfOperationId', 'createdAt'
])
const GRANT_INPUT_KEYS = Object.freeze([
  'userId', 'expectedAccountVersion', 'amount', 'reasonCode', 'note', 'idempotencyKey'
])
const REVERSAL_INPUT_KEYS = Object.freeze([
  'operation', 'expectedAccountVersion', 'reasonCode', 'note', 'idempotencyKey'
])
const REVERSAL_PAYLOAD_KEYS = Object.freeze([
  'originalOperationId', 'expectedAccountVersion', 'reasonCode', 'note', 'idempotencyKey'
])
const PENDING_SNAPSHOT_KEYS = Object.freeze([
  'schema', 'actorUserId', 'kind', 'userId', 'payload', 'original', 'createdAtMs'
])
const OPERATION_ID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/
const IDEMPOTENCY_KEY = /^[A-Za-z0-9][A-Za-z0-9._:-]{15,127}$/
const FORBIDDEN_NOTE_CHARACTERS = /[\p{Cc}\p{Cf}]/u
const MAX_BALANCE = 2147483647
const PENDING_SNAPSHOT_SCHEMA = 'fbsir.independent-board-credit-pending/v1'

export const CREDIT_GRANT_REASONS = Object.freeze([
  Object.freeze({ value: 'CUSTOMER_SUPPORT', label: '客户支持调整' }),
  Object.freeze({ value: 'SERVICE_RECOVERY', label: '服务恢复补偿' }),
  Object.freeze({ value: 'MIGRATION_CORRECTION', label: '迁移纠偏' })
])

export const CREDIT_REVERSAL_REASONS = Object.freeze([
  Object.freeze({ value: 'DUPLICATE_GRANT', label: '重复发放' }),
  Object.freeze({ value: 'OPERATOR_ERROR', label: '操作员误操作' }),
  Object.freeze({ value: 'POLICY_VIOLATION', label: '不符合运营政策' })
])

const GRANT_REASON_CODES = new Set(CREDIT_GRANT_REASONS.map(item => item.value))
const REVERSAL_REASON_CODES = new Set(CREDIT_REVERSAL_REASONS.map(item => item.value))

function fail(message) {
  throw new Error(message)
}

function isPlainObject(value) {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
}

function assertExactKeys(value, expected, label) {
  if (!isPlainObject(value)) fail(`${label}不是对象`)
  const actual = Object.keys(value).sort()
  const expectedSorted = [...expected].sort()
  if (actual.length !== expectedSorted.length
      || actual.some((key, index) => key !== expectedSorted[index])) {
    fail(`${label}字段集合不符合安全合同`)
  }
}

function isPositiveSafeInteger(value) {
  return Number.isSafeInteger(value) && value > 0
}

function isNonNegativeSafeInteger(value) {
  return Number.isSafeInteger(value) && value >= 0
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

function parseOperationId(value, label, nullable = false) {
  if (nullable && value === null) return null
  if (typeof value !== 'string' || !OPERATION_ID.test(value)) {
    fail(`${label}格式不正确`)
  }
  return value
}

function parseBalance(value, label) {
  if (!isNonNegativeSafeInteger(value) || value > MAX_BALANCE) {
    fail(`${label}超出安全余额范围`)
  }
  return value
}

function validateNote(value) {
  if (typeof value !== 'string'
      || value.trim() !== value
      || value.length < 8
      || value.length > 128
      || FORBIDDEN_NOTE_CHARACTERS.test(value)) {
    fail('审计备注必须为 8 到 128 个可见字符且不能包含首尾空格')
  }
  return value
}

function validateIdempotencyKey(value) {
  if (typeof value !== 'string' || !IDEMPOTENCY_KEY.test(value)) {
    fail('幂等键格式不符合安全合同')
  }
  return value
}

function parseCreditOperation(value, index = 0) {
  assertExactKeys(value, OPERATION_KEYS, `第${index + 1}条积分操作`)
  parseOperationId(value.operationId, '积分操作编号')
  if (!['GRANT', 'REVERSAL'].includes(value.operationType)
      || !Number.isSafeInteger(value.delta)
      || value.delta === 0
      || !isPositiveSafeInteger(value.actorUserId)
      || !isPositiveSafeInteger(value.sequenceNo)) {
    fail(`第${index + 1}条积分操作包含非法值`)
  }
  parseBalance(value.balanceBefore, '操作前余额')
  parseBalance(value.balanceAfter, '操作后余额')
  if (value.balanceAfter - value.balanceBefore !== value.delta) {
    fail(`第${index + 1}条积分操作余额变化不一致`)
  }
  if (value.operationType === 'GRANT') {
    if (value.delta <= 0
        || !GRANT_REASON_CODES.has(value.reasonCode)
        || value.reversalOfOperationId !== null) {
      fail(`第${index + 1}条发放操作语义不一致`)
    }
  } else if (value.delta >= 0
      || !REVERSAL_REASON_CODES.has(value.reasonCode)
      || parseOperationId(value.reversalOfOperationId, '原发放操作编号', true) === null) {
    fail(`第${index + 1}条冲正操作语义不一致`)
  }
  parseDateTime(value.createdAt, '积分操作创建时间')
  return Object.freeze({ ...value })
}

export function parseCreditAccount(value, expectedUserId) {
  assertExactKeys(value, ACCOUNT_KEYS, '积分账户响应')
  if (!isPositiveSafeInteger(expectedUserId)
      || value.userId !== expectedUserId
      || value.accountScope !== 'USER_GLOBAL'
      || value.currencyCode !== 'FBS_POINTS'
      || !isPositiveSafeInteger(value.version)
      || value.limit !== 100
      || typeof value.truncated !== 'boolean'
      || !Array.isArray(value.records)
      || value.records.length === 0
      || value.records.length > value.limit) {
    fail('积分账户响应边界、用户或固定账户语义不正确')
  }
  parseBalance(value.openingBalance, '开户余额')
  parseBalance(value.balance, '当前余额')
  parseDateTime(value.updatedAt, '积分账户更新时间')
  if (value.truncated
      && (value.version <= value.limit || value.records.length !== value.limit)) {
    fail('积分账户声明截断时版本必须超过上限并返回完整的 100 条安全窗口')
  }
  if (!value.truncated
      && (value.version > value.limit || value.records.length !== value.version)) {
    fail('未截断的积分账户必须返回与版本完全一致的完整操作历史')
  }

  const operationIds = new Set()
  const sequenceNumbers = new Set()
  const records = value.records.map((item, index) => {
    const operation = parseCreditOperation(item, index)
    if (operationIds.has(operation.operationId)
        || sequenceNumbers.has(operation.sequenceNo)) {
      fail('积分账户响应包含重复操作身份')
    }
    operationIds.add(operation.operationId)
    sequenceNumbers.add(operation.sequenceNo)
    if (index > 0) {
      const newer = value.records[index - 1]
      if (newer.sequenceNo !== operation.sequenceNo + 1
          || newer.balanceBefore !== operation.balanceAfter) {
        fail('积分操作窗口顺序或余额链不连续')
      }
    }
    return operation
  })

  if (records[0].sequenceNo !== value.version
      || records[0].balanceAfter !== value.balance) {
    fail('积分账户链头与当前余额或版本不一致')
  }
  if (!value.truncated
      && records[records.length - 1].balanceBefore !== value.openingBalance) {
    fail('完整积分操作窗口与开户余额不一致')
  }

  const recordIndexById = new Map(records.map((record, index) => [record.operationId, index]))
  const reversedGrantIds = new Set()
  records.forEach((record, index) => {
    if (record.operationType !== 'REVERSAL') return
    if (reversedGrantIds.has(record.reversalOfOperationId)) {
      fail('积分操作窗口包含对同一原发放的重复冲正')
    }
    reversedGrantIds.add(record.reversalOfOperationId)
    const originalIndex = recordIndexById.get(record.reversalOfOperationId)
    if (originalIndex === undefined) {
      if (!value.truncated) fail('完整积分操作历史包含找不到原发放的冲正')
      return
    }
    const original = records[originalIndex]
    if (originalIndex <= index
        || original.operationType !== 'GRANT'
        || record.delta !== -original.delta) {
      fail('积分冲正与可见原发放的顺序、类型或金额不一致')
    }
  })

  return Object.freeze({ ...value, records: Object.freeze(records) })
}

export function parseCreditCommandResult(value) {
  assertExactKeys(value, COMMAND_RESULT_KEYS, '积分操作回执')
  parseOperationId(value.operationId, '积分操作回执编号')
  if (!['GRANT', 'REVERSAL'].includes(value.operationType)
      || !isPositiveSafeInteger(value.userId)
      || !Number.isSafeInteger(value.delta)
      || value.delta === 0) {
    fail('积分操作回执包含非法值')
  }
  parseBalance(value.balanceAfter, '积分操作回执余额')
  parseDateTime(value.createdAt, '积分操作回执时间')
  if (value.operationType === 'GRANT') {
    if (value.delta <= 0 || value.reversalOfOperationId !== null) {
      fail('发放回执语义不一致')
    }
  } else if (value.delta >= 0
      || parseOperationId(value.reversalOfOperationId, '回执原发放操作编号', true) === null) {
    fail('冲正回执语义不一致')
  }
  return Object.freeze({ ...value })
}

export function buildCreditGrantPayload(input) {
  assertExactKeys(input, GRANT_INPUT_KEYS, '积分发放参数')
  if (!isPositiveSafeInteger(input.userId)) fail('目标用户 ID 无效')
  if (!isNonNegativeSafeInteger(input.expectedAccountVersion)) {
    fail('积分账户预期版本必须是非负安全整数')
  }
  if (!Number.isSafeInteger(input.amount) || input.amount < 1 || input.amount > 100000) {
    fail('积分发放金额必须为 1 到 100000 的整数')
  }
  if (!GRANT_REASON_CODES.has(input.reasonCode)) fail('积分发放原因不在允许范围')
  return Object.freeze({
    userId: input.userId,
    expectedAccountVersion: input.expectedAccountVersion,
    amount: input.amount,
    reasonCode: input.reasonCode,
    note: validateNote(input.note),
    idempotencyKey: validateIdempotencyKey(input.idempotencyKey)
  })
}

export function buildCreditReversalPayload(input) {
  assertExactKeys(input, REVERSAL_INPUT_KEYS, '积分冲正参数')
  const operation = parseCreditOperation(input.operation)
  if (operation.operationType !== 'GRANT') fail('只能冲正已提交的积分发放操作')
  if (!isNonNegativeSafeInteger(input.expectedAccountVersion)) {
    fail('积分账户预期版本必须是非负安全整数')
  }
  if (!REVERSAL_REASON_CODES.has(input.reasonCode)) fail('积分冲正原因不在允许范围')
  return parseCreditReversalPayload({
    originalOperationId: operation.operationId,
    expectedAccountVersion: input.expectedAccountVersion,
    reasonCode: input.reasonCode,
    note: validateNote(input.note),
    idempotencyKey: validateIdempotencyKey(input.idempotencyKey)
  })
}

function parseCreditReversalPayload(value) {
  assertExactKeys(value, REVERSAL_PAYLOAD_KEYS, '积分冲正请求')
  parseOperationId(value.originalOperationId, '冲正请求原发放操作编号')
  if (!isNonNegativeSafeInteger(value.expectedAccountVersion)) {
    fail('积分账户预期版本必须是非负安全整数')
  }
  if (!REVERSAL_REASON_CODES.has(value.reasonCode)) fail('积分冲正原因不在允许范围')
  return Object.freeze({
    originalOperationId: value.originalOperationId,
    expectedAccountVersion: value.expectedAccountVersion,
    reasonCode: value.reasonCode,
    note: validateNote(value.note),
    idempotencyKey: validateIdempotencyKey(value.idempotencyKey)
  })
}

export function verifyCreditGrantResult({ payload, result }) {
  const safePayload = buildCreditGrantPayload(payload)
  const safeResult = parseCreditCommandResult(result)
  if (safeResult.operationType !== 'GRANT'
      || safeResult.userId !== safePayload.userId
      || safeResult.delta !== safePayload.amount
      || safeResult.reversalOfOperationId !== null) {
    fail('积分发放回执与目标用户或金额不一致')
  }
  return safeResult
}

export function verifyCreditReversalResult({ userId, original, payload, result }) {
  if (!isPositiveSafeInteger(userId)) fail('积分冲正目标用户无效')
  const safeOriginal = parseCreditOperation(original)
  const safePayload = parseCreditReversalPayload(payload)
  const safeResult = parseCreditCommandResult(result)
  if (safePayload.originalOperationId !== safeOriginal.operationId
      || safeResult.operationType !== 'REVERSAL'
      || safeResult.userId !== userId
      || safeResult.delta !== -safeOriginal.delta
      || safeResult.reversalOfOperationId !== safePayload.originalOperationId) {
    fail('积分冲正回执与原发放用户或金额不一致')
  }
  return safeResult
}

export function isCreditOperationReversed(operation, records) {
  const safeOperation = parseCreditOperation(operation)
  if (safeOperation.operationType !== 'GRANT' || !Array.isArray(records)) return false
  return records.some(item => isPlainObject(item)
    && item.operationType === 'REVERSAL'
    && item.reversalOfOperationId === safeOperation.operationId)
}

export function createCreditIdempotencyKey(kind, randomUuid = globalThis.crypto?.randomUUID?.bind(globalThis.crypto)) {
  if (!['grant', 'reverse'].includes(kind) || typeof randomUuid !== 'function') {
    fail('无法生成受支持的积分幂等键')
  }
  const uuid = randomUuid()
  if (typeof uuid !== 'string' || !OPERATION_ID.test(uuid.toLowerCase())) {
    fail('浏览器未生成有效的积分幂等键')
  }
  return validateIdempotencyKey(`credit-${kind}:${uuid.toLowerCase()}`)
}

export function createCreditPendingSnapshot({
  actorUserId, kind, userId, payload, original = null, nowMs = Date.now()
}) {
  return validateCreditPendingSnapshot({
    schema: PENDING_SNAPSHOT_SCHEMA,
    actorUserId,
    kind,
    userId,
    payload,
    original,
    createdAtMs: nowMs
  }, actorUserId, nowMs)
}

export function parseCreditPendingSnapshot(raw, expectedActorUserId, nowMs = Date.now()) {
  if (typeof raw !== 'string' || raw.length === 0 || raw.length > 4096) {
    fail('积分未决恢复数据不是有界 JSON')
  }
  let value
  try {
    value = JSON.parse(raw)
  } catch {
    fail('积分未决恢复数据不是有效 JSON')
  }
  return validateCreditPendingSnapshot(value, expectedActorUserId, nowMs)
}

function validateCreditPendingSnapshot(value, expectedActorUserId, nowMs) {
  assertExactKeys(value, PENDING_SNAPSHOT_KEYS, '积分未决恢复数据')
  if (value.schema !== PENDING_SNAPSHOT_SCHEMA
      || !isPositiveSafeInteger(expectedActorUserId)
      || value.actorUserId !== expectedActorUserId
      || !isPositiveSafeInteger(value.userId)
      || !['grant', 'reverse'].includes(value.kind)
      || !Number.isSafeInteger(value.createdAtMs)
      || !Number.isSafeInteger(nowMs)
      || value.createdAtMs > nowMs + 5000) {
    fail('积分未决恢复数据不属于当前操作人或时间边界无效')
  }
  if (value.kind === 'grant') {
    if (value.original !== null) fail('积分发放恢复数据不能包含原操作')
    const safePayload = buildCreditGrantPayload(value.payload)
    if (safePayload.userId !== value.userId) fail('积分发放恢复用户不一致')
    return Object.freeze({ ...value, payload: safePayload, original: null })
  }
  const safeOriginal = parseCreditOperation(value.original)
  const safePayload = parseCreditReversalPayload(value.payload)
  if (safePayload.originalOperationId !== safeOriginal.operationId) {
    fail('积分冲正恢复请求与原发放不一致')
  }
  return Object.freeze({
    ...value,
    payload: safePayload,
    original: safeOriginal
  })
}

export function isAmbiguousCreditFailure(error) {
  const status = error?.response?.status
  return !Number.isInteger(status) || status === 408 || status >= 500
}

export function creditPendingStorageKey(actorUserId) {
  if (!isPositiveSafeInteger(actorUserId)) {
    fail('积分未决存储缺少有效操作人')
  }
  return `fbsir.independent-board-credit.pending.v1.actor.${actorUserId}`
}

export async function withCreditAccountMutationLock({
  actorUserId,
  run,
  lockManager = globalThis.navigator?.locks
}) {
  if (!isPositiveSafeInteger(actorUserId)
      || typeof run !== 'function') {
    fail('积分账户互斥参数无效')
  }
  if (!lockManager || typeof lockManager.request !== 'function') {
    fail('当前浏览器不支持安全的跨标签积分互斥')
  }
  // Pending recovery is one actor-scoped slot, so every account mutation by
  // the same administrator must share this lock until readback and cleanup.
  const name = `fbsir.independent-board-credit.pending.actor.${actorUserId}`
  return lockManager.request(name, { mode: 'exclusive' }, async lock => {
    if (!lock) fail('浏览器未授予积分账户互斥锁')
    return run()
  })
}

export function isCreditAccountNotFound(error) {
  return error?.response?.status === 404
    && error?.response?.data?.code === 404
    && error?.response?.data?.msg === 'CREDIT_ACCOUNT_NOT_FOUND'
}

export function isCreditAccountVersionConflict(error) {
  return error?.response?.status === 409
    && error?.response?.data?.code === 409
    && error?.response?.data?.msg === 'CREDIT_ACCOUNT_VERSION_CONFLICT'
}

export function creditReasonLabel(reasonCode) {
  return [...CREDIT_GRANT_REASONS, ...CREDIT_REVERSAL_REASONS]
    .find(item => item.value === reasonCode)?.label || '未知原因'
}

export function formatCreditDateTime(value) {
  parseDateTime(value, '展示时间')
  return new Intl.DateTimeFormat('zh-CN', {
    timeZone: 'Asia/Shanghai',
    dateStyle: 'medium',
    timeStyle: 'medium'
  }).format(new Date(value))
}
