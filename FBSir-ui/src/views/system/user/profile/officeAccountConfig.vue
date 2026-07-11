<template>
  <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
    <el-alert
      title="配置说明"
      type="warning"
      :closable="false"
      style="margin-bottom: 20px"
    >
      <template #default>
        <p><strong>重要：保存配置前必须完成以下步骤</strong></p>
        <p style="margin-left: 20px;">
          1️⃣ 登录微信公众平台获取开发者ID(AppID)和开发者密钥(AppSecret)
        </p>
        <p style="margin-left: 20px;">
          2️⃣ <strong style="color: #F56C6C;">【必须】先将服务器IP添加到公众号IP白名单中</strong>
          <el-button 
            type="text" 
            size="small" 
            :loading="loadingIp"
            @click="handleGetServerIp"
            style="margin-left: 5px;"
          >
            <el-icon><View /></el-icon>
            查看服务器IP
          </el-button>
        </p>
        <p v-if="serverIp" style="color: #E6A23C; font-weight: bold; margin-left: 40px;">
          📍 服务器IP：{{ serverIp }}
          <el-button 
            type="text" 
            size="small" 
            @click="handleCopyIp"
            style="margin-left: 5px;"
          >
            <el-icon><DocumentCopy /></el-icon>
            复制
          </el-button>
        </p>
        <p style="margin-left: 40px; color: #909399; font-size: 13px;">
          ⚠️ 说明：保存配置时需要验证公众号并上传封面图到素材库，此过程需要获取access_token，<br/>
          &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;微信会验证请求来源IP是否在白名单中，否则会验证失败。
        </p>
        <p style="margin-left: 20px;">
          3️⃣ 上传素材封面图（可选，用于文章默认封面）
        </p>
        <p style="margin-left: 20px;">
          4️⃣ 点击"保存配置"，系统将自动验证配置并上传封面图到公众号素材库
        </p>
        <p style="margin-left: 20px; color: #67C23A;">
          ✅ 配置成功后，可在日更助手中直接投递文章到公众号草稿箱
        </p>
        <p style="margin-left: 20px; color: #F56C6C; font-size: 13px;">
          🔒 安全提示：配置保存后，开发者ID和密钥将加密存储，不可查看，仅可修改更新
        </p>
      </template>
    </el-alert>

    <el-form-item label="开发者ID" prop="appId">
      <el-input
        v-model="form.appId"
        :placeholder="hasConfig ? '已配置（不可查看，可修改更新）' : '请输入公众号开发者ID(AppID)'"
        maxlength="50"
        show-word-limit
      >
        <template #suffix v-if="hasConfig">
          <el-tag type="success" size="small">已配置</el-tag>
        </template>
      </el-input>
      <div v-if="hasConfig" style="color: #67C23A; font-size: 12px; margin-top: 4px;">
        ✅ 开发者ID已配置并加密存储。留空=保持不变，填写=更新配置（需同时填写ID和密钥）
      </div>
    </el-form-item>

    <el-form-item label="开发者密钥" prop="appSecret">
      <el-input
        v-model="form.appSecret"
        type="password"
        :placeholder="hasConfig ? '已配置（不可查看，可修改更新）' : '请输入公众号开发者密钥(AppSecret)'"
        maxlength="50"
        show-word-limit
        show-password
      >
        <template #suffix v-if="hasConfig && !form.appSecret">
          <el-tag type="success" size="small">已配置</el-tag>
        </template>
      </el-input>
      <div v-if="hasConfig" style="color: #67C23A; font-size: 12px; margin-top: 4px;">
        ✅ 开发者密钥已配置并加密存储。留空=保持不变，填写=更新配置（需同时填写ID和密钥）
      </div>
    </el-form-item>

    <el-form-item label="作者名称" prop="authorName" :required="true">
      <el-input
        v-model="form.authorName"
        placeholder="请输入作者名称（必填，发布文章时显示）"
        maxlength="100"
        show-word-limit
      />
      <div style="color: #F56C6C; font-size: 12px; margin-top: 4px;">
        ⚠️ <strong>必填</strong>：作者名称将显示在发布的文章中
      </div>
    </el-form-item>

    <el-form-item label="素材封面图" prop="picUrl" :required="true">
      <image-upload 
        v-model="form.picUrl" 
        :limit="1"
        action="/common/upload/officialaccount"
      />
      <div style="color: #F56C6C; font-size: 12px; margin-top: 4px;">
        ⚠️ <strong>必填</strong>：封面图用于文章封面，保存时将上传到微信素材库（符合微信API要求）
      </div>
    </el-form-item>

    <!-- 用户不能自己禁用配置，只有管理员可以禁用 -->
    <!-- 如果被管理员禁用，会在页面加载时提示用户 -->

    <el-form-item>
      <el-button type="primary" @click="submitForm" :loading="loading">
        保存配置
      </el-button>
      <el-button @click="resetForm">重置</el-button>
    </el-form-item>
  </el-form>
