package com.example.automation

import java.util.Locale

enum class FieldSemanticType(
    val id: String,
    val label: String,
    val isSensitive: Boolean = false,
    val vaultCategory: AutomationVaultCategory = AutomationVaultCategory.CUSTOM,
    val aliases: Set<String> = emptySet()
) {
    NONE("NONE", "Generic Field"),

    // Identity & Name
    FIRST_NAME(
        id = "FIRST_NAME",
        label = "First Name",
        vaultCategory = AutomationVaultCategory.NAME,
        aliases = setOf("first name", "firstname", "fname", "given name", "given-name", "forename")
    ),
    MIDDLE_NAME(
        id = "MIDDLE_NAME",
        label = "Middle Name",
        vaultCategory = AutomationVaultCategory.NAME,
        aliases = setOf("middle name", "middlename", "mname", "additional name", "additional-name", "middle initial")
    ),
    LAST_NAME(
        id = "LAST_NAME",
        label = "Last Name",
        vaultCategory = AutomationVaultCategory.NAME,
        aliases = setOf("last name", "lastname", "lname", "surname", "family name", "family-name")
    ),
    FULL_NAME(
        id = "FULL_NAME",
        label = "Full Name",
        vaultCategory = AutomationVaultCategory.NAME,
        aliases = setOf("full name", "fullname", "your name", "name", "display name", "contact name", "recipient")
    ),
    NICKNAME(
        id = "NICKNAME",
        label = "Nickname",
        vaultCategory = AutomationVaultCategory.USERNAME,
        aliases = setOf("nickname", "alias", "screen name", "screenname")
    ),
    USERNAME(
        id = "USERNAME",
        label = "Username",
        vaultCategory = AutomationVaultCategory.USERNAME,
        aliases = setOf("username", "user name", "user id", "userid", "handle", "login id", "account name")
    ),
    TITLE_PREFIX(
        id = "TITLE_PREFIX",
        label = "Title / Prefix",
        vaultCategory = AutomationVaultCategory.NAME,
        aliases = setOf("salutation", "honorific", "honorific-prefix", "name prefix", "mr mrs")
    ),
    NAME_SUFFIX(
        id = "NAME_SUFFIX",
        label = "Name Suffix",
        vaultCategory = AutomationVaultCategory.NAME,
        aliases = setOf("name suffix", "honorific-suffix", "jr sr")
    ),
    GENDER(
        id = "GENDER",
        label = "Gender",
        vaultCategory = AutomationVaultCategory.GENDER,
        aliases = setOf("gender", "sex", "pronoun", "pronouns")
    ),

    // Contact & Web
    EMAIL(
        id = "EMAIL",
        label = "Email",
        vaultCategory = AutomationVaultCategory.EMAIL,
        aliases = setOf("email", "e-mail", "email address", "mail", "work email", "personal email", "recovery email")
    ),
    PHONE(
        id = "PHONE",
        label = "Phone Number",
        vaultCategory = AutomationVaultCategory.PHONE,
        aliases = setOf("phone", "phone number", "mobile", "telephone", "cell", "cellphone", "whatsapp", "tel", "contact number")
    ),
    COUNTRY_CODE(
        id = "COUNTRY_CODE",
        label = "Country Code",
        vaultCategory = AutomationVaultCategory.COUNTRY_CODE,
        aliases = setOf("country code", "dial code", "calling code", "phone code", "isd", "tel-country-code")
    ),
    PHONE_EXTENSION(
        id = "PHONE_EXTENSION",
        label = "Phone Ext",
        vaultCategory = AutomationVaultCategory.PHONE,
        aliases = setOf("extension", "phone ext", "ext", "tel-extension")
    ),
    WEBSITE_URL(
        id = "WEBSITE_URL",
        label = "Website / URL",
        aliases = setOf("website", "web site", "url", "homepage", "portfolio", "blog", "domain", "site url")
    ),
    SOCIAL_HANDLE(
        id = "SOCIAL_HANDLE",
        label = "Social Profile",
        aliases = setOf("linkedin", "github", "twitter", "instagram", "facebook", "youtube", "tiktok", "mastodon", "discord", "telegram", "slack", "social link")
    ),

    // Address & Location
    STREET_ADDRESS(
        id = "STREET_ADDRESS",
        label = "Street Address",
        vaultCategory = AutomationVaultCategory.ADDRESS,
        aliases = setOf("street address", "street", "address", "address line 1", "address-line1", "street-address", "billing address", "shipping address", "mailing address")
    ),
    ADDRESS_LINE_2(
        id = "ADDRESS_LINE_2",
        label = "Apt / Suite / Line 2",
        vaultCategory = AutomationVaultCategory.ADDRESS,
        aliases = setOf("address line 2", "address-line2", "apt", "apartment", "suite", "unit", "floor", "building", "ste")
    ),
    CITY(
        id = "CITY",
        label = "City",
        vaultCategory = AutomationVaultCategory.ADDRESS,
        aliases = setOf("city", "town", "locality", "suburb", "municipality", "address-level2")
    ),
    STATE_PROVINCE(
        id = "STATE_PROVINCE",
        label = "State / Province",
        vaultCategory = AutomationVaultCategory.ADDRESS,
        aliases = setOf("state", "province", "region", "county", "prefecture", "emirate", "territory", "address-level1")
    ),
    POSTAL_CODE(
        id = "POSTAL_CODE",
        label = "ZIP / Postal Code",
        vaultCategory = AutomationVaultCategory.ADDRESS,
        aliases = setOf("zip", "zipcode", "zip code", "postal code", "postal", "postcode", "pincode", "eircode", "postal-code")
    ),
    COUNTRY(
        id = "COUNTRY",
        label = "Country",
        vaultCategory = AutomationVaultCategory.ADDRESS,
        aliases = setOf("country", "country-name", "nation", "country of residence")
    ),
    LANGUAGE_LOCALE(
        id = "LANGUAGE_LOCALE",
        label = "Language",
        aliases = setOf("language", "locale", "preferred language", "lang")
    ),
    TIMEZONE(
        id = "TIMEZONE",
        label = "Timezone",
        aliases = setOf("timezone", "time zone", "tz")
    ),

    // Dates, Times & Birth
    DATE_OF_BIRTH(
        id = "DATE_OF_BIRTH",
        label = "Date of Birth",
        vaultCategory = AutomationVaultCategory.DATE_OF_BIRTH,
        aliases = setOf("date of birth", "dob", "birthday", "birthdate", "birth date", "bday")
    ),
    BIRTH_YEAR(
        id = "BIRTH_YEAR",
        label = "Birth Year",
        vaultCategory = AutomationVaultCategory.DATE_OF_BIRTH,
        aliases = setOf("birth year", "year of birth", "bday-year", "dob year")
    ),
    BIRTH_MONTH(
        id = "BIRTH_MONTH",
        label = "Birth Month",
        vaultCategory = AutomationVaultCategory.DATE_OF_BIRTH,
        aliases = setOf("birth month", "month of birth", "bday-month", "dob month")
    ),
    BIRTH_DAY(
        id = "BIRTH_DAY",
        label = "Birth Day",
        vaultCategory = AutomationVaultCategory.DATE_OF_BIRTH,
        aliases = setOf("birth day", "day of birth", "bday-day", "dob day")
    ),
    DATE(
        id = "DATE",
        label = "Date",
        aliases = setOf("date", "start date", "end date", "check in", "check out", "departure", "return date", "appointment date")
    ),
    TIME(
        id = "TIME",
        label = "Time",
        aliases = setOf("time", "time slot", "appointment time", "start time", "end time")
    ),
    AGE(
        id = "AGE",
        label = "Age",
        aliases = setOf("age", "your age")
    ),

    // Organization & Work
    COMPANY(
        id = "COMPANY",
        label = "Company / Org",
        vaultCategory = AutomationVaultCategory.NAME,
        aliases = setOf("company", "company name", "organization", "organisation", "employer", "business name", "firm", "corp", "workspace", "team name", "institution", "school", "university")
    ),
    JOB_TITLE(
        id = "JOB_TITLE",
        label = "Job Title",
        aliases = setOf("job title", "role title", "job role", "work title", "position", "occupation", "designation", "profession", "organization-title", "headline", "career")
    ),
    DEPARTMENT(
        id = "DEPARTMENT",
        label = "Department",
        aliases = setOf("department", "division", "team", "faculty", "branch")
    ),

    // Auth, Security & Verification
    PASSWORD(
        id = "PASSWORD",
        label = "Password",
        isSensitive = true,
        vaultCategory = AutomationVaultCategory.PASSWORD,
        aliases = setOf("password", "passwd", "pwd", "passphrase", "current-password", "new-password")
    ),
    CONFIRM_PASSWORD(
        id = "CONFIRM_PASSWORD",
        label = "Confirm Password",
        isSensitive = true,
        vaultCategory = AutomationVaultCategory.PASSWORD,
        aliases = setOf("confirm password", "re-enter password", "reenter password", "retype password", "repeat password", "verify password", "password confirmation")
    ),
    OTP_CODE(
        id = "OTP_CODE",
        label = "Verification / OTP",
        isSensitive = true,
        vaultCategory = AutomationVaultCategory.PASSWORD,
        aliases = setOf("otp", "one-time-code", "verification code", "2fa", "mfa", "totp", "auth code", "sms code", "security code")
    ),
    PIN_CODE(
        id = "PIN_CODE",
        label = "PIN Code",
        isSensitive = true,
        vaultCategory = AutomationVaultCategory.PASSWORD,
        aliases = setOf("pin", "passcode", "pin code")
    ),
    SECURITY_QUESTION(
        id = "SECURITY_QUESTION",
        label = "Security Question",
        isSensitive = true,
        aliases = setOf("security question", "security answer", "maiden name", "secret answer")
    ),
    PERSONAL_ID(
        id = "PERSONAL_ID",
        label = "National / Tax ID",
        isSensitive = true,
        aliases = setOf("ssn", "social security", "tax id", "ein", "vat", "tin", "national id", "passport", "driver license", "license number")
    ),

    // Payment & Commerce
    CARDHOLDER_NAME(
        id = "CARDHOLDER_NAME",
        label = "Cardholder Name",
        vaultCategory = AutomationVaultCategory.NAME,
        aliases = setOf("cardholder", "card holder", "name on card", "cc-name", "cardholder name")
    ),
    CREDIT_CARD(
        id = "CREDIT_CARD",
        label = "Card Number",
        isSensitive = true,
        aliases = setOf("card number", "credit card", "debit card", "cc-number", "cardnumber")
    ),
    CARD_EXPIRY(
        id = "CARD_EXPIRY",
        label = "Card Expiry",
        aliases = setOf("expiration", "expiry", "exp date", "expiration date", "cc-exp", "valid thru", "mm/yy", "mm/yyyy")
    ),
    CARD_CVV(
        id = "CARD_CVV",
        label = "Card CVV / CVC",
        isSensitive = true,
        aliases = setOf("cvv", "cvc", "csc", "cc-csc", "card verification", "security code")
    ),
    BANK_ACCOUNT(
        id = "BANK_ACCOUNT",
        label = "Bank / Routing",
        isSensitive = true,
        aliases = setOf("bank account", "iban", "routing number", "swift", "sort code", "account number")
    ),
    PROMO_CODE(
        id = "PROMO_CODE",
        label = "Promo / Coupon",
        aliases = setOf("promo", "promo code", "coupon", "coupon code", "voucher", "discount code", "referral", "referral code", "invite code", "gift card", "license key")
    ),
    QUANTITY(
        id = "QUANTITY",
        label = "Quantity",
        aliases = setOf("quantity", "qty", "count", "guests", "adults", "children", "rooms", "seats", "tickets", "units")
    ),
    AMOUNT_PRICE(
        id = "AMOUNT_PRICE",
        label = "Amount / Price",
        aliases = setOf("amount", "price", "cost", "budget", "salary", "income", "total", "donation", "tip", "bid", "payment amount")
    ),
    NUMERIC_RANGE(
        id = "NUMERIC_RANGE",
        label = "Range / Slider",
        aliases = setOf("range", "slider", "rating", "score", "volume", "zoom", "min", "max")
    ),

    // Content, Search, Files & Preferences
    SEARCH(
        id = "SEARCH",
        label = "Search",
        aliases = setOf("search", "query", "find", "lookup", "filter", "searchbox")
    ),
    MESSAGE_NOTES(
        id = "MESSAGE_NOTES",
        label = "Notes / Message",
        aliases = setOf("message", "notes", "note", "comment", "comments", "description", "bio", "about", "about me", "feedback", "inquiry", "cover letter", "summary", "reason", "details", "remarks")
    ),
    SUBJECT_TITLE(
        id = "SUBJECT_TITLE",
        label = "Subject / Title",
        aliases = setOf("subject", "headline", "topic", "issue title", "project title", "task title", "post title")
    ),
    CATEGORY_TAG(
        id = "CATEGORY_TAG",
        label = "Category / Tag",
        aliases = setOf("category", "topic", "tag", "tags", "industry", "department", "priority", "status")
    ),
    FILE_UPLOAD(
        id = "FILE_UPLOAD",
        label = "File Upload",
        aliases = setOf("upload", "file", "attachment", "attach", "resume", "cv", "avatar", "photo", "image", "document", "pdf")
    ),
    COLOR_PICKER(
        id = "COLOR_PICKER",
        label = "Color Picker",
        aliases = setOf("color", "colour", "hex", "swatch", "theme color")
    ),
    CONSENT_TERMS(
        id = "CONSENT_TERMS",
        label = "Terms / Consent",
        aliases = setOf("terms", "privacy", "agree", "consent", "accept terms", "policy", "newsletter", "subscribe", "opt in", "remember me")
    ),
    CAPTCHA_CHALLENGE(
        id = "CAPTCHA_CHALLENGE",
        label = "Captcha / Check",
        aliases = setOf("captcha", "recaptcha", "turnstile", "hcaptcha", "human verification")
    );

    companion object {
        fun fromId(raw: String?): FieldSemanticType {
            val clean = raw?.trim().orEmpty()
            if (clean.isEmpty()) return NONE
            return entries.firstOrNull { it.id.equals(clean, ignoreCase = true) } ?: NONE
        }
    }
}

