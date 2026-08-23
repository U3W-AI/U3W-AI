import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const ROOT = new URL('../', import.meta.url)
const read = relative => readFileSync(new URL(relative, ROOT), 'utf8')

const service = read(
  'FBSir-business/src/main/java/com/wx/fbsir/business/board/' +
    'attribution/service/IndependentBoardAttributionReadbackService.java')
const controller = read(
  'FBSir-business/src/main/java/com/wx/fbsir/business/board/' +
    'attribution/controller/IndependentBoardAttributionReadbackController.java')
const filter = read(
  'FBSir-business/src/main/java/com/wx/fbsir/business/board/' +
    'attribution/controller/IndependentBoardAttributionReadbackProtocolFilter.java')
const verifier = read(
  'FBSir-business/src/main/java/com/wx/fbsir/business/board/' +
    'attribution/receipt/BoardAttributionReadbackRequestV1Verifier.java')
const runtime = read(
  'FBSir-business/src/main/java/com/wx/fbsir/business/board/' +
    'attribution/config/IndependentBoardAttributionReadbackRuntimeInvariant.java')
const properties = read(
  'FBSir-business/src/main/java/com/wx/fbsir/business/board/' +
    'attribution/config/IndependentBoardAttributionProperties.java')
const yml = read('FBSir-admin/src/main/resources/application.yml')
const security = read(
  'FBSir-framework/src/main/java/com/wx/fbsir/framework/config/SecurityConfig.java')
const mapper = read(
  'FBSir-business/src/main/resources/mapper/board/attribution/' +
    'IndependentBoardAttributionV1Mapper.xml')
const proxyTest = read(
  'FBSir-business/src/test/java/com/wx/fbsir/business/board/' +
    'attribution/service/' +
    'IndependentBoardAttributionReadbackSpringProxyContractTest.java')

test('route is exact, POST-only, anonymous-HMAC and default-off', () => {
  assert.match(controller, /@Anonymous/)
  assert.match(controller, /authoritative-readback-enabled/)
  assert.match(controller,
    /\/internal\/independent-board\/attribution\/events\/readback/)
  assert.match(controller, /@PostMapping/)
  assert.doesNotMatch(controller, /@GetMapping|@RequestParam/)
  assert.match(yml,
    /authoritative-readback-enabled: \$\{FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_AUTHORITATIVE_READBACK_ENABLED:false\}/)
  assert.match(properties,
    /private boolean authoritativeReadbackEnabled = false;/)
})

test('decision responses bind request, status, freshness, release and jar', () => {
  assert.match(verifier, /FBSIR_INDEPENDENT_BOARD_READBACK_V1/)
  assert.match(verifier, /FBSIR_INDEPENDENT_BOARD_READBACK_RESPONSE_V1/)
  for (const field of [
    'method', 'path', 'httpStatus', 'requestDigest', 'requestNonceHash',
    'receiverReleaseId', 'receiverJarSha256', 'productCreditEligible'
  ]) assert.match(verifier, new RegExp(`values\\.put\\("${field}"`))
  assert.match(controller, /signResponse/)
  assert.match(controller, /HttpStatus\.SERVICE_UNAVAILABLE/)
  assert.match(controller, /RETRY_AFTER/)
})

test('primary reads are new repeatable server-read-only transaction', () => {
  assert.match(service, /readOnly = true/)
  assert.match(service, /Isolation\.REPEATABLE_READ/)
  assert.match(service, /Propagation\.REQUIRES_NEW/)
  assert.match(service, /timeout = 5/)
  const tx = service.indexOf('selectTransactionReadOnlyState')
  const event = service.indexOf('selectEventByEventId')
  const receipt = service.indexOf('selectEventByReceiptId')
  assert.ok(tx >= 0 && tx < event && event < receipt)
  assert.match(mapper, /SELECT @@transaction_read_only/)
  assert.match(proxyTest, /AopUtils\.isAopProxy\(service\)/)
  assert.match(proxyTest, /PROPAGATION_REQUIRES_NEW/)
  assert.match(proxyTest, /ISOLATION_REPEATABLE_READ/)
  assert.match(proxyTest, /getTimeout\(\)/)
  assert.match(proxyTest, /isReadOnly\(\)/)
  assert.doesNotMatch(service,
    /insertLedgerEvent|advanceJourneyHead|insertJourneyHeadIfAbsent|ForUpdate/)
})

test('readback code has no redis filesystem outbox journal or credit writer', () => {
  const combined = [service, controller, filter, verifier].join('\n')
  assert.doesNotMatch(combined,
    /Redis|RedisTemplate|FileOutputStream|Files\.write|outbox|BusinessEventLedger|RuntimeJournal|CreditService/)
})

test('strict body fence handles unknown length duplicate trailing and compression', () => {
  assert.match(filter, /MAX_REQUEST_BYTES = 16 \* 1024/)
  assert.match(filter, /STRICT_DUPLICATE_DETECTION/)
  assert.match(filter, /FAIL_ON_TRAILING_TOKENS/)
  assert.match(filter, /boundedBody/)
  assert.match(filter, /total > MAX_REQUEST_BYTES/)
  assert.match(filter, /CONTENT_ENCODING/)
  assert.match(filter, /REQUEST_FIELDS/)
})

test('runtime identity is the actual packaged ApplicationHome jar', () => {
  assert.match(runtime, /new ApplicationHome/)
  assert.match(runtime, /actual\.toRealPath/)
  assert.match(runtime, /!resolved\.equals\(running\)/)
  assert.match(runtime, /Files\.isRegularFile\(running/)
  assert.match(runtime, /sha256\(resolved\)/)
})

test('security permits only the two exact HMAC routes', () => {
  assert.match(security,
    /"\/internal\/independent-board\/attribution\/events",\s*"\/internal\/independent-board\/attribution\/events\/readback"/s)
  assert.doesNotMatch(security,
    /\/internal\/independent-board\/attribution\/\*\*/)
})
