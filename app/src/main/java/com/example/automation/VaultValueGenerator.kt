package com.example.automation

import com.example.data.local.AutomationVaultFolderEntity
import com.example.engine.SecurePasswordGenerator
import java.security.SecureRandom
import java.util.Locale
import java.util.UUID

enum class VaultGeneratorType(
    val id: String,
    val displayName: String,
    val category: AutomationVaultCategory
) {
    PASSWORD("PASSWORD", "Strong Password", AutomationVaultCategory.PASSWORD),
    PASSPHRASE("PASSPHRASE", "Passphrase", AutomationVaultCategory.PASSWORD),
    PIN("PIN", "PIN Code", AutomationVaultCategory.PASSWORD),
    OTP_RECOVERY("OTP_RECOVERY", "Recovery / 2FA Code", AutomationVaultCategory.PASSWORD),
    EMAIL("EMAIL", "Email Address", AutomationVaultCategory.EMAIL),
    PHONE("PHONE", "Phone Number", AutomationVaultCategory.PHONE),
    COUNTRY_CODE("COUNTRY_CODE", "Country Code", AutomationVaultCategory.COUNTRY_CODE),
    DATE_OF_BIRTH("DATE_OF_BIRTH", "Date of Birth", AutomationVaultCategory.DATE_OF_BIRTH),
    GENDER("GENDER", "Gender", AutomationVaultCategory.GENDER),
    USERNAME("USERNAME", "Username", AutomationVaultCategory.USERNAME),
    FULL_NAME("FULL_NAME", "Full Name", AutomationVaultCategory.NAME),
    FIRST_NAME("FIRST_NAME", "First Name", AutomationVaultCategory.NAME),
    LAST_NAME("LAST_NAME", "Last Name", AutomationVaultCategory.NAME),
    COMPANY("COMPANY", "Company Name", AutomationVaultCategory.NAME),
    ADDRESS("ADDRESS", "Street Address", AutomationVaultCategory.ADDRESS),
    CITY("CITY", "City", AutomationVaultCategory.ADDRESS),
    POSTAL_CODE("POSTAL_CODE", "Postal / ZIP Code", AutomationVaultCategory.ADDRESS),
    API_KEY("API_KEY", "API Key / Token", AutomationVaultCategory.CUSTOM),
    UUID_ID("UUID", "UUID v4", AutomationVaultCategory.CUSTOM),
    URL("URL", "Website URL", AutomationVaultCategory.CUSTOM),
    DATE("DATE", "Date", AutomationVaultCategory.CUSTOM),
    PROMO_CODE("PROMO_CODE", "Promo / License Code", AutomationVaultCategory.CUSTOM),
    TEST_CARD("TEST_CARD", "Sandbox Test Card", AutomationVaultCategory.CUSTOM),
    IP_ADDRESS("IP_ADDRESS", "IP Address", AutomationVaultCategory.CUSTOM),
    COLOR_HEX("COLOR_HEX", "Hex Color", AutomationVaultCategory.CUSTOM),
    CONTEXTUAL_CODE("CONTEXTUAL_CODE", "ID / Reference Code", AutomationVaultCategory.CUSTOM),
    CUSTOM_VALUE("CUSTOM_VALUE", "Contextual Value", AutomationVaultCategory.CUSTOM);

    companion object {
        fun fromId(raw: String?): VaultGeneratorType? =
            entries.firstOrNull { it.id.equals(raw?.trim(), ignoreCase = true) }
    }
}

data class VaultFolderAnalysis(
    val type: VaultGeneratorType,
    val category: AutomationVaultCategory,
    val badgeLabel: String,
    val description: String,
    val domainHint: String? = null,
    val pinDigits: Int = 6,
    val isBirthDate: Boolean = false,
    val isExpiryDate: Boolean = false,
    val phoneCountryHint: String = "US",
    val contextualPrefix: String = "VAL"
)

/**
 * Analyzes vault folder names and folder metadata to infer the right value generator
 * and synthesize ready-to-use values (passwords, emails, phones, usernames, names,
 * addresses, PINs, API keys, recovery codes, dates, IDs, etc.) on demand.
 */
object VaultValueGenerator {
    private val random = SecureRandom()

    private val firstNames = listOf(
        "Alex", "Maya", "Jordan", "Elena", "Liam", "Noah", "Sofia", "Ethan",
        "Aria", "Lucas", "Nadia", "Caleb", "Chloe", "owen", "Priya", "Marcus",
        "Hana", "Julian", "Zoe", "Devon", "Sienna", "Mateo", "Kira", "Rafael"
    ).map { it.replaceFirstChar { ch -> ch.uppercaseChar() } }

