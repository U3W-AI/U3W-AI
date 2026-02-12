<template>
  <div class="gitee-profile">
    <el-alert
      title="Gitee授权状态"
      type="info"
      :closable="false"
      class="gitee-alert"
    >
      <template #default>
        <div class="gitee-status">
          <el-tag :type="authorized ? 'success' : 'warning'" effect="plain">
            {{ authorized ? "已授权" : "未授权" }}
          </el-tag>
          <span class="gitee-status-text">
            {{ authorized ? "已获取 Gitee 授权，可直接拉取资料数据。" : "尚未授权，请点击授权按钮完成绑定。" }}
          </span>
          <el-button
            type="primary"
            :loading="authorizing"
            @click="handleAuthorize"
          >
            {{ authorized ? "重新授权" : "使用 Gitee 授权" }}
          </el-button>
          <el-button
            type="success"
            :disabled="!authorized"
            :loading="loadingAll"
            @click="handleRefreshAll"
          >
            一键刷新
          </el-button>
          <el-button
            type="danger"
            :disabled="!authorized"
            :loading="unbinding"
            @click="handleUnbind"
          >
            解除绑定
          </el-button>
        </div>
      </template>
    </el-alert>

    <el-alert
        title="个人简历"
        type="info"
        :closable="false"
        class="gitee-alert"
    >
      <template #default>
        <div class="gitee-status">
          <el-tag :type="resumeExists ? 'success' : 'warning'" effect="plain">
            {{ resumeExists ? "已上传" : "未上传" }}
          </el-tag>
          <span class="gitee-status-text">
            {{ resumeExists ? "可在「Gitee 分析」中查看访问与使用数据" : "简历上传后，可在「Gitee 分析」中查看访问与使用数据" }}
          </span>
          <el-button
              type="primary"
              :loading="uploadingResume"
              @click="resumeExists ? handleUpdateResume() : handleUploadResume()"
          >
            {{ resumeExists ? "更新简历" : "上传简历" }}
          </el-button>
        </div>
      </template>
    </el-alert>
    <input
        ref="fileInput"
        type="file"
        style="display: none"
        accept=".pdf,.doc,.docx"
        @change="handleFileChange"
    >

    <el-card class="gitee-card">
      <template #header>
        <div class="gitee-card-header">
          <span>授权用户资料</span>
          <div class="gitee-card-actions">
            <el-button
              size="small"
              :disabled="!authorized"
              :loading="loadingProfile"
              @click="fetchProfile"
            >
              刷新资料
            </el-button>
            <el-button size="small" :disabled="!profile?.html_url" @click="openProfile">
              打开主页
            </el-button>
            <el-button size="small" :disabled="!profile?.html_url" @click="copyProfileUrl">
              复制主页链接
            </el-button>
          </div>
        </div>
      </template>
      <div v-if="profile" class="gitee-profile-content">
        <el-avatar :size="72" :src="profile.avatar_url" />
        <div class="gitee-profile-meta">
          <div class="gitee-profile-name">
            <span class="gitee-profile-title">{{ profile.name || profile.login }}</span>
            <el-tag size="small" effect="plain">{{ profile.login }}</el-tag>
            <el-tag v-if="profile.type" size="small" type="info" effect="plain">{{ profile.type }}</el-tag>
          </div>
          <div class="gitee-profile-desc">
            {{ profile.bio || "暂无简介" }}
          </div>
          <div class="gitee-profile-links">
            <el-link v-if="profile.blog" :href="profile.blog" target="_blank">博客</el-link>
            <el-link v-if="profile.weibo" :href="profile.weibo" target="_blank">微博</el-link>
            <el-link v-if="profile.html_url" :href="profile.html_url" target="_blank">Gitee主页</el-link>
          </div>
        </div>
      </div>
      <el-descriptions v-if="profile" :column="3" border>
        <el-descriptions-item label="邮箱">{{ profile.email || "—" }}</el-descriptions-item>
        <el-descriptions-item label="Followers">{{ profile.followers ?? 0 }}</el-descriptions-item>
        <el-descriptions-item label="Following">{{ profile.following ?? 0 }}</el-descriptions-item>
        <el-descriptions-item label="公开仓库">{{ profile.public_repos ?? 0 }}</el-descriptions-item>
        <el-descriptions-item label="公开 Gists">{{ profile.public_gists ?? 0 }}</el-descriptions-item>
        <el-descriptions-item label="Star 数">{{ profile.stared ?? 0 }}</el-descriptions-item>
        <el-descriptions-item label="Watching">{{ profile.watched ?? 0 }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ formatTime(profile.created_at) }}</el-descriptions-item>
        <el-descriptions-item label="更新时间">{{ formatTime(profile.updated_at) }}</el-descriptions-item>
      </el-descriptions>
      <el-empty v-else description="暂无资料，请先刷新获取" />
    </el-card>

    <el-card class="gitee-card">
      <template #header>
        <div class="gitee-card-header">
          <span>授权用户仓库</span>
          <div class="gitee-card-actions">
            <el-select
              v-model="repoVisibility"
              size="small"
              class="gitee-filter"
              placeholder="可见性"
            >
              <el-option label="全部可见性" value="all" />
              <el-option label="公开" value="public" />
              <el-option label="私有" value="private" />
            </el-select>
            <el-select
              v-model="repoPerPage"
              size="small"
              class="gitee-filter"
              placeholder="每页数量"
            >
              <el-option label="20/页" :value="20" />
              <el-option label="50/页" :value="50" />
              <el-option label="100/页" :value="100" />
            </el-select>
            <el-input
              v-model="repoKeyword"
              placeholder="搜索仓库"
              size="small"
              clearable
              class="gitee-search-input"
            />
            <el-button
              size="small"
              :disabled="!authorized"
              :loading="loadingRepos"
              @click="fetchRepos"
            >
              刷新仓库
            </el-button>
          </div>
        </div>
      </template>
      <el-table v-if="filteredRepos.length" :data="filteredRepos" stripe>
        <el-table-column min-width="220">
          <template #header>
            <div class="gitee-column-header">
              <span>仓库</span>
              <span class="gitee-sort-icons">
                <el-icon
                  :class="getRepoSortClass('full_name', 'asc')"
                  @click.stop="setRepoSort('full_name', 'asc')"
                >
                  <CaretTop />
                </el-icon>
                <el-icon
                  :class="getRepoSortClass('full_name', 'desc')"
                  @click.stop="setRepoSort('full_name', 'desc')"
                >
                  <CaretBottom />
                </el-icon>
              </span>
            </div>
          </template>
          <template #default="{ row }">
            <el-link :href="row.html_url" target="_blank">{{ row.full_name || row.name }}</el-link>
            <div class="gitee-muted">{{ row.description || "暂无描述" }}</div>
          </template>
        </el-table-column>
        <el-table-column width="120">
          <template #header>
            <div class="gitee-column-header">
              <span>语言</span>
              <span class="gitee-sort-icons">
                <el-icon
                  :class="getRepoSortClass('language', 'asc')"
                  @click.stop="setRepoSort('language', 'asc')"
                >
                  <CaretTop />
                </el-icon>
                <el-icon
                  :class="getRepoSortClass('language', 'desc')"
                  @click.stop="setRepoSort('language', 'desc')"
                >
                  <CaretBottom />
                </el-icon>
              </span>
            </div>
          </template>
          <template #default="{ row }">
            <el-tag v-if="row.language" size="small" effect="plain">{{ row.language }}</el-tag>
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column width="90" prop="stargazers_count">
          <template #header>
            <div class="gitee-column-header">
              <span>Star</span>
              <span class="gitee-sort-icons">
                <el-icon
                  :class="getRepoSortClass('stargazers_count', 'asc')"
                  @click.stop="setRepoSort('stargazers_count', 'asc')"
                >
                  <CaretTop />
                </el-icon>
                <el-icon
                  :class="getRepoSortClass('stargazers_count', 'desc')"
                  @click.stop="setRepoSort('stargazers_count', 'desc')"
                >
                  <CaretBottom />
                </el-icon>
              </span>
            </div>
          </template>
        </el-table-column>
        <el-table-column width="90" prop="forks_count">
          <template #header>
            <div class="gitee-column-header">
              <span>Fork</span>
              <span class="gitee-sort-icons">
                <el-icon
                  :class="getRepoSortClass('forks_count', 'asc')"
                  @click.stop="setRepoSort('forks_count', 'asc')"
                >
                  <CaretTop />
                </el-icon>
                <el-icon
                  :class="getRepoSortClass('forks_count', 'desc')"
                  @click.stop="setRepoSort('forks_count', 'desc')"
                >
                  <CaretBottom />
                </el-icon>
              </span>
            </div>
          </template>
        </el-table-column>
        <el-table-column width="90" prop="watchers_count">
          <template #header>
            <div class="gitee-column-header">
              <span>Watch</span>
              <span class="gitee-sort-icons">
                <el-icon
                  :class="getRepoSortClass('watchers_count', 'asc')"
                  @click.stop="setRepoSort('watchers_count', 'asc')"
                >
                  <CaretTop />
                </el-icon>
                <el-icon
                  :class="getRepoSortClass('watchers_count', 'desc')"
                  @click.stop="setRepoSort('watchers_count', 'desc')"
                >
                  <CaretBottom />
                </el-icon>
              </span>
            </div>
          </template>
        </el-table-column>
        <el-table-column width="180">
          <template #header>
            <div class="gitee-column-header">
              <span>更新时间</span>
              <span class="gitee-sort-icons">
                <el-icon
                  :class="getRepoSortClass('updated_at', 'asc')"
                  @click.stop="setRepoSort('updated_at', 'asc')"
                >
                  <CaretTop />
                </el-icon>
                <el-icon
                  :class="getRepoSortClass('updated_at', 'desc')"
                  @click.stop="setRepoSort('updated_at', 'desc')"
                >
                  <CaretBottom />
                </el-icon>
              </span>
            </div>
          </template>
          <template #default="{ row }">{{ formatTime(row.updated_at) }}</template>
        </el-table-column>
      </el-table>
      <el-empty v-else description="暂无仓库数据" />
    </el-card>

    <el-card class="gitee-card">
      <template #header>
        <div class="gitee-card-header">
          <span>授权用户 Issues</span>
          <div class="gitee-card-actions">
            <el-select
              v-model="issueFilter"
              size="small"
              class="gitee-filter"
              placeholder="筛选范围"
            >
              <el-option label="我负责的" value="assigned" />
              <el-option label="我创建的" value="created" />
              <el-option label="全部" value="all" />
            </el-select>
            <el-select
              v-model="issueState"
              size="small"
              class="gitee-filter"
              placeholder="状态"
            >
              <el-option label="全部" value="all" />
              <el-option label="开启" value="open" />
              <el-option label="进行中" value="progressing" />
              <el-option label="关闭" value="closed" />
              <el-option label="拒绝" value="rejected" />
            </el-select>
            <el-input
              v-model="issueLabels"
              placeholder="标签（用逗号分隔）"
              size="small"
              clearable
              class="gitee-filter-wide"
            />
            <el-select
              v-model="issuePerPage"
              size="small"
              class="gitee-filter"
              placeholder="每页数量"
            >
              <el-option label="20/页" :value="20" />
              <el-option label="50/页" :value="50" />
              <el-option label="100/页" :value="100" />
            </el-select>
            <el-input
              v-model="issueKeyword"
              placeholder="搜索 Issue"
              size="small"
              clearable
              class="gitee-search-input"
            />
            <el-button
              size="small"
              :disabled="!authorized"
              :loading="loadingIssues"
              @click="fetchIssues"
            >
              刷新 Issues
            </el-button>
          </div>
        </div>
      </template>
      <el-table v-if="filteredIssues.length" :data="filteredIssues" stripe>
        <el-table-column min-width="240">
          <template #header>
            <div class="gitee-column-header">
              <span>标题</span>
              <span class="gitee-sort-icons">
                <el-icon
                  :class="getIssueSortClass('title', 'asc')"
                  @click.stop="setIssueSort('title', 'asc')"
                >
                  <CaretTop />
                </el-icon>
                <el-icon
                  :class="getIssueSortClass('title', 'desc')"
                  @click.stop="setIssueSort('title', 'desc')"
                >
                  <CaretBottom />
                </el-icon>
              </span>
            </div>
          </template>
          <template #default="{ row }">
            <el-link :href="row.html_url" target="_blank">{{ row.title }}</el-link>
            <div class="gitee-muted">{{ row.repository?.full_name || "—" }}</div>
          </template>
        </el-table-column>
        <el-table-column width="120">
          <template #header>
            <div class="gitee-column-header">
              <span>状态</span>
              <span class="gitee-sort-icons">
                <el-icon
                  :class="getIssueSortClass('state', 'asc')"
                  @click.stop="setIssueSort('state', 'asc')"
                >
                  <CaretTop />
                </el-icon>
                <el-icon
                  :class="getIssueSortClass('state', 'desc')"
                  @click.stop="setIssueSort('state', 'desc')"
                >
                  <CaretBottom />
                </el-icon>
              </span>
            </div>
          </template>
          <template #default="{ row }">
            <el-tag size="small" effect="plain">
              {{ row.issue_state_detail?.title || row.state || "未知" }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column width="180">
          <template #header>
            <div class="gitee-column-header">
              <span>更新时间</span>
              <span class="gitee-sort-icons">
                <el-icon
                  :class="getIssueSortClass('updated_at', 'asc')"
                  @click.stop="setIssueSort('updated_at', 'asc')"
                >
                  <CaretTop />
                </el-icon>
                <el-icon
                  :class="getIssueSortClass('updated_at', 'desc')"
                  @click.stop="setIssueSort('updated_at', 'desc')"
                >
                  <CaretBottom />
                </el-icon>
              </span>
            </div>
          </template>
          <template #default="{ row }">{{ formatTime(row.updated_at) }}</template>
        </el-table-column>
        <el-table-column width="180">
          <template #header>
            <div class="gitee-column-header">
              <span>创建时间</span>
              <span class="gitee-sort-icons">
                <el-icon
                  :class="getIssueSortClass('created_at', 'asc')"
                  @click.stop="setIssueSort('created_at', 'asc')"
                >
                  <CaretTop />
                </el-icon>
                <el-icon
                  :class="getIssueSortClass('created_at', 'desc')"
                  @click.stop="setIssueSort('created_at', 'desc')"
                >
                  <CaretBottom />
                </el-icon>
              </span>
            </div>
          </template>
          <template #default="{ row }">{{ formatTime(row.created_at) }}</template>
        </el-table-column>
      </el-table>
      <el-empty v-else description="暂无 Issue 数据" />
    </el-card>

    <el-card class="gitee-card">
      <template #header>
        <div class="gitee-card-header">
          <span>授权用户通知</span>
          <div class="gitee-card-actions">
            <el-select
              v-model="noticeUnread"
              size="small"
              class="gitee-filter"
              placeholder="未读状态"
            >
              <el-option label="全部" value="all" />
              <el-option label="仅未读" value="true" />
              <el-option label="仅已读" value="false" />
            </el-select>
            <el-select
              v-model="noticeParticipating"
              size="small"
              class="gitee-filter"
              placeholder="参与状态"
            >
              <el-option label="全部" value="all" />
              <el-option label="仅参与" value="true" />
              <el-option label="未参与" value="false" />
            </el-select>
            <el-select
              v-model="noticeType"
              size="small"
              class="gitee-filter"
              placeholder="通知类型"
            >
              <el-option label="全部" value="all" />
              <el-option label="事件通知" value="event" />
              <el-option label="@通知" value="referer" />
            </el-select>
            <el-select
              v-model="noticePerPage"
              size="small"
              class="gitee-filter"
              placeholder="每页数量"
            >
              <el-option label="20/页" :value="20" />
              <el-option label="50/页" :value="50" />
              <el-option label="100/页" :value="100" />
            </el-select>
            <el-input
              v-model="noticeKeyword"
              placeholder="搜索通知"
              size="small"
              clearable
              class="gitee-search-input"
            />
            <el-button
              size="small"
              :disabled="!authorized"
              :loading="loadingNotices"
              @click="fetchNotifications"
            >
              刷新通知
            </el-button>
          </div>
        </div>
      </template>
      <el-table v-if="filteredNotifications.length" :data="filteredNotifications" stripe>
        <el-table-column label="内容" min-width="320">
          <template #default="{ row }">
            <el-link :href="row.html_url" target="_blank">{{ row.content }}</el-link>
            <div class="gitee-muted">{{ row.repository?.full_name || "—" }}</div>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="140">
          <template #default="{ row }">{{ row.type || "—" }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.unread ? 'danger' : 'success'" size="small" effect="plain">
              {{ row.unread ? "未读" : "已读" }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="触发人" width="120">
          <template #default="{ row }">{{ row.actor?.name || row.actor?.login || "—" }}</template>
        </el-table-column>
        <el-table-column label="更新时间" width="180">
          <template #default="{ row }">{{ formatTime(row.updated_at) }}</template>
        </el-table-column>
      </el-table>
      <el-empty v-else description="暂无通知数据" />
    </el-card>
  </div>
</template>

<script setup name="GiteeProfile">
import { ref, computed, watch, onMounted } from "vue"
import { ElMessage, ElMessageBox } from "element-plus"
import { CaretBottom, CaretTop } from "@element-plus/icons-vue"
import { parseTime } from "@/utils/WxFbsir"
import { useRoute, useRouter } from "vue-router"
import {
  getGiteeStatus,
  getGiteeAuthorizeUrl,
  fetchGiteeProfile,
  fetchGiteeRepos,
  fetchGiteeIssues,
  fetchGiteeNotifications,
  unbindGitee,
  checkResumeExists,
  uploadResume,
  updateResume
} from "@/api/business/gitee/profile"

const redirectPath = "/user/profile/gitee"
const profile = ref(null)
const repos = ref([])
const issues = ref([])
const notifications = ref([])

const authorized = ref(false)
const authorizing = ref(false)
const unbinding = ref(false)

const resumeExists = ref(false)
const uploadingResume = ref(false)
const fileInput = ref(null)
const selectedFile = ref(null)

const repoKeyword = ref("")
const issueKeyword = ref("")
const noticeKeyword = ref("")
const repoVisibility = ref("all")
const repoSort = ref("updated_at")
const repoDirection = ref("desc")
const repoPerPage = ref(50)
const issueFilter = ref("created")
const issueState = ref("all")
const issueLabels = ref("")
const issueSort = ref("created_at")
const issueDirection = ref("desc")
const issuePerPage = ref(50)
const noticeUnread = ref("all")
const noticeParticipating = ref("all")
const noticeType = ref("all")
const noticePerPage = ref(50)

const loadingProfile = ref(false)
const loadingRepos = ref(false)
const loadingIssues = ref(false)
const loadingNotices = ref(false)
const loadingAll = ref(false)

const filteredRepos = computed(() => {
  const keyword = repoKeyword.value.trim().toLowerCase()
  const filtered = keyword
    ? repos.value.filter(repo => {
        return (repo.full_name || repo.name || "").toLowerCase().includes(keyword)
      })
    : repos.value
  return sortRepos(filtered)
})

const filteredIssues = computed(() => {
  const keyword = issueKeyword.value.trim().toLowerCase()
  const filtered = keyword
    ? issues.value.filter(issue => {
        const title = issue.title || ""
        const repo = issue.repository?.full_name || ""
        return `${title} ${repo}`.toLowerCase().includes(keyword)
      })
    : issues.value
  return sortIssues(filtered)
})

const filteredNotifications = computed(() => {
  const keyword = noticeKeyword.value.trim().toLowerCase()
  return notifications.value.filter(notice => {
    const content = notice.content || ""
    const repo = notice.repository?.full_name || ""
    if (keyword && !`${content} ${repo}`.toLowerCase().includes(keyword)) {
      return false
    }
    if (noticeType.value !== "all" && notice.type !== noticeType.value) {
      return false
    }
    if (noticeUnread.value !== "all" && String(Boolean(notice.unread)) !== noticeUnread.value) {
      return false
    }
    return true
  })
})

function formatTime(value) {
  const parsed = parseTime(value)
  return parsed || "—"
}

function handleError(error, fallback) {
  const message = error?.response?.data?.message || error?.message || fallback
  ElMessage.error(message)
}

function setIssueSort(field, direction) {
  issueSort.value = field
  issueDirection.value = direction
}

function getIssueSortClass(field, direction) {
  return [
    "gitee-sort-icon",
    issueSort.value === field && issueDirection.value === direction ? "is-active" : ""
  ]
}

function sortIssues(list) {
  const key = issueSort.value
  const direction = issueDirection.value === "asc" ? 1 : -1
  if (!key) {
    return list
  }
  return [...list].sort((a, b) => {
    const left = getIssueSortValue(a, key)
    const right = getIssueSortValue(b, key)
    if (left === right) {
      return 0
    }
    if (left == null) {
      return 1
    }
    if (right == null) {
      return -1
    }
    if (typeof left === "number" && typeof right === "number") {
      return (left - right) * direction
    }
    return String(left).localeCompare(String(right)) * direction
  })
}

function getIssueSortValue(issue, key) {
  if (!issue) {
    return ""
  }
  if (key === "title") {
    return issue.title || ""
  }
  if (key === "state") {
    return issue.issue_state_detail?.title || issue.state || ""
  }
  if (key === "updated_at") {
    return Date.parse(issue.updated_at || "") || 0
  }
  if (key === "created_at") {
    return Date.parse(issue.created_at || "") || 0
  }
  return issue[key] ?? ""
}

function setRepoSort(field, direction) {
  repoSort.value = field
  repoDirection.value = direction
}

function getRepoSortClass(field, direction) {
  return [
    "gitee-sort-icon",
    repoSort.value === field && repoDirection.value === direction ? "is-active" : ""
  ]
}

function sortRepos(list) {
  const key = repoSort.value
  const direction = repoDirection.value === "asc" ? 1 : -1
  if (!key) {
    return list
  }
  return [...list].sort((a, b) => {
    const left = getRepoSortValue(a, key)
    const right = getRepoSortValue(b, key)
    if (left === right) {
      return 0
    }
    if (left == null) {
      return 1
    }
    if (right == null) {
      return -1
    }
    if (typeof left === "number" && typeof right === "number") {
      return (left - right) * direction
    }
    return String(left).localeCompare(String(right)) * direction
  })
}

function getRepoSortValue(repo, key) {
  if (!repo) {
    return ""
  }
  if (key === "full_name") {
    return repo.full_name || repo.name || ""
  }
  if (key === "language") {
    return repo.language || ""
  }
  if (key === "updated_at") {
    return Date.parse(repo.updated_at || "") || 0
  }
  if (key === "stargazers_count" || key === "forks_count" || key === "watchers_count") {
    return Number(repo[key] ?? 0)
  }
  return repo[key] ?? ""
}

function clearData() {
  profile.value = null
  repos.value = []
  issues.value = []
  notifications.value = []
}

function handleAuthorize() {
  authorizing.value = true
  getGiteeAuthorizeUrl({ redirect: redirectPath }).then(res => {
    const authorizeUrl = res.data?.url
    if (!authorizeUrl) {
      throw new Error("授权地址获取失败")
    }
    window.location.href = authorizeUrl
  }).catch(error => {
    handleError(error, "获取授权地址失败")
    authorizing.value = false
  })
}

function openProfile() {
  if (profile.value?.html_url) {
    window.open(profile.value.html_url, "_blank")
  }
}

function copyProfileUrl() {
  if (!profile.value?.html_url) {
    return
  }
  navigator.clipboard?.writeText(profile.value.html_url).then(() => {
    ElMessage.success("链接已复制")
  }).catch(() => {
    ElMessage.error("复制失败，请手动复制")
  })
}

async function fetchProfile() {
  if (!authorized.value) {
    return
  }
  loadingProfile.value = true
  try {
    const res = await fetchGiteeProfile()
    profile.value = res.data || null
  } catch (error) {
    handleError(error, "获取用户资料失败")
  } finally {
    loadingProfile.value = false
  }
}

async function fetchRepos() {
  if (!authorized.value) {
    return
  }
  loadingRepos.value = true
  try {
    const params = {
      per_page: repoPerPage.value
    }
    const apiSort = getRepoApiSort(repoSort.value)
    if (apiSort) {
      params.sort = apiSort
      params.direction = repoDirection.value
    }
    if (repoVisibility.value !== "all") {
      params.visibility = repoVisibility.value
    }
    if (repoKeyword.value.trim()) {
      params.q = repoKeyword.value.trim()
    }
    const res = await fetchGiteeRepos(params)
    const data = res.data
    repos.value = Array.isArray(data) ? data : []
  } catch (error) {
    handleError(error, "获取仓库失败")
  } finally {
    loadingRepos.value = false
  }
}

function getRepoApiSort(field) {
  const map = {
    full_name: "full_name",
    updated_at: "updated",
    created_at: "created",
    pushed_at: "pushed"
  }
  return map[field] || ""
}

async function fetchIssues() {
  if (!authorized.value) {
    return
  }
  loadingIssues.value = true
  try {
    const params = {
      filter: issueFilter.value,
      state: issueState.value,
      per_page: issuePerPage.value
    }
    const apiSort = getIssueApiSort(issueSort.value)
    if (apiSort) {
      params.sort = apiSort
      params.direction = issueDirection.value
    }
    if (issueLabels.value.trim()) {
      params.labels = issueLabels.value.trim()
    }
    const res = await fetchGiteeIssues(params)
    const data = res.data
    issues.value = Array.isArray(data) ? data : []
  } catch (error) {
    handleError(error, "获取 Issue 失败")
  } finally {
    loadingIssues.value = false
  }
}

function getIssueApiSort(field) {
  const map = {
    created_at: "created",
    updated_at: "updated"
  }
  return map[field] || ""
}

async function fetchNotifications() {
  if (!authorized.value) {
    return
  }
  loadingNotices.value = true
  try {
    const params = {
      per_page: noticePerPage.value
    }
    if (noticeUnread.value !== "all") {
      params.unread = noticeUnread.value
    }
    if (noticeParticipating.value !== "all") {
      params.participating = noticeParticipating.value
    }
    if (noticeType.value !== "all") {
      params.type = noticeType.value
    }
    const res = await fetchGiteeNotifications(params)
    const data = res.data
    if (data && Array.isArray(data.list)) {
      notifications.value = data.list
    } else if (Array.isArray(data)) {
      notifications.value = data
    } else {
      notifications.value = []
    }
  } catch (error) {
    handleError(error, "获取通知失败")
  } finally {
    loadingNotices.value = false
  }
}

async function handleRefreshAll() {
  if (!authorized.value) {
    return
  }
  loadingAll.value = true
  try {
    await Promise.all([
      fetchProfile(),
      fetchRepos(),
      fetchIssues(),
      fetchNotifications()
    ])
  } finally {
    loadingAll.value = false
  }
}

async function handleUnbind() {
  if (!authorized.value) {
    return
  }
  try {
    await ElMessageBox.confirm(
      "解除绑定会清除已授权的 Gitee 信息，后续需重新授权才能使用相关功能。是否继续？",
      "解除绑定",
      {
        confirmButtonText: "确认解除",
        cancelButtonText: "取消",
        type: "warning"
      }
    )
  } catch (error) {
    return
  }
  unbinding.value = true
  try {
    await unbindGitee()
    authorized.value = false
    clearData()
    ElMessage.success("已解除绑定")
  } catch (error) {
    handleError(error, "解除绑定失败")
  } finally {
    unbinding.value = false
  }
}

async function loadStatus() {
  try {
    const res = await getGiteeStatus()
    authorized.value = Boolean(res.data?.authorized)
    if (!authorized.value) {
      clearData()
    }
  } catch (error) {
    handleError(error, "获取授权状态失败")
  } finally {
    authorizing.value = false
  }
}

async function loadResumeStatus() {
  try {
    const res = await checkResumeExists()
    // 处理不同的响应格式
    if (res.code === 200) {
      // 标准格式: { code: 200, data: { exists: boolean|string } }
      const existsValue = res.data?.exists
      // 处理字符串类型的布尔值
      if (typeof existsValue === 'string') {
        resumeExists.value = existsValue.toLowerCase() === 'true'
      } else {
        resumeExists.value = Boolean(existsValue)
      }
    } else if (typeof res === 'boolean') {
      // 直接返回布尔值的格式
      resumeExists.value = res
    } else if (res.data !== undefined) {
      // { data: boolean|string } 格式
      const dataValue = res.data
      if (typeof dataValue === 'string') {
        resumeExists.value = dataValue.toLowerCase() === 'true'
      } else {
        resumeExists.value = Boolean(dataValue)
      }
    } else {
      // 其他格式，默认为false
      resumeExists.value = false
    }
  } catch (error) {
    handleError(error, "获取简历状态失败")
    // 出错时默认为false
    resumeExists.value = false
  }
}

function handleUploadResume() {
  fileInput.value?.click()
}

function handleUpdateResume() {
  fileInput.value?.click()
}

async function handleFileChange(event) {
  const file = event.target.files[0]
  if (!file) {
    return
  }
  selectedFile.value = file
  await uploadResumeFile()
  event.target.value = ""
}

async function uploadResumeFile() {
  if (!selectedFile.value) {
    return
  }
  uploadingResume.value = true
  try {
    if (resumeExists.value) {
      await updateResume(selectedFile.value)
      ElMessage.success("简历更新成功")
    } else {
      await uploadResume(selectedFile.value)
      ElMessage.success("简历上传成功")
    }
    await loadResumeStatus()
  } catch (error) {
    handleError(error, resumeExists.value ? "简历更新失败" : "简历上传失败")
  } finally {
    uploadingResume.value = false
    selectedFile.value = null
  }
}


watch([repoVisibility, repoPerPage], () => {
  if (!authorized.value || loadingRepos.value) {
    return
  }
  fetchRepos()
})

watch([
  issueFilter,
  issueState,
  issueSort,
  issueDirection,
  issueLabels,
  issuePerPage
], () => {
  if (!authorized.value || loadingIssues.value) {
    return
  }
  fetchIssues()
})

watch([noticeUnread, noticeParticipating, noticeType, noticePerPage], () => {
  if (!authorized.value || loadingNotices.value) {
    return
  }
  fetchNotifications()
})

onMounted(async () => {
  const route = useRoute()
  const router = useRouter()
  const giteeError = route.query?.giteeError
  if (giteeError) {
    ElMessage.error(decodeURIComponent(String(giteeError)))
  }
  if (route.query?.giteeAuth) {
    ElMessage.success("Gitee 授权成功")
  }
  if (giteeError || route.query?.giteeAuth) {
    const cleanQuery = { ...route.query }
    delete cleanQuery.giteeError
    delete cleanQuery.giteeAuth
    router.replace({ path: route.path, query: cleanQuery })
  }
  await loadStatus()
  await loadResumeStatus()
  if (authorized.value) {
    handleRefreshAll()
  }
})
</script>

<style scoped>
.gitee-profile {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.gitee-alert {
  margin-bottom: 6px;
}

.gitee-status {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.gitee-status-text {
  color: #606266;
}

.gitee-card {
  border-radius: 6px;
}

.gitee-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.gitee-card-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.gitee-profile-content {
  display: flex;
  align-items: center;
  gap: 18px;
  margin-bottom: 16px;
}

.gitee-profile-meta {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.gitee-profile-title {
  font-size: 18px;
  font-weight: 600;
  margin-right: 8px;
}

.gitee-profile-name {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.gitee-profile-desc {
  color: #606266;
}

.gitee-profile-links {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.gitee-column-header {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.gitee-sort-icons {
  display: inline-flex;
  flex-direction: column;
  gap: 2px;
  cursor: pointer;
}

.gitee-sort-icon {
  font-size: 12px;
  color: #c0c4cc;
  line-height: 1;
}

.gitee-sort-icon.is-active {
  color: #409eff;
}

.gitee-search-input {
  width: 200px;
}

.gitee-filter {
  width: 140px;
}

.gitee-filter-wide {
  width: 220px;
}

.gitee-muted {
  color: #909399;
  font-size: 12px;
  margin-top: 4px;
}
</style>
