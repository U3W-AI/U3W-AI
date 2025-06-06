# Browser控制器接口文档

## 1. 接口概述
本接口文档描述了与浏览器自动化相关的登录状态检查、扫码登录等功能，支持腾讯元宝、元器代理、豆包等平台的登录管理。


## 2. 通用请求头
| 字段          | 类型   | 描述                 | 必填 |
|---------------|--------|----------------------|------|
| Content-Type  | string | application/x-www-form-urlencoded 或 multipart/form-data | 否   |


## 3. 接口详情

### 3.1 检查腾讯元宝登录状态
- **接口路径**：`/api/browser/checkLogin`
- **请求方法**：GET
- **功能描述**：检测用户在腾讯元宝主站的登录状态，返回账号或"false"（未登录）。

#### 3.1.1 入参
| 字段名   | 类型     | 说明               |
|----------|----------|--------------------|
| userId   | string   | 用户唯一标识       |

#### 3.1.2 出参
| 类型   | 描述                        |
|--------|---------------------------|
| string | 成功：已登录返回账号，未登录返回`"false"` |
|        | 失败：空字符串或异常信息              |

#### 3.1.3 请求示例
```http
GET /api/browser/checkLogin?userId=22
```

#### 3.1.4 响应示例
```json
"138****1234"  // 已登录
"false"        // 未登录
```


### 3.2 检查元器代理登录状态
- **接口路径**：`/api/browser/checkAgentLogin`
- **请求方法**：GET
- **功能描述**：检测用户在元器代理平台的登录状态（基于腾讯元宝会话）。

#### 3.2.1 入参与出参
同**3.1 检查腾讯元宝登录状态**，仅上下文为元器代理场景。


### 3.3 获取元器代理登录二维码
- **接口路径**：`/api/browser/getAgentQrCode`
- **请求方法**：GET
- **功能描述**：会自动打开扫码登录页面，直接进行扫码登录，登录成功返回账号。

#### 3.3.1 入参
| 字段名   | 类型     | 说明               |
|----------|----------|--------------------|
| userId   | string   | 用户唯一标识       |

#### 3.3.2 出参
| 类型   | 描述                                                                 |
|--------|----------------------------------------------------------------------|
| string | 成功：已登录返回账号，未登录返回`"false"` |
|        | 失败：空字符串或异常信息              |
#### 3.3.3 请求示例
```http
GET /api/browser/getAgentQrCode?userId=22
```

#### 3.3.4 响应示例
```json
"138****1234"  // 已登录
"false"        // 未登录
```


### 3.4 获取腾讯元宝登录二维码
- **接口路径**：`/api/browser/getYBQrCode`
- **请求方法**：GET
- **功能描述**：生成腾讯元宝主站登录二维码，逻辑与元器代理类似，登录后返回状态。

#### 3.4.1 入参与出参
同**3.3 获取元器代理登录二维码**，仅上下文为腾讯元宝场景。


### 3.5 检查豆包登录状态
- **接口路径**：`/api/browser/checkDBLogin`
- **请求方法**：GET
- **功能描述**：检测用户在豆包平台的登录状态，返回手机号或"false"。

#### 3.5.1 入参
| 字段名   | 类型     | 说明               |
|----------|----------|--------------------|
| userId   | string   | 用户唯一标识       |

#### 3.5.2 出参
| 类型   | 描述                                                                 |
|--------|----------------------------------------------------------------------|
| string | 成功：已登录返回手机号（如`138****1234`），未登录返回`"false"`       |

#### 3.5.3 请求示例
```http
GET /api/browser/checkDBLogin?userId=22
```
#### 3.5.4 响应示例
```json
"138****1234"  // 已登录
"false"        // 未登录
```

### 3.6 获取豆包登录二维码
- **接口路径**：`/api/browser/getDBQrCode`
- **请求方法**：GET
- **功能描述**：生成豆包登录二维码，直接在页面进行扫码登录，登录成功返回账号。

#### 3.6.1 入参
| 字段名   | 类型     | 说明               |
|----------|----------|--------------------|
| userId   | string   | 用户唯一标识       |

#### 3.6.2 出参
| 类型   | 描述                                                                 |
|--------|----------------------------------------------------------------------|
| string | 成功：已登录返回账号，未登录返回`"false"` |
|        | 失败：空字符串或异常信息              |
#### 3.6.3 请求示例
```http
GET /api/browser/getDBQrCode?userId=22
```

#### 3.6.4 响应示例
```json
"138****1234"  // 已登录
"false"        // 未登录
```

## 4. 错误处理
| 状态码 | 响应内容          | 描述                     |
|--------|-------------------|--------------------------|
| 200    | 正常返回值        | 接口调用成功             |
| 500    | "false" 或空字符串 | 服务器内部错误（如浏览器操作失败） |
| 400    | 参数缺失提示      | 未传递`userId`参数        |


## 5. 版本信息
- **接口版本**：V1.2
- **文档日期**：2025年05月28日
- **作者**：悟空共创（杭州）智能科技有限公司
