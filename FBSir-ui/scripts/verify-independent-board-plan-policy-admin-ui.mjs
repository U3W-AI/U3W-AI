import assert from 'node:assert/strict'
import fs from 'node:fs'
import {
  buildPlanPolicyRevisionPayload,
  buildPlanPolicyRollbackPayload,
  canRollbackPlanPolicyReceipt,
  createPlanPolicyFallbackMutex,
  createPlanPolicyIdempotencyKey,
  createPlanPolicyPendingSnapshot,
  formatPlanPolicyDateTime,
  isAmbiguousPlanPolicyFailure,
  isPlanPolicyCandidateEnabled,
  isPlanPolicyConflict,
  parsePlanPolicyPendingSnapshot,
  parsePlanPolicyCatalog,
  parsePlanPolicyReceiptEnvelope,
  parsePlanPolicyRevisionPayload,
  parsePlanPolicyRevisionResult,
  payloadMatchesPlanPolicyPending,
  planPolicyPendingStorageKey,
  reconcilePlanPolicyRevisionCatalog,
  verifyPlanPolicyRevisionResult,
  withPlanPolicyMutationLock
} from '../src/views/business/independentBoard/admin/entitlement/planPolicyModel.js'

const productPlans = Object.freeze([
  Object.freeze({
    productCode: 'FBSIR_INDEPENDENT_BOARD',
    planCode: 'BOARD_FREE',
    planName: '独董会免费版',
    vip: false,
    connectorRequired: false,
    dailyMeetingLimit: 2,
    agendaLimit: 8,
    seatLimit: 4,
    secretaryEnabled: false,
    status: 'ACTIVE',
    version: 2,
    updatedAt: '2026-07-22T08:00:00+08:00'
  }),
  Object.freeze({
    productCode: 'FBSIR_INDEPENDENT_BOARD',
    planCode: 'BOARD_VIP',
    planName: '独董会 VIP 版',
    vip: true,
    connectorRequired: true,
    dailyMeetingLimit: 9,
    agendaLimit: 30,
    seatLimit: null,
    secretaryEnabled: true,
    status: 'ACTIVE',
    version: 3,
    updatedAt: '2026-07-22T09:00:00+08:00'
  })
])

const freeBaseline = Object.freeze({
  receiptId: '11111111-1111-4111-8111-111111111111',
  planCode: 'BOARD_FREE',
  policyVersion: 1,
  previousReceiptId: null,
  rollbackOfReceiptId: null,
  action: 'PLAN_POLICY_BASELINED',
  actorType: 'SYSTEM_MIGRATION',
  actorUserId: null,
  planName: '独董会免费版',
  dailyMeetingLimit: 1,
  agendaLimit: 5,
  seatLimit: 3,
  secretaryEnabled: false,
  previousPolicyDigest: null,
  policyDigest: 'a'.repeat(64),
  evidenceLevel: 'ACTION_COMPLETED',
  createdAt: '2026-07-22T07:00:00+08:00'
})
const vipBaseline = Object.freeze({
  ...freeBaseline,
  receiptId: '22222222-2222-4222-8222-222222222222',
  planCode: 'BOARD_VIP',
  planName: '独董会 VIP 版',
  dailyMeetingLimit: 5,
  agendaLimit: 30,
  seatLimit: null,
  secretaryEnabled: true,
  policyDigest: 'b'.repeat(64)
})
const vipRevision = Object.freeze({
  ...vipBaseline,
  receiptId: '33333333-3333-4333-8333-333333333333',
  policyVersion: 2,
  previousReceiptId: vipBaseline.receiptId,
  action: 'PLAN_POLICY_REVISED',
  actorType: 'ADMIN_USER',
  actorUserId: 7,
  planName: '独董会 VIP 增强版',
  dailyMeetingLimit: 8,
  previousPolicyDigest: vipBaseline.policyDigest,
  policyDigest: 'c'.repeat(64),
  createdAt: '2026-07-22T08:00:00+08:00'
})

assert.equal(isPlanPolicyCandidateEnabled({
  VITE_FBSIR_BOARD_PLAN_POLICY_CANDIDATE: 'true'
}), true)
for (const value of [true, 'TRUE', '1', '', undefined]) {
  assert.equal(isPlanPolicyCandidateEnabled({
    VITE_FBSIR_BOARD_PLAN_POLICY_CANDIDATE: value
  }), false)
}

