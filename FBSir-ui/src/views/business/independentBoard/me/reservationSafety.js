const OPERATION_ID_PATTERN = /^[A-Za-z0-9][A-Za-z0-9._:-]{7,127}$/
const OUTCOME_STATES = new Set(['DRAFT', 'UNKNOWN', 'NOT_FOUND'])

export function reservationMatchesSubmitted(meeting, submitted) {
  return isObject(meeting)
    && isObject(submitted)
    && meeting.operationId === submitted.operationId
    && meeting.status === 'RESERVED'
    && meeting.agendaCount === submitted.agendaCount
    && meeting.seatCount === submitted.seatCount
    && isNonNegativeSafeInteger(meeting.remainingCount)
}

export function persistDraftWithReadback(storage, key, draft) {
  if (!storage || typeof storage.setItem !== 'function' || typeof storage.getItem !== 'function') {
    throw new Error('DRAFT_STORAGE_UNAVAILABLE')
  }
  if (typeof key !== 'string' || key.length === 0 || !isValidDraft(draft)) {
    throw new Error('DRAFT_STORAGE_INPUT_INVALID')
  }
  const serialized = JSON.stringify(draft)
  storage.setItem(key, serialized)
  const readback = storage.getItem(key)
  if (readback !== serialized) throw new Error('DRAFT_STORAGE_READBACK_MISMATCH')
  const parsed = JSON.parse(readback)
  if (!isValidDraft(parsed) || JSON.stringify(parsed) !== serialized) {
    throw new Error('DRAFT_STORAGE_READBACK_INVALID')
  }
  return parsed
}

export function parseStoredDraft(serialized, expectedTenantId, expectedUserId) {
  if (typeof serialized !== 'string' || serialized.length === 0) return null
  try {
    const value = JSON.parse(serialized)
    if (!isValidDraft(value)
        || value.tenantId !== expectedTenantId
        || value.userId !== expectedUserId) return null
    return value
  } catch {
    return null
  }
}

function isValidDraft(value) {
  return isObject(value)
    && isPositiveSafeInteger(value.tenantId)
    && isPositiveSafeInteger(value.userId)
    && typeof value.operationId === 'string'
    && OPERATION_ID_PATTERN.test(value.operationId)
    && isPositiveSafeInteger(value.agendaCount)
    && value.agendaCount <= 30
    && isPositiveSafeInteger(value.seatCount)
    && value.seatCount <= 100
    && OUTCOME_STATES.has(value.outcomeState)
}

function isObject(value) {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
}

function isPositiveSafeInteger(value) {
  return Number.isSafeInteger(value) && value > 0
}

function isNonNegativeSafeInteger(value) {
  return Number.isSafeInteger(value) && value >= 0
}
