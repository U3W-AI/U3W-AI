import request from '@/utils/request'

// 查询企业列表
export function listEnterprise(query) {
  return request({
    url: '/business/fbs/enterprise/list',
    method: 'get',
    params: query
  })
}

// 查询企业详情
export function getEnterprise(id) {
  return request({
    url: '/business/fbs/enterprise/' + id,
    method: 'get'
  })
}

// 新增企业
export function addEnterprise(data) {
  return request({
    url: '/business/fbs/enterprise',
    method: 'post',
    data: data
  })
}

// 修改企业
export function updateEnterprise(data) {
  return request({
    url: '/business/fbs/enterprise',
    method: 'put',
    data: data
  })
}

// 禁用企业
export function disableEnterprise(data) {
  return request({
    url: '/business/fbs/enterprise/disable',
    method: 'put',
    data: data
  })
}
