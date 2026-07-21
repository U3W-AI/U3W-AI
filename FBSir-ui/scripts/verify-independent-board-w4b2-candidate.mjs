import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import {
  BOARD_CONNECTOR_SCOPES,
  connectorActionPolicy,
  formatBoardCandidateReference,
  isBoardPortalCandidateEnabled,
  parseBoardCandidateEnvelope,
  parseBoardConnectorBinding,
  parseBoardConnectorView,
  parseBoardTenant,
  parseBoardOAuthClient,
  parseBoardOAuthFamily,
  parseBoardSecurityEvent
} from '../src/views/business/independentBoard/portalCandidateModel.js'

const tenantId = 7
const scopes = [...BOARD_CONNECTOR_SCOPES]

const tenant = {
  tenantId,
  tenantLabel: '福帮手测试企业',
  status: 'ACTIVE'
}
assert.deepEqual(parseBoardTenant(tenant), tenant)
assert.throws(() => parseBoardTenant({
  ...tenant,
  contactPhone: 'must-never-reach-the-browser'
}), /安全合同/)
assert.throws(() => parseBoardTenant({
  ...tenant,
  tenantId: 0
}), /企业/)

assert.equal(isBoardPortalCandidateEnabled({}), false)
assert.equal(isBoardPortalCandidateEnabled({ VITE_FBSIR_BOARD_PORTAL_CANDIDATE: 'false' }), false)
assert.equal(isBoardPortalCandidateEnabled({ VITE_FBSIR_BOARD_PORTAL_CANDIDATE: 'TRUE' }), false)
assert.equal(isBoardPortalCandidateEnabled({ VITE_FBSIR_BOARD_PORTAL_CANDIDATE: true }), false)
assert.equal(isBoardPortalCandidateEnabled({ VITE_FBSIR_BOARD_PORTAL_CANDIDATE: 'true' }), true)

const activeConnector = Object.freeze({
  tenantId,
  memberId: 42,
  uiState: 'ACTIVE',
  effectivePlanCode: 'BOARD_VIP',
  clientRef: 'cli_4f9a2c',
  familyRef: 'fam_a81e10',
  bindingRef: 'bnd_0216fe',
  scopes,
  issuedAt: '2026-07-21T10:00:00+08:00',
  expiresAt: '2026-08-21T10:00:00+08:00',
  lastSeenAt: '2026-07-21T10:05:00+08:00',
  version: 3,
  evidenceLevel: 'ACTION_COMPLETED'
})
const parsedConnector = parseBoardConnectorView(activeConnector, tenantId)
assert.deepEqual(parsedConnector, activeConnector)
assert.notEqual(parsedConnector, activeConnector)
assert.equal(Object.isFrozen(parsedConnector), true)
assert.equal(Object.isFrozen(parsedConnector.scopes), true)
assert.throws(() => parseBoardConnectorView({
  ...activeConnector,
  refreshToken: 'must-never-reach-the-browser'
}, tenantId), /安全合同/)
assert.throws(() => parseBoardConnectorView({
  ...activeConnector,
  scopes: scopes.slice(0, 3)
}, tenantId), /完整 Scope/)
assert.throws(() => parseBoardConnectorView({
  ...activeConnector,
  effectivePlanCode: 'BOARD_FREE'
}, tenantId), /VIP/)