object FieldSemanticClassifier {

    private val AUTOCOMPLETE_EXACT_MAP = mapOf(
        "given-name" to FieldSemanticType.FIRST_NAME,
        "additional-name" to FieldSemanticType.MIDDLE_NAME,
        "family-name" to FieldSemanticType.LAST_NAME,
        "name" to FieldSemanticType.FULL_NAME,
        "nickname" to FieldSemanticType.NICKNAME,
        "username" to FieldSemanticType.USERNAME,
        "honorific-prefix" to FieldSemanticType.TITLE_PREFIX,
        "honorific-suffix" to FieldSemanticType.NAME_SUFFIX,
        "sex" to FieldSemanticType.GENDER,
        "email" to FieldSemanticType.EMAIL,
        "url" to FieldSemanticType.WEBSITE_URL,
        "tel" to FieldSemanticType.PHONE,
        "tel-national" to FieldSemanticType.PHONE,
        "tel-local" to FieldSemanticType.PHONE,
        "tel-country-code" to FieldSemanticType.COUNTRY_CODE,
        "tel-extension" to FieldSemanticType.PHONE_EXTENSION,
        "street-address" to FieldSemanticType.STREET_ADDRESS,
        "address-line1" to FieldSemanticType.STREET_ADDRESS,
        "address-line2" to FieldSemanticType.ADDRESS_LINE_2,
        "address-line3" to FieldSemanticType.ADDRESS_LINE_2,
        "address-level2" to FieldSemanticType.CITY,
        "address-level1" to FieldSemanticType.STATE_PROVINCE,
        "postal-code" to FieldSemanticType.POSTAL_CODE,
        "country" to FieldSemanticType.COUNTRY,
        "country-name" to FieldSemanticType.COUNTRY,
        "language" to FieldSemanticType.LANGUAGE_LOCALE,
        "bday" to FieldSemanticType.DATE_OF_BIRTH,
        "bday-year" to FieldSemanticType.BIRTH_YEAR,
        "bday-month" to FieldSemanticType.BIRTH_MONTH,
        "bday-day" to FieldSemanticType.BIRTH_DAY,
        "organization" to FieldSemanticType.COMPANY,
        "organization-title" to FieldSemanticType.JOB_TITLE,
        "current-password" to FieldSemanticType.PASSWORD,
        "new-password" to FieldSemanticType.PASSWORD,
        "one-time-code" to FieldSemanticType.OTP_CODE,
        "cc-name" to FieldSemanticType.CARDHOLDER_NAME,
        "cc-given-name" to FieldSemanticType.CARDHOLDER_NAME,
        "cc-family-name" to FieldSemanticType.CARDHOLDER_NAME,
        "cc-number" to FieldSemanticType.CREDIT_CARD,
        "cc-exp" to FieldSemanticType.CARD_EXPIRY,
        "cc-exp-month" to FieldSemanticType.CARD_EXPIRY,
        "cc-exp-year" to FieldSemanticType.CARD_EXPIRY,
        "cc-csc" to FieldSemanticType.CARD_CVV
    )

