<template>
  <div class="ai-management-platform">
    <!-- 顶部导航区 -->
    <div class="top-nav">
      <div class="logo-area">
        <el-icon class="logo-icon" :size="32"><ChatDotRound /></el-icon>
        <h1 class="platform-title">AI助手</h1>
      </div>
      <div class="nav-buttons">
        <el-button type="primary" size="small" @click="createNewChat">
          <el-icon><Plus /></el-icon>
          创建新对话
        </el-button>
        <div class="history-button">
          <el-button type="text" @click="showHistoryDrawer">
            <el-icon><Clock /></el-icon>
            历史记录
          </el-button>
        </div>
      </div>
    </div>

    <!-- 🔥 历史记录抽屉（完全参考旧项目cube-ui实现） -->
    <el-drawer title="历史会话记录" v-model="historyDrawerVisible" direction="rtl" size="35%">
      <div class="history-content">
        <div v-if="historyLoading" class="history-loading">
          <el-icon class="is-loading"><Loading /></el-icon>
          <span>加载中...</span>
        </div>
        <!-- 🔥 按日期和chatId分组显示历史记录 -->
        <div v-else-if="chatHistory.length > 0">
          <div v-for="(group, date) in groupedHistory" :key="date" class="history-group">
            <div class="history-date">{{ date }}</div>
            <div class="history-list">
              <div v-for="(item, index) in group" :key="index" class="history-item">
                <!-- 🔥 会话组父记录 -->
                <div class="history-parent" 
                     @click="item.isChatGroup ? toggleHistoryExpansion(item) : loadHistoryItem(item)">
                  <div class="history-header">
                    <!-- 会话组展开/收起箭头 -->
                    <el-icon v-if="item.isChatGroup" 
                             :class="{ 'is-expanded': item.isExpanded }"
                             class="expand-arrow">
                      <ArrowRight />
                    </el-icon>
                    <!-- 单轮对话图标 -->
                    <el-icon v-else class="chat-icon"><ChatDotRound /></el-icon>
                    <div class="history-content-wrapper">
                      <div class="history-prompt">{{ item.userPrompt }}</div>
                      <div class="history-meta">
                        <span class="history-time">{{ formatHistoryTime(item.createTime) }}</span>
                        <span class="history-separator">•</span>
                        <span class="history-chatid" :title="'会话ID: ' + item.chatId">
                          会话 {{ item.chatId ? item.chatId.substring(0, 8) : '' }}
                        </span>
                        <span v-if="item.isChatGroup" class="children-count">
                          • {{ item.totalRounds }}轮对话
                        </span>
                      </div>
                    </div>
                  </div>
                </div>
                
                <!-- 🔥 展开显示各轮对话 -->
                <div v-if="item.isChatGroup && item.children && item.children.length > 0 && item.isExpanded" 
                     class="history-children">
                  <div v-for="(round, roundIndex) in item.children" 
                       :key="roundIndex" 
                       class="history-child-item"
                       @click="loadHistoryItem(round)">
                    <div class="history-child-content">
                      <span class="child-index">第{{ roundIndex + 1 }}轮</span>
                      <div class="history-prompt">{{ round.roundPrompt || round.userPrompt }}</div>
                      <div class="history-meta">
                        <span class="history-time">{{ formatHistoryTime(round.createTime) }}</span>
                        <span class="history-separator">•</span>
                        <span class="ai-count">{{ round.aiResponseCount || 1 }}个AI响应</span>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
        <div v-else class="history-empty">
          <el-icon><Document /></el-icon>
          <p>暂无历史记录</p>
        </div>
      </div>
    </el-drawer>

    <div class="main-content">
      <el-collapse v-model="activeCollapses">
        <!-- AI选择配置 -->
        <el-collapse-item name="ai-selection">
          <template #title>
            <div class="ai-config-header">
              <span>AI选择配置</span>
            </div>
          </template>
          <div class="ai-selection-section">
            <div class="ai-cards">
              <!-- DeepSeek卡片 - 硬编码 -->
              <el-card class="ai-card" :class="{ 'ai-card-not-logged': !deepseekLoggedIn }" shadow="hover">
                <div v-if="!deepseekLoggedIn" class="card-login-overlay">
                  <div class="card-login-message">
                    <el-icon><Warning /></el-icon>
                    <span>未登录</span>
                    <el-button size="small" type="primary" @click="handleDeepSeekLogin">扫码登录</el-button>
                  </div>
                </div>
                <div class="ai-card-header">
                  <div class="ai-left">
                    <div class="ai-avatar">
                      <el-icon :size="32"><ChatDotRound /></el-icon>
                    </div>
                    <div class="ai-name">DeepSeek</div>
                  </div>
                  <div class="ai-status">
                    <el-switch v-model="deepseekEnabled" active-color="#13ce66" inactive-color="#ff4949" :disabled="!deepseekLoggedIn" />
                  </div>
                </div>
                <!-- DeepSeek选项 -->
                <div class="ai-options">
                  <div class="button-options-group">
                    <div class="ai-capabilities">
                      <el-button :type="enableDeepThinking ? 'primary' : 'default'" size="small" :disabled="!deepseekEnabled || !deepseekLoggedIn" @click="enableDeepThinking = !enableDeepThinking">
                        深度思考
                      </el-button>
                      <el-button :type="enableWebSearch ? 'primary' : 'default'" size="small" :disabled="!deepseekEnabled || !deepseekLoggedIn" @click="enableWebSearch = !enableWebSearch">
                        联网搜索
                      </el-button>
                    </div>
                  </div>
                </div>
              </el-card>
            </div>
          </div>
        </el-collapse-item>

        <!-- 提示词输入区 -->
        <el-collapse-item title="提示词输入" name="prompt-input">
          <div class="prompt-input-section">
            <el-input type="textarea" :rows="5" placeholder="请输入提示词，支持Markdown格式" v-model="promptInput" resize="none" class="prompt-input" />
            <div class="prompt-footer">
              <div class="word-count">字数统计: {{ promptInput.length }}</div>
              <el-button type="primary" @click="sendPrompt" :disabled="!canSend" :loading="isSending" class="send-button">
                发送
              </el-button>
            </div>
          </div>
        </el-collapse-item>
      </el-collapse>

      <!-- 🔥 执行状态展示区（支持多AI区分显示，参考旧项目） -->
      <div class="execution-status-section" v-if="taskStarted">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-card class="task-flow-card">
              <template #header>
                <div class="card-header">
                  <span>任务流程</span>
                  <el-tag v-if="allTasksCompleted" type="success" size="small">全部完成</el-tag>
                  <el-tag v-else-if="hasRunningTasks" type="warning" size="small">执行中</el-tag>
                </div>
              </template>
              <div class="task-flow">
                <!-- 🔥 支持多AI任务流程展示 -->
                <div v-for="(ai, aiIndex) in enabledAIs" :key="aiIndex" class="task-item">
                  <div class="task-header" @click="toggleAiExpand(ai)">
                    <div class="header-left">
                      <el-icon class="expand-icon" :class="{ 'is-expanded': ai.isExpanded }">
                        <ArrowRight />
                      </el-icon>
                      <span class="ai-name">{{ ai.name || 'DeepSeek' }}</span>
                    </div>
                    <div class="header-right">
                      <span class="status-text">{{ getStatusText(ai.status || taskStatus) }}</span>
                      <el-icon v-if="(ai.status || taskStatus) === 'running'" class="is-loading"><Loading /></el-icon>
                      <el-icon v-else-if="(ai.status || taskStatus) === 'completed'" color="#67c23a"><CircleCheck /></el-icon>
                      <el-icon v-else-if="(ai.status || taskStatus) === 'failed'" color="#f56c6c"><CircleClose /></el-icon>
                      <el-icon v-else color="#909399"><Clock /></el-icon>
                    </div>
                  </div>
                  <!-- 🔥 进度日志（按AI区分） -->
                  <div class="progress-timeline" v-if="ai.isExpanded !== false && (ai.progressLogs || progressLogs).length > 0">
                    <div class="timeline-scroll">
                      <div v-for="(log, logIndex) in (ai.progressLogs || progressLogs)" :key="logIndex" class="progress-item">
                        <div class="progress-dot" :class="getLogDotClass(log)"></div>
                        <div class="progress-content">
                          <div class="progress-time">{{ formatTime(log.timestamp) }}</div>
                          <div class="progress-text">{{ log.content }}</div>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </el-card>
          </el-col>
          <el-col :span="12">
            <el-card class="screenshots-card">
              <template #header>
                <div class="card-header">
                  <span>执行可视化</span>
                </div>
              </template>
              <div class="screenshots">
                <el-carousel v-if="screenshots.length > 0" :interval="3000" :autoplay="false" indicator-position="outside" height="700px">
                  <el-carousel-item v-for="(screenshot, index) in screenshots" :key="index">
                    <img :src="screenshot" alt="执行截图" class="screenshot-image" @click="showLargeImage(screenshot)" />
                  </el-carousel-item>
                </el-carousel>
                <div v-else class="no-screenshots">
                  <el-icon :size="48"><Picture /></el-icon>
                  <p>等待截图...</p>
                </div>
              </div>
            </el-card>
          </el-col>
        </el-row>
      </div>

      <!-- 结果展示区 -->
      <div class="results-section" v-if="results.length > 0">
        <div class="section-header">
          <h2 class="section-title">执行结果</h2>
          <el-button type="success" size="small" @click="saveToDraft">
            <el-icon><Document /></el-icon>
            保存到草稿
          </el-button>
        </div>
        <el-card>
          <div v-for="(result, index) in results" :key="index" class="result-content">
            <div class="result-header" v-if="result.shareUrl">
              <div class="result-title">{{ result.aiName }}的执行结果</div>
              <el-button size="small" type="primary" @click="openShareUrl(result.shareUrl)">
                <el-icon><Link /></el-icon>
                查看原链接
              </el-button>
            </div>
            <div class="markdown-content" v-html="renderMarkdown(result.content)"></div>
          </div>
        </el-card>
      </div>
    </div>

    <!-- 大图查看对话框 -->
    <el-dialog v-model="showImageDialog" width="90%" center class="image-dialog">
      <img :src="currentLargeImage" alt="大图" class="large-image" />
    </el-dialog>

    <!-- DeepSeek登录对话框 -->
    <el-dialog v-model="loginDialogVisible" title="DeepSeek扫码登录" width="500px" center>
      <div class="login-dialog-content">
        <div v-if="loginLoading" class="login-loading">
          <el-icon class="is-loading" :size="32"><Loading /></el-icon>
          <p>{{ loginStatusText }}</p>
        </div>
        <div v-if="qrCodeUrl" class="qrcode-container">
          <img :src="qrCodeUrl" alt="登录二维码" class="qrcode-image" />
          <p>请使用微信扫码登录DeepSeek</p>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Clock, Loading, Document, Warning, CircleCheck, CircleClose, Link, Picture, ChatDotRound, ArrowRight } from '@element-plus/icons-vue'
