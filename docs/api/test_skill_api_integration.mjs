#!/usr/bin/env node
/**
 * OpenSpec #12 后端集成测试 - Skill API 端点
 * 测试 API Key 反查 userId 功能
 * 
 * 运行：node test_skill_api_integration.mjs
 */

const BASE = 'http://localhost:8080';
const LOGIN_PREFIX = ''; // 登录接口直接在根路径
const SKILL_API_PREFIX = '/fbs/skill-api';

let adminToken = null;
let testApiKey = process.env.FBS_TEST_API_KEY || null;
let testUserId = process.env.FBS_TEST_USER_ID ? parseInt(process.env.FBS_TEST_USER_ID) : null;

const results = [];
let passes = 0, fails = 0, warns = 0, skips = 0;

function logTest(tid, name, status, detail = '') {
  if (status === 'PASS') passes++;
  else if (status === 'FAIL') fails++;
  else if (status === 'WARN') warns++;
  else skips++;
  
  const icon = { PASS: '✅', FAIL: '❌', WARN: '⚠️', SKIP: '⏭️' }[status];
  results.push({ tid, name, status, detail: `${icon} ${detail}` });
  console.log(`  ${icon} ${tid} ${name} - ${detail}`);
}

async function fetchJson(method, url, options = {}) {
  try {
    const res = await fetch(url, {
      method,
      headers: {
        'Content-Type': 'application/json',
        ...options.headers
      },
      body: options.body ? JSON.stringify(options.body) : undefined,
      signal: AbortSignal.timeout(10000)
    });
    
    const json = await res.json();
    return { status: res.status, json };
  } catch (err) {
    return { status: 0, error: err.message };
  }
}

async function login() {
  console.log('\n[Step 1] 管理员登录...');
  
  const { status, json, error } = await fetchJson('POST', `${BASE}${LOGIN_PREFIX}/login`, {
    body: { username: 'admin', password: 'admin123' }
  });
  
  if (status === 200 && json && json.token) {
    adminToken = json.token;
    logTest('A0', '管理员登录', 'PASS', `token=${json.token.substring(0, 20)}...`);
    return true;
  } else {
    logTest('A0', '管理员登录', 'FAIL', `HTTP ${status}, ${error || json?.msg || '未知错误'}`);
    return false;
  }
}

async function getOrCreateTestApiKey() {
  console.log('\n[Step 2] 获取测试 API Key...');
  
  // 尝试获取现有的 API Key（使用正确的路径）
  const { status, json } = await fetchJson('GET', 
    `${BASE}/fbs/business/fbs/my-apikey/list`, {
    headers: { Authorization: adminToken }
  });
  
  if (status === 200 && json && json.rows && json.rows.length > 0) {
    const key = json.rows.find(k => k.status === 0);
    if (key) {
      testApiKey = key.apiKey;
      testUserId = key.userId;
      logTest('A1', '获取 API Key', 'PASS', `apiKey=${testApiKey.substring(0, 15)}..., userId=${testUserId}`);
      return true;
    }
  }
  
  // 如果没有现有的 Key，创建新的
  console.log('  ⚠️ 未找到现有 API Key，尝试创建新的...');
  
  const { status: createStatus, json: createJson } = await fetchJson('POST',
    `${BASE}/fbs/business/fbs/my-apikey`, {
    headers: { Authorization: adminToken },
    body: { keyName: `test-skill-api-${Date.now()}` }
  });
  
  if (createStatus === 200 && createJson && createJson.data) {
    testApiKey = createJson.data.apiKey;
    testUserId = createJson.data.userId;
    logTest('A1', '创建 API Key', 'PASS', `apiKey=${testApiKey.substring(0, 15)}..., userId=${testUserId}`);
    return true;
  }
  
  // 如果创建也失败，提示用户手动创建
  console.log('\n  ❌ 无法自动获取或创建 API Key');
  console.log('  请手动执行以下步骤：');
  console.log('  1. 访问 http://localhost:8080');
  console.log('  2. 登录后进入"我的 API Key"页面');
  console.log('  3. 创建一个新的 API Key');
  console.log('  4. 将 API Key 填入下方环境变量：');
  console.log('\n  $env:FBS_TEST_API_KEY = "你的API Key"');
  console.log('  $env:FBS_TEST_USER_ID = "你的用户ID"');
  
  // 尝试从环境变量获取
  const envApiKey = process.env.FBS_TEST_API_KEY;
  const envUserId = process.env.FBS_TEST_USER_ID;
  
  if (envApiKey && envUserId) {
    testApiKey = envApiKey;
    testUserId = parseInt(envUserId);
    logTest('A1', '使用环境变量 API Key', 'PASS', `apiKey=${testApiKey.substring(0, 15)}..., userId=${testUserId}`);
    return true;
  }
  
  logTest('A1', '获取 API Key', 'FAIL', '无法获取 API Key');
  return false;
}

