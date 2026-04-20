#!/usr/bin/env node
/**
 * CLI 代理脚本：从临时文件读取参数，调用 wecom-cli
 * 
 * 用法: node cli-proxy.js <cliPath> <category> <method> <paramsFile>
 * 
 * 解决 Windows Java ProcessBuilder 传递 JSON 参数时引号转义问题
 */
const { execFileSync } = require('child_process');
const fs = require('fs');
const path = require('path');

const [,, cliPath, category, method, paramsFile] = process.argv;

if (!cliPath || !category || !method || !paramsFile) {
    console.error('Usage: node cli-proxy.js <cliPath> <category> <method> <paramsFile>');
    process.exit(1);
}

// 从文件读取参数
const params = fs.readFileSync(paramsFile, 'utf-8');

try {
    const result = execFileSync(cliPath, [category, method, params], {
        encoding: 'utf-8',
        timeout: 30000,
        maxBuffer: 10 * 1024 * 1024
    });
    console.log(result);
} catch (err) {
    if (err.stdout) console.log(err.stdout);
    if (err.stderr) console.error(err.stderr);
    process.exit(err.status || 1);
}
