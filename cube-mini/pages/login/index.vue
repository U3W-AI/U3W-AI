<template>
	<view class="normal-login-container">
		<view class="logo-content align-center justify-center flex">
			<image src="https://ai-public.mastergo.com/ai/img_res/1747114705cd89a0b636d09b6117fc5f.jpg" mode="widthFix">
			</image>
		</view>

		<view class="login-form-content">
			<text>点击下方按钮一键登录</text>
			<view class="action-btn">
				<button @click="wxhandleLogin" class="login-btn cu-btn block bg-green lg round">一键授权登录</button>
			</view>
		</view>

	</view>
</template>

<script>
	import {
		getCodeImg
	} from '@/api/login'

	export default {
		data() {
			return {
				codeUrl: "",
				captchaEnabled: true,
				// 用户注册开关
				register: false,
				globalConfig: getApp().globalData.config,
				wxLoginForm: {
					appId: "",
					appSecret: "",
					code: "",
					encryptedIv: "",
					encryptedData: "",
					nickName: "",
					avatar: ""
				},
				loginForm: {
					username: "",
					password: "",
					code: "",
					uuid: ''
				}
			}
		},
		created() {

		},
		methods: {
			// 用户登录
			async wxhandleLogin() {


				this.$modal.loading("登录中，请耐心等待...")
				wx.getSystemInfo({
					success: (res) => {
						console.log('res:', res)
						if (res.environment) {

							wx.login({
								success: (res) => {
									console.log("微信code" + res.code)

									this.wxLoginForm.code = res.code
									wx.qy.login({
										success: (res) => {
											console.log("企业微信" + JSON.stringify(
												res))
											if (res.code) {
												console.log("企业微信code=" + res.code)
												this.wxLoginForm.qwcode = res.code
												this.sendWxLoginFormToLocalService(
													'qywx')
											} else {
												console.log('登录失败！' + res.errMsg)
											}
										}
									});

								}
							});



						} else {
							uni.getUserProfile({
								lang: 'zh_CN',
								desc: '用于完善会员资料',
								success: (user) => {
									console.log("用于完善会员资料" + JSON.stringify(user))
									this.wxLoginForm.nickName = user.userInfo.nickName
									this.wxLoginForm.avatar = user.userInfo.avatarUrl
									uni.getProvider({
										service: 'oauth',
										success: (res) => {
											if (~res.provider.indexOf("weixin")) {
												uni.login({
													provider: "weixin",
													success: (
														loginRes) => {
														this.wxLoginForm
															.code =
															loginRes
															.code
														uni.getUserInfo({
															success: (
																infoRes
															) => {
																this.wxLoginForm
																	.encryptedIv =
																	infoRes
																	.iv
																this.wxLoginForm
																	.encryptedData =
																	infoRes
																	.encryptedData
																this.sendWxLoginFormToLocalService(
																	'wx'
																)
															}
														})
													}
												})
											}
										}
									})
								},
								fail(res) {

								}
							})

						}

					}
				})


			},
			sendWxLoginFormToLocalService(env) {
				console.log("当前环境" + env)
				if (env == 'wx') {
					this.$store.dispatch('WxLogin', this.wxLoginForm).then(() => {
						this.$modal.closeLoading()
						this.loginSuccess()
						console.log('登录成功')
					}).catch(() => {
						console.log('登录失败')
					})
				} else if (env == 'qywx') {
					this.$store.dispatch('QyWxLogin', this.wxLoginForm).then(() => {
						this.$modal.closeLoading()
						this.loginSuccess()
						console.log('企业微信登录成功')
					}).catch(() => {
						console.log('企业微信登录失败')
					})
				}

			},
			// 登录成功后，处理函数
			loginSuccess(result) {
				this.$store.dispatch('GetInfo').then(res => {

					// 获取当前页面的路径
					const currentPagePath = getCurrentPages()[getCurrentPages().length - 2].route;
                    console.log('当前页面：'+currentPagePath)
					uni.navigateBack({
						delta: 1,
						success: function(res) {
							uni.reLaunch({
								url: "/" + currentPagePath, // 这里指定你希望重新加载的页面路径
								success: function(res) {
									console.log('小程序重新加载成功');
								},
								fail: function(err) {
									console.log('小程序重新加载失败', err);
								}
							});
						},
						fail: function(err) {
							console.log('返回失败', err);
						}
					});
				})

			},
			// 隐私协议
			handlePrivacy() {
				let site = this.globalConfig.appInfo.agreements[0]
				this.$tab.navigateTo(`/pages/common/webview/index?title=${site.title}&url=${site.url}`)
			},
			// 用户协议
			handleUserAgrement() {
				let site = this.globalConfig.appInfo.agreements[1]
				this.$tab.navigateTo(`/pages/common/webview/index?title=${site.title}&url=${site.url}`)
			},



		}
	}
</script>

<style lang="scss">
	page {
		background-color: #ffffff;
	}

	.normal-login-container {
		width: 100%;

		.logo-content {
			width: 100%;
			font-size: 21px;
			text-align: center;
			padding-top: 15%;

			image {
				border-radius: 4px;
			}

			.title {
				margin-left: 10px;
			}
		}

		.login-form-content {
			text-align: center;
			margin: 20px auto;
			margin-top: 15%;
			width: 80%;

			.input-item {
				margin: 20px auto;
				background-color: #f5f6f7;
				height: 45px;
				border-radius: 20px;

				.icon {
					font-size: 38rpx;
					margin-left: 10px;
					color: #999;
				}

				.input {
					width: 100%;
					font-size: 14px;
					line-height: 20px;
					text-align: left;
					padding-left: 15px;
				}

			}

			.login-btn {
				margin-top: 80rpx;
				height: 90rpx !important;
			}

			.reg {
				margin-top: 15px;
			}

			.xieyi {
				color: #333;
				margin-top: 20px;
			}

			.login-code {
				height: 38px;
				float: right;

				.login-code-img {
					height: 38px;
					position: absolute;
					margin-left: 10px;
					width: 200rpx;
				}
			}
		}
	}
</style>