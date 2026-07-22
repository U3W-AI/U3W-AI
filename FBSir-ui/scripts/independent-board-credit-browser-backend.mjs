import crypto from 'node:crypto'
import http from 'node:http'

const HOST = '127.0.0.1'
const PORT = 18086
const MAX_BODY_BYTES = 16 * 1024
const MAX_REQUEST_LOG = 500
const requestLog = []
const idempotencyReceipts = new Map()
const accounts = new Map()
let failNextMutationAfterCommit = false

const route = {
  name: 'IndependentBoardAdmin',
  path: '/independent-board-admin',
  component: 'Layout',
  alwaysShow: true,
  redirect: 'noRedirect',
  meta: { title: '独董会管理', icon: 'peoples' },
  children: [{
    name: 'IndependentBoardCreditGovernance',
    path: 'credit-ledger',
    component: 'business/independentBoard/admin/credit/index',
    meta: { title: '积分账本', icon: 'money' }
  }]
}

const initialGrant = Object.freeze({
  operationId: '11111111-1111-4111-8111-111111111111',
  operationType: 'GRANT',
  delta: 250,
  reasonCode: 'CUSTOMER_SUPPORT',
  actorUserId: 1,
  reversalOfOperationId: null,
  balanceBefore: 1000,
  balanceAfter: 1250,
  sequenceNo: 1,
  createdAt: '2026-07-22T08:00:00.000Z'
})
accounts.set(42, {
  userId: 42,
  accountScope: 'USER_GLOBAL',
  currencyCode: 'FBS_POINTS',
  openingBalance: 1000,
  balance: 1250,
  version: 1,
  updatedAt: initialGrant.createdAt,
  records: [initialGrant],
  limit: 100,
  truncated: false
})

function respondJson(response, statusCode, payload) {
  const body = JSON.stringify(payload)
  response.writeHead(statusCode, {
    'Content-Type': 'application/json; charset=utf-8',
    'Content-Length': Buffer.byteLength(body),
    'Cache-Control': 'no-store',
    Pragma: 'no-cache',
    Expires: '0'
  })
  response.end(body)
}

function success(response, data = null) {
  respondJson(response, 200, { code: 200, msg: '操作成功', data })
}

function failure(response, status, message) {
  respondJson(response, status, { code: status, msg: message })
}

function exactKeys(value, expected) {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return false
  const actual = Object.keys(value).sort()
  const safeExpected = [...expected].sort()
  return actual.length === safeExpected.length
    && actual.every((key, index) => key === safeExpected[index])
}

function validIdempotencyKey(value) {
  return typeof value === 'string'
    && /^[A-Za-z0-9][A-Za-z0-9._:-]{15,127}$/.test(value)
}

function validNote(value) {
  return typeof value === 'string'
    && value.trim() === value
    && value.length >= 8
    && value.length <= 128
    && !/[\p{Cc}\p{Cf}]/u.test(value)
}

function stableDigest(payload) {
  const { expectedAccountVersion: ignoredPrecondition, ...command } = payload
  void ignoredPrecondition
  return crypto.createHash('sha256').update(JSON.stringify(command)).digest('hex')
}

function accountView(account) {
  return {
    ...account,
    records: account.records.slice(0, 100).map(record => ({ ...record })),
    truncated: account.records.length > 100
  }
}

function commandResult(userId, operation) {
  return {
    operationId: operation.operationId,
    operationType: operation.operationType,
    userId,
    delta: operation.delta,
    balanceAfter: operation.balanceAfter,
    reversalOfOperationId: operation.reversalOfOperationId,
    createdAt: operation.createdAt
  }
}

function findGrant(operationId) {
  for (const account of accounts.values()) {
    const operation = account.records.find(record => record.operationId === operationId)
    if (operation?.operationType === 'GRANT') return { account, operation }
  }
  return null
}

function recordRequest(request, url, body = null) {
  if (!url.pathname.startsWith('/business/independent-board')) return
  requestLog.push({
    at: new Date().toISOString(),
    method: request.method,
    path: url.pathname,
    bodyKeys: body && typeof body === 'object' ? Object.keys(body).sort() : [],
    expectedAccountVersion: Number.isSafeInteger(body?.expectedAccountVersion)
      ? body.expectedAccountVersion
      : null,
    userId: Number.isSafeInteger(body?.userId) ? body.userId : null,
    originalOperationId: typeof body?.originalOperationId === 'string'
      ? body.originalOperationId
      : null,
    idempotencyKey: typeof body?.idempotencyKey === 'string'
      ? body.idempotencyKey
      : null
  })
  if (requestLog.length > MAX_REQUEST_LOG) requestLog.shift()
}

