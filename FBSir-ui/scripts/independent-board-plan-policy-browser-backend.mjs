import crypto from 'node:crypto'
import http from 'node:http'

const defaultOff = process.argv.includes('--default-off')
const HOST = '127.0.0.1'
const PORT = defaultOff ? 18094 : 18092
const MAX_BODY_BYTES = 16 * 1024
const requestLog = []
const receiptsByKey = new Map()
let failNextAfterCommit = false

const route = {
  name: 'IndependentBoardAdmin',
  path: '/independent-board-admin',
  component: 'Layout',
  alwaysShow: true,
  redirect: 'noRedirect',
  meta: { title: '独董会管理', icon: 'peoples' },
  children: [{
    name: 'IndependentBoardEntitlementGovernance',
    path: 'entitlements',
    component: 'business/independentBoard/admin/entitlement/index',
    meta: { title: '产品与权益', icon: 'user' }
  }]
}

const plans = [
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
    version: 1,
    updatedAt: '2026-07-22T08:00:00.000+08:00'
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
    version: 1,
    updatedAt: '2026-07-22T08:00:00.000+08:00'
  }
]

const receipts = plans.map((plan, index) => ({
  receiptId: `plan-policy-baseline-${plan.planCode.toLowerCase()}-v1`,
  planCode: plan.planCode,
  policyVersion: 1,
  previousReceiptId: null,
  rollbackOfReceiptId: null,
  action: 'PLAN_POLICY_BASELINED',
  actorType: 'SYSTEM_MIGRATION',
  actorUserId: null,
  planName: plan.planName,
  dailyMeetingLimit: plan.dailyMeetingLimit,
  agendaLimit: plan.agendaLimit,
  seatLimit: plan.seatLimit,
  secretaryEnabled: plan.secretaryEnabled,
  previousPolicyDigest: null,
  policyDigest: String(index + 1).repeat(64),
  evidenceLevel: 'ACTION_COMPLETED',
  createdAt: '2026-07-22T08:00:00.000+08:00'
}))