assert.deepEqual(parsePlanPolicyCatalog(productPlans), productPlans)
assert.throws(() => parsePlanPolicyCatalog(productPlans.map(plan =>
  plan.planCode === 'BOARD_FREE' ? { ...plan, version: 0 } : plan)), /版本|正整数/)
for (const planName of ['\u00A0非法目录名', '非法目录名\u202F', '\uD800']) {
  assert.throws(() => parsePlanPolicyCatalog(productPlans.map(plan =>
    plan.planCode === 'BOARD_FREE' ? { ...plan, planName } : plan)), /名称|非法值|字符/)
}
const maxCodePointPlanName = '😀'.repeat(128)
assert.equal(parsePlanPolicyCatalog(productPlans.map(plan =>
  plan.planCode === 'BOARD_FREE' ? { ...plan, planName: maxCodePointPlanName } : plan
))[0].planName, maxCodePointPlanName)
assert.throws(() => parsePlanPolicyCatalog(productPlans.map(plan =>
  plan.planCode === 'BOARD_FREE' ? { ...plan, planName: '😀'.repeat(129) } : plan
)), /名称|非法值|字符/)
assert.match(formatPlanPolicyDateTime('2026-07-22T08:00:00+08:00'), /2026/)

const audit = parsePlanPolicyReceiptEnvelope({
  records: [vipRevision, vipBaseline, freeBaseline],
  limit: 100,
  truncated: false
})
assert.equal(audit.records.length, 3)
assert.equal(Object.isFrozen(audit.records), true)
assert.throws(() => parsePlanPolicyReceiptEnvelope({
  records: [{ ...vipRevision, commandDigest: 'unsafe' }],
  limit: 100,
  truncated: false
}), /字段|安全合同/)
assert.throws(() => parsePlanPolicyReceiptEnvelope({
  records: [vipRevision, vipRevision],
  limit: 100,
  truncated: false
}), /重复/)
assert.throws(() => parsePlanPolicyReceiptEnvelope({
  records: [vipRevision],
  limit: 101,
  truncated: true
}), /100|边界/)
assert.throws(() => parsePlanPolicyReceiptEnvelope({
  records: [{ ...vipBaseline, actorUserId: 7 }],
  limit: 100,
  truncated: false
}), /基线|操作者/)

const revisionKey = createPlanPolicyIdempotencyKey(
  'revise', () => '44444444-4444-4444-8444-444444444444')
const revisionDraft = {
  planCode: 'BOARD_VIP',
  expectedVersion: 3,
  planName: '独董会 VIP 专业版',
  dailyMeetingLimit: 10,
  agendaLimit: 30,
  seatLimit: null,
  secretaryEnabled: true
}
const revisionPayload = buildPlanPolicyRevisionPayload({
  catalog: productPlans,
  draft: revisionDraft,
  rollbackOfReceiptId: null,
  idempotencyKey: revisionKey
})
assert.deepEqual(Object.keys(revisionPayload), [
  'planCode', 'expectedVersion', 'planName', 'dailyMeetingLimit',
  'agendaLimit', 'seatLimit', 'secretaryEnabled',
  'rollbackOfReceiptId', 'idempotencyKey'
])
assert.equal(revisionPayload.planName, '独董会 VIP 专业版')
assert.deepEqual(parsePlanPolicyRevisionPayload(revisionPayload), revisionPayload)
assert.throws(() => buildPlanPolicyRevisionPayload({
  catalog: productPlans,
  draft: { ...revisionDraft, internalId: 1 },
  rollbackOfReceiptId: null,
  idempotencyKey: revisionKey
}), /字段|参数/)
assert.throws(() => buildPlanPolicyRevisionPayload({
  catalog: productPlans,
  draft: {
    planCode: 'BOARD_FREE', expectedVersion: 2, planName: '免费专业版',
    dailyMeetingLimit: 10, agendaLimit: 8, seatLimit: 4, secretaryEnabled: false
  },
  rollbackOfReceiptId: null,
  idempotencyKey: revisionKey
}), /VIP|低于|目录/)
assert.throws(() => buildPlanPolicyRevisionPayload({
  catalog: productPlans,
  draft: { ...revisionDraft, agendaLimit: 31 },
  rollbackOfReceiptId: null,
  idempotencyKey: revisionKey
}), /议题|30/)
assert.throws(() => buildPlanPolicyRevisionPayload({
  catalog: productPlans,
  draft: {
    planCode: 'BOARD_FREE', expectedVersion: 2, planName: '免费版',
    dailyMeetingLimit: 2, agendaLimit: 8, seatLimit: null, secretaryEnabled: false
  },
  rollbackOfReceiptId: null,
  idempotencyKey: revisionKey
}), /免费|席位/)
assert.throws(() => buildPlanPolicyRevisionPayload({
  catalog: productPlans,
  draft: {
    planCode: 'BOARD_VIP', expectedVersion: 3, planName: '独董会 VIP 版',
    dailyMeetingLimit: 9, agendaLimit: 30, seatLimit: null, secretaryEnabled: true
  },
  rollbackOfReceiptId: null,
  idempotencyKey: revisionKey
}), /变化|修订/)

