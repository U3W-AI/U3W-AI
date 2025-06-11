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

export function getPromptTem(userId,type) {
  return request({
    'url': '/mini/getPromptTem?userId=' + userId+"&type="+type,
    headers: {
      isToken: false
    },
    method: 'get',
    timeout: 20000
  })
}
export function getYBDraft(taskId) {
  return request({
    'url': '/aigc/getYBDraft?taskId=' + taskId,
    headers: {
      isToken: false
    },
    method: 'get'
  })
}

export function getAgentDraft(taskId) {
  return request({
    'url': '/aigc/getAgentDraft?taskId=' + taskId,
    headers: {
      isToken: false
    },
    method: 'get'
  })
}

//发送任务
export function sendUserPrompt(data) {
  return request({
    'url': '/aigc/sendUserPrompt',
    headers: {
      isToken: false
    },
    'method': 'post',
    'data': data
  })
}
//发送智能体
export function sendAgentPrompt(data) {
  return request({
    'url': '/aigc/sendAgentPrompt',
    headers: {
      isToken: false
    },
    'method': 'post',
    'data': data
  })
}
//发送元宝
export function sendYBPrompt(data) {
  return request({
    'url': '/aigc/sendYBPrompt',
    headers: {
      isToken: false
    },
    'method': 'post',
    'data': data
  })
}

//发送秘塔
export function message(data) {
  return request({
    'url': '/aigc/message',
    headers: {
      isToken: false
    },
    'method': 'post',
    'data': data
  })
}