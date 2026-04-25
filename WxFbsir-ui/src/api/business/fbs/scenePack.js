import request from '@/utils/request'

// 查询场景包列表
export function listScenePack(query) {
  return request({
    url: '/business/fbs/scene-pack/list',
    method: 'get',
    params: query
  })
}

// 查询场景包详细
export function getScenePack(id) {
  return request({
    url: '/business/fbs/scene-pack/' + id,
    method: 'get'
  })
}

// 新增场景包
export function addScenePack(data) {
  return request({
    url: '/business/fbs/scene-pack',
    method: 'post',
    data: data
  })
}

// 修改场景包
export function updateScenePack(data) {
  return request({
    url: '/business/fbs/scene-pack',
    method: 'put',
    data: data
  })
}

// 删除场景包
export function delScenePack(id) {
  return request({
    url: '/business/fbs/scene-pack/' + id,
    method: 'delete'
  })
}

// 发布场景包
export function publishScenePack(data) {
  return request({
    url: '/business/fbs/scene-pack/publish',
    method: 'put',
    data: data
  })
}

// 下架场景包
export function unpublishScenePack(data) {
  return request({
    url: '/business/fbs/scene-pack/unpublish',
    method: 'put',
    data: data
  })
}
