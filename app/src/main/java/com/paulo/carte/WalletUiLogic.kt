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

    fun recognizeMerchantFromText(text: String): String {
        val n = text.lowercase()
            .replace("é", "e")
            .replace("è", "e")
            .replace("ê", "e")
            .replace("à", "a")
            .replace("ç", "c")
        return when {
            n.contains("carrefour") -> "Carrefour"
            n.contains("super u") || n.contains("carte u") -> "Super U"
            n.contains("e.leclerc") || n.contains("e leclerc") || n.contains("leclerc") -> "E.Leclerc"
            n.contains("intermarche") || n.contains("mousquetaires") -> "Intermarché"
            n.contains("auchan") -> "Auchan"
            n.contains("lidl") -> "Lidl"
            n.contains("aldi") -> "Aldi"
            else -> ""
        }
    }

    fun isValidWebUrl(url: String): Boolean = url.startsWith("https://") || url.startsWith("http://")
}
