import request from '@/utils/request'

// 查询用户权益列表
export function listUserPack(query) {
  return request({
    url: '/business/fbs/user-pack/list',
    method: 'get',
    params: query
  })
}

// 查询用户权益详细
export function getUserPack(id) {
  return request({
    url: '/business/fbs/user-pack/' + id,
    method: 'get'
  })
}

// 查询用户权益统计
export function getUserPackStats(query) {
  return request({
    url: '/business/fbs/user-pack/stats',
    method: 'get',
    params: query
  })
}
