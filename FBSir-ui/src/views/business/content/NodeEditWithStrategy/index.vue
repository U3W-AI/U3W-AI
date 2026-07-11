<template>
  <div class="workflow-node-edit-container">
    <!-- 登录状态提示 -->
    <el-alert
      v-if="!isLoggedIn"
      :title="loginAlertTitle"
      type="warning"
      :closable="false"
      show-icon
      class="login-alert"
    >
      <template #default>
        <span>{{ loginAlertMessage }}</span>
        <el-button type="primary" link @click="goToLoginManager" class="login-button">
          前往登录管理器
        </el-button>
      </template>
    </el-alert>

    <el-alert
      v-else
      title="已登录，可以正常使用工作流节点编辑功能"
      type="success"
      :closable="false"
      show-icon
      class="login-alert success-alert"
    />

    <!-- 节点编辑工作台 -->
    <el-card class="edit-card" v-if="isLoggedIn" shadow="hover">
      <template #header>
        <div class="card-header">
          <div class="header-left">
            <div class="icon-wrapper">
              <el-icon :size="28" color="#ffffff"><Edit /></el-icon>
            </div>
            <div class="title-section">
              <h3 class="card-title">工作流节点编辑</h3>
              <p class="card-subtitle">配置您的AI工作流节点参数</p>
            </div>
          </div>
          <el-tag 
            :type="connectionStatus.type" 
            size="small"
            class="status-tag"
            effect="dark"
          >
            {{ connectionStatus.text }}
          </el-tag>
        </div>
      </template>

      <el-form :model="editForm" label-width="140px" size="default" class="edit-form">
        <el-row :gutter="32">
          <el-col :span="12">
            <el-form-item label="空间名称" required class="form-item-enhanced">
              <el-input 
                v-model="editForm.spaceName" 
                placeholder="例如：福帮手开源"
                clearable
                class="enhanced-input"
              />
              <div class="form-item-hint">选择或输入工作流所在空间</div>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="智能体名称" required class="form-item-enhanced">
              <el-input 
                v-model="editForm.agentName" 
                placeholder="例如：日更助手1"
                clearable
                class="enhanced-input"
              />
              <div class="form-item-hint">输入智能体名称</div>
            </el-form-item>
          </el-col>
        </el-row>
        
        <el-row :gutter="32">
          <el-col :span="12">
            <el-form-item label="工作流名称" required class="form-item-enhanced">
              <el-input 
                v-model="editForm.workflowName" 
                placeholder="例如：日更助手-高优先级"
                clearable
                class="enhanced-input"
              />
              <div class="form-item-hint">输入具体工作流名称</div>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="节点名称" required class="form-item-enhanced">
              <el-input 
                v-model="editForm.nodeName" 
                placeholder="例如：混元大模型"
                clearable
                class="enhanced-input"
              />
              <div class="form-item-hint">输入要编辑的节点名称</div>
            </el-form-item>
          </el-col>
        </el-row>
        
        <el-form-item label="策略（可选）" class="form-item-enhanced">
          <el-input 
            v-model="editForm.strategy" 
            placeholder="留空则不使用策略，例如：成本优先、质量优先"
            clearable
            class="enhanced-input"
          />
          <div class="form-item-hint">可选策略配置，留空则不启用</div>
        </el-form-item>
        
        <el-divider content-position="left" class="section-divider">
          <div class="divider-content">
            <el-icon><Setting /></el-icon>
            <span>模型配置</span>
          </div>
        </el-divider>
        
        <el-row :gutter="32">
          <el-col :span="8">
            <el-form-item label="模型名称" class="form-item-enhanced">
              <el-input 
                v-model="editForm.config.modelName" 
                placeholder="例如：混元大模型长文本版"
                clearable
                class="enhanced-input"
              />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="Temperature" class="form-item-enhanced">
              <el-input-number 
                v-model="editForm.config.temperature" 
                :min="0" 
                :max="1" 
                :step="0.1" 
                class="enhanced-number-input"
                controls-position="right"
              />
              <div class="form-item-hint">值越高，输出越随机</div>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="TopP" class="form-item-enhanced">
              <el-input-number 
                v-model="editForm.config.topP" 
                :min="0" 
                :max="1" 
                :step="0.1" 
                class="enhanced-number-input"
                controls-position="right"
              />
              <div class="form-item-hint">核采样参数</div>
            </el-form-item>
          </el-col>
        </el-row>
        
        <el-row :gutter="32">
          <el-col :span="12">
            <el-form-item label="MaxTokens" class="form-item-enhanced">
              <el-input-number 
                v-model="editForm.config.maxTokens" 
                :min="1" 
                :max="10000" 
                class="enhanced-number-input"
                controls-position="right"
              />
              <div class="form-item-hint">最大输出长度</div>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="超时时间(秒)" class="form-item-enhanced">
              <el-input-number 
                v-model="editForm.timeout" 
                :min="30" 
                :max="300"
                class="enhanced-number-input"
                controls-position="right"
              />
              <div class="form-item-hint">操作超时时间（最大300秒，即5分钟）</div>
            </el-form-item>
          </el-col>
        </el-row>
        
        <el-form-item label="提示词模板" required class="form-item-enhanced">
          <el-input
            v-model="editForm.config.prompt"
            type="textarea"
            :rows="8"
            placeholder="输入提示词，支持变量占位符，如：{{名称2}}"
            show-word-limit
            maxlength="5000"
            class="enhanced-textarea"
          />
          <div class="form-hint">
            <el-icon><InfoFilled /></el-icon>
            <span>提示：使用 {{ '{' }}{{ '{' }}名称N{{ '}' }}{{ '}' }} 格式插入变量，N 为变量编号（从1开始）</span>
          </div>
        </el-form-item>
        
        <el-form-item class="action-buttons">
          <el-button 
            type="success" 
            size="large"
            @click="handleEditAndPublish" 
            :loading="editAndPublishLoading"
            :icon="Promotion"
            class="action-button"
          >
            <template v-if="editAndPublishLoading">
              <span class="button-loading-text">处理中...</span>
            </template>
            <template v-else>
              编辑并发布
            </template>
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 结果显示区域 -->
    <el-card class="result-card" v-if="isLoggedIn" shadow="hover">
      <template #header>
        <div class="card-header">
          <div class="header-left">
            <div class="icon-wrapper result-icon">
              <el-icon :size="24" color="#ffffff"><Document /></el-icon>
            </div>
            <div class="title-section">
              <h3 class="card-title">执行结果</h3>
              <p class="card-subtitle">查看操作日志和结果</p>
            </div>
          </div>
          <el-button 
            link
            size="small" 
            @click="clearResults" 
            :icon="Delete"
            class="clear-button"
          >
            清空结果
          </el-button>
        </div>
      </template>

      <div class="result-container" ref="resultContainer">
        <!-- 日志消息 -->
        <div
          v-for="(msg, index) in resultMessages"
          :key="index"
          :class="['result-item', `result-${msg.type}`]"
        >
          <div class="result-header">
            <div class="result-tag-wrapper">
              <el-tag :type="getResultTagType(msg)" size="small" effect="light" class="result-tag">
                {{ msg.typeLabel }}
              </el-tag>
              <span class="result-subtype" :class="`subtype-${msg.subtype}`">{{ msg.subtype }}</span>
            </div>
            <span class="result-time">{{ msg.time }}</span>
          </div>
          <div class="result-content">
            <div v-if="msg.type === 'log'" class="log-content">
              <div class="log-icon">
                <el-icon v-if="msg.subtype === 'error'"><CircleCloseFilled /></el-icon>
                <el-icon v-else-if="msg.subtype === 'success'"><CircleCheckFilled /></el-icon>
                <el-icon v-else><InfoFilled /></el-icon>
              </div>
              <div class="log-text">{{ msg.content }}</div>
            </div>
            <div v-else-if="msg.type === 'screenshot'" class="screenshot-content">
              <div class="screenshot-title">截图预览</div>
              <img :src="msg.content" alt="截图" @click="previewImage(msg.content)" />
              <div class="screenshot-hint">点击图片放大预览</div>
            </div>
            <div v-else-if="msg.type === 'result'" class="result-data">
              <div class="result-title">执行结果</div>
              <pre>{{ formatJson(msg.content) }}</pre>
              <div class="result-meta" :class="`meta-${msg.subtype}`">
                {{ msg.subtype === 'success' ? '✓ 操作成功' : '✗ 操作失败' }}
              </div>
            </div>
          </div>
        </div>
        <div v-if="resultMessages.length === 0" class="empty-result">
          <el-empty description="暂无执行结果" :image-size="120">
            <template #image>
              <div class="empty-icon-wrapper">
                <el-icon :size="80" color="#cbd5e1"><Document /></el-icon>
              </div>
            </template>
            <p class="empty-hint">执行操作后，结果将显示在这里</p>
          </el-empty>
        </div>
      </div>
    </el-card>

    <!-- 图片预览对话框 -->
    <el-dialog v-model="imagePreviewVisible" title="截图预览" width="80%" center class="image-dialog">
      <div class="image-preview-container">
        <img :src="previewImageUrl" alt="预览" />
        <div class="image-actions">
          <el-button type="primary" link @click="downloadImage">下载图片</el-button>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { ref, reactive, computed, onMounted, onBeforeUnmount, nextTick, watch } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { 
  Edit, 
  Promotion, 
  Document, 
  Delete, 
  InfoFilled, 
  Setting,
  CircleCheckFilled,
  CircleCloseFilled
} from '@element-plus/icons-vue';
import { getToken } from '@/utils/auth';
import { buildWebSocketUrl } from '@/utils/websocket';
import useUserStore from '@/store/modules/user';
import { 
  getEngineConfig, 
  restoreLoginStatusFromStorage
} from '@/config/engineConfig';
import { getStrategyByName } from '@/api/business/host/strategy';

