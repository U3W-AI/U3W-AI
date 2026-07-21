import test from 'node:test'
import assert from 'node:assert/strict'
import fs from 'node:fs'

const service = fs.readFileSync('FBSir-business/src/main/java/com/wx/fbsir/business/board/attribution/service/IndependentBoardAttributionEvidenceService.java', 'utf8')
const verifier = fs.readFileSync('FBSir-business/src/main/java/com/wx/fbsir/business/board/attribution/service/BoardAttributionEnvelopeVerifier.java', 'utf8')
const api2Interface = fs.readFileSync('FBSir-business/src/main/java/com/wx/fbsir/business/board/attribution/service/BoardAttributionApi2ReceiptVerifier.java', 'utf8')

test('Java attribution event binds receipt nonce to the issued challenge', () => {
  assert.match(service, /Objects\.equals\(challenge\.getNonceHash\(\),\s*event\.getReceiptNonceHash\(\)\)/)
})

test('Java attribution envelope requires a strictly later expiry', () => {
  assert.match(verifier, /!event\.getExpiresAt\(\)\.after\(event\.getIssuedAt\(\)\)/)
})

test('Java attribution dimensions reject the Node contract sensitive keyword set', () => {
  for (const keyword of ['authorization', 'cookie', 'token', 'password', 'secret', 'prompt', 'email', 'phone', 'mobile', 'subject', 'raw', 'content']) {
    assert.match(verifier, new RegExp(keyword))
  }
  assert.match(verifier, /SUSPICIOUS_DIMENSION/)
})

test('Java attribution writer requires a configured API2 receipt verifier bean', () => {
  assert.match(service, /ObjectProvider<BoardAttributionApi2ReceiptVerifier>/)
  assert.match(service, /getIfAvailable\(\)/)
  assert.match(service, /isConfigured\(\)/)
  assert.match(api2Interface, /BoardHostForwardingChallenge/)
})
