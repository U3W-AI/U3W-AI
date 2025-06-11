import request from '@/utils/request'
import { parseStrEmpty } from "@/utils/ruoyi";

export function getComment(query) {
  return request({
    url: '/comment/getComment',
    method: 'get',
    params: query
  })
}

export function updateComment(data) {
  return request({
    url: '/comment/updateComment',
    method: 'post',
    data: data
  })
}

export function textFilter(data) {
  return request({
    url: '/comment/textFilter',
    method: 'post',
    data: data
  })


}
