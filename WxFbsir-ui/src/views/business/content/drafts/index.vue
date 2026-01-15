<template>
  <div class="drafts-container" v-loading="loading">
    <!-- 搜索栏 -->
    <el-row style="margin-bottom: 16px;">
      <el-col>
        <el-form :model="queryParams" ref="queryForm" size="small" :inline="true">
          <el-form-item prop="keyWord">
            <el-input v-model="queryParams.keyWord" placeholder="请输入问题关键字" clearable style="width: 280px;"
              @keyup.enter="handleQuery" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :icon="Search" @click="handleQuery">搜索</el-button>
            <el-button :icon="Refresh" @click="resetQuery">重置</el-button>
          </el-form-item>
        </el-form>
      </el-col>
    </el-row>

    <!-- 卡片式对话列表 -->
    <div class="dialog-list" ref="scrollContainer" @scroll="handleScroll">
      <div v-for="(item, index) in dialogList" :key="index" class="dialog-card">
        <!-- 用户提问区域 -->
        <div class="user-question">
          <div class="user-avatar">
            <el-avatar :size="40" :src="item.userAvatar || defaultAvatar">
              <el-icon><User /></el-icon>
            </el-avatar>
          </div>
          <div class="user-info">
            <div class="user-name">{{ item.userName || '用户' }}</div>
            <div class="question-text">{{ item.question || '未知问题' }}</div>
            <div class="question-time">{{ item.questionTime }}</div>
          </div>
        </div>

        <!-- AI回答卡片网格 -->
        <div class="response-grid" v-if="item.aiResponses && item.aiResponses.length > 0">
          <div v-for="(model, mIndex) in item.aiResponses" :key="mIndex" class="response-card"
            @click="showModelResponse(model)">
            <!-- 模型标题 -->
            <div class="model-title">
              <el-icon class="model-icon"><ChatDotRound /></el-icon>
              <span class="model-name">{{ model.name }}</span>
            </div>
            <!-- 预览内容：优先显示截图，无截图时显示文本 -->
            <div class="preview-content">
              <template v-if="model.shareImgUrl">
                <img :src="model.shareImgUrl" class="ai-image" alt="AI响应截图" />
              </template>
              <template v-else>
                <div class="text-preview" v-html="renderMarkdown(model.content)"></div>
              </template>
            </div>
            <!-- 时间和分享链接 -->
            <div class="response-footer">
              <span class="response-time">{{ model.responseTime }}</span>
              <el-button v-if="model.shareUrl" link type="primary" size="small" @click.stop="openUrl(model.shareUrl)">
                分享链接
              </el-button>
            </div>
          </div>
        </div>
        <div v-else class="no-response">
          <el-empty description="暂无AI响应" :image-size="60" />
        </div>
      </div>

      <!-- 无数据提示 -->
      <el-empty v-if="dialogList.length === 0 && !loading" description="暂无草稿记录" />
    </div>

    <!-- 详情模态框 -->
    <el-dialog v-model="showModal" :title="selectedModel?.name || 'AI响应'" width="70%" destroy-on-close>
      <div class="modal-content" v-if="selectedModel">
        <!-- 🔥 查看模式切换（仅在同时有截图和文本时显示） -->
        <div v-if="selectedModel.shareImgUrl && selectedModel.content && selectedModel.content.trim()" class="view-mode-switch" style="margin-bottom: 16px;">
          <el-radio-group v-model="viewMode" size="small">
            <el-radio-button label="screenshot">截图查看</el-radio-button>
            <el-radio-button label="text">文本查看</el-radio-button>
          </el-radio-group>
        </div>
        
        <!-- 🔥 优先显示截图（如果有shareImgUrl） -->
        <template v-if="viewMode === 'screenshot' && selectedModel.shareImgUrl">
          <div class="screenshot-view">
            <img :src="selectedModel.shareImgUrl" style="max-width: 100%; height: auto; border-radius: 8px;" alt="对话截图" />
            <p style="text-align: center; color: #999; margin-top: 8px; font-size: 12px;">对话完整截图</p>
          </div>
        </template>
        
        <!-- 🔥 文本查看模式：直接显示draft_content字段（Markdown渲染） -->
        <template v-else>
          <div class="prose markdown-body" v-html="renderMarkdown(selectedModel.content)"></div>
        </template>
      </div>
      <template #footer>
        <div class="modal-footer">
          <span class="response-time-footer">{{ selectedModel?.responseTime }}</span>
          <div>
            <el-button v-if="selectedModel?.shareUrl" type="primary" @click="openUrl(selectedModel.shareUrl)">
              打开分享链接
            </el-button>
            <el-button @click="copyContent(selectedModel?.content)">复制内容</el-button>
            <el-button @click="showModal = false">关闭</el-button>
          </div>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, Refresh, User, ChatDotRound } from '@element-plus/icons-vue'