import { marked } from 'marked'
import { getToken } from '@/utils/auth'
import { addDraft } from '@/api/aigc/drafts'
import { saveChatData, getChatHistory } from '@/api/aigc/assistant'
import { buildWebSocketUrl } from '@/utils/websocket'

export default {
  name: 'AiAssistant',
  components: {
    Plus, Clock, Loading, Document, Warning, CircleCheck, CircleClose, Link, Picture, ChatDotRound, ArrowRight
  },
  setup() {
    // 响应式数据
    const activeCollapses = ref(['ai-selection', 'prompt-input'])
    const historyDrawerVisible = ref(false)
    const historyLoading = ref(false)
    const chatHistory = ref([])
    const expandedHistoryItems = ref({})  // 🔥 历史记录展开状态
    
    // DeepSeek硬编码配置 - 测试阶段默认已登录
    const deepseekEnabled = ref(true)
    const deepseekLoggedIn = ref(true)  // 测试阶段默认已登录
    const enableDeepThinking = ref(false)
    const enableWebSearch = ref(false)
    
    // 输入和发送状态
    const promptInput = ref('')
    const isSending = ref(false)
    const taskStarted = ref(false)
    const taskStatus = ref('pending')
    const progressLogs = ref([])
    const screenshots = ref([])
    const results = ref([])
    const currentChatId = ref(null)
    const enabledAIs = ref([])  // 🔥 启用的AI列表（支持多AI）
    const isNewChat = ref(true)  // 🔥 是否是新会话
    
    // 🔥 AI会话ID管理（完全参考旧项目cube-admin）
    const userInfoReq = ref({
      chatId: '',
      toneChatId: '',
      ybChatId: '',
      dbChatId: '',
      tyChatId: '',
      deepseekChatId: '',
      maxChatId: '',
      metasoChatId: '',
      kimiChatId: '',
      baiduChatId: '',
      zhzdChatId: '',
      isNewChat: true
    })
    
    // 对话框状态
    const showImageDialog = ref(false)
    const currentLargeImage = ref('')
    const loginDialogVisible = ref(false)
    const loginLoading = ref(false)
    const loginStatusText = ref('')
    const qrCodeUrl = ref('')
    
    // WebSocket连接
    let websocket = null
    let sessionId = null  // 会话ID，用于全链路追踪（与系统的requestId区分）
    
    // 计算属性
    const canSend = computed(() => {
      // 测试阶段：只要有输入内容且未在发送中即可发送
      return promptInput.value.trim().length > 0 && deepseekEnabled.value && !isSending.value
    })
    
    // 🔥 检查是否所有任务完成
    const allTasksCompleted = computed(() => {
      if (!taskStarted.value || enabledAIs.value.length === 0) return false
      return enabledAIs.value.every(ai => ai.status === 'completed' || ai.status === 'failed')
    })
    
    // 🔥 检查是否有任务运行中
    const hasRunningTasks = computed(() => {
      return enabledAIs.value.some(ai => ai.status === 'running')
    })
    
    // 🔥 按日期和chatId分组历史记录（完全参考旧项目cube-ui）
    const groupedHistory = computed(() => {
      const groups = {}
      const chatGroups = {}
      
      // 首先按chatId分组
      chatHistory.value.forEach(item => {
        const cid = item.chatId || 'unknown'
        if (!chatGroups[cid]) {
          chatGroups[cid] = []
        }
        chatGroups[cid].push(item)
      })
      
      // 按chatId聚合，每个chatId作为一个父记录
      Object.entries(chatGroups).forEach(([chatId, chatGroup]) => {
        // 按时间升序排序
        chatGroup.sort((a, b) => {
          const timeA = new Date(a.createTime).getTime()
          const timeB = new Date(b.createTime).getTime()
          return timeA - timeB
        })
        
        // 按userPrompt分组（同一个提问的多个AI响应算一轮）
        const roundGroups = {}
        chatGroup.forEach(record => {
          const prompt = record.userPrompt || '未知提问'
          if (!roundGroups[prompt]) {
            roundGroups[prompt] = []
          }
          roundGroups[prompt].push(record)
        })
        
        // 获取第一条记录用于日期分组
        const firstRecord = chatGroup[0]
        const date = getHistoryDate(firstRecord.createTime)
        
        if (!groups[date]) {
          groups[date] = []
        }
        
        // 将每一轮作为子记录
        const rounds = Object.entries(roundGroups).map(([prompt, roundRecords], roundIndex) => {
          const lastRecord = roundRecords[roundRecords.length - 1]
          let aiResponseCount = 0
          try {
            const recordData = JSON.parse(lastRecord.data)
            aiResponseCount = recordData.results ? recordData.results.length : 0
          } catch (e) {
            aiResponseCount = 1
          }
          
          return {
            ...lastRecord,
            roundIndex: roundIndex,
            roundPrompt: prompt,
            aiResponseCount: aiResponseCount,
            isRound: true
          }
        })
        
        // chatId作为父记录，各轮作为子记录
        groups[date].push({
          ...firstRecord,
          isParent: true,
          isChatGroup: rounds.length > 1,
          totalRounds: rounds.length,
          chatId: chatId,
          isExpanded: expandedHistoryItems.value[chatId] || false,
          children: rounds
        })
      })
      
      return groups
    })
    
    // 方法
    const createNewChat = () => {
      // 🔥 重置所有数据，标记为新会话（实际chatId在发送消息时生成）
      currentChatId.value = null  // 清空chatId，等待发送消息时生成
      isNewChat.value = true
      promptInput.value = ''
      taskStarted.value = false
      taskStatus.value = 'pending'
      progressLogs.value = []
      screenshots.value = []
      results.value = []
      enabledAIs.value = []
      
      // 🔥 重置AI会话ID
      userInfoReq.value = {
        chatId: '',
        toneChatId: '',
        ybChatId: '',
        dbChatId: '',
        tyChatId: '',
        deepseekChatId: '',
        maxChatId: '',
        metasoChatId: '',
        kimiChatId: '',
        baiduChatId: '',
        zhzdChatId: '',
        isNewChat: true
      }
      
      console.log('📝 [新建会话] 已标记为新会话，chatId将在发送消息时生成')
      ElMessage.success('已创建新对话')
    }
    
    const showHistoryDrawer = () => {
      historyDrawerVisible.value = true
      loadChatHistory()
    }
    
    const loadChatHistory = async () => {
      historyLoading.value = true
      try {
        const response = await getChatHistory({ pageNum: 1, pageSize: 50 })
        if (response.rows) {
          chatHistory.value = response.rows
        } else {
          chatHistory.value = []
        }
      } catch (error) {
        console.error('加载历史记录失败:', error)
        chatHistory.value = []
      } finally {
        historyLoading.value = false
      }
    }
    
    // 🔥 加载历史记录项（完全参考旧项目cube-ui，支持上下文复用）
    const loadHistoryItem = (item) => {
      try {
        const historyData = JSON.parse(item.data)
        console.log('📝 [加载历史] 原始item:', item)
        console.log('📝 [加载历史] 解析data:', historyData)
        
        // 🔥 从data.data中提取嵌套数据（Engine返回的结构是 payload.data.xxx）
        const nestedData = historyData.data || {}
        
        // 恢复提示词输入（优先从nestedData.query获取）
        promptInput.value = nestedData.query || historyData.promptInput || item.userPrompt || ''
        
        // 🔥 恢复任务流程（多层级解析）
        // 尝试从多个位置获取enabledAIs: historyData.enabledAIs, historyData.extraParams.enabledAIs
        let enabledAIsData = historyData.enabledAIs
        if (!enabledAIsData && historyData.extraParams && historyData.extraParams.enabledAIs) {
          enabledAIsData = historyData.extraParams.enabledAIs
        }
        
        if (enabledAIsData && enabledAIsData.length > 0) {
          enabledAIs.value = enabledAIsData
        } else {
          // 🔥 根据AI类型动态构造任务流程
          const aiType = historyData.aiType || nestedData.aiType || 'deepseek'
          enabledAIs.value = [{
            name: aiType === 'deepseek' ? 'DeepSeek' : aiType,
            status: 'completed',
            isExpanded: false,
            progressLogs: []
          }]
        }
        
        // 🔥 恢复进度日志（多层级解析）
        let progressLogsData = historyData.progressLogs
        if (!progressLogsData && historyData.extraParams && historyData.extraParams.progressLogs) {
          progressLogsData = historyData.extraParams.progressLogs
        }
        progressLogs.value = progressLogsData || []
        
        // 🔥 按aiType分配日志给对应的AI（支持多AI任务流程显示）
        if (progressLogs.value.length > 0 && enabledAIs.value.length > 0) {
          progressLogs.value.forEach(log => {
            const logAiType = log.aiType || 'deepseek'
            const targetAi = enabledAIs.value.find(ai => 
              ai.name.toLowerCase().includes(logAiType.toLowerCase())
            )
            if (targetAi) {
              if (!targetAi.progressLogs) {
                targetAi.progressLogs = []
              }
              targetAi.progressLogs.push(log)
            }
          })
          console.log('📝 [加载历史] 已按AI类型分配进度日志')
        }
        
        console.log('📝 [加载历史] 任务流程:', enabledAIs.value, '进度日志数:', progressLogs.value.length)
        
        // 恢复主机可视化（从nestedData中获取截图）
        screenshots.value = historyData.screenshots || []
        if (nestedData.conversationScreenshot) {
          screenshots.value.push(nestedData.conversationScreenshot)
        }
        
        // 恢复执行结果
        if (nestedData.answer) {
          results.value = [{
            aiName: 'DeepSeek',
            content: nestedData.answer,
            shareUrl: nestedData.shareUrl,
            chatId: nestedData.chatId,
            query: nestedData.query,
            mode: nestedData.mode
          }]
        } else {
          results.value = historyData.results || []
        }
        
        // 🔥 恢复chatId（区分前端分组ID和AI内部会话ID）
        // item.chatId 是数据库中的前端分组ID，用于关联多轮对话
        // nestedData.chatId 是AI返回的内部会话ID，用于上下文复用
        const restoredChatId = item.chatId || item.id  // 前端分组ID
        currentChatId.value = restoredChatId
        isNewChat.value = false
        
        // 🔥 恢复所有AI会话ID（关键：上下文复用）
        userInfoReq.value.chatId = restoredChatId
        userInfoReq.value.toneChatId = item.toneChatId || ''
        userInfoReq.value.ybChatId = item.ybChatId || ''
        userInfoReq.value.dbChatId = item.dbChatId || ''
        userInfoReq.value.tyChatId = item.tyChatId || ''
        // 🔥 DeepSeek会话ID：优先从nestedData获取（AI上下文复用），其次从数据库字段
        userInfoReq.value.deepseekChatId = nestedData.chatId || item.deepseekChatId || ''
        userInfoReq.value.maxChatId = item.maxChatId || ''
        userInfoReq.value.metasoChatId = item.metasoChatId || ''
        userInfoReq.value.kimiChatId = item.kimiChatId || ''
        userInfoReq.value.baiduChatId = item.baiduChatId || ''
        userInfoReq.value.zhzdChatId = item.zhzdChatId || ''
        userInfoReq.value.isNewChat = false
        
        console.log('📝 [加载历史] 前端分组ID(currentChatId):', restoredChatId)
        console.log('📝 [加载历史] AI上下文ID(deepseekChatId):', userInfoReq.value.deepseekChatId)
        
        // 展开相关区域
        activeCollapses.value = ['ai-selection', 'prompt-input']
        taskStarted.value = true
        taskStatus.value = 'completed'
        
        historyDrawerVisible.value = false
        ElMessage.success('已加载历史对话，可继续对话')
      } catch (error) {
        console.error('解析历史记录失败:', error)
        ElMessage.error('加载历史记录失败')
      }
    }
    
    // 🔥 切换历史记录展开状态
    const toggleHistoryExpansion = (item) => {
      const key = item.chatId
      expandedHistoryItems.value[key] = !expandedHistoryItems.value[key]
    }
    
    // 🔥 获取历史记录日期分组
    const getHistoryDate = (timestamp) => {
      const date = new Date(timestamp)
      const today = new Date()
      const yesterday = new Date(today)
      yesterday.setDate(yesterday.getDate() - 1)
      
      if (date.toDateString() === today.toDateString()) {
        return '今天'
      } else if (date.toDateString() === yesterday.toDateString()) {
        return '昨天'
      } else {
        return date.toLocaleDateString('zh-CN', {
          year: 'numeric',
          month: 'long',
          day: 'numeric'
        })
      }
    }
    
    // 🔥 格式化历史记录时间
    const formatHistoryTime = (timestamp) => {
      if (!timestamp) return ''
      const date = new Date(timestamp)
      return date.toLocaleTimeString('zh-CN', {
        hour: '2-digit',
        minute: '2-digit',
        hour12: false
      })
    }
    
    // 🔥 切换AI展开状态
    const toggleAiExpand = (ai) => {
      ai.isExpanded = ai.isExpanded !== false ? false : true
    }
    
    // 🔥 获取日志点样式
    const getLogDotClass = (log) => {
      if (log.type === 'error') return 'dot-error'
      if (log.type === 'success') return 'dot-success'
      return ''
    }
    
    const handleDeepSeekLogin = () => {
      loginDialogVisible.value = true
      loginLoading.value = true
      loginStatusText.value = '正在获取登录二维码...'
      
      // 发送登录检查请求
      sendWebSocketMessage('AI_DEEPSEEK_SCAN_LOGIN', {})
    }
    
    const sendPrompt = () => {
      if (!canSend.value) return
      
      isSending.value = true
      taskStarted.value = true
      taskStatus.value = 'running'
      progressLogs.value = []
      screenshots.value = []
      results.value = []
      
      // 🔥 初始化启用的AI列表（支持多AI扩展）
      enabledAIs.value = [{
        name: 'DeepSeek',
        status: 'running',
        isExpanded: true,
        progressLogs: []
      }]
      
      // 🔥 生成sessionId（用于追踪本次请求）
      sessionId = generateUUID()
      
      // 🔥 只在点击"创建新对话"按钮时才生成新的chatId
      if (isNewChat.value) {
        currentChatId.value = generateUUID()
        userInfoReq.value.chatId = currentChatId.value
        console.log('📝 [新建会话] 生成新的chatId:', currentChatId.value)
        isNewChat.value = false  // 生成后立即标记为非新会话
      }
      
      // 🔥 如果没有chatId（首次打开且未加载历史），则生成一个
      if (!currentChatId.value) {
        currentChatId.value = generateUUID()
        userInfoReq.value.chatId = currentChatId.value
        console.log('📝 [首次使用] 生成新的chatId:', currentChatId.value)
      }
      
      // 🔥 发送DeepSeek查询请求（传递AI会话ID支持上下文复用）
      sendWebSocketMessage('AI_DEEPSEEK_QUERY', {
        query: promptInput.value,
        enableDeepThinking: enableDeepThinking.value,
        enableWebSearch: enableWebSearch.value,
        chatId: currentChatId.value,
        // 🔥 传递已有的DeepSeek会话ID（关键：上下文复用）
        deepseekChatId: userInfoReq.value.deepseekChatId,
        sessionId: sessionId,
        aiType: 'deepseek',
        isNewChat: isNewChat.value,
        userPrompt: promptInput.value,
        // 🔥 传递任务流程和进度日志，便于保存到数据库
        enabledAIs: enabledAIs.value,
        progressLogs: progressLogs.value
      })
      
      // 🔥 首次发送后标记为非新会话
      isNewChat.value = false
      userInfoReq.value.isNewChat = false
      
      addProgressLog('已发送请求到DeepSeek')
      console.log('📝 [发送请求] chatId:', currentChatId.value, 'sessionId:', sessionId, 'deepseekChatId:', userInfoReq.value.deepseekChatId)
    }
    
    const sendWebSocketMessage = (type, payload) => {
      if (!websocket || websocket.readyState !== WebSocket.OPEN) {
        connectWebSocket(() => {
          doSendMessage(type, payload)
        })
      } else {
        doSendMessage(type, payload)
      }
    }
    
    const doSendMessage = (type, payload) => {
      // 生成sessionId用于追踪会话（与系统的requestId区分）
      if (!sessionId) {
        sessionId = generateUUID()
      }
      
      const finalChatId = payload.chatId || currentChatId.value
      
      const message = {
        type: type,
        engineId: 'engine-001',
        chatId: finalChatId,  // 🔥 顶层chatId
        payload: {
          ...payload,
          sessionId: sessionId,
          chatId: finalChatId,  // 🔥 payload中也保留chatId作为备用
          aiType: payload.aiType || 'deepseek'
        }
      }
      console.log('🔥 [WebSocket] 发送消息 - chatId:', finalChatId, 'sessionId:', sessionId)
      websocket.send(JSON.stringify(message))
    }
    
    const connectWebSocket = (callback) => {
      const token = getToken()
      // 使用通用WebSocket工具类构建连接URL
      const wsUrl = buildWebSocketUrl({
        path: '/ws/client',
        token: token,
        clientType: 'web'
      })
      
      console.log('连接WebSocket:', wsUrl)
      websocket = new WebSocket(wsUrl)
      
      websocket.onopen = () => {
        console.log('WebSocket已连接')
        if (callback) callback()
        // 测试阶段：跳过登录检查，默认已登录
        // checkDeepSeekLoginStatus()
        ElMessage.success('WebSocket连接成功')
      }
      
      websocket.onmessage = (event) => {
        handleWebSocketMessage(event.data)
      }
      
      websocket.onerror = (error) => {
        console.error('WebSocket错误:', error)
        ElMessage.error('WebSocket连接失败')
      }
      
      websocket.onclose = () => {
        console.log('WebSocket已断开')
      }
    }
    
    const checkDeepSeekLoginStatus = () => {
      sessionId = generateUUID()
      sendWebSocketMessage('AI_DEEPSEEK_CHECK_LOGIN', { sessionId: sessionId, aiType: 'deepseek' })
    }
    
    const handleWebSocketMessage = (data) => {
      try {
        const message = JSON.parse(data)
        console.log('收到WebSocket消息:', message)
        
        // Engine返回的消息格式：messageType/type 表示消息类型
        const messageType = message.messageType || message.type
        const payload = message.payload || {}
        
        // 处理TASK_LOG - 日志消息
        if (messageType === 'TASK_LOG') {
          const logMessage = payload.message
          const aiType = payload.aiType || 'deepseek'
          if (logMessage) {
            addProgressLog(logMessage, aiType)
          }
        }
        
        // 处理TASK_SCREENSHOT - 截图消息
        if (messageType === 'TASK_SCREENSHOT') {
          const screenshotUrl = payload.screenshotUrl
          if (screenshotUrl) {
            screenshots.value.push(screenshotUrl)
            console.log('📸 [截图] 收到新截图:', screenshotUrl)
          }
        }
        
        // 处理TASK_PROGRESS - 进度消息
        if (messageType === 'TASK_PROGRESS') {
          const progressMessage = payload.message
          const aiType = payload.aiType || 'deepseek'
          if (progressMessage) {
            addProgressLog(progressMessage, aiType)
          }
        }
        
        // 处理TASK_RESULT - 最终结果
        if (messageType === 'TASK_RESULT') {
          const resultData = payload.data || payload
          const success = payload.success
          
          if (success) {
            // 检查是否是登录检查结果
            if (resultData.isLoggedIn !== undefined) {
              deepseekLoggedIn.value = resultData.isLoggedIn === true
              if (deepseekLoggedIn.value) {
                ElMessage.success('DeepSeek已登录: ' + (resultData.userName || ''))
              }
              return
            }
            
            // 检查是否是扫码登录结果
            if (resultData.loginTime !== undefined) {
              deepseekLoggedIn.value = resultData.success === true
              loginDialogVisible.value = false
              if (resultData.success) {
                ElMessage.success('DeepSeek登录成功: ' + (resultData.userName || ''))
              }
              return
            }
            
            // 处理AI咨询结果
            if (resultData.answer) {
              taskStatus.value = 'completed'
              isSending.value = false
              
              // 🔥 更新启用AI的状态
              const aiType = payload.aiType || 'deepseek'
              const targetAi = enabledAIs.value.find(ai => ai.name.toLowerCase().includes(aiType.toLowerCase()))
              if (targetAi) {
                targetAi.status = 'completed'
              }
              
              const resultItem = {
                aiName: 'DeepSeek',
                content: resultData.answer,
                shareUrl: resultData.shareUrl,
                chatId: resultData.chatId,
                query: resultData.query,
                mode: resultData.mode
              }
              results.value.push(resultItem)
              
              // 🔥 保存返回的AI会话ID（仅用于上下文复用，不覆盖前端chatId）
              // currentChatId 是前端生成的会话分组ID，用于数据库关联多轮对话
              // deepseekChatId 是DeepSeek返回的AI内部会话ID，用于AI上下文复用
              if (resultData.chatId) {
                userInfoReq.value.deepseekChatId = resultData.chatId
                console.log('📝 [保存AI会话ID] deepseekChatId:', resultData.chatId, '(前端chatId保持不变:', currentChatId.value, ')')
              }
              
              // 添加对话截图
              if (resultData.conversationScreenshot) {
                screenshots.value.push(resultData.conversationScreenshot)
              }
              
              addProgressLog('DeepSeek回复完成，耗时' + resultData.elapsedTime + '秒')
              ElMessage.success(payload.message || 'DeepSeek回复完成')
              
              // 🔥 后端Admin已自动存储，前端无需再调用数据库
              // 数据已在Admin收到Engine消息时实时保存
            }
          } else {
            // 处理错误
            taskStatus.value = 'failed'
            isSending.value = false
            const errorMsg = payload.errorMessage || payload.message || '请求失败'
            addProgressLog('错误: ' + errorMsg)
            ElMessage.error(errorMsg)
          }
        }
        
        // 处理CONNECTED消息
        if (messageType === 'CONNECTED') {
          console.log('WebSocket连接确认:', message)
        }
      } catch (error) {
        console.error('处理WebSocket消息失败:', error)
      }
    }
    
    const addProgressLog = (content, aiType = 'deepseek') => {
      const logEntry = {
        content: content,
        timestamp: new Date(),
        aiType: aiType
      }
      
      // 添加到全局日志
      progressLogs.value.push(logEntry)
      
      // 🔥 同时添加到对应AI的日志列表（支持按AI区分显示）
      const targetAi = enabledAIs.value.find(ai => 
        ai.name.toLowerCase().includes(aiType.toLowerCase())
      )
      if (targetAi) {
        if (!targetAi.progressLogs) {
          targetAi.progressLogs = []
        }
        targetAi.progressLogs.push(logEntry)
      }
      
      console.log('📋 [进度日志]', aiType, ':', content)
    }
    
    // 🔥 数据存储已完全由后端Admin自动处理
    // Admin在以下时机自动存储到数据库：
    // 1. 收到前端请求时 - 存储初始请求信息
    // 2. Engine返回进度/截图时 - 实时更新数据
    // 3. Engine返回最终结果时 - 存储完整结果
    // 前端无需手动调用数据库API
    
    const formatTime = (timestamp) => {
      if (!timestamp) return ''
      const date = new Date(timestamp)
      return date.toLocaleTimeString()
    }
    
    const getStatusText = (status) => {
      const statusMap = {
        pending: '等待中',
        running: '执行中',
        completed: '已完成',
        failed: '失败'
      }
      return statusMap[status] || status
    }
    
    const renderMarkdown = (content) => {
      if (!content) return ''
      return marked(content)
    }
    
    const showLargeImage = (url) => {
      currentLargeImage.value = url
      showImageDialog.value = true
    }
    
    const openShareUrl = (url) => {
      window.open(url, '_blank')
    }
    
    const saveToDraft = async () => {
      if (results.value.length === 0) {
        ElMessage.warning('没有可保存的内容')
        return
      }
      
      try {
        const result = results.value[0]
        await addDraft({
          aiName: result.aiName,
          content: result.content,
          shareUrl: result.shareUrl
        })
        ElMessage.success('已保存到草稿库')
      } catch (error) {
        console.error('保存草稿失败:', error)
        ElMessage.error('保存草稿失败')
      }
    }
    
    const generateUUID = () => {
      return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function(c) {
        const r = Math.random() * 16 | 0
        const v = c === 'x' ? r : (r & 0x3 | 0x8)
        return v.toString(16)
      })
    }
    
    // 🔥 自动加载最后一次会话历史
    const loadLastChat = async () => {
      try {
        historyLoading.value = true
        const res = await getChatHistory({ isAll: 0 })  // isAll=0 只获取最新一条
        
        console.log('📝 [自动加载] API响应:', res)
        
        // 🔥 处理分页结构：res.rows 或 res.data
        const dataList = res.rows || res.data || []
        
        if (res.code === 200 && dataList.length > 0) {
          const lastChat = dataList[0]
          console.log('📝 [自动加载] 最后一次会话:', lastChat)
          
          // 加载最后一次会话
          await loadHistoryItem(lastChat)
          console.log('✅ [自动加载] 已恢复最后一次会话')
        } else {
          // 没有历史记录，初始化为空状态
          console.log('📝 [自动加载] 无历史记录，等待用户创建新对话')
          currentChatId.value = null
          isNewChat.value = true
        }
      } catch (error) {
        console.error('自动加载最后一次会话失败:', error)
        // 失败时初始化为空状态
        currentChatId.value = null
        isNewChat.value = true
      } finally {
        historyLoading.value = false
      }
    }
    
    // 生命周期
    onMounted(() => {
      connectWebSocket(() => {
        // WebSocket连接成功后，加载最后一次会话
        loadLastChat()
      })
    })
    
    onUnmounted(() => {
      if (websocket) {
        websocket.close()
      }
    })
    
    return {
      // 数据
      activeCollapses,
      historyDrawerVisible,
      historyLoading,
      chatHistory,
      deepseekEnabled,
      deepseekLoggedIn,
      enableDeepThinking,
      enableWebSearch,
      promptInput,
      isSending,
      taskStarted,
      taskStatus,
      progressLogs,
      screenshots,
      results,
      showImageDialog,
      currentLargeImage,
      loginDialogVisible,
      loginLoading,
      loginStatusText,
      qrCodeUrl,
      canSend,
      // 🔥 新增：上下文复用相关
      enabledAIs,
      groupedHistory,
      allTasksCompleted,
      hasRunningTasks,
      // 方法
      createNewChat,
      showHistoryDrawer,
      loadHistoryItem,
      handleDeepSeekLogin,
      sendPrompt,
      formatTime,
      getStatusText,
      renderMarkdown,
      showLargeImage,
      openShareUrl,
      saveToDraft,
      // 🔥 新增：历史记录和AI管理方法
      toggleHistoryExpansion,
      formatHistoryTime,
      toggleAiExpand,
      getLogDotClass
    }
  }
}
</script>

