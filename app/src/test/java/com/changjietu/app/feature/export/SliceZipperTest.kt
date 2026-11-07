package com.changjietu.app.feature.export

import com.changjietu.app.image.processing.SliceOutput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipInputStream

class SliceZipperTest {

    private fun slice(index: Int, file: File): SliceOutput = SliceOutput(
        index = index,
        total = 2,
        startY = index * 100,
        endY = index * 100 + 100,
        width = 10,
        height = 100,
        file = file,
        label = "%02d/02".format(index + 1),
    )

    @Test
    fun zip_contains_all_selected_slices() {
        val dir = Files.createTempDirectory("zipper").toFile()
        try {
            val a = File(dir, "longshot_01_of_02.jpg").apply { writeText("AAA") }
            val b = File(dir, "longshot_02_of_02.jpg").apply { writeText("BBB") }
            val out = File(dir, "out.zip")
            val count = SliceZipper.zip(listOf(slice(0, a), slice(1, b)), out)
            assertEquals(2, count)
            assertTrue(out.length() > 0)

            val entries = linkedMapOf<String, String>()
            ZipInputStream(out.inputStream()).use { zis ->
                while (true) {
                    val entry = zis.nextEntry ?: break
                    entries[entry.name] = zis.readBytes().decodeToString()
                }
            }
            assertEquals(setOf("longshot_01_of_02.jpg", "longshot_02_of_02.jpg"), entries.keys)
            assertEquals("AAA", entries["longshot_01_of_02.jpg"])
            assertEquals("BBB", entries["longshot_02_of_02.jpg"])
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun duplicate_entry_names_are_deduplicated() {
        val dir = Files.createTempDirectory("zipper-dup").toFile()
        try {
            val d1 = File(dir, "a").apply { mkdirs() }
            val d2 = File(dir, "b").apply { mkdirs() }
            val a = File(d1, "slice.jpg").apply { writeText("A") }
            val b = File(d2, "slice.jpg").apply { writeText("B") }
            val out = File(dir, "out.zip")
            SliceZipper.zip(listOf(slice(0, a), slice(1, b)), out)

            val names = mutableListOf<String>()
            ZipInputStream(out.inputStream()).use { zis ->
                while (true) {
                    val entry = zis.nextEntry ?: break
                    names += entry.name
                }
            }
            assertEquals(listOf("slice.jpg", "slice_2.jpg"), names)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun failure_leaves_no_partial_zip() {
        val dir = Files.createTempDirectory("zipper-fail").toFile()
        try {
            val missing = File(dir, "missing.jpg")
            val out = File(dir, "out.zip")
            try {
                SliceZipper.zip(listOf(slice(0, missing)), out)
                throw AssertionError("expected failure")
            } catch (_: Exception) {
                // expected
            }
            assertFalse(out.exists())
            assertFalse(File(dir, "out.zip.part").exists())
        } finally {
            dir.deleteRecursively()
        }
    }
}
