<script setup>
import { onMounted, onBeforeUnmount, ref, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Collection,
  FolderOpened,
  Guide,
  View,
  Plus,
  Refresh,
  Delete,
  Reading,
  Upload,
  UploadFilled,
  FolderAdd,
  Loading,
  CircleCheck,
  CircleClose,
  Edit,
  Setting,
  InfoFilled,
  Star,
  StarFilled
} from '@element-plus/icons-vue'
import useUserStore from '@/store/modules/user'
import { getToken } from '@/utils/auth'
import { buildWebSocketUrl } from '@/utils/websocket'
import {
  getUserKnowledgeList,
  getPublicTemplates,
  createKnowledgeBase,
  updateKnowledgeBase,
  deleteKnowledgeBase,
  uploadKnowledgeBase,
  uploadLocalDocument,
  toggleFavoriteKnowledge,
  getFavoriteKnowledgeList,
  getUserExtendInfo,
  getKnowledgeBasesByIds,
  updateAccountPermission,
  getAllUserPermissions,
  updateSpaceQuota
} from '@/api/business/content/knowledge/knowledge'

const userStore = useUserStore()

// 列表 & 选择
const loading = ref(false)
const knowledgeList = ref([])
const selectedKb = ref(null)
const listType = ref('my') // 'my' - 我的知识库, 'favorite' - 收藏的知识库

// 创建知识库表单
const creating = ref(false)
const createForm = ref({
  kbName: '',
  kbContent: '',
  isPublicTemplate: 0
})

// 创建导引弹窗
const guideDialogVisible = ref(false)

// 知识库统计信息
const kbStats = ref({
  quota: 1024, // 空间额度（MB）
  used: 0, // 已使用空间（MB）
  count: 0 // 已创建知识库数量
})

// 修改知识库弹窗
const editDialogVisible = ref(false)
const editForm = ref({
  kbId: null,
  kbName: '',
  kbContent: '',
  isPublicTemplate: 0
})
const editing = ref(false)

// 权限控制弹窗
const permissionDialogVisible = ref(false)
const permissionForm = ref({
  userId: null,
  userName: '',
  permissionType: 1, // 1-账户权限管理权限，2-模块功能操作权限
  isOpen: 0
})
const updatingPermission = ref(false)
const allUserPermissions = ref([])
const loadingPermissions = ref(false)
const editingPermission = ref(null) // 正在编辑的权限项 {userId, permissionType}
const editingQuota = ref(null) // 正在编辑的空间限额 {userId}
const quotaForm = ref({
  userId: null,
  quota: 0
})
const updatingQuota = ref(false)

// 当前用户权限信息
const currentUserInfo = ref(null)

// 上传相关
const uploadDialogVisible = ref(false)
const uploadType = ref(1) // 1-元器 2-企微机器人 3-两者
const uploadFile = ref(null)
const uploadFileList = ref([]) // el-upload组件的文件列表
const uploadRef = ref(null) // el-upload组件的引用
const agentName = ref('') // 智能体名称
const robotName = ref('') // 机器人名称
const teamName = ref('') // 团队名称

// 知识库上传弹窗
const uploadKbDialogVisible = ref(false)
const uploadKbType = ref(1) // 1-元器 2-企微机器人 3-两者
const uploadKbAgentName = ref('') // 智能体名称
const uploadKbRobotName = ref('') // 机器人名称
const uploadKbTeamName = ref('') // 团队名称

// 二维码登录弹窗
const qrCodeDialogVisible = ref(false)
const qrCodeUrl = ref('')
const qrCodeStatus = ref('waiting') // waiting, success, timeout
const qrCodeMessage = ref('请使用微信扫码登录')

// WebSocket连接（用于接收二维码截图）
const ws = ref(null)
const isWsConnected = ref(false)

const uploadTypeOptions = [
  { label: '智能体元器', value: 1 },
  { label: '企业微信机器人', value: 2 },
  { label: '同时上传到两者', value: 3 }
]

// 允许的文件格式
const allowedFileTypes = ['pdf', 'doc', 'docx', 'ppt', 'mhtml', 'pptx', 'wps', 'ppsx', 'xlsx', 'xls', 'md', 'txt', 'csv', 'html', 'pg', 'png', 'jpeg', 'tiff', 'bmp', 'gif']
const maxFileSize = 20 * 1024 * 1024 // 20MB in bytes

// 移除文件
const removeFileFromList = (file, fileList) => {
  // 清空文件引用
  uploadFile.value = null
  // 清空文件列表
  uploadFileList.value = []
  // 如果el-upload组件有引用，使用其方法移除文件
  if (uploadRef.value) {
    nextTick(() => {
      uploadRef.value.clearFiles()
    })
  }
  // 如果传入了fileList，手动移除
  if (fileList && fileList.length > 0) {
    const index = fileList.findIndex(f => f.uid === file.uid)
    if (index > -1) {
      fileList.splice(index, 1)
    }
  }
}

// 文件移除回调
const handleFileRemove = () => {
  uploadFile.value = null
  uploadFileList.value = []
}

const handleFileChange = (file, fileList) => {
  const fileObj = file.raw || file

  // 验证文件格式
  const fileName = fileObj.name || ''
  const fileExtension = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase()
  if (!allowedFileTypes.includes(fileExtension)) {
    ElMessage.error(`文件格式不支持，只允许上传以下格式：${allowedFileTypes.join('、')}`)
    // 移除文件
    removeFileFromList(file, fileList)
    return false
  }

  // 验证文件大小
  if (fileObj.size > maxFileSize) {
    ElMessage.error('文件大小不能超过20MB')
    // 移除文件
    removeFileFromList(file, fileList)
    return false
  }

  // 验证通过，保存文件
  uploadFile.value = fileObj
  uploadFileList.value = [file]
  return true
}

