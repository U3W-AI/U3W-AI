import request from '@/utils/request'
import axios from 'axios'
import { getToken } from '@/utils/auth'

/**
 * 生成当前会话的输出物
 * 设计说明：
 * 后端基于 sessionId 生成输出物，并写回会话数据中。
 *
 * @param {Object} data 请求参数
 * @returns {Promise}
 */
export function generateOutputArtifact(data) {
  return request({
    url: '/aigc/output/generate',
    method: 'post',
    data: data
  })
}

/**
 * 导出当前会话的 Markdown 输出物
 * 设计说明：
 * 该接口可能返回 Markdown 文件流，也可能返回 JSON 错误对象，
 * 因此单独使用 axios 获取完整响应对象，便于前端根据 content-type 区分成功与失败。
 *
 * @param {string} sessionId 会话ID
 * @returns {Promise}
 */
export function exportOutputMarkdown(sessionId) {
  return axios({
    url: import.meta.env.VITE_APP_BASE_API + '/aigc/output/exportMarkdown/' + sessionId,
    method: 'get',
    responseType: 'blob',
    headers: {
      Authorization: 'Bearer ' + getToken()
    },
    timeout: 60000
  })
}

/**
 * 导出当前会话的 JSON 输出物
 * 设计说明：
 * 返回标准结构数据，供前端展示或 Webhook 推送复用。
 *
 * @param {string} sessionId 会话ID
 * @returns {Promise}
 */
export function exportOutputJson(sessionId) {
  return request({
    url: '/aigc/output/exportJson/' + sessionId,
    method: 'get'
  })
}

/**
 * 推送当前会话的输出物到指定 Webhook
 * 设计说明：
 * 推送内容由后端统一构造，前端仅负责传递会话标识、格式与目标地址。
 *
 * @param {Object} data 请求参数
 * @returns {Promise}
 */
export function pushOutputWebhook(data) {
  return request({
    url: '/aigc/output/pushWebhook',
    method: 'post',
    data: data
  })
}

/**
 * 保存用户编辑后的输出物内容
 * 设计说明：
 * 保存后会覆盖当前会话中对应的输出物，保证后续导出与推送使用最新内容。
 *
 * @param {Object} data 请求参数
 * @returns {Promise}
 */
export function saveOutputArtifact(data) {
  return request({
    url: '/aigc/output/save',
    method: 'post',
    data: data
  })
}