import assert from 'node:assert/strict'
import fs from 'node:fs'
import {
  parseStoredDraft,
  persistDraftWithReadback,
  reservationMatchesSubmitted
} from '../src/views/business/independentBoard/me/reservationSafety.js'

const submitted = Object.freeze({
  operationId: 'board-20260720-safe-retry',
  agendaCount: 5,
  seatCount: 3
})
const reserved = {
  operationId: submitted.operationId,
  status: 'RESERVED',
  agendaCount: submitted.agendaCount,
  seatCount: submitted.seatCount,
  remainingCount: 2
}

assert.equal(reservationMatchesSubmitted(reserved, submitted), true)
for (const mutation of [
  { operationId: 'board-20260720-other-key' },
  { status: 'PENDING' },
  { status: 'COMPLETED' },
  { agendaCount: 4 },
  { seatCount: 4 },
  { remainingCount: null },
  { remainingCount: -1 }
]) {
  assert.equal(reservationMatchesSubmitted({ ...reserved, ...mutation }, submitted), false)
}

const draft = {
  tenantId: 7,
  userId: 42,
  operationId: submitted.operationId,
  agendaCount: submitted.agendaCount,
  seatCount: submitted.seatCount,
  outcomeState: 'UNKNOWN'
}
const memory = new Map()
const storage = {
  setItem(key, value) { memory.set(key, value) },
  getItem(key) { return memory.get(key) ?? null }
}
assert.deepEqual(persistDraftWithReadback(storage, 'draft-key', draft), draft)
assert.deepEqual(parseStoredDraft(memory.get('draft-key'), 7, 42), draft)
assert.equal(parseStoredDraft(memory.get('draft-key'), 8, 42), null)
assert.equal(parseStoredDraft('{broken', 7, 42), null)

assert.throws(() => persistDraftWithReadback({
  setItem() { throw new Error('denied') },
  getItem() { return null }
}, 'draft-key', draft))
assert.throws(() => persistDraftWithReadback({
  setItem() {},
  getItem() { return '{"different":true}' }
}, 'draft-key', draft), /DRAFT_STORAGE_READBACK_MISMATCH/)

const pageSource = fs.readFileSync(
  new URL('../src/views/business/independentBoard/me/index.vue', import.meta.url),
  'utf8'
)
const storageGateIndex = pageSource.indexOf('if (!persistCurrentReservationDraft())')
const sideEffectIndex = pageSource.indexOf('const response = await reserveIndependentBoardMeeting')
assert.ok(storageGateIndex >= 0 && storageGateIndex < sideEffectIndex)
assert.match(pageSource, /reservationMatchesSubmitted\(exactReadback\.meeting, submittedPayload\)/)
assert.match(pageSource, /reservationMatchesSubmitted\(dashboardReadback, submittedPayload\)/)
assert.match(pageSource, /reservationOutcomeState !== 'DRAFT'/)
assert.match(pageSource, /无法安全保存预约草稿/)

console.log('Independent Board UI safety verification passed: 19 assertions.')