    private val lastNames = listOf(
        "Mercer", "Lin", "Vance", "Rostova", "Patel", "Kowalski", "Bennett", "Moreau",
        "Hayes", "Takahashi", "okafor", "Rivera", "Sterling", "alvarez", "Chen", "Park",
        "Brooks", "Jensen", "Sharma", "Foster", "Cruz", "Keller", "Nolan", "Solis"
    ).map { it.replaceFirstChar { ch -> ch.uppercaseChar() } }

    private val emailDomains = listOf(
        "outlook.com", "proton.me", "icloud.com", "fastmail.com", "gmail.com", "mail.com"
    )

    private val workDomains = listOf(
        "northstar-ops.io", "veridianlabs.co", "crestline.work", "meridian-hq.com", "harborline.io"
    )

    private val streetNames = listOf(
        "Maple Ridge Ave", "Cedarbrook Ln", "Harbor View Blvd", "Oakwood Dr",
        "Summit Crest Rd", "Willow Creek Way", "Silverleaf St", "Beacon Hill Ct",
        "Pinehurst Ter", "Lakeside Pkwy"
    )

    private val citiesWithStates = listOf(
        Triple("Austin", "TX", "78701"),
        Triple("Seattle", "WA", "98101"),
        Triple("Denver", "CO", "80202"),
        Triple("Boston", "MA", "02108"),
        Triple("Portland", "OR", "97204"),
        Triple("Chicago", "IL", "60601"),
        Triple("Raleigh", "NC", "27601"),
        Triple("San Diego", "CA", "92101"),
        Triple("Atlanta", "GA", "30303"),
        Triple("Phoenix", "AZ", "85004")
    )

    private val handleAdjectives = listOf(
        "cobalt", "nova", "apex", "cipher", "velvet", "solar", "orbit", "arctic",
        "crimson", "amber", "prism", "horizon", "silver", "quantum", "nimbus", "zenith"
    )

    private val handleNouns = listOf(
        "falcon", "spark", "vector", "anchor", "harbor", "beacon", "kestrel", "mosaic",
        "echo", "glacier", "drift", "atlas", "summit", "timber", "lantern", "pulse"
    )

    private val companies = listOf(
        "Northstar Systems", "Veridian Labs", "Apex Horizon LLC", "Cobalt Works",
        "Silverleaf Studio", "Meridian Cloud", "Beacon Harbor Co.", "Prism Logistics"
    )

    private val recoveryAlphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    private val tokenAlphabet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"

    fun analyzeFolder(folder: AutomationVaultFolderEntity): VaultFolderAnalysis {
        if (folder.isDefault) {
            return when (folder.folderId) {
                AutomationVaultManager.FOLDER_ID_PASSWORDS -> VaultFolderAnalysis(
                    type = VaultGeneratorType.PASSWORD,
                    category = AutomationVaultCategory.PASSWORD,
                    badgeLabel = "Strong Password",
                    description = "16-char high-entropy password"
                )
                AutomationVaultManager.FOLDER_ID_EMAILS -> VaultFolderAnalysis(
                    type = VaultGeneratorType.EMAIL,
                    category = AutomationVaultCategory.EMAIL,
                    badgeLabel = "Email Address",
                    description = "Unique formatted email address"
                )
                AutomationVaultManager.FOLDER_ID_PHONE -> VaultFolderAnalysis(
                    type = VaultGeneratorType.PHONE,
                    category = AutomationVaultCategory.PHONE,
                    badgeLabel = "Phone Number",
                    description = "Formatted phone number"
                )
                AutomationVaultManager.FOLDER_ID_COUNTRY_CODE -> VaultFolderAnalysis(
                    type = VaultGeneratorType.COUNTRY_CODE,
                    category = AutomationVaultCategory.COUNTRY_CODE,
                    badgeLabel = "Country Code",
                    description = "International dial code (+1, +44, +91)"
                )
                AutomationVaultManager.FOLDER_ID_DATE_OF_BIRTH -> VaultFolderAnalysis(
                    type = VaultGeneratorType.DATE_OF_BIRTH,
                    category = AutomationVaultCategory.DATE_OF_BIRTH,
                    badgeLabel = "Date of Birth",
                    description = "Birth date (adapts to year/month/day fields)",
                    isBirthDate = true
                )
                AutomationVaultManager.FOLDER_ID_GENDER -> VaultFolderAnalysis(
                    type = VaultGeneratorType.GENDER,
                    category = AutomationVaultCategory.GENDER,
                    badgeLabel = "Gender",
                    description = "Gender option (Female, Male, Non-binary)"
                )
                AutomationVaultManager.FOLDER_ID_USERNAMES -> VaultFolderAnalysis(
                    type = VaultGeneratorType.USERNAME,
                    category = AutomationVaultCategory.USERNAME,
                    badgeLabel = "Username",
                    description = "Unique account handle"
                )
                AutomationVaultManager.FOLDER_ID_NAMES -> VaultFolderAnalysis(
                    type = VaultGeneratorType.FULL_NAME,
                    category = AutomationVaultCategory.NAME,
                    badgeLabel = "Full Name",
                    description = "Realistic first & last name"
                )
                AutomationVaultManager.FOLDER_ID_ADDRESSES -> VaultFolderAnalysis(
                    type = VaultGeneratorType.ADDRESS,
                    category = AutomationVaultCategory.ADDRESS,
                    badgeLabel = "Street Address",
                    description = "Street, city, state & ZIP"
                )
                else -> analyzeFolderName(folder.name, AutomationVaultCategory.fromId(folder.categoryId))
            }
        }
        return analyzeFolderName(
            folderName = folder.name,
            fallbackCategory = AutomationVaultCategory.fromId(folder.categoryId)
        )
    }

