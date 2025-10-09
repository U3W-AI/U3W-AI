<template>
  <div>
    <!-- 头像显示区域 -->
    <div class="user-info-head" @click="editCropper">
      <img :src="options.img" title="点击上传头像" class="img-circle img-lg" />
    </div>

    <!-- 裁剪弹窗 -->
    <el-dialog 
      v-model="open" 
      title="修改头像" 
      width="800px" 
      append-to-body 
      @opened="modalOpened"
      @close="closeDialog"
    >
      <el-row>
        <!-- 裁剪区域 -->
        <el-col :xs="24" :md="12" :style="{ height: '350px' }">
          <vue-cropper
            ref="cropperRef"
            :img="options.img"
            :info="true"
            :autoCrop="options.autoCrop"
            :autoCropWidth="options.autoCropWidth"
            :autoCropHeight="options.autoCropHeight"
            :fixedBox="options.fixedBox"
            :outputType="options.outputType"
            :fixed="options.fixed"
            :canMove="options.canMove"
            :centerBox="options.centerBox"
            @realTime="realTime"
            v-if="visible"
          />
        </el-col>

        <!-- 预览区域 -->
        <el-col :xs="24" :md="12" :style="{ height: '350px' }">
          <div class="avatar-upload-preview">
            <img :src="previews.url" :style="previews.img" />
          </div>
        </el-col>
      </el-row>

      <br />

      <!-- 操作按钮区域 -->
      <el-row>
        <!-- 选择图片 -->
        <el-col :lg="2" :sm="3" :xs="3">
          <el-upload 
            action="#" 
            :http-request="requestUpload" 
            :show-file-list="false" 
            :before-upload="beforeUpload"
          >
            <el-button size="small">
              选择
              <el-icon class="el-icon--right"><Upload /></el-icon>
            </el-button>
          </el-upload>
        </el-col>

        <!-- 缩放控制 -->
        <el-col :lg="{ span: 1, offset: 2 }" :sm="2" :xs="2">
          <el-button size="small" @click="changeScale(1)">
            <el-icon><Plus /></el-icon>
          </el-button>
        </el-col>

        <el-col :lg="{ span: 1, offset: 1 }" :sm="2" :xs="2">
          <el-button size="small" @click="changeScale(-1)">
            <el-icon><Minus /></el-icon>
          </el-button>
        </el-col>

        <!-- 旋转控制 -->
        <el-col :lg="{ span: 1, offset: 1 }" :sm="2" :xs="2">
          <el-button size="small" @click="rotateLeft">
            <el-icon><RefreshLeft /></el-icon>
          </el-button>
        </el-col>

        <el-col :lg="{ span: 1, offset: 1 }" :sm="2" :xs="2">
          <el-button size="small" @click="rotateRight">
            <el-icon><RefreshRight /></el-icon>
          </el-button>
        </el-col>

        <!-- 提交按钮 -->
        <el-col :lg="{ span: 2, offset: 6 }" :sm="2" :xs="2">
          <el-button type="primary" size="small" @click="uploadImg" :loading="uploadLoading">
            提 交
          </el-button>
        </el-col>
      </el-row>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onBeforeUnmount, getCurrentInstance } from 'vue'
import { ElMessage, ElLoading } from 'element-plus'
import { Upload, Plus, Minus, RefreshLeft, RefreshRight } from '@element-plus/icons-vue'
import 'vue-cropper/dist/index.css'
import { VueCropper } from 'vue-cropper'

// 假设的store导入，请根据您的实际项目调整
// import { useUserStore } from '@/stores/user'
// const userStore = useUserStore()

// 响应式数据
const open = ref(false)
const visible = ref(false)
const uploadLoading = ref(false)
const cropperRef = ref(null)
const resizeHandler = ref(null)

// 裁剪配置选项[1,3](@ref)
const options = reactive({
  img: '', // 初始图片路径，可以从store获取
  autoCrop: true,
  autoCropWidth: 200,
  autoCropHeight: 200,
  fixedBox: true,
  outputType: 'png',
  fixed: true,
  fixedNumber: [1, 1],
  canMove: false,
  centerBox: true
})

const previews = ref({})

// 初始化用户头像
onMounted(() => {
  // 从store获取用户头像，这里用默认头像替代
  options.img = 'https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png'
})

// 编辑头像 - 打开弹窗
const editCropper = () => {
  open.value = true
}