    fun isFieldControl(
        tagName: String,
        inputType: String,
        role: String,
        contentEditable: String,
        controlKind: ControlKind,
        editable: Boolean
    ): Boolean {
        if (controlKind in setOf(
                ControlKind.BUTTON,
                ControlKind.LINK,
                ControlKind.TEXT,
                ControlKind.HIDDEN_INPUT,
                ControlKind.FORM_CONTAINER,
                ControlKind.SUMMARY_DETAILS
            )
        ) {
            return false
        }
        val tag = tagName.lowercase(Locale.US)
        val type = inputType.lowercase(Locale.US)
        if (tag == "input" && type in setOf("hidden", "submit", "button", "reset", "image")) return false
        return editable ||
            tag in setOf("input", "textarea", "select") ||
            tag.contains("-") ||
            controlKind in setOf(
                ControlKind.STANDARD_INPUT,
                ControlKind.TEXTAREA,
                ControlKind.SELECT,
                ControlKind.CHECKBOX,
                ControlKind.RADIO,
                ControlKind.CONTENTEDITABLE,
                ControlKind.CUSTOM_DROPDOWN,
                ControlKind.DATE_TIME,
                ControlKind.FILE_INPUT
            ) ||
            role.lowercase(Locale.US) in setOf(
                "textbox", "searchbox", "combobox", "listbox", "spinbutton", "slider", "switch", "checkbox", "radio"
            ) ||
            contentEditable.equals("true", ignoreCase = true) ||
            contentEditable.equals("plaintext-only", ignoreCase = true) ||
            contentEditable.equals("inherited", ignoreCase = true)
    }

