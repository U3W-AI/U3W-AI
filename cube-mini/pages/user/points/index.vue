<template>
	<view  :style="{height: `${windowHeight}px`}">

		<view>
			<view class="top">
				<!-- 时间范围筛选框 -->
				<view class="filter-box">
					<picker mode="date" :value="selectedDateRange.start" :start="minDate" :end="maxDate"
						@change="onStartDateChange">
						<view class="picker">
							{{ selectedDateRange.start }} 至
						</view>
					</picker>
					<picker mode="date" :value="selectedDateRange.end" :start="selectedDateRange.start" :end="maxDate"
						@change="onEndDateChange">
						<view class="picker">
							{{ selectedDateRange.end }}
						</view>
					</picker>
				</view>

				<!-- Tab导航 -->
				<view class="tab-nav">
					<view class="tab-item" v-for="(item, index) in tabItems" :key="index"
						:class="{ active: selectedIndex === index }" @click="onTabItemClick(index)">
						{{ item.name }}
					</view>
				</view>
			</view>
			<!-- 列表 -->

				<scroll-view scroll-y class="list" @scrolltolower="onScrollToLower" refresher-enabled :refresher-triggered="triggered" @refresherpulling="onPulling"
					@refresherrefresh="onRefresh">

					<view class="list-item" v-for="(item, index) in listItems" :key="index">
					
						<!-- <view class="point-text">
							<text v-if="item.corp_name">{{ item.corp_name }}</text>
						</view> -->
						<view class="point-text" style="display:flex;justify-content: space-between;">
						<!-- 	<text>{{ item.nick_name }}</text> -->
						<text>{{ item.change_type }}</text> 
							<text :style="{color:item.change_amount >= 0 ? 'green' : 'red',fontSize: '35rpx'}">{{ item.change_amount }}</text>
						</view>
					
					
						<view class="point-text">
							<!-- <text style="color: #8f8f94">{{ item.change_type }}</text> -->
							<text style="color: #8f8f94">{{ item.create_time }}</text>
						</view>
						


					</view>
					<view v-if="!hasMore" class="end-text">
						已经到底啦~
					</view>
				</scroll-view>
		
		</view>
	</view>
</template>