    fun analyzeFolderName(
        folderName: String,
        fallbackCategory: AutomationVaultCategory = AutomationVaultCategory.CUSTOM
    ): VaultFolderAnalysis {
        val clean = folderName.trim()
        val lower = clean.lowercase(Locale.US)
        val tokens = Regex("""[a-z0-9]+""").findAll(lower).map { it.value }.toSet()

        fun hasAnyWord(vararg words: String): Boolean = words.any { w ->
            if (w.contains(' ')) lower.contains(w) else w in tokens
        }

        // 1. Recovery / 2FA / Backup codes & OTP / PIN
        if (hasAnyWord("2fa", "mfa", "totp", "otp", "recovery", "backup code", "backup codes", "verification code", "security code")) {
            val isNumericOtp = hasAnyWord("otp", "totp", "sms") && !hasAnyWord("recovery", "backup")
            return if (isNumericOtp) {
                VaultFolderAnalysis(
                    type = VaultGeneratorType.PIN,
                    category = AutomationVaultCategory.PASSWORD,
                    badgeLabel = "6-Digit Code",
                    description = "Generated from folder name '$clean'",
                    pinDigits = 6
                )
            } else {
                VaultFolderAnalysis(
                    type = VaultGeneratorType.OTP_RECOVERY,
                    category = AutomationVaultCategory.PASSWORD,
                    badgeLabel = "Recovery Code",
                    description = "Generated from folder name '$clean'"
                )
            }
        }

        if (hasAnyWord("pin", "pins", "passcode", "passcodes", "atm", "lockcode")) {
            val digits = if (lower.contains("4")) 4 else if (lower.contains("8")) 8 else 6
            return VaultFolderAnalysis(
                type = VaultGeneratorType.PIN,
                category = AutomationVaultCategory.PASSWORD,
                badgeLabel = "$digits-Digit PIN",
                description = "Generated from folder name '$clean'",
                pinDigits = digits
            )
        }

        // 2. Passphrases & Passwords
        if (hasAnyWord("passphrase", "passphrases", "memorable", "seedphrase")) {
            return VaultFolderAnalysis(
                type = VaultGeneratorType.PASSPHRASE,
                category = AutomationVaultCategory.PASSWORD,
                badgeLabel = "Passphrase",
                description = "Generated from folder name '$clean'"
            )
        }

        if (hasAnyWord("password", "passwords", "pass", "pwd", "pw", "logins", "login", "credentials", "credential", "secrets", "secret") &&
            !hasAnyWord("api", "token", "tokens", "key", "keys", "webhook", "jwt", "bearer")
        ) {
            return VaultFolderAnalysis(
                type = VaultGeneratorType.PASSWORD,
                category = AutomationVaultCategory.PASSWORD,
                badgeLabel = "Strong Password",
                description = "16-char password for '$clean'"
            )
        }

        // 3. Emails (with domain bias detection!)
        if (hasAnyWord("email", "emails", "mail", "mails", "e-mail", "gmail", "outlook", "yahoo", "proton", "protonmail", "icloud", "inbox", "newsletter")) {
            val domainHint = when {
                hasAnyWord("gmail") -> "gmail.com"
                hasAnyWord("outlook", "hotmail") -> "outlook.com"
                hasAnyWord("yahoo") -> "yahoo.com"
                hasAnyWord("proton", "protonmail") -> "proton.me"
                hasAnyWord("icloud") -> "icloud.com"
                hasAnyWord("work", "office", "corp", "company", "business", "enterprise") -> workDomains[random.nextInt(workDomains.size)]
                hasAnyWord("temp", "disposable", "burner", "throwaway") -> "tempmail.org"
                else -> null
            }
            return VaultFolderAnalysis(
                type = VaultGeneratorType.EMAIL,
                category = AutomationVaultCategory.EMAIL,
                badgeLabel = if (domainHint != null) "Email (@$domainHint)" else "Email Address",
                description = "Generated from folder name '$clean'",
                domainHint = domainHint
            )
        }

        // 3b. Country / Dial Code (must come before phone)
        if (hasAnyWord("country code", "countrycode", "dial code", "dialcode", "calling code", "phone code", "isd", "idd")) {
            return VaultFolderAnalysis(
                type = VaultGeneratorType.COUNTRY_CODE,
                category = AutomationVaultCategory.COUNTRY_CODE,
                badgeLabel = "Country Code",
                description = "Generated from folder name '$clean'"
            )
        }

        // 4. Phone / Mobile
        if (hasAnyWord("phone", "phones", "mobile", "mobiles", "cell", "telephone", "whatsapp", "contact", "contacts", "call", "fax")) {
            val countryHint = when {
                hasAnyWord("uk", "britain", "london") -> "UK"
                hasAnyWord("india", "in") -> "IN"
                else -> "US"
            }
            return VaultFolderAnalysis(
                type = VaultGeneratorType.PHONE,
                category = AutomationVaultCategory.PHONE,
                badgeLabel = "Phone Number",
                description = "Generated from folder name '$clean'",
                phoneCountryHint = countryHint
            )
        }

        // 4b. Gender
        if (hasAnyWord("gender", "genders", "sex", "pronoun", "pronouns")) {
            return VaultFolderAnalysis(
                type = VaultGeneratorType.GENDER,
                category = AutomationVaultCategory.GENDER,
                badgeLabel = "Gender",
                description = "Generated from folder name '$clean'"
            )
        }

        // 5. Usernames / Handles
        if (hasAnyWord("username", "usernames", "handle", "handles", "gamer", "gamertag", "nickname", "nicknames", "alias", "aliases", "userid")) {
            return VaultFolderAnalysis(
                type = VaultGeneratorType.USERNAME,
                category = AutomationVaultCategory.USERNAME,
                badgeLabel = "Username",
                description = "Generated from folder name '$clean'"
            )
        }

        // 6. Names / Company
        if (hasAnyWord("first name", "firstname", "first names", "given name")) {
            return VaultFolderAnalysis(
                type = VaultGeneratorType.FIRST_NAME,
                category = AutomationVaultCategory.NAME,
                badgeLabel = "First Name",
                description = "Generated from folder name '$clean'"
            )
        }
        if (hasAnyWord("last name", "lastname", "last names", "surname", "surnames", "family name")) {
            return VaultFolderAnalysis(
                type = VaultGeneratorType.LAST_NAME,
                category = AutomationVaultCategory.NAME,
                badgeLabel = "Last Name",
                description = "Generated from folder name '$clean'"
            )
        }
        if (hasAnyWord("company", "companies", "organization", "org", "startup", "business", "employer")) {
            return VaultFolderAnalysis(
                type = VaultGeneratorType.COMPANY,
                category = AutomationVaultCategory.NAME,
                badgeLabel = "Company Name",
                description = "Generated from folder name '$clean'"
            )
        }
        if (hasAnyWord("name", "names", "full name", "fullname", "profile", "profiles", "people", "person", "member", "members")) {
            return VaultFolderAnalysis(
                type = VaultGeneratorType.FULL_NAME,
                category = AutomationVaultCategory.NAME,
                badgeLabel = "Full Name",
                description = "Generated from folder name '$clean'"
            )
        }

        // 7. Address / City / Postal Code
        if (hasAnyWord("zip", "zipcode", "zipcodes", "postal", "postcode", "postcodes")) {
            return VaultFolderAnalysis(
                type = VaultGeneratorType.POSTAL_CODE,
                category = AutomationVaultCategory.ADDRESS,
                badgeLabel = "ZIP / Postal Code",
                description = "Generated from folder name '$clean'"
            )
        }
        if (hasAnyWord("city", "cities", "town", "location")) {
            return VaultFolderAnalysis(
                type = VaultGeneratorType.CITY,
                category = AutomationVaultCategory.ADDRESS,
                badgeLabel = "City",
                description = "Generated from folder name '$clean'"
            )
        }
        if (hasAnyWord("address", "addresses", "street", "streets", "shipping", "billing", "home", "residence", "delivery")) {
            return VaultFolderAnalysis(
                type = VaultGeneratorType.ADDRESS,
                category = AutomationVaultCategory.ADDRESS,
                badgeLabel = "Street Address",
                description = "Generated from folder name '$clean'"
            )
        }

        // 8. API Keys, Tokens, UUIDs
        if (hasAnyWord("uuid", "uuids", "guid", "guids")) {
            return VaultFolderAnalysis(
                type = VaultGeneratorType.UUID_ID,
                category = AutomationVaultCategory.CUSTOM,
                badgeLabel = "UUID v4",
                description = "Generated from folder name '$clean'"
            )
        }
        if (hasAnyWord("api", "apis", "token", "tokens", "bearer", "jwt", "apikey", "apikeys", "webhook", "webhooks", "ssh", "pat", "oauth", "secret key", "secret keys", "access key", "access keys")) {
            return VaultFolderAnalysis(
                type = VaultGeneratorType.API_KEY,
                category = AutomationVaultCategory.CUSTOM,
                badgeLabel = "API Key / Token",
                description = "Generated from folder name '$clean'"
            )
        }

        // 9. URLs / Links / Domains
        if (hasAnyWord("url", "urls", "link", "links", "website", "websites", "domain", "domains", "site", "sites", "endpoint", "endpoints")) {
            return VaultFolderAnalysis(
                type = VaultGeneratorType.URL,
                category = AutomationVaultCategory.CUSTOM,
                badgeLabel = "Website URL",
                description = "Generated from folder name '$clean'"
            )
        }

        // 10. Dates / Birthdays / Expiry
        if (hasAnyWord("birthday", "birthdays", "birth", "dob", "birthdate", "bday", "date of birth")) {
            return VaultFolderAnalysis(
                type = VaultGeneratorType.DATE_OF_BIRTH,
                category = AutomationVaultCategory.DATE_OF_BIRTH,
                badgeLabel = "Birth Date",
                description = "Generated from folder name '$clean'",
                isBirthDate = true
            )
        }
        if (hasAnyWord("date", "dates", "calendar", "expiry", "expiration", "schedule", "schedules")) {
            val isExp = hasAnyWord("expiry", "expiration")
            return VaultFolderAnalysis(
                type = VaultGeneratorType.DATE,
                category = AutomationVaultCategory.CUSTOM,
                badgeLabel = if (isExp) "Expiry (MM/YY)" else "Date (YYYY-MM-DD)",
                description = "Generated from folder name '$clean'",
                isBirthDate = false,
                isExpiryDate = isExp
            )
        }

        // 11. Promo / Coupon / License / Serial keys
        if (hasAnyWord("promo", "coupon", "coupons", "voucher", "vouchers", "discount", "license", "licenses", "serial", "activation", "invite", "referral")) {
            return VaultFolderAnalysis(
                type = VaultGeneratorType.PROMO_CODE,
                category = AutomationVaultCategory.CUSTOM,
                badgeLabel = "Promo / Key Code",
                description = "Generated from folder name '$clean'",
                contextualPrefix = buildPrefixFromFolderName(clean)
            )
        }

        // 12. Sandbox Test Cards
        if (hasAnyWord("card", "cards", "credit", "debit", "visa", "mastercard", "cvv", "payment")) {
            return VaultFolderAnalysis(
                type = VaultGeneratorType.TEST_CARD,
                category = AutomationVaultCategory.CUSTOM,
                badgeLabel = "Test Card Number",
                description = "Luhn-valid sandbox number for '$clean'"
            )
        }

        // 13. Network IP
        if (hasAnyWord("ip", "ips", "ipv4", "proxy", "proxies", "server", "servers", "host", "hosts")) {
            return VaultFolderAnalysis(
                type = VaultGeneratorType.IP_ADDRESS,
                category = AutomationVaultCategory.CUSTOM,
                badgeLabel = "IP Address",
                description = "Generated from folder name '$clean'"
            )
        }

        // 14. Hex Color
        if (hasAnyWord("color", "colors", "colour", "colours", "hex", "palette", "swatch")) {
            return VaultFolderAnalysis(
                type = VaultGeneratorType.COLOR_HEX,
                category = AutomationVaultCategory.CUSTOM,
                badgeLabel = "Hex Color",
                description = "Generated from folder name '$clean'"
            )
        }

        // 15. Generic code / ID / number / key / badge
        if (hasAnyWord("id", "ids", "code", "codes", "number", "numbers", "num", "ref", "reference", "ticket", "tickets", "order", "orders", "invoice", "employee", "member", "account", "tag", "tags", "key", "keys")) {
            val prefix = buildPrefixFromFolderName(clean)
            return VaultFolderAnalysis(
                type = VaultGeneratorType.CONTEXTUAL_CODE,
                category = AutomationVaultCategory.CUSTOM,
                badgeLabel = "$prefix Code",
                description = "Pattern inferred from '$clean'",
                contextualPrefix = prefix
            )
        }

        // 16. Fallback honors stored folder category if non-CUSTOM, else contextual folder value
        val prefix = buildPrefixFromFolderName(clean)
        return when (fallbackCategory) {
            AutomationVaultCategory.PASSWORD -> VaultFolderAnalysis(
                type = VaultGeneratorType.PASSWORD,
                category = AutomationVaultCategory.PASSWORD,
                badgeLabel = "Strong Password",
                description = "Generated for '$clean'"
            )
            AutomationVaultCategory.EMAIL -> VaultFolderAnalysis(
                type = VaultGeneratorType.EMAIL,
                category = AutomationVaultCategory.EMAIL,
                badgeLabel = "Email Address",
                description = "Generated for '$clean'"
            )
            AutomationVaultCategory.PHONE -> VaultFolderAnalysis(
                type = VaultGeneratorType.PHONE,
                category = AutomationVaultCategory.PHONE,
                badgeLabel = "Phone Number",
                description = "Generated for '$clean'"
            )
            AutomationVaultCategory.COUNTRY_CODE -> VaultFolderAnalysis(
                type = VaultGeneratorType.COUNTRY_CODE,
                category = AutomationVaultCategory.COUNTRY_CODE,
                badgeLabel = "Country Code",
                description = "Generated for '$clean'"
            )
            AutomationVaultCategory.DATE_OF_BIRTH -> VaultFolderAnalysis(
                type = VaultGeneratorType.DATE_OF_BIRTH,
                category = AutomationVaultCategory.DATE_OF_BIRTH,
                badgeLabel = "Date of Birth",
                description = "Generated for '$clean'",
                isBirthDate = true
            )
            AutomationVaultCategory.GENDER -> VaultFolderAnalysis(
                type = VaultGeneratorType.GENDER,
                category = AutomationVaultCategory.GENDER,
                badgeLabel = "Gender",
                description = "Generated for '$clean'"
            )
            AutomationVaultCategory.USERNAME -> VaultFolderAnalysis(
                type = VaultGeneratorType.USERNAME,
                category = AutomationVaultCategory.USERNAME,
                badgeLabel = "Username",
                description = "Generated for '$clean'"
            )
            AutomationVaultCategory.NAME -> VaultFolderAnalysis(
                type = VaultGeneratorType.FULL_NAME,
                category = AutomationVaultCategory.NAME,
                badgeLabel = "Full Name",
                description = "Generated for '$clean'"
            )
            AutomationVaultCategory.ADDRESS -> VaultFolderAnalysis(
                type = VaultGeneratorType.ADDRESS,
                category = AutomationVaultCategory.ADDRESS,
                badgeLabel = "Street Address",
                description = "Generated for '$clean'"
            )
            AutomationVaultCategory.CUSTOM -> VaultFolderAnalysis(
                type = VaultGeneratorType.CUSTOM_VALUE,
                category = AutomationVaultCategory.CUSTOM,
                badgeLabel = if (clean.isNotBlank()) "Smart '$clean'" else "Custom Value",
                description = if (clean.isNotBlank()) "Tailored to '$clean'" else "Unique generated value",
                contextualPrefix = prefix
            )
        }
    }

