package app.contrail.core.airports

/** Names for common IATA airline codes, so stats can say "Singapore Airlines" instead of "SQ". */
object Airlines {
    private val names = mapOf(
        // Asia Pacific
        "SQ" to "Singapore Airlines", "TR" to "Scoot", "3K" to "Jetstar Asia", "CX" to "Cathay Pacific",
        "UO" to "HK Express", "JL" to "Japan Airlines", "NH" to "ANA", "KE" to "Korean Air", "OZ" to "Asiana",
        "CI" to "China Airlines", "BR" to "EVA Air", "CA" to "Air China", "MU" to "China Eastern",
        "CZ" to "China Southern", "TG" to "Thai Airways", "MH" to "Malaysia Airlines", "AK" to "AirAsia",
        "D7" to "AirAsia X", "GA" to "Garuda Indonesia", "PR" to "Philippine Airlines", "5J" to "Cebu Pacific",
        "VN" to "Vietnam Airlines", "VJ" to "VietJet Air", "AI" to "Air India", "6E" to "IndiGo",
        "QF" to "Qantas", "JQ" to "Jetstar", "VA" to "Virgin Australia", "NZ" to "Air New Zealand",
        // Middle East and Europe
        "EK" to "Emirates", "EY" to "Etihad", "QR" to "Qatar Airways", "TK" to "Turkish Airlines",
        "LH" to "Lufthansa", "LX" to "SWISS", "OS" to "Austrian", "BA" to "British Airways",
        "AF" to "Air France", "KL" to "KLM", "IB" to "Iberia", "AY" to "Finnair", "SK" to "SAS",
        "EI" to "Aer Lingus", "U2" to "easyJet", "FR" to "Ryanair",
        // Americas
        "AA" to "American Airlines", "DL" to "Delta", "UA" to "United", "AC" to "Air Canada",
        "WN" to "Southwest", "B6" to "JetBlue", "AS" to "Alaska Airlines",
    )

    fun name(code: String): String = names[code.uppercase()] ?: code.uppercase()
}
