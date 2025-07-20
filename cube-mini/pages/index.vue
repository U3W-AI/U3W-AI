<template>
	<view class="container">
		<view class="swiper-container">
			<swiper class="swiper" circular :indicator-dots="true" :autoplay="true" interval="3000" duration="500">
				<swiper-item v-for="(item, index) in banners" :key="index">
					<image :src="item.image" mode="aspectFill" class="swiper-image" />
				</swiper-item>
			</swiper>
		</view>
		<scroll-view class="content" scroll-y>
			<view class="section">
				<view class="section-title" style="margin-left: 150rpx;">
					<uni-icons type="star" size="24" color="#4169E1" />
					<text class="title-text">优立方AI主机</text>
				</view>
				<view class="feature-cards">
					<view class="feature-card" v-for="(feature, index) in features" :key="index">
						<image :src="feature.icon" mode="aspectFit" class="feature-icon" />
						<text class="feature-title">{{ feature.title }}</text>
						<text class="feature-desc">{{ feature.description }}</text>
					</view>
				</view>
			</view>
			<view class="section">
				<!-- <view class="section-title">
					<uni-icons type="medal" size="24" color="#4169E1" />
					<text class="title-text">系统优势</text>
				</view> -->
				<view class="advantage-list">
					<view class="advantage-item" v-for="(advantage, index) in advantages" :key="index">
						<view class="advantage-icon-wrapper">
							<uni-icons :type="advantage.icon" size="24" color="#4169E1" />
						</view>
						<view class="advantage-content">
							<text class="advantage-title">{{ advantage.title }}</text>
							<text class="advantage-desc">{{ advantage.description }}</text>
						</view>
					</view>
				</view>
			</view>
			<view class="section">
				<!-- <view class="section-title">
					<uni-icons type="gear" size="24" color="#4169E1" />
					<text class="title-text">应用场景</text>
				</view> -->
				<view class="scenario-grid">
					<view class="scenario-card" v-for="(scenario, index) in scenarios" :key="index">
						<image :src="scenario.image" mode="aspectFill" class="scenario-image" />
						<view class="scenario-info">
							<text class="scenario-title">{{ scenario.title }}</text>
							<text class="scenario-desc">{{ scenario.description }}</text>
						</view>
					</view>
				</view>
			</view>
		</scroll-view>
	</view>
</template>
<script>
	export default {
		data() {
			return {
				banners: [{
						image: 'https://ai-public.mastergo.com/ai/img_res/1747114705cd89a0b636d09b6117fc5f.jpg'
					},
					{
						image: 'https://ai-public.mastergo.com/ai/img_res/21749e007ca9c553e43e57e2ca755cbb.jpg'
					},
					{
						image: 'https://ai-public.mastergo.com/ai/img_res/ea68981430e1307646a14e900ce6d3e6.jpg'
					}
				],
				features: [{
						name: '豆包',
						avatar: 'https://u3w.com/chatfile/%E8%B1%86%E5%8C%85.png',
						icon: 'https://u3w.com/chatfile/%E8%B1%86%E5%8C%85.png',
						title: '豆包',
						description: '字节跳动开发的AI助手，擅长深度思考和逻辑推理',
						type: 'doubao'
					},
					{
						name: 'DeepSeek',
						avatar: 'https://u3w.com/chatfile/Deepseek.png',
						icon: 'https://u3w.com/chatfile/Deepseek.png',
						title: 'DeepSeek',
						description: 'DeepSeek原生AI助手，擅长深度思考和联网搜索',
						type: 'doubao'
					},
					{
						name: 'MiniMax Chat',
						avatar: 'https://yzg.meooota.com/profile/MiniMax%20Chat.png',
						icon: 'https://yzg.meooota.com/profile/MiniMax%20Chat.png',
						title: 'MiniMax Chat',
						description: 'MiniMax ChatAI助手，提供专业、严谨的解答。',
						type: 'mini'
					}
				],
				advantages: [
					// {
					// 	icon: 'star-filled',
					// 	title: '多模型协同',
					// 	description: '整合多个AI模型，实现优势互补，提供更全面的解决方案'
					// },
					// {
					// 	icon: 'refresh',
					// 	title: '实时响应',
					// 	description: '快速处理请求，毫秒级响应，提供流畅的用户体验'
					// },
					// {
					// 	icon: 'shield',
					// 	title: '安全可靠',
					// 	description: '采用先进的安全防护措施，保障数据和隐私安全'
					// },
					// {
					// 	icon: 'staff',
					// 	title: '专业支持',
					// 	description: '24小时专业团队支持，解决您的使用疑难'
					// }
				],
				scenarios: [
					// {
					// 	image: 'https://ai-public.mastergo.com/ai/img_res/3460a8269aff64d63683572370ab0b59.jpg',
					// 	title: '企业管理',
					// 	description: '智能化办公，提升管理效率'
					// },
					// {
					// 	image: 'https://ai-public.mastergo.com/ai/img_res/038be95571253851dbc71264b8a0dc96.jpg',
					// 	title: '金融分析',
					// 	description: '精准的市场分析和预测'
					// },
					// {
					// 	image: 'https://ai-public.mastergo.com/ai/img_res/0fa10d1093ca0600699e733d035a67ba.jpg',
					// 	title: '数据研究',
					// 	description: '深度挖掘数据价值'
					// }
				]
			}
		}
	}
