package com.signalassist.demo

import java.awt.Color
import java.awt.Graphics2D
import java.awt.image.BufferedImage
import java.io.File
import kotlin.math.roundToInt

/**
 * 제출용 시연 영상 하나: 실제 앱 화면 → 부산 센텀시티(해운대로) 주행 한 번을 두 부분으로.
 *  ① 앱 화면을 볼 때: 전방 3D·지도 + 실제 앱 화면 (노란불·빨간불·'지금 신호'·초록불)
 *  ② 내비를 볼 때: 내비 위 알림 카드 (빨간불·긴급 감속·초록불)
 *
 *   ./gradlew :app:testDebugUnitTest --tests '*DemoRecordingTest*' --tests '*NavCompanionTest*' -Precord=true
 *   ./gradlew :demo:renderSubmissionVideo     # build/video/SixthSense_시연영상.mp4
 */
fun main(args: Array<String>) {
    val out = File(args.firstOrNull() ?: "build/video/SixthSense_시연영상.mp4").absoluteFile
    val audio = AudioTimeline()
    encodeVideo(out, 1280, 720, audio) { emit -> SubmissionVideo(audio).render(emit) }
}

private class SubmissionVideo(private val audio: AudioTimeline) {
    private val img = BufferedImage(1280, 720, BufferedImage.TYPE_3BYTE_BGR)
    private val g: Graphics2D = img.createGraphics().apply { setupHints() }
    private var total = 0L

    /** 같은 주행 기록에서 ①과 ②를 나누는 지점 (초록불 출발 뒤, 70초) */
    private val split = 700

    fun render(emit: (BufferedImage) -> Unit): Long {
        card(emit, 6_500) { opening() }
        // 실제 앱 화면 3장
        val app = AppVideo(audio)
        app.total = total; app.staticScreens(emit); total = app.total
        // 센텀시티 소개 + 항공 시점
        card(emit, 4_500) { centumIntro(g, it) }
        val centum = CentumRun(audio, standaloneIndex = null, precompute = false)
        total += centum.flyover(g, img, emit, 7_000)
        // ① 앱 화면을 볼 때
        card(emit, 4_000) {
            part("①", "앱 화면을 볼 때", "왼쪽: 전방 시점(3D)과 실제 지도 · 오른쪽: SixthSense 실제 앱 화면",
                "노란불 → 빨간불 정차 → '지금 신호 확인' → 초록불 출발")
        }
        app.total = total
        app.scene(emit, "centum", "① 앱 화면을 볼 때 · 부산 센텀시티 해운대로 (색각이상)", centum = centum, to = split)
        total = app.total
        // ② 내비를 볼 때
        card(emit, 5_000) {
            part("②", "내비를 보며 달릴 때", "운전 중엔 내비를 봅니다. 알림은 내비 위 카드로 찾아갑니다.",
                "시동을 걸면 저절로 켜짐 · 모양 아이콘 + 두세 단어 + 한 문장 · '다시 듣기'·'지금 신호' 버튼")
        }
        val nav = NavVideo(audio)
        nav.total = total; nav.drive(emit, from = split); total = nav.total
        card(emit, 8_000) { closing() }
        g.dispose()
        return total
    }

    private fun card(emit: (BufferedImage) -> Unit, ms: Long, draw: (Long) -> Unit) {
        val frames = (ms * FPS / 1000.0).roundToInt()
        repeat(frames) { f ->
            val t = f * 1000L / FPS
            g.color = Pal.bg; g.fillRect(0, 0, 1280, 720)
            draw(t)
            fadeBlack(g, t, ms, 1280, 720)
            emit(img)
        }
        total += ms
    }

    private fun opening() {
        Fonts.center(g, "SixthSense", 640f, 210f, Fonts.b(72f), Pal.text)
        Fonts.center(g, "색·소리·작은 경고등에 기대는 차량 정보를, 운전자에게 맞는 다른 감각으로", 640f, 280f, Fonts.r(28f), Pal.sky)
        listOf(
            "색각이상 → 소리 패턴·음성·모양   ·   청각장애 → 화면 강조·카드   ·   저시력·고령 → 음성·큰 글씨",
            "운전 중에는 내비 위 알림 카드로   ·   시동을 걸면 저절로 켜짐",
        ).forEachIndexed { k, l -> Fonts.center(g, l, 640f, 370f + k * 44, Fonts.r(22f), Pal.text) }
        Fonts.center(g, "앱 화면은 SixthSense 0.2.0 코드를 실행해 녹화했습니다 (Robolectric)", 640f, 500f, Fonts.r(19f), Pal.dim)
        Fonts.center(g, "주행·신호는 부산 센텀시티 실제 지도(OpenStreetMap) + SUMO 교통 시뮬레이션 · 소리는 실제 앱의 알림음", 640f, 532f, Fonts.r(19f), Pal.dim)
    }

    private fun part(num: String, title: String, line1: String, line2: String) {
        Fonts.center(g, num, 640f, 230f, Fonts.b(60f), Pal.amber)
        Fonts.center(g, title, 640f, 310f, Fonts.b(48f), Pal.text)
        Fonts.center(g, line1, 640f, 390f, Fonts.r(25f), Pal.sky)
        Fonts.center(g, line2, 640f, 440f, Fonts.r(21f), Pal.dim)
    }

    private fun closing() {
        Fonts.center(g, "SixthSense", 640f, 150f, Fonts.b(52f), Pal.text)
        listOf(
            "확실할 때만 '초록불' · 애매하면 '직접 확인하세요'",
            "긴급 경고는 남은 제동 거리로 판단해 꼭 필요할 때만",
            "앱 화면을 볼 때는 화면이, 내비를 볼 때는 내비 위 카드가 알려 줌",
            "Pleos Vehicle State · NaviHelper로 벨트·문·방향지시등·경로까지 (SDK 연결 진행 중)",
            "Pleos에 카메라 API가 없어 신호 인식은 실제 지도 기반 3D 시뮬레이션으로 검증",
        ).forEachIndexed { k, l -> Fonts.center(g, l, 640f, 245f + k * 52, Fonts.r(24f), Pal.text) }
        Fonts.center(g, "예시 내비 화면은 실제 내비 앱이 아닙니다 · 지도 데이터 © OpenStreetMap contributors", 640f, 640f, Fonts.r(18f), Pal.dim)
    }
}