    fun classifyElement(
        tagName: String,
        inputType: String = "",
        role: String = "",
        autocomplete: String = "",
        name: String = "",
        htmlId: String = "",
        label: String = "",
        accessibleName: String = "",
        placeholder: String = "",
        ariaPlaceholder: String = "",
        fieldsetLegend: String = "",
        nearbyText: String = "",
        contentEditable: String = "",
        controlKind: ControlKind = ControlKind.OTHER_INTERACTIVE,
        sensitiveCategory: SensitiveFieldCategory = SensitiveFieldCategory.NONE,
        dateTimeMeta: DateTimeMetadata? = null,
        relevantAttributes: Map<String, String> = emptyMap()
    ): FieldSemanticType {
        val tag = tagName.lowercase(Locale.US)
        val type = inputType.lowercase(Locale.US)
        val lowerRole = role.lowercase(Locale.US)
        val isField = isFieldControl(
            tagName = tag,
            inputType = type,
            role = lowerRole,
            contentEditable = contentEditable,
            controlKind = controlKind,
            editable = tag in setOf("input", "textarea") || contentEditable.isNotBlank()
        )
        if (!isField && !relevantAttributes.containsKey("data-country-code") && !relevantAttributes.containsKey("data-dial-code")) {
            return FieldSemanticType.NONE
        }

        val dataFieldHint = listOfNotNull(
            relevantAttributes["data-field"],
            relevantAttributes["data-field-name"],
            relevantAttributes["data-field-label"],
            relevantAttributes["data-name"],
            relevantAttributes["data-label"],
            relevantAttributes["data-placeholder"],
            relevantAttributes["data-type"],
            relevantAttributes["title"]
        ).joinToString(" ")

        val primaryText = cleanSignal(
            listOf(accessibleName, label, name, htmlId, placeholder, ariaPlaceholder, dataFieldHint)
                .filter { it.isNotBlank() }
                .joinToString(" ")
        )
        val contextText = cleanSignal(
            listOf(fieldsetLegend, nearbyText)
                .filter { it.isNotBlank() }
                .joinToString(" ")
        )
        val fullSignal = "$primaryText $contextText".trim()

        // 1. Password & Confirmation Password
        if (type == "password" || sensitiveCategory == SensitiveFieldCategory.PASSWORD) {
            return if (Regex("\\b(confirm|re[-_ ]?enter|retype|repeat|verify|again|match)\\b").containsMatchIn(primaryText)) {
                FieldSemanticType.CONFIRM_PASSWORD
            } else {
                FieldSemanticType.PASSWORD
            }
        }

        // 2. Explicit WHATWG autocomplete tokens (right-to-left token priority)
        val acTokens = autocomplete.trim().lowercase(Locale.US)
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() && it != "on" && it != "off" }
        for (token in acTokens.asReversed()) {
            val mapped = AUTOCOMPLETE_EXACT_MAP[token]
            if (mapped != null) {
                if (mapped == FieldSemanticType.PASSWORD &&
                    Regex("\\b(confirm|re[-_ ]?enter|retype|repeat|verify)\\b").containsMatchIn(primaryText)
                ) {
                    return FieldSemanticType.CONFIRM_PASSWORD
                }
                return mapped
            }
        }

        // 3. Explicit sensitive category
        when (sensitiveCategory) {
            SensitiveFieldCategory.CVV -> return FieldSemanticType.CARD_CVV
            SensitiveFieldCategory.CREDIT_CARD -> return FieldSemanticType.CREDIT_CARD
            SensitiveFieldCategory.BANK_ACCOUNT -> return FieldSemanticType.BANK_ACCOUNT
            SensitiveFieldCategory.OTP -> return FieldSemanticType.OTP_CODE
            SensitiveFieldCategory.SECURITY_QUESTION -> return FieldSemanticType.SECURITY_QUESTION
            SensitiveFieldCategory.PERSONAL_ID -> return FieldSemanticType.PERSONAL_ID
            SensitiveFieldCategory.PASSWORD -> return FieldSemanticType.PASSWORD
            SensitiveFieldCategory.NONE -> Unit
        }

        // 4. Native specialized input types & custom date/time controls
        when (type) {
            "email" -> return FieldSemanticType.EMAIL
            "search" -> return FieldSemanticType.SEARCH
            "url" -> return FieldSemanticType.WEBSITE_URL
            "file" -> return FieldSemanticType.FILE_UPLOAD
            "color" -> return FieldSemanticType.COLOR_PICKER
            "range" -> return FieldSemanticType.NUMERIC_RANGE
            "time" -> return FieldSemanticType.TIME
            "date", "datetime-local", "month", "week" -> {
                return if (hasBirthContext(fullSignal)) FieldSemanticType.DATE_OF_BIRTH else FieldSemanticType.DATE
            }
            "tel" -> {
                if (Regex("\\b(country[-_ ]?code|dial[-_ ]?code|calling[-_ ]?code)\\b").containsMatchIn(primaryText)) {
                    return FieldSemanticType.COUNTRY_CODE
                }
                if (Regex("\\b(ext|extension)\\b").containsMatchIn(primaryText)) {
                    return FieldSemanticType.PHONE_EXTENSION
                }
                return FieldSemanticType.PHONE
            }
        }

        if (dateTimeMeta != null) {
            val birth = hasBirthContext(fullSignal)
            when (dateTimeMeta.pickerRole) {
                "time-slot" -> return FieldSemanticType.TIME
                "year-input", "year-select", "year-option", "year-dropdown" ->
                    return if (birth || dateTimeMeta.controlType == "year-option") FieldSemanticType.BIRTH_YEAR else FieldSemanticType.DATE
                "month-input", "month-select", "month-dropdown" ->
                    return if (birth) FieldSemanticType.BIRTH_MONTH else FieldSemanticType.DATE
                "day-input", "day-select", "day-dropdown" ->
                    return if (birth) FieldSemanticType.BIRTH_DAY else FieldSemanticType.DATE
                else -> if (dateTimeMeta.pickerRole !in setOf("month-nav", "year-nav", "today-action", "clear-action")) {
                    return if (birth) FieldSemanticType.DATE_OF_BIRTH else FieldSemanticType.DATE
                }
            }
        }

