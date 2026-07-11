import request from '@/utils/request'

/**
 * 策略参数映射管理 API
 *
 * 后端控制器：StrategyParamMappingController
 */

// 查询策略列表
export function listStrategy(query) {
  return request({
    url: '/system/strategy/list',
    method: 'get',
    params: query
  })
}

// 根据ID获取详情
export function getStrategy(id) {
  return request({
    url: '/system/strategy/' + id,
    method: 'get'
  })
}

// 根据名称获取详情
export function getStrategyByName(name) {
  return request({
    url: '/system/strategy/name/' + name,
    method: 'get'
  })
}

// 新增策略
export function addStrategy(data) {
  return request({
    url: '/system/strategy',
    method: 'post',
    data: data
  })
}

// 修改策略
export function updateStrategy(data) {
  return request({
    url: '/system/strategy',
    method: 'put',
    data: data
  })
}

// 删除策略
export function delStrategy(id) {
  return request({
    url: '/system/strategy/' + id,
    method: 'delete'
  })
}