const unknownConnector = {
  tenantId,
  memberId: 42,
  uiState: 'UNKNOWN',
  effectivePlanCode: 'BOARD_FREE',
  clientRef: null,
  familyRef: null,
  bindingRef: null,
  scopes: [],
  issuedAt: null,
  expiresAt: null,
  lastSeenAt: null,
  version: 0,
  evidenceLevel: 'CURRENT_READ_INCOMPLETE'
}
assert.equal(parseBoardConnectorView(unknownConnector, tenantId).uiState, 'UNKNOWN')
assert.throws(() => parseBoardConnectorView({
  ...unknownConnector,
  clientRef: activeConnector.clientRef
}, tenantId), /UNKNOWN/)
assert.throws(() => parseBoardConnectorView({
  ...unknownConnector,
  effectivePlanCode: 'BOARD_VIP'
}, tenantId), /UNKNOWN/)
assert.equal(parseBoardConnectorView({
  ...unknownConnector,
  uiState: 'NOT_CONNECTED',
  evidenceLevel: 'CURRENT_READ_COMPLETE'
}, tenantId).uiState, 'NOT_CONNECTED')
assert.throws(() => parseBoardConnectorView({
  ...activeConnector,
  uiState: 'AUTHORIZATION_PENDING',
  effectivePlanCode: 'BOARD_FREE'
}, tenantId), /非法值/)
assert.throws(() => parseBoardConnectorView({
  ...activeConnector,
  uiState: 'PENDING_ACTIVATION'
}, tenantId), /免费版/)
assert.throws(() => parseBoardConnectorView({
  ...activeConnector,
  uiState: 'PENDING_ACTIVATION',
  effectivePlanCode: 'BOARD_FREE',
  bindingRef: null,
  lastSeenAt: null,
  evidenceLevel: 'BUSINESS_CONFIRMED'
}, tenantId), /证据|非法值/)
assert.deepEqual(parseBoardConnectorView({
  ...activeConnector,
  uiState: 'PENDING_ACTIVATION',
  effectivePlanCode: 'BOARD_FREE',
  bindingRef: null,
  lastSeenAt: null
}, tenantId).uiState, 'PENDING_ACTIVATION')
for (const timeField of ['issuedAt', 'expiresAt', 'lastSeenAt']) {
  assert.throws(() => parseBoardConnectorView({
    ...activeConnector,
    [timeField]: null
  }, tenantId), /current-read|时序/)
}
assert.throws(() => parseBoardConnectorView({
  ...activeConnector,
  lastSeenAt: '2026-08-21T10:00:00+08:00'
}, tenantId), /时序/)
assert.equal(parseBoardConnectorView({
  ...activeConnector,
  version: 0
}, tenantId).version, 0)
assert.throws(() => parseBoardConnectorView({
  ...unknownConnector,
  uiState: 'NOT_CONNECTED',
  evidenceLevel: 'CURRENT_READ_COMPLETE',
  version: 1
}, tenantId), /current-read/)
assert.deepEqual(connectorActionPolicy('UNKNOWN'), {
  primary: 'REFRESH',
  canReturnToWorkBuddy: false,
  canDisconnect: false,
  canReauthorize: false
})
assert.equal(connectorActionPolicy('NOT_CONNECTED').canReturnToWorkBuddy, true)
assert.equal(connectorActionPolicy('ACTIVE').canDisconnect, false,
  'W4b.2 candidate must not unlock disconnect writes')

const client = {
  clientRef: 'cli_4f9a2c',
  displayName: '未验证的本地公共客户端',
  status: 'ACTIVE',
  redirectUri: 'http://127.0.0.1:54321/oauth/callback',
  grantTypes: ['authorization_code', 'refresh_token'],
  responseTypes: ['code'],
  scopes,
  registeredAt: '2026-07-21T09:00:00+08:00',
  expiresAt: '2026-08-21T09:00:00+08:00',
  terminatedAt: null,
  version: 0,
  metadataDigestRef: 'sha256:3c118d4a',
  registrationSourceDigestRef: 'sha256:773e1b9c'
}
assert.deepEqual(parseBoardOAuthClient(client), client)
assert.equal(Object.isFrozen(parseBoardOAuthClient(client).grantTypes), true)
assert.equal(Object.isFrozen(parseBoardOAuthClient(client).responseTypes), true)
assert.throws(() => parseBoardOAuthClient({
  ...client,
  grantTypes: ['authorization_code']
}), /授权类型/)
assert.throws(() => parseBoardOAuthClient({
  ...client,
  grantType: 'authorization_code'
}), /安全合同/)
assert.throws(() => parseBoardOAuthClient({ ...client, clientSecret: 'unsafe' }), /安全合同/)
for (const port of [1, 1023, 65536]) {
  assert.throws(() => parseBoardOAuthClient({
    ...client,
    redirectUri: `http://127.0.0.1:${port}/oauth/callback`
  }), /回调地址/)
}
for (const redirectUri of [
  'http://127.000.000.001:54321/oauth/callback',
  'http://127.0.0.1:054321/oauth/callback',
  'http://127.0.0.1.:54321/oauth/callback',
  'HTTP://127.0.0.1:54321/oauth/callback'
]) {
  assert.throws(() => parseBoardOAuthClient({ ...client, redirectUri }), /回调地址/)
}
for (const port of [1024, 65535]) {
  assert.equal(parseBoardOAuthClient({
    ...client,
    redirectUri: `http://127.0.0.1:${port}/oauth/callback`
  }).redirectUri, `http://127.0.0.1:${port}/oauth/callback`)
}
assert.throws(() => parseBoardOAuthClient({
  ...client,
  expiresAt: client.registeredAt
}), /时序/)

