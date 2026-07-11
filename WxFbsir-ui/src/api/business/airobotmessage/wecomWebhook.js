import request from '@/utils/request'

export function listWecomWebhook(enterpriseId) {
  return request({ url: '/business/message/list', method: 'get', params: { enterpriseId } })
}

export function listWebhookEnterprises() {
  return request({ url: '/business/message/enterprises', method: 'get' })
}

export function getWecomWebhook(id, enterpriseId) {
  return request({ url: '/business/message/get', method: 'get', params: { id, enterpriseId } })
}

export function addWecomWebhook(data) {
  return request({ url: '/business/message/insert', method: 'post', data })
}

export function updateWecomWebhook(data) {
  return request({ url: '/business/message/update', method: 'post', data })
}

export function delWecomWebhook(id, enterpriseId, version) {
  return request({ url: '/business/message/delete', method: 'post', params: { id, enterpriseId, version } })
}

export function sendWecomWebhook(data) {
  return request({ url: '/business/message/send', method: 'post', data })
}
