import request from '@/utils/request'

// 查询企业场景包列表
export function listEnterprisePack(query) {
  return request({
    url: '/business/fbs/enterprise/pack/list',
    method: 'get',
    params: query
  })
}

// 查询企业场景包详情
export function getEnterprisePack(id) {
  return request({
    url: '/business/fbs/enterprise/pack/' + id,
    method: 'get'
  })
}

// 分发企业场景包
export function grantEnterprisePack(data) {
  return request({
    url: '/business/fbs/enterprise/pack/grant',
    method: 'post',
    data: data
  })
}

// 回收企业场景包
export function revokeEnterprisePack(data) {
  return request({
    url: '/business/fbs/enterprise/pack/revoke',
    method: 'put',
    data: data
  })
}