        // 5. Checkbox / Switch / Radio intent
        if (controlKind == ControlKind.CHECKBOX || type == "checkbox" || lowerRole in setOf("checkbox", "switch")) {
            if (Regex("\\b(terms|privacy|agree|consent|policy|condition|subscribe|newsletter|opt[-_ ]?in|remember|acknowledge|accept)\\b").containsMatchIn(fullSignal)) {
                return FieldSemanticType.CONSENT_TERMS
            }
            if (Regex("\\b(captcha|robot|human)\\b").containsMatchIn(fullSignal)) {
                return FieldSemanticType.CAPTCHA_CHALLENGE
            }
        }
        if (controlKind == ControlKind.RADIO || type == "radio" || lowerRole == "radio") {
            if (Regex("\\b(gender|sex|male|female|non[-_ ]?binary|pronoun)\\b").containsMatchIn(fullSignal)) {
                return FieldSemanticType.GENDER
            }
        }

        // 6. Specific attribute/token semantic rules (ordered from most specific to broader)
        if (relevantAttributes.containsKey("data-country-code") ||
            relevantAttributes.containsKey("data-dial-code") ||
            Regex("\\b(country[-_ ]?code|dial[-_ ]?code|calling[-_ ]?code|phone[-_ ]?code|isd|idd)\\b").containsMatchIn(fullSignal)
        ) {
            return FieldSemanticType.COUNTRY_CODE
        }
        if (Regex("\\b(captcha|recaptcha|turnstile|hcaptcha)\\b").containsMatchIn(primaryText)) {
            return FieldSemanticType.CAPTCHA_CHALLENGE
        }
        if (Regex("\\b(otp|2fa|mfa|totp|one[-_ ]?time|verification[-_ ]?code|auth[-_ ]?code|sms[-_ ]?code)\\b").containsMatchIn(primaryText)) {
            return FieldSemanticType.OTP_CODE
        }
        if (Regex("\\b(pin[-_ ]?code|passcode|atm[-_ ]?pin)\\b|\\bpin\\b").containsMatchIn(primaryText) &&
            !Regex("\\b(pin[-_ ]?code)\\b").containsMatchIn("pincode") // "pincode" is postal in India
        ) {
            if (!Regex("\\bpincode\\b").containsMatchIn(primaryText)) {
                return FieldSemanticType.PIN_CODE
            }
        }
        if (Regex("\\b(e[-_ ]?mail|email[-_ ]?addr|mail[-_ ]?address)\\b").containsMatchIn(primaryText)) {
            return FieldSemanticType.EMAIL
        }
        if (Regex("\\b(promo|coupon|voucher|discount[-_ ]?code|referral[-_ ]?code|invite[-_ ]?code|gift[-_ ]?card|license[-_ ]?key|serial[-_ ]?key)\\b").containsMatchIn(primaryText)) {
            return FieldSemanticType.PROMO_CODE
        }
        if (Regex("\\b(card[-_ ]?holder|name[-_ ]?on[-_ ]?card|cc[-_ ]?name)\\b").containsMatchIn(primaryText)) {
            return FieldSemanticType.CARDHOLDER_NAME
        }
        if (Regex("\\b(expir|exp[-_ ]?date|valid[-_ ]?thru|cc[-_ ]?exp|mm\\s*/\\s*yy)\\b").containsMatchIn(primaryText)) {
            return FieldSemanticType.CARD_EXPIRY
        }
        if (Regex("\\b(cvv|cvc|csc|card[-_ ]?security)\\b").containsMatchIn(primaryText)) {
            return FieldSemanticType.CARD_CVV
        }
        if (Regex("\\b(card[-_ ]?number|credit[-_ ]?card|debit[-_ ]?card)\\b").containsMatchIn(primaryText)) {
            return FieldSemanticType.CREDIT_CARD
        }
        if (Regex("\\b(phone[-_ ]?ext|tel[-_ ]?ext|extension)\\b").containsMatchIn(primaryText)) {
            return FieldSemanticType.PHONE_EXTENSION
        }
        if (Regex("\\b(phone|mobile|telephone|cell[-_ ]?phone|whatsapp|contact[-_ ]?number)\\b").containsMatchIn(primaryText) &&
            !Regex("\\b(email|code)\\b").containsMatchIn(primaryText)
        ) {
            return FieldSemanticType.PHONE
        }
        if (Regex("\\b(zip|zipcode|zip[-_ ]?code|postal|postcode|pincode|eircode)\\b").containsMatchIn(primaryText)) {
            return FieldSemanticType.POSTAL_CODE
        }
        if (Regex("\\b(addr(?:ess)?[-_ ]?(?:line)?[-_ ]?2|apt|apartment|suite|unit|floor|bldg|building)\\b").containsMatchIn(primaryText)) {
            return FieldSemanticType.ADDRESS_LINE_2
        }
        if (Regex("\\b(street|address|addr|address[-_ ]?1|line[-_ ]?1|mailing[-_ ]?addr|shipping[-_ ]?addr|billing[-_ ]?addr)\\b").containsMatchIn(primaryText) &&
            !Regex("\\bemail\\b").containsMatchIn(primaryText)
        ) {
            return FieldSemanticType.STREET_ADDRESS
        }
        if (Regex("\\b(city|town|locality|municipality|suburb)\\b").containsMatchIn(primaryText)) {
            return FieldSemanticType.CITY
        }
        if (Regex("\\b(state|province|region|county|prefecture|emirate|territory)\\b").containsMatchIn(primaryText)) {
            return FieldSemanticType.STATE_PROVINCE
        }
        if (Regex("\\b(country|nation|nationality)\\b").containsMatchIn(primaryText)) {
            return FieldSemanticType.COUNTRY
        }
        if (Regex("\\b(company|organi[sz]ation|employer|business[-_ ]?name|firm|corp(?:oration)?|workspace|institution|university|college|school)\\b").containsMatchIn(primaryText)) {
            return FieldSemanticType.COMPANY
        }
        if (Regex("\\b(job[-_ ]?title|role[-_ ]?title|job[-_ ]?role|work[-_ ]?title|occupation|designation|profession|position|career)\\b").containsMatchIn(primaryText)) {
            return FieldSemanticType.JOB_TITLE
        }
        if (Regex("\\b(department|division|faculty)\\b").containsMatchIn(primaryText)) {
            return FieldSemanticType.DEPARTMENT
        }
        if (Regex("\\b(linkedin|github|twitter|instagram|facebook|youtube|tiktok|mastodon|discord|telegram|slack|social)\\b").containsMatchIn(primaryText)) {
            return FieldSemanticType.SOCIAL_HANDLE
        }
        if (Regex("\\b(website|web[-_ ]?site|homepage|portfolio|url|domain)\\b").containsMatchIn(primaryText)) {
            return FieldSemanticType.WEBSITE_URL
        }
        if (Regex("\\b(first[-_ ]?name|fname|given[-_ ]?name|forename)\\b").containsMatchIn(primaryText)) {
            return FieldSemanticType.FIRST_NAME
        }
        if (Regex("\\b(middle[-_ ]?name|mname|middle[-_ ]?initial)\\b").containsMatchIn(primaryText)) {
            return FieldSemanticType.MIDDLE_NAME
        }
        if (Regex("\\b(last[-_ ]?name|lname|surname|family[-_ ]?name)\\b").containsMatchIn(primaryText)) {
            return FieldSemanticType.LAST_NAME
        }
        if (Regex("\\b(nick[-_ ]?name|alias|screen[-_ ]?name)\\b").containsMatchIn(primaryText)) {
            return FieldSemanticType.NICKNAME
        }
        if (Regex("\\b(user[-_ ]?name|userid|user[-_ ]?id|handle|gamer[-_ ]?tag)\\b").containsMatchIn(primaryText)) {
            return FieldSemanticType.USERNAME
        }
        if (Regex("\\b(salutation|honorific|prefix)\\b").containsMatchIn(primaryText)) {
            return FieldSemanticType.TITLE_PREFIX
        }
        if (Regex("\\b(suffix)\\b").containsMatchIn(primaryText)) {
            return FieldSemanticType.NAME_SUFFIX
        }
        if (Regex("\\b(full[-_ ]?name|your[-_ ]?name|display[-_ ]?name|recipient)\\b|^name$").containsMatchIn(primaryText) ||
            (Regex("\\bname\\b").containsMatchIn(primaryText) && !Regex("\\b(user|file|domain|host|server|project|product|item|card)\\b").containsMatchIn(primaryText))
        ) {
            return FieldSemanticType.FULL_NAME
        }
        if (Regex("\\b(gender|sex|pronoun)\\b").containsMatchIn(fullSignal)) {
            return FieldSemanticType.GENDER
        }
        if (Regex("\\b(language|locale)\\b").containsMatchIn(primaryText)) {
            return FieldSemanticType.LANGUAGE_LOCALE
        }
        if (Regex("\\b(time[-_ ]?zone|tz)\\b").containsMatchIn(primaryText)) {
            return FieldSemanticType.TIMEZONE
        }
        if (Regex("\\b(birth[-_ ]?year|year[-_ ]?of[-_ ]?birth|dob[-_ ]?year)\\b").containsMatchIn(fullSignal)) {
            return FieldSemanticType.BIRTH_YEAR
        }
        if (Regex("\\b(birth[-_ ]?month|month[-_ ]?of[-_ ]?birth|dob[-_ ]?month)\\b").containsMatchIn(fullSignal)) {
            return FieldSemanticType.BIRTH_MONTH
        }
        if (Regex("\\b(birth[-_ ]?day|day[-_ ]?of[-_ ]?birth|dob[-_ ]?day)\\b").containsMatchIn(fullSignal)) {
            return FieldSemanticType.BIRTH_DAY
        }
        if (hasBirthContext(primaryText)) {
            return FieldSemanticType.DATE_OF_BIRTH
        }
        if (Regex("\\b(age)\\b").containsMatchIn(primaryText)) {
            return FieldSemanticType.AGE
        }
        if (Regex("\\b(upload|attach|attachment|resume|cv|avatar)\\b").containsMatchIn(primaryText)) {
            return FieldSemanticType.FILE_UPLOAD
        }
        if (Regex("\\b(subject|headline|topic|issue[-_ ]?title|project[-_ ]?title|task[-_ ]?title)\\b").containsMatchIn(primaryText)) {
            return FieldSemanticType.SUBJECT_TITLE
        }
        if (Regex("\\b(category|industry|priority|tags?)\\b").containsMatchIn(primaryText)) {
            return FieldSemanticType.CATEGORY_TAG
        }
        if (Regex("\\b(qty|quantity|count|guests|adults|children|rooms|seats|tickets)\\b").containsMatchIn(primaryText)) {
            return FieldSemanticType.QUANTITY
        }
        if (Regex("\\b(amount|price|cost|budget|salary|income|total|donation|tip|bid|fee)\\b").containsMatchIn(primaryText)) {
            return FieldSemanticType.AMOUNT_PRICE
        }
        if (Regex("\\b(search|query|find|lookup|filter)\\b").containsMatchIn(primaryText) || lowerRole == "searchbox") {
            return FieldSemanticType.SEARCH
        }
        if (Regex("\\b(message|notes?|comments?|description|bio|about|feedback|inquiry|cover[-_ ]?letter|remarks|summary|reason)\\b").containsMatchIn(primaryText) ||
            tag == "textarea" ||
            controlKind == ControlKind.TEXTAREA ||
            controlKind == ControlKind.CONTENTEDITABLE
        ) {
            return FieldSemanticType.MESSAGE_NOTES
        }
        if (lowerRole == "slider") {
            return FieldSemanticType.NUMERIC_RANGE
        }
        if (Regex("\\b(date)\\b").containsMatchIn(primaryText)) {
            return FieldSemanticType.DATE
        }
        if (Regex("\\b(time)\\b").containsMatchIn(primaryText)) {
            return FieldSemanticType.TIME
        }
        if (type == "number" || relevantAttributes["inputmode"] in setOf("numeric", "decimal") || lowerRole == "spinbutton") {
            return FieldSemanticType.QUANTITY
        }

