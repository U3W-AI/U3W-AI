import request from '@/utils/request.js'

// 查询系统提示词列表
export function listSystemPrompt() {
  return request({
    url: '/business/prompt/list',
    method: 'get'
  })
}

// 获取系统提示词（前端接口）
export function getSystemPrompt(id) {
  return request({
    url: '/business/prompt/get',
    method: 'get',
    params: { id }
  })
}

// 更新系统提示词
export function updateSystemPrompt(data) {
  return request({
    url: '/business/prompt/update',
    method: 'post',
    data
  })
}

// 新增系统提示词
export function insertSystemPrompt(data) {
  return request({
    url: '/business/prompt/insert',
    method: 'post',
    data
  })
}

// 删除系统提示词
export function deleteSystemPrompt(id) {
  return request({
    url: '/business/prompt/delete',
    method: 'post',
    params: { id }
  })
}

