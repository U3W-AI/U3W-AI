import request from '@/utils/request'

// 证书模板列表
export function listCertificateTemplate(query) {
  return request({
    url: '/business/certificateTemplate/list',
    method: 'get',
    params: query
  })
}

// 证书模板详情
export function getCertificateTemplate(id) {
  return request({
    url: '/business/certificateTemplate/' + id,
    method: 'get'
  })
}

// 新增证书模板
export function addCertificateTemplate(data) {
  return request({
    url: '/business/certificateTemplate',
    method: 'post',
    data: data
  })
}

// 修改证书模板
export function updateCertificateTemplate(data) {
  return request({
    url: '/business/certificateTemplate',
    method: 'put',
    data: data
  })
}

// 删除证书模板
export function delCertificateTemplate(id) {
  return request({
    url: '/business/certificateTemplate/' + id,
    method: 'delete'
  })
}