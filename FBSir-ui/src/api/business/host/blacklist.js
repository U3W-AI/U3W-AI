import request from '@/utils/request'

// 查询IP黑名单列表
export function listBlacklist(query) {
  return request({
    url: '/business/host/blacklist/list',
    method: 'get',
    params: query
  })
}

// 查询IP黑名单详细
export function getBlacklist(id) {
  return request({
    url: '/business/host/blacklist/' + id,
    method: 'get'
  })
}

// 新增IP黑名单
export function addBlacklist(data) {
  return request({
    url: '/business/host/blacklist',
    method: 'post',
    data: data
  })
}

// 修改IP黑名单
export function updateBlacklist(data) {
  return request({
    url: '/business/host/blacklist',
    method: 'put',
    data: data
  })
}

// 删除IP黑名单
export function delBlacklist(ids) {
  return request({
    url: '/business/host/blacklist/' + ids,
    method: 'delete'
  })
}

// 导出IP黑名单
export function exportBlacklist(query) {
  return request({
    url: '/business/host/blacklist/export',
    method: 'post',
    params: query
  })
}
