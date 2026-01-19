import request from '@/utils/request'

// 认证申请列表
export function listCertificateApplication(query) {
  return request({
    url: '/business/certificateApplication/list',
    method: 'get',
    params: query
  })
}

// 认证申请详情
export function getCertificateApplication(id) {
  return request({
    url: '/business/certificateApplication/' + id,
    method: 'get'
  })
}

// 新增认证申请
export function addCertificateApplication(data) {
  return request({
    url: '/business/certificateApplication',
    method: 'post',
    data: data
  })
}

// 修改认证申请
export function updateCertificateApplication(data) {
  return request({
    url: '/business/certificateApplication',
    method: 'put',
    data: data
  })
}

// 删除认证申请
export function delCertificateApplication(id) {
  return request({
    url: '/business/certificateApplication/' + id,
    method: 'delete'
  })
}

// 提交认证申请
export function submitApplication(data) {
  return request({
    url: '/business/certificateApplication/submit',
    method: 'post',
    data: data
  })
}

// 撤回认证申请
export function withdrawApplication(id) {
  return request({
    url: '/business/certificateApplication/withdraw/' + id,
    method: 'post'
  })
}

// 获取我的申请列表
export function getMyApplications(query) {
  return request({
    url: '/business/certificateApplication/my',
    method: 'get',
    params: query
  })
}

// 获取用户积分
export function getUserPoints() {
  return request({
    url: '/points/getUserPoints',
    method: 'get'
  })
}

// 领取证书
export function receiveCertificate(applicationId) {
  return request({
    url: '/business/certificateApplication/receive/' + applicationId,
    method: 'post'
  })
}