const family = {
  familyRef: 'fam_a81e10',
  tenantId,
  tenantLabel: '福帮手测试企业',
  memberLabel: '成员 #42',
  userLabel: '用户 #99',
  clientRef: client.clientRef,
  consentIntent: 'FIRST_CONNECT',
  status: 'ACTIVE',
  currentRefreshGeneration: 2,
  bindingRef: activeConnector.bindingRef,
  issuedAt: '2026-07-21T10:00:00+08:00',
  activatedAt: '2026-07-21T10:01:00+08:00',
  expiresAt: '2026-08-21T10:00:00+08:00',
  terminatedAt: null,
  version: 4
}
assert.deepEqual(parseBoardOAuthFamily(family, tenantId), family)
assert.throws(() => parseBoardOAuthFamily({ ...family, tenantId: 8 }, tenantId), /跨企业/)
assert.equal(parseBoardOAuthFamily({
  ...family,
  status: 'PENDING_BINDING',
  currentRefreshGeneration: 0,
  bindingRef: null,
  activatedAt: null,
  version: 0
}, tenantId).version, 0)
assert.throws(() => parseBoardOAuthFamily({
  ...family,
  status: 'PENDING_BINDING'
}, tenantId), /PENDING_BINDING/)
assert.throws(() => parseBoardOAuthFamily({
  ...family,
  status: 'REVOKED'
}, tenantId), /终态/)
assert.throws(() => parseBoardOAuthFamily({
  ...family,
  activatedAt: family.expiresAt
}, tenantId), /时序/)

const binding = {
  bindingRef: activeConnector.bindingRef,
  tenantId,
  tenantLabel: '福帮手测试企业',
  memberLabel: '成员 #42',
  userLabel: '用户 #99',
  productCode: 'FBSIR_INDEPENDENT_BOARD',
  sourceCode: 'WORKBUDDY',
  connectorCode: 'fbs-connector',
  status: 'ACTIVE',
  scopes,
  verificationMethod: 'MCP_INITIALIZE',
  verifiedAt: '2026-07-21T10:01:00+08:00',
  lastSeenAt: '2026-07-21T10:05:00+08:00',
  validUntil: '2026-08-21T10:00:00+08:00',
  revokedAt: null,
  clientRef: client.clientRef,
  subjectDigestRef: 'sha256:8b93c411',
  version: 3,
  entitlementActive: true,
  familyActive: true,
  vipEffective: true
}
assert.deepEqual(parseBoardConnectorBinding(binding, tenantId), binding)
assert.throws(() => parseBoardConnectorBinding({
  ...binding,
  productCode: 'INDEPENDENT_BOARD',
  connectorCode: 'FBS_MCP'
}, tenantId), /非法值/)
assert.throws(() => parseBoardConnectorBinding({
  ...binding,
  scopes: scopes.slice(1)
}, tenantId), /VIP 连接/)
assert.equal(parseBoardConnectorBinding({
  ...binding,
  familyActive: false,
  vipEffective: false
}, tenantId).vipEffective, false)
assert.throws(() => parseBoardConnectorBinding({
  ...binding,
  familyActive: false,
  vipEffective: true
}, tenantId), /VIP 生效/)
assert.throws(() => parseBoardConnectorBinding({
  ...binding,
  verificationMethod: 'OAUTH_FIRST_PROTECTED_REQUEST'
}, tenantId), /非法值/)
assert.throws(() => parseBoardConnectorBinding({
  ...binding,
  verifiedAt: binding.validUntil
}, tenantId), /时序/)
assert.throws(() => parseBoardConnectorBinding({
  ...binding,
  lastSeenAt: binding.validUntil
}, tenantId), /时序/)
for (const status of ['REVOKED', 'COMPROMISED']) {
  assert.throws(() => parseBoardConnectorBinding({
    ...binding,
    status
  }, tenantId), /终态/)
}
assert.throws(() => parseBoardConnectorBinding({
  ...binding,
  status: 'EXPIRED',
  validUntil: '2026-07-21T10:06:00+08:00',
  familyActive: false,
  vipEffective: false
}, tenantId), /非法值/)
assert.equal(parseBoardConnectorBinding({
  ...binding,
  status: 'REVOKED',
  revokedAt: '2026-07-21T10:06:00+08:00',
  familyActive: false,
  vipEffective: false
}, tenantId).vipEffective, false)