<style lang="scss" scoped>
.ai-management-platform {
  padding: 20px;
  background-color: #f5f7fa;
  min-height: calc(100vh - 84px);
}

.top-nav {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 15px 20px;
  background: #fff;
  border-radius: 8px;
  margin-bottom: 20px;
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.1);

  .logo-area {
    display: flex;
    align-items: center;
    gap: 12px;

    .logo-icon {
      color: #409eff;
    }

    .platform-title {
      font-size: 20px;
      font-weight: 600;
      color: #303133;
      margin: 0;
    }
  }

  .nav-buttons {
    display: flex;
    align-items: center;
    gap: 15px;
  }
}

.main-content {
  .el-collapse {
    border: none;
    background: transparent;
  }

  .el-collapse-item {
    margin-bottom: 15px;
    background: #fff;
    border-radius: 8px;
    overflow: hidden;
    box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.1);
  }
}

.ai-config-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
  padding-right: 20px;
}

.ai-selection-section {
  padding: 15px;
}

.ai-cards {
  display: flex;
  flex-wrap: wrap;
  gap: 20px;
}

.ai-card {
  width: 280px;
  position: relative;
  border-radius: 12px;
  transition: all 0.3s;

  &:hover {
    transform: translateY(-5px);
  }

  &.ai-card-not-logged {
    opacity: 0.7;
  }

  .card-login-overlay {
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    background: rgba(255, 255, 255, 0.9);
    display: flex;
    align-items: center;
    justify-content: center;
    z-index: 10;
    border-radius: 12px;

    .card-login-message {
      text-align: center;
      
      .el-icon {
        font-size: 32px;
        color: #e6a23c;
        margin-bottom: 10px;
      }
      
      span {
        display: block;
        color: #909399;
        margin-bottom: 10px;
      }
    }
  }

  .ai-card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 15px;

    .ai-left {
      display: flex;
      align-items: center;
      gap: 10px;

      .ai-avatar {
        width: 40px;
        height: 40px;
        border-radius: 50%;
        background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
        display: flex;
        align-items: center;
        justify-content: center;
        color: #fff;
      }

      .ai-name {
        font-weight: 600;
        font-size: 16px;
      }
    }
  }

  .ai-options {
    .button-options-group {
      .ai-capabilities {
        display: flex;
        gap: 10px;
        flex-wrap: wrap;
      }
    }
  }
}

