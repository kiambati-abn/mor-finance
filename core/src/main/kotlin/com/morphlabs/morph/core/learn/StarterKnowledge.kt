package com.morphlabs.morph.core.learn

/**
 * What Morph "knows" before it has learned anything from you: common Kenyan merchants and
 * paybills mapped to account hints. Your own history always outweighs this.
 * Strength >= [AUTO_STRENGTH] is trusted enough to file without asking.
 */
object StarterKnowledge {

    const val AUTO_STRENGTH = 3.0

    data class Rule(val pattern: Regex, val hint: String, val strength: Double)

    private fun r(hint: String, strength: Double, vararg words: String) =
        Rule(Regex("""\b(""" + words.joinToString("|") { Regex.escape(it) } + """)\b"""), hint, strength)

    val rules: List<Rule> = listOf(
        r("bills", 3.0, "KPLC", "KENYA POWER", "NAIROBI WATER", "NAIROBI CITY WATER"),
        r("bills", 2.5, "ZUKU", "FAIBA", "SAFARICOM HOME", "HOME FIBRE", "DSTV", "GOTV", "STARTIMES", "SHOWMAX", "NETFLIX", "WATER", "PREPAID", "POSTPAID"),
        r("airtime", 3.0, "SAFARICOM DATA BUNDLES", "SAFARICOM DATA", "AIRTIME", "TUNUKIWA", "OKOA JAHAZI", "BUNDLES"),
        r("helb", 3.0, "HELB", "HIGHER EDUCATION LOANS"),
        r("hustler", 3.0, "HUSTLER", "HUSTLER FUND", "FINANCIAL INCLUSION FUND"),
        r("food", 2.0, "HOTEL", "RESTAURANT", "CAFE", "JAVA", "KFC", "PIZZA", "CHICKEN", "BURGER", "EATERY", "KITCHEN", "FOOD", "FOODS", "BAKERY", "BUTCHERY", "GRILL", "ARTCAFFE", "CAFETERIA", "CANTEEN", "NYAMA", "DINER", "INN"),
        r("groceries", 2.0, "CARREFOUR", "NAIVAS", "QUICKMART", "QUICK MART", "CHANDARANA", "MAJID", "SUPERMARKET", "MINIMART", "MINI MART", "GROCERS", "GROCERY", "MBOGA", "CLEANSHELF", "KASSMATT"),
        r("groceries", 1.0, "SHOP", "SHOPS", "STORE", "STORES", "KIOSK", "DUKA", "WHOLESALE", "WHOLESALERS", "MART"),
        r("transport", 2.0, "UBER", "BOLT", "LITTLE", "FARAS", "SACCO", "SHUTTLE", "MATATU", "EASY COACH", "FUEL", "PETROL", "SHELL", "TOTALENERGIES", "RUBIS", "OLA ENERGY", "PARKING", "SGR", "KENYA RAILWAYS", "BODA"),
        r("health", 2.0, "PHARMACY", "PHARMACEUTICALS", "CHEMIST", "HOSPITAL", "CLINIC", "MEDICAL", "DENTAL", "SOCIAL HEALTH", "NHIF", "LAB", "LABS"),
        r("school", 2.0, "SCHOOL", "ACADEMY", "UNIVERSITY", "COLLEGE", "FEES"),
        r("rent", 1.5, "RENT", "APARTMENTS", "PROPERTIES", "LANDLORD", "HOUSING"),
        r("bank", 1.5, "BANK", "EQUITY", "KCB", "CO-OP", "COOP", "NCBA", "ABSA", "STANBIC", "I&M", "DTB", "FAMILY BANK"),
        r("airtel", 1.5, "AIRTEL MONEY"),
        r("chama", 1.5, "CHAMA", "WELFARE", "INVESTMENT GROUP", "SELF HELP"),
        r("giving", 2.0, "CHURCH", "TITHE", "OFFERING", "MOSQUE", "PARISH", "MINISTRY", "MINISTRIES", "MCHANGA", "M-CHANGA", "HARAMBEE"),
        r("personal", 2.0, "SALON", "BARBER", "BARBERSHOP", "KINYOZI", "SPA", "BEAUTY", "COSMETICS"),
        r("gadgets", 1.0, "ELECTRONICS", "COMPUTERS", "PHONES", "ACCESSORIES", "TECH"),
        r("fun", 2.0, "SPORTPESA", "BETIKA", "ODIBETS", "MOZZART", "BETWAY", "SHABIKI", "1XBET", "CINEMA", "IMAX", "CLUB", "LOUNGE"),
        r("salary", 2.0, "SALARY", "PAYROLL"),
    )

    /** Best hint per rule family for this counterparty name (already normalized upper-case). */
    fun match(normalizedName: String): List<Pair<String, Double>> =
        rules.filter { it.pattern.containsMatchIn(normalizedName) }
            .groupBy { it.hint }
            .map { (hint, rs) -> hint to rs.maxOf { it.strength } }
}
