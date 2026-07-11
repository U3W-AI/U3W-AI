import compression from 'vite-plugin-compression'

export default function createCompression(env) {
    const { VITE_BUILD_COMPRESS } = env
    const plugin = []
    if (VITE_BUILD_COMPRESS) {
        const compressList = VITE_BUILD_COMPRESS.split(',')
        if (compressList.includes('gzip')) {
            // http://doc.ruoyi.vip/ruoyi-vue/other/faq.html#使用gzip解压缩静态文件
            plugin.push(
                compression({
                    ext: '.gz',
                    deleteOriginFile: false,  // 保留原文件，Nginx会根据浏览器支持情况返回相应版本
                    threshold: 10240,  // 只压缩大于10KB的文件
                    algorithm: 'gzip',
                    compressionOptions: {
                        level: 9  // 最高压缩级别
                    }
                })
            )
        }
        if (compressList.includes('brotli')) {
            plugin.push(
                compression({
                    ext: '.br',
                    algorithm: 'brotliCompress',
                    deleteOriginFile: false,  // 保留原文件
                    threshold: 10240,  // 只压缩大于10KB的文件
                    compressionOptions: {
                        level: 11  // brotli最高压缩级别
                    }
                })
            )
        }
    }
    return plugin
}