// 检查是否有修改知识库的权限
const canEditKnowledge = (kb) => {
  if (!currentUserInfo.value) return false

  // 超级用户有所有权限
  if (currentUserInfo.value.isSuper === 1) {
    return true
  }

  // 公共模板：需要模块功能操作权限
  if (kb.isPublicTemplate === 1) {
    return currentUserInfo.value.isOpenModulePerm === 1
  }

  // 非公共模板：需要是拥有者
  const hasKbIds = currentUserInfo.value.hasKnowledgeBase || ''
  const kbIdStr = String(kb.kbId)
  return hasKbIds.split(',').some(id => id.trim() === kbIdStr)
}

// 检查是否有删除知识库的权限
const canDeleteKnowledge = (kb) => {
  if (!currentUserInfo.value) return false

  // 超级用户有所有权限
  if (currentUserInfo.value.isSuper === 1) {
    return true
  }

  // 公共模板：需要模块功能操作权限
  if (kb.isPublicTemplate === 1) {
    return currentUserInfo.value.isOpenModulePerm === 1
  }

  // 非公共模板：需要是拥有者
  const hasKbIds = currentUserInfo.value.hasKnowledgeBase || ''
  const kbIdStr = String(kb.kbId)
  return hasKbIds.split(',').some(id => id.trim() === kbIdStr)
}

// 检查是否有权限管理权限
const canManagePermission = () => {
  if (!currentUserInfo.value) return false
  return currentUserInfo.value.isSuper === 1 || currentUserInfo.value.isOpenAccountPerm === 1
}

// 检查是否有修改空间限额权限
const canManageSpaceQuota = () => {
  if (!currentUserInfo.value) return false
  return currentUserInfo.value.isSuper === 1 || currentUserInfo.value.isOpenModulePerm === 1
}

// 打开修改知识库弹窗
const handleEditKnowledge = (row) => {
  editForm.value = {
    kbId: row.kbId,
    kbName: row.kbName,
    kbContent: row.kbContent,
    isPublicTemplate: row.isPublicTemplate || 0
  }
  editDialogVisible.value = true
}

// 提交修改知识库
const handleSubmitEdit = async () => {
  if (!editForm.value.kbName.trim()) {
    ElMessage.warning('请填写知识库名称')
    return
  }
  if (!editForm.value.kbContent.trim()) {
    ElMessage.warning('请填写知识库内容')
    return
  }

  editing.value = true
  try {
    await updateKnowledgeBase({
      kbId: editForm.value.kbId,
      kbName: editForm.value.kbName.trim(),
      kbContent: editForm.value.kbContent,
      isPublicTemplate: editForm.value.isPublicTemplate
    })
    ElMessage.success('修改知识库成功')
    editDialogVisible.value = false
    await loadData()
    // 如果修改的是当前选中的知识库，更新选中项
    if (selectedKb.value && selectedKb.value.kbId === editForm.value.kbId) {
      const updatedKb = knowledgeList.value.find(kb => kb.kbId === editForm.value.kbId)
      if (updatedKb) {
        selectedKb.value = updatedKb
      }
    }
  } catch (e) {
    ElMessage.error(e.message || '修改知识库失败')
  } finally {
    editing.value = false
  }
}

// 打开权限控制弹窗
const handleOpenPermissionDialog = async () => {
  permissionDialogVisible.value = true
  await loadAllUserPermissions()
}

// 加载所有用户权限列表
const loadAllUserPermissions = async () => {
  loadingPermissions.value = true
  try {
    const res = await getAllUserPermissions()
    allUserPermissions.value = res.data || []
  } catch (e) {
    ElMessage.error('获取用户权限列表失败')
    allUserPermissions.value = []
  } finally {
    loadingPermissions.value = false
  }
}

// 开始编辑权限
const handleEditPermission = (user, permissionType) => {
  editingPermission.value = {
    userId: user.userId,
    permissionType: permissionType
  }
  permissionForm.value = {
    userId: user.userId,
    userName: user.userName || user.nickName || `用户${user.userId}`,
    permissionType: permissionType,
    isOpen: permissionType === 1
        ? (user.isOpenAccountPerm === 1 ? 1 : 0)
        : (user.isOpenModulePerm === 1 ? 1 : 0)
  }
}

// 取消编辑
const handleCancelEdit = () => {
  editingPermission.value = null
  permissionForm.value = {
    userId: null,
    userName: '',
    permissionType: 1,
    isOpen: 0
  }
}

// 开始编辑空间限额
const handleEditQuota = (user) => {
  editingQuota.value = {
    userId: user.userId
  }
  quotaForm.value = {
    userId: user.userId,
    quota: user.kbSpaceQuota || 1024
  }
}

// 取消编辑空间限额
const handleCancelEditQuota = () => {
  editingQuota.value = null
  quotaForm.value = {
    userId: null,
    quota: 0
  }
}

// 提交空间限额修改
const handleSubmitQuota = async () => {
  if (!quotaForm.value.userId) {
    ElMessage.warning('请选择用户')
    return
  }
  if (quotaForm.value.quota < 0) {
    ElMessage.warning('空间限额不能为负数')
    return
  }

  updatingQuota.value = true
  try {
    await updateSpaceQuota(quotaForm.value.userId, quotaForm.value.quota)
    ElMessage.success('修改空间限额成功')
    // 重新加载权限列表
    await loadAllUserPermissions()
    // 取消编辑状态
    handleCancelEditQuota()
  } catch (e) {
    ElMessage.error(e.message || '修改空间限额失败')
  } finally {
    updatingQuota.value = false
  }
}

// 提交权限修改
const handleSubmitPermission = async () => {
  if (!permissionForm.value.userId) {
    ElMessage.warning('请选择用户')
    return
  }

  updatingPermission.value = true
  try {
    await updateAccountPermission(
        permissionForm.value.userId,
        permissionForm.value.permissionType,
        permissionForm.value.isOpen
    )
    ElMessage.success('修改权限成功')
    // 重新加载权限列表
    await loadAllUserPermissions()
    // 重新加载当前用户信息
    await loadCurrentUserInfo()
    // 取消编辑状态
    handleCancelEdit()
  } catch (e) {
    ElMessage.error(e.message || '修改权限失败')
  } finally {
    updatingPermission.value = false
  }
}

