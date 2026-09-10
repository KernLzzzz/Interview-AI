<template>
  <div class="interview-experience">
    <div v-if="loading" class="centered"><span class="door-mark">IA</span><p>正在准备面试房间…</p></div>

    <template v-else-if="record">
      <section v-if="record.status === '待开始' || needsResume" class="lobby">
        <header class="topbar">
          <button class="quiet-button" type="button" @click="leaveLobby"><el-icon><ArrowLeft /></el-icon> 返回场景</button>
          <div class="brand"><span>IA</span><strong>INTERVIEW ROOM</strong></div>
          <div class="secure"><i></i> 安全候场中</div>
        </header>
        <main class="lobby-main">
          <div class="lobby-copy">
            <span class="eyebrow">ROOM {{ String(id).padStart(6, '0') }} · {{ modeLabel }}</span>
            <h1>{{ needsResume ? '重新连接设备，继续面试' : '准备好了，再推门入场。' }}</h1>
            <p>面试开始后将抽取 5 道题，限时 15 分钟。问题可以语音播报，你可以随时关闭麦克风或摄像头。</p>
            <div class="readiness-list">
              <div class="readiness-row" :class="{ ready: connectionReady }"><span>01</span><div><strong>网络连接</strong><small>服务可用，答题草稿会保存在本机</small></div><i></i></div>
              <div class="readiness-row" :class="{ ready: mode === 'text' || deviceReady }"><span>02</span><div><strong>{{ deviceTitle }}</strong><small>{{ deviceStatusText }}</small></div><i></i></div>
              <div class="readiness-row ready"><span>03</span><div><strong>隐私与录制</strong><small>媒体仅绑定本场面试，用于回看与评测</small></div><i></i></div>
            </div>
            <p v-if="mediaError" class="device-error">{{ mediaError }}</p>
            <div class="lobby-actions">
              <el-button v-if="mode !== 'text'" size="large" :loading="checkingDevice" @click="prepareDevices">重新检测设备</el-button>
              <el-button type="primary" size="large" :loading="starting" :disabled="mode !== 'text' && !deviceReady" @click="enterRoom">{{ needsResume ? '继续面试' : '进入面试厅' }} <el-icon><Right /></el-icon></el-button>
            </div>
          </div>
          <div class="device-preview">
            <div class="preview-frame">
              <video v-if="mode === 'video'" ref="lobbyVideo" autoplay muted playsinline />
              <div v-else-if="mode === 'voice'" class="voice-preview">
                <div class="voice-orbit"><el-icon><Microphone /></el-icon></div>
                <div class="audio-bars"><i v-for="n in 18" :key="n" :style="barStyle(n)" /></div>
                <strong>说句话，确认收音是否正常</strong>
              </div>
              <div v-else class="text-preview"><span>Aa</span><strong>文本专注模式</strong><small>不启用摄像头与麦克风</small></div>
              <div class="frame-label"><i :class="{ active: deviceReady || mode === 'text' }"></i>{{ mode === 'text' ? 'READY' : deviceReady ? 'DEVICE READY' : 'WAITING FOR DEVICE' }}</div>
              <i class="corner tl"></i><i class="corner tr"></i><i class="corner bl"></i><i class="corner br"></i>
            </div>
            <div class="preview-note"><el-icon><Lock /></el-icon><span>浏览器会在本地采集媒体，并在提交时加密传输至对象存储。</span></div>
          </div>
        </main>
      </section>

      <section v-else-if="record.status === '进行中' && questions.length" class="room">
        <header class="topbar room-topbar">
          <div class="brand"><span>IA</span><strong>INTERVIEW ROOM</strong></div>
          <div class="room-meta"><span>{{ record.interviewContext?.targetRole || record.scenarioName }}</span><i></i><span>ROOM {{ String(id).padStart(6, '0') }}</span></div>
          <div class="room-status"><span></span>{{ mode === 'text' ? '面试进行中' : 'REC' }} <b>{{ elapsedTime }}</b></div>
        </header>
        <main class="room-grid">
          <section class="stage">
            <div class="stage-rail"><span>LIVE</span><i></i><small>{{ modeLabel }}</small></div>
            <video v-if="mode === 'video'" ref="roomVideo" autoplay muted playsinline :class="{ concealed: !cameraEnabled }" />
            <div v-if="mode === 'video' && !cameraEnabled" class="camera-off"><span>{{ candidateInitial }}</span><p>摄像头已关闭</p></div>
            <div v-else-if="mode === 'voice'" class="voice-stage">
              <div class="candidate-disc"><span>{{ candidateInitial }}</span><i :style="{ transform: `scale(${1 + audioLevel / 170})` }"></i></div>
              <p>{{ micEnabled ? '正在聆听你的回答' : '麦克风已静音' }}</p>
              <div class="audio-bars stage-wave"><i v-for="n in 24" :key="n" :style="barStyle(n)" /></div>
            </div>
            <div v-else class="text-stage"><div class="focus-symbol"><span></span><span></span><b>Aa</b></div><h2>专注表达</h2><p>把思考组织成清晰、有证据的回答。</p></div>
            <div class="ai-presence"><div class="ai-pulse"><span>AI</span><i></i></div><div><small>INTERVIEWER</small><strong>AI 面试官</strong><p>{{ speaking ? '正在播报题目…' : '正在倾听与记录' }}</p></div></div>
            <div class="stage-caption"><span>当前问题</span><p>{{ currentQuestion.content }}</p></div>
          </section>

          <aside class="answer-console">
            <div class="console-head"><div><span class="eyebrow">QUESTION SET</span><strong>{{ currentIndex + 1 }} / {{ questions.length }}</strong></div><div class="countdown" :class="{ warning: remainingSeconds < 180 }"><small>剩余时间</small><b>{{ formatTime(remainingSeconds) }}</b></div></div>
            <div class="progress-track"><i :style="{ width: `${(currentIndex + 1) / questions.length * 100}%` }"></i></div>
            <div class="question-number">Q{{ String(currentIndex + 1).padStart(2, '0') }}</div>
            <div class="question-title-row"><h1>{{ currentQuestion.content }}</h1><button type="button" class="speak-button" :class="{ active: speaking }" title="播报当前题目" @click="speakQuestion"><el-icon><Headset /></el-icon></button></div>
            <div class="question-tags"><span>{{ difficultyLabel(currentQuestion.difficulty) }}</span><span>{{ currentQuestion.type || '综合能力' }}</span></div>
            <div class="answer-field">
              <div class="field-label"><span>{{ mode === 'text' ? '你的回答' : '语音转写 / 回答要点' }}</span><small>{{ answerLength }} / 2000</small></div>
              <el-input v-model="answers[currentQuestion.id]" type="textarea" :rows="8" maxlength="2000" :placeholder="answerPlaceholder" />
              <div v-if="mode !== 'text'" class="transcript-state"><i :class="{ live: recognitionActive }"></i>{{ speechStatus }}</div>
            </div>
            <div class="question-nav"><button v-for="(q, index) in questions" :key="q.id" type="button" :class="{ active: index === currentIndex, answered: isAnswered(q.id) }" @click="currentIndex = index">{{ index + 1 }}</button></div>
            <div class="console-actions"><el-button :disabled="currentIndex === 0" @click="currentIndex--"><el-icon><ArrowLeft /></el-icon> 上一题</el-button><el-button v-if="currentIndex < questions.length - 1" type="primary" @click="currentIndex++">下一题 <el-icon><ArrowRight /></el-icon></el-button><el-button v-else type="primary" @click="handleSubmit(false)">完成面试</el-button></div>
          </aside>
        </main>
        <footer class="control-dock">
          <div class="dock-hint"><i></i><span>录制文件将在提交时上传</span></div>
          <div class="dock-controls">
            <button v-if="mode !== 'text'" type="button" :class="{ off: !micEnabled }" @click="toggleMic"><el-icon><Microphone /></el-icon><span>{{ micEnabled ? '麦克风' : '已静音' }}</span></button>
            <button v-if="mode === 'video'" type="button" :class="{ off: !cameraEnabled }" @click="toggleCamera"><el-icon><VideoCamera /></el-icon><span>{{ cameraEnabled ? '摄像头' : '已关闭' }}</span></button>
            <button type="button" @click="speakQuestion"><el-icon><Headset /></el-icon><span>播报题目</span></button>
          </div>
          <el-button class="finish-button" :loading="submitting" @click="handleSubmit(false)">{{ uploadProgress ? `上传 ${uploadProgress}%` : '结束并提交' }}</el-button>
        </footer>
      </section>

      <section v-else class="centered"><span class="door-mark">IA</span><h1>{{ record.status === '已完成' ? '面试已经提交' : '本场面试已结束' }}</h1><p>{{ record.status === '已完成' ? 'AI 评测任务正在后台运行，你可以前往报告页查看进度。' : '你可以返回场景广场创建新的面试。' }}</p><el-button type="primary" @click="router.push(record.status === '已完成' ? `/records/${id}/report` : '/scenarios')">{{ record.status === '已完成' ? '查看评测报告' : '返回场景广场' }}</el-button></section>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { useRecordStore } from '@/stores/record'
