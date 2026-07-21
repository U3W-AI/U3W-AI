import {
  assertBoardPortalCandidateEnabled,
  isBoardPortalCandidateEnabled
} from '../../../utils/independentBoardPortalCandidate.js'

export {
  assertBoardPortalCandidateEnabled,
  isBoardPortalCandidateEnabled
}

export const BOARD_CONNECTOR_SCOPES = Object.freeze([
  'identity.read',
  'entitlement.read',
  'board.meeting.reserve',
  'board.receipt.write'
])

const CONNECTOR_STATES = Object.freeze([
  'NOT_CONNECTED', 'PENDING_ACTIVATION',
  'ACTIVE', 'REAUTH_REQUIRED', 'UNKNOWN'
])
const CLIENT_STATUSES = Object.freeze(['ACTIVE', 'REVOKED', 'EXPIRED'])
const FAMILY_STATUSES = Object.freeze([
  'PENDING_BINDING', 'ACTIVE', 'REVOKED', 'COMPROMISED', 'EXPIRED'
])
const BINDING_STATUSES = Object.freeze(['ACTIVE', 'REVOKED', 'COMPROMISED'])
const TENANT_STATUSES = Object.freeze(['ACTIVE', 'DISABLED'])
const CONSENT_INTENTS = Object.freeze(['FIRST_CONNECT', 'EXPLICIT_REAUTHORIZATION'])
const CONNECTOR_SECURITY_ACTIONS = Object.freeze([
  'CONNECTOR_BINDING_VERIFIED', 'CONNECTOR_BINDING_REVOKED'
])
const SECURITY_ACTIONS = Object.freeze([
  ...CONNECTOR_SECURITY_ACTIONS,
  'AUTHORIZATION_APPROVED', 'AUTHORIZATION_DENIED',
  'AUTHORIZATION_CODE_ISSUED', 'AUTHORIZATION_CODE_REPLAY_DETECTED',
  'TOKEN_FAMILY_CREATED', 'TOKEN_FAMILY_ACTIVATED', 'TOKEN_FAMILY_REAUTHORIZED',
  'TOKEN_FAMILY_ROTATED', 'TOKEN_FAMILY_REVOKED', 'TOKEN_FAMILY_COMPROMISED',
  'REFRESH_REPLAY_DETECTED'
])
const ACTOR_TYPES = Object.freeze(['USER', 'CLIENT', 'SYSTEM'])
const EVIDENCE_LEVELS = Object.freeze([
  'CURRENT_READ_COMPLETE', 'CURRENT_READ_INCOMPLETE', 'ACTION_COMPLETED'
])
const AUTHORIZATION_SECURITY_ACTIONS = Object.freeze([
  'AUTHORIZATION_APPROVED', 'AUTHORIZATION_DENIED', 'AUTHORIZATION_CODE_ISSUED'
])
const FAMILY_WITHOUT_BINDING_SECURITY_ACTIONS = Object.freeze([
  'TOKEN_FAMILY_CREATED'
])
const FAMILY_WITH_BINDING_SECURITY_ACTIONS = Object.freeze([
  'TOKEN_FAMILY_ACTIVATED', 'TOKEN_FAMILY_REAUTHORIZED', 'TOKEN_FAMILY_ROTATED'
])
const FAMILY_OPTIONAL_BINDING_SECURITY_ACTIONS = Object.freeze([
  'AUTHORIZATION_CODE_REPLAY_DETECTED',
  'TOKEN_FAMILY_REVOKED', 'TOKEN_FAMILY_COMPROMISED',
  'REFRESH_REPLAY_DETECTED'
])
const USER_SECURITY_ACTIONS = Object.freeze([
  ...AUTHORIZATION_SECURITY_ACTIONS,
  'TOKEN_FAMILY_ACTIVATED', 'TOKEN_FAMILY_REAUTHORIZED'
])
const CLIENT_SECURITY_ACTIONS = Object.freeze([
  'AUTHORIZATION_CODE_REPLAY_DETECTED', 'TOKEN_FAMILY_CREATED',
  'TOKEN_FAMILY_ROTATED', 'TOKEN_FAMILY_REVOKED', 'TOKEN_FAMILY_COMPROMISED',
  'REFRESH_REPLAY_DETECTED'
])

