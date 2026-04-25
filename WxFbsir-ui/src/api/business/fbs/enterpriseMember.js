import request from '@/utils/request'

// 查询企业成员列表
export function listEnterpriseMember(query) {
  return request({
    url: '/business/fbs/enterprise/member/list',
    method: 'get',
    params: query
  })
}

// 查询企业成员详情
export function getEnterpriseMember(id) {
  return request({
    url: '/business/fbs/enterprise/member/' + id,
    method: 'get'
  })
}

// 添加企业成员
export function addEnterpriseMember(data) {
  return request({
    url: '/business/fbs/enterprise/member',
    method: 'post',
    data: data
  })
}

// 移除企业成员
export function removeEnterpriseMember(id) {
  return request({
    url: '/business/fbs/enterprise/member/' + id,
    method: 'delete'
  })
}
