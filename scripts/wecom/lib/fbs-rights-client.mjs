/**
 * FBS Rights Client — Skill 端权益对接模块
 * 
 * 封装 FBS Skill API 网关的 5 个接口调用：
 * 1. rightsCheck — 校验用户权益
 * 2. usageConsume — 一次性消费（校验+扣减+记录）
 * 3. usageStart / usageEnd — 两阶段使用记录（纯日志，不扣费）
 * 4. scenePackQuery — 查询场景包规则
 * 5. userInfo — 查询用户积分+场景包
 * 
 * 离线降级：API 不可用时回退本地缓存
 * 
 * @module fbs-rights-client
 */

const CACHE_TTL_MS = 5 * 60 * 1000; // 本地缓存 5 分钟

class FbsRightsClient {
  /**
   * @param {object} config
   * @param {string} config.apiKey - API Key（从 _plugin_meta.json 读取）
   * @param {string} config.apiBaseUrl - 后端地址（如 http://localhost:8080）
   * @param {string} config.userId - 用户ID（从 process.env.WB_USER_ID 获取）
   */
  constructor(config = {}) {
    this.apiKey = config.apiKey || process.env.FBS_API_KEY || '';
    this.apiBaseUrl = (config.apiBaseUrl || process.env.FBS_API_BASE_URL || 'http://localhost:8080').replace(/\/+$/, '');
    this.userId = config.userId || process.env.WB_USER_ID || '';
    this._cache = new Map(); // 本地缓存（离线降级）
  }

  // ========== 公共方法 ==========

  /**
   * 校验用户是否有指定场景包的权益
   * @param {string} packCode - 场景包编码
   * @param {object} [options] - 可选参数
   * @param {string} [options.authCode] - 授权码
   * @param {string} [options.hostType] - 宿主类型
   * @returns {Promise<{pass: boolean, failReason: string|null, packId: number|null, pointsRuleCode: string|null, pointsAmount: number}>}
   */
  async rightsCheck(packCode, options = {}) {
    return this._post('/fbs/skill-api/rights/check', {
      userId: Number(this.userId),
      packCode,
      authCode: options.authCode || null,
      hostType: options.hostType || 'WORKBUDDY',
    });
  }

  /**
   * 一次性消费：校验 → 扣减 → 记录（自闭环）
   * ⚠️ 与 start/end 互斥，同一 usageRecordId 只能走一种模式
   * @param {string} packCode - 场景包编码
   * @param {string} skillCode - Skill 编码
   * @param {string} usageRecordId - 幂等键（UUID 或 taskId）
   * @param {object} [options]
   * @returns {Promise<{success: boolean, usageRecordId: string, remainPoints: number|null, failReason: string|null}>}
   */
  async usageConsume(packCode, skillCode, usageRecordId, options = {}) {
    return this._post('/fbs/skill-api/usage/consume', {
      userId: Number(this.userId),
      packCode,
      skillCode,
      usageRecordId,
      authCode: options.authCode || null,
      hostType: options.hostType || 'WORKBUDDY',
    });
  }

  /**
   * 两阶段模式：开始使用记录（不扣费，纯日志）
   * ⚠️ 与 consume 互斥
   * @param {string} packCode - 场景包编码
   * @param {string} skillCode - Skill 编码
   * @param {string} usageRecordId - 幂等键
   * @param {object} [options]
   * @returns {Promise<{usageRecordId: string, status: number}>}
   */
  async usageStart(packCode, skillCode, usageRecordId, options = {}) {
    return this._post('/fbs/skill-api/usage/start', {
      userId: Number(this.userId),
      packCode,
      skillCode,
      usageRecordId,
      hostType: options.hostType || 'WORKBUDDY',
    });
  }

  /**
   * 两阶段模式：结束使用记录（不扣费，纯日志）
   * @param {string} usageRecordId - start 返回的幂等键
   * @param {number} status - 1=成功, 2=失败
   * @param {string} [errorMessage] - 失败原因
   * @returns {Promise<{msg: string}>}
   */
  async usageEnd(usageRecordId, status, errorMessage = null) {
    return this._put(`/fbs/skill-api/usage/end/${encodeURIComponent(usageRecordId)}`, {
      status,
      errorMessage,
    });
  }

  /**
   * 查询场景包规则（热更新）
   * 返回 contentSnapshot（原始 JSON 字符串，需自行 JSON.parse）
   * @param {string} packCode - 场景包编码
   * @returns {Promise<{packCode: string, packName: string, currentVersion: string, status: number, pointsRuleCode: string|null, contentSnapshot: string}>}
   */
  async scenePackQuery(packCode) {
    const cacheKey = `scenePack:${packCode}`;
    const cached = this._getCached(cacheKey);
    if (cached) return cached;

    const result = await this._post('/fbs/skill-api/scene-pack/query', { packCode });
    if (result && result.packCode) {
      this._setCache(cacheKey, result);
    }
    return result;
  }

  /**
   * 查询用户信息（积分余额 + 已激活场景包）
   * 不返回 T0-T3 层级
   * @returns {Promise<{userId: number, pointsBalance: number, activatedPacks: Array}>}
   */
  async userInfo() {
    return this._post('/fbs/skill-api/user/info', {
      userId: Number(this.userId),
    });
  }

  // ========== 内部方法 ==========

  async _post(path, body) {
    try {
      const response = await fetch(`${this.apiBaseUrl}${path}`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'X-FBS-API-Key': this.apiKey,
        },
        body: JSON.stringify(body),
      });

      const json = await response.json();

      if (response.ok && json.code === 200) {
        return json.data;
      }

      // 业务错误
      console.warn(`[FBS] API 错误: ${json.msg || json.message || response.statusText}`);
      return null;
    } catch (err) {
      console.warn(`[FBS] API 不可用，离线降级: ${err.message}`);
      return null;
    }
  }

  async _put(path, body) {
    try {
      const response = await fetch(`${this.apiBaseUrl}${path}`, {
        method: 'PUT',
        headers: {
          'Content-Type': 'application/json',
          'X-FBS-API-Key': this.apiKey,
        },
        body: JSON.stringify(body),
      });

      const json = await response.json();

      if (response.ok && json.code === 200) {
        return json.data || { msg: json.msg };
      }

      console.warn(`[FBS] API 错误: ${json.msg || json.message || response.statusText}`);
      return null;
    } catch (err) {
      console.warn(`[FBS] API 不可用，离线降级: ${err.message}`);
      return null;
    }
  }

  // ========== 本地缓存（离线降级）==========

  _getCached(key) {
    const entry = this._cache.get(key);
    if (!entry) return null;
    if (Date.now() - entry.ts > CACHE_TTL_MS) {
      this._cache.delete(key);
      return null;
    }
    return entry.data;
  }

  _setCache(key, data) {
    this._cache.set(key, { data, ts: Date.now() });
  }
}

// 导出
export default FbsRightsClient;
export { FbsRightsClient };
