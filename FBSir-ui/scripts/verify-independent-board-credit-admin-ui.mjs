import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import {
  buildCreditGrantPayload,
  buildCreditReversalPayload,
  creditPendingStorageKey,
  createCreditPendingSnapshot,
  createCreditIdempotencyKey,
  isAmbiguousCreditFailure,
  isCreditAccountNotFound,
  isCreditOperationReversed,
  parseCreditAccount,
  parseCreditCommandResult,
  parseCreditPendingSnapshot,
  withCreditAccountMutationLock,
  verifyCreditGrantResult,
  verifyCreditReversalResult
} from '../src/views/business/independentBoard/admin/credit/creditModel.js'
import {
  admitIndependentBoardPortalCandidateRoutes,
  isBoardCreditCandidateEnabled
} from '../src/utils/independentBoardPortalCandidate.js'

const userId = 99
const grantOperation = Object.freeze({
  operationId: '11111111-1111-4111-8111-111111111111',
  operationType: 'GRANT',
  delta: 250,
  reasonCode: 'CUSTOMER_SUPPORT',
  actorUserId: 1,
  reversalOfOperationId: null,
  balanceBefore: 100,
  balanceAfter: 350,
  sequenceNo: 1,
  createdAt: '2026-07-22T13:00:00+08:00'
})
const reversalOperation = Object.freeze({
  operationId: '22222222-2222-4222-8222-222222222222',
  operationType: 'REVERSAL',
  delta: -250,
  reasonCode: 'OPERATOR_ERROR',
  actorUserId: 1,
  reversalOfOperationId: grantOperation.operationId,
  balanceBefore: 350,
  balanceAfter: 100,
  sequenceNo: 2,
  createdAt: '2026-07-22T13:01:00+08:00'
})
const accountPayload = Object.freeze({
  userId,
  accountScope: 'USER_GLOBAL',
  currencyCode: 'FBS_POINTS',
  openingBalance: 100,
  balance: 100,
  version: 2,
  updatedAt: '2026-07-22T13:01:00+08:00',
  records: [reversalOperation, grantOperation],
  limit: 100,
  truncated: false
})

const account = parseCreditAccount(accountPayload, userId)
assert.deepEqual(account, accountPayload)
assert.equal(Object.isFrozen(account), true)
assert.equal(Object.isFrozen(account.records), true)
assert.equal(isCreditOperationReversed(grantOperation, account.records), true)
assert.equal(isCreditOperationReversed(reversalOperation, account.records), false)

for (const unsafeAccount of [
  { ...accountPayload, userId: 100 },
  { ...accountPayload, accountScope: 'TENANT' },
  { ...accountPayload, currencyCode: 'USD' },
  { ...accountPayload, balance: -1 },
  { ...accountPayload, version: 3 },
  { ...accountPayload, limit: 101 },
  { ...accountPayload, records: [grantOperation, reversalOperation] },
  { ...accountPayload, records: [{ ...reversalOperation, requestDigest: 'unsafe' }, grantOperation] },
  { ...accountPayload, internalAccountId: 'unsafe' }
]) {
  assert.throws(() => parseCreditAccount(unsafeAccount, userId))
}
assert.throws(() => parseCreditAccount({
  ...accountPayload,
  truncated: true
}, userId), /截断|100/)
assert.throws(() => parseCreditAccount({
  ...accountPayload,
  balance: 350,
  version: 3,
  records: [{ ...grantOperation, sequenceNo: 3 }]
}, userId), /完整|版本|记录/)
assert.throws(() => parseCreditAccount({
  ...accountPayload,
  openingBalance: 350,
  version: 1,
  records: [{ ...reversalOperation, sequenceNo: 1 }]
}, userId), /原发放|完整|冲正/)
assert.throws(() => parseCreditAccount({
  ...accountPayload,
  balance: 150,
  records: [{
    ...reversalOperation,
    delta: -200,
    balanceAfter: 150
  }, grantOperation]
}, userId), /金额|冲正|原发放/)