</template>

<script setup name="OfficeAccountConfig">
import { getMyOfficeAccount, addOfficeAccount, getServerIp } from "@/api/business/content/dailyassistant/officeAccount"
import ImageUpload from "@/components/ImageUpload"
import { View, DocumentCopy } from '@element-plus/icons-vue'

const { proxy } = getCurrentInstance()

const formRef = ref()
const loading = ref(false)
const loadingIp = ref(false)
const serverIp = ref("")
const originalConfigId = ref(null)  // 用于判断是否已配置
const form = ref({
  appId: "",
  appSecret: "",
  authorName: "",
  picUrl: ""
  // 注意：不包含isActive，用户不能自己禁用配置
})

// 是否已配置（用于UI显示）
const hasConfig = computed(() => {
  return originalConfigId.value !== null
})

const rules = computed(() => {
  // 如果已配置，修改和更新时不强制要求必填（允许只修改部分字段）
  if (hasConfig.value) {
    return {
      appId: [
        { 
          validator: (rule, value, callback) => {
            // 已配置时，如果留空表示不修改，如果填写则验证长度
            if (value && value.trim().length === 0) {
              callback()  // 允许为空
            } else {
              callback()
            }
          }, 
          trigger: "blur" 
        }
      ],
      appSecret: [
        { 
          validator: (rule, value, callback) => {
            // 已配置时，如果留空表示不修改，如果填写则验证长度
            callback()  // 允许为空
          }, 
          trigger: "blur" 
        }
      ],
      authorName: [
        { required: true, message: "请输入作者名称", trigger: "blur" }
      ],
      picUrl: [
        { 
          validator: (rule, value, callback) => {
            // 如果要重新配置密钥，封面图也必填
            const hasAppId = form.value.appId && form.value.appId.trim()
            const hasAppSecret = form.value.appSecret && form.value.appSecret.trim()
            
            if (hasAppId && hasAppSecret && (!value || !value.trim())) {
              callback(new Error("重新配置密钥时，素材封面图为必填项"))
            } else {
              callback()
            }
          }, 
          trigger: "change" 
        }
      ]
    }
  } else {
    // 首次配置时，必须填写
    return {
      appId: [
        { required: true, message: "请输入公众号开发者ID", trigger: "blur" }
      ],
      appSecret: [
        { required: true, message: "请输入公众号开发者密钥", trigger: "blur" }
      ],
      authorName: [
        { required: true, message: "请输入作者名称", trigger: "blur" }
      ],
      picUrl: [
        { required: true, message: "请上传素材封面图（必填）", trigger: "change" }
      ]
    }
  }
})

// 获取配置
function getConfig() {
  loading.value = true
  getMyOfficeAccount().then(response => {
    if (response.data) {
      // 保存配置ID，用于判断是否已配置
      originalConfigId.value = response.data.id || null
      
      // 检查是否被管理员禁用
      const isActive = response.data.isActive !== undefined ? response.data.isActive : 1
      if (isActive === 0) {
        proxy.$modal.alertWarning(
          "您的公众号配置已被管理员禁用，当前无法使用投递功能。<br/>" +
          "如需继续使用，请联系管理员处理。",
          "配置已禁用"
        )
      }
      
      // 后端会将 appId 和 appSecret 设置为 null（安全考虑）
      // 前端显示为空，表示"已配置但不可查看"
      form.value = {
        id: response.data.id,
        authorName: response.data.authorName || "",
        picUrl: response.data.picUrl || "",
        mediaId: response.data.mediaId || "",
        isActive: isActive,  // 保存状态用于判断（但不允许用户修改）
        appId: "",  // 后端返回null，前端显示为空
        appSecret: ""  // 后端返回null，前端显示为空
      }
    } else {
      // 没有配置
      originalConfigId.value = null
    }
  }).finally(() => {
    loading.value = false
  })
}

