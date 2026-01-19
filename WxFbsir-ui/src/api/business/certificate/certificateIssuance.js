import request from '@/utils/request'

// 证书发放列表
export function listCertificateIssuance(query) {
  return request({
    url: '/business/certificateApplication/issuance/list',
    method: 'get',
    params: query
  })
}

// 证书发放详情
export function getCertificateIssuance(id) {
  return request({
    url: '/business/certificateApplication/issuance/' + id,
    method: 'get'
  })
}

// 新增证书发放
export function addCertificateIssuance(data) {
  return request({
    url: '/business/certificateApplication/issuance',
    method: 'post',
    data: data
  })
}

// 修改证书发放
export function updateCertificateIssuance(data) {
  return request({
    url: '/business/certificateApplication/issuance',
    method: 'put',
    data: data
  })
}

// 删除证书发放
export function delCertificateIssuance(id) {
  return request({
    url: '/business/certificateApplication/issuance/' + id,
    method: 'delete'
  })
}