// 弹窗打开后的回调
const modalOpened = () => {
  visible.value = true
  
  // 添加防抖的resize事件监听[1](@ref)
  if (!resizeHandler.value) {
    resizeHandler.value = debounce(() => {
      refresh()
    }, 100)
  }
  window.addEventListener('resize', resizeHandler.value)
}

// 刷新裁剪器
const refresh = () => {
  if (cropperRef.value) {
    cropperRef.value.refresh()
  }
}

// 覆盖默认上传行为
const requestUpload = () => {
  // 空实现，使用beforeUpload处理上传逻辑[1](@ref)
}

// 向左旋转
const rotateLeft = () => {
  if (cropperRef.value) {
    cropperRef.value.rotateLeft()
  }
}

// 向右旋转
const rotateRight = () => {
  if (cropperRef.value) {
    cropperRef.value.rotateRight()
  }
}

// 图片缩放
const changeScale = (num) => {
  if (cropperRef.value) {
    cropperRef.value.changeScale(num)
  }
}

// 上传前处理 - 文件验证和转换[1,2](@ref)
const beforeUpload = (file) => {
  if (!file.type.startsWith('image/')) {
    ElMessage.error('文件格式错误，请上传图片类型，如：JPG，PNG后缀的文件。')
    return false
  }
  
  // 检查文件大小（限制为10MB）[5](@ref)
  const isLt10M = file.size / 1024 / 1024 < 10
  if (!isLt10M) {
    ElMessage.error('上传图片大小不能超过10MB')
    return false
  }

  // 将文件转换为Base64[3](@ref)
  const reader = new FileReader()
  reader.readAsDataURL(file)
  reader.onload = (e) => {
    options.img = e.target.result
  }
  
  return false // 阻止默认上传行为
}

// 上传图片
const uploadImg = async () => {
  if (!cropperRef.value) {
    ElMessage.error('裁剪器未初始化')
    return
  }

  uploadLoading.value = true
  
  try {
    // 获取裁剪后的Blob数据[1](@ref)
    cropperRef.value.getCropBlob(async (blobData) => {
      try {
        const formData = new FormData()
        formData.append('avatarfile', blobData, 'avatar.png')

        // 调用上传API[3](@ref)
        // const response = await uploadAvatar(formData)
        
        // 模拟上传成功
        setTimeout(() => {
          // 更新用户头像
          const newAvatarUrl = URL.createObjectURL(blobData)
          options.img = newAvatarUrl
          
          // 更新store中的用户头像[1](@ref)
          // userStore.updateAvatar(newAvatarUrl)
          
          open.value = false
          uploadLoading.value = false
          ElMessage.success('头像修改成功')
        }, 1000)
        
      } catch (error) {
        console.error('上传失败:', error)
        ElMessage.error('头像上传失败，请重试')
        uploadLoading.value = false
      }
    })
  } catch (error) {
    console.error('裁剪失败:', error)
    ElMessage.error('图片裁剪失败，请重试')
    uploadLoading.value = false
  }
}

// 实时预览
const realTime = (data) => {
  previews.value = data
}

// 关闭弹窗
const closeDialog = () => {
  // 恢复原始头像[1](@ref)
  // options.img = userStore.avatar
  visible.value = false
  
  // 移除resize事件监听
  if (resizeHandler.value) {
    window.removeEventListener('resize', resizeHandler.value)
    resizeHandler.value = null
  }
}

// 防抖函数[1](@ref)
const debounce = (func, wait) => {
  let timeout
  return function executedFunction(...args) {
    const later = () => {
      clearTimeout(timeout)
      func(...args)
    }
    clearTimeout(timeout)
    timeout = setTimeout(later, wait)
  }
}
</script>

<style scoped lang="scss">
.user-info-head {
  position: relative;
  display: inline-block;
  height: 120px;
  cursor: pointer;

  &:hover::after {
    content: '+';
    position: absolute;
    left: 0;
    right: 0;
    top: 0;
    bottom: 0;
    color: #eee;
    background: rgba(0, 0, 0, 0.5);
    font-size: 24px;
    font-style: normal;
    -webkit-font-smoothing: antialiased;
    -moz-osx-font-smoothing: grayscale;
    cursor: pointer;
    line-height: 110px;
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
  }
}

.img-circle {
  border-radius: 50%;
}

.img-lg {
  width: 100px;
  height: 100px;
  object-fit: cover;
}

.avatar-upload-preview {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  
  img {
    max-width: 100%;
    max-height: 100%;
    border-radius: 50%;
  }
}

:deep(.vue-cropper) {
  background-image: none;
}
</style>