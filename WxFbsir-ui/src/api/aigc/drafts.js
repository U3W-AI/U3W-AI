import request from '@/utils/request'

// 查询草稿列表（旧接口，表格式）
export function listDrafts(query) {
  return request({
    url: '/aigc/drafts',
    method: 'get',
    params: query
  })
}

// 🔥 获取草稿列表（按task_id分组，卡片式显示，参考旧项目cube-admin）
export function getPlayWrighDrafts(query) {
  return request({
    url: '/aigc/getPlayWrighDrafts',
    method: 'get',
    params: query
  })
}

// 查询草稿详细
export function getDraft(draftId) {
  return request({
    url: '/aigc/draft/' + draftId,
    method: 'get'
  })
}

// 新增草稿
export function addDraft(data) {
  return request({
    url: '/aigc/draft/save',
    method: 'post',
    data: data
  })
}

// 修改草稿
export function updateDraft(data) {
  return request({
    url: '/aigc/draft/save',
    method: 'post',
    data: data
  })
}

// 删除草稿
export function delDraft(draftId) {
  return request({
    url: '/aigc/draft/' + draftId,
    method: 'delete'
  })
}
