<template>
  <div class="login">
    <el-form ref="loginRef" :model="loginForm" :rules="loginRules" class="login-form">
      <div class="login-logo">
        <img src="@/assets/logo/logo.png" alt="福帮手AI主机" class="logo-img" />
      </div>
      <h3 class="title">{{ title }}</h3>
      <div class="field-block">
        <label class="field-label">账号</label>
        <el-form-item prop="username">
          <el-input
            v-model="loginForm.username"
            type="text"
            size="large"
            auto-complete="off"
            placeholder="账号"
          >
            <template #prefix><svg-icon icon-class="user" class="el-input__icon input-icon" /></template>
          </el-input>
        </el-form-item>
      </div>
      <div class="field-block">
        <div class="field-row">
          <label class="field-label">密码</label>
          <a class="forgot-link" href="javascript:void(0)">忘记密码？</a>
        </div>
        <el-form-item prop="password">
          <el-input
            v-model="loginForm.password"
            type="password"
            size="large"
            auto-complete="off"
            placeholder="密码"
            @keyup.enter="handleLogin"
          >
            <template #prefix><svg-icon icon-class="password" class="el-input__icon input-icon" /></template>
          </el-input>
        </el-form-item>
      </div>
      <el-form-item prop="code" v-if="captchaEnabled">
        <el-input
          v-model="loginForm.code"
          size="large"
          auto-complete="off"
          placeholder="验证码"
          style="width: 63%"
          @keyup.enter="handleLogin"
        >
          <template #prefix><svg-icon icon-class="validCode" class="el-input__icon input-icon" /></template>
        </el-input>
        <div class="login-code">
          <img :src="codeUrl" @click="getCode" class="login-code-img"/>
        </div>
      </el-form-item>
      <div class="login-options">
        <el-checkbox v-model="loginForm.rememberMe">记住密码</el-checkbox>
        <div class="register-link" v-if="registerEnabled">
          <span class="tip-text">还没有账号？</span>
          <router-link class="link-type" :to="'/register'">立即注册</router-link>
        </div>
      </div>
      <el-form-item style="width:100%;">
        <el-button
          :loading="loading"
          size="large"
          type="primary"
          class="primary-btn"
          style="width:100%;"
          @click.prevent="handleLogin"
        >
          <span v-if="!loading">登 录</span>
          <span v-else>登 录 中...</span>
        </el-button>
      </el-form-item>
      <div class="divider">
        <span>或</span>
      </div>
      <div class="social-login">
        <button class="social-btn" type="button" @click="handleGiteeLogin">
          <span class="social-icon gitee">G</span>
          <span class="social-text">使用 Gitee 登录</span>
          <span v-if="lastUsedProvider === 'gitee'" class="social-badge">上次使用</span>
        </button>
      </div>
    </el-form>
    <el-dialog
      v-model="giteeBindVisible"
      title="绑定 Gitee 账号"
      width="360px"
      :close-on-click-modal="false"
      @close="handleGiteeBindClose"
    >
      <div class="gitee-bind-tip">检测到未绑定的 Gitee 账号，请选择绑定已有账号或自动创建账号。</div>
      <el-form class="gitee-bind-form">
        <el-form-item>
          <el-input
            v-model="giteeBindForm.username"
            placeholder="已有账号"
            auto-complete="off"
            size="large"
          />
        </el-form-item>
        <el-form-item>
          <el-input
            v-model="giteeBindForm.password"
            type="password"
            placeholder="账号密码"
            auto-complete="off"
            size="large"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button
          type="primary"
          :loading="giteeBindLoading"
          @click="handleGiteeBindExisting"
        >
          绑定已有账号
        </el-button>
        <el-button
          plain
          :loading="giteeBindLoading"
          @click="handleGiteeBindCreate"
        >
          自动创建账号
        </el-button>
      </template>
    </el-dialog>
    <!--  底部  -->
    <div class="el-login-footer">
      <span>{{ footerContent }}</span>
    </div>
  </div>
</template>

