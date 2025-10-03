import request from '@/utils/request'

export function getPlayWrighDrafts(query) {
  return request({
    url: '/aigc/getPlayWrighDrafts',
    method: 'get',
    params: query
  })
}
export function getNodeLog(query) {
  return request({
    url: '/aigc/getNodeLog',
    method: 'get',
    params: query
  })
}




export function message(data) {
  return request({
    url: '/aigc/message',
    method: 'post',
    data: data
  })
}

// 获取评分拼接提示词
export function getScoreWord() {
  return request({
    url: `/media/getScoreWord`,
    method: 'get'
  })
}

// 获取媒体平台提示词
export function getMediaCallWord(platformId) {
  return request({
    url: `/media/getCallWord/${platformId}`,
    method: 'get'
  })
}


// 获取媒体平台提示词列表
export function getMediaCallWordList(params) {
  return request({
    url: `/media/getCallWordList`,
    method: 'get',
    params,
  })
}


// 更新媒体平台提示词
export function updateMediaCallWord(data) {
  return request({
    url: `/media/updateCallWord`,
    method: 'put',
    data,
  })
}

// 删除媒体平台提示词
export function deleteMediaCallWord(platformIds) {
  return request({
    url: `/media/deleteCallWord`,
    method: 'delete',
    data: platformIds,
  })
}

// 根据ID获取评分提示词
export function getScorePrompt(id) {
  return request({
    url: `/mini/getScorePrompt/${id}`,
    method: 'get'
  })
}

// 根据所有评分提示词
export function getAllScorePrompt() {
  return request({
    url: `/mini/getAllScorePrompt`,
    method: 'get'
  })
}


// 获取评分提示词列表
export function getScorePromptList(params) {
  return request({
    url: `/mini/getScorePromptList`,
    method: 'get',
    params,
  })
}

// 新增评分提示词
export function saveScorePrompt(data) {
  return request({
    url: `/mini/saveScorePrompt`,
    method: 'post',
    data,
  })
}

// 更新评分提示词
export function updateScorePrompt(data) {
  return request({
    url: `/mini/updateScorePrompt`,
    method: 'put',
    data,
  })
}

// 删除评分提示词
export function deleteScorePrompt(ids) {
  return request({
    url: `/mini/deleteScorePrompt`,
    method: 'delete',
    data: ids,
  })
}


export function saveUserChatData(data) {
  return request({
    url: '/aigc/saveUserChatData',
    method: 'post',
    data: data
  })
}

export function getChatHistory(userId,isAll) {
  return request({
    url: '/aigc/getChatHistory?userId=' + userId + '&isAll=' + isAll,
    method: 'get'
  })
}
export function pushAutoOffice(data) {
  return request({
    url: '/wx/publishToOffice',
    method: 'post',
    data: data
  })
}