import request from '@/utils/request'

// 查询主机ID白名单列表
export function listWhitelist(query) {
  return request({
    url: '/business/host/whitelist/list',
    method: 'get',
    params: query
  })
}

// 查询主机ID白名单详细
export function getWhitelist(id) {
  return request({
    url: '/business/host/whitelist/' + id,
    method: 'get'
  })
}

// 新增主机ID白名单
export function addWhitelist(data) {
  return request({
    url: '/business/host/whitelist',
    method: 'post',
    data: data
  })
}

// 修改主机ID白名单
export function updateWhitelist(data) {
  return request({
    url: '/business/host/whitelist',
    method: 'put',
    data: data
  })
}

// 删除主机ID白名单
export function delWhitelist(ids) {
  return request({
    url: '/business/host/whitelist/' + ids,
    method: 'delete'
  })
}

// 导出主机ID白名单
export function exportWhitelist(query) {
  return request({
    url: '/business/host/whitelist/export',
    method: 'post',
    params: query
  })
}

// 手动健康检查
export function manualHealthCheck(id) {
  return request({
    url: '/business/host/whitelist/health-check/' + id,
    method: 'get'
  })
}

// 获取所有主机状态
export function getAllHostStatus() {
  return request({
    url: '/business/host/whitelist/status',
    method: 'get'
  })
}
