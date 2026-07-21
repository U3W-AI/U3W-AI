import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import {
  admitIndependentBoardPortalCandidateRoutes,
  isBoardPortalCandidateEnabled
} from '../src/utils/independentBoardPortalCandidate.js'

const candidateChildren = [
  ['IndependentBoardOAuthClientCandidate', 'business/independentBoard/admin/oauth-client/index'],
  ['IndependentBoardOAuthFamilyCandidate', 'business/independentBoard/admin/oauth-family/index'],
  ['IndependentBoardConnectorBindingCandidate', 'business/independentBoard/admin/connector-binding/index'],
  ['IndependentBoardSecurityEventCandidate', 'business/independentBoard/admin/security-event/index']
].map(([name, component], index) => ({
  name,
  path: `candidate-${index}`,
  component
}))
const sourceRoutes = [{
  name: 'IndependentBoardMe',
  path: '/independent-board',
  component: 'business/independentBoard/me/index'
}, {
  name: '',
  path: '/',
  component: 'Layout',
  children: [{
    name: 'IndependentBoardConnectorCandidate',
    path: 'independent-board-connector',
    component: 'business/independentBoard/me/connector/index'
  }]
}, {
  name: 'IndependentBoardSecurityCandidate',
  path: '/independent-board-security',
  component: 'business/independentBoard/me/security/index'
}, {
  name: 'IndependentBoardAdmin',
  path: '/independent-board-admin',
  component: 'Layout',
  children: [{
    name: 'IndependentBoardEntitlementGovernance',
    path: 'entitlements',
    component: 'business/independentBoard/admin/entitlement/index'
  }, ...candidateChildren]
}]
const snapshot = JSON.stringify(sourceRoutes)

for (const env of [
  {},
  { VITE_FBSIR_BOARD_PORTAL_CANDIDATE: 'false' },
  { VITE_FBSIR_BOARD_PORTAL_CANDIDATE: 'TRUE' },
  { VITE_FBSIR_BOARD_PORTAL_CANDIDATE: true }
]) {
  assert.equal(isBoardPortalCandidateEnabled(env), false)
  const admitted = admitIndependentBoardPortalCandidateRoutes(sourceRoutes, env)
  const serialized = JSON.stringify(admitted)
  assert.doesNotMatch(serialized, /(?:me\/connector|oauth-client|oauth-family|connector-binding)/)
  assert.doesNotMatch(serialized, /(?:me\/security|security-event)/)
  assert.match(serialized, /admin\/entitlement\/index/)
  assert.match(serialized, /business\/independentBoard\/me\/index/)
}

const enabled = admitIndependentBoardPortalCandidateRoutes(sourceRoutes, {
  VITE_FBSIR_BOARD_PORTAL_CANDIDATE: 'true'
})
const enabledSource = JSON.stringify(enabled)
for (const component of [
  'me/connector', 'admin/oauth-client', 'admin/oauth-family', 'admin/connector-binding'
]) {
  assert.ok(enabledSource.includes(component), `enabled mount missing ${component}`)
}
assert.doesNotMatch(enabledSource, /(?:me\/security|security-event)/)
assert.equal(JSON.stringify(sourceRoutes), snapshot, 'route admission must not mutate API data')

const mismatchedIdentity = admitIndependentBoardPortalCandidateRoutes([{
  name: 'IndependentBoardOAuthClientCandidate',
  path: '/tampered',
  component: 'business/independentBoard/admin/oauth-family/index'
}], { VITE_FBSIR_BOARD_PORTAL_CANDIDATE: 'true' })
assert.deepEqual(mismatchedIdentity, [], 'candidate name/component mismatch must fail closed')

const scriptDirectory = path.dirname(fileURLToPath(import.meta.url))
const uiRoot = path.resolve(scriptDirectory, '..')
const read = relative => fs.readFileSync(path.join(uiRoot, relative), 'utf8')
const permissionStore = read('src/store/modules/permission.js')
assert.match(permissionStore, /admitIndependentBoardPortalCandidateRoutes/)
const admissionIndex = permissionStore.indexOf('admitIndependentBoardPortalCandidateRoutes(res.data')
const firstCloneIndex = permissionStore.indexOf('JSON.parse(JSON.stringify(')
assert.ok(admissionIndex >= 0 && admissionIndex < firstCloneIndex,
  'candidate route admission must happen before every router-data clone')

const candidateApi = read('src/api/business/independentBoard/portalCandidate.js')
assert.doesNotMatch(candidateApi, /@\/views\//,
  'API modules must not depend on view modules for the feature gate')

console.log('Independent Board W4b.2c runtime mount contract passed: exact flag, four-page allowlist, held-page denylist and pre-clone route admission are green.')