const grantPayload = buildCreditGrantPayload({
  userId,
  expectedAccountVersion: 2,
  amount: 250,
  reasonCode: 'CUSTOMER_SUPPORT',
  note: '客户服务补偿测试',
  idempotencyKey: 'credit-grant:33333333-3333-4333-8333-333333333333'
})
assert.deepEqual(Object.keys(grantPayload), [
  'userId', 'expectedAccountVersion', 'amount', 'reasonCode', 'note', 'idempotencyKey'
])
assert.throws(() => buildCreditGrantPayload({
  ...grantPayload,
  actorUserId: 1
}), /字段|参数/)
assert.throws(() => buildCreditGrantPayload({ ...grantPayload, amount: 0 }), /金额/)
assert.throws(() => buildCreditGrantPayload({
  ...grantPayload,
  expectedAccountVersion: -1
}), /版本/)
assert.throws(() => buildCreditGrantPayload({ ...grantPayload, amount: 100001 }), /金额/)
assert.throws(() => buildCreditGrantPayload({ ...grantPayload, reasonCode: 'SALES' }), /原因/)
assert.throws(() => buildCreditGrantPayload({ ...grantPayload, note: ' 前后空格不允许 ' }), /备注/)
assert.throws(() => buildCreditGrantPayload({ ...grantPayload, note: '包含\u200b隐形字符' }), /备注/)
assert.throws(() => buildCreditGrantPayload({ ...grantPayload, idempotencyKey: 'short' }), /幂等/)

const grantResult = parseCreditCommandResult({
  operationId: grantOperation.operationId,
  operationType: 'GRANT',
  userId,
  delta: 250,
  balanceAfter: 350,
  reversalOfOperationId: null,
  createdAt: grantOperation.createdAt
})
assert.deepEqual(verifyCreditGrantResult({ payload: grantPayload, result: grantResult }), grantResult)
assert.throws(() => verifyCreditGrantResult({
  payload: grantPayload,
  result: { ...grantResult, userId: 100 }
}), /回执|用户/)

const reversalPayload = buildCreditReversalPayload({
  operation: grantOperation,
  expectedAccountVersion: 2,
  reasonCode: 'OPERATOR_ERROR',
  note: '操作员误发积分冲正',
  idempotencyKey: 'credit-reverse:44444444-4444-4444-8444-444444444444'
})
assert.deepEqual(Object.keys(reversalPayload), [
  'originalOperationId', 'expectedAccountVersion', 'reasonCode', 'note', 'idempotencyKey'
])
assert.throws(() => buildCreditReversalPayload({
  operation: reversalOperation,
  expectedAccountVersion: 2,
  reasonCode: 'OPERATOR_ERROR',
  note: '操作员误发积分冲正',
  idempotencyKey: 'credit-reverse:44444444-4444-4444-8444-444444444444'
}), /发放/)

const reversalResult = parseCreditCommandResult({
  operationId: reversalOperation.operationId,
  operationType: 'REVERSAL',
  userId,
  delta: -250,
  balanceAfter: 100,
  reversalOfOperationId: grantOperation.operationId,
  createdAt: reversalOperation.createdAt
})
assert.deepEqual(verifyCreditReversalResult({
  userId,
  original: grantOperation,
  payload: reversalPayload,
  result: reversalResult
}), reversalResult)
assert.throws(() => verifyCreditReversalResult({
  userId,
  original: grantOperation,
  payload: {
    ...reversalPayload,
    originalOperationId: '66666666-6666-4666-8666-666666666666'
  },
  result: reversalResult
}), /回执|原发放|请求/)
assert.throws(() => verifyCreditReversalResult({
  userId,
  original: grantOperation,
  payload: reversalPayload,
  result: { ...reversalResult, delta: -249 }
}), /回执|金额/)

const pendingSnapshot = createCreditPendingSnapshot({
  actorUserId: 1,
  kind: 'grant',
  userId,
  payload: grantPayload,
  original: null,
  nowMs: 1784692800000
})
assert.deepEqual(
  parseCreditPendingSnapshot(JSON.stringify(pendingSnapshot), 1, 1784692801000),
  pendingSnapshot
)
assert.throws(() => parseCreditPendingSnapshot(
  JSON.stringify(pendingSnapshot), 2, 1784692801000
), /操作人|恢复/)
assert.deepEqual(parseCreditPendingSnapshot(
  JSON.stringify(pendingSnapshot), 1, 1884782800001
), pendingSnapshot, 'valid unresolved mutations must remain recoverable until proven')
assert.equal(
  creditPendingStorageKey(1),
  'fbsir.independent-board-credit.pending.v1.actor.1'
)
assert.throws(() => creditPendingStorageKey(0), /操作人|存储/)

