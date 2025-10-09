import request from '@/utils/request'

// 执行AI任务
export function executeAiTask(data) {
  return request({
    url: '/api/ai-platform/execute',
    method: 'post',
    data
  })
}

// 获取执行状态
export function getExecutionStatus(taskId) {
  return request({
    url: '/api/ai-platform/status/' + taskId,
    method: 'get'
  })
}

// 获取执行结果
export function getExecutionResult(taskId) {
  return request({
    url: '/api/ai-platform/result/' + taskId,
    method: 'get'
  })
} 