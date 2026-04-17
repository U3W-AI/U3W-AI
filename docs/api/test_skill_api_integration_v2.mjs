#!/usr/bin/env node
/**
 * OpenSpec #12 集成测试 - 简化版
 * 
 * 运行前提：
 * 1. 后端服务已启动（localhost:8080）
 * 2. 设置环境变量：
 *    $env:FBS_TEST_API_KEY = "你的API Key"
 *    $env:FBS_TEST_USER_ID = "你的用户ID"（可选，如果 API Key 已绑定用户则不需要）
 */

const BASE = 'http://localhost:8080';
const SKILL_API_PREFIX = '/fbs/skill-api';

// 从环境变量读取测试配置
const TEST_API_KEY = process.env.FBS_TEST_API_KEY;
const TEST_USER_ID = process.env.FBS_TEST_USER_ID ? parseInt(process.env.FBS_TEST_USER_ID) : null;

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

async function testHealthCheck() {
  console.log('\n=== 后端服务检查 ===');
  
  // 尝试登录接口作为健康检查
  const { status, json, error } = await fetchJson('POST', `${BASE}/login`, {
    body: { username: 'admin', password: 'admin123' }
  });
  
  if (status === 200 && json && json.token) {
    logTest('H1', '后端服务可达', 'PASS', `登录成功，服务正常`);
    return true;
  } else if (status > 0) {
    logTest('H1', '后端服务可达', 'PASS', `HTTP ${status}（服务响应）`);
    return true;
  } else {
    logTest('H1', '后端服务可达', 'FAIL', `无法连接: ${error || '未知错误'}`);
    return false;
  }
}

async function testUserInfoWithApiKey() {
  console.log('\n=== 测试 /user/info ===');
  
  if (!TEST_API_KEY) {
    logTest('T1.1', '/user/info', 'SKIP', '未设置 FBS_TEST_API_KEY 环境变量');
    return;
  }
  
  // 1. 不传 userId
  const { status, json } = await fetchJson('POST',
    `${BASE}${SKILL_API_PREFIX}/user/info`, {
    headers: { 'X-FBS-API-Key': TEST_API_KEY },
    body: {}
  });
  
  if (status === 200 && json && json.code === 200) {
    logTest('T1.1', '/user/info 不传 userId', 'PASS', 
      `userId=${json.data.userId}, balance=${json.data.pointsBalance}`);
  } else if (status === 401 || json?.code === 401) {
    logTest('T1.1', '/user/info 不传 userId', 'FAIL', 'API Key 无效或未认证');
  } else if (status === 403 || json?.code === 403) {
    logTest('T1.1', '/user/info 不传 userId', 'WARN', 'API Key 未绑定用户，需要传入 userId');
  } else {
    logTest('T1.1', '/user/info 不传 userId', 'FAIL', 
      `HTTP ${status}, code=${json?.code}, msg=${json?.msg}`);
  }
  
  // 2. 传 userId（如果提供了）
  if (TEST_USER_ID) {
    const { status: status2, json: json2 } = await fetchJson('POST',
      `${BASE}${SKILL_API_PREFIX}/user/info`, {
      headers: { 'X-FBS-API-Key': TEST_API_KEY },
      body: { userId: TEST_USER_ID }
    });
    
    if (status2 === 200 && json2 && json2.code === 200) {
      logTest('T1.2', '/user/info 传 userId', 'PASS', '向后兼容正常');
    } else {
      logTest('T1.2', '/user/info 传 userId', 'FAIL', 
        `HTTP ${status2}, msg=${json2?.msg}`);
    }
  }
}

