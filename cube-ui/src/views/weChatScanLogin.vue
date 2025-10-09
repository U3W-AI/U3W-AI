<template>
  <div id="login_container" style="height: 400px; width: 400px"></div>


</template>

<script>

export default {
  name: "WeCahtScanLogin",
  data() {
    return {
      authCode:'',
      wwLogin: null,//记录组件的实例
    }
  },
  watch: {
    "$route.query": {
      handler(newVal, oldVal) {
        if(this.authCode){
          this.handleWWLogin()// 根据企业微信code调用后台接口进行登录操作
        }
      },
      deep: true,
      immediate: true,
    },
    "authCode": {
      handler(newVal, oldVal) {
        console.log("authCode发生改变",this.authCode);
        if(this.authCode){
          this.handleWWLogin()
        }
      },

    },
  },
  // JS方法
  mounted() {
    this.createCode(); // 生成企业微信二维码 并获取code
  },
  created() {

  },
  methods: {
    createCode() {
      const that=this;
      this.wwLogin = ww.createWWLoginPanel({
        el: '#login_container',
        params: {
          login_type: 'CorpApp',
          appid: 'ww722362817b3c466a',
          agentid: '1000008',
          redirect_uri: 'https://u3w.com/#/weChatScanLogin',
          redirect_type: 'callback'
    },
      onCheckWeComLogin({ isWeComLogin }) {
        console.log('isWeComLogin',isWeComLogin)
      },
      onLoginSuccess(val) {
        console.log('onLoginSuccess',val.code)
        that.authCode=val.code;// 获取的code赋值
      },
      onLoginFail(err) {
        console.log('err',err)
      },

    });

    },

  }
};
</script>

