<template>
  <div class="login">
    <!-- 企业微信登录显示客服二维码 -->
    <el-form v-if="showWeChatServiceQR" ref="loginForm" :model="loginForm" class="login-form">
      <p class="wx_log_title">
        <img style="width: 21px;height: 16px;" src="https://www.logomaker.com.cn/statics/images/weixin.png" alt="">
        <span style="font-size: 20px;font-weight: bold;padding-left: 3px;">企业微信客服</span>
      </p>
      <div style="width: 182px;height: 182px;border: 1px solid #dddddd;margin: auto;">
        <img src="https://u3w.com/chatfile/企业微信.jpg" :width="size" :height="size" alt="企业微信客服" />
      </div>
      <div class="wechat-service-info">
        <p class="service-instruction">请扫码联系客服</p>
        <div class="service-message-container">
          <p class="service-message">
            抱歉，暂无您的用户信息。添加客服前，请先在
            <span class="browser-link" @click="copyProfileLink">浏览器后台个人中心</span>
            填写手机号，再将手机号连同
            <span class="userid-highlight" @click="copyUserid">{{ currentUserid || 'zhupengyu' }}</span>
            信息一起发给客服，方便为您添加信息，确保正常使用。
          </p>
          <div v-if="showCopyMessage" class="copy-message">{{ copyMessage }}</div>
        </div>
      </div>
    </el-form>

    <!-- 普通微信登录 -->
    <el-form v-else ref="loginForm" :model="loginForm" class="login-form">
      <p class="wx_log_title">
        <img style="width: 21px;height: 16px;" src="https://www.logomaker.com.cn/statics/images/weixin.png" alt="">
        <span style="font-size: 20px;font-weight: bold;padding-left: 3px;">微信登录/注册</span>
      </p>
      <div style="width: 182px;height: 182px;border: 1px solid #dddddd;margin: auto;">
        <img :src="qrCodeDataUrl" :width="size" :height="size" alt="QR Code" />
      </div>
      <div style="margin-top: 10px;margin-left: 20px">
        <span style="padding-top: 10px;text-align: center;font-size: 12px;color: rgba(153, 153, 153, 1)">关注"优立方服务号"进行登录/注册</span>
      </div>
    </el-form>
    <div class="footer-beian">
      <a href="https://beian.miit.gov.cn/" target="_blank" style="text-decoration: none; color: #666;">
        浙ICP备2025176978号-1
      </a>
      <br />
      <img src="@/assets/logo/ba.png" alt="logo" style="height: 18px; vertical-align: middle; margin-right: 6px;" />
      <a href=" " rel="noreferrer" target="_blank">浙公网安备33010802013946号</a>
    </div>
  </div>
</template>

<script>
import logoImg from '@/assets/logo/logo.jpg'
import { getQrCode } from "@/api/login";
import Cookies from "js-cookie";
import { encrypt, decrypt } from '@/utils/jsencrypt'
import QRCode from "qrcode";
import { getToken } from '@/utils/auth'