// 提交表单
function submitForm() {
  formRef.value.validate(valid => {
    if (valid) {
      // 如果是首次配置，必须填写 appId、appSecret 和 picUrl
      if (!hasConfig.value) {
        if (!form.value.appId || !form.value.appSecret) {
          proxy.$modal.msgWarning("首次配置时，开发者ID和密钥为必填项")
          return
        }
        if (!form.value.picUrl) {
          proxy.$modal.msgWarning("首次配置时，素材封面图为必填项（微信API要求）")
          return
        }
      }
      
      // 如果已配置，用户可以：
      // 1. 不修改appId和appSecret（都留空），只修改其他字段如公众号名称、封面图等
      // 2. 同时重新输入appId和appSecret（安全起见，必须两个都填）
      if (hasConfig.value) {
        const hasAppId = form.value.appId && form.value.appId.trim()
        const hasAppSecret = form.value.appSecret && form.value.appSecret.trim()
        
        // 如果填写了其中一个，必须两个都填写
        if ((hasAppId && !hasAppSecret) || (!hasAppId && hasAppSecret)) {
          proxy.$modal.msgWarning("修改密钥配置时，请同时重新输入开发者ID和密钥（安全要求）")
          return
        }
        
        // 如果重新配置密钥，也必须提供封面图
        if (hasAppId && hasAppSecret && !form.value.picUrl) {
          proxy.$modal.msgWarning("重新配置密钥时，素材封面图为必填项（微信API要求）")
          return
        }
      }
      
      loading.value = true
      
      // 构建提交数据（不包含isActive，用户不能修改启用状态）
      const submitData = {
        id: form.value.id,
        appId: form.value.appId || undefined,
        appSecret: form.value.appSecret || undefined,
        authorName: form.value.authorName,
        picUrl: form.value.picUrl
        // 注意：不传递isActive，后端保持原状态或默认启用
      }
      
      addOfficeAccount(submitData).then(response => {
        if (hasConfig.value) {
          const hasAppId = form.value.appId && form.value.appId.trim()
          const hasAppSecret = form.value.appSecret && form.value.appSecret.trim()
          
          if (hasAppId && hasAppSecret) {
            proxy.$modal.msgSuccess("配置更新成功！已重新验证公众号，新密钥已加密存储")
          } else {
            proxy.$modal.msgSuccess("配置更新成功！")
          }
        } else {
          proxy.$modal.msgSuccess("配置保存成功！已验证公众号并上传封面图到素材库，配置已加密存储")
        }
        getConfig()
      }).catch(error => {
        // 显示后端返回的详细错误信息
        const errorMsg = error.response?.data?.msg || error.message || "保存失败"
        proxy.$modal.msgError(errorMsg)
      }).finally(() => {
        loading.value = false
      })
    }
  })
}

// 重置表单
function resetForm() {
  formRef.value.resetFields()
  getConfig()
}

// 获取服务器IP
function handleGetServerIp() {
  loadingIp.value = true
  getServerIp().then(response => {
    if (response.code === 200) {
      serverIp.value = response.ip
      proxy.$modal.msgSuccess("获取成功：" + response.tip)
    } else {
      proxy.$modal.msgError(response.msg || "获取服务器IP失败")
    }
  }).catch(error => {
    proxy.$modal.msgError("获取服务器IP失败")
  }).finally(() => {
    loadingIp.value = false
  })
}

// 复制IP到剪贴板
function handleCopyIp() {
  if (!serverIp.value) {
    proxy.$modal.msgWarning("请先获取服务器IP")
    return
  }
  
  navigator.clipboard.writeText(serverIp.value).then(() => {
    proxy.$modal.msgSuccess("IP地址已复制到剪贴板")
  }).catch(() => {
    proxy.$modal.msgError("复制失败，请手动复制")
  })
}

onMounted(() => {
  getConfig()
})
</script>

<style scoped lang="scss">
</style>
