import request from '@/utils/request'

// 发送AI请求
export function sendAiRequest(data) {
  return request({
    url: '/aigc/request',
    method: 'post',
    data: data
  })
}

// 获取AI列表
export function getAiList() {
  return request({
    url: '/aigc/ai/list',
    method: 'get'
  })
}

// 查询聊天历史
export function getChatHistory(query) {
  return request({
    url: '/aigc/chat/history',
    method: 'get',
    params: query
  })
}

// 获取最近聊天
export function getLatestChat() {
  return request({
    url: '/aigc/chat/latest',
    method: 'get'
  })
}

// 检查用户主机状态
export function checkUserHostStatus() {
  return request({
    url: '/aigc/user/host-status',
    method: 'get'
  })
}

// 保存草稿
export function saveDraft(data) {
  return request({
    url: '/aigc/draft/save',
    method: 'post',
    data: data
  })
}

// 保存聊天数据
export function saveChatData(data) {
  return request({
    url: '/aigc/chat/save',
    method: 'post',
    data: data
  })
}