const rollbackKey = createPlanPolicyIdempotencyKey(
  'rollback', () => '55555555-5555-4555-8555-555555555555')
const rollbackPayload = buildPlanPolicyRollbackPayload({
  catalog: productPlans,
  receipt: vipBaseline,
  idempotencyKey: rollbackKey
})
assert.deepEqual(rollbackPayload, {
  planCode: 'BOARD_VIP',
  expectedVersion: 3,
  planName: vipBaseline.planName,
  dailyMeetingLimit: vipBaseline.dailyMeetingLimit,
  agendaLimit: vipBaseline.agendaLimit,
  seatLimit: vipBaseline.seatLimit,
  secretaryEnabled: vipBaseline.secretaryEnabled,
  rollbackOfReceiptId: vipBaseline.receiptId,
  idempotencyKey: rollbackKey
})
assert.equal(canRollbackPlanPolicyReceipt(productPlans, vipBaseline), true)
assert.equal(canRollbackPlanPolicyReceipt(productPlans, {
  ...vipBaseline,
  planName: productPlans[1].planName,
  dailyMeetingLimit: productPlans[1].dailyMeetingLimit
}), false)

const revisionResult = parsePlanPolicyRevisionResult({
  ...vipRevision,
  receiptId: '66666666-6666-4666-8666-666666666666',
  policyVersion: 4,
  previousReceiptId: vipRevision.receiptId,
  planName: revisionPayload.planName,
  dailyMeetingLimit: revisionPayload.dailyMeetingLimit,
  agendaLimit: revisionPayload.agendaLimit,
  seatLimit: revisionPayload.seatLimit,
  secretaryEnabled: revisionPayload.secretaryEnabled,
  previousPolicyDigest: vipRevision.policyDigest,
  policyDigest: 'd'.repeat(64)
})
assert.deepEqual(verifyPlanPolicyRevisionResult({
  payload: revisionPayload,
  result: revisionResult,
  actorUserId: 7
}), revisionResult)
assert.throws(() => verifyPlanPolicyRevisionResult({
  payload: revisionPayload,
  result: { ...revisionResult, planCode: 'BOARD_FREE' },
  actorUserId: 7
}), /回执|套餐/)
assert.throws(() => verifyPlanPolicyRevisionResult({
  payload: revisionPayload,
  result: revisionResult,
  actorUserId: 8
}), /操作人|回执/)

const committedCatalog = productPlans.map(plan => plan.planCode === 'BOARD_VIP'
  ? {
      ...plan,
      planName: revisionPayload.planName,
      dailyMeetingLimit: revisionPayload.dailyMeetingLimit,
      agendaLimit: revisionPayload.agendaLimit,
      seatLimit: revisionPayload.seatLimit,
      secretaryEnabled: revisionPayload.secretaryEnabled,
      version: revisionResult.policyVersion
    }
  : plan)
assert.equal(reconcilePlanPolicyRevisionCatalog(
  committedCatalog, revisionResult), 'CURRENT')
assert.equal(reconcilePlanPolicyRevisionCatalog(
  committedCatalog.map(plan => plan.planCode === 'BOARD_VIP'
    ? { ...plan, version: revisionResult.policyVersion + 1, planName: '后续版本' }
    : plan),
  revisionResult), 'SUPERSEDED')
assert.throws(() => reconcilePlanPolicyRevisionCatalog(
  productPlans, revisionResult), /head|版本|回执/)
assert.throws(() => reconcilePlanPolicyRevisionCatalog(
  committedCatalog.map(plan => plan.planCode === 'BOARD_VIP'
    ? { ...plan, planName: '同版本漂移' }
    : plan),
  revisionResult), /head|漂移|回执/)
