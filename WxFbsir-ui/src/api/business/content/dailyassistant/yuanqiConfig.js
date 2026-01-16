import request from '@/utils/request'

// 查询腾讯元器智能体配置列表
export function listYuanqiConfig(query) {
  return request({
    url: '/system/yuanqi-config/list',
    method: 'get',
    params: query
  })
}

// 获取当前用户的启用配置
export function getMyConfig(businessType = 'daily_assistant') {
  return request({
    url: '/system/yuanqi-config/myConfig',
    method: 'get',
    params: { businessType }
  })
}

// 查询腾讯元器智能体配置详细
export function getYuanqiConfig(id) {
  return request({
    url: '/system/yuanqi-config/' + id,
    method: 'get'
  })
}

// 新增腾讯元器智能体配置
export function addYuanqiConfig(data) {
  return request({
    url: '/system/yuanqi-config',
    method: 'post',
    data: data
  })
}

// 修改腾讯元器智能体配置
export function updateYuanqiConfig(data) {
  return request({
    url: '/system/yuanqi-config',
    method: 'put',
    data: data
  })
}

// 删除腾讯元器智能体配置
export function delYuanqiConfig(ids) {
  return request({
    url: '/system/yuanqi-config/' + ids,
    method: 'delete'
  })
}

// 验证智能体配置
export function verifyYuanqiConfig(data) {
  return request({
    url: '/system/yuanqi-config/verify',
    method: 'post',
    data: data
  })
}

// 导出腾讯元器智能体配置
export function exportYuanqiConfig(query) {
  return request({
    url: '/system/yuanqi-config/export',
    method: 'post',
    params: query
  })
}
