import Foundation
import AVFoundation
import AudioToolbox

final class GlassBeatCoreAudioController {
    private var engine: AVAudioEngine?
    private var playerNode: AVAudioPlayerNode?
    private var eqNode: AVAudioUnitEQ?
    private var timePitchNode: AVAudioUnitTimePitch?

    private var audioFile: AVAudioFile?
    private var fileSampleRate: Double = 44100.0
    private var totalFrames: AVAudioFramePosition = 0
    private var startFrameOffset: AVAudioFramePosition = 0
    private var isPlayingState: Bool = false
    private var scheduleGeneration: Int = 0

    private var pendingSpeed: Float = 1.0
    private var pendingVolume: Float = 0.85
    private var pendingEqEnabled: Bool = false
    private var pendingEqMilliBels: [Int] = [0, 0, 0, 0, 0]

    private let centerFrequencies: [Float] = [60.0, 230.0, 910.0, 4000.0, 14000.0]

    private func send(_ line: String) {
        FileHandle.standardOutput.write((line + "\n").data(using: .utf8)!)
    }

    private func isAudioUnitAvailable() -> Bool {
        var desc = AudioComponentDescription(
            componentType: kAudioUnitType_Generator,
            componentSubType: kAudioUnitSubType_ScheduledSoundPlayer,
            componentManufacturer: kAudioUnitManufacturer_Apple,
            componentFlags: 0,
            componentFlagsMask: 0
        )
        return AudioComponentFindNext(nil, &desc) != nil
    }

    private func ensureEngineInitialized() -> Bool {
        if engine != nil { return true }
        guard isAudioUnitAvailable() else {
            return false
        }

        let eng = AVAudioEngine()
        let player = AVAudioPlayerNode()
        let eq = AVAudioUnitEQ(numberOfBands: 5)
        let pitch = AVAudioUnitTimePitch()

        eng.attach(player)
        eng.attach(eq)
        eng.attach(pitch)

        for i in 0..<5 {
            let band = eq.bands[i]
            band.filterType = .parametric
            band.frequency = centerFrequencies[i]
            band.bandwidth = 1.2
            band.gain = 0.0
            band.bypass = false
        }
        eq.bypass = !pendingEqEnabled
        pitch.rate = pendingSpeed

        self.engine = eng
        self.playerNode = player
        self.eqNode = eq
        self.timePitchNode = pitch

        applyEqualizerInternal(enabled: pendingEqEnabled, milliBels: pendingEqMilliBels)
        eng.mainMixerNode.outputVolume = pendingVolume
        return true
    }

    func load(path: String, startMs: Int64) {
        guard ensureEngineInitialized(),
              let engine = self.engine,
              let playerNode = self.playerNode,
              let eqNode = self.eqNode,
              let timePitchNode = self.timePitchNode else {
            send("ERROR\tCoreAudio units unavailable")
            return
        }

        stopInternal()
        let url = URL(fileURLWithPath: path)
        do {
            let file = try AVAudioFile(forReading: url)
            self.audioFile = file
            self.fileSampleRate = file.processingFormat.sampleRate
            self.totalFrames = file.length

            engine.disconnectNodeOutput(playerNode)
            engine.disconnectNodeOutput(eqNode)
            engine.disconnectNodeOutput(timePitchNode)

            engine.connect(playerNode, to: eqNode, format: file.processingFormat)
            engine.connect(eqNode, to: timePitchNode, format: file.processingFormat)
            engine.connect(timePitchNode, to: engine.mainMixerNode, format: file.processingFormat)

            if !engine.isRunning {
                try engine.start()
            }

            let targetFrame = AVAudioFramePosition((Double(startMs) / 1000.0) * fileSampleRate)
            scheduleFrom(frame: max(0, min(targetFrame, totalFrames)))

            let durationMs = Int64((Double(totalFrames) / max(1.0, fileSampleRate)) * 1000.0)
            let channels = file.processingFormat.channelCount
            send("LOADED\t\(durationMs)\t\(Int(fileSampleRate))\t\(channels)")
        } catch {
            send("ERROR\t\(error.localizedDescription)")
        }
    }

    private func scheduleFrom(frame: AVAudioFramePosition) {
        guard let file = audioFile, let playerNode = playerNode else { return }
        scheduleGeneration += 1
        let currentGen = scheduleGeneration
        playerNode.stop()

        let clampedFrame = max(0, min(frame, totalFrames))
        startFrameOffset = clampedFrame
        let remainingFrames = totalFrames - clampedFrame

        if remainingFrames > 0 {
            let count = AVAudioFrameCount(min(remainingFrames, AVAudioFramePosition(UInt32.max)))
            playerNode.scheduleSegment(
                file,
                startingFrame: clampedFrame,
                frameCount: count,
                at: nil,
                completionCallbackType: .dataPlayedBack
            ) { [weak self] _ in
                guard let self = self else { return }
                if self.scheduleGeneration == currentGen && self.isPlayingState {
                    self.isPlayingState = false
                    self.send("ENDED")
                }
            }
        }
    }

