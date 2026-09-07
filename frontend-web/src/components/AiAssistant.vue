<template>
  <div
    v-if="!assistantHidden"
    class="ai-assistant-wrapper"
    :class="{ 'is-hidden': currentPath === '/ai/chat' }"
    @click="handleClick"
    @contextmenu.prevent="showContextMenu"
  >
    <el-tooltip :content="$t('ai.title')" placement="left" :show-after="300">
      <div class="ai-assistant-body">
        <!-- 天线 -->
        <div class="antenna">
          <div class="antenna-ball"></div>
        </div>
        <!-- 头部 -->
        <div class="robot-head">
          <!-- 眼睛 -->
          <div class="eyes">
            <div class="eye eye-left">
              <div class="pupil"></div>
            </div>
            <div class="eye eye-right">
              <div class="pupil"></div>
            </div>
          </div>
          <!-- 嘴巴 -->
          <div class="mouth">
            <div class="mouth-line"></div>
          </div>
          <!-- 腮红 -->
          <div class="blush blush-left"></div>
          <div class="blush blush-right"></div>
        </div>
        <!-- 波纹 -->
        <div class="pulse-ring ring-1"></div>
        <div class="pulse-ring ring-2"></div>
      </div>
    </el-tooltip>
    <!-- 气泡提示 -->
    <div class="bubble-tip" v-if="!dismissed && currentPath !== '/ai/chat'">
      <span>{{ $t('ai.tip') }}</span>
      <el-icon class="close-bubble" @click.stop="dismissed = true"><Close /></el-icon>
    </div>

    <!-- 右键菜单 -->
    <div
      v-if="contextMenuVisible"
      class="assistant-context-menu"
      :style="{ left: contextMenuPosition.x + 'px', top: contextMenuPosition.y + 'px' }"
      @click.stop
    >
      <div class="context-menu-item" @click="hideAssistant">
        <el-icon><Hide /></el-icon>
        <span>隐藏助手</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Hide } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const dismissed = ref(false)
const assistantHidden = ref(false)
const contextMenuVisible = ref(false)
const contextMenuPosition = ref({ x: 0, y: 0 })

const currentPath = computed(() => route.path)

const AI_ASSISTANT_HIDDEN_KEY = 'ai_assistant_hidden'

onMounted(() => {
  assistantHidden.value = localStorage.getItem(AI_ASSISTANT_HIDDEN_KEY) === 'true'
  document.addEventListener('click', closeContextMenu)
  window.addEventListener('ai-assistant-visibility-change', handleVisibilityChange)
})

onUnmounted(() => {
  document.removeEventListener('click', closeContextMenu)
  window.removeEventListener('ai-assistant-visibility-change', handleVisibilityChange)
})

function handleVisibilityChange(event: Event) {
  const customEvent = event as CustomEvent<{ visible: boolean }>
  assistantHidden.value = !customEvent.detail.visible
}

function handleClick() {
  router.push('/ai/chat')
}

function showContextMenu(event: MouseEvent) {
  contextMenuPosition.value = { x: event.clientX, y: event.clientY }
  contextMenuVisible.value = true
}

function closeContextMenu() {
  contextMenuVisible.value = false
}

function hideAssistant() {
  assistantHidden.value = true
  localStorage.setItem(AI_ASSISTANT_HIDDEN_KEY, 'true')
  contextMenuVisible.value = false
}
</script>

<style scoped lang="scss">
.ai-assistant-wrapper {
  position: fixed;
  right: 24px;
  bottom: 80px;
  z-index: 9999;
  cursor: pointer;
  user-select: none;
  transition: opacity 0.3s, transform 0.3s;

  &.is-hidden {
    opacity: 0;
    pointer-events: none;
    transform: scale(0.5);
  }
}

.ai-assistant-body {
  position: relative;
  width: 72px;
  height: 72px;
  animation: float 3s ease-in-out infinite;

  &:hover {
    animation: float 3s ease-in-out infinite, wiggle 0.4s ease-in-out;
    .pupil {
      transform: scale(0.6) translateY(-1px);
    }
    .mouth-line {
      width: 14px;
    }
  }

  &:active {
    transform: scale(0.9);
  }
}

// ========== 脉冲波纹 ==========
.pulse-ring {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  border-radius: 50%;
  border: 2px solid rgba(64, 158, 255, 0.4);
  pointer-events: none;

  &.ring-1 {
    width: 72px;
    height: 72px;
    animation: pulse 2s ease-out infinite;
  }
  &.ring-2 {
    width: 72px;
    height: 72px;
    animation: pulse 2s ease-out 1s infinite;
  }
}