const lockCalls = []
const lockedResult = await withCreditAccountMutationLock({
  actorUserId: 1,
  lockManager: {
    request: async (name, options, run) => {
      lockCalls.push({ name, options })
      return run({ name })
    }
  },
  run: async () => 'locked'
})
assert.equal(lockedResult, 'locked')
await withCreditAccountMutationLock({
  actorUserId: 1,
  lockManager: {
    request: async (name, options, run) => {
      lockCalls.push({ name, options })
      return run({ name })
    }
  },
  run: async () => 'different-user-operation'
})
assert.deepEqual(lockCalls, [{
  name: 'fbsir.independent-board-credit.pending.actor.1',
  options: { mode: 'exclusive' }
}, {
  name: 'fbsir.independent-board-credit.pending.actor.1',
  options: { mode: 'exclusive' }
}])
await assert.rejects(() => withCreditAccountMutationLock({
  actorUserId: 1,
  lockManager: null,
  run: async () => 'unsafe'
}), /互斥|浏览器/)

assert.equal(
  createCreditIdempotencyKey('grant', () => '55555555-5555-4555-8555-555555555555'),
  'credit-grant:55555555-5555-4555-8555-555555555555'
)
assert.throws(() => createCreditIdempotencyKey('unknown', () => 'x'), /幂等/)
assert.equal(isAmbiguousCreditFailure({ response: { status: 409 } }), false)
assert.equal(isAmbiguousCreditFailure({ response: { status: 503 } }), true)
assert.equal(isAmbiguousCreditFailure(new Error('Network Error')), true)
assert.equal(isCreditAccountNotFound({
  response: { status: 404, data: { code: 404, msg: 'CREDIT_ACCOUNT_NOT_FOUND' } }
}), true)
assert.equal(isCreditAccountNotFound({
  response: { status: 404, data: { code: 404, msg: 'Not Found' } }
}), false)
assert.equal(isCreditAccountNotFound({
  response: { status: 404, data: { code: 500, msg: 'CREDIT_ACCOUNT_NOT_FOUND' } }
}), false)

for (const env of [
  {},
  { VITE_FBSIR_BOARD_CREDIT_CANDIDATE: 'false' },
  { VITE_FBSIR_BOARD_CREDIT_CANDIDATE: 'TRUE' },
  { VITE_FBSIR_BOARD_CREDIT_CANDIDATE: true }
]) {
  assert.equal(isBoardCreditCandidateEnabled(env), false)
}
assert.equal(isBoardCreditCandidateEnabled({
  VITE_FBSIR_BOARD_CREDIT_CANDIDATE: 'true'
}), true)

const routeSource = [{
  name: 'IndependentBoardAdmin',
  path: '/independent-board-admin',
  component: 'Layout',
  children: [{
    name: 'IndependentBoardEntitlementGovernance',
    path: 'entitlements',
    component: 'business/independentBoard/admin/entitlement/index'
  }, {
    name: 'IndependentBoardCreditGovernance',
    path: 'credit-ledger',
    component: 'business/independentBoard/admin/credit/index'
  }]
}]
const routeSnapshot = JSON.stringify(routeSource)
const defaultRoutes = admitIndependentBoardPortalCandidateRoutes(routeSource, {})
assert.doesNotMatch(JSON.stringify(defaultRoutes), /admin\/credit\/index/)
assert.match(JSON.stringify(defaultRoutes), /admin\/entitlement\/index/)
const enabledRoutes = admitIndependentBoardPortalCandidateRoutes(routeSource, {
  VITE_FBSIR_BOARD_CREDIT_CANDIDATE: 'true'
})
assert.match(JSON.stringify(enabledRoutes), /admin\/credit\/index/)
assert.equal(JSON.stringify(routeSource), routeSnapshot, 'route admission must not mutate API data')
assert.deepEqual(admitIndependentBoardPortalCandidateRoutes([{
  name: 'IndependentBoardCreditGovernance',
  path: '/tampered',
  component: 'business/independentBoard/admin/meetingAudit/index'
}], { VITE_FBSIR_BOARD_CREDIT_CANDIDATE: 'true' }), [])

const scriptDirectory = path.dirname(fileURLToPath(import.meta.url))
const uiRoot = path.resolve(scriptDirectory, '..')
const read = relative => fs.readFileSync(path.join(uiRoot, relative), 'utf8')
const pageSource = read('src/views/business/independentBoard/admin/credit/index.vue')
const grantDialogSource = read('src/views/business/independentBoard/admin/credit/CreditGrantDialog.vue')
const reversalDialogSource = read('src/views/business/independentBoard/admin/credit/CreditReversalDialog.vue')
const operationTableSource = read('src/views/business/independentBoard/admin/credit/CreditOperationTable.vue')
const apiSource = read('src/api/business/independentBoard/admin.js')
const admissionSource = read('src/utils/independentBoardPortalCandidate.js')
const permissionStoreSource = read('src/store/modules/permission.js')
const browserFixtureSource = read('scripts/independent-board-credit-browser-backend.mjs')
const browserFixtureEnv = read('.env.credit-browser-fixture')
const browserFixtureOffEnv = read('.env.credit-browser-fixture-off')
const gitignoreSource = read('../.gitignore')
const packageSource = read('package.json')