assert.throws(() => parsePlanPolicyRevisionPayload({
  ...revisionPayload, planName: '\u00A0非法边界空白'
}), /名称|空白|字符/)
assert.throws(() => parsePlanPolicyRevisionPayload({
  ...revisionPayload, planName: '非法边界空白\u202F'
}), /名称|空白|字符/)
assert.equal(parsePlanPolicyRevisionPayload({
  ...revisionPayload, planName: maxCodePointPlanName
}).planName, maxCodePointPlanName)
assert.throws(() => parsePlanPolicyRevisionPayload({
  ...revisionPayload, planName: '😀'.repeat(129)
}), /名称|空白|字符/)
assert.throws(() => parsePlanPolicyRevisionPayload({
  ...revisionPayload, planName: '\uD800'
}), /名称|空白|字符/)

const pending = createPlanPolicyPendingSnapshot({
  actorUserId: 7,
  payload: revisionPayload,
  nowMs: 1_753_168_800_000
})
assert.deepEqual(parsePlanPolicyPendingSnapshot(
  JSON.stringify(pending), 7, 1_753_168_800_500), pending)
assert.equal(planPolicyPendingStorageKey(7),
  'fbsir.independent-board-plan-policy.pending.v1.actor.7')
assert.equal(payloadMatchesPlanPolicyPending(pending, revisionPayload), true)
assert.equal(payloadMatchesPlanPolicyPending(pending, {
  ...revisionPayload, dailyMeetingLimit: 11
}), false)
assert.throws(() => parsePlanPolicyPendingSnapshot(JSON.stringify({
  ...pending, actorUserId: 8
}), 7, 1_753_168_800_500), /操作者|身份/)

assert.equal(isPlanPolicyConflict({ response: { status: 409 } }), true)
assert.equal(isAmbiguousPlanPolicyFailure({ response: { status: 409 } }), false)
assert.equal(isAmbiguousPlanPolicyFailure({ response: { status: 503 } }), true)
assert.equal(isAmbiguousPlanPolicyFailure(new Error('Network Error')), true)

const lockCalls = []
assert.equal(await withPlanPolicyMutationLock({
  actorUserId: 7,
  lockManager: {
    request: async (name, options, run) => {
      lockCalls.push({ name, options })
      return run({ name })
    }
  },
  run: async () => 'locked'
}), 'locked')
assert.deepEqual(lockCalls, [{
  name: 'fbsir.independent-board-plan-policy.pending.actor.7',
  options: { mode: 'exclusive' }
}])

const fallback = createPlanPolicyFallbackMutex()
const fallbackOrder = []
let releaseFirst
const first = withPlanPolicyMutationLock({
  actorUserId: 7,
  lockManager: null,
  fallbackMutex: fallback,
  run: async () => {
    fallbackOrder.push('first:start')
    await new Promise(resolve => { releaseFirst = resolve })
    fallbackOrder.push('first:end')
  }
})
const second = withPlanPolicyMutationLock({
  actorUserId: 7,
  lockManager: null,
  fallbackMutex: fallback,
  run: async () => { fallbackOrder.push('second') }
})
await new Promise(resolve => setTimeout(resolve, 0))
assert.deepEqual(fallbackOrder, ['first:start'])
releaseFirst()
await Promise.all([first, second])
assert.deepEqual(fallbackOrder, ['first:start', 'first:end', 'second'])

const entitlementPageSource = fs.readFileSync(
  new URL('../src/views/business/independentBoard/admin/entitlement/index.vue', import.meta.url),
  'utf8'
)
const panelSource = fs.readFileSync(
  new URL('../src/views/business/independentBoard/admin/entitlement/PlanPolicyGovernancePanel.vue', import.meta.url),
  'utf8'
)
const dialogSource = fs.readFileSync(
  new URL('../src/views/business/independentBoard/admin/entitlement/PlanPolicyRevisionDialog.vue', import.meta.url),
  'utf8'
)
const apiSource = fs.readFileSync(
  new URL('../src/api/business/independentBoard/admin.js', import.meta.url),
  'utf8'
)
const browserFixtureSource = fs.readFileSync(
  new URL('./independent-board-plan-policy-browser-backend.mjs', import.meta.url),
  'utf8'
)
const enabledFixtureEnvironment = fs.readFileSync(
  new URL('../.env.plan-policy-browser-fixture', import.meta.url),
  'utf8'
)
const defaultOffFixtureEnvironment = fs.readFileSync(
  new URL('../.env.plan-policy-browser-fixture-off', import.meta.url),
  'utf8'
)
const gitignoreSource = fs.readFileSync(
  new URL('../../.gitignore', import.meta.url),
  'utf8'
)

