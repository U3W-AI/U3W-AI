import request from '@/utils/request'

// 查询企业微信 Webhook 列表
export function listWecomWebhook() {
  return request({
    url: '/business/message/list',
    method: 'get'
  })
}

// 查询企业微信 Webhook 详情
export function getWecomWebhook(id) {
  return request({
    url: '/business/message/get',
    method: 'get',
    params: { id }
  })
}

// 新增企业微信 Webhook
export function addWecomWebhook(data) {
  return request({
    url: '/business/message/insert',
    method: 'post',
    data
  })
}

// 修改企业微信 Webhook
export function updateWecomWebhook(data) {
  return request({
    url: '/business/message/update',
    method: 'post',
    data
  })
}

// 删除企业微信 Webhook
export function delWecomWebhook(id) {
  return request({
    url: '/business/message/delete',
    method: 'post',
    params: { id }
  })
}
