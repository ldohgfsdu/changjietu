package com.changjietu.app.feature.editor

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CutViewportContractTest {
    @Test fun statusChangesDoNotInsertOrRemoveViewportSiblings() {
        val relative = "src/main/java/com/changjietu/app/feature/editor/PreviewCutEditor.kt"
        val source = listOf(File(relative), File("app", relative)).first { it.isFile }.readText()
        assertFalse(source.contains("if (cuts.getOrNull(selectedCutIndex) in uncertainCutEnds) {"))
        assertFalse(source.contains("if (manualActive) {"))
        assertTrue(source.contains("height(48.dp)"))
        assertTrue(source.contains("height(56.dp)"))
    }
}
