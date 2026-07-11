<template>
  <div class="websocket-debug-container">
    <el-card class="box-card">
      <template #header>
        <div class="card-header">
          <span>WebSocket 调试工具</span>
          <el-tag :type="connectionStatus.type" size="small">{{ connectionStatus.text }}</el-tag>
        </div>
      </template>

      <!-- 连接配置 -->
      <el-form :model="form" label-width="100px" size="default">
        <el-form-item label="连接地址">
          <el-input v-model="wsUrl" placeholder="自动从系统配置获取" readonly>
            <template #prepend>
              <el-button :icon="Link" @click="reconnect">{{ isConnected ? '重新连接' : '连接' }}</el-button>
            </template>
          </el-input>
        </el-form-item>

        <el-form-item label="客户端类型">
          <el-select v-model="form.clientType" placeholder="选择客户端类型">
            <el-option label="网页端 (web)" value="web" />
            <el-option label="小程序端 (mini)" value="mini" />
          </el-select>
        </el-form-item>

        <el-form-item label="消息内容">
          <el-input
            v-model="form.messageJson"
            type="textarea"
            :rows="12"
            placeholder="输入完整的JSON消息，包含 type、engineId、payload 等字段"
            @keydown.ctrl.enter="sendMessage"
            class="json-input"
          />
          <div class="hint-text">
            <span>💡 提示：按 Ctrl+Enter 快速发送</span>
            <el-button type="text" size="small" @click="formatJson">格式化</el-button>
            <el-button type="text" size="small" @click="loadExample">加载示例</el-button>
          </div>
        </el-form-item>

        <el-form-item>
          <el-button type="primary" @click="sendMessage" :disabled="!isConnected" :icon="Promotion">
            发送消息
          </el-button>
          <el-button @click="clearMessages" :icon="Delete">清空消息</el-button>
          <el-button @click="exportMessages" :icon="Download">导出日志</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 消息输出区域 -->
    <el-card class="box-card message-output-card">
      <template #header>
        <div class="card-header">
          <span>消息输出 ({{ messages.length }})</span>
          <div>
            <el-checkbox v-model="autoScroll" size="small">自动滚动</el-checkbox>
            <el-checkbox v-model="prettyPrint" size="small">美化显示</el-checkbox>
          </div>
        </div>
      </template>

      <div ref="messageContainer" class="message-container">
        <div
          v-for="(msg, index) in messages"
          :key="index"
          :class="['message-item', `message-${msg.direction}`, `message-type-${msg.type}`]"
        >
          <div class="message-header">
            <el-tag :type="getMessageTagType(msg)" size="small">{{ msg.direction === 'send' ? '发送' : '接收' }}</el-tag>
            <span class="message-time">{{ msg.time }}</span>
            <el-tag v-if="msg.messageType" size="small" effect="plain">{{ msg.messageType }}</el-tag>
          </div>
          <div class="message-content">
            <div v-if="prettyPrint && isJsonString(msg.content)" class="json-display" v-html="highlightJson(msg.content)"></div>
            <pre v-else-if="isJsonString(msg.content)" class="json-plain">{{ formatJsonContent(msg.content) }}</pre>
            <div v-else class="message-text">{{ msg.content }}</div>
          </div>
          <div v-if="msg.parsed" class="message-parsed">
            <el-descriptions :column="2" size="small" border>
              <el-descriptions-item label="类型">{{ msg.parsed.type || msg.parsed.messageType || '-' }}</el-descriptions-item>
              <el-descriptions-item label="成功">
                <el-tag v-if="msg.parsed.payload?.success !== undefined" :type="msg.parsed.payload.success ? 'success' : 'danger'" size="small">
                  {{ msg.parsed.payload.success ? '是' : '否' }}
                </el-tag>
                <span v-else>-</span>
              </el-descriptions-item>
              <el-descriptions-item label="消息" :span="2" v-if="msg.parsed.payload?.message">
                {{ msg.parsed.payload.message }}
              </el-descriptions-item>
            </el-descriptions>
          </div>
        </div>
        <div v-if="messages.length === 0" class="empty-message">
          <el-empty description="暂无消息" />
        </div>
      </div>
    </el-card>
  </div>
