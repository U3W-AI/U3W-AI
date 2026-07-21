import request from '@/utils/request'
import { assertBoardPortalCandidateEnabled } from '@/views/business/independentBoard/portalCandidateModel'

function assertTenantId(tenantId) {
  if (!Number.isSafeInteger(tenantId) || tenantId <= 0) {
    throw new Error('企业上下文无效。')
  }
}

function optionalParam(value) {
  return typeof value === 'string' && value.trim().length > 0 ? value.trim() : undefined
}

function optionalCursor(value) {
  const cursor = optionalParam(value)
  if (cursor === undefined) return undefined
  if (!/^[A-Za-z0-9._~-]{16,512}$/.test(cursor)) {
    throw new Error('分页游标不符合安全合同。')
  }
  return cursor
}

export function getBoardConnectorCandidate(tenantId) {
  assertBoardPortalCandidateEnabled()
  assertTenantId(tenantId)
  return request({
    url: '/my/independent-board/connector',
    method: 'get',
    params: { tenantId }
  })
}

export function listBoardSecurityReceiptsCandidate(tenantId, cursor) {
  assertBoardPortalCandidateEnabled()
  assertTenantId(tenantId)
  return request({
    url: '/my/independent-board/security-receipts',
    method: 'get',
    params: { tenantId, cursor: optionalCursor(cursor) }
  })
}

export function listBoardOAuthClientsCandidate({ status, cursor } = {}) {
  assertBoardPortalCandidateEnabled()
  return request({
    url: '/business/independent-board/oauth/clients',
    method: 'get',
    params: { status: optionalParam(status), cursor: optionalCursor(cursor) }
  })
}

export function listBoardOAuthFamiliesCandidate({ tenantId, status, cursor } = {}) {
  assertBoardPortalCandidateEnabled()
  assertTenantId(tenantId)
  return request({
    url: '/business/independent-board/oauth/families',
    method: 'get',
    params: { tenantId, status: optionalParam(status), cursor: optionalCursor(cursor) }
  })
}

export function listBoardConnectorBindingsCandidate({ tenantId, status, cursor } = {}) {
  assertBoardPortalCandidateEnabled()
  assertTenantId(tenantId)
  return request({
    url: '/business/independent-board/connector-bindings',
    method: 'get',
    params: { tenantId, status: optionalParam(status), cursor: optionalCursor(cursor) }
  })
}

export function listBoardSecurityEventsCandidate({ tenantId, action, cursor } = {}) {
  assertBoardPortalCandidateEnabled()
  assertTenantId(tenantId)
  return request({
    url: '/business/independent-board/oauth/security-events',
    method: 'get',
    params: { tenantId, action: optionalParam(action), cursor: optionalCursor(cursor) }
  })
}
