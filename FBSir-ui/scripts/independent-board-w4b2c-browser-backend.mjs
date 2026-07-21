import http from 'node:http'

const HOST = '127.0.0.1'
const PORT = 18081
const MAX_REQUEST_LOG = 500
const requestLog = []

const boardScopes = [
  'identity.read',
  'entitlement.read',
  'board.meeting.reserve',
  'board.receipt.write'
]

const candidateRoutes = [{
  name: '',
  path: '/',
  component: 'Layout',
  children: [{
    name: 'IndependentBoardConnectorCandidate',
    path: 'independent-board-connector',
    component: 'business/independentBoard/me/connector/index',
    meta: { title: '连接与授权', icon: 'connection' }
  }]
}, {
  name: '',
  path: '/',
  component: 'Layout',
  children: [{
    name: 'IndependentBoardSecurityCandidate',
    path: 'independent-board-security',
    component: 'business/independentBoard/me/security/index',
    meta: { title: '安全回执（应被拒绝）', icon: 'lock' }
  }]
}, {
  name: 'IndependentBoardAdmin',
  path: '/independent-board-admin',
  component: 'Layout',
  alwaysShow: true,
  redirect: 'noRedirect',
  meta: { title: '独董会管理', icon: 'peoples' },
  children: [{
    name: 'IndependentBoardOAuthClientCandidate',
    path: 'oauth-clients',
    component: 'business/independentBoard/admin/oauth-client/index',
    meta: { title: 'OAuth 客户端', icon: 'monitor' }
  }, {
    name: 'IndependentBoardOAuthFamilyCandidate',
    path: 'oauth-families',
    component: 'business/independentBoard/admin/oauth-family/index',
    meta: { title: 'Token Family', icon: 'tree' }
  }, {
    name: 'IndependentBoardConnectorBindingCandidate',
    path: 'connector-bindings',
    component: 'business/independentBoard/admin/connector-binding/index',
    meta: { title: 'Connector Binding', icon: 'link' }
  }, {
    name: 'IndependentBoardSecurityEventCandidate',
    path: 'security-events',
    component: 'business/independentBoard/admin/security-event/index',
    meta: { title: '安全事件（应被拒绝）', icon: 'lock' }
  }]
}]

const tenantPageOne = {
  records: [
    { tenantId: 101, tenantLabel: '福帮手联调一号企业', status: 'ACTIVE' },
    { tenantId: 102, tenantLabel: '福帮手联调停用企业', status: 'DISABLED' }
  ],
  limit: 100,
  truncated: true,
  nextCursor: 'tenant-cursor-page2'
}

const tenantPageTwo = {
  records: [
    { tenantId: 103, tenantLabel: '福帮手联调三号企业', status: 'ACTIVE' }
  ],
  limit: 100,
  truncated: false,
  nextCursor: null
}

const emptyPage = Object.freeze({
  records: [],
  limit: 100,
  truncated: false,
  nextCursor: null
})

function respondJson(response, statusCode, payload) {
  const body = JSON.stringify(payload)
  response.writeHead(statusCode, {
    'Content-Type': 'application/json; charset=utf-8',
    'Content-Length': Buffer.byteLength(body),
    'Cache-Control': 'no-store'
  })
  response.end(body)
}

function recordBoardRequest(request, url) {
  if (!url.pathname.startsWith('/business/independent-board')
      && !url.pathname.startsWith('/my/independent-board')) return
  requestLog.push({
    at: new Date().toISOString(),
    method: request.method,
    path: url.pathname,
    query: Object.fromEntries(url.searchParams.entries())
  })
  if (requestLog.length > MAX_REQUEST_LOG) requestLog.shift()
}

