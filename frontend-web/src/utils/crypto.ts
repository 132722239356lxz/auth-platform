/**
 * 密码加密传输工具 —— 使用 JSEncrypt（纯 JS RSA 实现）加密，不依赖 crypto.subtle
 *
 * 流程:
 * 1. 调用后端 /auth-server/api/crypto/public-key 获取 RSA 公钥（PEM 格式）
 * 2. 使用 JSEncrypt 库（PKCS#1 v1.5）加密密码
 * 3. 返回 Base64 编码的密文，后端使用私钥解密后再 BCrypt 校验
 *
 * 为什么用 JSEncrypt 而不是 Web Crypto API:
 * 某些 Chrome 扩展会劫持 window.crypto.subtle 导致 crypto.subtle.importKey
 * 返回 undefined。JSEncrypt 是纯 JavaScript 实现，不受浏览器扩展影响。
 *
 * 公钥缓存: 首次获取后缓存于内存中，服务重启后前端刷新页面自动重新获取。
 */

import JSEncrypt from 'jsencrypt'

const PUBLIC_KEY_URL = '/auth-server/api/crypto/public-key'

let cachedPublicKey: string | null = null

/** 获取 RSA 公钥 PEM 字符串（带缓存，刷新页面后自动重新获取） */
async function fetchPublicKeyPem(): Promise<string> {
  if (cachedPublicKey) return cachedPublicKey

  let res: Response
  try {
    res = await fetch(PUBLIC_KEY_URL)
  } catch (e: any) {
    console.error('[Crypto] 获取公钥请求失败:', e)
    throw new Error('无法连接到服务器，请检查网络连接')
  }

  if (!res.ok) {
    console.error('[Crypto] 公钥接口返回异常状态:', res.status)
    throw new Error(`获取加密公钥失败 (HTTP ${res.status})，请刷新页面后重试`)
  }

  let json: any
  try {
    json = await res.json()
  } catch (e: any) {
    console.error('[Crypto] 公钥响应解析失败:', e)
    throw new Error('公钥数据格式异常，请刷新页面后重试')
  }

  const pem: any = json?.data ?? json?.pem ?? json
  if (!pem || typeof pem !== 'string' || !pem.includes('PUBLIC KEY')) {
    console.error('[Crypto] 公钥内容无效:', typeof pem === 'string' ? pem.substring(0, 50) : JSON.stringify(pem)?.substring(0, 50))
    throw new Error('无效的公钥数据，请确认 auth-server 已启动后刷新页面重试')
  }

  cachedPublicKey = pem
  console.log('[Crypto] 公钥加载成功')
  return cachedPublicKey
}

/**
 * 加密密码 —— 使用 RSA 公钥加密后返回 Base64 密文
 *
 * @param plainPassword 明文密码
 * @returns Base64 编码的 RSA 密文
 * @throws 公钥获取失败或加密失败时抛出异常
 */
export async function encryptPassword(plainPassword: string): Promise<string> {
  console.log('[Crypto] 开始加密密码，长度:', plainPassword.length)

  const pem = await fetchPublicKeyPem()

  const encryptor = new JSEncrypt()
  encryptor.setPublicKey(pem)

  const encrypted = encryptor.encrypt(plainPassword)
  if (!encrypted) {
    console.error('[Crypto] JSEncrypt 加密返回空值')
    clearPublicKeyCache()
    throw new Error('密码加密失败，请刷新页面后重试')
  }

  console.log('[Crypto] 密码加密成功，密文长度:', encrypted.length)
  return encrypted
}

/** 清除缓存的公钥（强制重新获取，用于异常恢复） */
export function clearPublicKeyCache(): void {
  cachedPublicKey = null
}
