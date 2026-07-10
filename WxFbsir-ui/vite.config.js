import { defineConfig, loadEnv } from 'vite'
import path from 'path'
import createVitePlugins from './vite/plugins'
import { fileURLToPath } from 'url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))

const defaultProxyTarget = 'http://localhost:8080'

const createApiProxy = (prefix, target) => ({
  target,
  changeOrigin: true,
  ws: true,
  rewrite: (requestPath) => requestPath.replace(new RegExp(`^${prefix}`), '')
})

// https://vitejs.dev/config/
export default defineConfig(({ mode, command }) => {
  const env = loadEnv(mode, process.cwd())
  const { VITE_APP_ENV } = env
  const proxyTarget = env.VITE_APP_PROXY_TARGET || defaultProxyTarget
  const springDocProxy = {
    target: proxyTarget,
    changeOrigin: true
  }
  return {
    // 部署生产环境和开发环境下的URL。
    // 默认情况下，vite 会假设你的应用是被部署在一个域名的根路径上
    // 例如 https://wx.wxfbsir.com/。如果应用被部署在一个子路径上，你就需要用这个选项指定这个子路径。例如，如果你的应用被部署在 https://wx.wxfbsir.com/admin/，则设置 baseUrl 为 /admin/。
    base: VITE_APP_ENV === 'production' ? '/' : '/',
    plugins: createVitePlugins(env, command === 'build'),
    resolve: {
      // https://cn.vitejs.dev/config/#resolve-alias
      alias: {
        // 设置路径
        '~': path.resolve(__dirname, './'),
        // 设置别名
        '@': path.resolve(__dirname, './src')
      },
      // https://cn.vitejs.dev/config/#resolve-extensions
      extensions: ['.mjs', '.js', '.ts', '.jsx', '.tsx', '.json', '.vue']
    },
    // 打包配置
    build: {
      // https://vite.dev/config/build-options.html
      sourcemap: command === 'build' ? false : 'inline',
      outDir: 'dist',
      assetsDir: 'static',
      chunkSizeWarningLimit: 1000,
      minify: 'esbuild',
      rollupOptions: {
        output: {
          chunkFileNames: 'static/js/[name]-[hash].js',
          entryFileNames: 'static/js/[name]-[hash].js',
          assetFileNames: 'static/[ext]/[name]-[hash].[ext]',
          // 代码分割配置
          manualChunks: {
            // 将Vue相关库分离
            'vue': ['vue', 'vue-router', 'pinia'],
            // 将Element Plus分离
            'element-plus': ['element-plus', '@element-plus/icons-vue'],
            // 将echarts分离
            'echarts': ['echarts'],
            // 将其他大型库分离
            'utils': ['axios', 'js-cookie', 'nprogress', 'clipboard', 'fuse.js'],
            // 将quill编辑器分离
            'quill': ['@vueup/vue-quill']
          }
        }
      }
    },
    // vite 相关配置
    server: {
      port: 80,
      host: true,
      open: true,
      proxy: {
        // https://cn.vitejs.dev/config/#server-proxy
        '/dev-api': createApiProxy('/dev-api', proxyTarget),
        // springdoc proxy
        '^/v3/api-docs/(.*)': springDocProxy
      }
    },
    // `vite preview` 用于本地验收生产/预发布构建，必须保留与部署网关一致的 API 前缀。
    preview: {
      proxy: {
        '/dev-api': createApiProxy('/dev-api', proxyTarget),
        '/prod-api': createApiProxy('/prod-api', proxyTarget),
        '/stage-api': createApiProxy('/stage-api', proxyTarget),
        '^/v3/api-docs/(.*)': springDocProxy
      }
    },
    css: {
      postcss: {
        plugins: [
          {
            postcssPlugin: 'internal:charset-removal',
            AtRule: {
              charset: (atRule) => {
                if (atRule.name === 'charset') {
                  atRule.remove()
                }
              }
            }
          }
        ]
      }
    }
  }
})