import { marked } from 'marked'
import { getPlayWrighDrafts, getDraftContent } from "@/api/aigc/drafts"

// 配置 marked
marked.setOptions({
  breaks: true,
  gfm: true,
  headerIds: false,
  mangle: false
})

// 数据定义
const loading = ref(true)
const total = ref(0)
const dialogList = ref([])
const showModal = ref(false)
const selectedModel = ref(null)
const scrollContainer = ref(null)
const defaultAvatar = 'https://cube.elemecdn.com/3/7c/3ea6beec64369c2642b92c6726f1epng.png'
const viewMode = ref('screenshot') // 🔥 查看模式：screenshot=截图查看，text=文本查看

const queryParams = reactive({
  pageNum: 1,
  pageSize: 10,
  keyWord: ''
})

// 方法定义
const getList = async () => {
  loading.value = true
  try {
    const response = await getPlayWrighDrafts(queryParams)
    total.value = response.total || 0
    if (queryParams.pageNum === 1) {
      dialogList.value = response.rows || []
    } else {
      // 滚动加载时追加数据
      const newData = response.rows || []
      newData.forEach(item => {
        dialogList.value.push(item)
      })
    }
  } catch (error) {
    console.error('获取草稿列表失败:', error)
    dialogList.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

const handleQuery = () => {
  queryParams.pageNum = 1
  getList()
}

const resetQuery = () => {
  queryParams.keyWord = ''
  queryParams.pageNum = 1
  getList()
}

// 滚动加载更多
const handleScroll = (event) => {
  const container = event.target
  if (container.scrollHeight - container.scrollTop <= container.clientHeight + 50) {
    if (!loading.value && dialogList.value.length < total.value) {
      queryParams.pageNum += 1
      getList()
    }
  }
}

// 显示AI响应详情
const showModelResponse = (model) => {
  // 🔥 数据库字段说明：
  // - content (即draft_content): 文本内容（Markdown格式）
  // - shareImgUrl: 对话截图URL
  // - shareUrl: 分享链接
  
  selectedModel.value = model
  
  // 🔥 默认查看模式：有截图则默认显示截图，否则显示文本
  viewMode.value = model.shareImgUrl ? 'screenshot' : 'text'
  showModal.value = true
}

// 🔥 获取当前对话的taskId（从dialogList中查找）
const getCurrentTaskId = () => {
  // selectedModel中没有taskId，需要从dialogList中找到对应的taskId
  for (const dialog of dialogList.value) {
    if (dialog.aiResponses) {
      const found = dialog.aiResponses.find(ai => ai === selectedModel.value)
      if (found) {
        return dialog.taskId
      }
    }
  }
  return null
}

// 判断是否为图片URL
const isImageUrl = (content) => {
  if (!content) return false
  const trimmed = content.trim()
  const imageRegex = /\.(jpg|jpeg|png|gif|bmp|webp)(\?.*)?$/i
  const urlImageRegex = /^https?:\/\/.*\.(jpg|jpeg|png|gif|bmp|webp)(\?.*)?$/i
  return imageRegex.test(trimmed) || urlImageRegex.test(trimmed)
}

// 渲染Markdown
const renderMarkdown = (content) => {
  if (!content) return ''
  // 如果是图片链接，直接返回
  if (isImageUrl(content)) return content
  return marked(content)
}

// 🔥 复制内容（从数据库获取，不使用DOM）
const copyContent = async () => {
  if (!selectedModel.value) {
    ElMessage.warning('没有选中的AI响应')
    return
  }
  
  try {
    const taskId = getCurrentTaskId()
    if (!taskId) {
      ElMessage.error('无法获取任务ID')
      return
    }
    
    // 🔥 从数据库获取真实的draft_content文本
    const aiName = selectedModel.value.name || 'deepseek'
    const response = await getDraftContent(taskId, aiName)
    
    if (response && response.data && response.data.content) {
      await navigator.clipboard.writeText(response.data.content)
      ElMessage.success('已复制到剪贴板')
    } else {
      ElMessage.warning('没有可复制的文本内容')
    }
  } catch (error) {
    console.error('复制失败:', error)
    ElMessage.error('复制失败，请重试')
  }
}

// 打开URL
const openUrl = (url) => {
  if (url) {
    window.open(url, '_blank')
  }
}

// 生命周期
onMounted(() => {
  getList()
})
</script>

<style lang="scss" scoped>
.drafts-container {
  padding: 20px;
}

.dialog-list {
  max-height: calc(100vh - 180px);
  overflow-y: auto;
  padding-right: 10px;
}

/* 对话卡片 */
.dialog-card {
  background-color: white;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  padding: 20px;
  margin-bottom: 20px;
  transition: all 0.3s ease;

  &:hover {
    box-shadow: 0 4px 16px rgba(0, 0, 0, 0.1);
  }
}

/* 用户提问区域 */
.user-question {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 16px;
  padding-bottom: 16px;
  border-bottom: 1px solid #f0f0f0;
}

.user-info {
  flex: 1;
}

.user-name {
  font-size: 15px;
  font-weight: 500;
  color: #303133;
  margin-bottom: 6px;
}

.question-text {
  font-size: 16px;
  font-weight: 600;
  color: #1f2937;
  line-height: 1.6;
  margin-bottom: 6px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.question-time {
  font-size: 13px;
  color: #909399;
}

/* AI回答卡片网格 */
.response-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 16px;
}

/* AI回答卡片 */
.response-card {
  background-color: #fafafa;
  border: 1px solid #ebeef5;
  border-radius: 10px;
  padding: 14px;
  cursor: pointer;
  transition: all 0.2s ease;
  min-height: 180px;
  display: flex;
  flex-direction: column;

  &:hover {
    background-color: #f0f7ff;
    border-color: #409eff;
    transform: translateY(-2px);
    box-shadow: 0 4px 12px rgba(64, 158, 255, 0.15);
  }
}

.model-title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}

.model-icon {
  font-size: 18px;
  color: #409eff;
}

.model-name {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

.preview-content {
  flex: 1;
  overflow: hidden;
  margin-bottom: 10px;
}

.text-preview {
  font-size: 13px;
  color: #606266;
  line-height: 1.6;
  display: -webkit-box;
  -webkit-line-clamp: 4;
  -webkit-box-orient: vertical;
  overflow: hidden;
  
  :deep(h1), :deep(h2), :deep(h3), :deep(h4) {
    font-size: 14px;
    margin: 4px 0;
  }
  
  :deep(p) {
    margin: 4px 0;
  }
  
  :deep(ul), :deep(ol) {
    padding-left: 16px;
    margin: 4px 0;
  }
}

.ai-image {
  max-width: 100%;
  max-height: 120px;
  object-fit: cover;
  border-radius: 6px;
}

.response-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: auto;
  padding-top: 8px;
  border-top: 1px solid #f0f0f0;
}

.response-time {
  font-size: 12px;
  color: #909399;
}

.no-response {
  padding: 20px;
  text-align: center;
}

/* 模态框内容 */
.modal-content {
  max-height: 60vh;
  overflow-y: auto;
  padding: 10px;
}

/* 🔥 查看模式切换 */
.view-mode-switch {
  margin-bottom: 16px;
  text-align: center;
  padding-bottom: 12px;
  border-bottom: 1px solid #ebeef5;
}

/* 🔥 截图查看容器 */
.screenshot-view {
  display: flex;
  flex-direction: column;
  align-items: center;
  
  img {
    max-width: 100%;
    height: auto;
    box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.1);
  }
}

.markdown-body {
  line-height: 1.8;
  
  :deep(h1), :deep(h2), :deep(h3) {
    margin-top: 20px;
    margin-bottom: 10px;
    border-bottom: 1px solid #ebeef5;
    padding-bottom: 8px;
  }
  
  :deep(p) {
    margin-bottom: 12px;
  }
  
  :deep(code) {
    background: #f5f7fa;
    padding: 2px 6px;
    border-radius: 4px;
    font-family: 'Monaco', 'Menlo', monospace;
  }
  
  :deep(pre) {
    background: #f5f7fa;
    padding: 16px;
    border-radius: 8px;
    overflow-x: auto;
  }
  
  :deep(ul), :deep(ol) {
    padding-left: 24px;
  }
  
  :deep(blockquote) {
    border-left: 4px solid #409eff;
    padding-left: 16px;
    color: #606266;
    margin: 12px 0;
  }
}

.modal-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.response-time-footer {
  font-size: 13px;
  color: #909399;
}
</style>
