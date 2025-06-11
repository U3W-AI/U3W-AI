import request from '@/utils/request'

// 查询攻略记录列表
export function listStrategy(query) {
  return request({
    url: '/mini/strategy/list',
    method: 'get',
    params: query
  })
}

// 查询攻略记录详细
export function getStrategy(id) {
  return request({
    url: '/mini/getStrategyDetail?id=' + id,
    method: 'get'
  })
}

// 新增攻略记录
export function addStrategy(data) {
  return request({
    url: '/mini/addStrategy',
    method: 'post',
    data: data
  })
}

// 修改攻略记录
export function updateStrategy(data) {
  return request({
    url: '/mini/editStrategy',
    method: 'put',
    data: data
  })
}
// 生成攻略记录
export function genStrategy(data) {
  return request({
    url: '/mini/genStrategy',
    method: 'post',
    data: data
  })
}
