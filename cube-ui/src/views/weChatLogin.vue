<template>
  <div class="login">
  </div>
</template>

<script>
import logoImg from '@/assets/logo/logo.jpg'
import {login, weChatlogin} from "@/api/login";
import Cookies from "js-cookie";
import { encrypt, decrypt } from '@/utils/jsencrypt'
import {setToken} from "@/utils/auth";

export default {
  name: "WeChatLogin",
  data() {
    return {
      loginForm: {
        code: ""
      },
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
    var query = this.$route.query
    if (query.code) {
      this.loginForm.code= query.code;
     this.weLogin()
    }else{
      window.location.href="https://open.weixin.qq.com/connect/oauth2/authorize?appid=企业ID&redirect_uri=自己的后台地址&response_type=code&scope=snsapi_base&agentid=自己的企业微信应用ID&state=10000001#wechat_redirect"
    }

  },
  methods: {
    weLogin() {
          this.$store.dispatch("WeChatLogin", this.loginForm).then(() => {
            this.$router.push({ path: this.redirect || "/" }).catch(()=>{});
          }).catch(() => {
            this.loading = false;
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
  background-image: url("../assets/images/login-background.jpg");
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
  width: 400px;
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
</style>
