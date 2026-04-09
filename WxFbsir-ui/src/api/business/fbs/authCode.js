import request from '@/utils/request'

// 查询授权码列表
export function listAuthCode(query) {
  return request({
    url: '/business/fbs/auth-code/list',
    method: 'get',
    params: query
  })
}

// 查询授权码详细
export function getAuthCode(id) {
  return request({
    url: '/business/fbs/auth-code/' + id,
    method: 'get'
  })
}

// 批量生成授权码
export function generateAuthCode(data) {
  return request({
    url: '/business/fbs/auth-code/generate',
    method: 'post',
    data: data
  })
}

// 禁用授权码
export function disableAuthCode(data) {
  return request({
    url: '/business/fbs/auth-code/disable',
    method: 'put',
    data: data
  })
}

// 启用授权码
export function enableAuthCode(data) {
  return request({
    url: '/business/fbs/auth-code/enable',
    method: 'put',
    data: data
  })
}

// 撤销授权码
export function revokeAuthCode(data) {
  return request({
    url: '/business/fbs/auth-code/revoke',
    method: 'put',
    data: data
  })
}
