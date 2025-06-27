import config from '@/config'
import storage from '@/utils/storage'
import constant from '@/utils/constant'
import { login, logout, getInfo,wxLogin,qywxLogin } from '@/api/login'
import { getToken, setToken, removeToken } from '@/utils/auth'

const baseUrl = config.baseUrl

const user = {
  state: {
    token: getToken(),
    name: storage.get(constant.name),
	openid: storage.get(constant.openid),
	corpId: storage.get(constant.corpId),
	userId: storage.get(constant.userId),
    avatar: storage.get(constant.avatar),
    roles: storage.get(constant.roles),
    permissions: storage.get(constant.permissions)
  },

  mutations: {
    SET_TOKEN: (state, token) => {
      state.token = token
    },
    SET_NAME: (state, name) => {
      state.name = name
      storage.set(constant.name, name)
    },
	SET_ID: (state, openid) => {
	  state.openid = openid
	  storage.set(constant.openid, openid)
	},
	SET_CORPID: (state, corpId) => {
	  state.corpId = corpId
	  storage.set(constant.corpId, corpId)
	},
	SET_USERID: (state, userId) => {
	  state.userId = userId
	  storage.set(constant.userId, userId)
	},
	SET_USERNAME: (state, username) => {
	  state.username = username
	  storage.set(constant.username, username)
	},
    SET_AVATAR: (state, avatar) => {
      state.avatar = avatar
      storage.set(constant.avatar, avatar)
    },
    SET_ROLES: (state, roles) => {
      state.roles = roles
      storage.set(constant.roles, roles)
    },
    SET_PERMISSIONS: (state, permissions) => {
      state.permissions = permissions
      storage.set(constant.permissions, permissions)
    }
  },

  actions: {
    // 登录
    Login({ commit }, userInfo) {
      const username = userInfo.username.trim()
      const password = userInfo.password
      const code = userInfo.code
      const uuid = userInfo.uuid
      return new Promise((resolve, reject) => {
        login(username, password, code, uuid).then(res => {
          setToken(res.token)
          commit('SET_TOKEN', res.token)
          resolve()
        }).catch(error => {
          reject(error)
        })
      })
    },
	// 微信登录
	WxLogin({ commit }, userInfo) {
	  const code = userInfo.code
	  const encryptedIv = userInfo.encryptedIv
	  const encryptedData = userInfo.encryptedData
	  const nickName = userInfo.nickName
	  const avatar = userInfo.avatar
	  const appId = userInfo.appId
	  const appSecret = userInfo.appSecret
	  return new Promise((resolve, reject) => {
	    wxLogin(code, encryptedIv, encryptedData, nickName, avatar,appId,appSecret).then(res => {
	      setToken(res.token)
	      commit('SET_TOKEN', res.token)
	      resolve()
	    }).catch(error => {
	      reject(error)
	    })
	  })
	},
	// 企业微信登录
	QyWxLogin({ commit }, userInfo) {
	  const code = userInfo.code
	  const qwcode = userInfo.qwcode
	  const appId = userInfo.appId
	  const appSecret = userInfo.appSecret
	  return new Promise((resolve, reject) => {
	    qywxLogin(code,qwcode,appId,appSecret).then(res => {
	      setToken(res.token)
	      commit('SET_TOKEN', res.token)
	      resolve()
	    }).catch(error => {
	      reject(error)
	    })
	  })
	},
    // 获取用户信息
    GetInfo({ commit, state }) {
      return new Promise((resolve, reject) => {
        getInfo().then(res => {
          const user = res.user
          const avatar = (user == null || user.avatar == "" || user.avatar == null) ? require("@/static/images/profile.jpg") : user.avatar
          const username = (user == null || user.userName == "" || user.userName == null) ? "" : user.userName
		  const nickname = (user == null || user.nickName == "" || user.nickName == null) ? "" : user.nickName
		  const userId = (user == null || user.userId == "" || user.userId == null) ? "" : user.userId
		  const corpId = (user == null || user.corpId == "" || user.corpId == null) ? "" : user.corpId
		  console.log("登录成功企业ID",corpId)
          if (res.roles && res.roles.length > 0) {
            commit('SET_ROLES', res.roles)
            commit('SET_PERMISSIONS', res.permissions)
          } else {
            commit('SET_ROLES', ['ROLE_DEFAULT'])
          }
          commit('SET_NAME', nickname)
		  commit('SET_ID', username)
		  commit('SET_CORPID', corpId)
		  commit('SET_USERID', userId)
          commit('SET_AVATAR', avatar)
          resolve(res)
        }).catch(error => {
          reject(error)
        })
      })
    },

    // 退出系统
    LogOut({ commit, state }) {
      return new Promise((resolve, reject) => {
        logout(state.token).then(() => {
          commit('SET_TOKEN', '')
          commit('SET_ROLES', [])
          commit('SET_PERMISSIONS', [])
		  commit('SET_NAME', '')
		  commit('SET_ID', '')
		  commit('SET_AVATAR', '')
          removeToken()
          storage.clean()
          resolve()
        }).catch(error => {
          reject(error)
        })
      })
    }
  }
}

export default user
