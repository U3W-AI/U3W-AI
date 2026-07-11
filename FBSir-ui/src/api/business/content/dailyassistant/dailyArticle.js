import request from '@/utils/request'

// 查询日更助手文章列表
export function listDailyArticle(query) {
  return request({
    url: '/system/daily-article/list',
    method: 'get',
    params: query
  })
}

// 查询当前用户的日更助手文章列表
export function getMyArticles(query) {
  return request({
    url: '/system/daily-article/myList',
    method: 'get',
    params: query
  })
}

// 查询日更助手文章详细
export function getDailyArticle(id) {
  return request({
    url: '/system/daily-article/' + id,
    method: 'get'
  })
}

// 新增日更助手文章
export function addDailyArticle(data) {
  return request({
    url: '/system/daily-article',
    method: 'post',
    data: data
  })
}

// 创建文章并调用智能体优化
export function createAndOptimize(articleTitle, selectedModels) {
  return request({
    url: '/system/daily-article/createAndOptimize',
    method: 'post',
    data: { 
      articleTitle,
      selectedModels: selectedModels.join(',') // 将数组转为逗号分隔的字符串，如 "1,2,3"
    }
  })
}

// 保存大模型生成的文章内容
export function saveModelContent(data) {
  return request({
    url: '/system/daily-article/saveModelContent',
    method: 'post',
    data: data
  })
}

// 更新优化后的文章内容
export function updateOptimizedContent(articleId, optimizedContent) {
  return request({
    url: '/system/daily-article/updateOptimizedContent',
    method: 'post',
    data: { articleId, optimizedContent }
  })
}

// 对文章内容进行智能排版（同步返回排版结果，不保存到数据库）
// 注意：排版调用腾讯元器同步工作流，通常需要10-20秒
export function layoutArticle(content) {
  return request({
    url: '/system/daily-article/layoutArticle',
    method: 'post',
    data: { content }
    // 使用全局60秒超时，无需单独设置
  })
}

// 修改日更助手文章
export function updateDailyArticle(data) {
  return request({
    url: '/system/daily-article',
    method: 'put',
    data: data
  })
}

// 删除日更助手文章
export function delDailyArticle(ids) {
  return request({
    url: '/system/daily-article/' + ids,
    method: 'delete'
  })
}

// 导出日更助手文章
export function exportDailyArticle(query) {
  return request({
    url: '/system/daily-article/export',
    method: 'post',
    params: query
  })
}

// 投递文章到公众号
// contentType: optimized, model1, model2, model3, layout
// 当contentType为layout时，需要同时传入layoutedContent作为投递内容
export function publishToWechat(articleId, contentType, layoutedContent) {
  const data = {
    articleId,
    contentType
  }

  if (contentType === 'layout' && layoutedContent) {
    data.layoutedContent = layoutedContent
  }

  return request({
    url: '/system/daily-article/publishToWechat',
    method: 'post',
    data
  })
}

// 新增layoutArticle方法
export function layoutArticleNew(content) {
  return request({
    url: '/system/daily-article/layoutArticle',
    method: 'post',
    data: { content }
  })
}