assert.match(entitlementPageSource, /VITE_FBSIR_BOARD_PLAN_POLICY_CANDIDATE/)
assert.match(entitlementPageSource, /checkRole\(\['admin'\]\)/)
assert.match(entitlementPageSource, /board:plan:revise/)
assert.match(entitlementPageSource, /board:plan:audit/)
assert.match(entitlementPageSource, /PlanPolicyGovernancePanel/)
assert.doesNotMatch(entitlementPageSource, /addRoute|router\.addRoute/)
assert.match(apiSource, /\/business\/independent-board\/plan-policy-revisions/)
assert.match(apiSource, /\/business\/independent-board\/plan-policy-receipts/)
assert.match(apiSource, /headers:\s*\{\s*repeatSubmit:\s*false\s*\}/)
assert.match(browserFixtureSource, /const HOST = '127\.0\.0\.1'/)
assert.match(browserFixtureSource, /fail-next-after-commit/)
assert.match(browserFixtureSource, /advance-vip/)
assert.match(enabledFixtureEnvironment, /VITE_FBSIR_BOARD_PORTAL_CANDIDATE=false/)
assert.match(enabledFixtureEnvironment, /VITE_FBSIR_BOARD_PLAN_POLICY_CANDIDATE=true/)
assert.match(defaultOffFixtureEnvironment, /VITE_FBSIR_BOARD_PORTAL_CANDIDATE=false/)
assert.match(defaultOffFixtureEnvironment, /VITE_FBSIR_BOARD_PLAN_POLICY_CANDIDATE=false/)
assert.match(gitignoreSource, /!FBSir-ui\/\.env\.plan-policy-browser-fixture\r?\n/)
assert.match(gitignoreSource, /!FBSir-ui\/\.env\.plan-policy-browser-fixture-off\r?\n/)

assert.match(panelSource, /v-hasPermi="\['board:plan:revise'\]"/)
assert.match(panelSource, /v-hasPermi="\['board:plan:audit'\]"/)
assert.match(panelSource, /withPlanPolicyMutationLock\(/)
assert.match(panelSource, /localStorage/)
assert.match(panelSource, /addEventListener\('storage'/)
assert.match(panelSource, /pendingMutation/)
assert.match(panelSource, /payloadMatchesPlanPolicyPending\(/)
assert.match(panelSource, /const pendingCleared = clearPendingMutation\(\)/)
assert.match(panelSource, /if \(!pendingCleared\)/)
assert.match(panelSource, /if \(!removed\)[\s\S]*pendingRecoveryBlocked\.value = true/)
assert.match(panelSource, /本标签页后续写入已锁定/)
assert.match(panelSource, /manualRefreshRequired/)
assert.match(panelSource, /&& !manualRefreshRequired\.value\)/)
assert.match(panelSource, /reconcilePlanPolicyRevisionCatalog\(catalog, result\)/)
assert.match(panelSource, /actorUserId: props\.actorUserId/)
assert.doesNotMatch(panelSource, /catalogMatchesResult/)
assert.match(panelSource, /409/)
assert.match(panelSource, /不会自动套用|不自动套用/)
assert.match(panelSource, /降级为只读/)
assert.match(panelSource, /禁止写入，避免并发双写/)
assert.match(panelSource, /fallbackMutex:\s*null/)
assert.match(panelSource, /audit\.truncated/)
assert.match(panelSource, /最多返回.*100/)
assert.doesNotMatch(panelSource, /planLabel\(scope\.row\.planCode\)/)
assert.match(panelSource, /@media \(max-width: 768px\)/)
assert.doesNotMatch(panelSource, /commandDigest|idempotencyKeyDigest|databaseId|internalLock/)

for (const field of [
  'planName', 'dailyMeetingLimit', 'agendaLimit', 'seatLimit', 'secretaryEnabled'
]) {
  assert.match(dialogSource, new RegExp(field))
}
assert.match(dialogSource, /aria-label=/)
assert.match(dialogSource, /close-on-click-modal="false"/)
assert.match(dialogSource, /:disabled="submitting \|\| ambiguous \|\| conflict \|\| isRollback"/)
assert.match(dialogSource, /lockedRollbackPayload/)
assert.doesNotMatch(dialogSource, /actorUserId|commandDigest|policyDigest/)

console.log('Independent Board plan-policy admin UI verification passed: candidate gate, strict contracts, pending replay, 409 preservation, rollback and responsive matrices are green.')
