package com.paulo.carte

object WalletUiLogic {
    fun categoryForMerchant(name: String): String {
        val n = name.lowercase()
        return when {
            listOf("super u", "intermarché", "intermarche", "leclerc", "carrefour", "auchan", "lidl", "aldi").any { n.contains(it) } -> "Supermarchés"
            listOf("total", "esso", "shell", "bp", "avia").any { n.contains(it) } -> "Carburant"
            listOf("zara", "h&m", "kiabi", "celio", "jules").any { n.contains(it) } -> "Mode"
            else -> "Autres"
        }
    }

    fun badgeForMerchant(name: String): String {
        val n = name.lowercase()
        return when {
            n.contains("super u") -> "U"
            n.contains("intermarch") -> "IM"
            n.contains("leclerc") -> "L"
            n.contains("carrefour") -> "C"
            else -> name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }.ifBlank { "★" }
        }
    }

    fun isValidWebUrl(url: String): Boolean = url.startsWith("https://") || url.startsWith("http://")
}