const CONNECTOR_KEYS = Object.freeze([
  'tenantId', 'memberId', 'uiState', 'effectivePlanCode', 'clientRef',
  'familyRef', 'bindingRef', 'scopes', 'issuedAt', 'expiresAt', 'lastSeenAt',
  'version', 'evidenceLevel'
])
const TENANT_KEYS = Object.freeze(['tenantId', 'tenantLabel', 'status'])
const CLIENT_KEYS = Object.freeze([
  'clientRef', 'displayName', 'status', 'redirectUri', 'grantTypes', 'responseTypes', 'scopes',
  'registeredAt', 'expiresAt', 'terminatedAt', 'version',
  'metadataDigestRef', 'registrationSourceDigestRef'
])
const FAMILY_KEYS = Object.freeze([
  'familyRef', 'tenantId', 'tenantLabel', 'memberLabel', 'userLabel',
  'clientRef', 'consentIntent', 'status', 'currentRefreshGeneration',
  'bindingRef', 'issuedAt', 'activatedAt', 'expiresAt', 'terminatedAt', 'version'
])
const BINDING_KEYS = Object.freeze([
  'bindingRef', 'tenantId', 'tenantLabel', 'memberLabel', 'userLabel',
  'productCode', 'sourceCode', 'connectorCode', 'status', 'scopes',
  'verificationMethod', 'verifiedAt', 'lastSeenAt', 'validUntil', 'revokedAt',
  'clientRef', 'subjectDigestRef', 'version', 'entitlementActive',
  'familyActive', 'vipEffective'
])
const SECURITY_EVENT_KEYS = Object.freeze([
  'eventRef', 'tenantId', 'occurredAt', 'action', 'actorType', 'tenantLabel',
  'memberLabel', 'clientRef', 'familyRef', 'bindingRef', 'correlationRef',
  'evidenceLevel', 'reasonCode'
])
const ENVELOPE_KEYS = Object.freeze(['records', 'limit', 'truncated', 'nextCursor'])

function fail(message) {
  throw new Error(message)
}

function isPlainObject(value) {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
}

function isPositiveInteger(value) {
  return Number.isSafeInteger(value) && value > 0
}

function isNonNegativeInteger(value) {
  return Number.isSafeInteger(value) && value >= 0
}

function isText(value) {
  return typeof value === 'string' && value.trim().length > 0
}

function isNullableText(value) {
  return value === null || isText(value)
}

function isRef(value) {
  return typeof value === 'string' && /^[A-Za-z0-9][A-Za-z0-9._:-]{3,191}$/.test(value)
}

function isNullableRef(value) {
  return value === null || isRef(value)
}

function hasSecurityEventLineage(value) {
  if (CONNECTOR_SECURITY_ACTIONS.includes(value.action)) {
    return value.actorType === 'USER'
      && isRef(value.clientRef) && value.familyRef === null
      && isRef(value.bindingRef) && value.correlationRef === null
  }
  if (!isRef(value.clientRef) || !isRef(value.correlationRef)) return false
  if (AUTHORIZATION_SECURITY_ACTIONS.includes(value.action)) {
    return value.familyRef === null && value.bindingRef === null
  }
  if (FAMILY_WITHOUT_BINDING_SECURITY_ACTIONS.includes(value.action)) {
    return isRef(value.familyRef) && value.bindingRef === null
  }
  if (FAMILY_WITH_BINDING_SECURITY_ACTIONS.includes(value.action)) {
    return isRef(value.familyRef) && isRef(value.bindingRef)
  }
  if (FAMILY_OPTIONAL_BINDING_SECURITY_ACTIONS.includes(value.action)) {
    return isRef(value.familyRef) && isNullableRef(value.bindingRef)
  }
  return false
}

function hasSecurityEventActor(value) {
  if (CONNECTOR_SECURITY_ACTIONS.includes(value.action)
      || USER_SECURITY_ACTIONS.includes(value.action)) {
    return value.actorType === 'USER'
  }
  if (CLIENT_SECURITY_ACTIONS.includes(value.action)) {
    return value.actorType === 'CLIENT'
  }
  return false
}

function isDigestRef(value) {
  return typeof value === 'string' && /^sha256:[0-9a-f]{8,64}$/.test(value)
}

