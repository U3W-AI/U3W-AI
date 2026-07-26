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

test('Java attribution writer requires every successful predecessor sequence before inserting an event', () => {
  const mapper = fs.readFileSync('FBSir-business/src/main/java/com/wx/fbsir/business/board/attribution/mapper/IndependentBoardAttributionMapper.java', 'utf8')
  const mapperXml = fs.readFileSync('FBSir-business/src/main/resources/mapper/board/attribution/IndependentBoardAttributionMapper.xml', 'utf8')
  const insertAt = service.indexOf('mapper.insertEventIfAbsent(event)')
  const guardAt = service.indexOf('selectSuccessfulPriorSequenceNosForUpdate')
  const verifierAt = service.indexOf('trustedVerifier.verifyEvent(event, challenge, properties)')
  assert.ok(guardAt >= 0 && guardAt < insertAt)
  assert.ok(verifierAt >= 0 && guardAt < verifierAt && verifierAt < insertAt)
  assert.match(service, /for \(long expectedSequence = 1; expectedSequence < event\.getSequenceNo\(\); expectedSequence\+\+\)/)
  assert.match(service, /!priorSequenceNos\.contains\(expectedSequence\)/)
  assert.match(mapper, /List<Long>\s+selectSuccessfulPriorSequenceNosForUpdate/)
  assert.match(mapperXml, /<select id="selectSuccessfulPriorSequenceNosForUpdate" resultType="long">[\s\S]*challenge_id = #\{challengeId\}[\s\S]*server_binding_id = #\{serverBindingId\}[\s\S]*contract_id = #\{contractId\}[\s\S]*tenant_subject_digest = #\{tenantSubjectDigest\}[\s\S]*outcome = 'success'[\s\S]*entry_surface = 'official_entry'[\s\S]*sequence_no &lt; #\{beforeSequenceNo\}[\s\S]*ORDER BY sequence_no[\s\S]*FOR UPDATE/)
})

test('W1A idempotency re-read uses a locking read after the journey-head lock', () => {
  const ingest = fs.readFileSync('FBSir-business/src/main/java/com/wx/fbsir/business/board/attribution/service/IndependentBoardAttributionIngestService.java', 'utf8')
  const mapper = fs.readFileSync('FBSir-business/src/main/java/com/wx/fbsir/business/board/attribution/mapper/IndependentBoardAttributionV1Mapper.java', 'utf8')
  const mapperXml = fs.readFileSync('FBSir-business/src/main/resources/mapper/board/attribution/IndependentBoardAttributionV1Mapper.xml', 'utf8')
  const lockAt = ingest.indexOf('mapper.selectJourneyHeadForUpdate(sameBindingKey)')
  const rereadAt = ingest.indexOf('mapper.selectEventByEventIdForUpdate(event.getEventId())')
  assert.ok(lockAt >= 0 && rereadAt > lockAt)
  assert.match(ingest, /selectEventByReceiptIdForUpdate\(event\.getReceiptId\(\)\)/)
  assert.match(mapper, /selectEventByEventIdForUpdate/)
  assert.match(mapper, /selectEventByReceiptIdForUpdate/)
  assert.match(mapperXml, /<select id="selectEventByEventIdForUpdate"[\s\S]*FROM fbs_board_attr_event_v1[\s\S]*WHERE event_id = #\{eventId\}[\s\S]*FOR UPDATE/)
  assert.match(mapperXml, /<select id="selectEventByReceiptIdForUpdate"[\s\S]*FROM fbs_board_attr_event_v1[\s\S]*WHERE receipt_id = #\{receiptId\}[\s\S]*FOR UPDATE/)
})

test('W1A bare event ingest rejects NATURAL before any mapper interaction', () => {
  const ingest = fs.readFileSync('FBSir-business/src/main/java/com/wx/fbsir/business/board/attribution/service/IndependentBoardAttributionIngestService.java', 'utf8')
  const guardAt = ingest.indexOf('natural_requires_verified_host_forwarding_ack')
  const firstMapperReadAt = ingest.indexOf('mapper.selectEventByEventId')
  assert.ok(guardAt >= 0)
  assert.ok(firstMapperReadAt > guardAt)
})
