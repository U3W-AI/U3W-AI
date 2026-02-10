<template>
  <div class="login-manager">
    <el-card class="header-card">
      <div class="header-content">
        <div class="header-left">
          <el-icon :size="24" color="#409eff"><Connection /></el-icon>
          <div class="header-info">
            <h2>登录管理器</h2>
            <p>管理所有Engine服务的登录状态</p>
          </div>
        </div>
        <div class="header-right">
          <el-button type="primary" @click="refreshAllLoginStatus" :loading="checking">
            <el-icon><Refresh /></el-icon>
            刷新所有登录状态
          </el-button>
        </div>
      </div>
    </el-card>

    <div class="services-grid">
      <el-card 
        v-for="service in allServices" 
        :key="service.id"
        class="service-card"
        :class="{ 'logged-in': service.loggedIn, 'checking': checkingServices[service.id] }"
        shadow="hover"
      >
        <div class="service-header">
          <div class="service-icon">
            <img v-if="service.icon.type === 'url'" :src="service.icon.value" class="icon-img" />
            <el-icon v-else :size="32"><ChatDotRound /></el-icon>
          </div>
          <div class="service-info">
            <div class="service-name">{{ service.displayName }}</div>
            <div class="service-type">{{ getServiceTypeLabel(service.type) }}</div>
          </div>
          <div class="service-status">
            <el-tag :type="service.loggedIn ? 'success' : 'info'" :effect="service.loggedIn ? 'dark' : 'plain'">
              <el-icon v-if="checkingServices[service.id]" class="is-loading"><Loading /></el-icon>
              <el-icon v-else-if="service.loggedIn"><CircleCheck /></el-icon>
              <el-icon v-else><CircleClose /></el-icon>
              {{ checkingServices[service.id] ? '检测中' : (service.loggedIn ? '已登录' : '未登录') }}
            </el-tag>
          </div>
        </div>

        <div class="service-description">{{ service.description }}</div>

        <div class="service-actions">
          <el-button 
            size="small" 
            type="primary" 
            @click="handleLogin(service.id)"
            :disabled="service.loggedIn || checkingServices[service.id]"
          >
            <el-icon><Link /></el-icon>
            扫码登录
          </el-button>
          <el-button 
            size="small" 
            @click="checkLoginStatus(service.id)"
            :loading="checkingServices[service.id]"
          >
            <el-icon><Refresh /></el-icon>
            检测登录
          </el-button>
        </div>

        <div class="service-meta">
          <span class="meta-item">
            <el-icon><Clock /></el-icon>
            最后检测: {{ service.lastCheckTime || '从未' }}
          </span>
        </div>
      </el-card>
    </div>

    <!-- 登录对话框 -->
    <el-dialog 
      v-model="loginDialogVisible" 
      :title="`${currentServiceName}扫码登录`" 
      width="95%" 
      :max-width="1400"
      center
      :close-on-click-modal="false"
      class="login-dialog"
    >
      <div class="login-dialog-content">
        <div v-if="loginLoading" class="login-loading">
          <el-icon class="is-loading" :size="40"><Loading /></el-icon>
          <p class="loading-text">{{ loginStatusText }}</p>
        </div>
        <div v-if="qrCodeUrl" class="qrcode-container">
          <div class="qrcode-wrapper">
            <img :src="qrCodeUrl" alt="登录二维码" class="qrcode-image" />
          </div>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Connection, Refresh, ChatDotRound, CircleCheck, CircleClose, Link, Clock, Loading } from '@element-plus/icons-vue'
import { getToken } from '@/utils/auth'
import { buildWebSocketUrl } from '@/utils/websocket'
import useUserStore from '@/store/modules/user'
import { 
  ENGINE_CONFIGS, 
  getEngineConfig,
  getServiceCheckLoginMessageType,
  getServiceScanLoginMessageType,
  updateServiceLoginStatus,
  getSortedEngineConfigs,
  saveLoginStatusToStorage,
  restoreLoginStatusFromStorage
} from '@/config/engineConfig'

const userStore = useUserStore()

// 响应式数据
const checking = ref(false)
const checkingServices = ref({})
const loginDialogVisible = ref(false)
const loginLoading = ref(false)
const loginStatusText = ref('')
const qrCodeUrl = ref('')
const currentServiceId = ref('')

// WebSocket连接
let websocket = null

// 计算属性
const allServices = computed(() => getSortedEngineConfigs())