.prompt-input-section {
  padding: 15px;

  .prompt-input {
    margin-bottom: 15px;
  }

  .prompt-footer {
    display: flex;
    justify-content: space-between;
    align-items: center;

    .word-count {
      color: #909399;
      font-size: 14px;
    }
  }
}

.execution-status-section {
  margin-top: 20px;

  .task-flow-card,
  .screenshots-card {
    height: 800px;
  }

  .card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }

  .task-flow {
    .task-item {
      background: #fff;
      border: 1px solid #e4e7ed;
      border-radius: 8px;
      padding: 12px;
      margin-bottom: 15px;
      box-shadow: 0 2px 8px rgba(0,0,0,0.06);
      transition: box-shadow 0.3s;
      
      &:hover {
        box-shadow: 0 4px 12px rgba(0,0,0,0.1);
      }
      
      .task-header {
        display: flex;
        justify-content: space-between;
        align-items: center;
        padding: 8px 10px;
        background: #f5f7fa;
        border-radius: 6px;
        cursor: pointer;
        transition: background 0.2s;
        
        &:hover {
          background: #ebeef5;
        }

        .header-left {
          display: flex;
          align-items: center;
          gap: 10px;
          
          .expand-icon {
            transition: transform 0.3s;
            color: #909399;
            
            &.is-expanded {
              transform: rotate(90deg);
            }
          }

          .ai-name {
            font-weight: 500;
            font-size: 15px;
            color: #303133;
          }
        }

        .header-right {
          display: flex;
          align-items: center;
          gap: 8px;

          .status-text {
            font-size: 14px;
            color: #606266;
          }
        }
      }

      .progress-timeline {
        margin-top: 15px;
        padding: 10px 15px;
        background: #fafafa;
        border-radius: 6px;
        max-height: 350px;
        overflow-y: auto;

        .progress-item {
          display: flex;
          gap: 15px;
          margin-bottom: 15px;
          position: relative;

          .progress-dot {
            width: 10px;
            height: 10px;
            border-radius: 50%;
            background: #409eff;
            flex-shrink: 0;
            margin-top: 5px;
          }

          .progress-content {
            .progress-time {
              font-size: 12px;
              color: #909399;
            }

            .progress-text {
              font-size: 14px;
              color: #303133;
            }
          }
        }
      }
    }
  }

  .screenshots {
    height: 720px;
    display: flex;
    align-items: center;
    justify-content: center;
    background: #f5f7fa;
    border-radius: 8px;
    padding: 10px;

    :deep(.el-carousel) {
      width: 100%;
      height: 100%;
    }
    
    :deep(.el-carousel__container) {
      height: 680px !important;
    }
    
    :deep(.el-carousel__item) {
      display: flex;
      align-items: center;
      justify-content: center;
      background: #fff;
      border-radius: 8px;
    }

    .screenshot-image {
      max-width: 100%;
      max-height: 660px;
      width: auto;
      height: auto;
      object-fit: contain;
      cursor: pointer;
      transition: transform 0.3s;
      border-radius: 4px;
      box-shadow: 0 2px 12px rgba(0,0,0,0.1);
    }
    
    .screenshot-image:hover {
      transform: scale(1.02);
      box-shadow: 0 4px 20px rgba(0,0,0,0.15);
    }

    .no-screenshots {
      text-align: center;
      color: #909399;

      .el-icon {
        margin-bottom: 10px;
      }
    }
  }
}

