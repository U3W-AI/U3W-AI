<template>
  <div class="login">
    <el-form ref="loginForm" :model="loginForm"  class="login-form">
      <p class="wx_log_title"><img style="width: 21px;height: 16px;" src="https://www.logomaker.com.cn/statics/images/weixin.png" alt="">
        <span style=" font-size: 20px;font-weight: bold;padding-left: 3px;">微信登录/注册</span>
      </p>
      <div style=" width: 182px;height: 182px;border: 1px solid #dddddd;margin: auto;">
        <img :src="qrCodeDataUrl" :width="size" :height="size" alt="QR Code" />
      </div>
      <div style="margin-top: 10px;margin-left: 20px">
        <span style="padding-top: 10px;text-align: center;font-size: 12px;color: rgba(153, 153, 153, 1)">关注"优立方服务号"进行登录/注册</span>
      </div>
    </el-form>
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
      redirect: undefined
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
    if(paramValue){
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
      }).catch(() => {
        this.loading = false;
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
    }
  }
};
</script>

<style rel="stylesheet/scss" lang="scss">
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

</style>