export default {
  name: "Login",
  data() {
    return {
      logo: logoImg,
      codeUrl: "",
      ticket:"",
      size:180,
      loginForm: {
        ticket: "",
      },
      qrCodeDataUrl:"",
      loading: false,
      // 验证码开关
      captchaEnabled: true,
      // 注册开关
      register: false,
      redirect: undefined,
      // 企业微信相关
      showWeChatServiceQR: false,
      currentUserid: '',
      serviceMessage: '',
      // 复制链接相关
      showCopyMessage: false,
      copyMessage: '',
      profileLink: 'https://u3w.com/#/user/profile'
    };
  },
  watch: {
    $route: {
      handler: function(route) {
        this.redirect = route.query && route.query.redirect;
      },
      immediate: true
    }
  },
  created() {
    let url = window.location.href;
    let params = url.split('?')[1];
    let paramsObj = {};
    if (params) {
      let paramsArr = params.split('&');
      for (let i = 0; i < paramsArr.length; i++) {
        let param = paramsArr[i].split('=');
        paramsObj[param[0]] = param[1];
      }
    }
    let paramValue = paramsObj['code'];
    let stateValue = paramsObj['state'];

    // 检查是否从企业微信登录页面过来（通过state参数判断）
    const isFromWeChatLogin = stateValue === 'wechat_login_page' || url.includes('#/weChatLogin') || this.$route.path === '/weChatLogin';

    if(paramValue && isFromWeChatLogin){
      this.loginForm.code= paramValue;
      // 如果有code参数且来自企业微信，说明是企业微信登录回调
      this.weLogin()
      return
    }

    // 如果是从企业微信登录页面过来但没有code，显示提示
    if (isFromWeChatLogin) {
      this.showWeChatServiceQR = true;
      this.serviceMessage = '正在等待企业微信授权登录...';
      console.log('检测到企业微信登录页面访问');
      return;
    }

    // 普通微信登录处理
    if(paramValue && !isFromWeChatLogin){
      this.loginForm.code= paramValue;
      this.weLogin()
      return
    }

    this.getCookie();
    this.generateQRCode();
  },
  methods: {
    async generateQRCode() {
      try {
        const res = await getQrCode();
        this.codeUrl = res.url;
        this.ticket = res.ticket;
        this.qrCodeDataUrl = await QRCode.toDataURL(this.codeUrl, {
          width: 200,
          height: 200,
        });
        this.startCheckLogin(this.ticket)
      } catch (error) {
        console.error("生成二维码失败", error);
      }
    },

    startCheckLogin(ticket) {
      this.loginForm.ticket = ticket;
      this.task = setInterval(async () => {
        try {

          this.$store.dispatch("OfficeLogin", this.loginForm).then((res)  => {
            if(getToken()){
              this.beforeDestroy()
            }
            this.$router.push({ path: this.redirect || "/" }).catch(()=>{});

          }).catch(() => {
            this.loading = false;
          });
        } catch (error) {
          console.error('检查登录状态失败', error);
        }
      }, 2000);
    },
    // 停止定时任务（例如在组件销毁时）
    stopCheckLogin() {
      if (this.task) {
        clearInterval(this.task);
        this.task = null;
      }
    },
    beforeDestroy() {
      // 在组件销毁时清理定时器，避免内存泄漏
      this.stopCheckLogin();
    },
    weLogin() {
      this.$store.dispatch("WeChatLogin", this.loginForm).then(() => {
        this.$router.push({ path: this.redirect || "/" }).catch(()=>{});
      }).catch((error) => {
        this.loading = false;
        console.log('企业微信登录错误:', error);
        // 检查是否是需要显示客服二维码的错误
        if (error.response && error.response.data) {
          const responseData = error.response.data;
          if (responseData.code === 1001 && responseData.needShowQRCode) {
            this.showWeChatServiceQR = true;
            this.currentUserid = responseData.userid;
            this.serviceMessage = responseData.msg || '请联系客服处理相关问题';
            console.log('显示企业微信客服二维码');
          }
        }
      });
    },
    getCookie() {
      const username = Cookies.get("username");
      const password = Cookies.get("password");
      const rememberMe = Cookies.get('rememberMe')
      this.loginForm = {
        username: username === undefined ? this.loginForm.username : username,
        password: password === undefined ? this.loginForm.password : decrypt(password),
        rememberMe: rememberMe === undefined ? false : Boolean(rememberMe)
      };
    },
    handleLogin() {
      this.$refs.loginForm.validate(valid => {
        if (valid) {
          this.loading = true;
          if (this.loginForm.rememberMe) {
            Cookies.set("username", this.loginForm.username, { expires: 30 });
            Cookies.set("password", encrypt(this.loginForm.password), { expires: 30 });
            Cookies.set('rememberMe', this.loginForm.rememberMe, { expires: 30 });
          } else {
            Cookies.remove("username");
            Cookies.remove("password");
            Cookies.remove('rememberMe');
          }
          this.$store.dispatch("Login", this.loginForm).then(() => {
            this.$router.push({ path: this.redirect || "/" }).catch(()=>{});
          }).catch(() => {
            this.loading = false;
            if (this.captchaEnabled) {
              this.getCode();
            }
          });
        }
      });
    },

    // 复制个人中心链接
    copyProfileLink() {
      if (navigator.clipboard && navigator.clipboard.writeText) {
        // 现代浏览器使用Clipboard API
        navigator.clipboard.writeText(this.profileLink).then(() => {
          this.showCopySuccess();
        }).catch(() => {
          this.fallbackCopyToClipboard();
        });
      } else {
        // 兼容旧浏览器的回退方案
        this.fallbackCopyToClipboard();
      }
    },

    // 回退复制方法
    fallbackCopyToClipboard() {
      const textArea = document.createElement('textarea');
      textArea.value = this.profileLink;
      textArea.style.position = 'fixed';
      textArea.style.left = '-999999px';
      textArea.style.top = '-999999px';
      document.body.appendChild(textArea);
      textArea.focus();
      textArea.select();

      try {
        document.execCommand('copy');
        this.showCopySuccess();
      } catch (err) {
        this.showCopyError();
      } finally {
        document.body.removeChild(textArea);
      }
    },

    // 显示复制成功消息
    showCopySuccess() {
      this.copyMessage = '链接已复制到剪贴板';
      this.showCopyMessage = true;
      setTimeout(() => {
        this.showCopyMessage = false;
      }, 2000);
    },

    // 显示复制失败消息
    showCopyError() {
      this.copyMessage = '复制失败，请手动复制: ' + this.profileLink;
      this.showCopyMessage = true;
      setTimeout(() => {
        this.showCopyMessage = false;
      }, 4000);
    },

    // 复制用户ID
    copyUserid() {
      const userid = this.currentUserid || 'zhupengyu';

      if (navigator.clipboard && navigator.clipboard.writeText) {
        // 现代浏览器使用Clipboard API
        navigator.clipboard.writeText(userid).then(() => {
          this.showCopySuccessUserid();
        }).catch(() => {
          this.fallbackCopyUserid();
        });
      } else {
        // 兼容旧浏览器的回退方案
        this.fallbackCopyUserid();
      }
    },

    // 回退复制用户ID方法
    fallbackCopyUserid() {
      const userid = this.currentUserid || 'zhupengyu';
      const textArea = document.createElement('textarea');
      textArea.value = userid;
      textArea.style.position = 'fixed';
      textArea.style.left = '-999999px';
      textArea.style.top = '-999999px';
      document.body.appendChild(textArea);
      textArea.focus();
      textArea.select();

      try {
        document.execCommand('copy');
        this.showCopySuccessUserid();
      } catch (err) {
        this.showCopyErrorUserid();
      } finally {
        document.body.removeChild(textArea);
      }
    },

    // 显示用户ID复制成功消息
    showCopySuccessUserid() {
      this.copyMessage = '用户ID已复制到剪贴板';
      this.showCopyMessage = true;
      setTimeout(() => {
        this.showCopyMessage = false;
      }, 2000);
    },

    // 显示用户ID复制失败消息
    showCopyErrorUserid() {
      const userid = this.currentUserid || 'zhupengyu';
      this.copyMessage = '复制失败，请手动复制: ' + userid;
      this.showCopyMessage = true;
      setTimeout(() => {
        this.showCopyMessage = false;
      }, 4000);
    }
  }
};
</script>

