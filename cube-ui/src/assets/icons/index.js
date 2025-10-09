// src/assets/icons/index.js
import SvgIcon from '@/components/SvgIcon' // svg component

// 注意：Vue 3 中不能直接使用 Vue.component()，需要在 main.js 中注册

const req = require.context('./svg', false, /\.svg$/)
const requireAll = requireContext => requireContext.keys().map(requireContext)
requireAll(req)

// 导出 SvgIcon 组件，以便在 main.js 中注册
export { SvgIcon }