</template>

<script>
import { ref, reactive, onMounted, onBeforeUnmount, nextTick, computed } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { Link, Promotion, Delete, Download } from '@element-plus/icons-vue';
import { getToken } from '@/utils/auth';
import { buildWebSocketUrl } from '@/utils/websocket';

export default {
  name: 'WebSocketDebug',
  components: {
    Link,
    Promotion,
    Delete,
    Download
  },
  setup() {
    // WebSocket连接
    const ws = ref(null);
    const isConnected = ref(false);
    const messageContainer = ref(null);

    // 表单数据
    const form = reactive({
      clientType: 'web',
      messageJson: ''
    });

    // 消息列表
    const messages = ref([]);
    
    // 配置项
    const autoScroll = ref(true);
    const prettyPrint = ref(true);
    

    // WebSocket URL
    const wsUrl = computed(() => {
      const token = getToken();
      return buildWebSocketUrl({
        path: '/ws/client',
        token: token,
        clientType: form.clientType || 'web'
      });
    });

    // 连接状态
    const connectionStatus = computed(() => {
      if (isConnected.value) {
        return { type: 'success', text: '已连接' };
      }
      return { type: 'info', text: '未连接' };
    });

    // 示例数据
    const examples = {
      'simple': {
        type: 'SIMPLE_HEALTH_CHECK_DEMO',
        engineId: 'engine-001',
        payload: {
          includeDetails: true
        }
      },
      'stream': {
        type: 'BAIDU_HOT_SEARCH_DEMO',
        engineId: 'engine-001',
        payload: {
          clickIndex: 0,
          needScreenshot: true
        }
      },
      'complex': {
        type: 'COMPLEX_TASK',
        engineId: 'engine-001',
        payload: {
          config: {
            timeout: 30000,
            retry: true
          },
          filters: [
            { field: 'status', value: 'active' }
          ],
          metadata: {
            tags: ['tag1', 'tag2']
          }
        }
      },
      'heartbeat': {
        type: 'HEARTBEAT_PING',
        engineId: 'engine-001',
        payload: {}
      }
    };

    // 初始化WebSocket连接
    const initWebSocket = () => {
      try {
        const url = wsUrl.value;
        ws.value = new WebSocket(url);

        ws.value.onopen = () => {
          isConnected.value = true;
          addMessage('system', '连接成功', 'system');
          ElMessage.success('WebSocket连接成功');
        };

        ws.value.onmessage = (event) => {
          addMessage('receive', event.data, 'receive');
        };

        ws.value.onerror = (error) => {
          console.error('WebSocket错误:', error);
          addMessage('system', 'WebSocket连接错误', 'error');
          ElMessage.error('WebSocket连接错误');
        };

        ws.value.onclose = () => {
          isConnected.value = false;
          addMessage('system', '连接已关闭', 'system');
        };
      } catch (error) {
        console.error('初始化WebSocket失败:', error);
        ElMessage.error('初始化WebSocket失败: ' + error.message);
      }
    };

    // 发送消息
    const sendMessage = () => {
      if (!isConnected.value) {
        ElMessage.warning('请先连接WebSocket');
        return;
      }

      if (!form.messageJson || !form.messageJson.trim()) {
        ElMessage.warning('请输入消息内容');
        return;
      }

      try {
        // 解析完整的JSON消息
        const message = JSON.parse(form.messageJson);
        
        // 验证必要字段
        if (!message.type) {
          ElMessage.error('消息必须包含 type 字段');
          return;
        }
        if (!message.engineId) {
          ElMessage.error('消息必须包含 engineId 字段');
          return;
        }

        const messageStr = JSON.stringify(message);
        ws.value.send(messageStr);
        addMessage('send', messageStr, 'send');
        ElMessage.success('消息已发送');
      } catch (error) {
        console.error('发送消息失败:', error);
        ElMessage.error('JSON格式错误: ' + error.message);
      }
    };

    // 添加消息到列表
    const addMessage = (direction, content, type = 'normal') => {
      const now = new Date();
      const time = `${now.getHours().toString().padStart(2, '0')}:${now.getMinutes().toString().padStart(2, '0')}:${now.getSeconds().toString().padStart(2, '0')}.${now.getMilliseconds().toString().padStart(3, '0')}`;
      
      let parsed = null;
      let messageType = null;
      
      // 尝试解析JSON
      if (isJsonString(content)) {
        try {
          parsed = JSON.parse(content);
          messageType = parsed.type || parsed.messageType || null;
        } catch (e) {
          // 忽略解析错误
        }
      }

      messages.value.push({
        direction,
        content,
        type,
        time,
        messageType,
        parsed
      });

      // 自动滚动到底部
      if (autoScroll.value) {
        nextTick(() => {
          if (messageContainer.value) {
            messageContainer.value.scrollTop = messageContainer.value.scrollHeight;
          }
        });
      }
    };

    // 清空消息
    const clearMessages = () => {
      ElMessageBox.confirm('确定要清空所有消息吗？', '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }).then(() => {
        messages.value = [];
        ElMessage.success('消息已清空');
      }).catch(() => {});
    };

    // 导出消息
    const exportMessages = () => {
      if (messages.value.length === 0) {
        ElMessage.warning('暂无消息可导出');
        return;
      }

      const content = messages.value.map(msg => {
        return `[${msg.time}] ${msg.direction === 'send' ? '发送' : '接收'}: ${msg.content}`;
      }).join('\n\n');

      const blob = new Blob([content], { type: 'text/plain;charset=utf-8' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `websocket-debug-${Date.now()}.log`;
      a.click();
      URL.revokeObjectURL(url);
      ElMessage.success('日志已导出');
    };

    // 重新连接
    const reconnect = () => {
      if (ws.value) {
        ws.value.close();
      }
      // 延迟一下再重连，确保旧连接已关闭
      setTimeout(() => {
        initWebSocket();
      }, 300);
    };

    // 加载示例
    const loadExample = () => {
      ElMessageBox({
        title: '选择示例',
        message: '请选择一个示例消息',
        showCancelButton: true,
        confirmButtonText: '加载',
        cancelButtonText: '取消'
      }).then(() => {
        // 默认加载复杂示例
        form.messageJson = JSON.stringify(examples.complex, null, 2);
        ElMessage.success('示例已加载');
      }).catch(() => {});
      
      // 简化：直接加载复杂示例
      form.messageJson = JSON.stringify(examples.complex, null, 2);
      ElMessage.success('已加载复杂任务示例');
    };

    // 格式化JSON
    const formatJson = () => {
      if (!form.messageJson || !form.messageJson.trim()) {
        return;
      }

      try {
        const obj = JSON.parse(form.messageJson);
        form.messageJson = JSON.stringify(obj, null, 2);
        ElMessage.success('JSON已格式化');
      } catch (e) {
        ElMessage.error('JSON格式错误，无法格式化');
      }
    };

    // 判断是否为JSON字符串
    const isJsonString = (str) => {
      if (typeof str !== 'string') return false;
      try {
        JSON.parse(str);
        return true;
      } catch (e) {
        return false;
      }
    };

    // 格式化JSON内容
    const formatJsonContent = (str) => {
      try {
        const obj = JSON.parse(str);
        return JSON.stringify(obj, null, 2);
      } catch (e) {
        return str;
      }
    };

    // JSON语法高亮
    const highlightJson = (str) => {
      try {
        const obj = JSON.parse(str);
        const jsonStr = JSON.stringify(obj, null, 2);
        
        return jsonStr
          .replace(/&/g, '&amp;')
          .replace(/</g, '&lt;')
          .replace(/>/g, '&gt;')
          // URL链接识别和点击
          .replace(/"(https?:\/\/[^"]+)"/g, '"<a href="$1" target="_blank" class="json-link">$1</a>"')
          // JSON字段高亮
          .replace(/"([\w-]+)":/g, '<span class="json-key">"$1"</span>:') // 键
          .replace(/: "([^"]*)"/g, (match, p1) => {
            // 如果已经是链接，不再处理
            if (match.includes('<a href')) return match;
            return `: <span class="json-string">"${p1}"</span>`;
          }) // 字符串值
          .replace(/: (true|false)/g, ': <span class="json-boolean">$1</span>') // 布尔值
          .replace(/: (null)/g, ': <span class="json-null">$1</span>') // null
          .replace(/: (-?\d+\.?\d*)/g, ': <span class="json-number">$1</span>'); // 数字
      } catch (e) {
        return str;
      }
    };

    // 获取消息标签类型
    const getMessageTagType = (msg) => {
      if (msg.direction === 'send') return '';
      if (msg.type === 'error') return 'danger';
      if (msg.type === 'system') return 'info';
      
      // 根据消息类型判断
      if (msg.messageType) {
        if (msg.messageType.includes('ERROR')) return 'danger';
        if (msg.messageType === 'TASK_RESULT') {
          return msg.parsed?.payload?.success ? 'success' : 'danger';
        }
        if (msg.messageType === 'TASK_LOG') return 'info';
        if (msg.messageType === 'TASK_SCREENSHOT') return 'warning';
      }
      
      return 'success';
    };


    // 生命周期
    onMounted(() => {
      initWebSocket();
      // 加载默认示例
      form.messageJson = JSON.stringify(examples.complex, null, 2);
    });

    onBeforeUnmount(() => {
      if (ws.value) {
        ws.value.close();
      }
    });

    return {
      form,
      messages,
      isConnected,
      wsUrl,
      connectionStatus,
      autoScroll,
      prettyPrint,
      messageContainer,
      sendMessage,
      clearMessages,
      exportMessages,
      reconnect,
      loadExample,
      formatJson,
      isJsonString,
      formatJsonContent,
      highlightJson,
      getMessageTagType,
      Link,
      Promotion,
      Delete,
      Download
    };
  }
};
</script>

