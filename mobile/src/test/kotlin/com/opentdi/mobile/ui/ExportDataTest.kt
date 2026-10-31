package com.opentdi.mobile.ui

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

class ExportDataTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun writeExport_zipsOnlyTheNamedFolders_withTheirPaths() {
        val base = tmp.root
        base.resolve("sessions/20261031T030000Z").apply { mkdirs() }.resolve("p_events.csv").writeText("a,b\n")
        base.resolve("watch/20261031T030000Z").apply { mkdirs() }.resolve("w_dream_041500.m4a").writeBytes(ByteArray(5))
        base.resolve("voice").apply { mkdirs() }.resolve("model.onnx").writeText("not exported")

        val out = ByteArrayOutputStream()
        val (files, bytes) = writeExport(base, listOf("sessions", "watch", "missing"), out)

        assertEquals(2, files)
        assertEquals(9L, bytes)
        val zip = ZipInputStream(ByteArrayInputStream(out.toByteArray()))
        val entries = generateSequence { zip.nextEntry }.associate { it.name to zip.readBytes().size }
        assertEquals(
            mapOf("sessions/20261031T030000Z/p_events.csv" to 4, "watch/20261031T030000Z/w_dream_041500.m4a" to 5),
            entries,
        )
    }
}
