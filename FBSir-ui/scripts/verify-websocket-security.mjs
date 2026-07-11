import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { redactWebSocketUrl } from '../src/utils/websocket.js'

const scriptDir = path.dirname(fileURLToPath(import.meta.url))
const uiRoot = path.resolve(scriptDir, '..')
const debugView = fs.readFileSync(path.join(uiRoot, 'src/views/business/debug/index.vue'), 'utf8')
const loginView = fs.readFileSync(path.join(uiRoot, 'src/views/login.vue'), 'utf8')
const requestView = fs.readFileSync(path.join(uiRoot, 'src/utils/request.js'), 'utf8')

const secret = 'header.payload.signature'
const actual = `ws://localhost/dev-api/ws/client?clientType=web&token=${secret}&clientInstanceId=tab-1`
const displayed = redactWebSocketUrl(actual)

assert.equal(
  displayed,
  'ws://localhost/dev-api/ws/client?clientType=web&token=[REDACTED]&clientInstanceId=tab-1'
)
assert.ok(!displayed.includes(secret), 'redacted WebSocket URL must not contain the original token')
assert.equal(redactWebSocketUrl('ws://localhost/ws/client?clientType=web'), 'ws://localhost/ws/client?clientType=web')
assert.match(debugView, /:model-value="displayWsUrl"/)
assert.doesNotMatch(debugView, /v-model="wsUrl"/)
assert.match(loginView, /username:\s*"admin"[\s\S]*password:\s*""/)
assert.match(requestView, /请确认 Admin 已启动并检查前端代理地址/)
assert.match(requestView, /ERROR_DEDUP_WINDOW_MS\s*=\s*3000/)
assert.match(requestView, /Request failed with status code 500/)

console.log('Verified first-run connection guidance, login defaults, and visible WebSocket token redaction.')
