import request from '@/utils/request'

// 查询公众号文章发布记录列表
export function listPublishRecord(query) {
  return request({
    url: '/business/publish-record/list',
    method: 'get',
    params: query
  })
}

// 查询公众号文章发布记录详细信息（包含关联的文章内容）
export function getPublishRecord(id) {
  return request({
    url: '/business/publish-record/' + id,
    method: 'get'
  })
}

// 删除公众号文章发布记录
export function delPublishRecord(ids) {
  return request({
    url: '/business/publish-record/' + ids,
    method: 'delete'
  })
}

// 导出公众号文章发布记录
export function exportPublishRecord(query) {
  return request({
    url: '/business/publish-record/export',
    method: 'post',
    params: query
  })
}