const currentServiceName = computed(() => {
  if (!currentServiceId.value) return ''
  const config = getEngineConfig(currentServiceId.value)
  return config ? config.displayName : ''
})

// 获取服务类型标签
const getServiceTypeLabel = (type) => {
  const labels = {
    'ai': 'AI服务',
    'login': '登录服务',
    'other': '其他服务'
  }
  return labels[type] || '未知'
}

// WebSocket连接
const connectWebSocket = (onConnected) => {
  const hostId = userStore.hostId
  if (!hostId || hostId.trim() === '') {
    ElMessage.error('未配置主机ID，请先在个人中心配置')
    return
  }

  const wsUrl = buildWebSocketUrl({
    path: '/ws/client',
    token: getToken(),
    clientType: 'web'
  })

  console.log('🔌 [登录管理器] 连接WebSocket:', wsUrl)

  websocket = new WebSocket(wsUrl)

  websocket.onopen = () => {
    console.log('✅ [登录管理器] WebSocket连接成功')
    if (onConnected) onConnected()
  }

  websocket.onmessage = (event) => {
    try {
      const message = JSON.parse(event.data)
      console.log('📨 [登录管理器] 收到消息:', message)
      handleWebSocketMessage(message)
    } catch (error) {
      console.error('❌ [登录管理器] 解析消息失败:', error)
    }
  }

  websocket.onerror = (error) => {
    console.error('❌ [登录管理器] WebSocket错误:', error)
    ElMessage.error('WebSocket连接错误')
  }

  websocket.onclose = () => {
    console.log('🔌 [登录管理器] WebSocket连接关闭')
  }
}

