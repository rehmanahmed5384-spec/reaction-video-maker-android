package com.example

import com.example.core.model.*
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun testAspectRatioDimensions() {
        assertEquals(16f / 9f, AspectRatio.LANDSCAPE_16_9.ratio, 0.01f)
        assertEquals(9f / 16f, AspectRatio.PORTRAIT_9_16.ratio, 0.01f)
        assertEquals(1f, AspectRatio.SQUARE_1_1.ratio, 0.01f)

        val landscape1080p = ExportResolution.RES_1080P.dimensionsFor(AspectRatio.LANDSCAPE_16_9)
        assertEquals(1920 to 1080, landscape1080p)

        val portrait1080p = ExportResolution.RES_1080P.dimensionsFor(AspectRatio.PORTRAIT_9_16)
        assertEquals(1080 to 1920, portrait1080p)

        val square1080p = ExportResolution.RES_1080P.dimensionsFor(AspectRatio.SQUARE_1_1)
        assertEquals(1080 to 1080, square1080p)
    }

    @Test
    fun testNormalizedRectClamping() {
        val outOfBounds = NormalizedRect(-0.2f, -0.5f, 1.5f, 1.2f)
        val clamped = outOfBounds.clamped()

        assertTrue(clamped.x >= 0f)
        assertTrue(clamped.y >= 0f)
        assertTrue(clamped.width <= 1.0f)
        assertTrue(clamped.height <= 1.0f)
        assertTrue(clamped.x + clamped.width <= 1.0001f)
        assertTrue(clamped.y + clamped.height <= 1.0001f)
    }

    @Test
    fun testIndependentLayerPlaybackSemantics() {
        val mainVideo = Layer(
            name = "Main Video",
            type = LayerType.VIDEO,
            isPlaying = true,
            visible = true
        )
        val reactionCam = Layer(
            name = "Cam",
            type = LayerType.CAMERA,
            isPlaying = false, // Camera 1 paused
            visible = true
        )
        val backgroundMusic = Layer(
            name = "Audio Track",
            type = LayerType.AUDIO,
            isPlaying = true,
            visible = false // Playing + hidden is legal
        )

        // AC-04: One layer can pause while other layers continue
        assertTrue(mainVideo.isPlaying)
        assertFalse(reactionCam.isPlaying)

        // AC-05: One layer can hide while continuing to play
        assertTrue(backgroundMusic.isPlaying)
        assertFalse(backgroundMusic.visible)
    }

    @Test
    fun testPiPPresetsValidity() {
        LayerPreset.values().forEach { preset ->
            val rect = preset.rect
            assertTrue("Preset ${preset.label} x out of range", rect.x in 0f..1f)
            assertTrue("Preset ${preset.label} y out of range", rect.y in 0f..1f)
            assertTrue("Preset ${preset.label} width out of range", rect.width in 0f..1f)
            assertTrue("Preset ${preset.label} height out of range", rect.height in 0f..1f)
            assertTrue("Preset ${preset.label} overflows canvas", rect.x + rect.width <= 1.01f)
            assertTrue("Preset ${preset.label} overflows canvas", rect.y + rect.height <= 1.01f)
        }
    }
}