function candidateResponse(request, response, url) {
  if (request.method !== 'GET') {
    respondJson(response, 405, { code: 405, msg: 'Fixture candidate endpoints are GET-only.' })
    return true
  }
  if (url.pathname === '/business/independent-board/tenants') {
    const query = url.searchParams.get('query')
    if (query === 'error') {
      respondJson(response, 500, { code: 500, msg: 'Bounded fixture error.' })
    } else if (query === 'empty') {
      respondJson(response, 200, { code: 200, data: emptyPage })
    } else if (url.searchParams.get('cursor') === 'tenant-cursor-page2') {
      respondJson(response, 200, { code: 200, data: tenantPageTwo })
    } else {
      respondJson(response, 200, { code: 200, data: tenantPageOne })
    }
    return true
  }
  if (url.pathname === '/business/independent-board/oauth/clients') {
    respondJson(response, 200, {
      code: 200,
      data: {
        records: [{
          clientRef: 'cli_fixture_01',
          displayName: '未验证的本地公共客户端',
          status: 'ACTIVE',
          redirectUri: 'http://127.0.0.1:4321/oauth/callback',
          grantTypes: ['authorization_code', 'refresh_token'],
          responseTypes: ['code'],
          scopes: boardScopes,
          registeredAt: '2026-07-21T08:00:00+08:00',
          expiresAt: '2026-07-22T08:00:00+08:00',
          terminatedAt: null,
          version: 1,
          metadataDigestRef: 'sha256:0123456789abcdef',
          registrationSourceDigestRef: 'sha256:fedcba9876543210'
        }],
        limit: 100,
        truncated: false,
        nextCursor: null
      }
    })
    return true
  }
  if (url.pathname === '/business/independent-board/oauth/families'
      || url.pathname === '/business/independent-board/connector-bindings') {
    respondJson(response, 200, { code: 200, data: emptyPage })
    return true
  }
  if (url.pathname === '/my/independent-board/contexts') {
    respondJson(response, 200, {
      code: 200,
      data: [{
        tenantId: 101,
        memberId: 501,
        tenantName: '福帮手联调一号企业',
        memberRole: 'BOARD_MEMBER'
      }]
    })
    return true
  }
  if (url.pathname === '/my/independent-board/connector') {
    respondJson(response, 200, {
      code: 200,
      data: {
        tenantId: 101,
        memberId: 501,
        uiState: 'NOT_CONNECTED',
        effectivePlanCode: 'BOARD_FREE',
        clientRef: null,
        familyRef: null,
        bindingRef: null,
        scopes: [],
        issuedAt: null,
        expiresAt: null,
        lastSeenAt: null,
        version: 0,
        evidenceLevel: 'CURRENT_READ_COMPLETE'
      }
    })
    return true
  }
  return false
}

const server = http.createServer((request, response) => {
  const url = new URL(request.url, `http://${HOST}:${PORT}`)
  recordBoardRequest(request, url)

  if (url.pathname === '/__fixture/state') {
    respondJson(response, 200, { requests: requestLog })
    return
  }
  if (url.pathname === '/captchaImage' && request.method === 'GET') {
    respondJson(response, 200, { code: 200, captchaEnabled: false, uuid: 'w4b2c-browser-fixture' })
    return
  }
  if (url.pathname === '/login' && request.method === 'POST') {
    respondJson(response, 200, { code: 200, token: 'w4b2c-local-fixture-session' })
    return
  }
  if (url.pathname === '/logout' && request.method === 'POST') {
    respondJson(response, 200, { code: 200 })
    return
  }
  if (url.pathname === '/getInfo' && request.method === 'GET') {
    respondJson(response, 200, {
      code: 200,
      user: {
        userId: 1,
        userName: 'admin',
        nickName: 'W4b.2c 本地管理员',
        avatar: '',
        hostId: 'fixture-host'
      },
      roles: ['admin'],
      permissions: [
        'my:independent-board:connector:view',
        'board:tenant:query',
        'board:oauth:client:query',
        'board:oauth:family:query',
        'board:connector:query'
      ],
      isDefaultModifyPwd: false,
      isPasswordExpired: false
    })
    return
  }
  if (url.pathname === '/getRouters' && request.method === 'GET') {
    respondJson(response, 200, { code: 200, data: candidateRoutes })
    return
  }
  if (candidateResponse(request, response, url)) return

  respondJson(response, 200, { code: 200, data: [] })
})

server.listen(PORT, HOST, () => {
  process.stdout.write(`W4b.2c browser fixture listening on http://${HOST}:${PORT}\n`)
})

for (const signal of ['SIGINT', 'SIGTERM']) {
  process.on(signal, () => server.close(() => process.exit(0)))
}
