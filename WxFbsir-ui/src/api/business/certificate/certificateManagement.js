import request from '@/utils/request'

// 证书管理列表
export function listCertificateManagement(query) {
  return request({
    url: '/business/certificateApplication/issuance/list',
    method: 'get',
    params: query
  })
}

// 证书管理详情
export function getCertificateManagement(id) {
  return request({
    url: '/business/certificateApplication/issuance/' + id,
    method: 'get'
  })
}

// 新增证书管理
export function addCertificateManagement(data) {
  return request({
    url: '/business/certificateApplication/issuance',
    method: 'post',
    data: data
  })
}

// 修改证书管理
export function updateCertificateManagement(data) {
  return request({
    url: '/business/certificateApplication/issuance',
    method: 'put',
    data: data
  })
}

// 删除证书管理
export function delCertificateManagement(id) {
  return request({
    url: '/business/certificateApplication/issuance/' + id,
    method: 'delete'
  })
}