import { uploadInterviewMedia } from '@/api/file'
import { useInterviewMedia, type InterviewMode } from '@/composables/useInterviewMedia'

const INTERVIEW_LIMIT_SECONDS = 900
const route = useRoute(); const router = useRouter(); const authStore = useAuthStore(); const recordStore = useRecordStore(); const id = Number(route.params.id)
const record = computed(() => recordStore.getById(id)); const questions = computed(() => record.value?.questionData ?? [])
const mode = computed<InterviewMode>(() => record.value?.interviewMode ?? 'text')
const modeLabel = computed(() => ({ text: '文本面试', voice: '语音面试', video: '视频面试' })[mode.value])
const deviceTitle = computed(() => mode.value === 'video' ? '摄像头与麦克风' : mode.value === 'voice' ? '麦克风' : '输入设备')
const candidateInitial = computed(() => authStore.username?.charAt(0).toUpperCase() || 'U')
const answers = ref<Record<number, string>>({}); const currentIndex = ref(0)
const currentQuestion = computed(() => questions.value[currentIndex.value] ?? { id: 0, content: '', difficulty: 1 })
const answerLength = computed(() => (answers.value[currentQuestion.value.id] ?? '').length)
const answeredCount = computed(() => questions.value.filter(q => isAnswered(q.id)).length)
const answerPlaceholder = computed(() => mode.value === 'text' ? '建议使用“观点—依据—案例—总结”的结构作答…' : '系统会尝试实时转写，你也可以在这里补充关键词和案例…')
const loading = ref(true); const starting = ref(false); const submitting = ref(false); const finished = ref(false); const checkingDevice = ref(false); const deviceReady = ref(false); const connectionReady = ref(navigator.onLine); const needsResume = ref(false); const uploadProgress = ref(0)
const lobbyVideo = ref<HTMLVideoElement | null>(null); const roomVideo = ref<HTMLVideoElement | null>(null)
const { stream, micEnabled, cameraEnabled, audioLevel, errorMessage: mediaError, prepare, startRecording, stopRecording, toggleMic, toggleCamera, release } = useInterviewMedia()

