// main.js

const resizeObserverErr = 'ResizeObserver loop completed with undelivered notifications.';
const resizeObserverErrRegex = new RegExp(resizeObserverErr, 'i');

window.addEventListener('error', (e) => {
  if (e.message === resizeObserverErr || resizeObserverErrRegex.test(e.message)) {
    e.stopImmediatePropagation();
  }
});

window.addEventListener('unhandledrejection', (e) => {
  if (e.reason && e.reason.message === resizeObserverErr || 
      (e.reason && resizeObserverErrRegex.test(e.reason.message))) {
    e.stopImmediatePropagation();
  }
});

import { createApp } from 'vue'
import Cookies from 'js-cookie'
import ElementPlus from 'element-plus'
import './assets/styles/element-variables.scss'
import '@/assets/styles/index.scss' // global css
import '@/assets/styles/ruoyi.scss' // ruoyi css
import App from './App.vue'
import store from './store'
import router from './router'
import directive from './directive' // directive
import plugins from './plugins' // plugins
import modal from './plugins/modal'
import { download, downloadUtils } from '@/utils/request'
import 'element-plus/dist/index.css'
import '@/assets/icons' // icon
import './permission' // permission control
import { ElMessage, ElMessageBox } from 'element-plus'
import { getDicts } from "@/api/system/dict/data";
import { getConfigKey } from "@/api/system/config";
import { parseTime, resetForm, addDateRange, selectDictLabel, selectDictLabels, handleTree } from "@/utils/ruoyi";
// 分页组件
// 确保在 main.js 中也导入了样式
import 'vue3-treeselect/dist/vue3-treeselect.css'
import Pagination from "@/components/Pagination";
// 自定义表格工具组件
import RightToolbar from "@/components/RightToolbar"
// 富文本组件
import Editor from "@/components/Editor"
// 文件上传组件
import FileUpload from "@/components/FileUpload"
// 图片上传组件
import ImageUpload from "@/components/ImageUpload"
// 图片预览组件
import ImagePreview from "@/components/ImagePreview"
// 字典标签组件
import DictTag from '@/components/DictTag'
// 头部标签组件
import { createHead } from '@vueuse/head'
// 字典数据组件
import DictData from '@/components/DictData'
// SVG 图标组件
import { SvgIcon } from '@/assets/icons'

// 创建应用实例
const app = createApp(App)

// 全局方法挂载 (Vue 3 方式)
app.config.globalProperties.getDicts = getDicts
app.config.globalProperties.getConfigKey = getConfigKey
app.config.globalProperties.parseTime = parseTime
app.config.globalProperties.resetForm = resetForm
app.config.globalProperties.addDateRange = addDateRange
app.config.globalProperties.selectDictLabel = selectDictLabel
app.config.globalProperties.selectDictLabels = selectDictLabels
app.config.globalProperties.download = download
// app.config.globalProperties.$download = downloadUtils
app.config.globalProperties.handleTree = handleTree
app.config.globalProperties.$modal = modal
app.config.globalProperties.$alert = ElMessageBox.alert

// 全局组件挂载
app.component('DictTag', DictTag)
app.component('Pagination', Pagination)
app.component('RightToolbar', RightToolbar)
app.component('Editor', Editor)
app.component('FileUpload', FileUpload)
app.component('ImageUpload', ImageUpload)
app.component('ImagePreview', ImagePreview)
// 注册 SVG 图标组件
app.component('svg-icon', SvgIcon)

// 使用插件
app.use(directive)
app.use(plugins)
app.use(createHead())
DictData.install(app)
app.use(ElementPlus)
// 使用 Element Plus
app.use(ElementPlus, {
  size: Cookies.get('size') || 'medium'
})
app.use(router)
app.use(store)

// 全局清理方法
app.config.globalProperties.$cleanup = () => {
  console.log('[全局清理] 清理所有全局资源');
  // 在这里添加需要清理的全局资源
};

// 挂载应用
app.mount('#app');

// 在开发模式下打印全局方法
if (process.env.NODE_ENV === 'development') {
  console.log('[全局方法]', app.config.globalProperties);
}
// main.js