<style scoped lang="scss">
.websocket-debug-container {
  padding: 20px;
}

.box-card {
  margin-bottom: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.hint-text {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 8px;
  font-size: 12px;
  color: #909399;
}

.message-output-card {
  .message-container {
    max-height: 600px;
    overflow-y: auto;
    background-color: var(--el-bg-color-page);
    padding: 16px;
    border-radius: 4px;
    font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
    font-size: 13px;
    line-height: 1.6;
  }

  .message-item {
    margin-bottom: 12px;
    padding: 16px;
    border-radius: 6px;
    border: 1px solid var(--el-border-color);
    border-left: 4px solid var(--el-color-primary);
    background-color: var(--el-fill-color-blank);
    box-shadow: 0 2px 4px rgba(0, 0, 0, 0.08);
    transition: all 0.3s ease;

    &:hover {
      box-shadow: 0 4px 8px rgba(0, 0, 0, 0.12);
      transform: translateX(2px);
    }

    &.message-send {
      border-left-color: var(--el-color-success);
      border-color: var(--el-color-success-light-5);
      background-color: var(--el-color-success-light-9);

      &:hover {
        background-color: var(--el-color-success-light-8);
        border-color: var(--el-color-success-light-3);
      }
    }

    &.message-receive {
      border-left-color: var(--el-color-primary);
      border-color: var(--el-color-primary-light-5);
      background-color: var(--el-color-primary-light-9);

      &:hover {
        background-color: var(--el-color-primary-light-8);
        border-color: var(--el-color-primary-light-3);
      }

      &.message-type-error {
        border-left-color: var(--el-color-danger);
        border-color: var(--el-color-danger-light-5);
        background-color: var(--el-color-danger-light-9);

        &:hover {
          background-color: var(--el-color-danger-light-8);
          border-color: var(--el-color-danger-light-3);
        }
      }
    }

    &.message-system {
      border-left-color: var(--el-color-info);
      border-color: var(--el-color-info-light-5);
      background-color: var(--el-color-info-light-9);

      &:hover {
        background-color: var(--el-color-info-light-8);
        border-color: var(--el-color-info-light-3);
      }
    }
  }

  .message-header {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 8px;
  }

  .message-time {
    color: var(--el-text-color-secondary);
    font-size: 12px;
    font-family: 'Consolas', 'Monaco', monospace;
  }

  .message-content {
    color: var(--el-text-color-primary);
    word-break: break-all;

    .json-display {
      margin: 0;
      padding: 16px;
      background-color: var(--el-fill-color-light);
      border: 1px solid var(--el-border-color);
      border-radius: 6px;
      overflow-x: auto;
      font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
      font-size: 13px;
      line-height: 1.8;
      white-space: pre;
    }

    .json-plain {
      margin: 0;
      padding: 16px;
      background-color: var(--el-fill-color-light);
      border: 1px solid var(--el-border-color);
      border-radius: 6px;
      overflow-x: auto;
      color: var(--el-text-color-primary);
      font-size: 13px;
      line-height: 1.8;
    }

    .message-text {
      white-space: pre-wrap;
      color: var(--el-text-color-primary);
      padding: 8px;
      background-color: var(--el-fill-color-light);
      border-radius: 4px;
    }
  }

  .message-parsed {
    margin-top: 12px;
    padding-top: 12px;
    border-top: 1px solid var(--el-border-color-lighter);

    :deep(.el-descriptions) {
      --el-descriptions-item-bordered-label-background: var(--el-fill-color-light);
      --el-descriptions-item-bordered-content-background: var(--el-bg-color);
    }

    :deep(.el-descriptions__label) {
      color: var(--el-text-color-regular);
      font-weight: 600;
    }

    :deep(.el-descriptions__content) {
      color: var(--el-text-color-primary);
    }

    :deep(.el-descriptions__cell) {
      border-color: var(--el-border-color);
    }
  }

  .empty-message {
    display: flex;
    justify-content: center;
    align-items: center;
    min-height: 200px;
  }
}

// 滚动条样式
.message-container::-webkit-scrollbar {
  width: 8px;
}

.message-container::-webkit-scrollbar-track {
  background: var(--el-fill-color-lighter);
  border-radius: 4px;
}

.message-container::-webkit-scrollbar-thumb {
  background: var(--el-border-color-dark);
  border-radius: 4px;

  &:hover {
    background: var(--el-border-color-darker);
  }
}

// JSON语法高亮样式（自动适配明暗主题）
.json-key {
  color: var(--el-color-primary);
  font-weight: 600;
}

.json-string {
  color: var(--el-color-success);
}

.json-number {
  color: var(--el-color-warning);
}

.json-boolean {
  color: var(--el-color-danger);
}

.json-null {
  color: var(--el-text-color-secondary);
}

.json-link {
  color: var(--el-color-primary);
  text-decoration: underline;
  cursor: pointer;
  transition: color 0.2s;

  &:hover {
    opacity: 0.8;
    text-decoration: underline;
  }
}

// 暗色主题特殊优化
@media (prefers-color-scheme: dark) {
  .json-key {
    color: #79c0ff;
  }
  
  .json-string {
    color: #7ee787;
  }
  
  .json-number {
    color: #ffa657;
  }
  
  .json-boolean {
    color: #ff7b72;
  }
  
  .json-null {
    color: #8b949e;
  }
  
  .json-link {
    color: #58a6ff;
  }
}

// JSON输入框样式
.json-input {
  :deep(textarea) {
    font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
    font-size: 13px;
    line-height: 1.8;
  }
}

// 消息解析区域优化
.message-parsed {
  :deep(.el-descriptions__body) {
    background-color: transparent;
  }

  :deep(.el-descriptions__table) {
    border-color: var(--el-border-color);
  }

  :deep(.el-descriptions-item__cell) {
    border-color: var(--el-border-color);
  }

  :deep(.el-descriptions-item__label) {
    background-color: var(--el-fill-color-light) !important;
    color: var(--el-text-color-regular) !important;
  }

  :deep(.el-descriptions-item__content) {
    background-color: var(--el-fill-color-blank) !important;
    color: var(--el-text-color-primary) !important;
  }
}
</style>
