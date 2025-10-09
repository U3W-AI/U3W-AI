// 应用全局配置
module.exports = {
	//生产
	baseUrl: 'http://localhost:8081',
	


	// 应用信息
	appInfo: {
		// true: 流式接口    false: 非流式接口
		isNonFlow: true,
		// 应用名称
		name: "cube-mini",
		// 应用版本
		version: "1.0.0",
		// 应用logo
		logo: "/static/logo.png",
		// 官方网站
		site_url: "https://u3w.com/",
		// 政策协议
		agreements: [{
				title: "隐私政策",
				url: ""
			},
			{
				title: "用户服务协议",
				url: ""
			}
		]
	}
}