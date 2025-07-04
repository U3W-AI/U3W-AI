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
    url: '/mini/pushAutoOffice',
    method: 'post',
    data: data
  })
}
