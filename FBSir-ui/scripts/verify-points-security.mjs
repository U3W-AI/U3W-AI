import { readFileSync, readdirSync } from 'node:fs'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'

const component = readFileSync(
  new URL('../src/components/PointsGetDialog/index.vue', import.meta.url),
  'utf8'
)
const pointsApi = readFileSync(
  new URL('../src/api/business/points.js', import.meta.url),
  'utf8'
)
function readFrontendSource(directory) {
  return readdirSync(directory, { withFileTypes: true })
    .flatMap(entry => {
      const path = join(directory, entry.name)
      if (entry.isDirectory()) {
        return readFrontendSource(path)
      }
      if (!/\.(?:js|mjs|ts|vue)$/.test(entry.name)) {
        return []
      }
      return [readFileSync(path, 'utf8')]
    })
    .join('\n')
}

const frontendSource = readFrontendSource(
  fileURLToPath(new URL('../src/', import.meta.url))
)

const required = [
  '积分任务维护中',
  '恢复后将展示经过服务端校验的任务',
  '已暂停',
  'getUserPoints'
]

for (const token of required) {
  if (!component.includes(token)) {
    throw new Error(`PointsGetDialog security closure is missing: ${token}`)
  }
}

const forbidden = [
  'changePoints',
  'WATCH_ADVERTISEMENT',
  'handleTask(',
  'executeTask('
]

for (const token of forbidden) {
  if (component.includes(token)) {
    throw new Error(`PointsGetDialog still exposes direct mutation behavior: ${token}`)
  }
}

if (pointsApi.includes('/points/changePoints') || pointsApi.includes('changePoints')) {
  throw new Error('The frontend points API still exposes the legacy mutation contract')
}

const retiredAdminGrantTokens = [
  '/points/fans/grantPoints',
  'grantPointsToUser',
  'handleGrantPoints',
  'handleSubmitGrantPoints',
  "points:fans:grant"
]

for (const token of retiredAdminGrantTokens) {
  if (frontendSource.includes(token)) {
    throw new Error(`The retired admin points mutation is still exposed: ${token}`)
  }
}

console.log(JSON.stringify({
  verifier: 'points-direct-mutation-security',
  result: 'PASS',
  directMutationUiClosed: true,
  retiredAdminGrantUiClosed: true
}))