function respondJson(response, status, payload) {
  const body = JSON.stringify(payload)
  response.writeHead(status, {
    'Content-Type': 'application/json; charset=utf-8',
    'Content-Length': Buffer.byteLength(body),
    'Cache-Control': 'no-store, no-cache, must-revalidate, max-age=0',
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

function digest(value) {
  return crypto.createHash('sha256').update(JSON.stringify(value)).digest('hex')
}

function commandDigest(payload) {
  const { idempotencyKey: ignored, ...command } = payload
  void ignored
  return digest(command)
}

function recordRequest(request, url, body = null) {
  requestLog.push({
    at: new Date().toISOString(),
    method: request.method,
    path: url.pathname,
    bodyKeys: body && typeof body === 'object' ? Object.keys(body).sort() : []
  })
  if (requestLog.length > 200) requestLog.shift()
}

async function readJson(request) {
  const chunks = []
  let length = 0
  for await (const chunk of request) {
    length += chunk.length
    if (length > MAX_BODY_BYTES) throw new Error('BODY_TOO_LARGE')
    chunks.push(chunk)
  }
  return JSON.parse(Buffer.concat(chunks).toString('utf8') || '{}')
}

function validRevision(payload) {
  return exactKeys(payload, [
    'planCode', 'expectedVersion', 'planName', 'dailyMeetingLimit',
    'agendaLimit', 'seatLimit', 'secretaryEnabled',
    'rollbackOfReceiptId', 'idempotencyKey'
  ])
    && ['BOARD_FREE', 'BOARD_VIP'].includes(payload.planCode)
    && Number.isSafeInteger(payload.expectedVersion) && payload.expectedVersion >= 1
    && typeof payload.planName === 'string'
    && payload.planName.trim() === payload.planName
    && [...payload.planName].length >= 1 && [...payload.planName].length <= 128
    && !/[\p{Cc}\p{Cf}\p{Cs}]/u.test(payload.planName)
    && Number.isSafeInteger(payload.dailyMeetingLimit)
    && payload.dailyMeetingLimit >= 1 && payload.dailyMeetingLimit <= 10000
    && Number.isSafeInteger(payload.agendaLimit)
    && payload.agendaLimit >= 1 && payload.agendaLimit <= 30
    && (payload.seatLimit === null || (Number.isSafeInteger(payload.seatLimit)
      && payload.seatLimit >= 1 && payload.seatLimit <= 100))
    && (payload.planCode !== 'BOARD_FREE' || payload.seatLimit !== null)
    && typeof payload.secretaryEnabled === 'boolean'
    && (payload.rollbackOfReceiptId === null
      || /^[A-Za-z0-9][A-Za-z0-9._:-]{15,127}$/.test(payload.rollbackOfReceiptId))
    && typeof payload.idempotencyKey === 'string'
    && /^[A-Za-z0-9][A-Za-z0-9._:-]{15,127}$/.test(payload.idempotencyKey)
}

function samePolicy(left, right) {
  return left.planName === right.planName
    && left.dailyMeetingLimit === right.dailyMeetingLimit
    && left.agendaLimit === right.agendaLimit
    && left.seatLimit === right.seatLimit
    && left.secretaryEnabled === right.secretaryEnabled
}

function catalogInvariant(candidate, other) {
  const free = candidate.planCode === 'BOARD_FREE' ? candidate : other
  const vip = candidate.planCode === 'BOARD_VIP' ? candidate : other
  return vip.dailyMeetingLimit >= free.dailyMeetingLimit
    && vip.agendaLimit >= free.agendaLimit
    && (vip.seatLimit === null || vip.seatLimit >= free.seatLimit)
    && (!free.secretaryEnabled || vip.secretaryEnabled)
}

async function revisePlan(request, response, url) {
  let payload
  try {
    payload = await readJson(request)
  } catch {
    failure(response, 400, 'BOARD_PLAN_POLICY_REQUEST_INVALID')
    return
  }
  recordRequest(request, url, payload)
  if (!validRevision(payload)) {
    failure(response, 400, 'BOARD_PLAN_POLICY_REQUEST_INVALID')
    return
  }

  const existing = receiptsByKey.get(payload.idempotencyKey)
  if (existing) {
    if (existing.commandDigest !== commandDigest(payload)) {
      failure(response, 409, 'BOARD_PLAN_POLICY_IDEMPOTENCY_CONFLICT')
    } else {
      success(response, existing.receipt)
    }
    return
  }

  const plan = plans.find(item => item.planCode === payload.planCode)
  const other = plans.find(item => item.planCode !== payload.planCode)
  if (plan.version !== payload.expectedVersion) {
    failure(response, 409, 'BOARD_PLAN_POLICY_VERSION_CONFLICT')
    return
  }
  if (samePolicy(plan, payload)) {
    failure(response, 409, 'BOARD_PLAN_POLICY_NO_CHANGE')
    return
  }
  const rollbackTarget = payload.rollbackOfReceiptId === null
    ? null
    : receipts.find(item => item.receiptId === payload.rollbackOfReceiptId
      && item.planCode === payload.planCode)
  if (payload.rollbackOfReceiptId !== null
      && (!rollbackTarget || !samePolicy(rollbackTarget, payload))) {
    failure(response, 409, 'BOARD_PLAN_POLICY_ROLLBACK_TARGET_MISMATCH')
    return
  }
  if (!catalogInvariant(payload, other)) {
    failure(response, 409, 'BOARD_PLAN_POLICY_CATALOG_INVARIANT')
    return
  }

  const previous = receipts.find(item => item.planCode === plan.planCode
    && item.policyVersion === plan.version)
  const now = new Date().toISOString()
  const receipt = {
    receiptId: crypto.randomUUID(),
    planCode: plan.planCode,
    policyVersion: plan.version + 1,
    previousReceiptId: previous.receiptId,
    rollbackOfReceiptId: rollbackTarget?.receiptId || null,
    action: rollbackTarget ? 'PLAN_POLICY_ROLLED_BACK' : 'PLAN_POLICY_REVISED',
    actorType: 'ADMIN_USER',
    actorUserId: 1,
    planName: payload.planName,
    dailyMeetingLimit: payload.dailyMeetingLimit,
    agendaLimit: payload.agendaLimit,
    seatLimit: payload.seatLimit,
    secretaryEnabled: payload.secretaryEnabled,
    previousPolicyDigest: previous.policyDigest,
    policyDigest: digest({ previous: previous.policyDigest, payload }),
    evidenceLevel: 'ACTION_COMPLETED',
    createdAt: now
  }
  receipts.unshift(receipt)
  Object.assign(plan, {
    planName: receipt.planName,
    dailyMeetingLimit: receipt.dailyMeetingLimit,
    agendaLimit: receipt.agendaLimit,
    seatLimit: receipt.seatLimit,
    secretaryEnabled: receipt.secretaryEnabled,
    version: receipt.policyVersion,
    updatedAt: now
  })
  receiptsByKey.set(payload.idempotencyKey, {
    commandDigest: commandDigest(payload),
    receipt
  })

  if (failNextAfterCommit) {
    failNextAfterCommit = false
    failure(response, 503, 'BOARD_PLAN_POLICY_AMBIGUOUS_AFTER_COMMIT')
    return
  }
  success(response, receipt)
}

function advanceVipOutOfBand() {
  const plan = plans.find(item => item.planCode === 'BOARD_VIP')
  const previous = receipts.find(item => item.planCode === plan.planCode
    && item.policyVersion === plan.version)
  const now = new Date().toISOString()
  const receipt = {
    receiptId: `fixture-oob-${crypto.randomUUID()}`,
    planCode: plan.planCode,
    policyVersion: plan.version + 1,
    previousReceiptId: previous.receiptId,
    rollbackOfReceiptId: null,
    action: 'PLAN_POLICY_REVISED',
    actorType: 'ADMIN_USER',
    actorUserId: 99,
    planName: plan.planName,
    dailyMeetingLimit: plan.dailyMeetingLimit + 1,
    agendaLimit: plan.agendaLimit,
    seatLimit: plan.seatLimit,
    secretaryEnabled: plan.secretaryEnabled,
    previousPolicyDigest: previous.policyDigest,
    policyDigest: digest({ previous: previous.policyDigest, source: 'fixture-oob' }),
    evidenceLevel: 'ACTION_COMPLETED',
    createdAt: now
  }
  receipts.unshift(receipt)
  Object.assign(plan, {
    dailyMeetingLimit: receipt.dailyMeetingLimit,
    version: receipt.policyVersion,
    updatedAt: now
  })
  return receipt
}

const server = http.createServer(async (request, response) => {
  const url = new URL(request.url, `http://${HOST}:${PORT}`)

  if (url.pathname === '/__fixture/state' && request.method === 'GET') {
    success(response, { defaultOff, plans, receipts, requests: requestLog })
    return
  }
  if (url.pathname === '/__fixture/fail-next-after-commit'
      && request.method === 'POST') {
    failNextAfterCommit = true
    success(response, { armed: true })
    return
  }
  if (url.pathname === '/__fixture/advance-vip'
      && request.method === 'POST') {
    success(response, advanceVipOutOfBand())
    return
  }
  if (url.pathname === '/captchaImage' && request.method === 'GET') {
    respondJson(response, 200, { code: 200, captchaEnabled: false, uuid: 'w3h-plan-fixture' })
    return
  }
  if (url.pathname === '/login' && request.method === 'POST') {
    respondJson(response, 200, { code: 200, token: 'w3h-plan-local-fixture-session' })
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
        nickName: 'W3h 本地策略管理员',
        avatar: '',
        hostId: 'plan-policy-fixture-host'
      },
      roles: ['admin'],
      permissions: [
        'business:fbs:enterprise:list',
        'business:fbs:enterpriseMember:list',
        'board:entitlement:query',
        'board:plan:revise',
        'board:plan:audit'
      ],
      isDefaultModifyPwd: false,
      isPasswordExpired: false
    })
    return
  }
  if (url.pathname === '/getRouters' && request.method === 'GET') {
    respondJson(response, 200, { code: 200, data: [route] })
    return
  }
  if (url.pathname === '/business/fbs/enterprise/list' && request.method === 'GET') {
    respondJson(response, 200, {
      code: 200,
      rows: [{ id: 1001, enterpriseName: '独董会本地验证企业', status: 1 }],
      total: 1
    })
    return
  }
  if (url.pathname === '/business/fbs/enterprise/member/list' && request.method === 'GET') {
    respondJson(response, 200, { code: 200, rows: [], total: 0 })
    return
  }
  if (url.pathname === '/business/independent-board/entitlements'
      && request.method === 'GET') {
    recordRequest(request, url)
    success(response, [])
    return
  }
  if (url.pathname === '/business/independent-board/plans' && request.method === 'GET') {
    recordRequest(request, url)
    success(response, plans)
    return
  }
  if (url.pathname === '/business/independent-board/plan-policy-receipts'
      && request.method === 'GET') {
    recordRequest(request, url)
    if (defaultOff) failure(response, 404, 'NOT_FOUND')
    else success(response, { records: receipts.slice(0, 100), limit: 100, truncated: false })
    return
  }
  if (url.pathname === '/business/independent-board/plan-policy-revisions'
      && request.method === 'POST') {
    if (defaultOff) {
      recordRequest(request, url)
      failure(response, 404, 'NOT_FOUND')
    } else {
      await revisePlan(request, response, url)
    }
    return
  }

  failure(response, 404, 'NOT_FOUND')
})

server.listen(PORT, HOST, () => {
  process.stdout.write(`W3h plan-policy browser fixture listening on http://${HOST}:${PORT} (${defaultOff ? 'default-off' : 'candidate'})\n`)
})

for (const signal of ['SIGINT', 'SIGTERM']) {
  process.on(signal, () => server.close(() => process.exit(0)))
}