// 加载当前用户信息
const loadCurrentUserInfo = async () => {
  try {
    const res = await getUserExtendInfo(userStore.id)
    currentUserInfo.value = res.data
  } catch (e) {
    console.error('获取用户信息失败:', e)
  }
}

// 加载我的知识库
const loadMyKnowledge = async () => {
  loading.value = true
  try {
    const res = await getUserKnowledgeList()
    knowledgeList.value = res.data || []
    if (!selectedKb.value && knowledgeList.value.length > 0) {
      selectedKb.value = knowledgeList.value[0]
    }
  } catch (e) {
    ElMessage.error('获取知识库列表失败')
  } finally {
    loading.value = false
  }
}

// 加载收藏的知识库
const loadFavoriteKnowledge = async () => {
  loading.value = true
  try {
    const res = await getFavoriteKnowledgeList()
    knowledgeList.value = res.data || []
    if (!selectedKb.value && knowledgeList.value.length > 0) {
      selectedKb.value = knowledgeList.value[0]
    }
  } catch (e) {
    ElMessage.error('获取收藏知识库列表失败')
    knowledgeList.value = []
  } finally {
    loading.value = false
  }
}

// 切换列表类型
const switchListType = (type) => {
  listType.value = type
  selectedKb.value = null
  if (type === 'my') {
    loadMyKnowledge()
  } else {
    loadFavoriteKnowledge()
  }
}

const loadData = async () => {
  if (listType.value === 'my') {
    await loadMyKnowledge()
  } else {
    await loadFavoriteKnowledge()
  }
  // 加载统计信息
  await loadKnowledgeStats()
}

const getTemplateType = (row) => {
  return row.isPublicTemplate === 1 ? '公共模板' : '个人知识库'
}

const getTemplateTagType = (row) => {
  return row.isPublicTemplate === 1 ? 'success' : 'info'
}

const handleRowClick = (row) => {
  selectedKb.value = row
}

// 创建知识库
const handleCreateKnowledge = async () => {
  if (!createForm.value.kbName.trim()) {
    ElMessage.warning('请填写知识库名称')
    return
  }
  if (!createForm.value.kbContent.trim()) {
    ElMessage.warning('请填写知识库内容（JSON 格式）')
    return
  }
  creating.value = true
  try {
    const payload = {
      kbName: createForm.value.kbName.trim(),
      kbContent: createForm.value.kbContent,
      isPublicTemplate: createForm.value.isPublicTemplate ? 1 : 0
    }
    await createKnowledgeBase(payload)
    ElMessage.success('创建知识库成功')
    // 清理表单
    createForm.value.kbName = ''
    createForm.value.kbContent = ''
    createForm.value.isPublicTemplate = 0
    await loadData()
  } catch (e) {
    ElMessage.error('创建知识库失败')
  } finally {
    creating.value = false
  }
}

// 删除知识库（仅对个人知识库开放按钮，权限后端再兜底）
const handleDeleteKnowledge = (row) => {
  ElMessageBox.confirm(`确定删除知识库【${row.kbName}】吗？`, '提示', {
    type: 'warning'
  }).then(async () => {
    try {
      await deleteKnowledgeBase(row.kbId)
      ElMessage.success('删除成功')
      if (selectedKb.value && selectedKb.value.kbId === row.kbId) {
        selectedKb.value = null
      }
      await loadData()
    } catch (e) {
      ElMessage.error('删除失败')
    }
  }).catch(() => {})
}

// 加载知识库统计信息
const loadKnowledgeStats = async () => {
  try {
    const res = await getUserExtendInfo(userStore.id)
    const userInfo = res.data
    if (!userInfo) {
      return
    }
    
    // 获取空间额度
    kbStats.value.quota = userInfo.kbSpaceQuota || 1024
    
    // 计算已使用空间和已创建知识库数量
    const hasKbIds = userInfo.hasKnowledgeBase || ''
    if (hasKbIds) {
      const kbIds = hasKbIds.split(',').filter(id => id.trim()).map(id => parseInt(id.trim()))
      kbStats.value.count = kbIds.length
      
      if (kbIds.length > 0) {
        // 加载知识库详情以计算已使用空间
        const kbRes = await getKnowledgeBasesByIds(kbIds)
        const kbList = kbRes.data || []
        let usedSizeMB = 0
        kbList.forEach(kb => {
          if (kb && kb.kbContent) {
            const bytes = new TextEncoder().encode(kb.kbContent).length
            usedSizeMB += Math.ceil(bytes / (1024.0 * 1024.0))
          }
        })
        kbStats.value.used = usedSizeMB
      } else {
        kbStats.value.used = 0
      }
    } else {
      kbStats.value.count = 0
      kbStats.value.used = 0
    }
  } catch (e) {
    console.error('获取知识库统计信息失败:', e)
  }
}

// 知识库创建导引（仅显示说明，不自动创建模板）
const handleShowGuide = () => {
  guideDialogVisible.value = true
}

// 检查知识库是否已收藏
const isKnowledgeFavorite = (kbId) => {
  if (!currentUserInfo.value || !kbId) {
    return false
  }
  const likesIds = currentUserInfo.value.kbLikesIds || ''
  if (!likesIds) {
    return false
  }
  const likesList = likesIds.split(',').filter(id => id.trim())
  return likesList.includes(String(kbId))
}

// 收藏/取消收藏知识库
const handleToggleFavorite = async () => {
  if (!selectedKb.value) {
    ElMessage.warning('请先选择一个知识库')
    return
  }
  try {
    await toggleFavoriteKnowledge(selectedKb.value.kbId, userStore.id)
    // 重新加载用户信息以更新收藏状态
    await loadCurrentUserInfo()
    ElMessage.success('操作成功')
    // 如果当前在收藏列表，刷新列表
    if (listType.value === 'favorite') {
      await loadFavoriteKnowledge()
    }
  } catch (e) {
    ElMessage.error('操作失败')
  }
}