watch(stream, async media => { await nextTick(); if (lobbyVideo.value) lobbyVideo.value.srcObject = media; if (roomVideo.value) roomVideo.value.srcObject = media })
watch(roomVideo, el => { if (el) el.srcObject = stream.value }); watch(lobbyVideo, el => { if (el) el.srcObject = stream.value })
const deviceStatusText = computed(() => mode.value === 'text' ? '键盘输入已就绪，无需媒体权限' : checkingDevice.value ? '正在请求浏览器设备权限…' : deviceReady.value ? (mode.value === 'video' ? '画面与收音正常' : '麦克风收音正常') : '等待检测，请允许浏览器访问设备')
const barStyle = (n: number) => ({ height: `${8 + Math.max(4, audioLevel.value * ((n % 7 + 2) / 9))}%` })

async function prepareDevices() { checkingDevice.value = true; try { await prepare(mode.value); deviceReady.value = true } catch (error) { deviceReady.value = false; ElMessage.error(error instanceof Error ? error.message : '设备检测失败') } finally { checkingDevice.value = false } }
async function enterRoom() {
  starting.value = true
  try {
    if (mode.value !== 'text' && !deviceReady.value) await prepareDevices()
    if (mode.value !== 'text' && !deviceReady.value) return
    if (record.value?.status === '待开始') await recordStore.startRecord(id)
    if (mode.value !== 'text') startRecording(mode.value)
    needsResume.value = false; startTimer(); startRecognition(); await nextTick()
    if (roomVideo.value) roomVideo.value.srcObject = stream.value
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '进入面试厅失败') } finally { starting.value = false }
}
function leaveLobby() { release(); router.push(record.value?.status === '进行中' ? '/records' : '/scenarios') }