assert.equal(formatBoardCandidateReference(null), '未记录')
assert.equal(formatBoardCandidateReference('cli_4f9a2c'), 'cli_4f9a2c')
const longReference = 'family_0123456789abcdef0123456789abcdef'
const shortenedReference = formatBoardCandidateReference(longReference)
assert.notEqual(shortenedReference, longReference)
assert.match(shortenedReference, /…/)
assert.equal(shortenedReference.includes('0123456789abcdef0123456789abcdef'), false)

const securityEvent = {
  eventRef: 'evt_101b0c',
  tenantId,
  occurredAt: '2026-07-21T10:06:00+08:00',
  action: 'TOKEN_FAMILY_ROTATED',
  actorType: 'CLIENT',
  tenantLabel: '福帮手测试企业',
  memberLabel: '成员 #42',
  clientRef: client.clientRef,
  familyRef: family.familyRef,
  bindingRef: binding.bindingRef,
  correlationRef: 'cor_6f21a4',
  evidenceLevel: 'ACTION_COMPLETED',
  reasonCode: null
}
assert.deepEqual(parseBoardSecurityEvent(securityEvent, tenantId), securityEvent)
const connectorSecurityEvent = {
  ...securityEvent,
  eventRef: 'evt_7f37ac',
  action: 'CONNECTOR_BINDING_VERIFIED',
  actorType: 'USER',
  familyRef: null,
  correlationRef: null
}
assert.deepEqual(parseBoardSecurityEvent(connectorSecurityEvent, tenantId), connectorSecurityEvent)
const authorizationSecurityEvent = {
  ...securityEvent,
  eventRef: 'evt_approval_9a2c',
  action: 'AUTHORIZATION_APPROVED',
  actorType: 'USER',
  familyRef: null,
  bindingRef: null
}
assert.deepEqual(parseBoardSecurityEvent(authorizationSecurityEvent, tenantId), authorizationSecurityEvent)
assert.throws(() => parseBoardSecurityEvent({
  ...securityEvent,
  clientRef: null,
  familyRef: null,
  bindingRef: null,
  correlationRef: null
}, tenantId), /血缘/)
assert.throws(() => parseBoardSecurityEvent({
  ...securityEvent,
  actorType: 'USER'
}, tenantId), /主体/)
assert.throws(() => parseBoardSecurityEvent({
  ...authorizationSecurityEvent,
  actorType: 'CLIENT'
}, tenantId), /主体/)
assert.throws(() => parseBoardSecurityEvent({
  ...connectorSecurityEvent,
  familyRef: family.familyRef
}, tenantId), /血缘/)
assert.throws(() => parseBoardSecurityEvent({
  ...connectorSecurityEvent,
  correlationRef: 'cor_connector_invalid'
}, tenantId), /血缘/)
assert.throws(() => parseBoardSecurityEvent({
  ...authorizationSecurityEvent,
  familyRef: family.familyRef
}, tenantId), /血缘/)
assert.throws(() => parseBoardSecurityEvent({
  ...securityEvent,
  action: 'OAUTH_CLIENT_REGISTERED'
}, tenantId), /非法值/)
assert.throws(() => parseBoardSecurityEvent({
  ...securityEvent,
  action: 'TOKEN_REVOCATION_COMPLETED'
}, tenantId), /非法值/)
assert.throws(() => parseBoardSecurityEvent({
  ...securityEvent,
  reasonCode: 'REFRESH_REPLAY'
}, tenantId), /非法值/)
assert.throws(() => parseBoardSecurityEvent({
  ...securityEvent,
  authorizationHeader: 'Bearer unsafe'
}, tenantId), /安全合同/)