<style rel="stylesheet/scss" lang="scss">
/* 新增备案号样式 */
.footer-beian {
  position: fixed;
  bottom: 20px;
  left: 0;
  right: 0;
  text-align: center;
  font-size: 12px;
  color: #666;
}

.login {
  /* 确保登录容器有相对定位 */
  position: relative;
  min-height: 100%;
}
.login {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 100%;
  background-image: url("../assets/images/login-background.jpeg");
  background-size: cover;
}
.title {
  margin: 0px auto 30px auto;
  text-align: center;
  color: #707070;
}

.login-form {
  border-radius: 6px;
  background: #ffffff;
  padding: 25px 25px 5px 25px;
  .el-input {
    height: 38px;
    input {
      height: 38px;
    }
  }
  .input-icon {
    height: 39px;
    width: 14px;
    margin-left: 2px;
  }
}
.login-tip {
  font-size: 13px;
  text-align: center;
  color: #bfbfbf;
}
.login-code {
  width: 33%;
  height: 38px;
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
}
.login-code-img {
  height: 38px;
}
.qrcode_box{
  width: 4rem!important;
  height: 4rem!important;
  margin:rem(40) auto 0;
  .qrcode{
    width: 4rem!important;
    height: 4rem!important;
  }
}
.wx_log_title{
  text-align: center;
}