const remainingSeconds = ref(INTERVIEW_LIMIT_SECONDS); let timerHandle: ReturnType<typeof setInterval> | null = null
const elapsedTime = computed(() => formatTime(INTERVIEW_LIMIT_SECONDS - remainingSeconds.value))
function formatTime(sec: number) { const safe = Math.max(0, sec); return `${String(Math.floor(safe / 60)).padStart(2, '0')}:${String(safe % 60).padStart(2, '0')}` }
function startTimer() { stopTimer(); const started = record.value?.startedAt ? new Date(record.value.startedAt).getTime() : Date.now(); remainingSeconds.value = Math.max(0, INTERVIEW_LIMIT_SECONDS - Math.floor((Date.now() - started) / 1000)); timerHandle = setInterval(() => { remainingSeconds.value--; if (remainingSeconds.value <= 0) { stopTimer(); ElMessage.warning('时间到，正在自动提交'); void handleSubmit(true) } }, 1000) }
function stopTimer() { if (timerHandle) { clearInterval(timerHandle); timerHandle = null } }
function isAnswered(questionId: number) { return Boolean((answers.value[questionId] ?? '').trim()) }
function difficultyLabel(value: number) { return value === 1 ? '基础' : value === 2 ? '进阶' : '挑战' }

function draftKey() { return `interview_draft_${id}` }
function loadDraft() { try { const saved = JSON.parse(localStorage.getItem(draftKey()) || '{}'); if (saved.answers) Object.assign(answers.value, saved.answers) } catch { /* 忽略损坏草稿 */ } }
function saveDraft() { localStorage.setItem(draftKey(), JSON.stringify({ answers: answers.value, updatedAt: new Date().toISOString() })) }
let saveHandle: ReturnType<typeof setTimeout> | null = null
watch(answers, () => { if (saveHandle) clearTimeout(saveHandle); saveHandle = setTimeout(saveDraft, 500) }, { deep: true })

const speaking = ref(false)
function speakQuestion() { if (!('speechSynthesis' in window)) return ElMessage.info('当前浏览器不支持语音播报'); window.speechSynthesis.cancel(); const speech = new SpeechSynthesisUtterance(currentQuestion.value.content); speech.lang = 'zh-CN'; speech.rate = .92; speech.onstart = () => { speaking.value = true }; speech.onend = speech.onerror = () => { speaking.value = false }; window.speechSynthesis.speak(speech) }
interface RecognitionLike { lang: string; continuous: boolean; interimResults: boolean; onresult: (event: any) => void; onend: () => void; start: () => void; stop: () => void }
type RecognitionConstructor = new () => RecognitionLike
const recognitionConstructor = (window as typeof window & { SpeechRecognition?: RecognitionConstructor; webkitSpeechRecognition?: RecognitionConstructor }).SpeechRecognition ?? (window as typeof window & { webkitSpeechRecognition?: RecognitionConstructor }).webkitSpeechRecognition
const speechSupported = Boolean(recognitionConstructor); const recognitionActive = ref(false); let recognition: RecognitionLike | null = null
let pendingMediaBlob: Blob | null = null
const speechStatus = computed(() => speechSupported ? (recognitionActive.value ? '实时转写中' : '转写已暂停，可手动补充') : '当前浏览器不支持实时转写，请手动记录要点')
function startRecognition() { if (!speechSupported || mode.value === 'text' || recognition) return; recognition = new recognitionConstructor!(); recognition.lang = 'zh-CN'; recognition.continuous = true; recognition.interimResults = false; recognition.onresult = event => { let phrase = ''; for (let index = event.resultIndex; index < event.results.length; index++) phrase += event.results[index][0].transcript; const questionId = currentQuestion.value.id; answers.value[questionId] = `${answers.value[questionId] ?? ''}${phrase}`.slice(0, 2000) }; recognition.onend = () => { recognitionActive.value = false }; try { recognition.start(); recognitionActive.value = true } catch { recognitionActive.value = false } }
function stopRecognition() { if (recognition) { try { recognition.stop() } catch { /* noop */ } recognition = null }; recognitionActive.value = false }

