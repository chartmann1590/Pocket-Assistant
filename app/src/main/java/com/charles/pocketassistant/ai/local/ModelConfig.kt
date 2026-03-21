package com.charles.pocketassistant.ai.local

data class LocalModelProfile(
    val id: String,
    val tierLabel: String,
    val displayName: String,
    val shortDescription: String,
    val remoteUrl: String,
    val modelSizeMb: Int,
    val requiredFreeSpaceMb: Int,
    val requiresAuthToken: Boolean,
    val repoUrl: String,
    val tokenHelpText: String
)

object ModelConfig {
    const val version = "preview"
    const val expectedChecksumSha256 = ""
    const val localFilename = "active_local_model.litertlm"
    const val wifiRecommended = true

    val profiles: List<LocalModelProfile> = listOf(
        LocalModelProfile(
            id = "qwen3_0_6b",
            tierLabel = "Lightweight",
            displayName = "Qwen3 0.6B",
            shortDescription = "Fastest and easiest install. Best default for small downloads and lower-RAM phones.",
            remoteUrl = "https://huggingface.co/litert-community/Qwen3-0.6B/resolve/main/Qwen3-0.6B.litertlm",
            modelSizeMb = 586,
            requiredFreeSpaceMb = 1200,
            requiresAuthToken = false,
            repoUrl = "https://huggingface.co/litert-community/Qwen3-0.6B",
            tokenHelpText = "No Hugging Face token is required for this model."
        ),
        LocalModelProfile(
            id = "qwen25_1_5b_instruct",
            tierLabel = "Medium",
            displayName = "Qwen 2.5 1.5B Instruct",
            shortDescription = "Better answer quality than the lightweight option while still fitting comfortably on a phone.",
            remoteUrl = "https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct/resolve/main/Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm",
            modelSizeMb = 1524,
            requiredFreeSpaceMb = 2600,
            requiresAuthToken = false,
            repoUrl = "https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct",
            tokenHelpText = "No Hugging Face token is required for this model."
        ),
        LocalModelProfile(
            id = "gemma3n_e2b",
            tierLabel = "Heavy",
            displayName = "Gemma 3n E2B",
            shortDescription = "Most capable on-device option here. Largest download and requires a Hugging Face token with Gemma access.",
            remoteUrl = "https://huggingface.co/google/gemma-3n-E2B-it-litert-lm/resolve/main/gemma-3n-E2B-it-int4.litertlm",
            modelSizeMb = 3487,
            requiredFreeSpaceMb = 5500,
            requiresAuthToken = true,
            repoUrl = "https://huggingface.co/google/gemma-3n-E2B-it-litert-lm",
            tokenHelpText = "Requires a Hugging Face token with Gemma gated-model access."
        )
    )

    val defaultProfile: LocalModelProfile = profiles.first()

    fun profileFor(id: String?): LocalModelProfile = profiles.firstOrNull { it.id == id } ?: defaultProfile

    fun hasRemoteDownload(profile: LocalModelProfile): Boolean = profile.remoteUrl.isNotBlank()

    fun requiredFreeSpaceBytes(profile: LocalModelProfile): Long = profile.requiredFreeSpaceMb * 1024L * 1024L

    fun installActionLabel(profile: LocalModelProfile): String = "Download ${profile.tierLabel.lowercase()} model"

    fun formatSize(profile: LocalModelProfile): String {
        val mb = profile.modelSizeMb
        return if (mb >= 1024) {
            "%.1f GB".format(mb / 1024.0)
        } else {
            "$mb MB"
        }
    }

    fun installSummary(profile: LocalModelProfile): String = buildString {
        append(profile.tierLabel)
        append(": ")
        append(profile.displayName)
        append(" in LiteRT-LM format for on-device use. ")
        append(profile.tokenHelpText)
    }
}
