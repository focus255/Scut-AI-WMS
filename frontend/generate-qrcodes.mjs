/**
 * 从后端 API 拉取所有"待入库"条码，生成 QR 二维码 PNG 位图文件。
 * 用法：后端启动后运行 node generate-qrcodes.mjs
 * @author Focus
 * @date 2026-06-11
 */
import QRCodeLib from 'qrcode'
import fs from 'fs'
import path from 'path'

const BASE = 'http://localhost:8080/api'
const OUT = path.resolve('barcode-labels')

const loginRes = await fetch(`${BASE}/auth/login`, {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ username: 'admin', password: 'admin123' })
})
const loginData = await loginRes.json()
const token = loginData.data?.token
if (!token) { console.error('登录失败'); process.exit(1) }

const listRes = await fetch(`${BASE}/inbound/orders?page=1&size=50`, {
  headers: { 'Authorization': `Bearer ${token}` }
})
const listData = await listRes.json()
const orders = (listData.data?.records || []).filter(o => o.status !== '已完成')
if (orders.length === 0) { console.error('无待入库订单'); process.exit(1) }

const allBarcodes = []
for (const order of orders) {
  const detailRes = await fetch(`${BASE}/inbound/orders/${order.id}`, {
    headers: { 'Authorization': `Bearer ${token}` }
  })
  const detailData = await detailRes.json()
  for (const bc of (detailData.data?.barcodes || [])) {
    if (bc.status !== '在库' && bc.status !== '已出库') {
      allBarcodes.push(bc)
    }
  }
}
if (allBarcodes.length === 0) { console.error('无待入库条码'); process.exit(1) }

if (!fs.existsSync(OUT)) fs.mkdirSync(OUT)
for (const f of fs.readdirSync(OUT)) {
  fs.unlinkSync(path.join(OUT, f))
}

for (const bc of allBarcodes) {
  const fileName = `${bc.barcode.replace(/\|/g, '-')}.png`
  // qrcode.toFile 直接输出 PNG 位图，无需 canvas 依赖
  await QRCodeLib.toFile(path.join(OUT, fileName), bc.barcode, {
    width: 200, margin: 1,
    color: { dark: '#000000', light: '#ffffff' }
  })
  console.log(`✅ ${fileName}`)
}
console.log(`\n📦 ${allBarcodes.length} 个二维码 PNG → ${OUT}`)
