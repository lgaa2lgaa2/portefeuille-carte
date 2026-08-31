package com.paulo.carte

data class AffiliateAd(
    val title: String,
    val subtitle: String,
    val url: String,
    val enabled: Boolean = true
) {
    fun isValid(): Boolean {
        val normalized = url.trim().lowercase()
        return title.isNotBlank() && (normalized.startsWith("https://") || normalized.startsWith("http://"))
    }
}

object MerchantDetector {
    private val knownMerchants = listOf(
        "super u" to "Super U",
        "hyper u" to "Hyper U",
        "intermarch" to "Intermarché",
        "e.leclerc" to "E.Leclerc",
        "leclerc" to "E.Leclerc",
        "carrefour" to "Carrefour",
        "auchan" to "Auchan",
        "lidl" to "Lidl",
        "aldi" to "Aldi",
        "monoprix" to "Monoprix",
        "casino" to "Casino",
        "decathlon" to "Decathlon",
        "ikea" to "IKEA",
        "leroy merlin" to "Leroy Merlin",
        "castorama" to "Castorama"
    )

    fun detect(text: String): String {
        val normalized = text.lowercase()
        knownMerchants.firstOrNull { normalized.contains(it.first) }?.let { return it.second }

        return text.lineSequence()
            .map { it.trim() }
            .firstOrNull { line ->
                line.length in 3..40 && line.any(Char::isLetter) &&
                    !line.lowercase().contains("fidélité") &&
                    !line.lowercase().contains("fidelite") &&
                    !line.lowercase().contains("carte")
            }
            ?: "Ma carte"
    }
}
