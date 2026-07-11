import request from '@/utils/request'

// 查询WebSocket连接记录列表
export function listConnectionLog(query) {
  return request({
    url: '/business/host/connection/list',
    method: 'get',
    params: query
  })
}

// 查询WebSocket连接记录详细
export function getConnectionLog(sessionId) {
  return request({
    url: '/business/host/connection/' + sessionId,
    method: 'get'
  })
}

// 删除WebSocket连接记录
export function delConnectionLog(sessionId) {
  return request({
    url: '/business/host/connection/' + sessionId,
    method: 'delete'
  })
}

// 查询在线主机列表（通过现有的WebSocket管理接口）
export function listOnlineEngines() {
  return request({
    url: '/ws/admin/engines',
    method: 'get'
  })
}

// 强制下线指定主机（通过现有的WebSocket管理接口）
export function forceOfflineEngine(engineId) {
  return request({
    url: '/ws/admin/engines/' + engineId,
    method: 'delete'
  })
}

// 向Engine发送健康检查请求
export function healthCheckEngine(engineId) {
  return request({
    url: '/ws/engine/request',
    method: 'post',
    data: {
      engineId: engineId,
      type: 'HEALTH_CHECK',
      timeout: 10
    }
  })
}