// 初始化WebSocket连接（用于接收二维码截图）
const initWebSocket = () => {
  try {
    const token = getToken()
    const wsUrl = buildWebSocketUrl({
      path: '/ws/client',
      token: token,
      clientType: 'web'
    })

    ws.value = new WebSocket(wsUrl)

    ws.value.onopen = () => {
      isWsConnected.value = true
      console.log('[知识库] WebSocket连接成功')
    }

    ws.value.onmessage = (event) => {
      try {
        const message = JSON.parse(event.data)
        // 处理二维码截图消息
        if (message.type === 'TASK_SCREENSHOT') {
          const screenshotUrl = message.payload?.screenshotUrl || message.payload?.qrCodeUrl
          if (screenshotUrl) {
            qrCodeUrl.value = screenshotUrl
            qrCodeDialogVisible.value = true
            qrCodeStatus.value = 'waiting'
            qrCodeMessage.value = message.payload?.message || '请使用微信扫码登录'
          }
        }
        // 处理任务日志
        else if (message.type === 'TASK_LOG') {
          const logMessage = message.payload?.message || ''
          if (logMessage.includes('登录成功') || logMessage.includes('登录完成')) {
            qrCodeStatus.value = 'success'
            qrCodeMessage.value = '登录成功！'
            setTimeout(() => {
              qrCodeDialogVisible.value = false
            }, 2000)
          } else if (logMessage.includes('超时') || logMessage.includes('失败')) {
            qrCodeStatus.value = 'timeout'
            qrCodeMessage.value = logMessage
          }
        }
        // 处理任务结果
        else if (message.type === 'TASK_RESULT') {
          const success = message.payload?.success
          if (success) {
            qrCodeStatus.value = 'success'
            qrCodeMessage.value = '上传任务完成！'
            setTimeout(() => {
              qrCodeDialogVisible.value = false
              ElMessage.success('知识库上传完成')
            }, 2000)
          } else {
            qrCodeStatus.value = 'timeout'
            const errorMessage = message.payload?.errorMessage || message.payload?.error || '上传失败'
            qrCodeMessage.value = errorMessage
            // 显示错误提示
            ElMessage.error(errorMessage)
            setTimeout(() => {
              qrCodeDialogVisible.value = false
            }, 3000)
          }
        }
      } catch (e) {
        console.error('[知识库] 解析WebSocket消息失败:', e)
      }
    }

    ws.value.onerror = (error) => {
      console.error('[知识库] WebSocket错误:', error)
      isWsConnected.value = false
    }

    ws.value.onclose = () => {
      isWsConnected.value = false
      console.log('[知识库] WebSocket连接已关闭')
    }
  } catch (error) {
    console.error('[知识库] 初始化WebSocket失败:', error)
  }
}

// 打开知识库上传弹窗
const openUploadKbDialog = () => {
  if (!selectedKb.value) {
    ElMessage.warning('请先选择一个知识库')
    return
  }
  uploadKbType.value = 1 // 默认选择上传到元器
  uploadKbAgentName.value = ''
  uploadKbRobotName.value = ''
  uploadKbTeamName.value = ''
  uploadKbDialogVisible.value = true
}

// 提交知识库上传
const handleUploadKnowledge = async () => {
  if (!selectedKb.value) {
    ElMessage.warning('请先选择一个知识库')
    return
  }

  // 验证输入
  if (uploadKbType.value === 1 && !uploadKbAgentName.value.trim()) {
    ElMessage.warning('请输入智能体名称')
    return
  }
  if (uploadKbType.value === 2 && !uploadKbRobotName.value.trim()) {
    ElMessage.warning('请输入机器人名称')
    return
  }
  if (uploadKbType.value === 3) {
    if (!uploadKbAgentName.value.trim()) {
      ElMessage.warning('请输入智能体名称')
      return
    }
    if (!uploadKbRobotName.value.trim()) {
      ElMessage.warning('请输入机器人名称')
      return
    }
  }
  // 如果上传到元器或同时上传到两者，需要团队名称（不填则默认"个人空间"）
  const finalTeamName = (uploadKbType.value === 1 || uploadKbType.value === 3) 
    ? (uploadKbTeamName.value.trim() || '个人空间') 
    : ''

  // 确保WebSocket已连接
  if (!isWsConnected.value) {
    initWebSocket()
    // 等待连接建立
    await new Promise(resolve => setTimeout(resolve, 1000))
  }

  try {
    await uploadKnowledgeBase(
        selectedKb.value.kbId,
        uploadKbType.value,
        uploadKbAgentName.value.trim(),
        uploadKbRobotName.value.trim(),
        finalTeamName
    )
    ElMessage.info('知识库上传任务已触发，请等待二维码出现后扫码登录')
    uploadKbDialogVisible.value = false
  } catch (e) {
    ElMessage.error('知识库上传失败')
  }
}

// 提交本地文档上传
const handleSubmitUpload = async () => {
  if (!uploadFile.value) {
    ElMessage.warning('请先选择文件')
    return
  }

  // 验证输入
  if (uploadType.value === 1 && !agentName.value.trim()) {
    ElMessage.warning('请输入智能体名称')
    return
  }
  if (uploadType.value === 2 && !robotName.value.trim()) {
    ElMessage.warning('请输入机器人名称')
    return
  }
  if (uploadType.value === 3) {
    if (!agentName.value.trim()) {
      ElMessage.warning('请输入智能体名称')
      return
    }
    if (!robotName.value.trim()) {
      ElMessage.warning('请输入机器人名称')
      return
    }
  }
  // 如果上传到元器或同时上传到两者，需要团队名称（不填则默认"个人空间"）
  const finalTeamName = (uploadType.value === 1 || uploadType.value === 3) 
    ? (teamName.value.trim() || '个人空间') 
    : ''

  // 确保WebSocket已连接
  if (!isWsConnected.value) {
    initWebSocket()
    // 等待连接建立
    await new Promise(resolve => setTimeout(resolve, 1000))
  }

  try {
    // 获取文件名（不含扩展名）作为知识库名称
    const fileName = uploadFile.value.name || 'document'
    const kbName = fileName.replace(/\.[^/.]+$/, '') // 移除文件扩展名

    await uploadLocalDocument(
        uploadFile.value,
        uploadType.value,
        agentName.value.trim(),
        robotName.value.trim(),
        kbName, // 传递知识库名称
        finalTeamName
    )
    ElMessage.info('本地文档上传任务已触发，请等待二维码出现后扫码登录')
    uploadDialogVisible.value = false
    uploadFile.value = null
    uploadFileList.value = []
    agentName.value = ''
    robotName.value = ''
    teamName.value = ''
  } catch (e) {
    ElMessage.error('本地文档上传失败')
  }
}