</script>
<style>
	page {
		height: 100%;
	}

	.container {
		display: flex;
		flex-direction: column;
		height: 100%;
		background-color: #F5F7FA;
	}

	.swiper-container {
		flex-shrink: 0;
		height: 400rpx;
		background-color: #fff;
	}

	.swiper {
		width: 100%;
		height: 100%;
	}

	.swiper-image {
		width: 100%;
		height: 100%;
	}

	.content {
		flex: 1;
		overflow: auto;
	}

	.section {
		margin: 30rpx 20rpx;
		background: linear-gradient(135deg, #ffffff 0%, #fafbff 100%);
		border-radius: 24rpx;
		padding: 50rpx 40rpx;
		box-shadow:
			0 8rpx 32rpx rgba(0, 0, 0, 0.04),
			0 2rpx 16rpx rgba(0, 0, 0, 0.02);
		border: 1rpx solid rgba(255, 255, 255, 0.8);
		position: relative;
		overflow: hidden;
	}

	.section::before {
		content: '';
		position: absolute;
		top: 0;
		left: 0;
		right: 0;
		height: 3rpx;
		background: linear-gradient(90deg, #667eea 0%, #764ba2 100%);
		opacity: 0.6;
	}

	.section-title {
		display: flex;
		align-items: center;
		margin-bottom: 50rpx;
		position: relative;
		z-index: 2;
	}

	.title-text {
		margin-left: 20rpx;
		font-size: 22px;
		font-weight: 700;
		color: #2d3748;
		letter-spacing: 0.8px;
		position: relative;
	}

	.title-text::after {
		content: '';
		position: absolute;
		bottom: -8rpx;
		left: 0;
		width: 60rpx;
		height: 4rpx;
		background: linear-gradient(90deg, #667eea 0%, #764ba2 100%);
		border-radius: 2rpx;
		opacity: 0.7;
	}

	.feature-cards {
		display: grid;
		grid-template-columns: repeat(auto-fit, minmax(280rpx, 1fr));
		gap: 30rpx;
		margin-top: 20rpx;
	}

	.feature-card {
		background: linear-gradient(145deg, #ffffff 0%, #f8faff 100%);
		padding: 40rpx 30rpx 35rpx;
		border-radius: 20rpx;
		display: flex;
		flex-direction: column;
		align-items: center;
		position: relative;
		overflow: hidden;
		box-shadow:
			0 8rpx 24rpx rgba(0, 0, 0, 0.06),
			0 2rpx 8rpx rgba(0, 0, 0, 0.04);
		border: 1rpx solid rgba(255, 255, 255, 0.8);
		transition: all 0.3s cubic-bezier(0.25, 0.46, 0.45, 0.94);
	}

	.feature-card::before {
		content: '';
		position: absolute;
		top: 0;
		left: 0;
		right: 0;
		height: 6rpx;
		background: linear-gradient(90deg, #667eea 0%, #764ba2 100%);
		opacity: 0.8;
	}

	.feature-card::after {
		content: '';
		position: absolute;
		top: -50%;
		left: -50%;
		width: 200%;
		height: 200%;
		background: radial-gradient(circle, rgba(102, 126, 234, 0.03) 0%, transparent 70%);
		opacity: 0;
		transition: opacity 0.4s ease;
	}

	.feature-card:hover {
		transform: translateY(-8rpx) scale(1.01);
		box-shadow:
			0 16rpx 32rpx rgba(102, 126, 234, 0.12),
			0 4rpx 16rpx rgba(0, 0, 0, 0.08);
		border-color: rgba(102, 126, 234, 0.15);
	}

	.feature-card:hover::after {
		opacity: 1;
	}

	.feature-icon {
		width: 72rpx;
		height: 72rpx;
		margin-bottom: 24rpx;
		border-radius: 14rpx;
		box-shadow: 0 4rpx 12rpx rgba(0, 0, 0, 0.08);
		transition: all 0.3s ease;
		position: relative;
		z-index: 2;
	}

	.feature-card:hover .feature-icon {
		transform: translateY(-4rpx) scale(1.05);
		box-shadow: 0 8rpx 20rpx rgba(102, 126, 234, 0.15);
	}

	.feature-title {
		font-size: 16px;
		font-weight: 600;
		color: #2d3748;
		margin-bottom: 12rpx;
		letter-spacing: 0.3px;
		text-align: center;
		position: relative;
		z-index: 2;
		line-height: 1.3;
	}

	.feature-desc {
		font-size: 13px;
		color: #64748b;
		text-align: center;
		line-height: 1.6;
		max-width: 220rpx;
		position: relative;
		z-index: 2;
		opacity: 0.85;
	}

	.advantage-list {
		display: flex;
		flex-direction: column;
		gap: 20rpx;
	}

	.advantage-item {
		display: flex;
		align-items: flex-start;
		padding: 20rpx;
		background-color: #F8F9FF;
		border-radius: 12rpx;
	}

	.advantage-icon-wrapper {
		flex-shrink: 0;
		width: 80rpx;
		height: 80rpx;
		display: flex;
		align-items: center;
		justify-content: center;
		background-color: #E6EFFF;
		border-radius: 50%;
	}

	.advantage-content {
		margin-left: 20rpx;
	}

	.advantage-title {
		font-size: 16px;
		font-weight: bold;
		color: #333;
		margin-bottom: 8rpx;
	}

	.advantage-desc {
		font-size: 14px;
		color: #666;
	}

	.scenario-grid {
		display: flex;
		flex-wrap: wrap;
		gap: 20rpx;
	}

	.scenario-card {
		flex: 1;
		min-width: 280rpx;
		background-color: #fff;
		border-radius: 12rpx;
		overflow: hidden;
		box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
	}

	.scenario-image {
		width: 100%;
		height: 200rpx;
	}

	.scenario-info {
		padding: 20rpx;
	}

	.scenario-title {
		font-size: 16px;
		font-weight: bold;
		color: #333;
		margin-bottom: 8rpx;
	}

	.scenario-desc {
		font-size: 14px;
		color: #666;
	}
</style>
