import request from '@/utils/request'


export function getUserPointsRecord(data) {

  return request({
    'url': '/mini/getUserPointsRecord',
    headers: {
      isToken: false
    },
    'method': 'post',
    'data': data
  })
}

export function getUserCount(userid) {
  return request({
    'url': '/mini/getUserCount?userId=' + userid,
    headers: {
      isToken: false
    },
    method: 'get',
    timeout: 20000
  })
}


//保存用户模板
export function updateUserPromptTem(data) {
  return request({
    'url': '/mini/updateUserPromptTem',
    headers: {
      isToken: false
    },
    'method': 'post',
    'data': data
  })
}

export function getUserPromptTem(userId,agentId) {
  return request({
    'url': '/mini/getUserPromptTem?userId=' + userId+"&agentId="+agentId,
    headers: {
      isToken: false
    },
    method: 'get',
    timeout: 20000
  })
}