<script setup>
import { getCodeImg } from "@/api/login"
import request from "@/utils/request"
import Cookies from "js-cookie"
import { encrypt, decrypt } from "@/utils/jsencrypt"
import { setToken } from "@/utils/auth"
import useUserStore from '@/store/modules/user'
import usePermissionStore from '@/store/modules/permission'
import { isHttp } from "@/utils/validate"
import defaultSettings from '@/settings'
import { ElMessage, ElMessageBox } from "element-plus"

const title = import.meta.env.VITE_APP_TITLE
const footerContent = defaultSettings.footerContent
const userStore = useUserStore()
const route = useRoute()
const router = useRouter()
const { proxy } = getCurrentInstance()

const loginForm = ref({
  username: "admin",
  password: "admin123",
  rememberMe: false,
  code: "",
  uuid: ""
})

const loginRules = {
  username: [{ required: true, trigger: "blur", message: "请输入您的账号" }],
  password: [{ required: true, trigger: "blur", message: "请输入您的密码" }],
  code: [{ required: true, trigger: "change", message: "请输入验证码" }]
}

const codeUrl = ref("")
const loading = ref(false)
// 验证码开关
const captchaEnabled = ref(true)
// 注册开关
const registerEnabled = ref(false)
const redirect = ref(undefined)
const baseApi = (import.meta.env.VITE_APP_BASE_API || "").replace(/\/$/, "")
const giteeBindVisible = ref(false)
const giteeBindLoading = ref(false)
const giteeBindToken = ref("")
const giteeBindForm = ref({
  username: "",
  password: ""
})

watch(route, (newRoute) => {
    redirect.value = newRoute.query && newRoute.query.redirect
    handleOauthToken()
}, { immediate: true })

function handleLogin() {
  proxy.$refs.loginRef.validate(valid => {
    if (valid) {
      loading.value = true
      setLastUsed("password")
      // 勾选了需要记住密码设置在 cookie 中设置记住用户名和密码
      if (loginForm.value.rememberMe) {
        Cookies.set("username", loginForm.value.username, { expires: 30 })
        Cookies.set("password", encrypt(loginForm.value.password), { expires: 30 })
        Cookies.set("rememberMe", loginForm.value.rememberMe, { expires: 30 })
      } else {
        // 否则移除
        Cookies.remove("username")
        Cookies.remove("password")
        Cookies.remove("rememberMe")
      }
      // 调用action的登录方法
      userStore.login(loginForm.value).then(() => {
        const query = route.query
        const otherQueryParams = Object.keys(query).reduce((acc, cur) => {
          if (cur !== "redirect") {
            acc[cur] = query[cur]
          }
          return acc
        }, {})
        router.push({ path: redirect.value || "/", query: otherQueryParams })
      }).catch(() => {
        loading.value = false
        // 重新获取验证码
        if (captchaEnabled.value) {
          getCode()
        }
      })
    }
  })
}

function getCode() {
  getCodeImg().then(res => {
    captchaEnabled.value = res.captchaEnabled === undefined ? true : res.captchaEnabled
    registerEnabled.value = res.registerEnabled === true
    if (captchaEnabled.value) {
      codeUrl.value = "data:image/gif;base64," + res.img
      loginForm.value.uuid = res.uuid
    }
  })
}

function getCookie() {
  const username = Cookies.get("username")
  const password = Cookies.get("password")
  const rememberMe = Cookies.get("rememberMe")
  loginForm.value = {
    username: username === undefined ? loginForm.value.username : username,
    password: password === undefined ? loginForm.value.password : decrypt(password),
    rememberMe: rememberMe === undefined ? false : Boolean(rememberMe)
  }
}

const lastUsedProvider = ref("")

function setLastUsed(provider) {
  lastUsedProvider.value = provider
  localStorage.setItem("lastLoginProvider", provider)
}

function handleGiteeLogin() {
  setLastUsed("gitee")
  window.location.href = `${baseApi}/gitlogin`
}