    fun generateValue(
        folder: AutomationVaultFolderEntity,
        overrideType: VaultGeneratorType? = null,
        existingValues: Set<String> = emptySet()
    ): Pair<VaultFolderAnalysis, String> {
        val baseAnalysis = analyzeFolder(folder)
        val effectiveType = overrideType ?: baseAnalysis.type
        val effectiveAnalysis = if (overrideType != null && overrideType != baseAnalysis.type) {
            baseAnalysis.copy(
                type = overrideType,
                category = overrideType.category,
                badgeLabel = overrideType.displayName
            )
        } else {
            baseAnalysis
        }

        var candidate = ""
        repeat(8) {
            candidate = generateSingle(effectiveAnalysis, folder.name)
            if (candidate.isNotBlank() && candidate !in existingValues) {
                return effectiveAnalysis to candidate
            }
        }
        return effectiveAnalysis to candidate
    }

    private fun generateSingle(analysis: VaultFolderAnalysis, rawFolderName: String): String {
        return when (analysis.type) {
            VaultGeneratorType.PASSWORD -> SecurePasswordGenerator.generateRandom(length = 16)
            VaultGeneratorType.PASSPHRASE -> SecurePasswordGenerator.generatePassphrase(wordCount = 4, includeNumberAndSymbol = true)
            VaultGeneratorType.PIN -> buildString {
                repeat(analysis.pinDigits.coerceIn(4, 10)) {
                    append(random.nextInt(10))
                }
            }
            VaultGeneratorType.OTP_RECOVERY -> (0 until 3).joinToString("-") {
                randomChars(recoveryAlphabet, 4)
            }
            VaultGeneratorType.EMAIL -> {
                val first = firstNames[random.nextInt(firstNames.size)].lowercase(Locale.US)
                val last = lastNames[random.nextInt(lastNames.size)].lowercase(Locale.US)
                val num = 100 + random.nextInt(9000)
                val domain = analysis.domainHint ?: emailDomains[random.nextInt(emailDomains.size)]
                "$first.$last$num@$domain"
            }
            VaultGeneratorType.PHONE -> when (analysis.phoneCountryHint) {
                "UK" -> "+44 7${100 + random.nextInt(899)} ${100000 + random.nextInt(899999)}"
                "IN" -> "+91 ${7 + random.nextInt(3)}${100000000 + random.nextInt(899999999)}"
                else -> {
                    val area = 201 + random.nextInt(789)
                    val prefix = 200 + random.nextInt(799)
                    val line = 1000 + random.nextInt(9000)
                    "+1 ($area) $prefix-$line"
                }
            }
            VaultGeneratorType.COUNTRY_CODE -> {
                val codes = listOf("+1", "+44", "+91", "+61", "+81", "+49", "+33", "+55", "+82", "+65", "+971", "+34", "+39", "+31", "+46", "+41")
                codes[random.nextInt(codes.size)]
            }
            VaultGeneratorType.DATE_OF_BIRTH -> {
                val year = 1978 + random.nextInt(27)
                val month = 1 + random.nextInt(12)
                val day = 1 + random.nextInt(28)
                String.format(Locale.US, "%04d-%02d-%02d", year, month, day)
            }
            VaultGeneratorType.GENDER -> {
                val genders = listOf("Female", "Male", "Non-binary")
                genders[random.nextInt(genders.size)]
            }
            VaultGeneratorType.USERNAME -> {
                val adj = handleAdjectives[random.nextInt(handleAdjectives.size)]
                val noun = handleNouns[random.nextInt(handleNouns.size)]
                val num = 10 + random.nextInt(990)
                val sep = if (random.nextBoolean()) "_" else "."
                "$adj$sep$noun$num"
            }
            VaultGeneratorType.FULL_NAME -> {
                val first = firstNames[random.nextInt(firstNames.size)]
                val last = lastNames[random.nextInt(lastNames.size)]
                "$first $last"
            }
            VaultGeneratorType.FIRST_NAME -> firstNames[random.nextInt(firstNames.size)]
            VaultGeneratorType.LAST_NAME -> lastNames[random.nextInt(lastNames.size)]
            VaultGeneratorType.COMPANY -> companies[random.nextInt(companies.size)]
            VaultGeneratorType.ADDRESS -> {
                val house = 100 + random.nextInt(9899)
                val street = streetNames[random.nextInt(streetNames.size)]
                val (city, state, zipBase) = citiesWithStates[random.nextInt(citiesWithStates.size)]
                val zip = (zipBase.toIntOrNull()?.plus(random.nextInt(80)) ?: 10001)
                "$house $street, $city, $state $zip"
            }
            VaultGeneratorType.CITY -> {
                val (city, state, _) = citiesWithStates[random.nextInt(citiesWithStates.size)]
                "$city, $state"
            }
            VaultGeneratorType.POSTAL_CODE -> {
                String.format(Locale.US, "%05d", 10001 + random.nextInt(89990))
            }
            VaultGeneratorType.API_KEY -> {
                val lower = rawFolderName.lowercase(Locale.US)
                val prefix = when {
                    lower.contains("live") -> "sk_live_"
                    lower.contains("test") -> "sk_test_"
                    lower.contains("bearer") || lower.contains("jwt") -> "tok_"
                    else -> "apx_"
                }
                prefix + randomChars(tokenAlphabet, 28)
            }
            VaultGeneratorType.UUID_ID -> UUID.randomUUID().toString()
            VaultGeneratorType.URL -> {
                val slug = rawFolderName.lowercase(Locale.US)
                    .replace(Regex("""[^a-z0-9]+"""), "-")
                    .trim('-')
                    .take(18)
                    .ifBlank { "portal" }
                val suffix = 10 + random.nextInt(90)
                "https://$slug-$suffix.example.com"
            }
            VaultGeneratorType.DATE -> when {
                analysis.isBirthDate -> {
                    val year = 1978 + random.nextInt(25)
                    val month = 1 + random.nextInt(12)
                    val day = 1 + random.nextInt(28)
                    String.format(Locale.US, "%04d-%02d-%02d", year, month, day)
                }
                analysis.isExpiryDate -> {
                    val month = 1 + random.nextInt(12)
                    val year = 27 + random.nextInt(6)
                    String.format(Locale.US, "%02d/%02d", month, year)
                }
                else -> {
                    val year = 2025 + random.nextInt(4)
                    val month = 1 + random.nextInt(12)
                    val day = 1 + random.nextInt(28)
                    String.format(Locale.US, "%04d-%02d-%02d", year, month, day)
                }
            }
            VaultGeneratorType.PROMO_CODE -> {
                val pfx = analysis.contextualPrefix.ifBlank { "PROMO" }
                "$pfx-${randomChars(recoveryAlphabet, 4)}-${randomChars(recoveryAlphabet, 4)}"
            }
            VaultGeneratorType.TEST_CARD -> {
                // Generate 16-digit Luhn-valid test number starting with 411111
                val digits = IntArray(16)
                val seed = intArrayOf(4, 1, 1, 1, 1, 1)
                for (i in seed.indices) digits[i] = seed[i]
                for (i in 6..14) digits[i] = random.nextInt(10)
                digits[15] = computeLuhnCheckDigit(digits)
                digits.joinToString("").chunked(4).joinToString("-")
            }
            VaultGeneratorType.IP_ADDRESS -> {
                "192.168.${1 + random.nextInt(250)}.${2 + random.nextInt(250)}"
            }
            VaultGeneratorType.COLOR_HEX -> {
                String.format(Locale.US, "#%06X", random.nextInt(0x1000000))
            }
            VaultGeneratorType.CONTEXTUAL_CODE -> {
                val pfx = analysis.contextualPrefix.ifBlank { "ID" }
                "$pfx-${100000 + random.nextInt(900000)}"
            }
            VaultGeneratorType.CUSTOM_VALUE -> {
                val cleanSlug = rawFolderName.trim()
                    .lowercase(Locale.US)
                    .replace(Regex("""[^a-z0-9]+"""), "-")
                    .trim('-')
                    .take(20)
                    .ifBlank { "value" }
                "$cleanSlug-${randomChars(recoveryAlphabet, 6).lowercase(Locale.US)}"
            }
        }
    }

    private fun buildPrefixFromFolderName(folderName: String): String {
        val words = Regex("""[A-Za-z0-9]+""")
            .findAll(folderName)
            .map { it.value.uppercase(Locale.US) }
            .filter { it !in setOf("MY", "THE", "FOR", "AND", "OR", "VALUES", "FOLDER", "LIST") }
            .toList()
        if (words.isEmpty()) return "VAL"
        if (words.size == 1) {
            return words.first().take(4).ifBlank { "VAL" }
        }
        val initials = words.take(4).mapNotNull { it.firstOrNull() }.joinToString("")
        return initials.ifBlank { words.first().take(4) }
    }

    private fun randomChars(alphabet: String, count: Int): String = buildString(count) {
        repeat(count) {
            append(alphabet[random.nextInt(alphabet.length)])
        }
    }

    private fun computeLuhnCheckDigit(digits: IntArray): Int {
        var sum = 0
        for (i in 0 until 15) {
            var d = digits[14 - i]
            if (i % 2 == 0) {
                d *= 2
                if (d > 9) d -= 9
            }
            sum += d
        }
        return (10 - (sum % 10)) % 10
    }
}
