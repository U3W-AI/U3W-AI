export const PORTAL_ENTRY_PATH = '/portal-entry'
export const DEFAULT_PORTAL_PATH = '/index'
export const ME_PORTAL_HOSTNAME = 'me.u3w.com'
export const INDEPENDENT_BOARD_ROUTE_NAME = 'IndependentBoardMe'

export function normalizePortalHostname(hostname) {
  if (typeof hostname !== 'string') return ''
  return hostname.trim().replace(/\.$/, '').toLowerCase()
}

export function resolvePortalEntry(hostname, hasIndependentBoardRoute) {
  if (
    normalizePortalHostname(hostname) === ME_PORTAL_HOSTNAME &&
    hasIndependentBoardRoute === true
  ) {
    return { name: INDEPENDENT_BOARD_ROUTE_NAME }
  }
  return { path: DEFAULT_PORTAL_PATH }
}

export function resolveLoginDestination(explicitRedirect) {
  if (typeof explicitRedirect === 'string' && explicitRedirect.trim() !== '') {
    return explicitRedirect
  }
  return PORTAL_ENTRY_PATH
}