/* 企业微信客服样式 */
.login-form .wechat-service-info {
  text-align: center;
  padding: 15px 10px;
}

.login-form .service-instruction {
  font-size: 12px;
  color: rgba(153, 153, 153, 1);
  margin: 8px 0 15px 0;
  font-weight: 500;
}

.login-form .service-message-container {
  background-color: #f8f9fa;
  padding: 15px;
  border-radius: 8px;
  border: 1px solid #e9ecef;
  margin: 10px 0;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.05);
}

.login-form .service-message {
  font-size: 12px;
  color: rgba(51, 51, 51, 1);
  line-height: 1.6;
  margin: 0;
  text-align: left;
}

/* 浏览器链接样式 */
.login-form .browser-link {
  color: #409EFF;
  cursor: pointer;
  text-decoration: none;
  font-weight: 600;
  transition: all 0.2s ease;
  padding: 2px 6px;
  border-radius: 4px;
  display: inline-block;
  background-color: rgba(64, 158, 255, 0.1);
  border: 1px solid rgba(64, 158, 255, 0.2);
}

.login-form .browser-link:hover {
  color: #ffffff;
  background-color: #409EFF;
  transform: translateY(-1px);
  box-shadow: 0 2px 6px rgba(64, 158, 255, 0.3);
  border-color: #409EFF;
}

.login-form .browser-link:active {
  transform: translateY(0);
  box-shadow: 0 1px 3px rgba(64, 158, 255, 0.3);
}

/* 用户ID高亮样式 */
.login-form .userid-highlight {
  color: #E6A23C;
  cursor: pointer;
  text-decoration: none;
  font-weight: bold;
  transition: all 0.2s ease;
  padding: 2px 6px;
  border-radius: 4px;
  display: inline-block;
  background-color: rgba(230, 162, 60, 0.15);
  border: 1px solid rgba(230, 162, 60, 0.3);
  font-size: 13px;
}

.login-form .userid-highlight:hover {
  color: #ffffff;
  background-color: #E6A23C;
  transform: translateY(-1px);
  box-shadow: 0 2px 6px rgba(230, 162, 60, 0.3);
  border-color: #E6A23C;
}

.login-form .userid-highlight:active {
  transform: translateY(0);
  box-shadow: 0 1px 3px rgba(230, 162, 60, 0.3);
}

/* 复制消息样式 */
.login-form .copy-message {
  font-size: 11px !important;
  color: #67C23A !important;
  margin: 10px 0 0 0 !important;
  line-height: 1.4 !important;
  animation: fadeInOut 2s ease-in-out;
  background-color: rgba(103, 194, 58, 0.1);
  padding: 6px 10px;
  border-radius: 4px;
  border: 1px solid rgba(103, 194, 58, 0.2);
  text-align: center;
  font-weight: 500;
}

@keyframes fadeInOut {
  0% {
    opacity: 0;
    transform: translateY(-10px) scale(0.95);
  }
  15%, 85% {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
  100% {
    opacity: 0;
    transform: translateY(-5px) scale(0.98);
  }
}

</style>
