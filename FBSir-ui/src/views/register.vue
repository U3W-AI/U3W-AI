<template>
  <div class="register">
    <div class="register-card">
      <el-form ref="registerRef" :model="registerForm" :rules="registerRules" class="register-form">
        <div class="card-head">
          <h3 class="title">注册</h3>
        </div>
        <div class="card-body">
          <div class="field-block">
            <label class="field-label">账号</label>
            <el-form-item prop="username" class="field-item">
              <el-input 
                v-model="registerForm.username" 
                type="text" 
                size="large" 
                auto-complete="off" 
                placeholder=""
                class="field-input"
              />
            </el-form-item>
          </div>
          <div class="field-block">
            <label class="field-label">密码</label>
            <el-form-item prop="password" class="field-item">
              <el-input
                v-model="registerForm.password"
                type="password"
                size="large" 
                auto-complete="off"
                placeholder=""
                class="field-input"
                @keyup.enter="handleRegister"
              />
            </el-form-item>
          </div>
          <div class="field-block">
            <label class="field-label">确认密码</label>
            <el-form-item prop="confirmPassword" class="field-item">
              <el-input
                v-model="registerForm.confirmPassword"
                type="password"
                size="large" 
                auto-complete="off"
                placeholder=""
                class="field-input"
                @keyup.enter="handleRegister"
              />
            </el-form-item>
          </div>
          <div class="field-block code-block" v-if="captchaEnabled">
            <label class="field-label">验证码</label>
            <div class="code-wrap">
              <el-form-item prop="code" class="field-item code-item">
                <el-input
                  size="large" 
                  v-model="registerForm.code"
                  auto-complete="off"
                  placeholder=""
                  class="field-input"
                  @keyup.enter="handleRegister"
                />
              </el-form-item>
              <div class="register-code">
                <img :src="codeUrl" @click="getCode" class="register-code-img"/>
              </div>
            </div>
          </div>
          <div class="terms-checkbox">
            <el-checkbox v-model="agree" class="terms-box" />
            <span class="terms-text">
              我已阅读并同意
              <a class="link" href="javascript:void(0)">服务条款</a>
              和
              <a class="link" href="javascript:void(0)">隐私政策</a>
            </span>
          </div>
          <el-button
            :loading="loading"
            size="large" 
            type="primary"
            class="primary-btn"
            :disabled="!agree"
            @click.prevent="handleRegister"
          >
            <span v-if="!loading">注册</span>
            <span v-else>注册中...</span>
          </el-button>
          <div class="divider"><span>or</span></div>
          <div class="social-login">
            <button class="social-btn" type="button" @click="handleGiteeLogin">
              <span class="social-icon gitee">G</span>
              <span class="social-text">使用 Gitee 登录</span>
              <span v-if="lastUsedProvider === 'gitee'" class="social-badge">上次使用</span>
            </button>
          </div>
          <div class="login-tip">
            <span>已有账号？</span>
          </div>
          <router-link class="login-btn" :to="'/login'">立即登录</router-link>
        </div>
      </el-form>
    </div>
    <!--  底部  -->
    <div class="el-register-footer">
      <span>{{ footerContent }}</span>
    </div>
  </div>
</template>

<script setup>
import { ElMessageBox, ElMessage } from "element-plus"
import { getCodeImg, register } from "@/api/login"
import defaultSettings from '@/settings'

const footerContent = defaultSettings.footerContent
const router = useRouter()
const route = useRoute()
const { proxy } = getCurrentInstance()
const registerEnabled = ref(false)
const agree = ref(false)
const baseApi = (import.meta.env.VITE_APP_BASE_API || "").replace(/\/$/, "")
const lastUsedProvider = ref("")

const registerForm = ref({
  username: "",
  password: "",
  confirmPassword: "",
  code: "",
  uuid: ""
})

const equalToPassword = (rule, value, callback) => {
  if (registerForm.value.password !== value) {
    callback(new Error("两次输入的密码不一致"))
  } else {
    callback()
  }
}

