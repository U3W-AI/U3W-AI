import request from '@/utils/request'

// 查询当前用户可查看的知识库列表（自己的 + 公共模板）
export function getUserKnowledgeList() {
  return request({
    url: '/api/kb/user/knowledge',
    method: 'get'
  })
}

// 查询用户收藏的知识库列表
export function getFavoriteKnowledgeList() {
  return request({
    url: '/api/kb/user/favorite',
    method: 'get'
  })
}

// 查询公共模板列表
export function getPublicTemplates() {
  return request({
    url: '/api/kb/public',
    method: 'get'
  })
}

// 查询知识库详情
export function getKnowledgeDetail(kbId) {
  return request({
    url: '/api/kb/base',
    method: 'get',
    params: { kbId }
  })
}

// 新增知识库
export function createKnowledgeBase(data) {
  return request({
    url: '/api/kb/base',
    method: 'post',
    data
  })
}

// 修改知识库
export function updateKnowledgeBase(data) {
  return request({
    url: '/api/kb/base',
    method: 'put',
    data
  })
}

// 删除知识库（单个）
export function deleteKnowledgeBase(kbId) {
  return request({
    url: '/api/kb/base/' + kbId,
    method: 'delete'
  })
}

// 收藏/取消收藏知识库
export function toggleFavoriteKnowledge(kbId, userId) {
  return request({
    url: '/api/kb/favorite',
    method: 'post',
    params: { kbId, userId }
  })
}

// 知识库上传到元器/企业微信机器人
export function uploadKnowledgeBase(kbId, uploadType, agentName, robotName, teamName) {
  return request({
    url: '/api/kb/upload',
    method: 'post',
    params: { kbId, uploadType, agentName, robotName, teamName }
  })
}

// 本地文档上传到元器/企业微信机器人（multipart/form-data）
export function uploadLocalDocument(file, uploadType, agentName, robotName, kbName, teamName) {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('uploadType', uploadType)
  if (agentName) {
    formData.append('agentName', agentName)
  }
  if (robotName) {
    formData.append('robotName', robotName)
  }
  if (kbName) {
    formData.append('kbName', kbName)
  }
  if (teamName) {
    formData.append('teamName', teamName)
  }
  return request({
    url: '/api/kb/file/upload',
    method: 'post',
    data: formData,
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

// 根据知识库ID列表查询知识库（前端实现，通过循环调用单个查询接口）
export async function getKnowledgeBasesByIds(kbIds) {
  if (!kbIds || kbIds.length === 0) {
    return { data: [] }
  }
  const results = []
  for (const kbId of kbIds) {
    try {
      const res = await getKnowledgeDetail(kbId)
      if (res.data) {
        results.push(res.data)
      }
    } catch (e) {
      console.error(`获取知识库 ${kbId} 失败:`, e)
    }
  }
  return { data: results }
}

// 查询用户扩展信息（包含知识库空间信息）
export function getUserExtendInfo(userId) {
  return request({
    url: '/api/kb/account/' + userId,
    method: 'get'
  })
}

// 修改账户权限
export function updateAccountPermission(userId, permissionType, isOpen) {
  return request({
    url: '/api/kb/account/permission',
    method: 'put',
    params: { userId, permissionType, isOpen }
  })
}

// 获取所有用户的权限信息列表
export function getAllUserPermissions() {
  return request({
    url: '/api/kb/account/permissions',
    method: 'get'
  })
}

// 修改空间限额
export function updateSpaceQuota(userId, quota) {
  return request({
    url: '/api/kb/space/quota',
    method: 'put',
    params: { userId, quota }
  })
}