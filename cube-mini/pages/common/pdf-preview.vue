<template>
	<view class="pdf-preview-container">
		<!-- 头部导航 -->
		<view class="pdf-header">
			<view class="back-btn" @tap="goBack">
				<text class="back-icon">←</text>
				<text class="back-text">返回</text>
			</view>
			<text class="pdf-title">PDF预览</text>
			<view class="header-actions">
				<view class="action-btn" @tap="copyPdfUrl">
					<text class="action-text">复制链接</text>
				</view>
			</view>
		</view>
		
		<!-- PDF内容区域 -->
		<view class="pdf-content">
			<!-- 使用web-view加载PDF -->
			<web-view 
				v-if="pdfUrl" 
				:src="pdfViewerUrl" 
				class="pdf-webview"
				@message="handleMessage"
				@error="handleError">
			</web-view>
			
			<!-- 加载状态 -->
			<view v-if="!pdfUrl" class="loading-container">
				<view class="loading-icon">📄</view>
				<text class="loading-text">正在加载PDF...</text>
			</view>
			
			<!-- 错误状态 -->
			<view v-if="loadError" class="error-container">
				<view class="error-icon">❌</view>
				<text class="error-text">PDF加载失败</text>
				<button class="retry-btn" @tap="retryLoad">重试</button>
				<button class="fallback-btn" @tap="openExternal">在浏览器中打开</button>
			</view>
		</view>
	</view>
</template>

<script>
export default {
	name: 'PdfPreview',
	data() {
		return {
			pdfUrl: '',
			loadError: false
		};
	},
	
	computed: {
		// 使用Google Docs或其他PDF查看器来预览PDF
		pdfViewerUrl() {
			if (!this.pdfUrl) return '';
			
			// 方案1：使用Google Docs Viewer（推荐）
			return `https://docs.google.com/viewer?url=${encodeURIComponent(this.pdfUrl)}&embedded=true`;
			
			// 方案2：使用微软的PDF查看器
			// return `https://view.officeapps.live.com/op/embed.aspx?src=${encodeURIComponent(this.pdfUrl)}`;
			
			// 方案3：直接加载PDF（某些浏览器支持）
			// return this.pdfUrl;
		}
	},
	
	onLoad(options) {
		console.log('PDF预览页面加载，参数:', options);
		if (options.url) {
			this.pdfUrl = decodeURIComponent(options.url);
			console.log('PDF URL:', this.pdfUrl);
		} else {
			uni.showToast({
				title: 'PDF链接无效',
				icon: 'none'
			});
		}
	},
	
	methods: {
		goBack() {
			uni.navigateBack();
		},
		
		copyPdfUrl() {
			if (this.pdfUrl) {
				uni.setClipboardData({
					data: this.pdfUrl,
					success: () => {
						uni.showToast({
							title: 'PDF链接已复制',
							icon: 'success'
						});
					}
				});
			}
		},
		
		handleMessage(event) {
			console.log('Web-view消息:', event);
		},
		
		handleError(event) {
			console.error('Web-view加载错误:', event);
			this.loadError = true;
			uni.showToast({
				title: 'PDF加载失败',
				icon: 'none'
			});
		},
		
		retryLoad() {
			this.loadError = false;
			// 强制重新加载
			this.$forceUpdate();
		},
		
		openExternal() {
			// 复制链接供用户在外部浏览器打开
			uni.setClipboardData({
				data: this.pdfUrl,
				success: () => {
					uni.showModal({
						title: '提示',
						content: 'PDF链接已复制到剪贴板，请在浏览器中粘贴打开',
						showCancel: false,
						confirmText: '知道了'
					});
				}
			});
		}
	}
};
</script>

<style scoped>
.pdf-preview-container {
	height: 100vh;
	display: flex;
	flex-direction: column;
	background-color: #f5f5f5;
}

/* 头部导航 */
.pdf-header {
	display: flex;
	justify-content: space-between;
	align-items: center;
	padding: 10px 15px;
	padding-top: calc(10px + var(--status-bar-height));
	background-color: #fff;
	border-bottom: 1px solid #ebeef5;
	position: sticky;
	top: 0;
	z-index: 999;
}

.back-btn {
	display: flex;
	align-items: center;
	gap: 5px;
}

.back-icon {
	font-size: 18px;
	color: #409EFF;
}

.back-text {
	font-size: 16px;
	color: #409EFF;
}

.pdf-title {
	font-size: 18px;
	font-weight: 600;
	color: #303133;
}

.header-actions {
	display: flex;
	gap: 10px;
}

.action-btn {
	padding: 6px 12px;
	background-color: #f0f8ff;
	border-radius: 16px;
	border: 1px solid #409EFF;
}

.action-text {
	font-size: 12px;
	color: #409EFF;
}

/* PDF内容区域 */
.pdf-content {
	flex: 1;
	position: relative;
}

.pdf-webview {
	width: 100%;
	height: 100%;
}

/* 加载状态 */
.loading-container,
.error-container {
	position: absolute;
	top: 50%;
	left: 50%;
	transform: translate(-50%, -50%);
	display: flex;
	flex-direction: column;
	align-items: center;
	gap: 15px;
}

.loading-icon,
.error-icon {
	font-size: 48px;
}

.loading-text,
.error-text {
	font-size: 16px;
	color: #606266;
}

.retry-btn,
.fallback-btn {
	padding: 8px 16px;
	background-color: #409EFF;
	color: #fff;
	border: none;
	border-radius: 20px;
	font-size: 14px;
}

.fallback-btn {
	background-color: #67c23a;
	margin-top: 5px;
}
</style> 