function isDateTime(value, nullable = false) {
  return nullable && value === null
    ? true
    : isText(value) && !Number.isNaN(Date.parse(value))
}

function isBefore(left, right) {
  return isDateTime(left) && isDateTime(right) && Date.parse(left) < Date.parse(right)
}

function isAtOrBefore(left, right) {
  return isDateTime(left) && isDateTime(right) && Date.parse(left) <= Date.parse(right)
}

function isOpaqueCursor(value) {
  return typeof value === 'string' && /^[A-Za-z0-9._~-]{16,512}$/.test(value)
}

function assertExactKeys(value, keys, label) {
  if (!isPlainObject(value)) fail(`${label}不是对象，已按安全合同拒绝。`)
  const actual = Object.keys(value).sort()
  const expected = [...keys].sort()
  if (actual.length !== expected.length
      || actual.some((key, index) => key !== expected[index])) {
    fail(`${label}字段集合不符合安全合同。`)
  }
}

function hasExactScopes(value, allowEmpty = false) {
  if (!Array.isArray(value)) return false
  if (allowEmpty && value.length === 0) return true
  return value.length === BOARD_CONNECTOR_SCOPES.length
    && value.every((scope, index) => scope === BOARD_CONNECTOR_SCOPES[index])
}

function hasExactValues(value, expected) {
  return Array.isArray(value)
    && value.length === expected.length
    && value.every((item, index) => item === expected[index])
}

function freezeRecord(value) {
  const record = { ...value }
  for (const key of ['scopes', 'grantTypes', 'responseTypes']) {
    if (Array.isArray(record[key])) record[key] = Object.freeze([...record[key]])
  }
  return Object.freeze(record)
}

function assertTenant(value, expectedTenantId, label) {
  if (!isPositiveInteger(expectedTenantId) || value.tenantId !== expectedTenantId) {
    fail(`${label}包含跨企业数据。`)
  }
}

function assertLoopbackRedirect(value) {
  const match = typeof value === 'string'
    ? /^http:\/\/127\.0\.0\.1:([1-9][0-9]{3,4})\/oauth\/callback$/.exec(value)
    : null
  const port = match ? Number(match[1]) : 0
  if (!match || !Number.isSafeInteger(port) || port < 1024 || port > 65535) {
    fail('OAuth 客户端回调地址不符合安全合同。')
  }
}

export function formatBoardCandidateReference(value) {
  if (!isText(value)) return '未记录'
  const reference = value.trim()
  if (reference.length <= 14) return reference
  const visiblePrefixLength = reference.startsWith('sha256:') ? 15 : 8
  return `${reference.slice(0, visiblePrefixLength)}…${reference.slice(-4)}`
}

export function connectorActionPolicy(state) {
  const policies = {
    NOT_CONNECTED: ['RETURN_TO_WORKBUDDY', true, false, false],
    PENDING_ACTIVATION: ['REFRESH', true, false, false],
    ACTIVE: ['REFRESH', true, false, false],
    REAUTH_REQUIRED: ['RETURN_TO_WORKBUDDY', true, false, false],
    UNKNOWN: ['REFRESH', false, false, false]
  }
  const policy = policies[state] || policies.UNKNOWN
  return Object.freeze({
    primary: policy[0],
    canReturnToWorkBuddy: policy[1],
    canDisconnect: policy[2],
    canReauthorize: policy[3]
  })
}

export function parseBoardTenant(value) {
  assertExactKeys(value, TENANT_KEYS, '企业选项')
  if (!isPositiveInteger(value.tenantId)
      || !isText(value.tenantLabel)
      || value.tenantLabel !== value.tenantLabel.trim()
      || value.tenantLabel.length > 128
      || !TENANT_STATUSES.includes(value.status)) {
    fail('企业选项包含非法值。')
  }
  return freezeRecord(value)
}

