import request from '@/utils/request'

// 查询我的权益列表
export function listMyPacks(query) {
  return request({
    url: '/my/packs',
    method: 'get',
    params: query
  })
}

// 激活授权码
export function activateAuthCode(data) {
  return request({
    url: '/my/auth-code/activate',
    method: 'post',
    data: data
  })
}

// 查询可领取场景包列表
export function listClaimablePacks(query) {
  return request({
    url: '/my/scene-packs',
    method: 'get',
    params: query
  })
}

// 领取场景包
export function claimScenePack(data) {
  return request({
    url: '/my/scene-pack/claim',
    method: 'post',
    data: data
  })
}