async function readJson(request) {
  const chunks = []
  let length = 0
  for await (const chunk of request) {
    length += chunk.length
    if (length > MAX_BODY_BYTES) throw new Error('BODY_TOO_LARGE')
    chunks.push(chunk)
  }
  const text = Buffer.concat(chunks).toString('utf8')
  return text ? JSON.parse(text) : {}
}

function idempotentReceipt(payload) {
  const existing = idempotencyReceipts.get(payload.idempotencyKey)
  if (!existing) return null
  return existing.digest === stableDigest(payload) ? existing : false
}

function rememberReceipt(payload, userId, operation) {
  const receipt = Object.freeze({
    digest: stableDigest(payload),
    result: Object.freeze(commandResult(userId, operation))
  })
  idempotencyReceipts.set(payload.idempotencyKey, receipt)
  return receipt
}

function maybeFailAfterCommit(response, receipt) {
  if (!failNextMutationAfterCommit) {
    success(response, receipt.result)
    return
  }
  failNextMutationAfterCommit = false
  failure(response, 503, 'CREDIT_FIXTURE_AMBIGUOUS_AFTER_COMMIT')
}

async function handleGrant(request, response, url) {
  let payload
  try {
    payload = await readJson(request)
  } catch {
    failure(response, 400, 'CREDIT_REQUEST_INVALID')
    return
  }
  recordRequest(request, url, payload)
  if (!exactKeys(payload, [
    'userId', 'expectedAccountVersion', 'amount', 'reasonCode', 'note', 'idempotencyKey'
  ])
      || !Number.isSafeInteger(payload.userId) || payload.userId <= 0
      || !Number.isSafeInteger(payload.expectedAccountVersion)
      || payload.expectedAccountVersion < 0
      || !Number.isSafeInteger(payload.amount) || payload.amount < 1 || payload.amount > 100000
      || !['CUSTOMER_SUPPORT', 'SERVICE_RECOVERY', 'MIGRATION_CORRECTION'].includes(payload.reasonCode)
      || !validNote(payload.note) || !validIdempotencyKey(payload.idempotencyKey)) {
    failure(response, 400, 'CREDIT_REQUEST_INVALID')
    return
  }
  const replay = idempotentReceipt(payload)
  if (replay === false) {
    failure(response, 409, 'CREDIT_IDEMPOTENCY_CONFLICT')
    return
  }
  if (replay) {
    success(response, replay.result)
    return
  }

  const account = accounts.get(payload.userId) || {
    userId: payload.userId,
    accountScope: 'USER_GLOBAL',
    currencyCode: 'FBS_POINTS',
    openingBalance: 100,
    balance: 100,
    version: 0,
    updatedAt: null,
    records: [],
    limit: 100,
    truncated: false
  }
  if (payload.expectedAccountVersion !== account.version) {
    failure(response, 409, 'CREDIT_ACCOUNT_VERSION_CONFLICT')
    return
  }
  if (account.balance + payload.amount > 2147483647) {
    failure(response, 409, 'CREDIT_BALANCE_OVERFLOW')
    return
  }
  const createdAt = new Date().toISOString()
  const operation = Object.freeze({
    operationId: crypto.randomUUID(),
    operationType: 'GRANT',
    delta: payload.amount,
    reasonCode: payload.reasonCode,
    actorUserId: 1,
    reversalOfOperationId: null,
    balanceBefore: account.balance,
    balanceAfter: account.balance + payload.amount,
    sequenceNo: account.version + 1,
    createdAt
  })
  account.balance = operation.balanceAfter
  account.version = operation.sequenceNo
  account.updatedAt = createdAt
  account.records.unshift(operation)
  accounts.set(payload.userId, account)
  maybeFailAfterCommit(response, rememberReceipt(payload, payload.userId, operation))
}

