import request from '@/utils/request'

export function getIndependentBoardContexts() {
  return request({
    url: '/my/independent-board/contexts',
    method: 'get'
  })
}

export function getIndependentBoardDashboard(tenantId) {
  return request({
    url: '/my/independent-board/dashboard',
    method: 'get',
    params: { tenantId }
  })
}

export function reserveIndependentBoardMeeting(data) {
  return request({
    url: '/my/independent-board/meeting-reservations',
    method: 'post',
    data
  })
}

export function getIndependentBoardMeetingReservation(operationId, tenantId) {
  return request({
    url: `/my/independent-board/meeting-reservations/${encodeURIComponent(operationId)}`,
    method: 'get',
    params: { tenantId }
  })
}
