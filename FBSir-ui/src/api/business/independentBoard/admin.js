import request from '@/utils/request'

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

export function listIndependentBoardOperations(tenantId) {
  return request({
    url: '/business/independent-board/operations',
    method: 'get',
    params: { tenantId }
  })
}