// 处理WebSocket消息
const handleWebSocketMessage = (message) => {
  console.log('📨 [登录管理器] 原始消息:', message)
  
  // 🔥 兼容多种消息格式
  const messageType = message.messageType || message.type
  const payload = message.payload || message
  const metadata = message.metadata || {}
  
  console.log('📨 [登录管理器] 解析后 - messageType:', messageType, 'payload:', payload, 'metadata:', metadata)

  // 🔥 处理登录流程的日志消息（TASK_LOG）
  if (messageType === 'TASK_LOG') {
    if (loginDialogVisible.value && payload.message) {
      loginStatusText.value = payload.message
      console.log('📝 [登录管理器] 状态更新:', payload.message)
    }
    return
  }

  // 🔥 处理登录流程的截图消息（TASK_SCREENSHOT）
  if (messageType === 'TASK_SCREENSHOT') {
    const screenshotUrl = payload.screenshotUrl
    console.log('📸 [登录管理器] 收到截图 URL:', screenshotUrl, 'loginDialogVisible:', loginDialogVisible.value)
    if (screenshotUrl && loginDialogVisible.value) {
      qrCodeUrl.value = screenshotUrl
      loginLoading.value = false
      console.log('📸 [登录管理器] 二维码已更新:', screenshotUrl)
    }
    return
  }

  // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  // 🎯 通用登录检测处理（兼容所有后端响应格式）
  // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  // 
  // 支持的识别方式（优先级从高到低）：
  // 1. metadata.requestType 匹配 messageTypes.checkLogin（最可靠）
  // 2. payload.aiType 匹配服务ID（AI服务专用）
  // 3. messageType 直接匹配 messageTypes.checkLogin（旧格式兼容）
  // 4. 当前正在检测的服务（checkingServices）
  //
  // 判断条件：messageType === 'TASK_RESULT' 且不是扫码登录结果
  // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  
  if (messageType === 'TASK_RESULT') {
    // 🔍 识别服务ID（通过多种方式匹配）
    let serviceId = null
    let matchMethod = ''
    
    // 方式1: 通过 metadata.requestType 匹配（最可靠）
    if (metadata.requestType) {
      serviceId = ENGINE_CONFIGS.find(config => 
        config.messageTypes?.checkLogin === metadata.requestType
      )?.id
      if (serviceId) matchMethod = 'metadata.requestType'
    }
    
    // 方式2: 通过 payload.aiType 匹配（AI服务）
    if (!serviceId && payload.aiType) {
      serviceId = ENGINE_CONFIGS.find(config => config.id === payload.aiType)?.id
      if (serviceId) matchMethod = 'payload.aiType'
    }
    
    // 方式3: 通过 messageType 直接匹配（旧格式）
    if (!serviceId && messageType.includes('CHECK_LOGIN')) {
      serviceId = ENGINE_CONFIGS.find(config => 
        config.messageTypes?.checkLogin === messageType
      )?.id
      if (serviceId) matchMethod = 'messageType'
    }
    
    // 方式4: 查找当前正在检测的服务
    if (!serviceId) {
      const checkingServiceIds = Object.keys(checkingServices.value).filter(
        id => checkingServices.value[id] === true
      )
      if (checkingServiceIds.length === 1) {
        serviceId = checkingServiceIds[0]
        matchMethod = 'checkingServices'
      }
    }
    
    // 🎯 处理登录检测结果
    if (serviceId && checkingServices.value[serviceId]) {
      const isLoggedIn = payload?.data?.isLoggedIn || payload?.loggedIn || false
      const userName = payload?.data?.userName || payload?.userName || ''
      const config = getEngineConfig(serviceId)
      
      console.log('✅ [登录管理器] 登录检测结果 - 服务:', config?.displayName, '匹配方式:', matchMethod, '已登录:', isLoggedIn, '用户:', userName)
      
      // 更新状态
      checkingServices.value[serviceId] = false
      updateServiceLoginStatus(serviceId, isLoggedIn)
      saveLoginStatusToStorage()
      
      // 更新最后检测时间
      const service = ENGINE_CONFIGS.find(s => s.id === serviceId)
      if (service) {
        service.lastCheckTime = new Date().toLocaleString()
      }
      
      // 显示结果
      ElMessage.success(`${config?.displayName} 登录状态: ${isLoggedIn ? '已登录' + (userName ? ' (' + userName + ')' : '') : '未登录'}`)
      return // ✅ 处理完成，直接返回
    }
    
    // 如果不是登录检测结果，继续处理扫码登录逻辑
  }
  
  // 🔥 旧格式兼容：直接响应格式（messageType 包含 CHECK_LOGIN）
  if (messageType && messageType.includes('CHECK_LOGIN')) {
    const serviceId = ENGINE_CONFIGS.find(config => 
      config.messageTypes?.checkLogin === messageType
    )?.id

    if (serviceId && checkingServices.value[serviceId]) {
      const isLoggedIn = payload?.loggedIn || payload?.data?.isLoggedIn || false
      const userName = payload?.userName || payload?.data?.userName || ''
      const config = getEngineConfig(serviceId)
      
      console.log('✅ [登录管理器] 登录检测结果（旧格式） - 服务:', config?.displayName, '已登录:', isLoggedIn)
      
      checkingServices.value[serviceId] = false
      updateServiceLoginStatus(serviceId, isLoggedIn)
      saveLoginStatusToStorage()
      
      const service = ENGINE_CONFIGS.find(s => s.id === serviceId)
      if (service) {
        service.lastCheckTime = new Date().toLocaleString()
      }

      ElMessage.success(`${config?.displayName} 登录状态: ${isLoggedIn ? '已登录' + (userName ? ' (' + userName + ')' : '') : '未登录'}`)
      return
    }
  }

  // 处理扫码登录响应
  // 🔥 兼容多种消息类型：TASK_PROGRESS（进度）、TASK_RESULT（最终结果）、DEEPSEEK_SCAN_LOGIN（直接响应）
  if (messageType === 'TASK_PROGRESS' || messageType === 'TASK_RESULT' || (messageType && messageType.includes('SCAN_LOGIN'))) {
    console.log('📨 [登录管理器] 扫码登录消息 - messageType:', messageType, 'payload:', payload)
    
    // 🔥 格式1: TASK_PROGRESS - 进度通知（二维码更新）
    if (messageType === 'TASK_PROGRESS') {
      const qrUrl = payload?.qrCodeUrl
      const status = payload?.status
      const message = payload?.message
      const elapsedSeconds = payload?.elapsedSeconds
      
      if (qrUrl) {
        qrCodeUrl.value = qrUrl
        loginLoading.value = false
        loginStatusText.value = message || `等待扫码（已等待${elapsedSeconds}秒）`
        console.log('📨 [登录管理器] 二维码已更新，等待时间:', elapsedSeconds, '秒')
      }
    }
    
    // 🔥 格式2: TASK_RESULT - 最终结果
    // ✅ 兼容后端实际返回格式：payload.success && payload.data?.success && payload.data?.userName
    else if (messageType === 'TASK_RESULT' && payload?.success && payload?.data?.success && payload?.data?.userName) {
      // ✅ 通过 payload.data.userName 判断这是扫码登录成功结果（而非登录检测结果）
      const data = payload.data
      const message = payload.message
      
      console.log('📨 [登录管理器] 扫码登录成功结果 - data:', data)
      
      // 登录成功
      loginDialogVisible.value = false
      loginLoading.value = false
      qrCodeUrl.value = ''
      
      // 更新登录状态
      updateServiceLoginStatus(currentServiceId.value, true)
      
      // 🔥 保存登录状态到localStorage
      saveLoginStatusToStorage()
      
      const userName = data.userName || ''
      const loginTime = data.loginTime || 0
      ElMessage.success(message || `${currentServiceName.value} 登录成功！用户: ${userName}（耗时${loginTime}秒）`)
      
      console.log('✅ [登录管理器] 登录成功 - 用户:', userName, '耗时:', loginTime, '秒')
      console.log('✅ [登录管理器] 登录状态已保存，无需再次验证（避免数据库锁定）')
    }
    // 登录失败处理
    else if (messageType === 'TASK_RESULT' && payload?.success === false) {
      loginLoading.value = false
      ElMessage.error(`${currentServiceName.value} 登录失败: ${payload?.message || '未知错误'}`)
      console.log('❌ [登录管理器] 登录失败:', payload?.message)
    }
    
    // 🔥 格式3: 直接响应格式（兼容旧格式）
    else if (messageType && messageType.includes('SCAN_LOGIN')) {
      // 后端返回二维码
      if (payload?.qrCodeUrl) {
        qrCodeUrl.value = payload.qrCodeUrl
        loginLoading.value = false
        loginStatusText.value = '请使用微信扫码登录'
      } 
      // 登录成功
      else if (payload?.status === 'success' || (payload?.data?.isLoggedIn === true && payload?.success === true)) {
        loginDialogVisible.value = false
        loginLoading.value = false
        qrCodeUrl.value = ''
        
        // 更新登录状态
        updateServiceLoginStatus(currentServiceId.value, true)
        
        // 🔥 保存登录状态到localStorage
        saveLoginStatusToStorage()
        
        ElMessage.success(`${currentServiceName.value} 登录成功！`)
        console.log('✅ [登录管理器] 登录状态已保存，无需再次验证（避免数据库锁定）')
      } 
      // 登录失败
      else if (payload?.status === 'failed' || payload?.success === false) {
        loginLoading.value = false
        ElMessage.error(`${currentServiceName.value} 登录失败`)
      }
    }
  }
}