export default {
  name: 'WorkflowNodeEdit',
  setup() {
    const router = useRouter();
    const userStore = useUserStore();

    // 登录状态（直接读取全局配置中的 loggedIn 字段）
    const yuanqiConfig = computed(() => getEngineConfig('yuanqi'));
    const isLoggedIn = computed(() => yuanqiConfig.value?.loggedIn || false);
    const loginAlertTitle = ref('未登录元器平台');
    const loginAlertMessage = ref('请先前往登录管理器完成元器登录，然后才能使用工作流节点编辑功能。');

    // WebSocket连接
    const ws = ref(null);
    const isConnected = ref(false);
    const connectionStatus = computed(() => {
      if (isConnected.value) {
        return { type: 'success', text: '已连接' };
      }
      return { type: 'info', text: '未连接' };
    });

    // 表单数据
    const editForm = reactive({
      spaceName: '福帮手开源',
      agentName: '日更助手1',
      workflowName: '日更助手-高优先级',
      nodeName: '混元大模型',
      strategy: '',
      timeout: 90,
      config: {
        modelName: '混元大模型长文本版',
        temperature: 0.4,
        topP: 0.8,
        maxTokens: 4000,
        prompt: '创作 700-900 字公众号文章，标题用《》包裹单独成行。开篇用真实感强的生活场景或个人经历引入，中间分 2-3 层逻辑推进，将核心观点融入故事线，穿插细节描写和轻幽默桥段，加入 1-2 个贴近读者的生活化案例，结尾结合读者需求引发共鸣并设计互动提问，按场景自然分段，语言活泼口语化，适度用网络热词增强传播感，不使用任何特殊符号。'
      }
    });

    // 加载状态
    const editAndPublishLoading = ref(false);

    // 编辑并发布流程状态
    const isEditAndPublishFlow = ref(false);
    const pendingPublishPayload = ref(null);

    // 结果消息
    const resultMessages = ref([]);
    const resultContainer = ref(null);

    // 图片预览
    const imagePreviewVisible = ref(false);
    const previewImageUrl = ref('');

    // WebSocket URL
    const wsUrl = computed(() => {
      const token = getToken();
      return buildWebSocketUrl({
        path: '/ws/client',
        token: token,
        clientType: 'web'
      });
    });

    // 初始化WebSocket连接
    const initWebSocket = () => {
      if (!isLoggedIn.value) {
        return;
      }

      try {
        const url = wsUrl.value;
        ws.value = new WebSocket(url);

        ws.value.onopen = () => {
          isConnected.value = true;
          addResultMessage('log', 'WebSocket连接成功', 'system');
          ElMessage.success('WebSocket连接成功');
        };

        ws.value.onmessage = (event) => {
          handleWebSocketMessage(event.data);
        };

        ws.value.onerror = (error) => {
          console.error('WebSocket错误:', error);
          addResultMessage('log', 'WebSocket连接错误', 'error');
          ElMessage.error('WebSocket连接错误');
        };

        ws.value.onclose = () => {
          isConnected.value = false;
          addResultMessage('log', 'WebSocket连接已关闭', 'system');
        };
      } catch (error) {
        console.error('初始化WebSocket失败:', error);
        ElMessage.error('初始化WebSocket失败: ' + error.message);
      }
    };

    // 处理WebSocket消息
    const handleWebSocketMessage = (data) => {
      try {
        const message = JSON.parse(data);
        const messageType = message.type || message.messageType;
        const payload = message.payload || message.data || {};

        console.log('收到WebSocket消息:', messageType, payload);

        // 处理任务日志
        if (messageType === 'TASK_LOG') {
          const logContent = payload.message || payload.content || JSON.stringify(payload);
          addResultMessage('log', logContent, 'info');
        }
        // 处理截图
        else if (messageType === 'TASK_SCREENSHOT') {
          const screenshotUrl = payload.screenshotUrl || payload.url || payload;
          if (screenshotUrl) {
            addResultMessage('screenshot', screenshotUrl, 'success');
          }
        }
        // 处理任务结果
        else if (messageType === 'TASK_RESULT' || messageType === 'TASK_COMPLETE') {
          const result = payload.data || payload;
          addResultMessage('result', result, payload.success ? 'success' : 'error');
          
          // 检查是否是编辑并发布流程中的编辑完成
          if (isEditAndPublishFlow.value && payload.success) {
            // 编辑完成，等待几秒后自动发布
            addResultMessage('log', '编辑完成，等待保存成功...', 'info');
            setTimeout(() => {
              if (pendingPublishPayload.value) {
                addResultMessage('log', '开始发布工作流...', 'info');
                if (sendMessage(pendingPublishPayload.value)) {
                  // 发布消息已发送，重置状态
                  isEditAndPublishFlow.value = false;
                  pendingPublishPayload.value = null;
                } else {
                  isEditAndPublishFlow.value = false;
                  pendingPublishPayload.value = null;
                }
              }
            }, 3000); // 等待3秒确保保存成功
          }
          
          // 更新加载状态
          if (!isEditAndPublishFlow.value || !pendingPublishPayload.value) {
            editAndPublishLoading.value = false;
          }

          if (payload.success) {
            if (!isEditAndPublishFlow.value) {
              ElMessage.success('操作完成');
            }
          } else {
            ElMessage.error(payload.message || '操作失败');
            // 编辑失败，重置编辑并发布流程
            if (isEditAndPublishFlow.value) {
              isEditAndPublishFlow.value = false;
              pendingPublishPayload.value = null;
              editAndPublishLoading.value = false;
            }
          }
        }
        // 处理错误
        else if (messageType === 'ERROR' || messageType === 'TASK_ERROR') {
          const errorMsg = payload.message || payload.error || JSON.stringify(payload);
          addResultMessage('log', `错误: ${errorMsg}`, 'error');
          
          // 更新加载状态
          editAndPublishLoading.value = false;

          ElMessage.error(errorMsg);
        }
      } catch (error) {
        console.error('解析WebSocket消息失败:', error);
        addResultMessage('log', `消息解析失败: ${data}`, 'error');
      }
    };

    // 发送WebSocket消息
    const sendMessage = (message) => {
      if (!isConnected.value) {
        ElMessage.warning('WebSocket未连接，请稍候再试');
        return false;
      }

      try {
        const messageStr = JSON.stringify(message);
        ws.value.send(messageStr);
        console.log('发送WebSocket消息:', message);
        return true;
      } catch (error) {
        console.error('发送消息失败:', error);
        ElMessage.error('发送消息失败: ' + error.message);
        return false;
      }
    };

    // 添加结果消息
    const addResultMessage = (type, content, subtype = 'info') => {
      const now = new Date();
      const time = `${now.getHours().toString().padStart(2, '0')}:${now.getMinutes().toString().padStart(2, '0')}:${now.getSeconds().toString().padStart(2, '0')}`;
      
      const typeLabels = {
        log: '日志',
        screenshot: '截图',
        result: '结果'
      };

      const subtypeLabels = {
        info: '信息',
        error: '错误',
        success: '成功',
        system: '系统',
        warning: '警告'
      };

      resultMessages.value.push({
        type,
        content,
        subtype,
        time,
        typeLabel: typeLabels[type] || '消息',
        subtypeText: subtypeLabels[subtype] || subtype
      });

      // 自动滚动到底部
      nextTick(() => {
        if (resultContainer.value) {
          resultContainer.value.scrollTop = resultContainer.value.scrollHeight;
        }
      });
    };

    // 格式化JSON
    const formatJson = (obj) => {
      try {
        if (typeof obj === 'string') {
          return JSON.stringify(JSON.parse(obj), null, 2);
        }
        return JSON.stringify(obj, null, 2);
      } catch (e) {
        return String(obj);
      }
    };

    // 获取结果标签类型
    const getResultTagType = (msg) => {
      if (msg.subtype === 'error') return 'danger';
      if (msg.subtype === 'success') return 'success';
      if (msg.subtype === 'system') return 'info';
      if (msg.subtype === 'warning') return 'warning';
      return 'info';
    };

    // 预览图片
    const previewImage = (url) => {
      previewImageUrl.value = url;
      imagePreviewVisible.value = true;
    };

    // 下载图片
    const downloadImage = () => {
      const link = document.createElement('a');
      link.href = previewImageUrl.value;
      link.download = `screenshot-${new Date().getTime()}.png`;
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
    };

    // 清空结果
    const clearResults = () => {
      resultMessages.value = [];
      ElMessage.success('结果已清空');
    };

    // 应用策略参数映射（前端实现）
    const applyStrategyParamMapping = async (strategyName, config) => {
      if (!strategyName || !strategyName.trim()) {
        return config; // 没有策略，直接返回原config
      }

      try {
        // 调用API获取策略参数
        const response = await getStrategyByName(strategyName.trim());
        if (response.code !== 200 || !response.data) {
          console.warn(`[策略参数映射] 未找到策略: ${strategyName}`);
          ElMessage.warning(`未找到策略: ${strategyName}，将使用表单中的配置`);
          return config;
        }

        const strategy = response.data;
        const mergedConfig = { ...config };

        // 应用策略参数覆盖（策略参数优先级更高，覆盖config中的现有值）
        if (strategy.modelName != null && strategy.modelName !== '') {
          mergedConfig.modelName = strategy.modelName;
        }
        if (strategy.temperature != null) {
          mergedConfig.temperature = parseFloat(strategy.temperature);
        }
        if (strategy.topP != null) {
          mergedConfig.topP = parseFloat(strategy.topP);
        }
        if (strategy.maxTokens != null) {
          mergedConfig.maxTokens = parseInt(strategy.maxTokens);
        }
        if (strategy.prompt != null && strategy.prompt !== '') {
          mergedConfig.prompt = strategy.prompt;
        }

        console.log(`[策略参数映射] 已应用策略: ${strategyName} -> config已覆盖`, mergedConfig);
        ElMessage.success(`已应用策略: ${strategyName}`);
        return mergedConfig;

      } catch (error) {
        console.error('[策略参数映射] 应用策略参数映射失败', error);
        ElMessage.warning(`获取策略参数失败: ${error.message}，将使用表单中的配置`);
        return config; // 失败时使用原config
      }
    };

    // 处理编辑并发布
    const handleEditAndPublish = async () => {
      if (!editForm.spaceName || !editForm.agentName || !editForm.workflowName || !editForm.nodeName) {
        ElMessage.warning('请填写完整的工作流和节点信息');
        return;
      }

      editAndPublishLoading.value = true;
      isEditAndPublishFlow.value = true;
      addResultMessage('log', '开始编辑节点，完成后将自动发布...', 'info');

      // 准备发布消息（稍后使用）
      const hostId = userStore.hostId || 'engine-001';
      pendingPublishPayload.value = {
        type: 'YUANQI_PUBLISH_WORKFLOW',
        engineId: hostId,
        payload: {
          spaceName: editForm.spaceName,
          agentName: editForm.agentName,
          workflowName: editForm.workflowName,
          timeout: editForm.timeout || 90
        }
      };

      // 🔧 前端策略参数映射：如果有策略名称，先获取策略参数并覆盖config
      let finalConfig = { ...editForm.config };
      if (editForm.strategy && editForm.strategy.trim()) {
        addResultMessage('log', `正在加载策略: ${editForm.strategy}...`, 'info');
        finalConfig = await applyStrategyParamMapping(editForm.strategy, finalConfig);
      }

      // 先执行编辑（使用映射后的config）
      const editMessage = {
        type: 'YUANQI_EDIT_NODE',
        engineId: hostId,
        payload: {
          spaceName: editForm.spaceName,
          agentName: editForm.agentName,
          workflowName: editForm.workflowName,
          nodeName: editForm.nodeName,
          timeout: editForm.timeout,
          strategy: editForm.strategy || '', // 保留strategy字段用于日志，但后端不会处理
          config: finalConfig // 使用映射后的config
        }
      };

      if (!sendMessage(editMessage)) {
        editAndPublishLoading.value = false;
        isEditAndPublishFlow.value = false;
        pendingPublishPayload.value = null;
      }
    };

    // 跳转到登录管理器
    const goToLoginManager = () => {
      router.push('/content/login-manager');
    };

    // 监听登录状态变化（当登录管理器更新状态后，自动连接/断开WebSocket）
    watch(isLoggedIn, (newVal) => {
      if (newVal && !isConnected.value) {
        // 登录后自动连接WebSocket
        console.log('✅ [工作流节点编辑] 检测到登录状态变化，已登录，连接WebSocket');
        setTimeout(() => {
          initWebSocket();
        }, 500);
      } else if (!newVal && isConnected.value) {
        // 未登录时关闭WebSocket
        console.log('⚠️ [工作流节点编辑] 检测到登录状态变化，未登录，关闭WebSocket');
        if (ws.value) {
          ws.value.close();
        }
      }
    });

    // 生命周期
    onMounted(() => {
      // 恢复登录状态（从 localStorage 恢复）
      restoreLoginStatusFromStorage();
      
      // 如果已登录，初始化WebSocket
      if (isLoggedIn.value) {
        initWebSocket();
      }
    });

    onBeforeUnmount(() => {
      if (ws.value) {
        ws.value.close();
      }
    });

    return {
      isLoggedIn,
      loginAlertTitle,
      loginAlertMessage,
      connectionStatus,
      editForm,
      editAndPublishLoading,
      resultMessages,
      resultContainer,
      imagePreviewVisible,
      previewImageUrl,
      Promotion,
      Document,
      Delete,
      InfoFilled,
      Setting,
      CircleCheckFilled,
      CircleCloseFilled,
      handleEditAndPublish,
      goToLoginManager,
      clearResults,
      formatJson,
      getResultTagType,
      previewImage,
      downloadImage
    };
  }
};
</script>

