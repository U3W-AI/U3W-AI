import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { createMemoryHistory, createRouter } from 'vue-router'

import {
  DEFAULT_PORTAL_PATH,
  INDEPENDENT_BOARD_ROUTE_NAME,
  PORTAL_ENTRY_PATH,
  normalizePortalHostname,
  resolveLoginDestination,
  resolvePortalEntry
} from '../src/utils/portalEntry.js'

const scriptDir = path.dirname(fileURLToPath(import.meta.url))
const uiRoot = path.resolve(scriptDir, '..')

const entryCases = [
  {
    name: 'me portal with an authorized dynamic route',
    hostname: 'me.u3w.com',
    hasRoute: true,
    expected: { name: INDEPENDENT_BOARD_ROUTE_NAME }
  },
  {
    name: 'me portal without the dynamic route',
    hostname: 'me.u3w.com',
    hasRoute: false,
    expected: { path: DEFAULT_PORTAL_PATH }
  },
  {
    name: 'admin portal never selects the user route',
    hostname: 'admin.u3w.com',
    hasRoute: true,
    expected: { path: DEFAULT_PORTAL_PATH }
  },
  {
    name: 'unknown host never selects the user route',
    hostname: 'portal.example.com',
    hasRoute: true,
    expected: { path: DEFAULT_PORTAL_PATH }
  },
  {
    name: 'DNS hostname matching is case insensitive',
    hostname: 'ME.U3W.COM',
    hasRoute: true,
    expected: { name: INDEPENDENT_BOARD_ROUTE_NAME }
  },
  {
    name: 'empty hostname fails closed',
    hostname: '',
    hasRoute: true,
    expected: { path: DEFAULT_PORTAL_PATH }
  },
  {
    name: 'missing hostname fails closed',
    hostname: null,
    hasRoute: true,
    expected: { path: DEFAULT_PORTAL_PATH }
  },
  {
    name: 'lookalike hostname fails closed',
    hostname: 'me.u3w.com.example.org',
    hasRoute: true,
    expected: { path: DEFAULT_PORTAL_PATH }
  }
]

for (const testCase of entryCases) {
  assert.deepEqual(
    resolvePortalEntry(testCase.hostname, testCase.hasRoute),
    testCase.expected,
    testCase.name
  )
}

assert.equal(normalizePortalHostname(' ME.U3W.COM. '), 'me.u3w.com')
assert.equal(resolveLoginDestination(undefined), PORTAL_ENTRY_PATH)
assert.equal(resolveLoginDestination(''), PORTAL_ENTRY_PATH)
assert.equal(resolveLoginDestination('   '), PORTAL_ENTRY_PATH)

const explicitDeepLink = '/system/user/profile/security?source=notification'
assert.equal(
  resolveLoginDestination(explicitDeepLink),
  explicitDeepLink,
  'an explicit post-login deep link must not be rewritten by portal-host routing'
)

const testComponent = { render: () => null }

function createNavigationHarness(hostname) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: PORTAL_ENTRY_PATH, name: 'PortalEntryHarness', component: testComponent },
      { path: DEFAULT_PORTAL_PATH, name: 'IndexHarness', component: testComponent },
      {
        path: '/system/user/profile/security',
        name: 'ExplicitDeepLinkHarness',
        component: testComponent
      }
    ]
  })
  let guardVisits = 0

  router.beforeEach(to => {
    guardVisits += 1
    assert.ok(guardVisits <= 2, `portal navigation loop detected for ${hostname || '<empty>'}`)
    if (to.path === PORTAL_ENTRY_PATH) {
      return {
        ...resolvePortalEntry(hostname, router.hasRoute(INDEPENDENT_BOARD_ROUTE_NAME)),
        replace: true
      }
    }
    return true
  })

  return {
    router,
    installIndependentBoardRoute() {
      router.addRoute({
        path: '/independent-board',
        name: INDEPENDENT_BOARD_ROUTE_NAME,
        component: testComponent
      })
    },
    resetGuardVisits() {
      guardVisits = 0
    },
    guardVisitCount() {
      return guardVisits
    }
  }
}

async function navigateFromPortalEntry(harness, expectedPath) {
  harness.resetGuardVisits()
  await harness.router.push(PORTAL_ENTRY_PATH)
  assert.equal(harness.router.currentRoute.value.path, expectedPath)
  assert.equal(harness.guardVisitCount(), 2, 'portal-entry must resolve in one redirect without a loop')
}

const meHarness = createNavigationHarness('me.u3w.com')
await navigateFromPortalEntry(meHarness, DEFAULT_PORTAL_PATH)
assert.equal(meHarness.router.hasRoute(INDEPENDENT_BOARD_ROUTE_NAME), false)

meHarness.installIndependentBoardRoute()
assert.equal(meHarness.router.hasRoute(INDEPENDENT_BOARD_ROUTE_NAME), true)
await navigateFromPortalEntry(meHarness, '/independent-board')
assert.equal(meHarness.router.currentRoute.value.name, INDEPENDENT_BOARD_ROUTE_NAME)

const adminHarness = createNavigationHarness('admin.u3w.com')
adminHarness.installIndependentBoardRoute()
await navigateFromPortalEntry(adminHarness, DEFAULT_PORTAL_PATH)

const unknownHarness = createNavigationHarness('unknown.example.com')
unknownHarness.installIndependentBoardRoute()
await navigateFromPortalEntry(unknownHarness, DEFAULT_PORTAL_PATH)

const deepLinkHarness = createNavigationHarness('me.u3w.com')
deepLinkHarness.installIndependentBoardRoute()
deepLinkHarness.resetGuardVisits()
await deepLinkHarness.router.push(resolveLoginDestination(explicitDeepLink))
assert.equal(deepLinkHarness.router.currentRoute.value.fullPath, explicitDeepLink)
assert.equal(deepLinkHarness.guardVisitCount(), 1, 'an explicit deep link must not enter portal routing')

const routerSource = fs.readFileSync(path.join(uiRoot, 'src/router/index.js'), 'utf8')
const permissionSource = fs.readFileSync(path.join(uiRoot, 'src/permission.js'), 'utf8')
const loginSource = fs.readFileSync(path.join(uiRoot, 'src/views/login.vue'), 'utf8')

assert.match(routerSource, /path:\s*PORTAL_ENTRY_PATH,[\s\S]*?hidden:\s*true/)
assert.match(routerSource, /path:\s*['"]['"],[\s\S]*?redirect:\s*PORTAL_ENTRY_PATH/)
assert.match(permissionSource, /router\.hasRoute\(INDEPENDENT_BOARD_ROUTE_NAME\)/)
assert.match(permissionSource, /to\.path\s*===\s*PORTAL_ENTRY_PATH/)
assert.match(permissionSource, /window\.location\.hostname/)
assert.ok(
  (loginSource.match(/resolveLoginDestination\(redirect\.value\)/g) || []).length >= 3,
  'all default login completion paths must preserve explicit redirects and otherwise use portal-entry'
)
assert.doesNotMatch(
  loginSource,
  /router\.(?:push|replace)\(\{\s*path:\s*['"]\/index['"]/,
  'login completion must not bypass portal-entry with a hard-coded /index destination'
)

console.log(`Verified ${entryCases.length} pure portal-entry cases and 5 in-memory router navigation cases.`)
