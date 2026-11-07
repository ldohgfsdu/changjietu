package com.changjietu.app.core

import org.junit.Assert.assertEquals
import org.junit.Test

class ZipNameSanitizerTest {

    @Test
    fun appends_zip_extension() {
        assertEquals("我的截图.zip", ZipNameSanitizer.sanitize("我的截图", "fallback"))
    }

    @Test
    fun keeps_existing_zip_extension() {
        assertEquals("abc.zip", ZipNameSanitizer.sanitize("abc.zip", "fallback"))
        assertEquals("abc.ZIP", ZipNameSanitizer.sanitize("abc.ZIP", "fallback"))
    }

    @Test
    fun strips_path_separators_and_traversal() {
        assertEquals("a_b_c.zip", ZipNameSanitizer.sanitize("a/b\\c", "fallback"))
        assertEquals("a_b.zip", ZipNameSanitizer.sanitize("a/../b", "fallback"))
    }

    @Test
    fun strips_invalid_filename_characters() {
        assertEquals("a_b_c.zip", ZipNameSanitizer.sanitize("a:b*c", "fallback"))
    }

    @Test
    fun blank_falls_back_to_default() {
        assertEquals("longshot_x.zip", ZipNameSanitizer.sanitize("   ", "longshot_x"))
        assertEquals("longshot_x.zip", ZipNameSanitizer.sanitize(null, "longshot_x"))
    }

    @Test
    fun strips_leading_dot() {
        assertEquals("hidden.zip", ZipNameSanitizer.sanitize(".hidden", "fallback"))
    }
}