        return FieldSemanticType.NONE
    }

    fun inferDesiredSemanticsFromInstruction(
        clause: String,
        targetLabel: String,
        quotedValue: String = ""
    ): Set<FieldSemanticType> {
        val combined = cleanSignal("$targetLabel $clause")
        if (combined.isBlank()) return emptySet()

        val matched = LinkedHashSet<FieldSemanticType>()
        if (Regex("\\b(confirm|re[-_ ]?enter|retype|repeat|verify)\\b.*\\bpassword\\b|\\bpassword\\b.*\\b(confirm|again)\\b").containsMatchIn(combined)) {
            matched.add(FieldSemanticType.CONFIRM_PASSWORD)
        } else if (Regex("\\bpassword\\b|\\bpwd\\b|\\bpassphrase\\b").containsMatchIn(combined)) {
            matched.add(FieldSemanticType.PASSWORD)
        }

        if (Regex("\\b(country[-_ ]?code|dial[-_ ]?code|calling[-_ ]?code|phone[-_ ]?code|area[-_ ]?code)\\b").containsMatchIn(combined)) {
            matched.add(FieldSemanticType.COUNTRY_CODE)
        } else if (Regex("\\b(phone[-_ ]?ext|extension|ext)\\b").containsMatchIn(combined)) {
            matched.add(FieldSemanticType.PHONE_EXTENSION)
        } else if (Regex("\\b(phone|mobile|telephone|cell|whatsapp)\\b").containsMatchIn(combined)) {
            matched.add(FieldSemanticType.PHONE)
        }

        if (Regex("\\b(birth[-_ ]?year|year[-_ ]?of[-_ ]?birth|bday[-_ ]?year|dob[-_ ]?year)\\b").containsMatchIn(combined)) {
            matched.add(FieldSemanticType.BIRTH_YEAR)
        } else if (Regex("\\b(birth[-_ ]?month|bday[-_ ]?month|dob[-_ ]?month)\\b").containsMatchIn(combined)) {
            matched.add(FieldSemanticType.BIRTH_MONTH)
        } else if (Regex("\\b(birth[-_ ]?day|bday[-_ ]?day|dob[-_ ]?day)\\b").containsMatchIn(combined)) {
            matched.add(FieldSemanticType.BIRTH_DAY)
        } else if (hasBirthContext(combined)) {
            matched.add(FieldSemanticType.DATE_OF_BIRTH)
        }

        if (Regex("\\b(addr(?:ess)?[-_ ]?(?:line)?[-_ ]?2|apt|apartment|suite|unit|floor)\\b").containsMatchIn(combined)) {
            matched.add(FieldSemanticType.ADDRESS_LINE_2)
        } else if (Regex("\\b(street|address|addr|address[-_ ]?line[-_ ]?1)\\b").containsMatchIn(combined) && !Regex("\\bemail\\b").containsMatchIn(combined)) {
            matched.add(FieldSemanticType.STREET_ADDRESS)
        }

        for (type in FieldSemanticType.entries) {
            if (type == FieldSemanticType.NONE || type.aliases.isEmpty()) continue
            for (alias in type.aliases) {
                val cleanAlias = cleanSignal(alias)
                if (cleanAlias.length >= 2 && Regex("""\b${Regex.escape(cleanAlias)}\b""").containsMatchIn(combined)) {
                    // Prevent "name" from triggering FULL_NAME when user clearly wrote First/Last/Company/User name
                    if (type == FieldSemanticType.FULL_NAME &&
                        Regex("\\b(first|last|middle|user|company|org|nick|card|screen|display)\\b").containsMatchIn(combined)
                    ) {
                        continue
                    }
                    // Prevent "country" from overriding COUNTRY_CODE
                    if (type == FieldSemanticType.COUNTRY && FieldSemanticType.COUNTRY_CODE in matched) {
                        continue
                    }
                    // Prevent "password" matching when CONFIRM_PASSWORD was already matched
                    if (type == FieldSemanticType.PASSWORD && FieldSemanticType.CONFIRM_PASSWORD in matched) {
                        continue
                    }
                    matched.add(type)
                    break
                }
            }
        }

        if (matched.isEmpty() && quotedValue.isNotBlank()) {
            val q = quotedValue.trim()
            when {
                q.contains("@") && q.contains(".") -> matched.add(FieldSemanticType.EMAIL)
                q.startsWith("http://", true) || q.startsWith("https://", true) -> matched.add(FieldSemanticType.WEBSITE_URL)
                Regex("^\\+\\d{1,4}$").matches(q) -> matched.add(FieldSemanticType.COUNTRY_CODE)
                Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(q) -> matched.add(FieldSemanticType.DATE)
                Regex("^(0?[1-9]|1[0-2])/(2[4-9]|3\\d)$").matches(q) -> matched.add(FieldSemanticType.CARD_EXPIRY)
            }
        }
        return matched
    }

    fun areSemanticallyCompatible(desired: FieldSemanticType, actual: FieldSemanticType): Boolean {
        if (desired == FieldSemanticType.NONE || actual == FieldSemanticType.NONE) return false
        if (desired == actual) return true
        return when (desired) {
            FieldSemanticType.DATE_OF_BIRTH -> actual in setOf(
                FieldSemanticType.BIRTH_YEAR,
                FieldSemanticType.BIRTH_MONTH,
                FieldSemanticType.BIRTH_DAY,
                FieldSemanticType.DATE
            )
            FieldSemanticType.DATE -> actual == FieldSemanticType.DATE_OF_BIRTH
            FieldSemanticType.STREET_ADDRESS -> actual == FieldSemanticType.ADDRESS_LINE_2
            FieldSemanticType.PASSWORD -> actual == FieldSemanticType.CONFIRM_PASSWORD
            else -> false
        }
    }

    fun inferSelectorHintsForSemanticType(type: FieldSemanticType): String {
        return when (type) {
            FieldSemanticType.EMAIL -> "input[type=\"email\"], input[autocomplete=\"email\"], input[name*=\"mail\" i]"
            FieldSemanticType.PASSWORD, FieldSemanticType.CONFIRM_PASSWORD -> "input[type=\"password\"], input[autocomplete*=\"password\" i]"
            FieldSemanticType.FIRST_NAME -> "input[autocomplete=\"given-name\"], input[name*=\"first\" i], input[id*=\"first\" i]"
            FieldSemanticType.MIDDLE_NAME -> "input[autocomplete=\"additional-name\"], input[name*=\"middle\" i]"
            FieldSemanticType.LAST_NAME -> "input[autocomplete=\"family-name\"], input[name*=\"last\" i], input[name*=\"surname\" i]"
            FieldSemanticType.FULL_NAME -> "input[autocomplete=\"name\"], input[name*=\"name\" i]"
            FieldSemanticType.USERNAME -> "input[autocomplete=\"username\"], input[name*=\"user\" i]"
            FieldSemanticType.COMPANY -> "input[autocomplete=\"organization\"], input[name*=\"company\" i], input[name*=\"org\" i]"
            FieldSemanticType.JOB_TITLE -> "input[autocomplete=\"organization-title\"], input[name*=\"title\" i], input[name*=\"role\" i]"
            FieldSemanticType.STREET_ADDRESS -> "input[autocomplete=\"street-address\"], input[autocomplete=\"address-line1\"], input[name*=\"address\" i], input[name*=\"street\" i]"
            FieldSemanticType.ADDRESS_LINE_2 -> "input[autocomplete=\"address-line2\"], input[name*=\"line2\" i], input[name*=\"apt\" i], input[name*=\"suite\" i]"
            FieldSemanticType.CITY -> "input[autocomplete=\"address-level2\"], input[name*=\"city\" i]"
            FieldSemanticType.STATE_PROVINCE -> "select[autocomplete=\"address-level1\"], input[autocomplete=\"address-level1\"], select[name*=\"state\" i], input[name*=\"state\" i], input[name*=\"province\" i]"
            FieldSemanticType.POSTAL_CODE -> "input[autocomplete=\"postal-code\"], input[name*=\"zip\" i], input[name*=\"postal\" i]"
            FieldSemanticType.COUNTRY -> "select[autocomplete*=\"country\" i], input[autocomplete*=\"country\" i], select[name*=\"country\" i]"
            FieldSemanticType.COUNTRY_CODE -> "input[autocomplete=\"tel-country-code\"], [data-dial-code], [data-country-code], select[name*=\"code\" i], input[placeholder*=\"country\" i]"
            FieldSemanticType.PHONE -> "input[type=\"tel\"], input[inputmode=\"tel\"], input[autocomplete*=\"tel\" i], input[name*=\"phone\" i]"
            FieldSemanticType.WEBSITE_URL -> "input[type=\"url\"], input[autocomplete=\"url\"], input[name*=\"url\" i], input[name*=\"website\" i]"
            FieldSemanticType.PROMO_CODE -> "input[name*=\"promo\" i], input[name*=\"coupon\" i], input[name*=\"voucher\" i], input[name*=\"discount\" i]"
            FieldSemanticType.OTP_CODE -> "input[autocomplete=\"one-time-code\"], input[name*=\"otp\" i], input[name*=\"code\" i]"
            FieldSemanticType.CARD_EXPIRY -> "input[autocomplete=\"cc-exp\"], input[name*=\"expir\" i], input[name*=\"exp\" i]"
            FieldSemanticType.CARDHOLDER_NAME -> "input[autocomplete=\"cc-name\"], input[name*=\"cardholder\" i]"
            FieldSemanticType.FILE_UPLOAD -> "input[type=\"file\"]"
            FieldSemanticType.MESSAGE_NOTES -> "textarea, [contenteditable=\"true\"], [role=\"textbox\"]"
            FieldSemanticType.SEARCH -> "input[type=\"search\"], [role=\"searchbox\"], input[name*=\"search\" i], input[name*=\"q\" i]"
            FieldSemanticType.COLOR_PICKER -> "input[type=\"color\"]"
            FieldSemanticType.NUMERIC_RANGE -> "input[type=\"range\"], [role=\"slider\"]"
            FieldSemanticType.QUANTITY -> "input[type=\"number\"], input[inputmode=\"numeric\"], [role=\"spinbutton\"]"
            else -> ""
        }
    }

    /**
     * Returns 0..24 bonus for typo-tolerant / stem similarity on multi-character strings.
     */
    fun scoreFuzzySimilarity(rawA: String, rawB: String): Int {
        val a = cleanSignal(rawA).replace(" ", "")
        val b = cleanSignal(rawB).replace(" ", "")
        if (a.length < 4 || b.length < 4) return 0
        if (a == b) return 24
        val maxLen = maxOf(a.length, b.length)
        if (kotlin.math.abs(a.length - b.length) > (maxLen / 3).coerceAtLeast(2)) return 0
        val dist = levenshtein(a, b)
        val threshold = when {
            maxLen <= 5 -> 1
            maxLen <= 9 -> 2
            else -> 3
        }
        return if (dist <= threshold) {
            (22 - dist * 5).coerceAtLeast(8)
        } else {
            0
        }
    }

    fun cleanSignal(raw: String): String {
        return raw
            .replace(Regex("([a-z0-9])([A-Z])"), "$1 $2")
            .lowercase(Locale.US)
            .replace(Regex("[*_]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun hasBirthContext(text: String): Boolean {
        return Regex("\\b(birth|birthday|birthdate|bday|dob|date\\s*of\\s*birth|born|year\\s*of\\s*birth|month\\s*of\\s*birth|day\\s*of\\s*birth)\\b")
            .containsMatchIn(text)
    }

    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        val m = a.length
        val n = b.length
        var prev = IntArray(n + 1) { it }
        var curr = IntArray(n + 1)
        for (i in 1..m) {
            curr[0] = i
            val ca = a[i - 1]
            for (j in 1..n) {
                val cost = if (ca == b[j - 1]) 0 else 1
                curr[j] = minOf(
                    curr[j - 1] + 1,
                    prev[j] + 1,
                    prev[j - 1] + cost
                )
            }
            val tmp = prev
            prev = curr
            curr = tmp
        }
        return prev[n]
    }
}
