package com.opentdi.mobile.journal

import com.opentdi.mobile.stats.SummaryCache
import com.opentdi.mobile.stats.writeCache
import com.opentdi.shared.stats.SUMMARY_VERSION
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TranscriberTest {
    private val s = 16_000 // samples a second

    private fun withDir(block: (File) -> Unit) {
        val dir = Files.createTempDirectory("otdi").toFile()
        try {
            block(dir)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun speech_dropsSpansUnderHalfASecond_andMergesNeighboursUpTo20s() {
        val spans = listOf(
            0 until s / 2, // exactly 0.5 s: kept
            s until s + s / 2 - 1, // one sample short of 0.5 s: dropped
            2 * s until 3 * s, // merges with the first, the pause included
            19 * s until 20 * s, // ends exactly 20 s after the first began: still merges
            20 * s until 21 * s, // would make 21 s: starts the next stretch
            39 * s until 40 * s, // exactly 20 s again
        )
        assertEquals(listOf(0 until 20 * s, 20 * s until 40 * s), mergeSpeech(spans, s / 2, 20 * s))
        assertEquals(listOf(0 until 25 * s), mergeSpeech(listOf(0 until 25 * s), s / 2, 20 * s)) // too long: kept whole
        assertEquals(emptyList<IntRange>(), mergeSpeech(listOf(0 until s / 4), s / 2, 20 * s))
    }

    @Test
    fun repeatGuard_cutsRunsOfThreeOrMoreToOneCopy() {
        assertEquals("I saw a dog in the park.", guardRepeats("I saw a dog a dog a dog in the park."))
        assertEquals("go to the park now", guardRepeats("go to the park go to the park go to the park now"))
        assertEquals("I was flying, over the sea.", guardRepeats("I was flying, Flying, flying over the sea."))
        assertEquals("um", guardRepeats("um um um")) // too few words to count as mostly one word
    }

    @Test
    fun repeatGuard_dropsTextThatIsMostlyOneWord() {
        assertEquals("", guardRepeats("The the THE the."))
        assertEquals("", guardRepeats("you, you, okay you you"))
        assertEquals("", guardRepeats(""))
    }

    @Test
    fun repeatGuard_leavesOrdinarySpeechAlone() {
        val report = "We walked to the old school and the doors were open."
        assertEquals(report, guardRepeats(report))
        assertEquals("very very nice", guardRepeats("very very nice")) // two copies are speech
        assertEquals("a b c d e a b c d e a b c d e", guardRepeats("a b c d e a b c d e a b c d e")) // 5 words: not cut
        assertEquals("so quiet", guardRepeats("  so   quiet\n"))
    }

    @Test
    fun aRecordingNeedsATranscript_whenItHasNoneOrOnlyAnOlderOne() = withDir { dir ->
        val recording = File(dir, "w_dream_064512.m4a").apply { writeBytes(ByteArray(3)) }
        recording.setLastModified(2_000_000L)
        assertTrue(needsTranscript(recording))
        File(dir, "w_dream_064512.txt.part").writeText("left by a killed run")
        assertTrue(needsTranscript(recording))
        val transcript = File(dir, "w_dream_064512.txt").apply { writeText("") } // empty: no speech found
        transcript.setLastModified(3_000_000L)
        assertEquals(transcript, transcriptFile(recording))
        assertFalse(needsTranscript(recording))
        recording.setLastModified(4_000_000L) // copied again from the watch
        assertTrue(needsTranscript(recording))
    }

    @Test
    fun pending_isEveryRecordingWithoutATranscript_newestNightFirst_withoutPrunedNights() = withDir { root ->
        fun night(id: String) = File(root, id).apply { mkdirs() }
        val pruned = night("20260601T030000Z-0001")
        writeCache(pruned, SummaryCache(SUMMARY_VERSION, emptyMap(), pruned = true))
        File(pruned, "w_dream_064512.m4a").writeBytes(ByteArray(3)) // the prune would delete its transcript again
        val earlier = night("20261026T030000Z-0002")
        File(earlier, "w_dream_051500.m4a").writeBytes(ByteArray(3))
        val latest = night("20261028T030000Z-0003")
        File(latest, "w_dream_041000.m4a").apply { writeBytes(ByteArray(3)) }.setLastModified(1_000_000L)
        File(latest, "w_dream_041000.txt").apply { writeText("done") }.setLastModified(2_000_000L)
        File(latest, "w_dream_063000.m4a").writeBytes(ByteArray(3))
        File(latest, "w_dream_023000.m4a").writeBytes(ByteArray(3))
        File(latest, "w_packets.csv").writeText("not a recording")
        File(latest, "w_dream_9.m4a").writeBytes(ByteArray(3)) // no watch recording name
        assertEquals(
            listOf(
                "${latest.name}/w_dream_023000.m4a",
                "${latest.name}/w_dream_063000.m4a",
                "${earlier.name}/w_dream_051500.m4a",
            ),
            pendingRecordings(root).map { "${it.parentFile?.name}/${it.name}" },
        )
    }

    @Test
    fun aTranscript_isReplacedWhole_andNoPartIsLeft() = withDir { dir ->
        val recording = File(dir, "w_dream_064512.m4a").apply { writeBytes(ByteArray(3)) }
        File(dir, "w_dream_064512.txt.part").writeText("left by a killed run")
        writeTranscript(recording, "I was flying over the old school.")
        assertEquals("I was flying over the old school.", transcriptFile(recording).readText())
        writeTranscript(recording, "")
        assertEquals("", transcriptFile(recording).readText())
        assertEquals(listOf("w_dream_064512.m4a", "w_dream_064512.txt"), dir.list().orEmpty().sorted())
    }

    @Test
    fun sha256_matchesTheStandardVectors_andFilesLargerThanItsBlock() = withDir { dir ->
        val empty = File(dir, "empty").apply { writeBytes(ByteArray(0)) }
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", sha256Hex(empty))
        val abc = File(dir, "abc").apply { writeText("abc") }
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", sha256Hex(abc))
        val bytes = ByteArray(200_000) { (it * 31).toByte() } // more than three 64 KB blocks
        val whole = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        assertEquals(whole, sha256Hex(File(dir, "large").apply { writeBytes(bytes) }))
    }

    @Test
    fun aModel_isIntactOnlyWithEveryFileAtItsDigest() = withDir { dir ->
        File(dir, "model.int8.onnx").writeText("weights")
        File(dir, "tokens.txt").writeText("a 0\nb 1\n")
        val digests = listOf("model.int8.onnx", "tokens.txt").associateWith { sha256Hex(File(dir, it)) }
        assertTrue(filesIntact(dir, digests))
        File(dir, "model.int8.onnx").writeText("weig") // a push cut short
        assertFalse(filesIntact(dir, digests))
        File(dir, "model.int8.onnx").writeText("weights")
        File(dir, "tokens.txt").delete()
        assertFalse(filesIntact(dir, digests))
    }
}
