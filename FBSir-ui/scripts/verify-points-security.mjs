import { readFileSync } from 'node:fs'

const component = readFileSync(
  new URL('../src/components/PointsGetDialog/index.vue', import.meta.url),
  'utf8'
)
const pointsApi = readFileSync(
  new URL('../src/api/business/points.js', import.meta.url),
  'utf8'
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

console.log(JSON.stringify({
  verifier: 'points-direct-mutation-security',
  result: 'PASS',
  directMutationUiClosed: true
}))