onMounted(() => {
  loadData()
  // 初始化WebSocket连接
  initWebSocket()
  // 加载当前用户信息
  loadCurrentUserInfo()
  // 加载知识库统计信息
  loadKnowledgeStats()
})

onBeforeUnmount(() => {
  // 关闭WebSocket连接
  if (ws.value) {
    ws.value.close()
  }
})
</script>

<template>
  <div class="knowledge-container">
    <el-row :gutter="20">
      <!-- 左侧：创建和知识库列表（仿 dailyassistant 风格） -->
      <el-col :span="10" :xs="24">
        <el-card class="box-card">
          <template #header>
            <div class="card-header">
              <div style="display: flex; align-items: center; gap: 8px;">
                <span class="card-title">
                  <el-icon><Collection /></el-icon>
                  知识库管理
                </span>
                <el-tooltip content="知识库创建导引" placement="top">
                  <el-icon 
                    style="cursor: pointer; color: #409EFF; font-size: 18px;"
                    @click="handleShowGuide"
                  >
                    <InfoFilled />
                  </el-icon>
                </el-tooltip>
                <el-button
                    v-if="canManagePermission()"
                    type="warning"
                    size="small"
                    @click="handleOpenPermissionDialog"
                    style="margin-left: auto;"
                >
                  <el-icon><Setting /></el-icon>
                  权限控制
                </el-button>
              </div>
            </div>
            <!-- 知识库统计信息 -->
            <div style="margin-top: 12px; padding: 12px; background-color: #f5f7fa; border-radius: 4px; display: flex; justify-content: space-between; align-items: center;">
              <div style="display: flex; gap: 20px; align-items: center;">
                <div>
                  <span style="color: #606266; font-size: 13px;">剩余额度：</span>
                  <span style="color: #409EFF; font-weight: 600;">{{ kbStats.used }} MB / {{ kbStats.quota }} MB</span>
                </div>
                <div>
                  <span style="color: #606266; font-size: 13px;">已创建知识库数量：</span>
                  <span style="color: #67C23A; font-weight: 600;">{{ kbStats.count }} 个</span>
                </div>
              </div>
            </div>
          </template>

          <!-- 创建知识库 -->
          <div class="create-section">
            <el-input
                v-model="createForm.kbName"
                placeholder="请输入知识库名称"
                clearable
                :disabled="creating"
                @keyup.enter="handleCreateKnowledge"
            >
              <template #append>
                <el-button
                    :loading="creating"
                    @click="handleCreateKnowledge"
                    :disabled="!createForm.kbName.trim()"
                    style="background-color: #409EFF; color: white; border-color: #409EFF;"
                >
                  <el-icon><Plus /></el-icon>
                  创建
                </el-button>
              </template>
            </el-input>

            <div class="model-selection">
              <span class="selection-label">是否设置为公共模板：</span>
              <el-switch
                  v-model="createForm.isPublicTemplate"
                  :active-value="1"
                  :inactive-value="0"
                  active-text="公共模板"
                  inactive-text="个人知识库"
              />
            </div>

            <el-input
                v-model="createForm.kbContent"
                type="textarea"
                :rows="5"
                placeholder="请输入知识库内容（JSON 格式文本），用于指导元器/企微机器人知识集构建"
            />
          </div>

          <!-- 知识库列表 -->
          <div class="knowledge-list">
            <div class="list-header">
              <el-radio-group v-model="listType" @change="switchListType" size="small">
                <el-radio-button label="my">我的知识库</el-radio-button>
                <el-radio-button label="favorite">收藏</el-radio-button>
              </el-radio-group>
              <el-button
                  type="text"
                  size="small"
                  @click="loadData"
                  :loading="loading"
              >
                <el-icon><Refresh /></el-icon>
                刷新
              </el-button>
            </div>

            <el-scrollbar height="420px">
              <div
                  v-for="item in knowledgeList"
                  :key="item.kbId"
                  class="knowledge-item"
                  :class="{ active: selectedKb && selectedKb.kbId === item.kbId }"
                  @click="handleRowClick(item)"
              >
                <div class="knowledge-header">
                  <span class="knowledge-title">{{ item.kbName }}</span>
                  <el-tag :type="getTemplateTagType(item)" size="small">
                    {{ getTemplateType(item) }}
                  </el-tag>
                </div>
                <div class="knowledge-meta">
                  <span class="id-text">ID: {{ item.kbId }}</span>
                  <div class="meta-actions">
                    <!-- 修改按钮：根据权限显示 -->
                    <el-button
                        v-if="canEditKnowledge(item)"
                        type="primary"
                        size="small"
                        text
                        @click.stop="handleEditKnowledge(item)"
                    >
                      <el-icon><Edit /></el-icon>
                      修改
                    </el-button>
                    <!-- 删除按钮：根据权限显示 -->
                    <el-button
                        v-if="canDeleteKnowledge(item)"
                        type="danger"
                        size="small"
                        text
                        @click.stop="handleDeleteKnowledge(item)"
                    >
                      <el-icon><Delete /></el-icon>
                      删除
                    </el-button>
                  </div>
                </div>
              </div>

              <el-empty
                  v-if="!loading && (!knowledgeList || knowledgeList.length === 0)"
                  description="暂无知识库，先创建一个吧"
                  :image-size="120"
                  class="empty-block"
              />
            </el-scrollbar>
          </div>
        </el-card>
      </el-col>

      <!-- 右侧：知识库内容与上传操作 -->
      <el-col :span="14" :xs="24">
        <el-card class="box-card content-card">
          <template #header>
            <div class="card-header">
              <span class="card-title">
                <el-icon><Reading /></el-icon>
                知识库内容与同步
              </span>
            </div>
          </template>

          <div v-if="selectedKb" class="content-section">
            <div class="content-header">
              <h3>{{ selectedKb.kbName }}</h3>
              <div class="action-buttons">
                <el-button
                    :type="isKnowledgeFavorite(selectedKb.kbId) ? 'warning' : 'primary'"
                    size="small"
                    @click="handleToggleFavorite"
                >
                  <el-icon>
                    <StarFilled v-if="isKnowledgeFavorite(selectedKb.kbId)" />
                    <Star v-else />
                  </el-icon>
                  {{ isKnowledgeFavorite(selectedKb.kbId) ? '取消收藏' : '收藏' }}
                </el-button>
                <el-button
                    type="success"
                    size="small"
                    @click="openUploadKbDialog"
                >
                  <el-icon><Upload /></el-icon>
                  上传知识库
                </el-button>
                <el-button
                    type="primary"
                    plain
                    size="small"
                    @click="uploadDialogVisible = true"
                >
                  <el-icon><UploadFilled /></el-icon>
                  本地文档上传
                </el-button>
              </div>
            </div>

            <el-scrollbar height="520px">
              <el-input
                  v-model="selectedKb.kbContent"
                  type="textarea"
                  :rows="20"
                  readonly
                  class="content-textarea"
              />
            </el-scrollbar>
          </div>

          <el-empty
              v-else
              description="请选择左侧的知识库以查看内容"
              :image-size="160"
          />
        </el-card>
      </el-col>
    </el-row>

    <!-- 知识库创建导引弹窗（说明性内容） -->
    <el-dialog
        v-model="guideDialogVisible"
        title="知识库创建导引"
        width="700px"
    >
      <div class="guide-content">
        <el-alert
            title="知识库建设指南"
            type="info"
            :closable="false"
            style="margin-bottom: 20px;"
        >
          <template #default>
            <p style="margin: 0; line-height: 1.6;">
              本导引基于项目设计文档，帮助企业了解如何创建和管理知识库。
            </p>
          </template>
        </el-alert>

        <h4 style="margin-top: 0; color: #409EFF;">📋 知识库创建步骤：</h4>
        <ol class="guide-list">
          <li>
            <strong>确定知识库名称</strong>：知识库名称在同一空间下必须唯一，建议使用描述性名称，如"招聘简历匹配知识库"。
          </li>
          <li>
            <strong>选择知识库类型</strong>：
            <ul style="margin-top: 8px; padding-left: 20px;">
              <li><strong>个人知识库</strong>：仅创建者可见和操作，适合企业内部使用</li>
              <li><strong>公共模板</strong>：所有用户可查看，适合作为通用模板供其他企业复用</li>
            </ul>
          </li>
          <li>
            <strong>填写知识库内容</strong>：内容为JSON格式数据，用于指导元器/企微机器人知识集构建。
            <br />
            <strong>推荐内容结构</strong>（招聘场景示例）：
            <ul style="margin-top: 8px; padding-left: 20px;">
              <li>岗位说明书：明确岗位职责、任职要求等</li>
              <li>简历筛选清单：列出筛选标准和检查项</li>
              <li>人岗匹配评估表：评估维度与评分标准</li>
              <li>筛选结果记录表：记录筛选过程和结果</li>
            </ul>
          </li>
          <li>
            <strong>创建知识库空间</strong>：首次使用需要先创建知识库空间，系统会分配默认额度（3000字符）。
          </li>
          <li>
            <strong>添加到空间</strong>：创建的知识库或公共模板可以添加到自己的知识库空间中。
          </li>
          <li>
            <strong>同步到智能体</strong>：将知识库上传到智能体元器或企业微信机器人，实现知识集的自动配置。
          </li>
        </ol>

        <h4 style="margin-top: 24px; color: #67C23A;">💡 使用建议：</h4>
        <ul class="guide-list">
          <li>知识库内容建议使用结构化的JSON格式，便于后续处理和扩展</li>
          <li>公共模板修改后，使用该模板的企业会自动同步更新</li>
          <li>知识库空间以字符长度计算限额，注意控制内容大小</li>
          <li>上传到元器/企微机器人时，可能需要扫码登录，请准备好微信</li>
        </ul>
      </div>
      <template #footer>
        <span class="dialog-footer">
          <el-button type="primary" @click="guideDialogVisible = false">我知道了</el-button>
        </span>
      </template>
    </el-dialog>

    <!-- 二维码登录弹窗 -->
    <el-dialog
        v-model="qrCodeDialogVisible"
        title="扫码登录"
        width="500px"
        :close-on-click-modal="false"
        :close-on-press-escape="false"
    >
      <div class="qr-code-content">
        <div v-if="qrCodeStatus === 'waiting'" class="qr-code-wrapper">
          <img
              v-if="qrCodeUrl"
              :src="qrCodeUrl"
              alt="登录二维码"
              class="qr-code-image"
          />
          <div v-else class="qr-code-loading">
            <el-icon class="is-loading"><Loading /></el-icon>
            <p>正在生成二维码...</p>
          </div>
          <p class="qr-code-tip">{{ qrCodeMessage }}</p>
        </div>
        <div v-else-if="qrCodeStatus === 'success'" class="qr-code-success">
          <el-icon style="font-size: 48px; color: #67C23A;"><CircleCheck /></el-icon>
          <p style="margin-top: 16px; color: #67C23A; font-size: 16px;">{{ qrCodeMessage }}</p>
        </div>
        <div v-else class="qr-code-error">
          <el-icon style="font-size: 48px; color: #F56C6C;"><CircleClose /></el-icon>
          <p style="margin-top: 16px; color: #F56C6C;">{{ qrCodeMessage }}</p>
        </div>
      </div>
      <template #footer>
        <span class="dialog-footer">
          <el-button
              v-if="qrCodeStatus === 'waiting'"
              @click="qrCodeDialogVisible = false"
          >
            取消
          </el-button>
          <el-button
              v-else
              type="primary"
              @click="qrCodeDialogVisible = false"
          >
            确定
          </el-button>
        </span>
      </template>
    </el-dialog>

    <!-- 知识库上传弹窗 -->
    <el-dialog
        v-model="uploadKbDialogVisible"
        title="上传知识库"
        width="520px"
    >
      <el-form label-width="120px">
        <el-form-item label="上传目标">
          <el-radio-group v-model="uploadKbType">
            <el-radio v-for="opt in uploadTypeOptions" :key="opt.value" :label="opt.value">
              {{ opt.label }}
            </el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item
            label="智能体名称"
            v-if="uploadKbType === 1 || uploadKbType === 3"
        >
          <el-input
              v-model="uploadKbAgentName"
              placeholder="请输入智能体名称"
              clearable
          />
        </el-form-item>
        <el-form-item
            label="团队名称"
            v-if="uploadKbType === 1 || uploadKbType === 3"
        >
          <el-input
              v-model="uploadKbTeamName"
              placeholder="请输入团队名称（不填则默认'个人空间'）"
              clearable
          />
        </el-form-item>
        <el-form-item
            label="机器人名称"
            v-if="uploadKbType === 2 || uploadKbType === 3"
        >
          <el-input
              v-model="uploadKbRobotName"
              placeholder="请输入机器人名称"
              clearable
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="uploadKbDialogVisible = false">取 消</el-button>
          <el-button type="primary" @click="handleUploadKnowledge">确 定</el-button>
        </span>
      </template>
    </el-dialog>

    <!-- 本地文档上传弹窗 -->
    <el-dialog
        v-model="uploadDialogVisible"
        title="本地文档上传"
        width="520px"
    >
      <el-form label-width="120px">
        <el-form-item label="上传目标">
          <el-radio-group v-model="uploadType">
            <el-radio v-for="opt in uploadTypeOptions" :key="opt.value" :label="opt.value">
              {{ opt.label }}
            </el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item
            label="智能体名称"
            v-if="uploadType === 1 || uploadType === 3"
        >
          <el-input
              v-model="agentName"
              placeholder="请输入智能体名称"
              clearable
          />
        </el-form-item>
        <el-form-item
            label="团队名称"
            v-if="uploadType === 1 || uploadType === 3"
        >
          <el-input
              v-model="teamName"
              placeholder="请输入团队名称（不填则默认'个人空间'）"
              clearable
          />
        </el-form-item>
        <el-form-item
            label="机器人名称"
            v-if="uploadType === 2 || uploadType === 3"
        >
          <el-input
              v-model="robotName"
              placeholder="请输入机器人名称"
              clearable
          />
        </el-form-item>
        <el-form-item label="选择文件">
          <el-upload
              drag
              :limit="1"
              :auto-upload="false"
              :on-change="handleFileChange"
              :on-remove="handleFileRemove"
              :file-list="uploadFileList"
              :accept="allowedFileTypes.map(t => '.' + t).join(',')"
              ref="uploadRef"
          >
            <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
            <div class="el-upload__text">将文件拖到此处，或<em>点击上传</em></div>
          </el-upload>
          <div style="margin-top: 10px; color: #909399; font-size: 12px;">
            <div>支持格式：{{ allowedFileTypes.join('、') }}</div>
            <div>文件大小：不超过20MB</div>
            <div v-if="uploadFile" style="margin-top: 5px;">
              提示：知识库名称将使用文件名（不含扩展名）：{{ uploadFile.name ? uploadFile.name.replace(/\.[^/.]+$/, '') : '' }}
            </div>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="uploadDialogVisible = false">取 消</el-button>
          <el-button type="primary" @click="handleSubmitUpload">确 定</el-button>
        </span>
      </template>
    </el-dialog>

    <!-- 修改知识库弹窗 -->
    <el-dialog
        v-model="editDialogVisible"
        title="修改知识库"
        width="700px"
    >
      <el-form label-width="120px">
        <el-form-item label="知识库名称">
          <el-input
              v-model="editForm.kbName"
              placeholder="请输入知识库名称"
              clearable
          />
        </el-form-item>
        <el-form-item label="是否公共模板">
          <el-switch
              v-model="editForm.isPublicTemplate"
              :active-value="1"
              :inactive-value="0"
              active-text="公共模板"
              inactive-text="个人知识库"
              :disabled="true"
          />
          <span style="margin-left: 10px; color: #909399; font-size: 12px;">
            （知识库类型不可修改）
          </span>
        </el-form-item>
        <el-form-item label="知识库内容">
          <el-input
              v-model="editForm.kbContent"
              type="textarea"
              :rows="15"
              placeholder="请输入知识库内容（JSON 格式文本）"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="editDialogVisible = false">取 消</el-button>
          <el-button type="primary" @click="handleSubmitEdit" :loading="editing">确 定</el-button>
        </span>
      </template>
    </el-dialog>

    <!-- 权限控制弹窗 -->
    <el-dialog
        v-model="permissionDialogVisible"
        title="权限控制"
        width="900px"
    >
      <div v-loading="loadingPermissions">
        <el-table :data="allUserPermissions" border style="width: 100%">
          <el-table-column prop="userId" label="用户ID" width="100" />
          <el-table-column prop="userName" label="用户名" width="150" />
          <el-table-column prop="nickName" label="昵称" width="150" />
          <el-table-column label="超级账户" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="row.isSuper === 1 ? 'success' : 'info'">
                {{ row.isSuper === 1 ? '是' : '否' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="账户权限管理权限" width="180" align="center">
            <template #default="{ row }">
              <div v-if="editingPermission && editingPermission.userId === row.userId && editingPermission.permissionType === 1">
                <el-switch
                    v-model="permissionForm.isOpen"
                    :active-value="1"
                    :inactive-value="0"
                    @change="handleSubmitPermission"
                />
              </div>
              <div v-else>
                <el-tag :type="row.isOpenAccountPerm === 1 ? 'success' : 'info'">
                  {{ row.isOpenAccountPerm === 1 ? '开放' : '关闭' }}
                </el-tag>
                <el-button
                    type="primary"
                    size="small"
                    text
                    style="margin-left: 8px;"
                    @click="handleEditPermission(row, 1)"
                >
                  修改
                </el-button>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="模块功能操作权限" width="180" align="center">
            <template #default="{ row }">
              <div v-if="editingPermission && editingPermission.userId === row.userId && editingPermission.permissionType === 2">
                <el-switch
                    v-model="permissionForm.isOpen"
                    :active-value="1"
                    :inactive-value="0"
                    @change="handleSubmitPermission"
                />
              </div>
              <div v-else>
                <el-tag :type="row.isOpenModulePerm === 1 ? 'success' : 'info'">
                  {{ row.isOpenModulePerm === 1 ? '开放' : '关闭' }}
                </el-tag>
                <el-button
                    type="primary"
                    size="small"
                    text
                    style="margin-left: 8px;"
                    @click="handleEditPermission(row, 2)"
                >
                  修改
                </el-button>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="空间限额" width="180" align="center" v-if="canManageSpaceQuota()">
            <template #default="{ row }">
              <div v-if="editingQuota && editingQuota.userId === row.userId">
                <el-input-number
                    v-model="quotaForm.quota"
                    :min="0"
                    :precision="0"
                    size="small"
                    style="width: 120px;"
                />
                <span style="margin-left: 4px; font-size: 12px;">MB</span>
                <el-button
                    type="success"
                    size="small"
                    text
                    style="margin-left: 8px;"
                    @click="handleSubmitQuota"
                    :loading="updatingQuota"
                >
                  保存
                </el-button>
                <el-button
                    type="info"
                    size="small"
                    text
                    @click="handleCancelEditQuota"
                >
                  取消
                </el-button>
              </div>
              <div v-else>
                <span>{{ row.kbSpaceQuota || 1024 }} MB</span>
                <el-button
                    type="primary"
                    size="small"
                    text
                    style="margin-left: 8px;"
                    @click="handleEditQuota(row)"
                >
                  修改
                </el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>

        <!-- 编辑表单（隐藏，用于提交） -->
        <div v-if="editingPermission" style="margin-top: 20px; padding: 15px; background-color: #f5f7fa; border-radius: 4px;">
          <el-form :model="permissionForm" label-width="150px" inline>
            <el-form-item label="用户">
              <span>{{ permissionForm.userName }} (ID: {{ permissionForm.userId }})</span>
            </el-form-item>
            <el-form-item label="权限类型">
              <span>{{ permissionForm.permissionType === 1 ? '账户权限管理权限' : '模块功能操作权限' }}</span>
            </el-form-item>
            <el-form-item label="权限状态">
              <el-switch
                  v-model="permissionForm.isOpen"
                  :active-value="1"
                  :inactive-value="0"
                  active-text="开放"
                  inactive-text="关闭"
              />
            </el-form-item>
            <el-form-item>
              <el-button @click="handleCancelEdit">取消</el-button>
              <el-button type="primary" @click="handleSubmitPermission" :loading="updatingPermission">保存</el-button>
            </el-form-item>
          </el-form>
        </div>
      </div>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="permissionDialogVisible = false">关 闭</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.knowledge-container {
  padding: 20px;
}

.box-card {
  width: 100%;
}

.content-card {
  height: 100%;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-title {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 16px;
  font-weight: 600;
}

.create-section {
  margin-bottom: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.model-selection {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
}

.selection-label {
  color: #606266;
}

.knowledge-list {
  margin-top: 12px;
}

.list-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.knowledge-item {
  padding: 8px 10px;
  border-radius: 6px;
  border: 1px solid transparent;
  margin-bottom: 8px;
  cursor: pointer;
  transition: all 0.2s;
}

.knowledge-item:hover {
  background-color: #f5f7fa;
}

.knowledge-item.active {
  border-color: #409eff;
  background-color: #ecf5ff;
}

.knowledge-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.knowledge-title {
  font-size: 14px;
  font-weight: 500;
}

.knowledge-meta {
  margin-top: 4px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 12px;
  color: #909399;
}

.id-text {
  font-family: SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New', monospace;
}

.meta-actions {
  display: flex;
  gap: 4px;
}

.empty-block {
  margin-top: 20px;
}

.content-section {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.content-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.content-header h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
}

.action-buttons {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.content-textarea :deep(textarea) {
  font-family: SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New', monospace;
}

.guide-list {
  margin: 10px 0 0;
  padding-left: 20px;
  font-size: 13px;
  line-height: 1.8;
}

.guide-content {
  max-height: 600px;
  overflow-y: auto;
}

.guide-content h4 {
  font-size: 15px;
  margin: 16px 0 12px;
}

.space-info-content {
  min-height: 200px;
}

.qr-code-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 30px;
  min-height: 500px;
}

.qr-code-wrapper {
  text-align: center;
}

.qr-code-image {
  width: 400px;
  height: 400px;
  border: 1px solid #dcdfe6;
  border-radius: 8px;
  padding: 15px;
  background: #fff;
  object-fit: contain;
}

.qr-code-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 250px;
  color: #909399;
}

.qr-code-tip {
  margin-top: 16px;
  color: #606266;
  font-size: 14px;
}

.qr-code-success,
.qr-code-error {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px;
}
</style>