export function parseBoardConnectorView(value, expectedTenantId) {
  assertExactKeys(value, CONNECTOR_KEYS, '连接状态')
  assertTenant(value, expectedTenantId, '连接状态')
  if (!isPositiveInteger(value.memberId)
      || !CONNECTOR_STATES.includes(value.uiState)
      || !['BOARD_FREE', 'BOARD_VIP'].includes(value.effectivePlanCode)
      || !isNullableRef(value.clientRef) || !isNullableRef(value.familyRef)
      || !isNullableRef(value.bindingRef)
      || !isDateTime(value.issuedAt, true) || !isDateTime(value.expiresAt, true)
      || !isDateTime(value.lastSeenAt, true) || !isNonNegativeInteger(value.version)
      || !EVIDENCE_LEVELS.includes(value.evidenceLevel)) fail('连接状态包含非法值。')
  const scopeMayBeEmpty = ['UNKNOWN', 'NOT_CONNECTED'].includes(value.uiState)
  if (!hasExactScopes(value.scopes, scopeMayBeEmpty)) {
    fail('连接状态未包含完整 Scope。')
  }
  if (value.uiState === 'ACTIVE'
      && (value.effectivePlanCode !== 'BOARD_VIP'
        || !isRef(value.clientRef) || !isRef(value.familyRef) || !isRef(value.bindingRef)
        || !isDateTime(value.issuedAt) || !isDateTime(value.expiresAt)
        || !isDateTime(value.lastSeenAt)
        || value.evidenceLevel !== 'ACTION_COMPLETED')) {
    fail('VIP 连接状态未通过完整 current-read。')
  }
  if (value.uiState === 'ACTIVE'
      && (!isAtOrBefore(value.issuedAt, value.lastSeenAt)
        || !isBefore(value.lastSeenAt, value.expiresAt))) {
    fail('ACTIVE 连接状态时序不符合安全合同。')
  }
  if (value.uiState === 'PENDING_ACTIVATION'
      && (value.effectivePlanCode !== 'BOARD_FREE'
        || !isRef(value.clientRef) || !isRef(value.familyRef) || value.bindingRef !== null
        || !isDateTime(value.issuedAt) || !isDateTime(value.expiresAt)
        || !isBefore(value.issuedAt, value.expiresAt) || value.lastSeenAt !== null
        || value.evidenceLevel !== 'ACTION_COMPLETED')) {
    fail('PENDING_ACTIVATION 状态必须回退免费版并保留受约束 family current-read 证据。')
  }
  if (value.uiState === 'UNKNOWN'
      && (value.effectivePlanCode !== 'BOARD_FREE'
        || value.clientRef !== null || value.familyRef !== null || value.bindingRef !== null
        || value.scopes.length !== 0 || value.issuedAt !== null || value.expiresAt !== null
        || value.lastSeenAt !== null || value.version !== 0
        || value.evidenceLevel !== 'CURRENT_READ_INCOMPLETE')) {
    fail('UNKNOWN 状态未按安全模式投影。')
  }
  if (value.uiState === 'NOT_CONNECTED'
      && (value.effectivePlanCode !== 'BOARD_FREE'
        || value.clientRef !== null || value.familyRef !== null || value.bindingRef !== null
        || value.scopes.length !== 0 || value.issuedAt !== null || value.expiresAt !== null
        || value.lastSeenAt !== null || value.version !== 0
        || value.evidenceLevel !== 'CURRENT_READ_COMPLETE')) {
    fail('NOT_CONNECTED 状态未通过完整空拓扑 current-read。')
  }
  if (value.uiState === 'REAUTH_REQUIRED'
      && (value.effectivePlanCode !== 'BOARD_FREE'
        || !isRef(value.clientRef) || !isRef(value.familyRef)
        || !isDateTime(value.issuedAt) || !isDateTime(value.expiresAt)
        || !isBefore(value.issuedAt, value.expiresAt)
        || ((value.bindingRef === null) !== (value.lastSeenAt === null))
        || (value.lastSeenAt !== null
          && (!isAtOrBefore(value.issuedAt, value.lastSeenAt)
            || !isBefore(value.lastSeenAt, value.expiresAt)))
        || !['CURRENT_READ_COMPLETE', 'ACTION_COMPLETED'].includes(value.evidenceLevel))) {
    fail('REAUTH_REQUIRED 状态未通过终态 current-read。')
  }
  if (value.uiState !== 'ACTIVE' && value.effectivePlanCode !== 'BOARD_FREE') {
    fail('非 ACTIVE 连接状态必须保持免费版。')
  }
  return freezeRecord(value)
}

