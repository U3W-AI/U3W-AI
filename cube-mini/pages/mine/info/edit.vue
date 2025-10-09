<template>
  <view class="container">
    <view class="example">

      <uni-forms ref="form" :model="user" labelWidth="80px" :rules="rules">
        <button class="avatar-wrapper" open-type="chooseAvatar" @chooseavatar="onChooseAvatar">
          <image v-if="user.avatar" :src="user.avatar" class="cu-avatar xl round" mode="widthFix"></image>
          <text v-if="!user.avatar" class="loginLogoText">授权头像</text>
        </button>

        <uni-forms-item label="用户昵称" name="nickName">
          <uni-easyinput  type='nickname' v-model="user.nickName" placeholder="请输入昵称" />
        </uni-forms-item>

        <uni-forms-item label="主机ID" name="corpId">
          <uni-easyinput v-model="user.corpId" placeholder="请输入主机ID" maxlength="50" />
        </uni-forms-item>

        <!-- <uni-forms-item label="手机号码" name="phonenumber">
					<uni-easyinput v-model="user.phonenumber" placeholder="请输入手机号码" />
				</uni-forms-item>
				<uni-forms-item label="邮箱" name="email">
					<uni-easyinput v-model="user.email" placeholder="请输入邮箱" />
				</uni-forms-item> -->
        <!--  <uni-forms-item label="性别" name="sex" required>
          <uni-data-checkbox v-model="user.sex" :localdata="sexs" />
        </uni-forms-item> -->
      </uni-forms>
      <button type="primary" class="login-btn cu-btn block bg-green lg round" @click="submit">提交</button>
    </view>
  </view>
</template>

<script>
  import {
    getUserProfile
  } from "@/api/system/user"
  import {
    updateUserProfile
  } from "@/api/system/user"
  import storage from '@/utils/storage'
  import constant from '@/utils/constant'
  import config from '@/config'
  import { ensureLatestCorpId, setCorpId } from '@/utils/corpId'

  export default {
    data() {
      return {
        user: {
          nickName: "",
          phonenumber: "",
          email: "",
          sex: "",
          avatar: "",
          corpId: ""
        },
        sexs: [{
          text: '男',
          value: "0"
        }, {
          text: '女',
          value: "1"
        }],
        rules: {
          nickName: {
            rules: [{
              required: true,
              errorMessage: '用户昵称不能为空'
            }]
          }
        }
      }
    },
    async onLoad() {
      // 确保主机ID是最新的，然后获取用户信息
      try {
        const result = await ensureLatestCorpId();
        console.log('完善资料页面进入时主机ID已更新:', result.corpId);
        // 如果有最新的企业ID，先更新用户信息中的主机ID
        if (result.corpId && this.user) {
          this.user.corpId = result.corpId;
          console.log('完善资料页面主机ID已预设:', result.corpId);
        }
      } catch (error) {
        console.warn('刷新主机ID失败，继续加载用户信息:', error);
      }
      this.getUser();
      
      // 监听企业ID自动更新事件
      uni.$on('corpIdUpdated', this.handleCorpIdUpdated);
    },
    onUnload() {
      // 移除事件监听
      uni.$off('corpIdUpdated', this.handleCorpIdUpdated);
    },
    onReady() {
      this.$refs.form.setRules(this.rules)
    },
    methods: {
      //获取微信头像
      onChooseAvatar(e) {
        console.log("头像地址：" + e.detail.avatarUrl)
        const baseUrl = config.baseUrl
        wx.uploadFile({
          filePath: e.detail.avatarUrl,
          name: 'file',
          url: baseUrl + "/common/upload",
          success: (res) => {
            // 拿到一个服务器地址，永久地址
            // JSON 解析，将JSON字符串解析为JSON对象
            const resObj = JSON.parse(res.data);
            // 拿到一个服务器地址，永久地址
            //将永久地址存到 newAvatar 进行显示和存储信息
            this.user.avatar = resObj.url

          },
          fail: function(res) {
            console.log(res); //发送失败回调，可以在这里了解失败原因
          }
        })

      },
      async getUser() {
        try {
          const response = await getUserProfile();
          this.user = response.data;
          
          // 获取用户信息后，确保主机ID字段显示最新值
          const currentCorpId = storage.get(constant.corpId);
          if (currentCorpId && this.user.corpId !== currentCorpId) {
            console.log('用户信息获取完成，同步最新主机ID:', currentCorpId);
            this.user.corpId = currentCorpId;
            this.$forceUpdate(); // 强制更新视图
          }
        } catch (error) {
          console.error('获取用户信息失败:', error);
        }
      },
      submit(ref) {
        console.log(this.user.nickName, "this.user.nickName");
        this.$refs.form.validate().then(res => {
          // 保存修改前的主机ID用于比较
          const oldCorpId = storage.get(constant.corpId);
          
          updateUserProfile(this.user).then(response => {
            storage.set(constant.name, this.user.nickName)
            storage.set(constant.avatar, this.user.avatar)
            
            // 如果主机ID发生变化，使用setCorpId函数更新
            if (this.user.corpId && this.user.corpId !== oldCorpId) {
              console.log('用户手动更新主机ID:', this.user.corpId);
              setCorpId(this.user.corpId);
            }
            
            console.log("修改后" + storage.get(constant.name))
            console.log("修改后" + storage.get(constant.avatar))
            this.$modal.msgSuccess("修改成功")
            this.$tab.reLaunch('/pages/mine/index')
          })
        })
      },
      handleCorpIdUpdated(data) {
        console.log('完善资料页面：检测到主机ID更新', data.corpId);
        this.$modal.msgSuccess('主机ID已自动更新');
        // 直接更新本地用户数据中的主机ID
        if (this.user && data.corpId) {
          this.user.corpId = data.corpId;
          this.$forceUpdate(); // 强制更新视图
        }
        // 重新获取用户信息，确保显示最新的主机ID
        this.getUser();
      }
    }
  }
</script>

<style lang="scss">
  page {
    background-color: #ffffff;
  }

  .cu-avatar {
    // border: 2px solid #eaeaea;

    .icon {
      font-size: 40px;
    }
  }

  .example {
    padding: 15px;
    background-color: #fff;
  }

  .segmented-control {
    margin-bottom: 15px;
  }

  .button-group {
    margin-top: 15px;
    display: flex;
    justify-content: space-around;
  }

  .form-item {
    display: flex;
    align-items: center;
    flex: 1;
  }

  .avatar-wrapper {
    background-color: white;
    margin-bottom: 50rpx;
  }

  .avatar-wrapper::after {
    background-color: white;
    border: 0;
    display: block;
    height: 200rpx;
    margin-top: 150rpx;
  }

  .button {
    display: flex;
    align-items: center;
    height: 35px;
    line-height: 35px;
    margin-left: 10px;
  }

  .login-btn {
    margin-top: 80rpx;
    height: 90rpx !important;
    background-color: #1ec57c !important;
  }
</style>