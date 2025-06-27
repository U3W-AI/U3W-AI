<template>
  <view class="mine-container" :style="{height: `${windowHeight}px`}">
    <!--顶部个人信息栏-->
    <view class="header-section">
      <view class="flex justify-between" style="padding: 0 30rpx 50rpx;">
        <view class="flex align-center">

          <image v-if="avatar" :src="avatar" class="cu-avatar xl round" mode="widthFix"></image>
          <view v-if="!name" @click="wxhandleLogin" class="login-tip">
            点击登录
          </view>
          <view v-if="name" class="user-info">
            <view class="u_title">
              {{ name }}
            </view>
            <view class="u_title1">
              用户ID: {{ userid }}
            </view>
          </view>
        </view>

      </view>
    </view>

    <view class="content-section">
     <view class="mine-actions grid col-4 text-center">
        <view class="action-item" @click="myPoints">
          <text style="font-size: 16px;"> {{points}} </text>
          <text class="text">我的积分</text>
        </view>
      
      </view>

      <view class="menu-list">
        <view class="list-cell list-cell-arrow" @click="handleToEditInfo">
          <view class="menu-item-box">
            <view class="iconfont icon-user menu-icon"></view>
            <view>完善资料</view>
          </view>
        </view>

      </view>
	  

      <view class="action-btn" v-if="name">
        <button @click="handleLogout" class="login-btn cu-btn block bg-green lg round">退出登录</button>
      </view>


    </view>
  </view>
</template>

<script>
  import storage from '@/utils/storage'
  import {
    getUserCount
  } from '@/api/report'
  import constant from '@/utils/constant'

  export default {
    data() {
      return {
        /**微信登录的form数据**/
        wxLoginForm: {
          code: "",
          qwcode: "",
          encryptedIv: "",
          encryptedData: "",
          nickName: "",
          avatar: ""
        },

        name: storage.get(constant.name),
        openid: this.$store.state.user.openid,
        userid: storage.get(constant.userId),
        points: 0,
        reportNum: 0,
        collectionNum: 0,
        browseNum: 0,
        version: getApp().globalData.config.appInfo.version
      }
    },
	onLoad(options){
	//   if (options) {
	//     let orderId = decodeURIComponent(options.orderId)
	// 	if(orderId){
	// 		uni.showModal({
	// 		    title: '嘟嘟',
	// 		    content: '订单ID:'+orderId,
	// 		    success: function (res) {
	// 		        if (res.confirm) {
	// 		            console.log('用户点击确定');
	// 		            // 用户点击了确定按钮的相关逻辑可以放在这里
	// 		        } else if (res.cancel) {
	// 		            console.log('用户点击取消');
	// 		            // 用户点击了取消按钮的相关逻辑可以放在这里
	// 		        }
	// 		    }
	// 		});
	// 	}
	
	//   }
	},
    computed: {
      avatar() {
        return storage.get(constant.avatar)
      },
      windowHeight() {
        return uni.getSystemInfoSync().windowHeight - 50
      }
    },
    onShow() {
      console.log("用户ID" + this.userid)
      if (this.userid) {
        this.getUserCount(this.userid)
      }
    },
    created() {

    },
    methods: {
      getUserCount(userid) {

        getUserCount(userid).then(res => {
          this.points = res.data.points
          this.reportNum = res.data.reportNum
          this.collectionNum = res.data.collectionNum
          this.browseNum = res.data.browseNum
        })
      },
      wxhandleLogin() {
        uni.navigateTo({
          url: '/pages/login/index'
        });


      },

      handleToInfo() {
        this.$tab.navigateTo('/pages/mine/info/index')
      },
      handleToEditInfo() {
        this.$tab.navigateTo('/pages/mine/info/edit')
      },

      subscribeRes() {
        this.$tab.navigateTo('/pages/mine/setting/index')
      },
      handleToLogin() {
        this.$tab.reLaunch('/pages/login')
      },
      handleToAvatar() {
        this.$tab.navigateTo('/pages/mine/avatar/index')
      },
      handleLogout() {
        this.$modal.confirm('确定退出登录吗？').then(() => {
          this.$store.dispatch('LogOut').then(() => {
            this.$tab.reLaunch('/pages/mine/index')
          })
        })
      },
      myPoints() {
        this.$tab.navigateTo('/pages/user/points/index')
      }
    
    }
  }
</script>

<style lang="scss">
  page {
    background-color: #f5f6f7;
  }

  .login-btn {
    margin-top: 40px;
    height: 90rpx !important;
    line-height: 90rpx;
    margin-left: 5%;
    width: 90%;
  }

  .mine-container {
    width: 100%;
    height: 100%;


    .header-section {
      padding: 10rpx 20rpx 70rpx;
      background-color: #1ec57c;
      // background-color: #3c96f3;
      color: white;

      .login-tip {
        font-size: 18px;
        margin-left: 10px;
      }

      .cu-avatar {
        // border: 2px solid #eaeaea;
        width: 120rpx;
        height: 120rpx;

        .icon {
          font-size: 40px;
        }
      }

      .user-info {
        margin-left: 15px;

        .u_title {
          font-size: 38rpx;
          margin-bottom: 6rpx;
        }

        .u_title1 {
          font-size: 24rpx;
        }
      }
    }

    .content-section {
      position: relative;
      top: -50px;

      .mine-actions {
        margin: 30rpx 30rpx;
        padding: 40rpx 0 28rpx;
        border-radius: 16rpx;
        background-color: white;

        .action-item {
          .icon {
            font-size: 48rpx;
          }

          .text {
            display: block;
            font-size: 26rpx;
            margin: 16rpx 0;
          }
        }
      }
    }
  }
</style>