function handleOauthToken() {
  const giteeError = route.query?.giteeError
  if (giteeError) {
    ElMessage.error(decodeURIComponent(String(giteeError)))
    const cleanQuery = { ...route.query }
    delete cleanQuery.giteeError
    router.replace({ path: redirect.value || "/login", query: cleanQuery })
    return
  }
  const bindToken = route.query?.giteeBindToken
  if (bindToken) {
    giteeBindToken.value = String(bindToken)
    giteeBindVisible.value = true
    return
  }
  const boundUsername = route.query?.giteeUsername
  if (boundUsername && route.query?.giteeBound) {
    loginForm.value.username = String(boundUsername)
    ElMessage.info("Gitee账号已绑定，请输入密码完成登录")
  }
  let token = route.query?.oauthToken || route.query?.token
  let targetPath = ""
  let targetQuery = {}
  if (!token) {
    const redirectParam = route.query?.redirect
    if (typeof redirectParam === "string") {
      const [pathPart, queryPart] = redirectParam.split("?")
      const params = new URLSearchParams(queryPart || "")
      token = params.get("oauthToken") || params.get("token")
      if (token) {
        params.delete("oauthToken")
        params.delete("token")
        params.forEach((value, key) => {
          targetQuery[key] = value
        })
        targetPath = pathPart || "/"
      }
    }
  }
  if (!token) {
    return
  }
  completeOauthLogin(token, targetPath, targetQuery)
}

function completeOauthLogin(token, targetPath = "", targetQuery = {}) {
  setToken(token)
  userStore.token = token
  userStore.getInfo().then(() => {
    usePermissionStore().generateRoutes().then(accessRoutes => {
      accessRoutes.forEach(route => {
        if (!isHttp(route.path)) {
          router.addRoute(route)
        }
      })
      if (targetPath) {
        router.replace({ path: targetPath, query: targetQuery })
        return
      }
      const cleanQuery = { ...route.query }
      delete cleanQuery.oauthToken
      delete cleanQuery.token
      delete cleanQuery.giteeBindToken
      delete cleanQuery.giteeBound
      delete cleanQuery.giteeUsername
      delete cleanQuery.giteeError
      router.replace({ path: redirect.value || "/", query: cleanQuery })
    })
  })
}

function resetGiteeBindForm() {
  giteeBindForm.value.username = ""
  giteeBindForm.value.password = ""
}

function tryAutoLogin(username, password) {
  loginForm.value.username = username
  loginForm.value.password = password
  if (captchaEnabled.value) {
    ElMessage.info("请输入验证码后点击登录完成认证")
    return
  }
  loading.value = true
  userStore.login(loginForm.value).then(() => {
    router.replace({ path: "/index" })
  }).catch(() => {
    loading.value = false
  })
}

async function handleGiteeBindExisting() {
  if (!giteeBindToken.value) {
    ElMessage.error("绑定信息已失效，请重新授权")
    return
  }
  if (!giteeBindForm.value.username || !giteeBindForm.value.password) {
    ElMessage.warning("请输入账号和密码")
    return
  }
  giteeBindLoading.value = true
  try {
    const res = await request({
      url: "/gitee/bind",
      method: "post",
      headers: {
        isToken: false,
        repeatSubmit: false
      },
      data: {
        bindToken: giteeBindToken.value,
        username: giteeBindForm.value.username,
        password: giteeBindForm.value.password
      }
    })
    giteeBindVisible.value = false
    const username = giteeBindForm.value.username
    const password = giteeBindForm.value.password
    resetGiteeBindForm()
    tryAutoLogin(username, password)
  } catch (error) {
    ElMessage.error(error?.message || "绑定失败，请重试")
  } finally {
    giteeBindLoading.value = false
  }
}

async function handleGiteeBindCreate() {
  if (!giteeBindToken.value) {
    ElMessage.error("绑定信息已失效，请重新授权")
    return
  }
  giteeBindLoading.value = true
  try {
    const res = await request({
      url: "/gitee/create",
      method: "post",
      headers: {
        isToken: false,
        repeatSubmit: false
      },
      data: {
        bindToken: giteeBindToken.value
      }
    })
    giteeBindVisible.value = false
    resetGiteeBindForm()
    if (res.username && res.password) {
      const message = `
        <div style="line-height:1.6;">
          <div>账号已创建，请保存以下信息：</div>
          <div>用户名：<strong>${res.username}</strong></div>
          <div>初始密码：<strong>${res.password}</strong></div>
        </div>
      `
      ElMessageBox.alert(message, "Gitee 账号创建成功", {
        dangerouslyUseHTMLString: true,
        type: "success",
        confirmButtonText: "我已保存"
      }).then(() => {
        tryAutoLogin(res.username, res.password)
      })
    } else {
      ElMessage.info("账号已创建，请使用新账号登录")
    }
  } catch (error) {
    ElMessage.error(error?.message || "创建账号失败，请重试")
  } finally {
    giteeBindLoading.value = false
  }
}