async function handleSubmit(auto: boolean) {
  if (submitting.value) return
  if (!auto && !answeredCount.value) return ElMessage.warning('请至少回答一道题')
  if (!auto) { const confirmed = await ElMessageBox.confirm(`已完成 ${answeredCount.value}/${questions.value.length} 题。结束后将上传录制并启动 AI 评测，确认提交？`, '离开面试厅', { type: 'warning', confirmButtonText: '结束并提交' }).catch(() => false); if (!confirmed) return }
  submitting.value = true; stopTimer(); stopRecognition(); window.speechSynthesis?.cancel()
  try {
    let media: { mediaFileId: number; mediaDuration: number } | undefined
    if (mode.value !== 'text') { pendingMediaBlob ??= await stopRecording(); if (!pendingMediaBlob?.size) throw new Error('本场录制文件为空，请检查设备后重试'); const extension = pendingMediaBlob.type.includes('ogg') ? 'ogg' : pendingMediaBlob.type.includes('mp4') ? (mode.value === 'video' ? 'mp4' : 'm4a') : 'webm'; const file = new File([pendingMediaBlob], `interview-${id}-${Date.now()}.${extension}`, { type: pendingMediaBlob.type }); const uploaded = await uploadInterviewMedia(file, mode.value, value => { uploadProgress.value = value }); media = { mediaFileId: uploaded.fileId, mediaDuration: INTERVIEW_LIMIT_SECONDS - remainingSeconds.value } }
    const submission = questions.value.map(question => ({ questionId: question.id, answer: answers.value[question.id] ?? '' }))
    await recordStore.submitRecord(id, submission, media); pendingMediaBlob = null; localStorage.removeItem(draftKey()); finished.value = true; release(); ElMessage.success('面试已提交，AI 正在异步评测'); router.push(`/records/${id}/report`)
  } catch (error) { if (remainingSeconds.value > 0) startTimer(); ElMessage.error(error instanceof Error ? error.message : '提交失败，可再次点击提交重试上传') } finally { submitting.value = false; uploadProgress.value = 0 }
}

function markOnline() { connectionReady.value = true } function markOffline() { connectionReady.value = false }
onMounted(async () => { window.addEventListener('online', markOnline); window.addEventListener('offline', markOffline); try { await recordStore.fetchRecords(); if (!record.value) return router.push('/records'); loadDraft(); if (record.value.status === '进行中') { if (mode.value === 'text') startTimer(); else needsResume.value = true } else if (record.value.status === '待开始' && mode.value !== 'text') await prepareDevices(); else if (mode.value === 'text') deviceReady.value = true } finally { loading.value = false } })
onBeforeUnmount(() => { window.removeEventListener('online', markOnline); window.removeEventListener('offline', markOffline); stopTimer(); stopRecognition(); window.speechSynthesis?.cancel(); if (saveHandle) clearTimeout(saveHandle); if (!finished.value) saveDraft() })
</script>

