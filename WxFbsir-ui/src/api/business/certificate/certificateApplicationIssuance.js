import request from '@/utils/request'

// 基于申请表的证书发放列表
export function listCertificateApplicationIssuance(query) {
  return request({
    url: '/business/certificateApplication/issuance/list',
    method: 'get',
    params: query
  })
}

// 基于申请表的证书发放详情
export function getCertificateApplicationIssuance(id) {
  return request({
    url: '/business/certificateApplication/issuance/' + id,
    method: 'get'
  })
}