    func play() {
        guard audioFile != nil, let engine = engine, let playerNode = playerNode else { return }
        do {
            if !engine.isRunning {
                try engine.start()
            }
            if !playerNode.isPlaying {
                playerNode.play()
            }
            isPlayingState = true
            send("STATE\t1")
        } catch {
            send("ERROR\t\(error.localizedDescription)")
        }
    }

    func pause() {
        let currentPosMs = currentPositionMs()
        if let playerNode = playerNode, playerNode.isPlaying {
            playerNode.pause()
        }
        isPlayingState = false
        let targetFrame = AVAudioFramePosition((Double(currentPosMs) / 1000.0) * fileSampleRate)
        startFrameOffset = max(0, min(targetFrame, totalFrames))
        send("STATE\t0")
    }

    func stop() {
        stopInternal()
        send("STATE\t0")
    }

    private func stopInternal() {
        scheduleGeneration += 1
        isPlayingState = false
        playerNode?.stop()
        startFrameOffset = 0
    }

    func seek(ms: Int64) {
        guard audioFile != nil, let playerNode = playerNode else { return }
        let wasPlaying = isPlayingState
        let targetFrame = AVAudioFramePosition((Double(ms) / 1000.0) * fileSampleRate)
        scheduleFrom(frame: targetFrame)
        if wasPlaying {
            playerNode.play()
            isPlayingState = true
        }
        sendStatus()
    }

    func setSpeed(_ speed: Float) {
        let clamped = max(0.25, min(3.0, speed))
        pendingSpeed = clamped
        timePitchNode?.rate = clamped
    }

    func setVolume(_ volume: Float) {
        let clamped = max(0.0, min(1.0, volume))
        pendingVolume = clamped
        engine?.mainMixerNode.outputVolume = clamped
    }

    func setEqualizer(enabled: Bool, milliBels: [Int]) {
        pendingEqEnabled = enabled
        pendingEqMilliBels = milliBels
        applyEqualizerInternal(enabled: enabled, milliBels: milliBels)
    }

    private func applyEqualizerInternal(enabled: Bool, milliBels: [Int]) {
        guard let eqNode = eqNode else { return }
        eqNode.bypass = !enabled
        for i in 0..<min(5, milliBels.count) {
            let db = Float(milliBels[i]) / 100.0
            eqNode.bands[i].gain = max(-15.0, min(15.0, db))
            eqNode.bands[i].bypass = !enabled
        }
    }

    func currentPositionMs() -> Int64 {
        guard audioFile != nil, let playerNode = playerNode else { return 0 }
        if isPlayingState,
           let nodeTime = playerNode.lastRenderTime,
           let playerTime = playerNode.playerTime(forNodeTime: nodeTime) {
            let currentFrame = startFrameOffset + playerTime.sampleTime
            let clamped = max(0, min(currentFrame, totalFrames))
            return Int64((Double(clamped) / max(1.0, playerTime.sampleRate)) * 1000.0)
        } else {
            return Int64((Double(startFrameOffset) / max(1.0, fileSampleRate)) * 1000.0)
        }
    }

    func durationMs() -> Int64 {
        guard audioFile != nil else { return 0 }
        return Int64((Double(totalFrames) / max(1.0, fileSampleRate)) * 1000.0)
    }

    func sendStatus() {
        let pos = currentPositionMs()
        let dur = durationMs()
        let playing = isPlayingState ? 1 : 0
        send("POS\t\(pos)\t\(dur)\t\(playing)")
    }
}

let controller = GlassBeatCoreAudioController()

while let line = readLine() {
    let parts = line.split(separator: "\t", omittingEmptySubsequences: false).map(String.init)
    guard let cmd = parts.first else { continue }
    switch cmd {
    case "LOAD":
        if parts.count >= 2 {
            let path = parts[1]
            let startMs = parts.count >= 3 ? (Int64(parts[2]) ?? 0) : 0
            controller.load(path: path, startMs: startMs)
        }
    case "PLAY":
        controller.play()
    case "PAUSE":
        controller.pause()
    case "STOP":
        controller.stop()
    case "SEEK":
        if parts.count >= 2, let ms = Int64(parts[1]) {
            controller.seek(ms: ms)
        }
    case "SPEED":
        if parts.count >= 2, let rate = Float(parts[1]) {
            controller.setSpeed(rate)
        }
    case "VOLUME":
        if parts.count >= 2, let vol = Float(parts[1]) {
            controller.setVolume(vol)
        }
    case "EQ":
        if parts.count >= 7 {
            let enabled = parts[1] == "1"
            let gains = (2..<7).map { Int(parts[$0]) ?? 0 }
            controller.setEqualizer(enabled: enabled, milliBels: gains)
        }
    case "POS":
        controller.sendStatus()
    case "QUIT":
        controller.stop()
        exit(0)
    default:
        break
    }
}