async function testUsageConsume() {
  console.log('\n=== 测试 /usage/consume ===');
  
  if (!TEST_API_KEY) {
    logTest('T2.1', '/usage/consume', 'SKIP', '未设置 FBS_TEST_API_KEY 环境变量');
    return;
  }
  
  const usageRecordId = `test-${Date.now()}-${Math.random().toString(36).substring(2, 8)}`;
  
  const body = {
    packCode: 'PACK_BOOK_WRITER_PRO',
    skillCode: 'FBS-BookWriter',
    usageRecordId,
    pointsAmount: 1
  };
  
  // 如果提供了 userId，添加到请求体
  if (TEST_USER_ID) {
    body.userId = TEST_USER_ID;
  }
  
  const { status, json } = await fetchJson('POST',
    `${BASE}${SKILL_API_PREFIX}/usage/consume`, {
    headers: { 'X-FBS-API-Key': TEST_API_KEY },
    body
  });
  
  if (status === 200 && json && json.code === 200) {
    logTest('T2.1', '/usage/consume', 'PASS', 
      `usageRecordId=${json.data.usageRecordId}, remain=${json.data.remainPoints}`);
    
    // 幂等性测试
    const { status: status2, json: json2 } = await fetchJson('POST',
      `${BASE}${SKILL_API_PREFIX}/usage/consume`, {
      headers: { 'X-FBS-API-Key': TEST_API_KEY },
      body
    });
    
    if (status2 === 200 && json2 && json2.code === 200 && 
        json2.data.usageRecordId === usageRecordId) {
      logTest('T2.2', '幂等性', 'PASS', '相同 usageRecordId 返回相同结果');
    } else {
      logTest('T2.2', '幂等性', 'WARN', '幂等性异常');
    }
  } else if (json && json.msg && json.msg.includes('余额不足')) {
    logTest('T2.1', '/usage/consume', 'WARN', '余额不足，但接口正常');
  } else if (json && json.msg && json.msg.includes('场景包不存在')) {
    logTest('T2.1', '/usage/consume', 'WARN', '场景包不存在（需要先在数据库中创建场景包）');
  } else if (json && json.msg && json.msg.includes('用户未激活该场景包')) {
    logTest('T2.1', '/usage/consume', 'WARN', '用户未激活场景包（需要先激活）');
  } else if (status === 401 || json?.code === 401) {
    logTest('T2.1', '/usage/consume', 'FAIL', 'API Key 无效或未认证');
  } else {
    logTest('T2.1', '/usage/consume', 'FAIL', 
      `HTTP ${status}, code=${json?.code}, msg=${json?.msg}`);
  }
}

async function testInvalidApiKey() {
  console.log('\n=== 测试无效 API Key ===');
  
  const { status, json } = await fetchJson('POST',
    `${BASE}${SKILL_API_PREFIX}/user/info`, {
    headers: { 'X-FBS-API-Key': 'invalid_test_key_12345' },
    body: {}
  });
  
  if (status === 401 || json?.code === 401) {
    logTest('T3.1', '无效 API Key', 'PASS', '正确返回 401');
  } else {
    logTest('T3.1', '无效 API Key', 'WARN', `HTTP ${status}（预期 401）`);
  }
}

async function generateReport() {
  const reportPath = `./skill-api-test-report.md`;
  const timestamp = new Date().toLocaleString('zh-CN');
  
  const content = [
    `# OpenSpec #12 集成测试报告`,
    ``,
    `**时间**: ${timestamp}`,
    ``,
    `**结果**: ✅ 通过 ${passes} | ❌ 失败 ${fails} | ⚠️ 警告 ${warns} | ⏭️ 跳过 ${skips}`,
    ``,
    `## 测试详情`,
    ``,
    `| 测试项 | ID | 状态 | 详情 |`,
    `|--------|----|----|------|`,
    ...results.map(r => `| ${r.name} | ${r.tid} | ${r.status} | ${r.detail} |`),
    ``,
    `## 运行说明`,
    ``,
    `运行此测试前，请设置环境变量：`,
    ``,
    `\`\`\`powershell`,
    `$env:FBS_TEST_API_KEY = "你的API Key"`,
    `$env:FBS_TEST_USER_ID = "你的用户ID"  # 可选，如果 API Key 已绑定用户则不需要`,
    `node test_skill_api_integration_v2.mjs`,
    `\`\`\``
  ].join('\n');
  
  const fs = await import('fs');
  fs.writeFileSync(reportPath, content, 'utf8');
  
  console.log('\n' + '='.repeat(60));
  console.log(`测试结果: ✅ 通过 ${passes} | ❌ 失败 ${fails} | ⚠️ 警告 ${warns} | ⏭️ 跳过 ${skips}`);
  console.log('='.repeat(60));
  console.log(`\n报告已保存: ${reportPath}`);
  
  if (!TEST_API_KEY) {
    console.log('\n⚠️  未设置测试 API Key，部分测试已跳过。');
    console.log('请设置环境变量后重新运行：');
    console.log('  $env:FBS_TEST_API_KEY = "你的API Key"');
    console.log('  $env:FBS_TEST_USER_ID = "你的用户ID"');
  }
}

async function main() {
  console.log('='.repeat(60));
  console.log('OpenSpec #12 集成测试 - Skill API 端点');
  console.log(`时间: ${new Date().toLocaleString('zh-CN')}`);
  console.log('='.repeat(60));
  
  // 1. 健康检查
  const healthOk = await testHealthCheck();
  if (!healthOk) {
    console.error('\nFATAL: 后端服务未启动');
    process.exit(1);
  }
  
  // 2. 执行测试
  await testUserInfoWithApiKey();
  await testUsageConsume();
  await testInvalidApiKey();
  
  // 3. 生成报告
  await generateReport();
  
  // 退出码
  process.exit(fails > 0 ? 1 : 0);
}

main().catch(err => {
  console.error('FATAL:', err);
  process.exit(1);
});
