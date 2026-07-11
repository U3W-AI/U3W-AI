import request from '@/utils/request'

// 查询微信公众号配置列表
export function listOfficeAccount(query) {
  return request({
    url: '/business/office-account/list',
    method: 'get',
    params: query
  })
}

// 查询微信公众号配置详细
export function getOfficeAccount(id) {
  return request({
    url: '/business/office-account/' + id,
    method: 'get'
  })
}

// 获取当前用户的微信公众号配置
export function getMyOfficeAccount() {
  return request({
    url: '/business/office-account/my-config',
    method: 'get'
  })
}

// 新增微信公众号配置
export function addOfficeAccount(data) {
  return request({
    url: '/business/office-account',
    method: 'post',
    data: data
  })
}

// 修改微信公众号配置
export function updateOfficeAccount(data) {
  return request({
    url: '/business/office-account',
    method: 'put',
    data: data
  })
}

// 删除微信公众号配置
export function delOfficeAccount(id) {
  return request({
    url: '/business/office-account/' + id,
    method: 'delete'
  })
}

// 发布文章到微信公众号草稿箱
export function publishToWechat(articleId, contentType, layoutedContent) {
  const data = contentType === 'layout' && layoutedContent ? { layoutedContent } : {}
  return request({
    url: `/business/office-account/publish/${articleId}/${contentType}`,
    method: 'post',
    data
  })
}

// 查询文章的发布记录
export function getPublishRecords(articleId) {
  return request({
    url: `/business/office-account/publish-records/${articleId}`,
    method: 'get'
  })
}

// 获取服务器公网IP
export function getServerIp() {
  return request({
    url: '/business/office-account/server-ip',
    method: 'get'
  })
}

// 验证公众号配置是否可用
export function verifyConfig() {
  return request({
    url: '/business/office-account/verify',
    method: 'post'
  })
}