// 发送WebSocket消息
const sendMessage = (message) => {
  if (websocket && websocket.readyState === WebSocket.OPEN) {
    websocket.send(JSON.stringify(message))
    console.log('📤 [登录管理器] 发送消息:', message)
  } else {
    console.error('❌ [登录管理器] WebSocket未连接')
    ElMessage.error('WebSocket未连接，请刷新页面')
  }
}

// 检测单个服务登录状态
const checkLoginStatus = (serviceId) => {
  const config = getEngineConfig(serviceId)
  if (!config) return

  checkingServices.value[serviceId] = true

  const hostId = userStore.hostId
  const message = {
    type: config.messageTypes.checkLogin,
    engineId: hostId
  }

  sendMessage(message)
}

// 检测所有服务登录状态
const checkAllLoginStatus = (forceAll = false) => {
  checking.value = true
  
  ENGINE_CONFIGS.forEach(service => {
    if (service.messageTypes?.checkLogin) {
      // 🔥 如果forceAll=true，检测所有服务；否则只检测未登录的服务
      if (forceAll || !service.loggedIn) {
        checkLoginStatus(service.id)
      } else {
        console.log('⏭️ [登录管理器] 跳过已登录服务:', service.displayName)
      }
    }
  })

  setTimeout(() => {
    checking.value = false
  }, 3000)
}

// 刷新所有服务登录状态（包括已登录的）
const refreshAllLoginStatus = () => {
  checkAllLoginStatus(true)
}

// 处理登录
const handleLogin = (serviceId) => {
  const config = getEngineConfig(serviceId)
  if (!config) return

  currentServiceId.value = serviceId
  loginDialogVisible.value = true
  loginLoading.value = true
  loginStatusText.value = '正在获取二维码...'
  qrCodeUrl.value = ''

  const hostId = userStore.hostId
  const message = {
    type: config.messageTypes.scanLogin,
    engineId: hostId
  }

  sendMessage(message)
}

// 生命周期
onMounted(() => {
  // 页面加载时恢复登录状态
  restoreLoginStatusFromStorage()
  
  connectWebSocket(() => {
    // 连接成功后自动检测未登录的服务
    setTimeout(() => {
      checkAllLoginStatus()
    }, 500)
  })
})