.results-section {
  margin-top: 20px;

  .section-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 15px;

    .section-title {
      font-size: 18px;
      font-weight: 600;
      margin: 0;
    }
  }

  .result-content {
    .result-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 15px;
      padding-bottom: 15px;
      border-bottom: 1px solid #ebeef5;

      .result-title {
        font-weight: 600;
        font-size: 16px;
      }
    }

    .markdown-content {
      line-height: 1.8;
      max-height: 600px;
      overflow-y: auto;
      padding-right: 10px;
      
      /* 自定义滚动条样式 */
      &::-webkit-scrollbar {
        width: 8px;
      }
      
      &::-webkit-scrollbar-track {
        background: #f1f1f1;
        border-radius: 4px;
      }
      
      &::-webkit-scrollbar-thumb {
        background: #c1c1c1;
        border-radius: 4px;
        
        &:hover {
          background: #a8a8a8;
        }
      }
      
      :deep(h1), :deep(h2), :deep(h3) {
        margin-top: 20px;
        margin-bottom: 10px;
      }

      :deep(p) {
        margin-bottom: 10px;
      }

      :deep(code) {
        background: #f5f7fa;
        padding: 2px 6px;
        border-radius: 4px;
      }

      :deep(pre) {
        background: #f5f7fa;
        padding: 15px;
        border-radius: 8px;
        overflow-x: auto;
      }
    }
  }
}