<script>
	import {
		getUserPointsRecord
	} from '@/api/report'
	import storage from '@/utils/storage'
	import constant from '@/utils/constant'
	export default {
		data() {
			return {
				triggered: false,
				selectedIndex: 0,
				tabItems: [{
						name: '全部'
					},
					{
						name: '积分消费'
					},
					{
						name: '积分充值'
					}
				],
				listItems: [],
				total: '',
				hasMore: true,
				pointsForm: {
					userId: storage.get(constant.userId),
					pageSize: 10,
					pageIndex: 1,
					start: '',
					end: '',
					type: ''
				},
				selectedDateRange: {
					start: '2023-01-01', // 初始开始日期
					end: '2023-01-01' // 初始结束日期
				},
				minDate: '', // 最小日期
				maxDate: '' // 最大日期
			}
		},
		 // 分享到微信好友
		    // onShareAppMessage() {
		    //   return {
		    //     title: '我的积分',
		    //     path: '/pages/user/points/index',
		    //     imageUrl: '',
		    //   }
		    // },
		    // 分享到朋友圈
		    // onShareTimeline() {
		    //   return {
		    //     title: '我的积分',
		    //     path: '/pages/user/points/index',
		    //     imageUrl: '',
		    //   }
		    // },
		created() {
			// wx.showShareMenu({
			// 	withShareTicket: true,
			// 	menus: ['shareAppMessage', 'shareTimeline']
			// 	});
			this.updateDateRanges();
			this.getUserPointsRecord();
		},
		computed: {

			windowHeight() {
				return uni.getSystemInfoSync().windowHeight - 50
			}
		},
		methods: {
			onPulling() {
			            var that = this;
			            if(!this.triggered){
			                //下拉加载，先让其变true再变false才能关闭
			                this.triggered = true; 
			                //关闭加载状态 (转动的圈)，需要一点延时才能关闭
			                setTimeout(() => {
			                    that.triggered = false;
			                },1000)
			            }
			        },
			onRefresh() {
				console.log("下拉刷新");
				this.hasMore = true;
				this.pointsForm.pageIndex = 1;
			getUserPointsRecord(this.pointsForm).then(res => {
						if (res.data && res.data.list) {
							this.listItems = res.data.list;
							uni.stopPullDownRefresh();
							console.log("关闭刷新");
							this.total = res.data.total;
						}
					})
			},
			onScrollToLower() {

				if (this.hasMore) {
					this.loadMore();
				}
			},
			loadMore() {
				if (this.pointsForm.pageIndex * this.pointsForm.pageSize >= this.total) {
					this.hasMore = false;
					return;
				}
				this.pointsForm.pageIndex++;
				this.getUserPointsRecord();
			},
			getUserPointsRecord() {
				getUserPointsRecord(this.pointsForm).then(res => {
					if (res.data && res.data.list) {
						this.listItems = this.listItems.concat(res.data.list);
						this.total = res.data.total;
					}
				})
			},

			updateDateRanges() {
				const today = new Date();
				const minDate = new Date(today);
				minDate.setMonth(today.getMonth() - 1); // 设置为一个月前的今天
				this.minDate = minDate.toISOString().split('T')[0]; // 转换为 YYYY-MM-DD 格式
				this.maxDate = today.toISOString().split('T')[0]; // 转换为 YYYY-MM-DD 格式
				this.selectedDateRange.start = this.minDate;
				this.pointsForm.start = this.minDate;
				this.selectedDateRange.end = this.maxDate;
				this.pointsForm.end = this.maxDate;
			},
			onTabItemClick(index) {
				this.selectedIndex = index;
				
				if(index==1){
					this.pointsForm.type = 2
				}else if(index==2) {
					this.pointsForm.type = 1
				}else {
					this.pointsForm.type = ''
				}
				getUserPointsRecord(this.pointsForm).then(res => {
							if (res.data && res.data.list) {
								this.listItems = res.data.list;
								this.total = res.data.total;
							}
						})
			},
			onStartDateChange(e) {
				this.selectedDateRange.start = e.detail.value;
				// 确保结束日期不早于开始日期
				if (this.selectedDateRange.end < this.selectedDateRange.start) {
					this.selectedDateRange.end = this.selectedDateRange.start;
				}
				this.pointsForm.start = this.selectedDateRange.start;
				// console.log('开始日期:', this.selectedDateRange.start);
			},
			onEndDateChange(e) {
				this.selectedDateRange.end = e.detail.value;
				// 确保开始日期不晚于结束日期
				if (this.selectedDateRange.start > this.selectedDateRange.end) {
					this.selectedDateRange.start = this.selectedDateRange.end;
				}
				this.pointsForm.end = this.selectedDateRange.end;
				// console.log('结束日期:', this.selectedDateRange.end);
			}
		}
	}
</script>

<style lang="scss">
	page {
		background-color: #f8f8f8;
	}

	.end-text {
		text-align: center;
		padding: 20rpx;
		color: #999;
	}



	.filter-box {
		display: flex;
		align-items: center;
		padding: 10rpx;
		background-color: white;
	
	}

	.picker {
		padding: 10rpx;
		background-color: white;
		border-radius: 5rpx;
	}

	.tab-nav {
		display: flex;
		width: 100%;
		justify-content: space-around;
		padding: 10rpx 0;
		background-color: white;
		border-radius: 5rpx;
	}

	.tab-item {
		padding: 10rpx;
		position: relative;
		color: #000;
	}

	.tab-item.active {
		color: royalblue;
	}

	.tab-item.active::after {
		content: '';
		position: absolute;
		bottom: 0;
		left: 50%;
		transform: translateX(-50%);
		width: 50%;
		height: 2rpx;
		background-color: blue;
	}

	.list {
		height: 87vh;
		/* 根据实际情况调整高度 */
		overflow-y: auto;
		/* 确保内容超出时可以滚动 */
		width: 99%;
		padding: 10rpx;
		border-radius: 5rpx;
	}

	.list-item {
		display: flex;
		flex-direction: column;
		margin-bottom: 20rpx;
	    background-color: white;
		border-radius: 10rpx;
	}


	.point-text {
		padding: 30rpx;
	}
</style>