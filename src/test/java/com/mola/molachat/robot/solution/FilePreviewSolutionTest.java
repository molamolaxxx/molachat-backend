package com.mola.molachat.robot.solution;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class FilePreviewSolutionTest {

    @Test
    public void renderModeUsesExtensionAndMediaType() {
        assertEquals("MARKDOWN", FilePreviewSolution.renderMode("README.md", "text/plain"));
        assertEquals("HTML", FilePreviewSolution.renderMode("page", "text/html"));
        assertEquals("CODE", FilePreviewSolution.renderMode("Service.java", "text/plain"));
        assertEquals("TEXT", FilePreviewSolution.renderMode("server.log", "text/plain"));
    }

    @Test
    public void languageMappingIsStable() {
        assertEquals("java", FilePreviewSolution.language("Service.java"));
        assertEquals("javascript", FilePreviewSolution.language("app.js"));
        assertEquals("yaml", FilePreviewSolution.language("config.yml"));
        assertNull(FilePreviewSolution.language("README"));
    }
}