async function testUserInfoWithApiKey() {
  console.log('\n=== 测试 /user/info API Key 反查 ===');
  
  // 1. 不传 userId，使用 API Key 反查
  const { status, json } = await fetchJson('POST',
    `${BASE}${SKILL_API_PREFIX}/user/info`, {
    headers: { 'X-FBS-API-Key': testApiKey },
    body: {}
  });
  
  if (status === 200 && json && json.code === 200) {
    logTest('T5.1.1', '/user/info 不传 userId', 'PASS', 
      `userId=${json.data.userId}, balance=${json.data.pointsBalance}`);
  } else {
    logTest('T5.1.1', '/user/info 不传 userId', 'FAIL', 
      `HTTP ${status}, code=${json?.code}, msg=${json?.msg}`);
  }
  
  // 2. 传 userId，向后兼容测试
  const { status: status2, json: json2 } = await fetchJson('POST',
    `${BASE}${SKILL_API_PREFIX}/user/info`, {
    headers: { 'X-FBS-API-Key': testApiKey },
    body: { userId: testUserId }
  });
  
  if (status2 === 200 && json2 && json2.code === 200) {
    logTest('T5.1.2', '/user/info 传 userId', 'PASS', '向后兼容正常');
  } else {
    logTest('T5.1.2', '/user/info 传 userId', 'FAIL', 
      `HTTP ${status2}, msg=${json2?.msg}`);
  }
}

async function testUserInfoInvalidApiKey() {
  console.log('\n=== 测试无效 API Key ===');
  
  const { status, json } = await fetchJson('POST',
    `${BASE}${SKILL_API_PREFIX}/user/info`, {
    headers: { 'X-FBS-API-Key': 'invalid_key_12345' },
    body: {}
  });
  
  if (status === 401 || json?.code === 401) {
    logTest('T5.1.3', '无效 API Key', 'PASS', '正确返回 401');
  } else {
    logTest('T5.1.3', '无效 API Key', 'WARN', `HTTP ${status}（预期 401）`);
  }
}

