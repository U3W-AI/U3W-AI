import store from '@/store'

// 企业ID最后刷新时间缓存
let lastRefreshTime = 0
// 刷新间隔（毫秒）- 5分钟
const REFRESH_INTERVAL = 5 * 60 * 1000

/**
 * 更新本地存储的企业ID
 * @param {string} corpId 企业ID
 */
function updateLocalCorpId(corpId) {
  if (corpId) {
    // 更新localStorage
    localStorage.setItem('corp_id', corpId)
    // 更新sessionStorage
    sessionStorage.setItem('corp_id', corpId)
    // 更新Vuex store
    store.commit('SET_CORP_ID', corpId)
    console.log('本地企业ID已更新:', corpId)
  }
}

/**
 * 获取本地存储的企业ID
 * @returns {string} 企业ID
 */
function getLocalCorpId() {
  return localStorage.getItem('corp_id') || 
         sessionStorage.getItem('corp_id') || 
         store.state.user.corp_id || ''
}

/**
 * 确保企业ID是最新的
 * 如果距离上次刷新超过指定时间，则自动刷新企业ID
 * @param {boolean} forceRefresh 是否强制刷新
 * @returns {Promise} 返回刷新结果
 */
export function ensureLatestCorpId(forceRefresh = false) {
  const now = Date.now()
  
  // 如果强制刷新或者距离上次刷新时间超过间隔
  if (forceRefresh || (now - lastRefreshTime > REFRESH_INTERVAL)) {
    return store.dispatch('RefreshCorpId').then(res => {
      lastRefreshTime = now
      const newCorpId = res.corpId
      const currentCorpId = getLocalCorpId()
      
      console.log('企业ID已刷新:', newCorpId)
      
      // 更新本地存储的企业ID
      updateLocalCorpId(newCorpId)
      
      // 自动更新全局主机ID值
      if (newCorpId && newCorpId !== currentCorpId) {
        console.log('检测到企业ID变更，自动更新全局主机ID:', newCorpId)
        // 触发全局状态更新事件
        if (window.dispatchEvent) {
          window.dispatchEvent(new CustomEvent('corpIdUpdated', { 
            detail: { corpId: newCorpId, oldCorpId: currentCorpId } 
          }))
        }
      }
      
      return res
    }).catch(error => {
      console.warn('企业ID刷新失败，使用缓存的企业ID:', error)
      // 即使刷新失败也返回当前的企业ID
      const cachedCorpId = getLocalCorpId()
      return Promise.resolve({ corpId: cachedCorpId })
    })
  }
  
  // 返回当前缓存的企业ID
  const cachedCorpId = getLocalCorpId()
  return Promise.resolve({ corpId: cachedCorpId })
}

/**
 * 获取当前企业ID（带自动刷新）
 * @param {boolean} autoRefresh 是否自动刷新
 * @returns {Promise<string>} 企业ID
 */
export function getCorpId(autoRefresh = true) {
  if (autoRefresh) {
    return ensureLatestCorpId().then(res => res.corpId)
  }
  return Promise.resolve(getLocalCorpId())
}

/**
 * 强制刷新企业ID
 * @returns {Promise} 返回刷新结果
 */
export function forceRefreshCorpId() {
  return ensureLatestCorpId(true)
}

/**
 * 手动设置企业ID（用于用户手动修改）
 * @param {string} corpId 企业ID
 */
export function setCorpId(corpId) {
  updateLocalCorpId(corpId)
  
  // 触发全局状态更新事件
  if (window.dispatchEvent) {
    window.dispatchEvent(new CustomEvent('corpIdUpdated', { 
      detail: { corpId: corpId, source: 'manual' } 
    }))
  }
} 