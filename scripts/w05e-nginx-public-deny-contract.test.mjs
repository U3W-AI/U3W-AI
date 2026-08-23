import assert from 'node:assert/strict'
import { createHash } from 'node:crypto'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const candidate = readFileSync(new URL(
  '../ops/api2/nginx/api2-fbss-w05e.conf', import.meta.url))
const text = candidate.toString('utf8')
const PREIMAGE_BYTES = 3591
const PREIMAGE_SHA =
  '21f7f6d12c94d2e7437bc5878a8fbd48073acad17b37cf6a8d8fb047180b5cd5'
const CANDIDATE_SHA =
  '357cfadc900f576ce0f0ab8ddc93f8e79e79469d075bb0203ea012d398e5cb32'
const PATH =
  '/prod-api/internal/independent-board/attribution/events/readback'
const sha = bytes => createHash('sha256').update(bytes).digest('hex')

test('candidate preserves the exact active snippet prefix', () => {
  assert.equal(candidate.length, 3896)
  assert.equal(sha(candidate.subarray(0, PREIMAGE_BYTES)), PREIMAGE_SHA)
  assert.equal(sha(candidate), CANDIDATE_SHA)
})

test('one exact no-store 404 deny owns the public readback path', () => {
  const escaped = PATH.replaceAll('/', '\\/')
  assert.equal((text.match(new RegExp(escaped, 'g')) || []).length, 1)
  assert.match(text, new RegExp(
    `location = ${escaped} \\{[\\s\\S]*` +
    'add_header Cache-Control "no-store" always;[\\s\\S]*' +
    'return 404;[\\s\\S]*\\}'))
  const block = text.slice(text.indexOf(`location = ${PATH}`))
  assert.doesNotMatch(block, /proxy_pass|alias|root\s/)
})

test('candidate contains no CRLF BOM wildcard or readback upstream', () => {
  assert.equal(candidate[0] === 0xef && candidate[1] === 0xbb && candidate[2] === 0xbf, false)
  assert.equal(text.includes('\r'), false)
  assert.doesNotMatch(text, /location\s+[~^]|readback[\s\S]*proxy_pass/)
})
