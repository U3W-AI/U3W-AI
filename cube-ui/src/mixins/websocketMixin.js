import websocketClient from '@/utils/websocket'

export default {
  data() {
    return {
      wsConnected: false,
      wsReconnecting: false,
      wsError: null
    }
  },

  methods: {
    // 初始化WebSocket连接
    initWebSocketConnection(userId) {
      if (!userId) {
        console.error('初始化WebSocket需要userId参数');
        return;
      }

      const wsUrl = process.env.VUE_APP_WS_API + `mypc-${userId}`;
      console.log('WebSocket URL:', wsUrl);

      websocketClient.connect(wsUrl, this.handleWebSocketEvent);
    },

    // 处理WebSocket事件
    handleWebSocketEvent(event) {
      switch (event.type) {
        case 'open':
          this.wsConnected = true;
          this.wsError = null;
          this.wsReconnecting = false;
          this.onWebSocketOpen && this.onWebSocketOpen();
          break;

        case 'message':
          this.onWebSocketMessage && this.onWebSocketMessage(event.data);
          break;

        case 'close':
          this.wsConnected = false;
          this.onWebSocketClose && this.onWebSocketClose();
          break;

        case 'error':
          this.wsError = event.error;
          this.onWebSocketError && this.onWebSocketError(event.error);
          break;

        case 'reconnect_failed':
          this.wsReconnecting = false;
          this.onWebSocketReconnectFailed && this.onWebSocketReconnectFailed();
          break;
      }
    },

    // 发送WebSocket消息
    sendWebSocketMessage(data) {
      if (!this.wsConnected) {
        this.$message.warning('WebSocket未连接，请稍后重试');
        return false;
      }

      const success = websocketClient.send(data);
      if (!success) {
        this.$message.error('消息发送失败，请检查网络连接');
      }
      return success;
    },

    // 关闭WebSocket连接
    closeWebSocketConnection() {
      websocketClient.close();
      this.wsConnected = false;
    },

    // 重新连接WebSocket
    reconnectWebSocket(userId) {
      if (this.wsReconnecting) {
        return;
      }
      this.wsReconnecting = true;
      this.initWebSocketConnection(userId);
    },

    // 获取WebSocket连接状态
    getWebSocketState() {
      return websocketClient.getState();
    }
  },

  // 组件销毁时自动关闭WebSocket连接
  beforeDestroy() {
    this.closeWebSocketConnection();
  }
} 