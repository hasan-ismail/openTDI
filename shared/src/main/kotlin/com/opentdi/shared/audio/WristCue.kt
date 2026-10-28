package com.opentdi.shared.audio

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlin.math.roundToInt

/**
 * The wrist-speaker channel (DataLayerPaths.WRIST_AUDIO_CHANNEL). The phone sends one cue, already faded in and at its
 * target gain, as mono PCM16 behind a header; [Cue.calibrate] marks the test cue, whose watch volume becomes the
 * watch's calibration, and [Cue.storeAs] a clip the watch keeps under that name instead of playing it: [WAKE_VOICE]
 * (the spoken wake-up alarm) or a [GuideClip]. The watch writes a started marker just before the sound starts, then
 * one [Reply].
 */
object WristCue {
    const val PLAYED = 1
    const val VOLUME_ZERO = 2
    const val FAILED = 3
    const val NOT_ON_SPEAKER = 4 // the watch's media output is not its speaker (a headset is connected)

    /** The spoken wake-up alarm: it travels as the plain store flag, which older apps send and read too. */
    const val WAKE_VOICE = "wake_voice"
    private const val STARTED = 0x53
    private const val MAGIC = 0x4F544432 // "OTD2": the header layout; a different app version reads as FAILED
    private const val MAX_SAMPLES = 48_000 * 120 // two minutes at 48 kHz bounds what the watch allocates
    private const val CALIBRATE = 1
    private const val STORE = 2
    private const val EARBUDS = 4
    private const val NAMED = 8 // a clip name follows the flags (owner, 2026-10-28: the guided back-to-sleep)
    private val NAME = Regex("[a-z0-9_]{1,32}") // the watch makes it a file name: nothing that leaves its folder

    class Cue(val audio: WavAudio, val calibrate: Boolean, val storeAs: String? = null, val earbuds: Boolean = false)

    /** The watch's answer: [status], its media volume index, the output the cue played on and the volume-raised factor. */
    data class Reply(val status: Int, val streamIndex: Int = -1, val routedType: Int = -1, val factor: Float = 1f)

    fun write(
        out: OutputStream,
        samples: FloatArray,
        sampleRate: Int,
        calibrate: Boolean,
        storeAs: String? = null,
        earbuds: Boolean = false,
    ) {
        require(storeAs == null || storeAs.matches(NAME)) { "bad clip name: $storeAs" }
        val named = storeAs?.takeIf { it != WAKE_VOICE }
        val data = DataOutputStream(out.buffered())
        data.writeInt(MAGIC)
        data.writeInt(
            (if (calibrate) CALIBRATE else 0) or (if (storeAs == WAKE_VOICE) STORE else 0) or
                (if (earbuds) EARBUDS else 0) or (if (named != null) NAMED else 0),
        )
        // Where the rate was: an older watch reads the name's length and first letters as a rate above 48 kHz and
        // answers FAILED, so it never plays a guide clip or keeps one as its wake-up voice.
        if (named != null) data.writeUTF(named)
        data.writeInt(sampleRate)
        data.writeInt(samples.size)
        for (s in samples) data.writeShort((s.coerceIn(-1f, 1f) * Short.MAX_VALUE).roundToInt())
        data.flush()
    }

    fun read(input: InputStream): Cue {
        val data = DataInputStream(input.buffered())
        if (data.readInt() != MAGIC) throw IOException("not a wrist cue: the phone and watch apps differ")
        val flags = data.readInt()
        val storeAs = when {
            flags and NAMED != 0 -> data.readUTF().also { if (!it.matches(NAME)) throw IOException("bad clip name") }
            flags and STORE != 0 -> WAKE_VOICE
            else -> null
        }
        val sampleRate = data.readInt()
        val count = data.readInt()
        if (sampleRate !in 8_000..48_000 || count !in 0..MAX_SAMPLES) {
            throw IOException("bad wrist cue header: rate=$sampleRate count=$count")
        }
        val audio = WavAudio(FloatArray(count) { data.readShort() / Short.MAX_VALUE.toFloat() }, sampleRate)
        return Cue(audio, calibrate = flags and CALIBRATE != 0, storeAs = storeAs, earbuds = flags and EARBUDS != 0)
    }

    fun writeStarted(out: OutputStream) {
        out.write(STARTED)
        out.flush()
    }

    fun writeReply(out: OutputStream, reply: Reply) {
        val data = DataOutputStream(out)
        data.writeByte(reply.status)
        data.writeInt(reply.streamIndex)
        data.writeInt(reply.routedType)
        data.writeFloat(reply.factor)
        data.flush()
    }

    /** The phone's side: calls [onStarted] when the watch starts the sound; null when the channel ended first. */
    fun readReply(input: InputStream, onStarted: () -> Unit): Reply? {
        val data = DataInputStream(input)
        var status = data.read()
        if (status == STARTED) {
            onStarted()
            status = data.read()
        }
        if (status < 0) return null
        return try {
            Reply(status, data.readInt(), data.readInt(), data.readFloat())
        } catch (e: EOFException) {
            Reply(status)
        }
    }
}

/**
 * The clips of the guided back-to-sleep after WBTB (owner, 2026-10-28): the phone renders and sends each one at Prepare
 * cues, and the watch keeps it as filesDir/guide/<[file]>.wav and plays them in its own order (GuideRoutine).
 */
enum class GuideClip {
    SETTLE, MILD, SSILD, FAST, SLOW, CUE_INTRO, PROMPT_THOUGHTS, PROMPT_BODY, PROMPT_BREATH, PROMPT_AWARE, CLOSE;

    /** The name it is sent and kept under. */
    val file: String get() = name.lowercase()
}