// ========== 天线 ==========
.antenna {
  position: absolute;
  top: -12px;
  left: 50%;
  transform: translateX(-50%);
  width: 4px;
  height: 14px;
  background: linear-gradient(to top, #409eff, #66b1ff);
  border-radius: 2px 2px 0 0;
  z-index: 3;

  .antenna-ball {
    position: absolute;
    top: -6px;
    left: 50%;
    transform: translateX(-50%);
    width: 10px;
    height: 10px;
    background: #409eff;
    border-radius: 50%;
    box-shadow: 0 0 8px rgba(64, 158, 255, 0.6);
    animation: blink-ball 2s ease-in-out infinite;
  }
}

// ========== 头部主体 ==========
.robot-head {
  position: absolute;
  width: 72px;
  height: 72px;
  background: linear-gradient(135deg, #e8f4ff 0%, #d0e8ff 50%, #b8dcff 100%);
  border-radius: 20px;
  box-shadow:
    0 4px 16px rgba(64, 158, 255, 0.25),
    0 1px 4px rgba(0, 0, 0, 0.08),
    inset 0 2px 0 rgba(255, 255, 255, 0.6);
  border: 2px solid #a0cfff;
  z-index: 2;
  display: flex;
  align-items: center;
  justify-content: center;
}

// ========== 眼睛 ==========
.eyes {
  display: flex;
  gap: 14px;
  position: absolute;
  top: 18px;
}

.eye {
  width: 16px;
  height: 18px;
  background: white;
  border-radius: 50%;
  border: 2px solid #3a3a3a;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: inset 0 1px 2px rgba(0, 0, 0, 0.1);

  .pupil {
    width: 8px;
    height: 9px;
    background: #3a3a3a;
    border-radius: 50%;
    transition: transform 0.2s ease;
    position: relative;

    &::after {
      content: '';
      position: absolute;
      width: 3px;
      height: 3px;
      background: white;
      border-radius: 50%;
      top: 2px;
      right: 1px;
    }
  }
}

// ========== 嘴巴 ==========
.mouth {
  position: absolute;
  bottom: 18px;

  .mouth-line {
    width: 10px;
    height: 3px;
    background: #3a3a3a;
    border-radius: 0 0 4px 4px;
    transition: width 0.3s ease;
  }
}

// ========== 腮红 ==========
.blush {
  position: absolute;
  bottom: 16px;
  width: 10px;
  height: 6px;
  background: rgba(255, 154, 162, 0.45);
  border-radius: 50%;

  &.blush-left {
    left: 10px;
  }
  &.blush-right {
    right: 10px;
  }
}

// ========== 气泡提示 ==========
.bubble-tip {
  position: absolute;
  right: 84px;
  top: 50%;
  transform: translateY(-50%);
  background: white;
  border-radius: 12px;
  padding: 8px 14px;
  white-space: nowrap;
  font-size: 13px;
  color: #303133;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.12);
  animation: fadeInRight 0.4s ease-out;
  display: flex;
  align-items: center;
  gap: 8px;

  &::after {
    content: '';
    position: absolute;
    right: -6px;
    top: 50%;
    transform: translateY(-50%);
    width: 0;
    height: 0;
    border-top: 6px solid transparent;
    border-bottom: 6px solid transparent;
    border-left: 6px solid white;
  }

  .close-bubble {
    cursor: pointer;
    color: #c0c4cc;
    font-size: 14px;
    &:hover { color: #909399; }
  }
}

// ========== 右键菜单 ==========
.assistant-context-menu {
  position: fixed;
  z-index: 10000;
  background: white;
  border-radius: 8px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.12);
  padding: 4px 0;
  min-width: 120px;
  animation: fadeIn 0.15s ease-out;

  .context-menu-item {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 8px 14px;
    font-size: 13px;
    color: #606266;
    cursor: pointer;
    transition: background 0.2s, color 0.2s;

    &:hover {
      background: #f5f7fa;
      color: #409eff;
    }
  }
}

// ========== 动画 ==========
@keyframes float {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-8px); }
}

@keyframes wiggle {
  0%, 100% { transform: rotate(0); }
  25% { transform: rotate(-5deg); }
  50% { transform: rotate(5deg); }
  75% { transform: rotate(-3deg); }
}

@keyframes pulse {
  0% {
    width: 72px;
    height: 72px;
    opacity: 0.6;
  }
  100% {
    width: 110px;
    height: 110px;
    opacity: 0;
  }
}

@keyframes blink-ball {
  0%, 100% { opacity: 1; transform: translateX(-50%) scale(1); }
  50% { opacity: 0.6; transform: translateX(-50%) scale(0.7); }
}

@keyframes fadeInRight {
  from { opacity: 0; transform: translateY(-50%) translateX(10px); }
  to { opacity: 1; transform: translateY(-50%) translateX(0); }
}

@keyframes fadeIn {
  from { opacity: 0; transform: scale(0.95); }
  to { opacity: 1; transform: scale(1); }
}
</style>
