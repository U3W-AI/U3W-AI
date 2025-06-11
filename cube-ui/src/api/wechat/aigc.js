import request from '@/utils/request'

export function getChromeData(query) {
  return request({
    url: '/aigc/getChromeData',
    method: 'get',
    params: query
  })
}
export function getHotKeyWordList(query) {
  return request({
    url: '/aigc/getHotKeyWordList',
    method: 'get',
    params: query
  })
}
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
export function getChromeKeyWord(query) {
  return request({
    url: '/aigc/getChromeKeyWord',
    method: 'get',
    params: query
  })
}
export function getHotKeyWordLog(id) {
  return request({
    url: '/aigc/getHotKeyWordLog?id='+id,
    method: 'get'
  })
}
export function getHotKeyWordById(id) {
  return request({
    url: '/aigc/getHotKeyWordById?id='+id,
    method: 'get'
  })
}
export function pushOffice(ids) {
  return request({
    url: '/mini/pushOffice',
    method: 'post',
    data: ids
  })
}
export function delLink(data) {
  return request({
    url: '/aigc/delLink',
    method: 'post',
    data: data
  })
}
export function updateHotKeyWord(data) {
  return request({
    url: '/aigc/updateHotKeyWord',
    method: 'post',
    data: data
  })
}

export function saveHotKeyWord(data) {
  return request({
    url: '/aigc/saveHotKeyWord',
    method: 'post',
    data: data
  })
}

export function updateArticleLink(data) {
  return request({
    url: '/aigc/updateArticleLink',
    method: 'post',
    data: data
  })
}

export function delBatchLink(ids) {
  return request({
    url: '/aigc/delBatchLink',
    method: 'post',
    data: ids
  })
}

export function message(data) {
  return request({
    url: '/aigc/message',
    method: 'post',
    data: data
  })
}

export function saveUserChatData(data) {
  return request({
    url: '/aigc/saveUserChatData',
    method: 'post',
    data: data
  })
}

export function getChatHistory(userId) {
  return request({
    url: '/aigc/getChatHistory?userId=' + userId,
    method: 'get'
  })
}