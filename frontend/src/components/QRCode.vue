<!--
  QR 二维码渲染组件。
  @author Focus
  @date 2026-06-11
-->
<template>
  <canvas ref="canvasRef" class="qr-canvas"></canvas>
</template>

<script setup>
import { ref, onMounted, watch, nextTick } from 'vue'
import QRCodeLib from 'qrcode'

const props = defineProps({
  value: { type: String, required: true },
  size: { type: Number, default: 80 },
  height: { type: Number, default: 0 }
})

const canvasRef = ref(null)
const effectiveSize = () => props.height > 0 ? props.height : props.size

async function render() {
  if (!canvasRef.value || !props.value) return
  try {
    const s = effectiveSize()
    await QRCodeLib.toCanvas(canvasRef.value, props.value, {
      width: s, height: s,
      margin: 1,
      color: { dark: '#000000', light: '#ffffff' }
    })
  } catch { /* 静默 */ }
}

onMounted(() => { nextTick(render) })
watch(() => props.value, () => { nextTick(render) })
</script>

<style scoped>
.qr-canvas { display: block; }
</style>
