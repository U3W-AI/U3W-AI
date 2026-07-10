/**
 * WebSocket URL 解析工具
 * 从HTTP URL自动解析对应的WebSocket URL
 */

/**
 * 将HTTP URL转换为WebSocket URL
 * @param {string} httpUrl - HTTP URL (如: http://localhost:8080 或 https://api.example.com)
 * @returns {string} WebSocket URL (如: ws://localhost:8080 或 wss://api.example.com)
 */
export function httpToWs(httpUrl) {
  if (!httpUrl) {
    return '';
  }

  if (httpUrl.startsWith('ws://') || httpUrl.startsWith('wss://')) {
    return httpUrl.replace(/\/$/, '');
  }
  
  try {
    // 如果不是完整URL，添加协议
    let fullUrl = httpUrl;
    if (!httpUrl.startsWith('http://') && !httpUrl.startsWith('https://')) {
      fullUrl = 'http://' + httpUrl;
    }
    
    const url = new URL(fullUrl);
    
    // http -> ws, https -> wss
    if (url.protocol === 'https:') {
      url.protocol = 'wss:';
    } else if (url.protocol === 'http:') {
      url.protocol = 'ws:';
    }
    
    return url.toString().replace(/\/$/, ''); // 移除末尾的斜杠
  } catch (e) {
    console.error('解析URL失败:', e);
    // 降级处理：简单替换协议
    if (httpUrl.startsWith('https://')) {
      return httpUrl.replace('https://', 'wss://');
    } else if (httpUrl.startsWith('http://')) {
      return httpUrl.replace('http://', 'ws://');
    }
    return 'ws://' + httpUrl;
  }
}

/**
 * 将WebSocket URL转换为HTTP URL
 * @param {string} wsUrl - WebSocket URL (如: ws://localhost:8080 或 wss://api.example.com)
 * @returns {string} HTTP URL (如: http://localhost:8080 或 https://api.example.com)
 */
export function wsToHttp(wsUrl) {
  if (!wsUrl) {
    return '';
  }
  
  try {
    const url = new URL(wsUrl);
    
    // ws -> http, wss -> https
    if (url.protocol === 'wss:') {
      url.protocol = 'https:';
    } else if (url.protocol === 'ws:') {
      url.protocol = 'http:';
    }
    
    return url.toString().replace(/\/$/, ''); // 移除末尾的斜杠
  } catch (e) {
    console.error('解析URL失败:', e);
    return wsUrl.replace(/^ws:/, 'http:').replace(/^wss:/, 'https:');
  }
}

/**
 * 从配置的baseURL获取WebSocket URL
 * @param {string} path - WebSocket路径 (如: /ws/client)
 * @returns {string} 完整的WebSocket URL
 */
export function getWebSocketUrl(path = '/ws/client') {
  // 获取配置的API地址（可能是完整URL或路径前缀）
  let baseApi = '';
  
  // 尝试从import.meta.env获取（Vite环境）
  if (typeof import.meta !== 'undefined' && import.meta.env && import.meta.env.VITE_APP_BASE_API) {
    baseApi = import.meta.env.VITE_APP_BASE_API;
  }
  // 兼容Vue CLI环境
  else if (typeof process !== 'undefined' && process.env && process.env.VUE_APP_BASE_API) {
    baseApi = process.env.VUE_APP_BASE_API;
  }
  
  const configuredWsBase = typeof import.meta !== 'undefined' && import.meta.env
    ? import.meta.env.VITE_APP_WS_BASE_URL
    : '';

  // 显式配置优先；否则让相对 API 前缀与 HTTP 请求一样走同源网关/开发代理。
  let wsBaseUrl;
  if (configuredWsBase) {
    wsBaseUrl = httpToWs(configuredWsBase);
  } else if (baseApi && (baseApi.startsWith('http://') || baseApi.startsWith('https://'))) {
    // 情况1: 完整URL（如 http://localhost:8090 或 https://api.example.com）
    // 直接转换为WebSocket URL
    wsBaseUrl = httpToWs(baseApi);
  } else {
    // 情况2: 路径前缀（如 /dev-api 或 /prod-api）。保留前缀并使用当前页面同源地址，
    // 由 Vite/nginx 网关代理 WebSocket Upgrade，避免固定端口和跨域配置漂移。
    const normalizedBaseApi = baseApi
      ? '/' + baseApi.replace(/^\/+|\/+$/g, '')
      : '';
    wsBaseUrl = httpToWs(`${window.location.origin}${normalizedBaseApi}`);
  }
  
  // 确保path以/开头
  const normalizedPath = path.startsWith('/') ? path : '/' + path;
  
  return wsBaseUrl.replace(/\/$/, '') + normalizedPath;
}

/**
 * 构建完整的WebSocket连接URL（包含查询参数）
 * @param {Object} options - 配置选项
 * @param {string} options.path - WebSocket路径
 * @param {string} options.token - 认证token
 * @param {string} options.clientType - 客户端类型 (默认: web)
 * @returns {string} 完整的WebSocket URL
 */
export function buildWebSocketUrl(options = {}) {
  const {
    path = '/ws/client',
    token = '',
    clientType = 'web'
  } = options;
  
  const baseUrl = getWebSocketUrl(path);
  const params = new URLSearchParams();
  
  if (clientType) {
    params.append('clientType', clientType);
  }
  
  if (token) {
    params.append('token', token);
  }
  
  const queryString = params.toString();
  return queryString ? `${baseUrl}?${queryString}` : baseUrl;
}
