import request from '@/utils/request'

export function listIndependentBoardPlans() {
  return request({
    url: '/business/independent-board/plans',
    method: 'get'
  })
}

export function listIndependentBoardEntitlements(tenantId) {
  return request({
    url: '/business/independent-board/entitlements',
    method: 'get',
    params: { tenantId }
  })
}

export function grantIndependentBoardEntitlement(data) {
  return request({
    url: '/business/independent-board/entitlements',
    method: 'post',
    data
  })
}

export function revokeIndependentBoardEntitlement(data) {
  return request({
    url: '/business/independent-board/entitlements/revoke',
    method: 'post',
    data
  })
}

export function listIndependentBoardEntitlementReceipts(tenantId) {
  return request({
    url: '/business/independent-board/entitlement-receipts',
    method: 'get',
    params: { tenantId }
  })
}

export function listIndependentBoardOperations(tenantId) {
  return request({
    url: '/business/independent-board/operations',
    method: 'get',
    params: { tenantId }
  })
}

export function getIndependentBoardCreditAccount(userId) {
  return request({
    url: `/business/independent-board/credit-accounts/${userId}`,
    method: 'get'
  })
}

export function grantIndependentBoardCredit(data) {
  return request({
    url: '/business/independent-board/credit-operations/grants',
    method: 'post',
    data
  })
}

export function reverseIndependentBoardCredit(data) {
  return request({
    url: '/business/independent-board/credit-operations/reversals',
    method: 'post',
    data
  })
}