export function parseBoardOAuthClient(value) {
  assertExactKeys(value, CLIENT_KEYS, 'OAuth 客户端')
  if (!isRef(value.clientRef) || value.displayName !== '未验证的本地公共客户端'
      || !CLIENT_STATUSES.includes(value.status)
      || !hasExactScopes(value.scopes) || !isDateTime(value.registeredAt)
      || !isDateTime(value.expiresAt) || !isDateTime(value.terminatedAt, true)
      || !isNonNegativeInteger(value.version) || !isDigestRef(value.metadataDigestRef)
      || !isDigestRef(value.registrationSourceDigestRef)) {
    fail('OAuth 客户端包含非法值或不符合安全合同。')
  }
  if (!hasExactValues(value.grantTypes, ['authorization_code', 'refresh_token'])) {
    fail('OAuth 客户端授权类型不符合固定 profile。')
  }
  if (!hasExactValues(value.responseTypes, ['code'])) {
    fail('OAuth 客户端响应类型不符合固定 profile。')
  }
  if ((value.status === 'ACTIVE' && value.terminatedAt !== null)
      || (value.status !== 'ACTIVE' && value.terminatedAt === null)) {
    fail('OAuth 客户端生命周期与终止时间不一致。')
  }
  if (!isBefore(value.registeredAt, value.expiresAt)
      || (value.terminatedAt !== null && !isAtOrBefore(value.registeredAt, value.terminatedAt))) {
    fail('OAuth 客户端时序不符合安全合同。')
  }
  assertLoopbackRedirect(value.redirectUri)
  return freezeRecord(value)
}

export function parseBoardOAuthFamily(value, expectedTenantId) {
  assertExactKeys(value, FAMILY_KEYS, 'Token Family')
  assertTenant(value, expectedTenantId, 'Token Family')
  if (!isRef(value.familyRef) || !isText(value.tenantLabel)
      || !isText(value.memberLabel) || !isText(value.userLabel) || !isRef(value.clientRef)
      || !CONSENT_INTENTS.includes(value.consentIntent) || !FAMILY_STATUSES.includes(value.status)
      || !isNonNegativeInteger(value.currentRefreshGeneration)
      || !isNullableRef(value.bindingRef) || !isDateTime(value.issuedAt)
      || !isDateTime(value.activatedAt, true) || !isDateTime(value.expiresAt)
      || !isDateTime(value.terminatedAt, true) || !isNonNegativeInteger(value.version)) {
    fail('Token Family 包含非法值。')
  }
  if (value.status === 'PENDING_BINDING'
      && (value.bindingRef !== null || value.activatedAt !== null
        || value.terminatedAt !== null || value.currentRefreshGeneration !== 0)) {
    fail('PENDING_BINDING Token Family 与初始 current-read 不一致。')
  }
  if (value.status === 'ACTIVE'
      && (!isRef(value.bindingRef) || value.activatedAt === null || value.terminatedAt !== null)) {
    fail('ACTIVE Token Family 缺少 binding current-read。')
  }
  if (!['PENDING_BINDING', 'ACTIVE'].includes(value.status)
      && (value.terminatedAt === null
        || ((value.bindingRef === null) !== (value.activatedAt === null)))) {
    fail('Token Family 终态与激活拓扑不一致。')
  }
  if (!isBefore(value.issuedAt, value.expiresAt)
      || (value.activatedAt !== null
        && (!isAtOrBefore(value.issuedAt, value.activatedAt)
          || !isBefore(value.activatedAt, value.expiresAt)))
      || (value.terminatedAt !== null
        && !isAtOrBefore(value.activatedAt || value.issuedAt, value.terminatedAt))) {
    fail('Token Family 时序不符合安全合同。')
  }
  return freezeRecord(value)
}

