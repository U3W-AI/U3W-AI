import request from "@/utils/request"

export function getGiteeStatus() {
  return request({
    url: "/business/gitee/status",
    method: "get"
  })
}

export function getGiteeAuthorizeUrl(params = {}) {
  return request({
    url: "/business/gitee/authorize",
    method: "get",
    params
  })
}

export function fetchGiteeProfile() {
  return request({
    url: "/business/gitee/profile",
    method: "get"
  })
}

export function fetchGiteeRepos(params = {}) {
  return request({
    url: "/business/gitee/repos",
    method: "get",
    params
  })
}

export function fetchGiteeIssues(params = {}) {
  return request({
    url: "/business/gitee/issues",
    method: "get",
    params
  })
}

export function fetchGiteeNotifications(params = {}) {
  return request({
    url: "/business/gitee/notifications",
    method: "get",
    params
  })
}

export function unbindGitee() {
  return request({
    url: "/business/gitee/unbind",
    method: "post"
  })
}

export function reevaluateGiteeAnalysis() {
  return request({
    url: "/business/gitee/analysis/reevaluate",
    method: "post"
  })
}

export function saveGiteeAnalysisReport(data) {
  return request({
    url: "/business/gitee/analysis/report",
    method: "post",
    data
  })

}

export function checkResumeExists() {
  return request({
    url: "/resume/exists",
    method: "get"
  })
}

export function uploadResume(file) {
  const formData = new FormData()
  formData.append("file", file)
  return request({
    url: "/resume/upload",
    method: "post",
    data: formData,
    headers: {
      "Content-Type": "multipart/form-data"
    }
  })
}

export function updateResume(file) {
  const formData = new FormData()
  formData.append("file", file)
  return request({
    url: "/resume/updata",
    method: "post",
    data: formData,
    headers: {
      "Content-Type": "multipart/form-data"
    }
  })
}