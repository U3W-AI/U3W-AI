import request from '@/utils/request'

// 申请审核列表
export function listApplicationReview(query) {
  return request({
    url: '/business/applicationReview/listWithDetails',
    method: 'get',
    params: query
  })
}

// 申请审核详情（通过审核ID）
export function getApplicationReview(reviewId) {
  return request({
    url: '/business/applicationReview/info/' + reviewId,
    method: 'get'
  })
}

// 申请详情（通过申请ID）
export function getApplicationByApplicationId(applicationId) {
  return request({
    url: '/business/applicationReview/getApplication?applicationId=' + applicationId,
    method: 'get'
  })
}

// 修改申请审核
export function updateApplicationReview(data) {
  return request({
    url: '/business/applicationReview',
    method: 'put',
    data: data
  })
}

// 审核认证申请
export function reviewApplication(data) {
  return request({
    url: '/business/applicationReview/reviewApplication',
    method: 'put',
    data: data
  })
}