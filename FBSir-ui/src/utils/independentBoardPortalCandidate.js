const MOUNTABLE_ROUTE_IDENTITIES = Object.freeze(new Map([
  ['IndependentBoardConnectorCandidate',
    'business/independentBoard/me/connector/index'],
  ['IndependentBoardOAuthClientCandidate',
    'business/independentBoard/admin/oauth-client/index'],
  ['IndependentBoardOAuthFamilyCandidate',
    'business/independentBoard/admin/oauth-family/index'],
  ['IndependentBoardConnectorBindingCandidate',
    'business/independentBoard/admin/connector-binding/index']
]))

const HELD_ROUTE_IDENTITIES = Object.freeze(new Map([
  ['IndependentBoardSecurityCandidate',
    'business/independentBoard/me/security/index'],
  ['IndependentBoardSecurityEventCandidate',
    'business/independentBoard/admin/security-event/index']
]))

const CREDIT_ROUTE_IDENTITIES = Object.freeze(new Map([
  ['IndependentBoardCreditGovernance',
    'business/independentBoard/admin/credit/index']
]))

const mountableComponents = new Set(MOUNTABLE_ROUTE_IDENTITIES.values())
const heldComponents = new Set(HELD_ROUTE_IDENTITIES.values())
const creditComponents = new Set(CREDIT_ROUTE_IDENTITIES.values())

export function isBoardPortalCandidateEnabled(env = {}) {
  return env !== null
    && typeof env === 'object'
    && !Array.isArray(env)
    && env.VITE_FBSIR_BOARD_PORTAL_CANDIDATE === 'true'
}

export function assertBoardPortalCandidateEnabled(
  env = (typeof import.meta.env === 'object' ? import.meta.env : {})
) {
  if (!isBoardPortalCandidateEnabled(env)) {
    throw new Error('独董会门户候选功能默认关闭。')
  }
}

export function isBoardCreditCandidateEnabled(env = {}) {
  return env !== null
    && typeof env === 'object'
    && !Array.isArray(env)
    && env.VITE_FBSIR_BOARD_CREDIT_CANDIDATE === 'true'
}

export function admitIndependentBoardPortalCandidateRoutes(routes, env = {}) {
  if (!Array.isArray(routes)) return []
  const candidateState = Object.freeze({
    portalEnabled: isBoardPortalCandidateEnabled(env),
    creditEnabled: isBoardCreditCandidateEnabled(env)
  })
  return routes
    .map(route => admitRoute(route, candidateState))
    .filter(route => route !== null)
}

function admitRoute(route, candidateState) {
  if (route === null || typeof route !== 'object' || Array.isArray(route)) return null
  const name = typeof route.name === 'string' ? route.name : ''
  const component = typeof route.component === 'string' ? route.component : ''

  if (HELD_ROUTE_IDENTITIES.has(name) || heldComponents.has(component)) return null

  const expectedComponent = MOUNTABLE_ROUTE_IDENTITIES.get(name)
  const candidateIdentity = expectedComponent !== undefined || mountableComponents.has(component)
  if (candidateIdentity
      && (!candidateState.portalEnabled || expectedComponent !== component)) return null

  const expectedCreditComponent = CREDIT_ROUTE_IDENTITIES.get(name)
  const creditIdentity = expectedCreditComponent !== undefined || creditComponents.has(component)
  if (creditIdentity
      && (!candidateState.creditEnabled || expectedCreditComponent !== component)) return null

  const admitted = { ...route }
  if (Array.isArray(route.children)) {
    const children = route.children
      .map(child => admitRoute(child, candidateState))
      .filter(child => child !== null)
    if (children.length > 0) {
      admitted.children = children
    } else {
      delete admitted.children
      if (['Layout', 'ParentView'].includes(component)) return null
    }
  }
  return admitted
}
