import store from '@/store'
import config from '@/config'
import logo from '@/log'

import { getToken } from '@/utils/auth'
import errorCode from '@/utils/errorCode'
import { toast, showConfirm, tansParams } from '@/utils/common'

let timeout = 3000000
const baseUrl = config.baseUrl

const request = config => {
  // 是否需要设置 token
  const isToken = (config.headers || {}).isToken === false
  config.header = config.header || {}
  if (getToken() && !isToken) {
    config.header['Authorization'] = 'Bearer ' + getToken()
  }
  // get请求映射params参数
  if (config.params) {
    let url = config.url + '?' + tansParams(config.params)
    url = url.slice(0, -1)
    config.url = url
  }
  return new Promise((resolve, reject) => {
    uni.request({
        method: config.method || 'get',
        timeout: config.timeout ||  timeout,
        url: config.baseUrl || baseUrl + config.url,
        data: config.data,
        header: config.header,
        dataType: 'json'
      }).then(response => {
        let [error, res] = response
        if (error) {
		  logo.error("请求参数："+JSON.stringify(config)+"返回参数"+JSON.stringify(res));
          toast('后端接口连接异常')
          reject('后端接口连接异常')
          return
        }
		
        const code = res.data.code || 200
        const msg = errorCode[code] || res.data.msg || res.data.messages|| errorCode['default']
        if (code === 401) {
		  logo.error("请求参数："+JSON.stringify(config)+"返回参数"+JSON.stringify(res));
          showConfirm('登录状态已过期，您可以继续留在该页面，或者重新登录?').then(res => {
            if (res.confirm) {
              store.dispatch('LogOut').then(res => {
                uni.reLaunch({ url: 'pages/mine/index' })
              })
            }
          })
          reject('无效的会话，或者会话已过期，请重新登录。')
        } else if (code === 500) {
		logo.error("请求参数："+JSON.stringify(config)+"返回参数"+JSON.stringify(res));
          toast(msg)
          reject('500')
        } else if (code !== 200 && code != 201) {
		 //logo.info("请求参数："+JSON.stringify(res.data)+"返回参数"JSON.stringify(res.data));
          toast(msg)
          reject(code)
        }
        resolve(res.data)
      })
      .catch(error => {
        let { message } = error
        if (message === 'Network Error') {
		  logo.error("请求参数："+JSON.stringify(config)+"返回参数"+JSON.stringify(res));
          message = '后端接口连接异常'
        } else if (message.includes('timeout')) {
		  logo.error("请求参数："+JSON.stringify(config)+"返回参数"+JSON.stringify(res));
          message = '系统接口请求超时'
        } else if (message.includes('Request failed with status code')) {
		 logo.error("请求参数："+JSON.stringify(config)+"返回参数"+JSON.stringify(res));
          message = '系统接口' + message.substr(message.length - 3) + '异常'
        }
        toast(message)
        reject(error)
      })
  })
}

export default request
