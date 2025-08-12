import request from '@/utils/request'

// 查询日志信息（记录方法执行日志）列表
export function listUserLog(query) {
  return request({
    url: '/monitor/userLog/list',
    method: 'get',
    params: query
  })
}

// 查询日志信息（记录方法执行日志）详细
export function getUserLog(id) {
  return request({
    url: '/monitor/userLog/' + id,
    method: 'get'
  })
}

// 新增日志信息（记录方法执行日志）
export function addUserLog(data) {
  return request({
    url: '/monitor/userLog',
    method: 'post',
    data: data
  })
}

// 修改日志信息（记录方法执行日志）
export function updateUserLog(data) {
  return request({
    url: '/monitor/userLog',
    method: 'put',
    data: data
  })
}

// 删除日志信息（记录方法执行日志）
export function delUserLog(id) {
  return request({
    url: '/monitor/userLog/' + id,
    method: 'delete'
  })
}

// 清空日志信息（记录方法执行日志）
export function cleanUserLog() {
  return request({
    url: '/monitor/userLog/clean',
    method: 'delete'
  })
}