.history-content {
  padding: 15px;

  .history-loading {
    text-align: center;
    padding: 40px;
    color: #909399;
  }

  // 🔥 历史记录分组样式（参考旧项目cube-ui）
  .history-group {
    margin-bottom: 20px;
    
    .history-date {
      font-size: 14px;
      font-weight: 600;
      color: #606266;
      padding: 10px 0;
      border-bottom: 1px solid #ebeef5;
      margin-bottom: 10px;
    }
    
    .history-list {
      .history-item {
        margin-bottom: 8px;
        
        .history-parent {
          padding: 12px;
          border-radius: 8px;
          cursor: pointer;
          transition: background 0.3s;
          
          &:hover {
            background: #f5f7fa;
          }
          
          .history-header {
            display: flex;
            align-items: flex-start;
            gap: 10px;
            
            .expand-arrow {
              flex-shrink: 0;
              color: #909399;
              transition: transform 0.3s;
              margin-top: 3px;
              
              &.is-expanded {
                transform: rotate(90deg);
              }
            }
            
            .chat-icon {
              flex-shrink: 0;
              color: #909399;
              margin-top: 3px;
            }
            
            .history-content-wrapper {
              flex: 1;
              min-width: 0;
              
              .history-prompt {
                font-size: 14px;
                color: #303133;
                margin-bottom: 6px;
                overflow: hidden;
                text-overflow: ellipsis;
                white-space: nowrap;
              }
              
              .history-meta {
                font-size: 12px;
                color: #909399;
                display: flex;
                align-items: center;
                gap: 4px;
                flex-wrap: wrap;
                
                .history-separator {
                  color: #c0c4cc;
                }
                
                .history-chatid {
                  color: #409eff;
                }
                
                .children-count {
                  color: #67c23a;
                }
              }
            }
          }
        }
        
        // 🔥 子记录（各轮对话）
        .history-children {
          margin-left: 30px;
          border-left: 2px solid #ebeef5;
          padding-left: 15px;
          margin-top: 8px;
          
          .history-child-item {
            padding: 10px;
            border-radius: 6px;
            cursor: pointer;
            transition: background 0.3s;
            margin-bottom: 6px;
            
            &:hover {
              background: #f5f7fa;
            }
            
            .history-child-content {
              .child-index {
                display: inline-block;
                font-size: 12px;
                color: #409eff;
                background: #ecf5ff;
                padding: 2px 8px;
                border-radius: 4px;
                margin-bottom: 6px;
              }
              
              .history-prompt {
                font-size: 13px;
                color: #606266;
                margin-bottom: 4px;
                overflow: hidden;
                text-overflow: ellipsis;
                white-space: nowrap;
              }
              
              .history-meta {
                font-size: 12px;
                color: #909399;
                
                .ai-count {
                  color: #67c23a;
                }
              }
            }
          }
        }
      }
    }
  }

  .history-empty {
    text-align: center;
    padding: 40px;
    color: #909399;

    .el-icon {
      font-size: 48px;
      margin-bottom: 10px;
    }
  }
}

.image-dialog {
  .large-image {
    max-width: 100%;
    max-height: 80vh;
    object-fit: contain;
  }
}

.login-dialog-content {
  text-align: center;
  padding: 20px;

  .login-loading {
    margin-bottom: 20px;
  }

  .qrcode-container {
    .qrcode-image {
      max-width: 300px;
      border: 1px solid #ebeef5;
      border-radius: 8px;
    }

    p {
      margin-top: 15px;
      color: #606266;
    }
  }
}
</style>
