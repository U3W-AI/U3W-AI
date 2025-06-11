import request from '@/utils/request'
import { parseStrEmpty } from "@/utils/ruoyi";

export function getReportList(query) {
  return request({
    url: '/res/getReportList',
    method: 'get',
    params: query
  })
}
export function getResOpeData(query) {
  return request({
    url: '/res/getResOpeData',
    method: 'get',
    params: query
  })
}

export function getReportDetail(resId) {
  return request({
    url: '/res/getReportDetail?resId='+resId,
    method: 'get'
  })
}


export function addReport(data) {
  return request({
    url: '/res/addReport',
    method: 'post',
    data: data
  })
}
export function updateReport(data) {
  return request({
    url: '/res/updateReport',
    method: 'post',
    data: data
  })
}
export function changeResportFlowStatus(data) {
  return request({
    url: '/res/changeResportFlowStatus',
    method: 'post',
    data: data
  })
}
