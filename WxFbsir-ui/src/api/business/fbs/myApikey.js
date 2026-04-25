import request from '@/utils/request'

// 查询我的 API Key 列表
export function listMyApiKeys() {
  return request({
    url: '/fbs/business/my/apikey/list',
    method: 'get'
  })
}

// 创建 API Key
export function createApiKey(data) {
  return request({
    url: '/fbs/business/my/apikey/create',
    method: 'post',
    data: data
  })
}

// 禁用/启用 API Key
export function toggleApiKey(id, status) {
  return request({
    url: `/fbs/business/my/apikey/toggle/${id}`,
    method: 'put',
    params: { status }
  })
}

// 删除 API Key
export function deleteApiKey(id) {
  return request({
    url: `/fbs/business/my/apikey/${id}`,
    method: 'delete'
  })
}