const clientEnvelope = parseBoardCandidateEnvelope({
  records: [client],
  limit: 100,
  truncated: false,
  nextCursor: null
}, parseBoardOAuthClient)
assert.equal(clientEnvelope.records.length, 1)
assert.equal(Object.isFrozen(clientEnvelope.records), true)
assert.throws(() => parseBoardCandidateEnvelope({
  records: [client, client],
  limit: 100,
  truncated: false,
  nextCursor: null
}, parseBoardOAuthClient), /重复/)
assert.throws(() => parseBoardCandidateEnvelope({
  records: [client],
  limit: 100,
  truncated: true,
  nextCursor: '<script>alert(1)</script>'
}, parseBoardOAuthClient), /边界/)

const secondFamily = { ...family, familyRef: 'fam_b72f20' }
const familyEnvelope = parseBoardCandidateEnvelope({
  records: [family, secondFamily],
  limit: 100,
  truncated: false,
  nextCursor: null
}, parseBoardOAuthFamily, tenantId)
assert.equal(familyEnvelope.records.length, 2,
  'families sharing one client must be deduplicated by familyRef')

const scriptDirectory = path.dirname(fileURLToPath(import.meta.url))
const uiRoot = path.resolve(scriptDirectory, '..')
const read = relativePath => fs.readFileSync(path.join(uiRoot, relativePath), 'utf8')