export function parseBoardConnectorBinding(value, expectedTenantId) {
  assertExactKeys(value, BINDING_KEYS, 'Connector Binding')
  assertTenant(value, expectedTenantId, 'Connector Binding')
  if (!isRef(value.bindingRef) || !isText(value.tenantLabel)
      || !isText(value.memberLabel) || !isText(value.userLabel)
      || value.productCode !== 'FBSIR_INDEPENDENT_BOARD' || value.sourceCode !== 'WORKBUDDY'
      || value.connectorCode !== 'fbs-connector' || !BINDING_STATUSES.includes(value.status)
      || !['MCP_INITIALIZE', 'MCP_TOOLS_LIST'].includes(value.verificationMethod)
      || !isDateTime(value.verifiedAt) || !isDateTime(value.lastSeenAt)
      || !isDateTime(value.validUntil) || !isDateTime(value.revokedAt, true)
      || !isRef(value.clientRef) || !isDigestRef(value.subjectDigestRef)
      || !isPositiveInteger(value.version)
      || typeof value.entitlementActive !== 'boolean'
      || typeof value.familyActive !== 'boolean' || typeof value.vipEffective !== 'boolean') {
    fail('Connector Binding 包含非法值。')
  }
  if (!hasExactScopes(value.scopes)) {
    fail('VIP 连接未包含完整 Scope。')
  }
  if (value.status === 'ACTIVE' && value.revokedAt !== null) {
    fail('ACTIVE Connector Binding 不得包含终止时间。')
  }
  if (value.status !== 'ACTIVE'
      && (value.familyActive || value.vipEffective
        || (['REVOKED', 'COMPROMISED'].includes(value.status) && value.revokedAt === null))) {
    fail('Connector Binding 终态与生效状态不一致。')
  }
  const shouldBeVipEffective = value.status === 'ACTIVE'
    && value.entitlementActive && value.familyActive
  if (value.vipEffective !== shouldBeVipEffective) {
    fail('Connector Binding 的 VIP 生效投影与权威拓扑不一致。')
  }
  if (!isAtOrBefore(value.verifiedAt, value.lastSeenAt)
      || !isBefore(value.lastSeenAt, value.validUntil)
      || (value.revokedAt !== null && !isAtOrBefore(value.lastSeenAt, value.revokedAt))) {
    fail('Connector Binding 时序不符合安全合同。')
  }
  return freezeRecord(value)
}

export function parseBoardSecurityEvent(value, expectedTenantId) {
  assertExactKeys(value, SECURITY_EVENT_KEYS, 'OAuth 安全事件')
  assertTenant(value, expectedTenantId, 'OAuth 安全事件')
  if (!isRef(value.eventRef) || !isDateTime(value.occurredAt)
      || !SECURITY_ACTIONS.includes(value.action) || !ACTOR_TYPES.includes(value.actorType)
      || !isText(value.tenantLabel) || !isText(value.memberLabel)
      || !isNullableRef(value.clientRef) || !isNullableRef(value.familyRef)
      || !isNullableRef(value.bindingRef) || !isNullableRef(value.correlationRef)
      || value.evidenceLevel !== 'ACTION_COMPLETED'
      || value.reasonCode !== null) {
    fail('OAuth 安全事件包含非法值。')
  }
  if (!hasSecurityEventLineage(value)) {
    fail('OAuth 安全事件动作与对象血缘不一致。')
  }
  if (!hasSecurityEventActor(value)) {
    fail('OAuth 安全事件动作与执行主体不一致。')
  }
  return freezeRecord(value)
}

export function parseBoardCandidateEnvelope(value, parseRecord, expectedTenantId) {
  assertExactKeys(value, ENVELOPE_KEYS, '候选列表响应')
  if (!Array.isArray(value.records) || !isPositiveInteger(value.limit) || value.limit > 500
      || value.records.length > value.limit || typeof value.truncated !== 'boolean'
      || !(value.nextCursor === null || isOpaqueCursor(value.nextCursor))
      || (value.truncated && value.nextCursor === null)
      || (!value.truncated && value.nextCursor !== null)
      || typeof parseRecord !== 'function') fail('候选列表响应边界不正确。')
  const seen = new Set()
  const records = value.records.map(record => {
    const parsed = expectedTenantId === undefined
      ? parseRecord(record)
      : parseRecord(record, expectedTenantId)
    const identity = parsed.eventRef || parsed.familyRef || parsed.bindingRef
      || parsed.clientRef || parsed.tenantId
    if (!identity || seen.has(identity)) fail('候选列表响应包含重复记录。')
    seen.add(identity)
    return parsed
  })
  return Object.freeze({
    records: Object.freeze(records),
    limit: value.limit,
    truncated: value.truncated,
    nextCursor: value.nextCursor
  })
}