function handleGiteeBindClose() {
  giteeBindVisible.value = false
  resetGiteeBindForm()
}

getCode()
getCookie()
lastUsedProvider.value = localStorage.getItem("lastLoginProvider") || ""
</script>

<style lang='scss' scoped>
.login {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 100%;
  background-color: #f2f4f8;
  background-image: url('@/assets/images/login-background.jpg');
  background-size: cover;
  background-position: center;
  background-attachment: fixed;
  position: relative;
  
  &::before {
    content: '';
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    background-color: rgba(0, 0, 0, 0.3);
    z-index: 0;
  }
}
.login-logo {
  text-align: center;
  margin-bottom: 20px;
  z-index: 1;
  position: relative;
  .logo-img {
    width: 120px;
    height: auto;
  }
}

.title {
  margin: 0px auto 16px auto;
  text-align: center;
  color: #2b2b2b;
  font-weight: 600;
  z-index: 1;
  position: relative;
}

.login-form {
  border-radius: 12px;
  background: #ffffff;
  width: 360px;
  padding: 22px 22px 16px 22px;
  z-index: 1;
  position: relative;
  border: 1px solid #e7e9ef;
  box-shadow: 0 18px 50px rgba(18, 28, 45, 0.12);
  .el-input {
    height: 40px;
    input {
      height: 40px;
    }
  }
  .input-icon {
    height: 39px;
    width: 14px;
    margin-left: 0px;
  }
}
.login-form :deep(.el-input__wrapper) {
  border-radius: 8px;
  box-shadow: none;
  background: #f8fafc;
}
.login-form :deep(.el-input__inner) {
  color: #111827;
}
.login-form :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 2px rgba(59, 108, 246, 0.25);
}
.social-login {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-bottom: 14px;
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
.divider {
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 6px 0 14px;
  color: #9aa3b2;
  font-size: 12px;
}
.divider::before,
.divider::after {
  content: "";
  flex: 1;
  height: 1px;
  background: #e4e7ee;
  margin: 0 8px;
}
.field-block {
  margin-bottom: 10px;
}
.field-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
}
.field-label {
  font-size: 12px;
  color: #111827;
  font-weight: 600;
}
.forgot-link {
  font-size: 12px;
  color: #7b8798;
  text-decoration: none;
}
.forgot-link:hover {
  color: #4b5563;
}
.primary-btn {
  height: 40px;
  border-radius: 8px;
  font-weight: 600;
  background-color: #3b6cf6;
  border-color: #3b6cf6;
}
.primary-btn:hover {
  background-color: #305ce0;
  border-color: #305ce0;
}
.login-tip {
  font-size: 13px;
  text-align: center;
  color: #bfbfbf;
}
.login-code {
  width: 33%;
  height: 40px;
  float: right;
  img {
    cursor: pointer;
    vertical-align: middle;
  }
}
.el-login-footer {
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
  z-index: 1;
}
.login-code-img {
  height: 40px;
  padding-left: 12px;
}
.login-options {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  
  .register-link {
    display: flex;
    align-items: center;
    gap: 4px;
    
    .tip-text {
      font-size: 13px;
      color: #909399;
    }
    
    .link-type {
      font-size: 13px;
      color: #409eff;
      text-decoration: none;
      
      &:hover {
        color: #66b1ff;
      }
    }
  }
}
.gitee-bind-tip {
  font-size: 12px;
  color: #6b7280;
  margin-bottom: 12px;
  line-height: 1.5;
}
.gitee-bind-form :deep(.el-input__wrapper) {
  border-radius: 8px;
}
</style>
