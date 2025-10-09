import { getToken } from '@/utils/auth'

// 登录页面
const loginPage = "/pages/login/index"
  
// 页面白名单
const whiteList = [
  '/pages/index','/pages/mine/index',"/pages/login/index","/pages/mine/about/index", '/pages/common/webview/index'
]

// 检查地址白名单
function checkWhite(url) {
  const path = url.split('?')[0]
  return whiteList.indexOf(path) !== -1
}

// 页面跳转验证拦截器
let list = ["navigateTo", "redirectTo", "reLaunch", "switchTab"]
list.forEach(item => {
  uni.addInterceptor(item, {
    invoke(to) {
      if (getToken()) {
		  console.log("12323")
        if (to.url === loginPage) {
          uni.reLaunch({ url: "/" })
        }
        return true
      } else {
		  console.log(checkWhite(to.url))
        if (checkWhite(to.url)) {
          return true
        }
        uni.navigateTo({
                url: '/pages/login/index'
            });
        return false
      }
    },
    fail(err) {
      console.log(err)
    }
  })
})