async function handleReversal(request, response, url) {
  let payload
  try {
    payload = await readJson(request)
  } catch {
    failure(response, 400, 'CREDIT_REQUEST_INVALID')
    return
  }
  recordRequest(request, url, payload)
  if (!exactKeys(payload, [
    'originalOperationId', 'expectedAccountVersion', 'reasonCode', 'note', 'idempotencyKey'
  ])
      || typeof payload.originalOperationId !== 'string'
      || !Number.isSafeInteger(payload.expectedAccountVersion)
      || payload.expectedAccountVersion < 0
      || !['DUPLICATE_GRANT', 'OPERATOR_ERROR', 'POLICY_VIOLATION'].includes(payload.reasonCode)
      || !validNote(payload.note) || !validIdempotencyKey(payload.idempotencyKey)) {
    failure(response, 400, 'CREDIT_REQUEST_INVALID')
    return
  }
  const replay = idempotentReceipt(payload)
  if (replay === false) {
    failure(response, 409, 'CREDIT_IDEMPOTENCY_CONFLICT')
    return
  }
  if (replay) {
    success(response, replay.result)
    return
  }
  const found = findGrant(payload.originalOperationId)
  if (!found) {
    failure(response, 404, 'CREDIT_ORIGINAL_OPERATION_NOT_FOUND')
    return
  }
  const { account, operation: original } = found
  if (payload.expectedAccountVersion !== account.version) {
    failure(response, 409, 'CREDIT_ACCOUNT_VERSION_CONFLICT')
    return
  }
  if (account.records.some(item => item.operationType === 'REVERSAL'
      && item.reversalOfOperationId === original.operationId)) {
    failure(response, 409, 'CREDIT_OPERATION_ALREADY_REVERSED')
    return
  }
  const createdAt = new Date().toISOString()
  const operation = Object.freeze({
    operationId: crypto.randomUUID(),
    operationType: 'REVERSAL',
    delta: -original.delta,
    reasonCode: payload.reasonCode,
    actorUserId: 1,
    reversalOfOperationId: original.operationId,
    balanceBefore: account.balance,
    balanceAfter: account.balance - original.delta,
    sequenceNo: account.version + 1,
    createdAt
  })
  if (operation.balanceAfter < 0) {
    failure(response, 409, 'CREDIT_BALANCE_UNDERFLOW')
    return
  }
  account.balance = operation.balanceAfter
  account.version = operation.sequenceNo
  account.updatedAt = createdAt
  account.records.unshift(operation)
  maybeFailAfterCommit(response, rememberReceipt(payload, account.userId, operation))
}

const server = http.createServer(async (request, response) => {
  const url = new URL(request.url, `http://${HOST}:${PORT}`)

  if (url.pathname === '/__fixture/state' && request.method === 'GET') {
    success(response, {
      requests: requestLog,
      accounts: [...accounts.values()].map(account => accountView(account)),
      pendingFailure: failNextMutationAfterCommit
    })
    return
  }
  if (url.pathname === '/__fixture/fail-next-after-commit' && request.method === 'POST') {
    failNextMutationAfterCommit = true
    success(response, { armed: true })
    return
  }
  if (url.pathname === '/captchaImage' && request.method === 'GET') {
    respondJson(response, 200, { code: 200, captchaEnabled: false, uuid: 'w3g-credit-fixture' })
    return
  }
  if (url.pathname === '/login' && request.method === 'POST') {
    respondJson(response, 200, { code: 200, token: 'w3g-credit-local-fixture-session' })
    return
  }
  if (url.pathname === '/logout' && request.method === 'POST') {
    respondJson(response, 200, { code: 200 })
    return
  }
  if (url.pathname === '/getInfo' && request.method === 'GET') {
    respondJson(response, 200, {
      code: 200,
      user: {
        userId: 1,
        userName: 'admin',
        nickName: 'W3g 本地积分管理员',
        avatar: '',
        hostId: 'credit-fixture-host'
      },
      roles: ['admin'],
      permissions: ['board:credit:query', 'board:credit:grant', 'board:credit:reverse'],
      isDefaultModifyPwd: false,
      isPasswordExpired: false
    })
    return
  }
  if (url.pathname === '/getRouters' && request.method === 'GET') {
    respondJson(response, 200, { code: 200, data: [route] })
    return
  }

  const accountMatch = url.pathname.match(/^\/business\/independent-board\/credit-accounts\/(\d+)$/)
  if (accountMatch && request.method === 'GET') {
    recordRequest(request, url)
    const userId = Number(accountMatch[1])
    if (userId === 4040) {
      failure(response, 404, 'NOT_FOUND')
      return
    }
    if (userId === 5000) {
      success(response, { ...accountView(accounts.get(42)), userId, internalAccountId: 7 })
      return
    }
    const account = accounts.get(userId)
    if (!account) {
      failure(response, 404, 'CREDIT_ACCOUNT_NOT_FOUND')
      return
    }
    success(response, accountView(account))
    return
  }
  if (url.pathname === '/business/independent-board/credit-operations/grants'
      && request.method === 'POST') {
    await handleGrant(request, response, url)
    return
  }
  if (url.pathname === '/business/independent-board/credit-operations/reversals'
      && request.method === 'POST') {
    await handleReversal(request, response, url)
    return
  }

  failure(response, 404, 'NOT_FOUND')
})

server.listen(PORT, HOST, () => {
  process.stdout.write(`W3g credit browser fixture listening on http://${HOST}:${PORT}\n`)
})

for (const signal of ['SIGINT', 'SIGTERM']) {
  process.on(signal, () => server.close(() => process.exit(0)))
}