<style scoped lang="scss">
.interview-experience{min-height:100vh;background:#0d1d2d;color:#edf5f7}.centered{min-height:100vh;display:grid;place-content:center;justify-items:center;gap:18px;text-align:center;background:radial-gradient(circle at 50% 38%,#1d4052 0,#0d1d2d 42%)}.centered h1{font-size:32px}.centered p{max-width:520px;color:#9cb1c0}.door-mark,.brand>span{display:grid;place-items:center;width:46px;height:46px;border:1px solid rgba(214,239,242,.5);border-radius:50%;font:700 13px/1 ui-monospace,monospace;letter-spacing:.08em}.lobby,.room{min-height:100vh;display:flex;flex-direction:column}.lobby{background:radial-gradient(circle at 78% 35%,rgba(35,96,108,.34),transparent 32%),linear-gradient(135deg,#102238,#0b1926)}
.topbar{height:74px;display:flex;align-items:center;justify-content:space-between;padding:0 38px;border-bottom:1px solid rgba(220,239,242,.12)}.brand{display:flex;align-items:center;gap:12px}.brand>span{width:36px;height:36px;font-size:10px}.brand strong{font:700 11px/1 ui-monospace,monospace;letter-spacing:.17em}.quiet-button{display:flex;align-items:center;gap:6px;color:#a9bac7;background:transparent;border:0;cursor:pointer}.secure{display:flex;align-items:center;gap:9px;color:#9ec5c8;font-size:12px}.secure i,.readiness-row>i{width:7px;height:7px;border-radius:50%;background:#52c5a7;box-shadow:0 0 0 5px rgba(82,197,167,.1)}
.lobby-main{width:min(1180px,calc(100% - 64px));flex:1;display:grid;grid-template-columns:.9fr 1.1fr;align-items:center;gap:8%;margin:auto;padding:54px 0}.eyebrow{color:#6fb8c2;font:700 10px/1.4 ui-monospace,monospace;letter-spacing:.2em}.lobby-copy h1{max-width:540px;margin:18px 0 16px;font-size:clamp(36px,4.5vw,62px);line-height:1.08;letter-spacing:-.04em}.lobby-copy>p{max-width:550px;color:#9eb0bd;line-height:1.8}.readiness-list{margin:34px 0 28px;border-top:1px solid rgba(255,255,255,.12)}.readiness-row{display:grid;grid-template-columns:42px 1fr 18px;align-items:center;min-height:70px;border-bottom:1px solid rgba(255,255,255,.12);opacity:.62}.readiness-row.ready{opacity:1}.readiness-row>span{color:#6f8493;font:600 10px/1 ui-monospace,monospace}.readiness-row div{display:flex;flex-direction:column;gap:5px}.readiness-row strong{font-size:14px}.readiness-row small{color:#8ea1af}.readiness-row>i{background:#657888;box-shadow:none}.readiness-row.ready>i{background:#52c5a7}.device-error{color:#ff9b92!important;font-size:13px}.lobby-actions{display:flex;gap:12px}
.preview-frame{position:relative;aspect-ratio:16/10;display:grid;place-items:center;overflow:hidden;background:#08141f;border:1px solid rgba(155,211,218,.22);box-shadow:0 34px 80px rgba(0,0,0,.35)}.preview-frame:after{content:'';position:absolute;inset:0;background:linear-gradient(180deg,transparent 55%,rgba(4,13,20,.65));pointer-events:none}.preview-frame video{width:100%;height:100%;object-fit:cover;transform:scaleX(-1)}.frame-label{position:absolute;z-index:2;left:22px;bottom:18px;display:flex;align-items:center;gap:8px;color:#a6b8c4;font:700 9px/1 ui-monospace,monospace;letter-spacing:.14em}.frame-label i{width:6px;height:6px;border-radius:50%;background:#748694}.frame-label i.active{background:#52c5a7}.corner{position:absolute;z-index:3;width:24px;height:24px;border-color:#80c0c7}.tl{top:12px;left:12px;border-top:1px solid;border-left:1px solid}.tr{top:12px;right:12px;border-top:1px solid;border-right:1px solid}.bl{bottom:12px;left:12px;border-bottom:1px solid;border-left:1px solid}.br{bottom:12px;right:12px;border-bottom:1px solid;border-right:1px solid}.preview-note{display:flex;gap:9px;margin-top:15px;color:#7f929f;font-size:11px;line-height:1.5}.voice-preview,.text-preview{display:flex;flex-direction:column;align-items:center;gap:14px;color:#cfdee4}.voice-orbit{display:grid;place-items:center;width:86px;height:86px;border:1px solid #397988;border-radius:50%;font-size:30px;box-shadow:0 0 0 18px rgba(34,111,126,.08)}.audio-bars{height:52px;display:flex;align-items:center;gap:4px}.audio-bars i{width:3px;min-height:3px;max-height:100%;background:#5eb9c4;transition:height .08s}.text-preview span{font:300 74px/1 Georgia,serif;color:#82bdc5}.text-preview small{color:#758b99}
.room{background:#0c1b2a}.room-topbar{height:64px}.room-meta{display:flex;align-items:center;gap:12px;color:#8fa3b1;font:600 10px/1 ui-monospace,monospace;letter-spacing:.08em}.room-meta i{width:3px;height:3px;border-radius:50%;background:#5b7181}.room-status{display:flex;align-items:center;gap:8px;color:#b9c7d0;font:600 10px/1 ui-monospace,monospace;letter-spacing:.12em}.room-status b{color:white;font-size:13px}.room-status>span{width:7px;height:7px;border-radius:50%;background:#e45f5f;animation:pulse 1.8s infinite}.room-grid{flex:1;min-height:0;display:grid;grid-template-columns:minmax(0,1.25fr) minmax(420px,.75fr)}
.stage{position:relative;min-height:560px;overflow:hidden;background:linear-gradient(145deg,#162e40,#0a1723 75%);border-right:1px solid rgba(255,255,255,.1)}.stage>video{width:100%;height:100%;object-fit:cover;transform:scaleX(-1);opacity:.88}.stage>video.concealed{opacity:0}.stage:after{content:'';position:absolute;inset:0;pointer-events:none;background:linear-gradient(180deg,rgba(4,14,22,.1),rgba(4,14,22,.65))}.stage-rail{position:absolute;z-index:4;top:28px;left:28px;display:flex;align-items:center;gap:10px;color:#d3e0e5;font:700 9px/1 ui-monospace,monospace;letter-spacing:.12em}.stage-rail>i{width:44px;height:1px;background:rgba(255,255,255,.35)}.stage-rail small{color:#8facb7}.camera-off,.voice-stage,.text-stage{position:absolute;inset:0;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:12px;color:#9db0bc}.camera-off span,.candidate-disc>span{display:grid;place-items:center;width:112px;height:112px;border-radius:50%;color:white;background:#205368;font-size:38px}.candidate-disc{position:relative}.candidate-disc>i{position:absolute;inset:-20px;border:1px solid rgba(96,190,200,.45);border-radius:50%;transition:transform .08s}.voice-stage p,.text-stage p{color:#8ba0ad}.stage-wave{width:210px;justify-content:center}.focus-symbol{position:relative;width:150px;height:150px;display:grid;place-items:center}.focus-symbol span{position:absolute;inset:0;border:1px solid rgba(102,184,193,.24);transform:rotate(45deg)}.focus-symbol span:nth-child(2){inset:18px}.focus-symbol b{font:300 45px/1 Georgia,serif;color:#8fc6cd}
.ai-presence{position:absolute;z-index:4;left:34px;bottom:138px;display:flex;align-items:center;gap:16px}.ai-pulse{position:relative;display:grid;place-items:center;width:58px;height:58px;border-radius:50%;background:rgba(11,29,42,.72);border:1px solid rgba(118,199,207,.55);backdrop-filter:blur(10px)}.ai-pulse span{font:700 12px/1 ui-monospace,monospace}.ai-pulse i{position:absolute;inset:-7px;border:1px solid rgba(100,187,197,.18);border-radius:50%;animation:ring 2.4s infinite}.ai-presence>div:last-child{display:flex;flex-direction:column;gap:3px;text-shadow:0 2px 8px #07131e}.ai-presence small{color:#7eaeb7;font:700 8px/1 ui-monospace,monospace;letter-spacing:.18em}.ai-presence strong{font-size:14px}.ai-presence p{color:#a3b5c0;font-size:11px}.stage-caption{position:absolute;z-index:4;left:34px;right:34px;bottom:28px;padding:18px 20px;background:rgba(6,19,29,.74);border-left:2px solid #4da1ad;backdrop-filter:blur(12px)}.stage-caption span{color:#6faeb7;font:700 8px/1 ui-monospace,monospace;letter-spacing:.16em}.stage-caption p{margin-top:7px;color:#f3f7f8;font-size:15px;line-height:1.55}
.answer-console{overflow-y:auto;padding:30px 34px;color:#173044;background:#f4f7f8}.console-head{display:flex;align-items:flex-end;justify-content:space-between}.console-head>div:first-child{display:flex;flex-direction:column;gap:5px}.console-head strong{font:700 16px/1 ui-monospace,monospace}.countdown{display:flex;align-items:flex-end;gap:8px}.countdown small{color:#728492;font-size:10px}.countdown b{color:#176f7f;font:700 20px/1 ui-monospace,monospace}.countdown.warning b{color:#ce5555}.progress-track{height:2px;margin:20px 0 38px;background:#dbe4e8}.progress-track i{display:block;height:100%;background:#247e8d;transition:width .25s}.question-number{color:#8da0ac;font:700 11px/1 ui-monospace,monospace;letter-spacing:.14em}.question-title-row{display:flex;align-items:flex-start;gap:14px;margin-top:10px}.question-title-row h1{flex:1;font-size:clamp(21px,2vw,28px);line-height:1.45;letter-spacing:-.02em}.speak-button{flex:0 0 36px;height:36px;border-radius:50%;color:#46707e;background:transparent;border:1px solid #c8d5da;cursor:pointer}.speak-button.active{color:white;background:#167487;border-color:#167487}.question-tags{display:flex;gap:8px;margin:14px 0 26px}.question-tags span{padding:5px 8px;color:#58707f;background:#e4ecef;font-size:10px;border-radius:2px}.answer-field{padding:18px;background:white;border:1px solid #d7e1e5;border-radius:5px}.field-label{display:flex;justify-content:space-between;margin-bottom:10px;font-size:11px;color:#536b7a}.field-label small{font-family:ui-monospace,monospace;color:#8b9ba5}.answer-field :deep(.el-textarea__inner){padding:12px 0;box-shadow:none;resize:none;font-size:14px;line-height:1.7}.transcript-state{display:flex;align-items:center;gap:7px;padding-top:10px;border-top:1px solid #edf1f3;color:#82939e;font-size:10px}.transcript-state i{width:6px;height:6px;border-radius:50%;background:#a9b5bc}.transcript-state i.live{background:#db5b5b;animation:pulse 1.5s infinite}.question-nav{display:flex;gap:7px;margin:24px 0}.question-nav button{width:31px;height:31px;border-radius:50%;border:1px solid #cbd7dc;color:#6f818d;background:transparent;cursor:pointer;font:600 11px/1 ui-monospace,monospace}.question-nav button.answered{color:white;background:#7d9ca6;border-color:#7d9ca6}.question-nav button.active{color:white;background:#167487;border-color:#167487;box-shadow:0 0 0 4px rgba(22,116,135,.12)}.console-actions{display:flex;justify-content:space-between}
.control-dock{min-height:76px;display:grid;grid-template-columns:1fr auto 1fr;align-items:center;padding:10px 28px;border-top:1px solid rgba(255,255,255,.1);background:#0a1723}.dock-hint{display:flex;align-items:center;gap:8px;color:#718795;font-size:10px}.dock-hint i{width:6px;height:6px;border-radius:50%;background:#e15b5b}.dock-controls{display:flex;gap:10px}.dock-controls button{min-width:76px;display:flex;flex-direction:column;align-items:center;gap:5px;padding:8px 12px;color:#b7c5ce;background:transparent;border:0;border-radius:5px;cursor:pointer}.dock-controls button:hover{background:rgba(255,255,255,.07)}.dock-controls button.off{color:#f08a82;background:rgba(224,84,84,.12)}.dock-controls .el-icon{font-size:18px}.dock-controls span{font-size:9px}.finish-button{justify-self:end;color:#fff;background:#a94646;border-color:#a94646}
@keyframes pulse{50%{opacity:.4}}@keyframes ring{50%{transform:scale(1.14);opacity:.25}}@media(max-width:900px){.lobby-main{grid-template-columns:1fr}.lobby-copy{order:2}.room-grid{grid-template-columns:1fr}.stage{min-height:44vh}.answer-console{min-height:56vh}.room-meta,.dock-hint{display:none}.control-dock{grid-template-columns:1fr auto}.dock-controls{justify-self:start}}@media(max-width:600px){.topbar{padding-inline:16px}.topbar>.brand strong{display:none}.lobby-main{width:calc(100% - 32px);padding:28px 0}.lobby-copy h1{font-size:36px}.room-grid{display:block}.stage{min-height:38vh}.stage-caption{left:16px;right:16px;bottom:16px}.ai-presence{left:18px;bottom:122px}.answer-console{padding:24px 18px 100px}.control-dock{position:fixed;z-index:10;left:0;right:0;bottom:0;padding:8px 12px}.dock-controls button{min-width:56px;padding-inline:5px}.finish-button{padding-inline:10px}.room-status{margin-left:auto}}@media(prefers-reduced-motion:reduce){*,*:before,*:after{animation:none!important;transition:none!important}}
</style>