const registerRules = {
  username: [
    { required: true, trigger: "blur", message: "请输入您的账号" },
    { min: 2, max: 20, message: "用户账号长度必须介于 2 和 20 之间", trigger: "blur" }
  ],
  password: [
    { required: true, trigger: "blur", message: "请输入您的密码" },
    { min: 5, max: 20, message: "用户密码长度必须介于 5 和 20 之间", trigger: "blur" },
    { pattern: /^[^<>"'|\\]+$/, message: "不能包含非法字符：< > \" ' \\\ |", trigger: "blur" }
  ],
  confirmPassword: [
    { required: true, trigger: "blur", message: "请再次输入您的密码" },
    { required: true, validator: equalToPassword, trigger: "blur" }
  ],
  code: [{ required: true, trigger: "change", message: "请输入验证码" }]
}

const codeUrl = ref("")
const loading = ref(false)
const captchaEnabled = ref(true)

function getCode() {
  getCodeImg().then(res => {
    captchaEnabled.value = res.captchaEnabled === undefined ? true : res.captchaEnabled
    registerEnabled.value = res.registerEnabled === true
    
    // 检查注册功能是否开启
    if (!registerEnabled.value) {
      ElMessage.warning('系统当前不允许注册新用户，请联系管理员')
      router.push('/login')
      return
    }
    
    if (captchaEnabled.value) {
      codeUrl.value = "data:image/gif;base64," + res.img
      registerForm.value.uuid = res.uuid
    }
  }).catch(() => {
    ElMessage.error('获取验证码失败，请稍后再试')
    router.push('/login')
  })
}

function handleRegister() {
  // 再次检查注册功能是否开启
  if (!registerEnabled.value) {
    ElMessage.warning('系统当前不允许注册新用户，请联系管理员')
    router.push('/login')
    return
  }
  if (!agree.value) {
    ElMessage.warning('请先同意服务条款与隐私政策')
    return
  }
  
  proxy.$refs.registerRef.validate(valid => {
    if (valid) {
      loading.value = true
      register(registerForm.value).then(() => {
        const username = registerForm.value.username
        loading.value = false
        showRegisterSuccess(username)
      }).catch(() => {
        loading.value = false
        if (captchaEnabled) {
          getCode()
        }
      })
    }
  })
}

function showRegisterSuccess(username) {
  const message = `
    <div class="register-countdown__content">
      <div class="register-countdown__title">注册成功</div>
      <div class="register-countdown__desc">欢迎你，${username}</div>
    </div>
  `

  const finish = () => {
    router.push("/")
  }

  ElMessageBox.alert(message, "系统提示", {
    dangerouslyUseHTMLString: true,
    type: "success",
    confirmButtonText: "确定",
    closeOnClickModal: false,
    closeOnPressEscape: false,
    customClass: "register-success-dialog",
    callback: finish
  })
}

function handleGiteeLogin() {
  setLastUsed("gitee")
  window.location.href = `${baseApi}/gitlogin`
}

getCode()
lastUsedProvider.value = localStorage.getItem("lastLoginProvider") || ""

watch(route, (newRoute) => {
  const query = newRoute.query || {}
  if (query.giteeBindToken || query.giteeBound || query.giteeError || query.oauthToken || query.token) {
    router.replace({ path: "/login", query })
  }
}, { immediate: true })

function setLastUsed(provider) {
  lastUsedProvider.value = provider
  localStorage.setItem("lastLoginProvider", provider)
}
</script>

<style lang='scss' scoped>
.register {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 100%;
  position: relative;
  min-height: 100vh;
  padding: 32px 16px 64px;
  background: radial-gradient(circle at top, #eef3ff 0%, #f7f9fc 50%, #ffffff 100%);
  font-family: "Poppins", "PingFang SC", "Microsoft YaHei", sans-serif;
}
.register::before {
  content: "";
  position: absolute;
  inset: 0;
  background: linear-gradient(120deg, rgba(83, 116, 255, 0.08), transparent 60%);
  pointer-events: none;
}
.register-card {
  width: 360px;
  border-radius: 14px;
  background: #ffffff;
  border: 1px solid #e6eaf2;
  box-shadow: 0 16px 40px rgba(25, 42, 89, 0.12);
  position: relative;
  z-index: 1;
  animation: card-rise 0.6s ease;
}
.title {
  margin: 0;
  text-align: left;
  color: #0f172a;
  font-size: 22px;
  font-weight: 700;
  letter-spacing: 0.2px;
}

.register-form {
  padding: 20px 26px 26px;
}
.card-head {
  padding-bottom: 12px;
  border-bottom: 1px solid #eef1f6;
}
.card-body {
  padding-top: 18px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.field-block {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.field-label {
  font-size: 13px;
  color: #3b4253;
  font-weight: 600;
}
.field-item {
  margin-bottom: 0;
}
.field-item :deep(.el-form-item__content) {
  margin-left: 0;
  width: 100%;
}
.field-input {
  width: 100%;
}
.field-input :deep(.el-input__wrapper) {
  border-radius: 8px;
  padding: 4px 12px;
  box-shadow: 0 0 0 1px #c9d4ea inset;
}
.field-input :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 2px rgba(72, 113, 247, 0.35) inset;
}
.field-input :deep(.el-input__inner) {
  height: 36px;
  line-height: 36px;
}
.code-block {
  gap: 8px;
}
.code-wrap {
  display: flex;
  gap: 10px;
  align-items: center;
}
.code-item {
  flex: 1;
}
.register-code {
  width: 108px;
  height: 40px;
  border-radius: 8px;
  border: 1px solid #d8e1f4;
  overflow: hidden;
  background: #f5f7ff;
  img {
    cursor: pointer;
    vertical-align: middle;
    width: 100%;
    height: 100%;
    object-fit: cover;
  }
}
.terms-checkbox {
  margin-top: 4px;
  font-size: 12px;
  color: #4b5563;
  display: flex;
  align-items: center;
}
.terms-box :deep(.el-checkbox__input) {
  margin-top: 2px;
}
.terms-box :deep(.el-checkbox__label) {
  display: none;
}
.terms-text {
  padding-left: 8px;
  line-height: 1.4;
  display: inline-flex;
  align-items: center;
  gap: 2px;
}
.terms-checkbox .link {
  color: #3b6ff6;
}
.primary-btn {
  width: 100%;
  border-radius: 8px;
  font-weight: 600;
  background: #3b6ff6;
  border-color: #3b6ff6;
}
.primary-btn:hover {
  background: #2d5fe0;
  border-color: #2d5fe0;
}
.divider {
  display: flex;
  align-items: center;
  justify-content: center;
  color: #8a94a6;
  font-size: 12px;
  gap: 12px;
  margin: 4px 0;
}
.divider::before,
.divider::after {
  content: "";
  flex: 1;
  height: 1px;
  background: #e6eaf2;
}
.social-login {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.social-btn {
  position: relative;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  background: #ffffff;
  border: 1px solid #e3e6ee;
  border-radius: 8px;
  font-size: 13px;
  color: #222;
  cursor: pointer;
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
}
.social-btn:hover {
  border-color: #cfd6e6;
  box-shadow: 0 6px 16px rgba(17, 24, 39, 0.08);
}
.social-icon {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  font-weight: 700;
  color: #ffffff;
  background: #111827;
}
.social-icon.gitee {
  background: #c71d23;
}
.social-text {
  flex: 1;
  text-align: center;
  font-weight: 600;
}
.social-badge {
  position: absolute;
  right: 10px;
  top: -8px;
  padding: 2px 6px;
  font-size: 10px;
  color: #ffffff;
  background: #3b6cf6;
  border-radius: 10px;
}
.login-tip {
  text-align: center;
  font-size: 12px;
  color: #6b7280;
  margin-top: 6px;
}
.login-btn {
  display: block;
  width: max-content;
  margin: 0 auto;
  padding: 6px 14px;
  border-radius: 8px;
  border: 1px solid #d8deea;
  color: #1f2937;
  font-weight: 600;
  text-align: center;
}
.login-btn:hover {
  border-color: #3b6ff6;
  color: #3b6ff6;
}
.el-register-footer {
  height: 40px;
  line-height: 40px;
  position: fixed;
  bottom: 0;
  width: 100%;
  text-align: center;
  color: #fff;
  font-family: Arial;
  font-size: 12px;
  letter-spacing: 1px;
}
.register-code-img {
  padding-left: 0;
}
:deep(.register-countdown__content) {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
:deep(.register-countdown__title) {
  font-size: 14px;
  font-weight: 700;
  color: #0f172a;
}
:deep(.register-countdown__desc) {
  font-size: 12px;
  color: #3b4253;
}
@keyframes card-rise {
  from {
    opacity: 0;
    transform: translateY(12px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
</style>
