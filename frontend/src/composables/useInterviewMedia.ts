import { computed, onBeforeUnmount, ref } from 'vue'

export type InterviewMode = 'text' | 'voice' | 'video'

export function useInterviewMedia() {
  const stream = ref<MediaStream | null>(null)
  const recorder = ref<MediaRecorder | null>(null)
  const recording = ref(false)
  const micEnabled = ref(true)
  const cameraEnabled = ref(true)
  const audioLevel = ref(0)
  const errorMessage = ref('')
  const chunks: Blob[] = []
  let audioContext: AudioContext | null = null
  let levelFrame = 0

  const hasMediaDevices = computed(() => Boolean(navigator.mediaDevices?.getUserMedia))

  async function prepare(mode: InterviewMode) {
    errorMessage.value = ''
    if (mode === 'text') return null
    if (!hasMediaDevices.value || !('MediaRecorder' in window)) throw new Error('当前浏览器不支持媒体采集与录制')
    release()
    try {
      stream.value = await navigator.mediaDevices.getUserMedia({
        audio: { echoCancellation: true, noiseSuppression: true, autoGainControl: true },
        video: mode === 'video' ? { width: { ideal: 1280 }, height: { ideal: 720 }, facingMode: 'user' } : false
      })
      micEnabled.value = true
      cameraEnabled.value = mode !== 'video' || Boolean(stream.value.getVideoTracks().length)
      monitorAudio(stream.value)
      return stream.value
    } catch (error) {
      errorMessage.value = error instanceof DOMException && error.name === 'NotAllowedError'
        ? '设备权限被拒绝，请在浏览器地址栏中允许访问后重试'
        : '无法连接面试设备，请检查麦克风和摄像头'
      throw new Error(errorMessage.value)
    }
  }

  function monitorAudio(media: MediaStream) {
    const AudioContextClass = window.AudioContext
    audioContext = new AudioContextClass()
    const analyser = audioContext.createAnalyser()
    analyser.fftSize = 256
    audioContext.createMediaStreamSource(media).connect(analyser)
    const values = new Uint8Array(analyser.frequencyBinCount)
    const tick = () => {
      analyser.getByteFrequencyData(values)
      audioLevel.value = Math.min(100, Math.round(values.reduce((sum, value) => sum + value, 0) / values.length * 1.35))
      levelFrame = requestAnimationFrame(tick)
    }
    tick()
  }

  function bestMime(mode: InterviewMode) {
    const choices = mode === 'video'
      ? ['video/webm;codecs=vp9,opus', 'video/webm;codecs=vp8,opus', 'video/webm', 'video/mp4']
      : ['audio/webm;codecs=opus', 'audio/webm', 'audio/ogg;codecs=opus', 'audio/mp4']
    return choices.find(type => MediaRecorder.isTypeSupported(type)) ?? ''
  }

  function startRecording(mode: InterviewMode) {
    if (mode === 'text') return
    if (!stream.value) throw new Error('请先完成设备检测')
    chunks.splice(0)
    const mimeType = bestMime(mode)
    recorder.value = new MediaRecorder(stream.value, mimeType ? { mimeType } : undefined)
    recorder.value.ondataavailable = event => { if (event.data.size) chunks.push(event.data) }
    recorder.value.start(1000)
    recording.value = true
  }

  function stopRecording(): Promise<Blob | null> {
    if (!recorder.value || recorder.value.state === 'inactive') return Promise.resolve(null)
    return new Promise(resolve => {
      const activeRecorder = recorder.value!
      activeRecorder.addEventListener('stop', () => {
        recording.value = false
        resolve(new Blob(chunks, { type: activeRecorder.mimeType || 'application/octet-stream' }))
      }, { once: true })
      activeRecorder.stop()
    })
  }

  function toggleMic() {
    micEnabled.value = !micEnabled.value
    stream.value?.getAudioTracks().forEach(track => { track.enabled = micEnabled.value })
  }

  function toggleCamera() {
    cameraEnabled.value = !cameraEnabled.value
    stream.value?.getVideoTracks().forEach(track => { track.enabled = cameraEnabled.value })
  }

  function release() {
    stream.value?.getTracks().forEach(track => track.stop())
    stream.value = null
    if (levelFrame) cancelAnimationFrame(levelFrame)
    if (audioContext && audioContext.state !== 'closed') void audioContext.close()
    audioContext = null
    audioLevel.value = 0
  }

  onBeforeUnmount(release)

  return {
    stream, recording, micEnabled, cameraEnabled, audioLevel, errorMessage,
    prepare, startRecording, stopRecording, toggleMic, toggleCamera, release
  }
}
