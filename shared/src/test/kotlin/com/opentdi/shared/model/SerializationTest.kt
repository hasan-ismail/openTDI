package com.opentdi.shared.model

import kotlin.random.Random
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SerializationTest {
    private val sessionId = "20261027T193400Z-1a2b"
    private val packet = BiosignalPacket(
        timestamp = 1_793_000_000_000L, hr = 63.5f, ibiList = listOf(812, 830), rmssd = 41.25f,
        skinConductance = null, accelRms = 0.0031f, edaStatus = -5, hrStatus = 1, accelMean = 1.002f,
        accelPeak = 0.012f, jerkEvent = true, limpEvent = false, stillnessScore = 0.98f,
        source = SensorSource.SAMSUNG_SDK,
    )
    private val score = DetectionScore(
        confidence = 0.925f, detectionActive = true, stillMean = 0.97f, movingSeconds = 1, gate = true,
        hrMa = 63.633f, hrDropFraction = 0.091f, hrSignal = true, rmssdRiseFraction = null, rmssdSignal = false,
        edaSlopeUsPerS = -0.002f, edaBonus = true, jerkBonus = false, limpBonus = false, holdS = 10, holdRequiredS = 10,
        refractory = false,
    )
    private val baseline = BaselineStats(
        startMs = 1_793_000_000_000L, endMs = 1_793_000_180_000L, windowS = 180, framesTotal = 180,
        framesMotionDiscarded = 3, hrMeanLive = 70.0078f, hrMean = 70.0078f, hrSd = 0.6403f, hrN = 177,
        priorRestingHrBpm = null, priorHrWeightApplied = 0f, rmssdMean = 40.0067f, rmssdSd = 2.1f, rmssdN = 170,
        edaMean = null, edaSd = null, edaN = 0, accelNoiseFloorP95G = 0.0042f,
    )
    private val config = SessionConfig(
        revision = 1_793_000_000_123L, mode = SessionMode.TIMED_CUES, theme = "a \"forest\" tree",
        detection = DetectionConfig(
            sensitivity = SensitivityPreset.HIGH,
            hrDropFraction = 0.06f,
            priorRestingHrBpm = 58f,
        ),
        audio = AudioConfig(
            route = AudioRouteMode.BLUETOOTH_SLEEP_AUDIO,
            routeCapGain = AudioRouteMode.entries.associateWith { (it.ordinal + 1) / 10f },
            carrierMode = CarrierMode.NOISE,
        ),
        simulation = SimulationMode.FAST_HYPNAGOGIA,
    )

    private inline fun <reified T> roundTrip(value: T) =
        assertEquals(value, OpenTdiJson.decodeFromString<T>(OpenTdiJson.encodeToString(value)))

    @Test
    fun everyContract_roundTrips() {
        roundTrip(packet)
        roundTrip(packet.copy(hr = null, ibiList = emptyList(), stillnessScore = null, accelRms = null))
        roundTrip(score)
        roundTrip(baseline)
        roundTrip(
            HypnagogiaTrigger(
                sessionId = sessionId, cycle = 1, onsetTimestampMs = 1_793_000_346_000L, countdownMs = 180_000,
                confidence = 0.925f, evidence = score, baseline = baseline, preset = SensitivityPreset.MIT_STANDARD,
                source = SensorSource.ANDROID_SENSORS, sentAtWallMs = 1_793_000_346_120L,
            ),
        )
        val frame = TelemetryFrame(
            seq = 346, packet = packet, state = TdiState.HYPNAGOGIA_DETECTED, stateReason = StateReason.ONSET,
            completedCycles = 0, score = score,
        )
        roundTrip(frame)
        roundTrip(frame.copy(stateReason = null, score = null))
        roundTrip(
            TelemetryBatch(
                sessionId = sessionId, batchSeq = 70, sensorStatus = SensorStatus.CONNECTED_SAMSUNG,
                gravityMode = GravityMode.LINEAR_ONLY, frames = listOf(frame, frame.copy(seq = 347)), sentAtWallMs = 1L,
            ),
        )
        roundTrip(Ack(ok = false, code = "PROTOCOL_MISMATCH"))
        roundTrip(SessionConfig())
        roundTrip(config)
    }

    private inline fun <reified E : Enum<E>> enumOnTheWire() = enumValues<E>().forEach {
        assertEquals("\"${it.name}\"", OpenTdiJson.encodeToString(it))
        roundTrip(it)
    }

    @Test
    fun everyEnumValue_isItsName() {
        enumOnTheWire<TdiState>()
        enumOnTheWire<SessionMode>()
        enumOnTheWire<PromptCue>()
        enumOnTheWire<AudioRouteMode>()
        enumOnTheWire<SensitivityPreset>()
        enumOnTheWire<SensorSource>()
        enumOnTheWire<SimulationMode>()
        enumOnTheWire<CycleStageType>()
        enumOnTheWire<CycleOutcome>()
        enumOnTheWire<CountdownAbortReason>()
        enumOnTheWire<DeviceRole>()
        enumOnTheWire<SessionEndReason>()
        enumOnTheWire<StateReason>()
        enumOnTheWire<SensorStatus>()
        enumOnTheWire<GravityMode>()
        enumOnTheWire<SleepStateAnswer>()
        enumOnTheWire<CarrierMode>()
        enumOnTheWire<FocusMode>()
        enumOnTheWire<RouteLossPolicy>()
        enumOnTheWire<EndAlarm>()
        enumOnTheWire<PriorHrSource>()
    }

    @Test
    fun commands_haveTheWireDiscriminators() {
        val commands = mapOf(
            "start" to TdiCommand.StartSession("s", config, DeviceRole.PHONE, startedAtWallMs = 5L),
            "pause" to TdiCommand.PauseSession("s", DeviceRole.WATCH),
            "resume" to TdiCommand.ResumeSession("s", DeviceRole.PHONE),
            "abort" to TdiCommand.AbortSession("s", DeviceRole.PHONE, SessionEndReason.TIME_LIMIT),
            "config" to TdiCommand.ConfigSync(null, config),
            "abort_countdown" to TdiCommand.AbortCountdown("s", 2, CountdownAbortReason.MAJOR_MOVEMENT, atMs = 9L),
            "cycle_stage" to TdiCommand.CycleStage(
                "s", 3, CycleStageType.CYCLE_COMPLETE, CycleOutcome.ABORTED_AUDIO, atWallMs = 7L,
            ),
        )
        commands.forEach { (type, command) ->
            val json = OpenTdiJson.encodeToString<TdiCommand>(command)
            assertEquals(type, OpenTdiJson.parseToJsonElement(json).jsonObject["type"]!!.jsonPrimitive.content)
            assertEquals(command, OpenTdiJson.decodeFromString<TdiCommand>(json))
        }
        val start = OpenTdiJson.encodeToString<TdiCommand>(commands.getValue("start"))
        assertEquals("1", OpenTdiJson.parseToJsonElement(start).jsonObject["protocolVersion"]!!.jsonPrimitive.content)
        val configSync = OpenTdiJson.encodeToString<TdiCommand>(commands.getValue("config"))
        assertFalse("explicitNulls = false omits a null sessionId", "sessionId" in configSync)
    }

    @Test
    fun unknownFields_areIgnored() {
        val json = """{"type":"pause","sessionId":"s","origin":"WATCH","addedInProtocol2":{"x":[1,2]}}"""
        assertEquals(TdiCommand.PauseSession("s", DeviceRole.WATCH), OpenTdiJson.decodeFromString<TdiCommand>(json))
        assertEquals(Ack(true), OpenTdiJson.decodeFromString<Ack>("""{"ok":true,"futureField":3}"""))
        val packetJson = OpenTdiJson.encodeToString(packet).replaceFirst("{", """{"tiltDeg":3.5,""")
        assertEquals(packet, OpenTdiJson.decodeFromString<BiosignalPacket>(packetJson))
    }

    @Test
    fun routeCapGain_isAnEnumKeyedObject() {
        val audio = OpenTdiJson.parseToJsonElement(OpenTdiJson.encodeToString(config)).jsonObject["audio"]!!.jsonObject
        val caps = audio["routeCapGain"]!!.jsonObject
        assertEquals(AudioRouteMode.entries.map { it.name }, caps.keys.toList())
        assertEquals("0.2", caps["BLUETOOTH_SLEEP_AUDIO"]!!.jsonPrimitive.content)
        val decoded = OpenTdiJson.decodeFromString<AudioConfig>("""{"routeCapGain":{"NIGHTSTAND_SPEAKER":0.45}}""")
        assertEquals(mapOf(AudioRouteMode.NIGHTSTAND_SPEAKER to 0.45f), decoded.routeCapGain)
    }

    @Test
    fun sessionId_format() {
        val id = newSessionId(0L, Random(7))
        assertTrue(id, id.matches(Regex("""19700101T000000Z-[0-9a-f]{4}""")))
    }
}
