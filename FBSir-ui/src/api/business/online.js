import request from '@/utils/request'

/**
 * 获取所有在线Engine列表（用于调试工具）
 */
export function getOnlineEngineIds() {
  return request({
    url: '/ws/admin/engines',
    method: 'get'
  }).then(res => {
    if (res.success && res.engines && Array.isArray(res.engines)) {
      return res.engines.map(item => ({
        engineId: item.engineId,
        deviceId: item.deviceId,
        version: item.version,
        status: item.status
      }));
    }
    return [];
  }).catch(error => {
    console.error('获取在线Engine失败:', error);
    return [];
  });
}
