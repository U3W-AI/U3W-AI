import storage from '@/utils/storage';
import constant from '@/utils/constant';


export default class WebSocketService {
  constructor(url) {
    this.url = url; // WebSocket 地址
    this.socket = null; // WebSocket 对象
    this.messageCallback = null; // 消息回调函数
    this.isConnected = false; // 是否已连接
  }

  /**
   * 初始化 WebSocket
   */
  connect() {
    console.log('正在连接 WebSocket...');
    this.socket = uni.connectSocket({
      url: this.url,
      success: () => {
        console.log('WebSocket 连接成功:', this.url);
      },
      fail: (err) => {
        console.error('WebSocket 连接失败:', err);
      },
    });

    // 注册事件监听
    this.socket.onOpen(() => {
      console.log('WebSocket 已打开');
      this.isConnected = true;
	    // 连接成功后，动态获取 userId 和 corpId
	const userId = storage.get(constant.userId);
	const corpId = storage.get(constant.corpId);
	console.log("WebSocket 连接成功，userId:", userId);
     this.sendMessage({
						type: "PLAY_CHECK_YB_LOGIN",
						userId: userId,
						corpId: corpId,
						username: '',
						password: '',
						phone: '',
						code: ''
					});
		// this.sendMessage({
		// 						type: "PLAY_CHECK_QWEN_LOGIN",
		// 						userId: userId,
		// 						corpId: corpId,
		// 						username: '',
		// 						password: '',
		// 						phone: '',
		// 						code: ''
		// 					});
	
    });

    this.socket.onMessage((res) => {
      const message = JSON.parse(res.data);
      console.log('收到 WebSocket 消息:', message);

      // 如果有回调函数，调用回调
      if (this.messageCallback) {
        this.messageCallback(message);
      }
    });

    this.socket.onError((err) => {
      console.error('WebSocket 错误:', err);
      this.isConnected = false;
    });

    this.socket.onClose(() => {
      console.log('WebSocket 已关闭');
      this.isConnected = false;
    });
  }

  /**
   * 设置消息回调函数
   * @param {Function} callback - 消息处理回调
   */
  setMessageCallback(callback) {
    this.messageCallback = callback;
  }

  /**
   * 发送消息
   * @param {Object} data - 要发送的数据
   */
  sendMessage(data) {
    if (!this.isConnected) {
      console.error('WebSocket 未连接，无法发送消息');
      return;
    }
    this.socket.send({
      data: JSON.stringify(data),
      success: () => {
        console.log('消息发送成功:', data);
      },
      fail: (err) => {
        console.error('消息发送失败:', err);
      },
    });
  }

  /**
   * 关闭 WebSocket 连接
   */
  close() {
    if (this.socket) {
      this.socket.close();
      this.isConnected = false;
    }
  }
}