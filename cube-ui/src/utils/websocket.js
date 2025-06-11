class WebSocketClient {
  constructor() {
    this.ws = null;
    this.messageCallback = null;
    this.reconnectAttempts = 0;
    this.maxReconnectAttempts = 5;
    this.reconnectInterval = 3000; // 3秒重连间隔
  }

  // 初始化WebSocket连接
  connect(url, messageCallback) {
    this.messageCallback = messageCallback;
    
    try {
      this.ws = new WebSocket(url);

      this.ws.onopen = () => {
        console.log('WebSocket连接成功');
        this.reconnectAttempts = 0; // 重置重连次数
        if (this.messageCallback) {
          this.messageCallback({ type: 'open' });
        }
      };

      this.ws.onmessage = (event) => {
        if (this.messageCallback) {
          this.messageCallback({ type: 'message', data: event.data });
        }
      };

      this.ws.onclose = () => {
        console.log('WebSocket连接关闭');
        if (this.messageCallback) {
          this.messageCallback({ type: 'close' });
        }
        this.reconnect(url);
      };

      this.ws.onerror = (error) => {
        console.error('WebSocket连接错误:', error);
        if (this.messageCallback) {
          this.messageCallback({ type: 'error', error });
        }
      };
    } catch (error) {
      console.error('WebSocket初始化错误:', error);
      this.reconnect(url);
    }
  }

  // 重连机制
  reconnect(url) {
    if (this.reconnectAttempts < this.maxReconnectAttempts) {
      this.reconnectAttempts++;
      console.log(`尝试重连 (${this.reconnectAttempts}/${this.maxReconnectAttempts})...`);
      
      setTimeout(() => {
        this.connect(url, this.messageCallback);
      }, this.reconnectInterval);
    } else {
      console.log('达到最大重连次数，停止重连');
      if (this.messageCallback) {
        this.messageCallback({ type: 'reconnect_failed' });
      }
    }
  }

  // 发送消息
  send(data) {
    if (this.ws && this.ws.readyState === WebSocket.OPEN) {
      this.ws.send(typeof data === 'string' ? data : JSON.stringify(data));
      return true;
    }
    return false;
  }

  // 关闭连接
  close() {
    if (this.ws) {
      this.ws.close();
      this.ws = null;
    }
  }

  // 获取连接状态
  getState() {
    if (!this.ws) return WebSocket.CLOSED;
    return this.ws.readyState;
  }
}

// 创建单例实例
const websocketClient = new WebSocketClient();

export default websocketClient; 