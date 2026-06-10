<!--
  二维码扫描组件 — 摄像头扫码 + 上传图片识别。
  @author Focus
  @date 2026-06-11
-->
<template>
  <!-- ========== 摄像头扫码弹窗 ========== -->
  <Teleport to="body">
    <el-dialog v-model="cameraVisible" title="扫二维码"
      width="min(520px, calc(100vw - 32px))" destroy-on-close
      @opened="startCamera" @closed="stopCamera">
      <div id="camera-scanner-area" class="camera-container"></div>
      <div v-if="cameraError" class="scan-error">
        <el-icon :size="16"><WarningFilled /></el-icon><span>{{ cameraError }}</span>
      </div>
      <template #footer>
        <el-button @click="cameraVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </Teleport>

  <!-- ========== 上传图片弹窗 ========== -->
  <Teleport to="body">
    <el-dialog v-model="uploadVisible" title="上传二维码图片"
      width="min(480px, calc(100vw - 32px))" destroy-on-close>
      <div class="upload-area">
        <el-upload :auto-upload="false" :limit="1" accept="image/*"
          :on-change="handleImageUpload" :file-list="fileList" drag>
          <el-icon :size="40"><UploadFilled /></el-icon>
          <div class="upload-text">将二维码图片拖到此处，或点击上传</div>
          <div class="upload-formats">支持 JPG / PNG / BMP 等常见图片格式</div>
        </el-upload>
        <div v-if="uploadScanning" class="upload-scanning">
          <el-icon :size="18" class="is-loading"><Loading /></el-icon><span>识别中...</span>
        </div>
        <div v-if="uploadResult" class="upload-result">
          <span>识别结果：</span><strong>{{ uploadResult }}</strong>
        </div>
        <div v-if="uploadError" class="scan-error">
          <el-icon :size="16"><WarningFilled /></el-icon><span>{{ uploadError }}</span>
        </div>
      </div>
      <template #footer>
        <div class="dialog-footer">
          <span class="footer-tip">通过位图识别引擎读取图片中的二维码。</span>
          <el-button @click="uploadVisible = false">关闭</el-button>
        </div>
      </template>
    </el-dialog>
  </Teleport>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { WarningFilled, UploadFilled, Loading } from '@element-plus/icons-vue'
import { Html5Qrcode } from 'html5-qrcode'

const emit = defineEmits(['scanned'])

// ==================== 摄像头 ====================
const cameraVisible = ref(false)
const cameraError = ref('')
let html5QrCode = null

async function startCamera() {
  cameraError.value = ''
  try {
    html5QrCode = new Html5Qrcode('camera-scanner-area')
    await html5QrCode.start(
      { facingMode: 'environment' },
      { fps: 10, qrbox: { width: 250, height: 250 }, aspectRatio: 1.0 },
      (text) => {
        stopCamera()
        emit('scanned', text)
        cameraVisible.value = false
        ElMessage.success(`识别成功：${text}`)
      },
      () => {}
    )
  } catch { cameraError.value = '无法启动摄像头，请检查浏览器权限。' }
}
function stopCamera() {
  if (html5QrCode) { try { html5QrCode.stop().catch(() => {}) } catch {}; html5QrCode = null }
}

// ==================== 图片上传（位图识别） ====================
const uploadVisible = ref(false)
const fileList = ref([])
const uploadScanning = ref(false)
const uploadResult = ref('')
const uploadError = ref('')

async function handleImageUpload(file) {
  uploadScanning.value = true; uploadResult.value = ''; uploadError.value = ''
  fileList.value = [file]
  try {
    // 用 html5-qrcode 的 scanFile 做真正的位图识别
    const id = 'qr-upload-' + Date.now()
    const el = document.createElement('div'); el.id = id; el.style.display = 'none'
    document.body.appendChild(el)
    const scanner = new Html5Qrcode(id)
    const result = await scanner.scanFile(file.raw, false)
    scanner.clear()
    document.body.removeChild(el)
    if (result) {
      uploadResult.value = result
      emit('scanned', result)
      ElMessage.success(`识别成功：${result}`)
      setTimeout(() => { uploadVisible.value = false; uploadResult.value = ''; fileList.value = [] }, 600)
    } else {
      uploadError.value = '未能识别图片中的二维码，请确保图片清晰且包含完整的二维码。'
    }
  } catch {
    uploadError.value = '未能识别图片中的二维码，请尝试摄像头扫码。'
  } finally { uploadScanning.value = false }
}

// ==================== 对外方法 ====================
function openCamera() { cameraVisible.value = true }
function openUpload() { uploadVisible.value = true }
defineExpose({ openCamera, openUpload })
</script>

<style scoped>
.camera-container { width: 100%; min-height: 300px; border-radius: 4px; overflow: hidden; }
.camera-container :deep(video) { width: 100%; border-radius: 4px; }
.upload-area { text-align: center; padding: 10px 0; }
.upload-text { font-size: 13px; color: var(--text-secondary); margin-top: 8px; }
.upload-formats { font-size: 11px; color: var(--text-placeholder); margin-top: 4px; }
.upload-scanning, .upload-result { margin-top: 12px; padding: 10px; background: #f0f9eb; border-radius: 4px; font-size: 14px; display: flex; align-items: center; justify-content: center; gap: 6px; }
.upload-scanning { background: #f7f9fc; }
.scan-error { margin-top: 12px; padding: 10px 14px; background: #fef0f0; border-radius: 4px; color: #f56c6c; font-size: 13px; display: flex; align-items: center; gap: 6px; }
.dialog-footer { display: flex; align-items: center; justify-content: space-between; width: 100%; }
.footer-tip { font-size: 12px; color: var(--text-secondary); }
</style>