async function testUsageConsumeWithApiKey() {
  console.log('\n=== 测试 /usage/consume API Key 反查 ===');
  
  const usageRecordId = `test-${Date.now()}-${Math.random().toString(36).substring(2, 8)}`;
  
  // 1. 不传 userId，使用 API Key 反查
  const { status, json } = await fetchJson('POST',
    `${BASE}${SKILL_API_PREFIX}/usage/consume`, {
    headers: { 'X-FBS-API-Key': testApiKey },
    body: {
      packCode: 'general',
      skillCode: 'FBS-BookWriter',
      usageRecordId,
      pointsAmount: 1
    }
  });
  
  if (status === 200 && json && json.code === 200) {
    logTest('T5.1.4', '/usage/consume 不传 userId', 'PASS', 
      `usageRecordId=${json.data.usageRecordId}, remain=${json.data.remainPoints}`);
    
    // 幂等性测试
    const { status: status2, json: json2 } = await fetchJson('POST',
      `${BASE}${SKILL_API_PREFIX}/usage/consume`, {
      headers: { 'X-FBS-API-Key': testApiKey },
      body: {
        packCode: 'general',
        skillCode: 'FBS-BookWriter',
        usageRecordId,
        pointsAmount: 1
      }
    });
    
    if (status2 === 200 && json2 && json2.code === 200 && 
        json2.data.usageRecordId === usageRecordId) {
      logTest('T5.1.5', '幂等性测试', 'PASS', '相同 usageRecordId 返回相同结果');
    } else {
      logTest('T5.1.5', '幂等性测试', 'WARN', '幂等性异常');
    }
  } else if (json && json.msg && json.msg.includes('余额不足')) {
    logTest('T5.1.4', '/usage/consume 不传 userId', 'WARN', '余额不足，但接口正常');
  } else {
    logTest('T5.1.4', '/usage/consume 不传 userId', 'FAIL', 
      `HTTP ${status}, code=${json?.code}, msg=${json?.msg}`);
  }
}

async function generateReport() {
  const reportPath = `./skill-api-test-result-${Date.now()}.md`;
  const timestamp = new Date().toLocaleString('zh-CN');
  
  const content = [
    `# OpenSpec #12 后端集成测试报告`,
    ``,
    `**时间**: ${timestamp}`,
    ``,
    `**结果**: ✅ 通过 ${passes} | ❌ 失败 ${fails} | ⚠️ 警告 ${warns} | ⏭️ 跳过 ${skips}`,
    ``,
    `## 测试详情`,
    ``,
    `| 测试项 | ID | 状态 | 详情 |`,
    `|--------|----|----|------|`,
    ...results.map(r => `| ${r.name} | ${r.tid} | ${r.status} | ${r.detail} |`)
  ].join('\n');
  
  const fs = await import('fs');
  fs.writeFileSync(reportPath, content, 'utf8');
  
  console.log('\n' + '='.repeat(60));
  console.log(`测试结果: ✅ 通过 ${passes} | ❌ 失败 ${fails} | ⚠️ 警告 ${warns} | ⏭️ 跳过 ${skips}`);
  console.log('='.repeat(60));
  console.log(`\n报告已保存: ${reportPath}`);
}

async function main() {
  console.log('='.repeat(60));
  console.log('OpenSpec #12 后端集成测试 - Skill API 端点');
  console.log(`时间: ${new Date().toLocaleString('zh-CN')}`);
  console.log('='.repeat(60));
  
  // 1. 登录
  const loginOk = await login();
  if (!loginOk) {
    console.error('\nFATAL: 登录失败，无法继续测试');
    process.exit(1);
  }
  
  // 2. 检查是否已有 API Key
  if (testApiKey && testUserId) {
    console.log('\n[Step 2] 使用环境变量中的测试 API Key...');
    logTest('A1', '环境变量 API Key', 'PASS', `apiKey=${testApiKey.substring(0, 15)}..., userId=${testUserId}`);
  } else {
    // 尝试获取或创建 API Key
    const apiKeyOk = await getOrCreateTestApiKey();
    if (!apiKeyOk) {
      console.error('\nFATAL: 无法获取 API Key，无法继续测试');
      console.error('\n请设置环境变量后重试：');
      console.error('  $env:FBS_TEST_API_KEY = "你的API Key"');
      console.error('  $env:FBS_TEST_USER_ID = "你的用户ID"');
      process.exit(1);
    }
  }
  
  // 3. 执行测试
  await testUserInfoWithApiKey();
  await testUserInfoInvalidApiKey();
  await testUsageConsumeWithApiKey();
  
  // 4. 生成报告
  await generateReport();
  
  // 退出码
  process.exit(fails > 0 ? 1 : 0);
}

main().catch(err => {
  console.error('FATAL:', err);
  process.exit(1);
});
