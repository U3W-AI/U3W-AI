import request from '@/utils/request'

// 查询文档解析列表
export function listDocumentParse(query) {
  return request({
    url: '/system/document-parse/list',
    method: 'get',
    params: query
  })
}

// 查询当前用户的文档解析列表
export function getMyDocumentParses(query) {
  return request({
    url: '/system/document-parse/myList',
    method: 'get',
    params: query
  })
}

// 查询文档解析详细
export function getDocumentParse(id) {
  return request({
    url: '/system/document-parse/' + id,
    method: 'get'
  })
}

// 新增文档解析
export function addDocumentParse(data) {
  return request({
    url: '/system/document-parse',
    method: 'post',
    data: data
  })
}

// 上传文档并调用智能体解析
// file: 文档文件（MultipartFile）
// prompt: 提示词
// documentId: 文档ID（可选，如果不提供会自动生成）
// documentName: 文档名称（可选，如果不提供会使用文件名）
export function uploadAndParse(file, prompt, documentId, documentName) {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('prompt', prompt)
  if (documentId) {
    formData.append('documentId', documentId)
  }
  if (documentName) {
    formData.append('documentName', documentName)
  }
  
  return request({
    url: '/system/document-parse/uploadAndParse',
    method: 'post',
    data: formData,
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  })
}

// 更新解析后的内容（供工作流回调使用）
export function updateParsedContent(documentId, parsedContent) {
  return request({
    url: '/system/document-parse/updateParsedContent',
    method: 'post',
    data: { documentId, parsedContent }
  })
}

// 查询文档解析处理状态（用于前端轮询）
export function getDocumentParseStatus(id) {
  return request({
    url: '/system/document-parse/status/' + id,
    method: 'get'
  })
}

// 修改文档解析
export function updateDocumentParse(data) {
  return request({
    url: '/system/document-parse',
    method: 'put',
    data: data
  })
}

// 删除文档解析
export function delDocumentParse(ids) {
  return request({
    url: '/system/document-parse/' + ids,
    method: 'delete'
  })
}

// 导出文档解析
export function exportDocumentParse(query) {
  return request({
    url: '/system/document-parse/export',
    method: 'post',
    params: query
  })
}

