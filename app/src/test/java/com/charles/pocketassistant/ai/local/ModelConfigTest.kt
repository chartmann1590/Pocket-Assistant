package com.charles.pocketassistant.ai.local

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ModelConfigTest {

    @Before
    fun useUsLocale() {
        Locale.setDefault(Locale.US)
    }

    @Test
    fun defaultProfileIsFirstInList() {
        assertEquals(ModelConfig.profiles.first(), ModelConfig.defaultProfile)
    }

    @Test
    fun profileForNullOrUnknownFallsBackToDefault() {
        assertEquals(ModelConfig.defaultProfile, ModelConfig.profileFor(null))
        assertEquals(ModelConfig.defaultProfile, ModelConfig.profileFor("not_a_real_id"))
    }

    @Test
    fun profileForKnownIds() {
        assertEquals("qwen3_0_6b", ModelConfig.profileFor("qwen3_0_6b").id)
        assertEquals("gemma4_e2b", ModelConfig.profileFor("gemma4_e2b").id)
        assertEquals("gemma4_e4b", ModelConfig.profileFor("gemma4_e4b").id)
        assertEquals("gemma3n_e2b", ModelConfig.profileFor("gemma3n_e2b").id)
    }

    @Test
    fun hasRemoteDownloadTrueWhenUrlPresent() {
        val p = ModelConfig.defaultProfile
        assertTrue(ModelConfig.hasRemoteDownload(p))
    }

    @Test
    fun requiredFreeSpaceBytesMatchesMb() {
        val p = ModelConfig.profileFor("qwen3_0_6b")
        assertEquals(p.requiredFreeSpaceMb * 1024L * 1024L, ModelConfig.requiredFreeSpaceBytes(p))
    }

    @Test
    fun formatSizeUsesMbBelowOneGb() {
        val small = ModelConfig.profileFor("qwen3_0_6b")
        assertEquals("586 MB", ModelConfig.formatSize(small))
    }

    @Test
    fun formatSizeUsesGbAtOrAboveOneGb() {
        val large = ModelConfig.profileFor("gemma4_e2b")
        assertEquals("2.4 GB", ModelConfig.formatSize(large))
    }

    @Test
    fun installActionLabelUsesTier() {
        val p = ModelConfig.profileFor("qwen3_0_6b")
        assertEquals("Download lightweight model", ModelConfig.installActionLabel(p))
    }

    @Test
    fun installSummaryIncludesTierDisplayNameAndTokenHelp() {
        val p = ModelConfig.profileFor("gemma3n_e2b")
        val s = ModelConfig.installSummary(p)
        assertTrue(s.contains(p.tierLabel))
        assertTrue(s.contains(p.displayName))
        assertTrue(s.contains("LiteRT-LM"))
        assertTrue(s.contains(p.tokenHelpText))
    }

    @Test
    fun gemma4ProfilesDoNotRequireToken() {
        assertFalse(ModelConfig.profileFor("gemma4_e2b").requiresAuthToken)
        assertFalse(ModelConfig.profileFor("gemma4_e4b").requiresAuthToken)
    }

    @Test
    fun gemma3nRequiresToken() {
        assertTrue(ModelConfig.profileFor("gemma3n_e2b").requiresAuthToken)
    }
}
