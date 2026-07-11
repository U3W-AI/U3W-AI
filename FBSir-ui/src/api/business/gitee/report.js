import request from '@/utils/request'

export function listUsageReport(query) {
  return request({
    url: '/business/gitee/admin/usage-report/list',
    method: 'get',
    params: query
  })
}

export function generateUsageReport(reportDate) {
  return request({
    url: '/business/gitee/admin/usage-report/generate',
    method: 'post',
    params: reportDate ? { reportDate } : {}
  })
}

export function parseResume() {
  return request({
    url: '/resume/parse',
    method: 'get'
  })
}

export function getResumeStatus() {
  return request({
    url: '/resume/status',
    method: 'get'
  })
}

export function getAccessCodeList(query) {
  return request({
    url: '/resume-link/accessCode',
    method: 'get',
    params: query
  })
}

export function addAccessCode(data) {
  return request({
    url: '/resume-link/addAccessCode',
    method: 'post',
    data: data
  })
}

export function batchAddAccessCode(data) {
  return request({
    url: '/resume-link/batchAddAccessCode',
    method: 'post',
    params: data
  })
}

export function getAccessData(query) {
  return request({
    url: '/AccessData',
    method: 'get',
    params: query
  })
}

export function getAccessLog(query) {
  return request({
    url: '/AccessLog',
    method: 'get',
    params: query
  })
}

export function updateDeadLine(time) {
  return request({
    url: '/updateDeadLine',
    method: 'get',
    params: time ? { Time: time } : {}
  })
}

export function updateAccessCodeAvailable(available) {
  return request({
    url: '/resume/access-code/change',
    method: 'get',
    params: { available }
  })
}