const apiSource = read('src/api/business/independentBoard/portalCandidate.js')
for (const internalPath of [
  '/my/independent-board/connector',
  '/my/independent-board/security-receipts',
  '/business/independent-board/oauth/clients',
  '/business/independent-board/oauth/families',
  '/business/independent-board/connector-bindings',
  '/business/independent-board/tenants',
  '/business/independent-board/oauth/security-events'
]) {
  assert.ok(apiSource.includes(internalPath), `missing internal candidate API ${internalPath}`)
}
assert.doesNotMatch(apiSource, /method:\s*['"](?:post|put|patch|delete)['"]/i)
assert.doesNotMatch(apiSource, /\/oauth2\/|\/\.well-known\/|\/fbs-mcp\/mcp/)
assert.equal((apiSource.match(/assertBoardPortalCandidateEnabled\(\)/g) || []).length, 7)
assert.match(apiSource, /function optionalCursor/)
assert.equal((apiSource.match(/cursor:\s*optionalCursor\(cursor\)/g) || []).length, 6)

const candidatePages = [
  ['src/views/business/independentBoard/me/connector/index.vue', 'my:independent-board:connector:view'],
  ['src/views/business/independentBoard/me/security/index.vue', 'my:independent-board:security:view'],
  ['src/views/business/independentBoard/admin/oauth-client/index.vue', 'board:oauth:client:query'],
  ['src/views/business/independentBoard/admin/oauth-family/index.vue', 'board:oauth:family:query'],
  ['src/views/business/independentBoard/admin/connector-binding/index.vue', 'board:connector:query'],
  ['src/views/business/independentBoard/admin/security-event/index.vue', 'board:oauth:security:audit']
]
for (const [relativePath, permission] of candidatePages) {
  const source = read(relativePath)
  assert.match(source, /福帮手 FBSir/)
  assert.match(source, /isBoardPortalCandidateEnabled/)
  assert.match(source, /候选功能默认关闭/)
  assert.doesNotMatch(source, /refreshToken|accessToken|authorizationCode|clientSecret|stateKeyBase64/)
  assert.doesNotMatch(source, /<el-button[^>]*>\s*断开/s)
  if (permission) assert.ok(source.includes(permission), `${relativePath} missing permission gate`)
}

const meConnectorSource = read(
  'src/views/business/independentBoard/me/connector/index.vue'
)
assert.equal((meConnectorSource.match(/formatBoardCandidateReference\(connector\./g) || []).length, 3)
const meSecuritySource = read(
  'src/views/business/independentBoard/me/security/index.vue'
)
assert.equal((meSecuritySource.match(/formatBoardCandidateReference\(scope\.row\./g) || []).length, 4)
assert.doesNotMatch(meSecuritySource,
  /<el-table-column\s+prop="(?:clientRef|familyRef|bindingRef|correlationRef)"/)
assert.match(meSecuritySource, /该回执模型未提供/)
assert.match(meSecuritySource, /const MAX_SECURITY_RECEIPTS = 5000/)
assert.match(meSecuritySource, /combined\.length > MAX_SECURITY_RECEIPTS/)
assert.match(meSecuritySource,
  /} catch \{\s+if \(sequence !== requestSequence\) return\s+clearReceipts\(\)/)

const adminCandidateShell = read(
  'src/views/business/independentBoard/admin/components/CandidateReadTable.vue'
)
assert.match(adminCandidateShell, /checkRole\(\['admin'\]\)/)
assert.match(adminCandidateShell, /checkPermi\(\[props\.permission\]\)/)
assert.match(adminCandidateShell, /checkPermi\(\['board:tenant:query'\]\)/)
assert.doesNotMatch(adminCandidateShell, /listEnterprise/)
assert.doesNotMatch(adminCandidateShell, /pageSize:\s*1000/)
assert.match(adminCandidateShell, /parseBoardTenant/)
assert.match(adminCandidateShell, /loadTenantNextPage/)
assert.match(adminCandidateShell, /const MAX_TENANT_OPTIONS = 500/)
assert.match(adminCandidateShell, /formatBoardCandidateReference\(value\)/)
assert.match(adminCandidateShell, /:type="resolveTagType\(scope\.row, column\)"/)
assert.match(adminCandidateShell, /COMPROMISED:\s*'danger'/)
assert.match(adminCandidateShell, /column\.nullText/)
assert.match(adminCandidateShell, /const MAX_CANDIDATE_RECORDS = 5000/)
assert.match(adminCandidateShell, /combined\.length > MAX_CANDIDATE_RECORDS/)
assert.match(adminCandidateShell,
  /combined\.length === MAX_CANDIDATE_RECORDS && page\.truncated/)
assert.match(adminCandidateShell, /catch \{[\s\S]*?clearData\(\)[\s\S]*?errorMessage\.value/)
const adminSecuritySource = read(
  'src/views/business/independentBoard/admin/security-event/index.vue'
)
assert.match(adminSecuritySource, /nullText:\s*'该回执模型未提供'/)

for (const envFile of ['.env.development', '.env.staging', '.env.production']) {
  assert.match(read(envFile), /^VITE_FBSIR_BOARD_PORTAL_CANDIDATE=false$/m)
}

const candidateMenuSql = read('../sql/update_20260721_independent_board_portal_candidate_menu.sql')
for (const component of [
  'business/independentBoard/me/connector/index',
  'business/independentBoard/admin/oauth-client/index',
  'business/independentBoard/admin/oauth-family/index',
  'business/independentBoard/admin/connector-binding/index'
]) {
  assert.ok(candidateMenuSql.includes(component), `candidate menu missing ${component}`)
}
assert.match(candidateMenuSql, /@u3w_enable_independent_board_w4b2c_candidate/)
assert.match(candidateMenuSql, /board:tenant:query/)
assert.doesNotMatch(candidateMenuSql,
  /business\/independentBoard\/(?:me\/security|admin\/security-event)/)

const controllerRoot = path.resolve(
  uiRoot,
  '..',
  'FBSir-business/src/main/java/com/wx/fbsir/business/board/controller'
)
const controllerSources = fs.readdirSync(controllerRoot)
  .filter(name => name.endsWith('.java'))
  .map(name => fs.readFileSync(path.join(controllerRoot, name), 'utf8'))
  .join('\n')
assert.doesNotMatch(controllerSources, /\/oauth2\/|\/\.well-known\/|\/fbs-mcp\/mcp/)

const verifierSource = fs.readFileSync(fileURLToPath(import.meta.url), 'utf8')
const directAssertionCallSites = (verifierSource.match(/\bassert\.[A-Za-z]+\(/g) || []).length
console.log(`Independent Board W4b.2 frontend source candidate passed (${directAssertionCallSites} direct assertion call sites): default-off, GET-only, safe-projection and no-menu gates are green; backend and public-route closure require separate evidence.`)