assert.match(apiSource, /\/business\/independent-board\/credit-accounts\/\$\{userId\}/)
assert.match(apiSource, /\/business\/independent-board\/credit-operations\/grants/)
assert.match(apiSource, /\/business\/independent-board\/credit-operations\/reversals/)
assert.match(pageSource, /isBoardCreditCandidateEnabled\(import\.meta\.env\)/)
assert.match(pageSource, /checkRole\(\['admin'\]\)/)
assert.match(pageSource, /checkPermi\(\['board:credit:query'\]\)/)
assert.match(pageSource, /v-hasPermi="\['board:credit:grant'\]"/)
assert.match(operationTableSource, /v-hasPermi="\['board:credit:reverse'\]"/)
assert.match(pageSource, /parseCreditAccount\(response\?\.data, requestedUserId\)/)
assert.match(pageSource, /selectedUserId\.value !== requestedUserId/)
assert.match(pageSource, /queryUserId\.value === selectedUserId\.value/)
assert.match(pageSource, /pendingMutation/)
assert.match(pageSource, /payloadMatchesPending\(/)
assert.match(pageSource, /:external-error="mutationError"/)
assert.match(pageSource, /localStorage/)
assert.match(pageSource, /addEventListener\('storage'/)
assert.match(pageSource, /synchronizePendingFromStorage\(/)
assert.match(pageSource, /creditPendingStorageKey\(actorUserId\)/)
assert.match(pageSource, /withCreditAccountMutationLock\(/)
assert.match(pageSource, /beforeunload/)
assert.match(pageSource, /onBeforeRouteLeave/)
assert.match(pageSource, /wasRecovering/)
assert.match(pageSource, /audit\.truncated/)
assert.match(pageSource, /@media \(max-width: 768px\)/)
assert.doesNotMatch(pageSource, /grantPoints|changePoints|points\/earn/)
assert.doesNotMatch(pageSource, /actorUserId\s*:/)
assert.match(operationTableSource, /isCreditOperationReversed\(/)
assert.match(operationTableSource, /aria-label=/)
for (const source of [grantDialogSource, reversalDialogSource]) {
  assert.match(source, /aria-label=/)
  assert.match(source, /close-on-click-modal="false"/)
  assert.match(source, /本次不会自动重试/)
  assert.match(source, /:disabled="submitting \|\| ambiguous"/)
  assert.match(source, /externalError/)
  assert.doesNotMatch(source, /actorUserId|userName|tenantId|memberId/)
}
assert.match(grantDialogSource, /buildCreditGrantPayload\(/)
assert.match(reversalDialogSource, /buildCreditReversalPayload\(/)
assert.match(admissionSource, /VITE_FBSIR_BOARD_CREDIT_CANDIDATE/)
assert.match(admissionSource, /IndependentBoardCreditGovernance/)
assert.match(permissionStoreSource, /admitIndependentBoardPortalCandidateRoutes\(res\.data, import\.meta\.env\)/)
assert.match(browserFixtureSource, /IndependentBoardCreditGovernance/)
assert.match(browserFixtureSource, /board:credit:query/)
assert.match(browserFixtureSource, /CREDIT_ACCOUNT_NOT_FOUND/)
assert.match(browserFixtureSource, /CREDIT_IDEMPOTENCY_CONFLICT/)
assert.match(browserFixtureSource, /CREDIT_ACCOUNT_VERSION_CONFLICT/)
assert.match(browserFixtureSource, /fail-next-after-commit/)
assert.doesNotMatch(browserFixtureSource, /body\.actorUserId|payload\.actorUserId/)
assert.match(browserFixtureEnv, /VITE_FBSIR_BOARD_CREDIT_CANDIDATE=true/)
assert.match(browserFixtureOffEnv, /VITE_FBSIR_BOARD_CREDIT_CANDIDATE=false/)
assert.match(gitignoreSource, /!FBSir-ui\/\.env\.credit-browser-fixture(?:\r?\n|$)/)
assert.match(gitignoreSource, /!FBSir-ui\/\.env\.credit-browser-fixture-off(?:\r?\n|$)/)
assert.match(packageSource, /dev:independent-board-credit-fixture/)
assert.match(packageSource, /dev:independent-board-credit-fixture-off/)

console.log('Independent Board W3g credit admin UI verification passed: strict projection, safe idempotent mutations, exact route admission, permissions and accessibility source gates are green.')
