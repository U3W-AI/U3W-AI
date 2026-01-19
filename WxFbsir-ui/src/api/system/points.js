import request from '@/utils/request'

// 查询积分规则列表
export function listPointsRule(query) {
  return request({
    url: '/points/rule/list',
    method: 'get',
    params: query
  })
}

// 查询积分规则详细
export function getPointsRule(ruleId) {
  return request({
    url: '/points/rule/' + ruleId,
    method: 'get'
  })
}

// 根据规则编码查询积分规则
export function getPointsRuleByCode(ruleCode) {
  return request({
    url: '/points/rule/code/' + ruleCode,
    method: 'get'
  })
}

// 新增积分规则
export function addPointsRule(data) {
  return request({
    url: '/points/rule',
    method: 'post',
    data: data
  })
}

// 修改积分规则
export function updatePointsRule(data) {
  return request({
    url: '/points/rule',
    method: 'put',
    data: data
  })
}

// 删除积分规则
export function delPointsRule(ruleIds) {
  return request({
    url: '/points/rule/' + ruleIds,
    method: 'delete'
  })
}

// 导出积分规则
export function exportPointsRule(query) {
  return request({
    url: '/points/rule/export',
    method: 'post',
    params: query
  })
}