onUnmounted(() => {
  if (websocket) {
    websocket.close()
  }
})
</script>

<style lang="scss" scoped>
.login-manager {
  padding: 20px;

  .header-card {
    margin-bottom: 20px;
    border-radius: 12px;

    .header-content {
      display: flex;
      justify-content: space-between;
      align-items: center;

      .header-left {
        display: flex;
        align-items: center;
        gap: 16px;

        .header-info {
          h2 {
            margin: 0 0 4px 0;
            font-size: 20px;
            font-weight: 600;
            color: #303133;
          }

          p {
            margin: 0;
            font-size: 14px;
            color: #909399;
          }
        }
      }
    }
  }

  .services-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
    gap: 16px;

    @media (min-width: 1400px) {
      grid-template-columns: repeat(3, 1fr);
    }

    @media (min-width: 1800px) {
      grid-template-columns: repeat(4, 1fr);
    }
  }

  .service-card {
    border-radius: 12px;
    transition: all 0.3s;
    border: 2px solid #e4e7ed;

    &.logged-in {
      border-color: #67c23a;
      background: linear-gradient(135deg, #f0f9ff 0%, #ffffff 100%);
    }

    &.checking {
      border-color: #409eff;
    }

    &:hover {
      transform: translateY(-4px);
      box-shadow: 0 8px 24px rgba(0, 0, 0, 0.12);
    }

    .service-header {
      display: flex;
      align-items: center;
      gap: 12px;
      margin-bottom: 12px;

      .service-icon {
        width: 48px;
        height: 48px;
        border-radius: 12px;
        background: linear-gradient(135deg, #409eff 0%, #3a8ee6 100%);
        display: flex;
        align-items: center;
        justify-content: center;
        color: #fff;
        flex-shrink: 0;
        overflow: hidden;

        .icon-img {
          width: 100%;
          height: 100%;
          object-fit: cover;
        }
      }

      .service-info {
        flex: 1;
        min-width: 0;

        .service-name {
          font-weight: 600;
          font-size: 16px;
          color: #303133;
          margin-bottom: 4px;
        }

        .service-type {
          font-size: 12px;
          color: #909399;
        }
      }

      .service-status {
        flex-shrink: 0;
      }
    }

    .service-description {
      font-size: 13px;
      color: #606266;
      margin-bottom: 16px;
      line-height: 1.6;
    }

    .service-actions {
      display: flex;
      gap: 8px;
      margin-bottom: 12px;

      .el-button {
        flex: 1;
      }
    }

    .service-meta {
      padding-top: 12px;
      border-top: 1px solid #f0f2f5;

      .meta-item {
        display: flex;
        align-items: center;
        gap: 4px;
        font-size: 12px;
        color: #909399;

        .el-icon {
          font-size: 14px;
        }
      }
    }
  }

  .login-dialog-content {
    text-align: center;
    padding: 0;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    min-height: 400px;

    .login-loading {
      padding: 60px 40px;
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;

      .el-icon {
        display: block;
        margin: 0 auto 20px;
      }

      .loading-text {
        color: #606266;
        font-size: 16px;
        margin: 0;
      }
    }

    .qrcode-container {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      width: 100%;
      height: 100%;
      padding: 0;

      .qrcode-wrapper {
        width: 100%;
        max-width: 100%;
        display: flex;
        align-items: center;
        justify-content: center;
        margin-bottom: 0;
      }

      .qrcode-image {
        width: 100%;
        height: auto;
        max-width: 100%;
        aspect-ratio: 16 / 9;
        object-fit: contain;
        border: none;
        border-radius: 0;
        box-shadow: none;
        background: #fff;
      }

      @media (max-width: 768px) {
        .qrcode-image {
          aspect-ratio: 16 / 9;
        }
      }

      @media (max-width: 480px) {
        .qrcode-image {
          aspect-ratio: 16 / 9;
        }
      }
    }
  }

  :deep(.login-dialog) {
    .el-dialog {
      display: flex;
      flex-direction: column;
      max-height: 90vh;
    }

    .el-dialog__body {
      flex: 1;
      overflow-y: auto;
      padding: 0;
      display: flex;
      align-items: center;
      justify-content: center;
    }

    .el-dialog__header {
      padding: 20px;
      border-bottom: 1px solid #e4e7ed;
    }
  }
}
</style>