<style scoped lang="scss">
.workflow-node-edit-container {
  padding: 32px;
  background: linear-gradient(135deg, #f8fafc 0%, #e2e8f0 100%);
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  gap: 24px;

  .login-alert {
    margin-bottom: 0;
    border-radius: 12px;
    border: none;
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
    
    &.success-alert {
      background: linear-gradient(135deg, #ecfdf5 0%, #d1fae5 100%);
      border: 1px solid #a7f3d0;
    }

    .login-button {
      margin-left: 12px;
      font-weight: 600;
    }
  }

  .edit-card, .result-card {
    border-radius: 16px;
    box-shadow: 0 8px 32px rgba(0, 0, 0, 0.08);
    transition: transform 0.3s ease, box-shadow 0.3s ease;
    border: 1px solid #e2e8f0;
    overflow: hidden;
    
    &:hover {
      transform: translateY(-2px);
      box-shadow: 0 12px 40px rgba(0, 0, 0, 0.12);
    }

    .card-header {
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
      padding: 24px 32px;
      background: linear-gradient(135deg, #ffffff 0%, #f8fafc 100%);
      border-bottom: 1px solid #e2e8f0;

      .header-left {
        display: flex;
        align-items: center;
        gap: 20px;

        .icon-wrapper {
          width: 56px;
          height: 56px;
          background: linear-gradient(135deg, #6366f1 0%, #8b5cf6 100%);
          border-radius: 14px;
          display: flex;
          align-items: center;
          justify-content: center;
          box-shadow: 0 4px 12px rgba(99, 102, 241, 0.25);
          
          &.result-icon {
            background: linear-gradient(135deg, #10b981 0%, #059669 100%);
            box-shadow: 0 4px 12px rgba(16, 185, 129, 0.25);
          }
        }

        .title-section {
          .card-title {
            font-size: 22px;
            font-weight: 700;
            color: #1e293b;
            margin: 0 0 6px 0;
            line-height: 1.2;
          }
          
          .card-subtitle {
            font-size: 14px;
            color: #64748b;
            margin: 0;
            font-weight: 500;
          }
        }
      }

      .status-tag {
        font-weight: 600;
        letter-spacing: 0.5px;
        padding: 6px 16px;
        border-radius: 20px;
        font-size: 13px;
      }

      .clear-button {
        color: #64748b;
        font-weight: 500;
        
        &:hover {
          color: #ef4444;
          background: #fef2f2;
          border-radius: 8px;
        }
      }
    }
  }

  .edit-form {
    padding: 32px;
    
    .form-item-enhanced {
      margin-bottom: 28px;
      
      .el-form-item__label {
        font-weight: 600;
        color: #475569;
        font-size: 15px;
        padding-right: 20px;
      }
      
      .form-item-hint {
        font-size: 12px;
        color: #94a3b8;
        margin-top: 6px;
        line-height: 1.4;
      }
    }

    .enhanced-input {
      .el-input__wrapper {
        border-radius: 12px;
        border: 2px solid #e2e8f0;
        background: #ffffff;
        transition: all 0.3s ease;
        padding: 8px 16px;
        
        &:hover {
          border-color: #cbd5e1;
          box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
        }
        
        &.is-focus {
          border-color: #6366f1;
          box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.1);
        }
      }
    }

    .enhanced-number-input {
      width: 100%;
      
      .el-input-number__decrease,
      .el-input-number__increase {
        background: #f1f5f9;
        border: none;
        border-radius: 8px;
        color: #475569;
        transition: all 0.2s ease;
        
        &:hover {
          background: #e2e8f0;
          color: #1e293b;
        }
      }
      
      .el-input__wrapper {
        border-radius: 12px;
        border: 2px solid #e2e8f0;
        background: #ffffff;
      }
    }

    .enhanced-textarea {
      .el-textarea__inner {
        border-radius: 12px;
        border: 2px solid #e2e8f0;
        background: #ffffff;
        padding: 16px;
        font-family: 'Inter', -apple-system, BlinkMacSystemFont, sans-serif;
        line-height: 1.6;
        transition: all 0.3s ease;
        
        &:hover {
          border-color: #cbd5e1;
          box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
        }
        
        &:focus {
          border-color: #6366f1;
          box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.1);
        }
      }
    }

    .section-divider {
      margin: 40px 0;
      
      .divider-content {
        display: flex;
        align-items: center;
        gap: 10px;
        background: #ffffff;
        padding: 0 20px;
        color: #475569;
        font-size: 16px;
        font-weight: 600;
        
        .el-icon {
          color: #6366f1;
        }
      }
      
      &::before {
        background-color: #e2e8f0;
      }
      
      &::after {
        background-color: #e2e8f0;
      }
    }

    .form-hint {
      margin-top: 12px;
      display: flex;
      align-items: center;
      gap: 8px;
      font-size: 13px;
      color: #64748b;
      background: #f8fafc;
      padding: 12px;
      border-radius: 8px;
      border-left: 4px solid #6366f1;

      .el-icon {
        color: #6366f1;
        font-size: 16px;
      }
    }

    .action-buttons {
      margin-top: 48px;
      padding-top: 32px;
      border-top: 2px solid #f1f5f9;
      display: flex;
      gap: 20px;
      justify-content: center;
      
      .action-button {
        min-width: 180px;
        height: 52px;
        font-size: 16px;
        font-weight: 600;
        border-radius: 14px;
        transition: all 0.3s ease;
        letter-spacing: 0.5px;
        position: relative;
        overflow: hidden;
        
        &.el-button--primary {
          background: linear-gradient(135deg, #6366f1 0%, #8b5cf6 100%);
          border: none;
          
          &:hover {
            transform: translateY(-2px);
            box-shadow: 0 12px 24px rgba(99, 102, 241, 0.25);
          }
          
          &:active {
            transform: translateY(0);
          }
        }
        
        &.el-button--success {
          background: linear-gradient(135deg, #10b981 0%, #059669 100%);
          border: none;
          
          &:hover {
            transform: translateY(-2px);
            box-shadow: 0 12px 24px rgba(16, 185, 129, 0.25);
          }
          
          &:active {
            transform: translateY(0);
          }
        }
        
        &:disabled {
          opacity: 0.6;
          transform: none !important;
          box-shadow: none !important;
        }
        
        .el-icon {
          font-size: 20px;
          margin-right: 10px;
        }
        
        .button-loading-text {
          display: inline-flex;
          align-items: center;
          gap: 8px;
        }
      }
    }
  }

  .result-container {
    max-height: 700px;
    overflow-y: auto;
    padding: 24px;
    background: #f8fafc;
    border-radius: 12px;
    
    .result-item {
      margin-bottom: 20px;
      border-radius: 14px;
      border: none;
      background: white;
      box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
      transition: all 0.3s ease;
      border-left: 6px solid;
      overflow: hidden;
      
      &.result-log {
        border-left-color: #60a5fa;
        background: linear-gradient(135deg, #ffffff 0%, #f0f9ff 100%);
      }
      
      &.result-screenshot {
        border-left-color: #34d399;
        background: linear-gradient(135deg, #ffffff 0%, #f0fdf4 100%);
      }
      
      &.result-result {
        border-left-color: #8b5cf6;
        background: linear-gradient(135deg, #ffffff 0%, #f5f3ff 100%);
      }
      
      &:hover {
        transform: translateY(-2px);
        box-shadow: 0 8px 24px rgba(0, 0, 0, 0.1);
      }
      
      &:last-child {
        margin-bottom: 0;
      }
      
      .result-header {
        display: flex;
        align-items: center;
        justify-content: space-between;
        padding: 20px 24px;
        background: rgba(255, 255, 255, 0.5);
        border-bottom: 1px solid #f1f5f9;
        
        .result-tag-wrapper {
          display: flex;
          align-items: center;
          gap: 12px;
          
          .result-tag {
            font-weight: 600;
            padding: 6px 14px;
            border-radius: 20px;
          }
          
          .result-subtype {
            font-size: 12px;
            padding: 4px 12px;
            border-radius: 12px;
            font-weight: 500;
            
            &.subtype-error {
              background: #fef2f2;
              color: #dc2626;
            }
            
            &.subtype-success {
              background: #f0fdf4;
              color: #16a34a;
            }
            
            &.subtype-system {
              background: #eff6ff;
              color: #2563eb;
            }
            
            &.subtype-info {
              background: #f8fafc;
              color: #64748b;
            }
            
            &.subtype-warning {
              background: #fffbeb;
              color: #d97706;
            }
          }
        }
        
        .result-time {
          color: #94a3b8;
          font-size: 13px;
          font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
          font-weight: 500;
        }
      }
      
      .result-content {
        padding: 24px;
        
        .log-content {
          display: flex;
          align-items: flex-start;
          gap: 16px;
          
          .log-icon {
            flex-shrink: 0;
            width: 40px;
            height: 40px;
            background: #f1f5f9;
            border-radius: 10px;
            display: flex;
            align-items: center;
            justify-content: center;
            
            .el-icon {
              font-size: 20px;
              
              &:has(+ .subtype-error) {
                color: #dc2626;
              }
              
              &:has(+ .subtype-success) {
                color: #16a34a;
              }
            }
          }
          
          .log-text {
            flex: 1;
            white-space: pre-wrap;
            word-break: break-all;
            color: #1e293b;
            font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
            font-size: 14px;
            line-height: 1.8;
            padding: 16px;
            background: #ffffff;
            border-radius: 10px;
            border: 1px solid #e2e8f0;
          }
        }
        
        .screenshot-content {
          .screenshot-title {
            font-size: 16px;
            font-weight: 600;
            color: #475569;
            margin-bottom: 16px;
          }
          
          img {
            max-width: 100%;
            max-height: 500px;
            border-radius: 12px;
            cursor: pointer;
            transition: all 0.3s ease;
            border: 2px solid #e2e8f0;
            display: block;
            margin: 0 auto;
            box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
            
            &:hover {
              transform: scale(1.01);
              box-shadow: 0 8px 32px rgba(0, 0, 0, 0.12);
              border-color: #cbd5e1;
            }
          }
          
          .screenshot-hint {
            text-align: center;
            color: #94a3b8;
            font-size: 13px;
            margin-top: 12px;
            font-style: italic;
          }
        }
        
        .result-data {
          .result-title {
            font-size: 16px;
            font-weight: 600;
            color: #475569;
            margin-bottom: 16px;
            display: flex;
            align-items: center;
            gap: 10px;
          }
          
          pre {
            margin: 0;
            padding: 24px;
            background: #ffffff;
            border: 1px solid #e2e8f0;
            border-radius: 12px;
            overflow-x: auto;
            font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
            font-size: 14px;
            line-height: 1.8;
            color: #1e293b;
            max-height: 400px;
            overflow-y: auto;
          }
          
          .result-meta {
            margin-top: 16px;
            padding: 12px 20px;
            border-radius: 10px;
            font-weight: 600;
            font-size: 14px;
            
            &.meta-success {
              background: #f0fdf4;
              color: #16a34a;
              border: 1px solid #bbf7d0;
            }
            
            &.meta-error {
              background: #fef2f2;
              color: #dc2626;
              border: 1px solid #fecaca;
            }
          }
        }
      }
    }
    
    .empty-result {
      display: flex;
      justify-content: center;
      align-items: center;
      min-height: 300px;
      
      .el-empty {
        .empty-icon-wrapper {
          width: 120px;
          height: 120px;
          background: #f1f5f9;
          border-radius: 50%;
          display: flex;
          align-items: center;
          justify-content: center;
          margin-bottom: 20px;
        }
        
        .el-empty__description {
          color: #64748b;
          font-size: 16px;
          font-weight: 500;
          margin-bottom: 8px;
        }
        
        .empty-hint {
          color: #94a3b8;
          font-size: 14px;
          text-align: center;
          margin-top: 8px;
        }
      }
    }
  }

  .image-dialog {
    .el-dialog__header {
      border-bottom: 1px solid #e2e8f0;
      padding: 24px;
      margin: 0;
      
      .el-dialog__title {
        font-size: 20px;
        font-weight: 600;
        color: #1e293b;
      }
    }
    
    .image-preview-container {
      text-align: center;
      padding: 32px;
      
      img {
        max-width: 100%;
        max-height: 70vh;
        border-radius: 12px;
        box-shadow: 0 8px 32px rgba(0, 0, 0, 0.1);
        border: 1px solid #e2e8f0;
      }
      
      .image-actions {
        margin-top: 24px;
        
        .el-button {
          font-weight: 500;
          color: #6366f1;
          
          &:hover {
            color: #4f46e5;
          }
        }
      }
    }
  }
}

// 滚动条样式
.result-container::-webkit-scrollbar {
  width: 8px;
}

.result-container::-webkit-scrollbar-track {
  background: #f1f5f9;
  border-radius: 4px;
}

.result-container::-webkit-scrollbar-thumb {
  background: #cbd5e1;
  border-radius: 4px;
  
  &:hover {
    background: #94a3b8;
  }
}

// 响应式设计
@media (max-width: 768px) {
  .workflow-node-edit-container {
    padding: 16px;
    gap: 16px;
    
    .edit-card, .result-card {
      .card-header {
        flex-direction: column;
        gap: 16px;
        padding: 20px;
        align-items: stretch;
        
        .header-left {
          gap: 16px;
          
          .icon-wrapper {
            width: 48px;
            height: 48px;
            border-radius: 12px;
          }
          
          .title-section {
            .card-title {
              font-size: 18px;
            }
            
            .card-subtitle {
              font-size: 13px;
            }
          }
        }
        
        .status-tag {
          align-self: flex-start;
        }
      }
    }
    
    .edit-form {
      padding: 20px;
      
      .el-row {
        margin: 0 !important;
        
        .el-col {
          width: 100%;
          padding: 0;
          margin-bottom: 20px;
        }
      }
      
      .form-item-enhanced {
        .el-form-item__label {
          width: 100% !important;
          text-align: left;
          margin-bottom: 8px;
          padding-right: 0;
        }
      }
      
      .action-buttons {
        flex-direction: column;
        gap: 16px;
        
        .action-button {
          width: 100%;
          min-width: unset;
          height: 48px;
        }
      }
    }
    
    .result-container {
      padding: 16px;
      max-height: 500px;
      
      .result-item {
        .result-header {
          flex-direction: column;
          gap: 12px;
          align-items: stretch;
          padding: 16px;
          
          .result-tag-wrapper {
            justify-content: space-between;
          }
          
          .result-time {
            align-self: flex-end;
          }
        }
        
        .result-content {
          padding: 16px;
          
          .log-content {
            flex-direction: column;
            gap: 12px;
            
            .log-icon {
              align-self: center;
            }
          }
        }
      }
    }
  }
}

// 动画效果
@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.result-item {
  animation: fadeIn 0.3s ease-out;
}

// 加载动画
@keyframes shimmer {
  0% {
    background-position: -200px 0;
  }
  100% {
    background-position: 200px 0;
  }
}

.action-button.is-loading {
  position: relative;
  overflow: hidden;
  
  &::after {
    content: '';
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.2), transparent);
    animation: shimmer 1.5s infinite;
  }
}
</style>
