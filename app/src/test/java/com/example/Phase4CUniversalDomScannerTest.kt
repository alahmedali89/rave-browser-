package com.example

import android.content.Context
import android.webkit.WebView
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.automation.ActiveRecordingSession
import com.example.automation.AutomationActionType
import com.example.automation.AutomationEngine
import com.example.automation.AutomationHudMode
import com.example.automation.ControlKind
import com.example.automation.DomScanStatus
import com.example.automation.DropdownKind
import com.example.automation.LocatorStability
import com.example.automation.LocatorStrategy
import com.example.automation.RecordedDomEvent
import com.example.automation.ScannerLimits
import com.example.automation.SensitiveFieldCategory
import com.example.automation.UniversalDomScanner
import com.example.ui.browser.AutomationHudSheet
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Phase4CUniversalDomScannerTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var engine: AutomationEngine
    private lateinit var webView: WebView

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        engine = AutomationEngine()
        webView = WebView(context)
    }

    @After
    fun tearDown() {
        UniversalDomScanner.clearAttachedFixture(webView)
    }

    // =========================================================================
    // 23.1 Standard HTML Discovery Tests
    // =========================================================================

    @Test
    fun standardHtmlControls_discoversAllFormInputsLabelsFieldsetsAndStates() = runBlocking {
        val url = "https://portal.example.com/register"
        val html = """
            <html>
            <body>
                <form id="registration-form">
                    <fieldset>
                        <legend>Personal Information</legend>
                        <label for="full-name">Full Name</label>
                        <input id="full-name" name="fullName" type="text" placeholder="Jane Doe" required />

                        <label>
                            Work Email
                            <input id="work-email" name="email" type="email" autocomplete="email" required />
                        </label>

                        <label for="user-phone">Phone Number</label>
                        <input id="user-phone" name="phone" type="tel" />

                        <label for="age-num">Age</label>
                        <input id="age-num" name="age" type="number" min="18" max="120" step="1" />

                        <label for="site-search">Search Directory</label>
                        <input id="site-search" name="q" type="search" />

                        <label for="portfolio-url">Portfolio URL</label>
                        <input id="portfolio-url" name="website" type="url" readonly />

                        <label for="fav-color">Theme Color</label>
                        <input id="fav-color" name="color" type="color" />

                        <label for="volume-range">Notification Volume</label>
                        <input id="volume-range" name="volume" type="range" min="0" max="100" step="5" />

                        <label for="resume-upload">Upload Resume</label>
                        <input id="resume-upload" name="resume" type="file" />

                        <input id="csrf-hidden" name="csrf" type="hidden" value="tok_123" />

                        <label for="bio-text">Short Bio</label>
                        <textarea id="bio-text" name="bio" placeholder="Tell us about yourself"></textarea>
                    </fieldset>

                    <fieldset>
                        <legend>Preferences</legend>
                        <label for="country-select">Country</label>
                        <select id="country-select" name="country" required>
                            <option id="opt-us" value="us" selected>United States</option>
                            <option id="opt-ca" value="ca">Canada</option>
                            <option id="opt-uk" value="uk">United Kingdom</option>
                        </select>

                        <label><input id="plan-free" type="radio" name="tier" value="free" checked /> Free Plan</label>
                        <label><input id="plan-pro" type="radio" name="tier" value="pro" /> Pro Plan</label>

                        <label><input id="terms-check" type="checkbox" name="terms" required /> Accept Terms</label>
                    </fieldset>

                    <button id="submit-btn" type="submit">Create Account</button>
                    <button id="disabled-btn" type="button" disabled>Unavailable Action</button>
                    <a id="help-link" href="https://portal.example.com/help">Need Help?</a>
                    <details id="faq-details">
                        <summary id="faq-summary">Read FAQ</summary>
                    </details>
                </form>
            </body>
            </html>
        """.trimIndent()

        UniversalDomScanner.attachHtmlFixture(webView, url, html)
        val state = engine.scanPageElements(webView, url, tabId = "tab_1")

        assertEquals(DomScanStatus.COMPLETED, state.scanStatus)
        assertTrue(state.elements.isNotEmpty())

        val byId = state.elements.filter { it.htmlId.isNotEmpty() }.associateBy { it.htmlId }

        val fullName = byId["full-name"]
        assertNotNull(fullName)
        assertEquals("Full Name", fullName!!.accessibleName)
        assertEquals(ControlKind.STANDARD_INPUT, fullName.controlKind)
        assertTrue(fullName.required)
        assertFalse(fullName.disabled)
        assertTrue(fullName.editable)
        assertEquals("registration-form", fullName.formGroupId)
        assertEquals("Personal Information", fullName.fieldsetLegend)
        assertEquals("#full-name", fullName.selector)
        assertEquals(LocatorStrategy.STABLE_ID, fullName.locatorStrategy)

        val workEmail = byId["work-email"]
        assertNotNull(workEmail)
        assertTrue(workEmail!!.accessibleName.contains("Work Email"))
        assertEquals("email", workEmail.inputType)
        assertTrue(workEmail.required)

        val portfolioUrl = byId["portfolio-url"]
        assertNotNull(portfolioUrl)
        assertTrue(portfolioUrl!!.readonly)
        assertFalse("Readonly input should have editable=false", portfolioUrl.editable)

        val fileInput = byId["resume-upload"]
        assertNotNull(fileInput)
        assertEquals(ControlKind.FILE_INPUT, fileInput!!.controlKind)

        val hiddenInput = byId["csrf-hidden"]
        assertNotNull(hiddenInput)
        assertEquals(ControlKind.HIDDEN_INPUT, hiddenInput!!.controlKind)
        assertFalse(hiddenInput.visible)

        val countrySelect = byId["country-select"]
        assertNotNull(countrySelect)
        assertEquals(ControlKind.SELECT, countrySelect!!.controlKind)
        assertEquals(DropdownKind.NATIVE_SELECT, countrySelect.dropdownKind)
        assertEquals("Preferences", countrySelect.fieldsetLegend)
        assertEquals(true, byId["opt-us"]?.selected)
        assertEquals(false, byId["opt-ca"]?.selected)

        val radioFree = byId["plan-free"]
        val radioPro = byId["plan-pro"]
        assertNotNull(radioFree)
        assertNotNull(radioPro)
        assertEquals(ControlKind.RADIO, radioFree!!.controlKind)
        assertEquals("tier", radioFree.radioGroupName)
        assertEquals(true, radioFree.checked)
        assertEquals(false, radioPro!!.checked)

        val termsCheck = byId["terms-check"]
        assertNotNull(termsCheck)
        assertEquals(ControlKind.CHECKBOX, termsCheck!!.controlKind)
        assertTrue(termsCheck.required)

        val submitBtn = byId["submit-btn"]
        assertNotNull(submitBtn)
        assertEquals(ControlKind.BUTTON, submitBtn!!.controlKind)
        assertEquals("Create Account", submitBtn.accessibleName)

        val disabledBtn = byId["disabled-btn"]
        assertNotNull(disabledBtn)
        assertTrue(disabledBtn!!.disabled)
        assertFalse(disabledBtn.interactable)

        val helpLink = byId["help-link"]
        assertNotNull(helpLink)
        assertEquals(ControlKind.LINK, helpLink!!.controlKind)

        val faqSummary = byId["faq-summary"]
        assertNotNull(faqSummary)
        assertEquals(ControlKind.SUMMARY_DETAILS, faqSummary!!.controlKind)
    }

    // =========================================================================
    // 23.2 Contenteditable Discovery Tests
    // =========================================================================

    @Test
    fun contentEditableControls_detectsEditableRegionsAndSkipsNonEditableDivs() = runBlocking {
        val url = "https://editor.example.com/compose"
        val html = """
            <html>
            <body>
                <div id="rich-body" contenteditable="true" role="textbox" aria-label="Email Message Body">
                    <p id="nested-p">Nested paragraph inside editor</p>
                </div>
                <div id="subject-line" contenteditable="plaintext-only" aria-label="Subject Line"></div>
                <div id="implicit-ce" contenteditable="" data-testid="quick-note" title="Quick Note"></div>
                <div id="non-editable-box" contenteditable="false">Static help text</div>
            </body>
            </html>
        """.trimIndent()

        UniversalDomScanner.attachHtmlFixture(webView, url, html)
        val state = engine.scanPageElements(webView, url, tabId = "tab_ce")

        val byId = state.elements.filter { it.htmlId.isNotEmpty() }.associateBy { it.htmlId }

        val richBody = byId["rich-body"]
        assertNotNull(richBody)
        assertEquals("true", richBody!!.contentEditable)
        assertEquals(ControlKind.CONTENTEDITABLE, richBody.controlKind)
        assertEquals("Email Message Body", richBody.accessibleName)
        assertTrue(richBody.editable)

        val nestedP = byId["nested-p"]
        assertNotNull(nestedP)
        assertEquals("inherited", nestedP!!.contentEditable)
        assertEquals(ControlKind.CONTENTEDITABLE, nestedP.controlKind)

        val subjectLine = byId["subject-line"]
        assertNotNull(subjectLine)
        assertEquals("plaintext-only", subjectLine!!.contentEditable)
        assertEquals("Subject Line", subjectLine.accessibleName)

        val implicitCe = byId["implicit-ce"]
        assertNotNull(implicitCe)
        assertEquals("true", implicitCe!!.contentEditable)
        assertEquals("Quick Note", implicitCe.accessibleName)

        // contenteditable="false" must not be classified as editable
        assertNull(byId["non-editable-box"])
        assertTrue(state.summary.contentEditableFound >= 3)
    }

    // =========================================================================
    // 23.3 ARIA, Custom Dropdown & Date/Time Control Tests
    // =========================================================================

    @Test
    fun ariaCustomControlsAndDateTime_resolvesRolesStatesLinkedListboxesAndPickers() = runBlocking {
        val url = "https://booking.example.com/flights"
        val html = """
            <html>
            <body>
                <span id="lbl-origin">Departure Airport</span>
                <span id="lbl-city-hint">Select city</span>
                <div id="airport-combo"
                     role="combobox"
                     aria-labelledby="lbl-origin lbl-city-hint"
                     aria-describedby="combo-desc"
                     aria-controls="airport-listbox"
                     aria-expanded="false"
                     aria-haspopup="listbox"
                     aria-autocomplete="list"
                     aria-required="true"
                     tabindex="0">
                </div>
                <span id="combo-desc">Type 3-letter IATA code</span>
                <ul id="airport-listbox" role="listbox" style="display: none;">
                    <li id="opt-sfo" role="option" aria-selected="true">SFO - San Francisco</li>
                    <li id="opt-jfk" role="option" aria-selected="false">JFK - New York</li>
                </ul>

                <div id="dark-mode-switch" role="switch" aria-label="Dark Mode" aria-checked="true" tabindex="0"></div>
                <div id="seat-slider" role="slider" aria-label="Price Filter" aria-valuemin="50" aria-valuemax="1500" aria-valuenow="400" tabindex="0"></div>
                <div id="passengers-spin" role="spinbutton" aria-label="Passengers" aria-valuemin="1" aria-valuemax="9" aria-valuenow="2" tabindex="0"></div>
                <div id="cabin-tab" role="tab" aria-label="Business Class" aria-selected="false" tabindex="0"></div>

                <label for="depart-date">Departure Date</label>
                <input id="depart-date" type="date" name="departDate" min="2026-01-01" max="2026-12-31" step="1" required />

                <div id="custom-calendar" role="grid" aria-label="Return Date Calendar" data-datepicker="true" data-format="YYYY-MM-DD" tabindex="0"></div>
            </body>
            </html>
        """.trimIndent()

        UniversalDomScanner.attachHtmlFixture(webView, url, html)
        val state = engine.scanPageElements(webView, url, tabId = "tab_aria")

        val byId = state.elements.filter { it.htmlId.isNotEmpty() }.associateBy { it.htmlId }

        val combo = byId["airport-combo"]
        assertNotNull(combo)
        assertEquals("Departure Airport Select city", combo!!.accessibleName)
        assertEquals(ControlKind.CUSTOM_DROPDOWN, combo.controlKind)
        assertEquals(DropdownKind.AUTOCOMPLETE, combo.dropdownKind)
        assertEquals("airport-listbox", combo.controlledElementId)
        assertEquals("false", combo.ariaAttributes["aria-expanded"])
        assertTrue(combo.required)

        val switchEl = byId["dark-mode-switch"]
        assertNotNull(switchEl)
        assertEquals("switch", switchEl!!.role)
        assertEquals(true, switchEl.checked)

        val sliderEl = byId["seat-slider"]
        assertNotNull(sliderEl)
        assertEquals(ControlKind.ARIA_CONTROL, sliderEl!!.controlKind)
        assertEquals("50", sliderEl.ariaAttributes["aria-valuemin"])
        assertEquals("1500", sliderEl.ariaAttributes["aria-valuemax"])
        assertEquals("400", sliderEl.ariaAttributes["aria-valuenow"])

        val spinEl = byId["passengers-spin"]
        assertNotNull(spinEl)
        assertEquals("spinbutton", spinEl!!.role)

        val nativeDate = byId["depart-date"]
        assertNotNull(nativeDate)
        assertEquals(ControlKind.DATE_TIME, nativeDate!!.controlKind)
        assertNotNull(nativeDate.dateTimeMetadata)
        assertFalse(nativeDate.dateTimeMetadata!!.isCustomPicker)
        assertEquals("YYYY-MM-DD", nativeDate.dateTimeMetadata!!.inputFormat)
        assertEquals("2026-01-01", nativeDate.dateTimeMetadata!!.min)
        assertEquals("2026-12-31", nativeDate.dateTimeMetadata!!.max)

        val customCal = byId["custom-calendar"]
        assertNotNull(customCal)
        assertNotNull(customCal!!.dateTimeMetadata)
        assertTrue(customCal.dateTimeMetadata!!.isCustomPicker)
        assertEquals("calendar-grid", customCal.dateTimeMetadata!!.pickerRole)
    }

    // =========================================================================
    // 23.4 Shadow DOM Traversal Tests (Open vs Closed)
    // =========================================================================

    @Test
    fun shadowDomTraversal_discoversNestedOpenShadowElementsAndReportsClosedShadowBoundary() = runBlocking {
        val url = "https://components.example.com/checkout"
        val html = """
            <html>
            <body>
                <checkout-shell id="checkout-host">
                    <template shadowrootmode="open">
                        <input id="coupon-input" type="text" aria-label="Promo Code" />
                        <payment-card-panel id="inner-host">
                            <template shadowrootmode="open">
                                <button id="apply-promo-btn" type="button">Apply Promo</button>
                            </template>
                        </payment-card-panel>
                    </template>
                </checkout-shell>

                <secure-enclave-widget id="closed-widget">
                    <template shadowrootmode="closed">
                        <button id="secret-btn">Hidden Inside Closed Shadow</button>
                    </template>
                </secure-enclave-widget>
            </body>
            </html>
        """.trimIndent()

        UniversalDomScanner.attachHtmlFixture(webView, url, html)
        val state = engine.scanPageElements(webView, url, tabId = "tab_shadow")

        assertEquals(1, state.summary.inaccessibleShadowRootsFound)
        assertEquals(2, state.summary.shadowDomElementsFound)

        val byId = state.elements.filter { it.htmlId.isNotEmpty() }.associateBy { it.htmlId }
        assertNull("Closed shadow root children must not be claimed as accessible", byId["secret-btn"])

        val coupon = byId["coupon-input"]
        assertNotNull(coupon)
        assertTrue(coupon!!.isInShadowDom)
        assertEquals(listOf("checkout-shell#checkout-host"), coupon.shadowHostPath)
        assertEquals("checkout-shell#checkout-host >>> #coupon-input", coupon.selector)
        assertEquals(LocatorStrategy.SHADOW_DOM_CHAIN, coupon.locatorStrategy)
        assertTrue(engine.validateSelector(coupon.selector).isValid)

        val nestedBtn = byId["apply-promo-btn"]
        assertNotNull(nestedBtn)
        assertTrue(nestedBtn!!.isInShadowDom)
        assertEquals(
            listOf("checkout-shell#checkout-host", "payment-card-panel#inner-host"),
            nestedBtn.shadowHostPath
        )
        assertEquals(
            "checkout-shell#checkout-host >>> payment-card-panel#inner-host >>> #apply-promo-btn",
            nestedBtn.selector
        )
        assertTrue(engine.validateSelector(nestedBtn.selector).isValid)
    }

    // =========================================================================
    // 23.5 Iframe Traversal Tests (Same-Origin vs Cross-Origin vs Unloaded)
    // =========================================================================

    @Test
    fun iframeTraversal_discoversSameOriginFrameElementsAndReportsCrossOriginAndUnloadedFrames() = runBlocking {
        val url = "https://app.example.com/workspace"
        val html = """
            <html>
            <body>
                <button id="top-save-btn">Save Workspace</button>

                <iframe id="profile-frame" name="profileFrame" src="https://app.example.com/embedded/profile">
                    <form id="profile-form">
                        <label for="display-handle">Display Handle</label>
                        <input id="display-handle" name="handle" type="text" />
                        <button id="frame-submit" type="submit">Update Handle</button>
                    </form>
                </iframe>

                <iframe id="CrossOriginPaymentFrame" name="stripeFrame" src="https://checkout.stripe.com/pay">
                    <input id="cross-origin-card" type="text" />
                </iframe>

                <iframe id="unloaded-frame" name="emptyFrame" data-frame-state="unloaded"></iframe>
            </body>
            </html>
        """.trimIndent()

        UniversalDomScanner.attachHtmlFixture(webView, url, html)
        val state = engine.scanPageElements(webView, url, tabId = "tab_frames")

        assertEquals(2, state.summary.sameOriginIframeElementsFound)
        assertEquals(2, state.inaccessibleFrames.size)

        val crossOriginReport = state.inaccessibleFrames.firstOrNull { it.reason == "cross-origin" }
        assertNotNull(crossOriginReport)
        assertEquals("iframe#CrossOriginPaymentFrame", crossOriginReport!!.framePath)

        val unloadedReport = state.inaccessibleFrames.firstOrNull { it.reason == "unloaded" }
        assertNotNull(unloadedReport)
        assertEquals("iframe#unloaded-frame", unloadedReport!!.framePath)

        val byId = state.elements.filter { it.htmlId.isNotEmpty() }.associateBy { it.htmlId }
        assertNull("Cross-origin iframe elements must never be accessed or returned", byId["cross-origin-card"])

        val handleInput = byId["display-handle"]
        assertNotNull(handleInput)
        assertTrue(handleInput!!.isInSameOriginIframe)
        assertEquals(listOf("iframe#profile-frame"), handleInput.framePath)
        assertEquals("iframe#profile-frame >> #display-handle", handleInput.selector)
        assertEquals(LocatorStrategy.IFRAME_CHAIN, handleInput.locatorStrategy)
        assertEquals("https://app.example.com:443", handleInput.frameOrigin)
        assertTrue(engine.validateSelector(handleInput.selector).isValid)
    }

    // =========================================================================
    // 23.6 Dynamic DOM & MutationObserver Tests
    // =========================================================================

    @Test
    fun dynamicDomMutations_discoversNewlyInsertedModalElementsMarksRemovedStaleAndRejectsMismatchedSession() = runBlocking {
        val url = "https://dashboard.example.com/projects"
        val html = """
            <html>
            <body>
                <button id="open-modal-btn">New Project</button>
                <button id="temp-action-btn">Temporary Action</button>
            </body>
            </html>
        """.trimIndent()

        UniversalDomScanner.attachHtmlFixture(webView, url, html)
        val initialState = engine.scanPageElements(webView, url, tabId = "tab_dyn")
        assertEquals(2, initialState.elements.size)

        val session = engine.universalScanner.getActiveSession("tab_dyn")
        assertNotNull(session)

        // 1. Valid dynamic mutation batch: #temp-action-btn removed, modal input #project-title added
        val validBatch = JSONObject().apply {
            put("sessionId", session!!.scanSessionId)
            put("tabId", "tab_dyn")
            put("origin", "https://dashboard.example.com:443")
            put("removedSelectors", JSONArray().put("#temp-action-btn"))
            put(
                "updatedElements",
                JSONArray()
                    .put(
                        JSONObject().apply {
                            put("tag", "input")
                            put("inputType", "text")
                            put("htmlId", "project-title")
                            put("name", "projectTitle")
                            put("accessibleName", "Project Title")
                            put("label", "Project Title")
                            put("selector", "#project-title")
                            put("locatorStrategy", "STABLE_ID")
                            put("controlKind", "STANDARD_INPUT")
                            put("required", true)
                            put("visible", true)
                            put("disabled", false)
                            put("editable", true)
                        }
                    )
            )
        }.toString()

        val updatedState = engine.universalScanner.processDynamicMutationBatch(
            rawJson = validBatch,
            sourceTabId = "tab_dyn",
            sourceWebView = webView,
            liveWebViewUrl = url
        )
        assertNotNull(updatedState)
        assertEquals(1, updatedState!!.summary.dynamicUpdatesProcessed)

        val byId = updatedState.elements.filter { it.htmlId.isNotEmpty() }.associateBy { it.htmlId }
        val modalInput = byId["project-title"]
        assertNotNull("Newly inserted modal input must be discovered", modalInput)
        assertEquals("Project Title", modalInput!!.accessibleName)

        val removedBtn = byId["temp-action-btn"]
        assertNotNull(removedBtn)
        assertTrue("Removed element must be marked stale=true", removedBtn!!.stale)

        // 2. Reject mutation batch from wrong sessionId or wrong origin
        val wrongOriginBatch = JSONObject(validBatch).apply {
            put("origin", "https://evil.example.org:443")
        }.toString()
        val rejectedOrigin = engine.universalScanner.processDynamicMutationBatch(
            rawJson = wrongOriginBatch,
            sourceTabId = "tab_dyn",
            sourceWebView = webView,
            liveWebViewUrl = url
        )
        assertNull("Cross-origin mutation batch must be rejected", rejectedOrigin)

        val wrongSessionBatch = JSONObject(validBatch).apply {
            put("sessionId", "scan_wrong_session_id")
        }.toString()
        val rejectedSession = engine.universalScanner.processDynamicMutationBatch(
            rawJson = wrongSessionBatch,
            sourceTabId = "tab_dyn",
            sourceWebView = webView,
            liveWebViewUrl = url
        )
        assertNull("Wrong session mutation batch must be rejected", rejectedSession)
    }

    // =========================================================================
    // 23.7 Long-Page & Extended Scan Scroll Restoration Tests
    // =========================================================================

    @Test
    fun longPageAndExtendedScan_discoversOffScreenWithoutScrollingAndRestoresScrollAfterExtendedScan() = runBlocking {
        val url = "https://catalog.example.com/long-form"
        val html = """
            <html>
            <body>
                <input id="top-name" type="text" aria-label="Customer Name" />
                <button id="footer-submit" type="submit" data-offscreen="true">Submit Footer Order</button>
            </body>
            </html>
        """.trimIndent()

        val lazyStep1 = """
            <div>
                <input id="lazy-promo-field" type="text" aria-label="Lazy Loaded Promo Field" />
            </div>
        """.trimIndent()
        val lazyStep2 = """
            <div>
                <button id="lazy-final-confirm" type="button">Confirm Final Step</button>
            </div>
        """.trimIndent()

        UniversalDomScanner.attachHtmlFixture(
            webView = webView,
            url = url,
            html = html,
            lazyScrollHtmlSnippets = listOf(lazyStep1, lazyStep2)
        )
        UniversalDomScanner.getAttachedFixture(webView)?.apply {
            scrollX = 0
            scrollY = 240
        }

        // 1. Passive scan discovers off-screen element (#footer-submit) without scrolling
        val passiveState = engine.scanPageElements(webView, url, tabId = "tab_long")
        assertEquals(2, passiveState.elements.size)
        val footerBtn = passiveState.elements.first { it.htmlId == "footer-submit" }
        assertFalse("Element with data-offscreen=true must have inViewport=false", footerBtn.inViewport)
        assertTrue(footerBtn.visible)
        assertEquals(240, UniversalDomScanner.getAttachedFixture(webView)?.scrollY)

        // 2. Extended scan scrolls in bounded increments, discovers lazy elements, and restores scrollY=240
        val extendedState = engine.universalScanner.runExtendedScan(
            webView = webView,
            tabId = "tab_long",
            currentUrl = url
        )
        assertEquals(240, UniversalDomScanner.getAttachedFixture(webView)?.scrollY)
        assertEquals(4, extendedState.elements.size)
        assertTrue(extendedState.elements.any { it.htmlId == "lazy-promo-field" })
        assertTrue(extendedState.elements.any { it.htmlId == "lazy-final-confirm" })
    }

    @Test
    fun boundedScanLimits_marksPartialStatusWhenMaxElementsReached() = runBlocking {
        val url = "https://catalog.example.com/massive"
        val inputsHtml = (1..15).joinToString("\n") { i ->
            """<input id="field-$i" type="text" aria-label="Field $i" />"""
        }
        val html = "<html><body>$inputsHtml</body></html>"

        UniversalDomScanner.attachHtmlFixture(webView, url, html)
        val state = engine.universalScanner.scanPage(
            webView = webView,
            tabId = "tab_bounded",
            currentUrl = url,
            customLimits = ScannerLimits(maxElements = 5)
        )

        assertEquals(5, state.elements.size)
        assertEquals(DomScanStatus.PARTIAL, state.scanStatus)
        assertNotNull("Partial scan must include a clear partialReason", state.summary.partialReason)
    }

    // =========================================================================
    // 23.8 Locator Generation, Escaping & Ambiguity Tests
    // =========================================================================

    @Test
    fun locatorGeneration_escapesSpecialIdsPrioritizesStrategiesAndFlagsAmbiguousButtons() = runBlocking {
        val url = "https://billing.example.com/settings"
        val html = """
            <html>
            <body>
                <input id="user:email.input[0]" type="email" aria-label="Primary Billing Email" />
                <input data-testid="billing-zip" type="text" aria-label="ZIP Code" />
                <button class="row-delete">Remove Item</button>
                <button class="row-delete">Remove Item</button>
            </body>
            </html>
        """.trimIndent()

        UniversalDomScanner.attachHtmlFixture(webView, url, html)
        val state = engine.scanPageElements(webView, url, tabId = "tab_loc")

        val emailEl = state.elements.first { it.htmlId == "user:email.input[0]" }
        assertEquals(LocatorStrategy.STABLE_ID, emailEl.locatorStrategy)
        assertEquals(LocatorStability.HIGH, emailEl.locatorCandidates.first().stability)
        assertTrue(
            "Escaped CSS ID selector must pass validateSelector",
            engine.validateSelector(emailEl.selector).isValid
        )

        val zipEl = state.elements.first { it.accessibleName == "ZIP Code" }
        assertEquals(LocatorStrategy.STABLE_DATA_ATTR, zipEl.locatorStrategy)
        assertEquals("input[data-testid=\"billing-zip\"]", zipEl.selector)

        val removeButtons = state.elements.filter { it.accessibleName == "Remove Item" }
        assertEquals(2, removeButtons.size)
        assertTrue("Duplicate buttons with identical text and no unique IDs must be marked ambiguous", removeButtons.all { it.isLocatorAmbiguous })
    }

    // =========================================================================
    // 23.9 Security, Sensitive Field Exclusion & Session Isolation Tests
    // =========================================================================

    @Test
    fun sensitiveFieldExclusion_classifiesSensitiveCategoriesAndNeverCollectsValues() = runBlocking {
        val url = "https://bank.example.com/checkout"
        val html = """
            <html>
            <body>
                <input id="login-pwd" type="password" name="password" value="SuperSecretPassword123!" aria-label="Account Password" />
                <input id="cc-num" type="text" name="cardNumber" autocomplete="cc-number" value="4111222233334444" aria-label="Card Number" />
                <input id="cc-cvv" type="text" name="cvc" autocomplete="cc-csc" value="987" aria-label="Security Code CVV" />
                <input id="otp-code" type="text" name="otp" autocomplete="one-time-code" value="654321" aria-label="One-Time Verification Code" />
                <input id="routing-num" type="text" name="routing_number" value="021000021" aria-label="Bank Routing Number" />
                <input id="ssn-field" type="text" name="ssn" value="123-45-6789" aria-label="Social Security Number" />
            </body>
            </html>
        """.trimIndent()

        UniversalDomScanner.attachHtmlFixture(webView, url, html)
        val state = engine.scanPageElements(webView, url, tabId = "tab_sec")

        val serializedInspection = state.toString()
        assertFalse(serializedInspection.contains("SuperSecretPassword123!"))
        assertFalse(serializedInspection.contains("4111222233334444"))
        assertFalse(serializedInspection.contains("987"))
        assertFalse(serializedInspection.contains("654321"))
        assertFalse(serializedInspection.contains("021000021"))
        assertFalse(serializedInspection.contains("123-45-6789"))

        val byId = state.elements.filter { it.htmlId.isNotEmpty() }.associateBy { it.htmlId }
        assertEquals(SensitiveFieldCategory.PASSWORD, byId["login-pwd"]?.sensitiveCategory)
        assertEquals(SensitiveFieldCategory.CREDIT_CARD, byId["cc-num"]?.sensitiveCategory)
        assertEquals(SensitiveFieldCategory.CVV, byId["cc-cvv"]?.sensitiveCategory)
        assertEquals(SensitiveFieldCategory.OTP, byId["otp-code"]?.sensitiveCategory)
        assertEquals(SensitiveFieldCategory.BANK_ACCOUNT, byId["routing-num"]?.sensitiveCategory)
        assertEquals(SensitiveFieldCategory.PERSONAL_ID, byId["ssn-field"]?.sensitiveCategory)
        assertTrue(state.elements.all { it.sensitiveCategory.isSensitive && it.safeValue.isEmpty() })
    }

    @Test
    fun sessionTabAndOriginIsolation_invalidatesOnNavigationWebViewReplacementAndTabClose() = runBlocking {
        val urlA = "https://alpha.example.com/form"
        val html = """<html><body><button id="alpha-btn">Alpha Submit</button></body></html>"""
        UniversalDomScanner.attachHtmlFixture(webView, urlA, html)

        val stateA = engine.scanPageElements(webView, urlA, tabId = "tab_iso")
        assertEquals(DomScanStatus.COMPLETED, stateA.scanStatus)
        assertNotNull(engine.universalScanner.getActiveSession("tab_iso"))

        // 1. Navigation to a different URL invalidates the scan session
        engine.universalScanner.onTabNavigated("tab_iso", "https://beta.example.org/login")
        val navInvalidated = engine.universalScanner.getLatestInspection("tab_iso")
        assertNotNull(navInvalidated)
        assertEquals(DomScanStatus.DOCUMENT_CHANGED, navInvalidated!!.scanStatus)
        assertTrue(navInvalidated.elements.all { it.stale })
        assertNull(engine.universalScanner.getActiveSession("tab_iso"))

        // 2. Rescan, then replace WebView instance
        engine.scanPageElements(webView, urlA, tabId = "tab_iso")
        val newWebView = WebView(context)
        engine.universalScanner.onWebViewReplaced("tab_iso", webView, newWebView)
        val wvReplaced = engine.universalScanner.getLatestInspection("tab_iso")
        assertNotNull(wvReplaced)
        assertEquals(DomScanStatus.WEBVIEW_REPLACED, wvReplaced!!.scanStatus)

        // 3. Rescan, then close tab
        engine.scanPageElements(webView, urlA, tabId = "tab_iso")
        engine.universalScanner.invalidateTabSession(
            tabId = "tab_iso",
            status = DomScanStatus.SESSION_INVALIDATED,
            reason = "Tab closed."
        )
        val closedState = engine.universalScanner.getLatestInspection("tab_iso")
        assertNotNull(closedState)
        assertEquals(DomScanStatus.SESSION_INVALIDATED, closedState!!.scanStatus)
    }

    // =========================================================================
    // 23.10 Recorder, Manual Instructions & HUD Integration Tests
    // =========================================================================

    @Test
    fun recorderAndManualInstructionsIntegration_bindsDiscoveredLocatorsAndProtectsPasswords() = runBlocking {
        val url = "https://shop.example.com/signin"
        val html = """
            <html>
            <body>
                <label for="signin-email">Email Address</label>
                <input id="signin-email" name="userEmail" type="email" data-testid="auth-email" />

                <label for="signin-pwd">Account Password</label>
                <input id="signin-pwd" name="userPassword" type="password" />

                <shadow-login-panel id="login-panel">
                    <template shadowrootmode="open">
                        <button id="shadow-signin-btn" type="button">Sign In Now</button>
                    </template>
                </shadow-login-panel>
            </body>
            </html>
        """.trimIndent()

        UniversalDomScanner.attachHtmlFixture(webView, url, html)
        val inspection = engine.scanPageElements(webView, url, tabId = "tab_int")

        // 1. Recorder event matching enriches recorded event with discovered element accessibleName & selector
        val recEvent = RecordedDomEvent(
            sessionId = "sess_1",
            tabId = "tab_int",
            origin = "https://shop.example.com:443",
            sequenceNumber = 1L,
            eventType = "INPUT",
            targetLabel = "userEmail",
            selector = "input[name=\"userEmail\"]",
            value = "alice@example.com",
            isDestructive = false
        )
        val matched = engine.universalScanner.matchRecordedEventToElement(recEvent, inspection)
        assertNotNull(matched)
        assertEquals("Email Address", matched!!.accessibleName)
        assertEquals("#signin-email", matched.selector)

        // 2. Manual instructions parser binds discovered selectors (including Shadow DOM >>> chain) and flags password step
        val rawInstructions = """
            Enter "alice@example.com" in Email Address
            Enter "ShouldBeRedacted123" in Account Password
            Click Sign In Now
        """.trimIndent()

        val steps = engine.parseInstructionsToSteps(
            rawInstructions = rawInstructions,
            currentUrl = url,
            inspectionState = inspection
        )
        assertEquals(3, steps.size)

        val emailStep = steps[0]
        assertEquals(AutomationActionType.INPUT, emailStep.actionType)
        assertEquals("Email Address", emailStep.targetLabel)
        assertEquals("#signin-email", emailStep.selector)
        assertEquals("alice@example.com", emailStep.value)
        assertFalse(emailStep.requiresCredential)

        val pwdStep = steps[1]
        assertEquals("Account Password", pwdStep.targetLabel)
        assertEquals("#signin-pwd", pwdStep.selector)
        assertEquals("Password value must never be stored in manual instruction step", "", pwdStep.value)
        assertTrue(pwdStep.requiresCredential)
        assertEquals("https://shop.example.com:443", pwdStep.expectedOrigin)

        val clickShadowStep = steps[2]
        assertEquals(AutomationActionType.CLICK, clickShadowStep.actionType)
        assertEquals("Sign In Now", clickShadowStep.targetLabel)
        assertEquals("shadow-login-panel#login-panel >>> #shadow-signin-btn", clickShadowStep.selector)
    }

    @Test
    fun automationHudSheet_displaysScanStatusSummaryCountsAndSafeElementMetadata() {
        val url = "https://portal.example.com/login"
        val html = """
            <html>
            <body>
                <label for="user-email">Work Email</label>
                <input id="user-email" type="email" required />
                <label for="user-pass">Master Password</label>
                <input id="user-pass" type="password" value="DoNotShowThisSecret!" />
                <button id="login-submit" type="submit">Sign In</button>
            </body>
            </html>
        """.trimIndent()

        UniversalDomScanner.attachHtmlFixture(webView, url, html)
        val inspection = runBlocking { engine.scanPageElements(webView, url, tabId = "tab_ui") }

        var extendedScanClicked = false
        composeRule.setContent {
            MaterialTheme {
                AutomationHudSheet(
                    hudMode = AutomationHudMode.ELEMENTS,
                    pageInspection = inspection,
                    recordingSession = ActiveRecordingSession(),
                    instructionDraftText = "",
                    instructionDraftSteps = emptyList(),
                    executionState = null,
                    workflows = emptyList(),
                    executions = emptyList(),
                    onSelectMode = {},
                    onScanPage = {},
                    onExtendedScan = { extendedScanClicked = true },
                    onCancelScan = {},
                    onStartRecording = {},
                    onPauseRecording = {},
                    onResumeRecording = {},
                    onStopRecording = {},
                    onMinimizeRecording = {},
                    onRenameRecordingDraft = {},
                    onEditRecordingStep = { _, _, _ -> },
                    onDeleteRecordingStep = {},
                    onSaveRecording = {},
                    onDiscardRecording = {},
                    onInstructionTextChange = {},
                    onAddInstructions = {},
                    onClearInstructions = {},
                    onReviewInstructionSteps = {},
                    onEditInstructionStep = { _, _, _, _ -> },
                    onDeleteInstructionStep = {},
                    onMoveInstructionStepUp = {},
                    onMoveInstructionStepDown = {},
                    onSaveInstructionsAsWorkflow = {},
                    onExecuteInstructions = {},
                    onRunWorkflow = {},
                    onOpenWorkflowDetails = {},
                    onOpenWorkspace = {},
                    onOpenNanoRetrieverPanel = {},
                    onConfirmDestructiveExecution = {},
                    onCancelDestructiveExecution = {},
                    onClearExecutionBanner = {},
                    onDismiss = {}
                )
            }
        }

        composeRule.onNodeWithTag("elements_scan_status_subtitle").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Status: COMPLETED", substring = true).assertIsDisplayed()
        composeRule.onNodeWithTag("elements_extended_scan_button").performScrollTo().performClick()
        assertTrue(extendedScanClicked)
    }

    // =========================================================================
    // 23.11 Date/Calendar, Gender, Bottom Sheet & Phone+CountryCode Recognition
    // =========================================================================

    @Test
    fun dateSelectionAndCalendarInteraction_discoversCustomPickersSplitFieldsAndCalendarGridCells() = runBlocking {
        val url = "https://booking.example.com/profile"
        val html = """
            <html>
            <body>
                <label for="dob-trigger">Date of Birth</label>
                <input id="dob-trigger" type="text" readonly placeholder="YYYY-MM-DD" aria-haspopup="dialog" data-datepicker />

                <fieldset>
                    <legend>Anniversary Split</legend>
                    <select id="bday-month" name="bdayMonth" autocomplete="bday-month">
                        <option value="01">January</option>
                    </select>
                    <input id="bday-day" name="bdayDay" type="number" autocomplete="bday-day" placeholder="DD" />
                    <input id="bday-year" name="bdayYear" type="number" autocomplete="bday-year" placeholder="YYYY" />
                </fieldset>

                <div role="dialog" aria-modal="true" aria-label="Choose date" data-datepicker data-state="open">
                    <button id="prev-month-btn" type="button" aria-label="Previous month">&lt;</button>
                    <button id="next-month-btn" type="button" title="Next month">&gt;</button>
                    <div role="grid" aria-label="Calendar May 2025">
                        <div role="row">
                            <button type="button" role="gridcell" data-date="2025-05-14" aria-label="May 14, 2025">14</button>
                            <div role="gridcell" data-date="2025-05-15" aria-current="date" aria-selected="true">15</div>
                            <span class="calendar-day" data-day="16">16</span>
                        </div>
                    </div>
                </div>
            </body>
            </html>
        """.trimIndent()

        UniversalDomScanner.attachHtmlFixture(webView, url, html)
        val inspection = engine.scanPageElements(webView, url, tabId = "tab_cal")
        assertEquals(DomScanStatus.COMPLETED, inspection.scanStatus)

        val trigger = inspection.elements.firstOrNull { it.htmlId == "dob-trigger" }
        assertNotNull("Custom readonly SPA date-picker trigger should be discovered", trigger)
        assertEquals(ControlKind.DATE_TIME, trigger!!.controlKind)
        assertNotNull(trigger.dateTimeMetadata)
        assertTrue(trigger.dateTimeMetadata!!.isCustomPicker)

        val bdayMonth = inspection.elements.firstOrNull { it.htmlId == "bday-month" }
        val bdayDay = inspection.elements.firstOrNull { it.htmlId == "bday-day" }
        val bdayYear = inspection.elements.firstOrNull { it.htmlId == "bday-year" }
        assertNotNull(bdayMonth)
        assertNotNull(bdayDay)
        assertNotNull(bdayYear)
        assertEquals("Anniversary Split", bdayDay!!.fieldsetLegend)
        assertEquals("date-part", bdayDay.dateTimeMetadata?.controlType)
        assertEquals("day", bdayDay.dateTimeMetadata?.inputFormat)
        assertEquals("day-input", bdayDay.dateTimeMetadata?.pickerRole)
        assertEquals("date-part", bdayYear!!.dateTimeMetadata?.controlType)
        assertEquals("year", bdayYear.dateTimeMetadata?.inputFormat)

        val prevBtn = inspection.elements.firstOrNull { it.htmlId == "prev-month-btn" }
        val nextBtn = inspection.elements.firstOrNull { it.htmlId == "next-month-btn" }
        assertNotNull(prevBtn)
        assertNotNull(nextBtn)
        assertEquals("Previous month", prevBtn!!.accessibleName)
        assertEquals("Next month", nextBtn!!.accessibleName)
        assertEquals(ControlKind.BUTTON, nextBtn.controlKind)
        assertEquals("month-nav", nextBtn.dateTimeMetadata?.pickerRole)

        val cell14 = inspection.elements.firstOrNull { it.relevantAttributes["data-date"] == "2025-05-14" }
        val cell15 = inspection.elements.firstOrNull { it.relevantAttributes["data-date"] == "2025-05-15" }
        val cell16 = inspection.elements.firstOrNull { it.relevantAttributes["data-day"] == "16" }
        assertNotNull("Calendar button gridcell should be discovered", cell14)
        assertNotNull("Calendar div gridcell should be discovered", cell15)
        assertNotNull("Leaf .calendar-day element should be discovered", cell16)
        assertEquals(ControlKind.DATE_TIME, cell14!!.controlKind)
        assertEquals(ControlKind.DATE_TIME, cell15!!.controlKind)
        assertEquals(ControlKind.DATE_TIME, cell16!!.controlKind)
        assertEquals("calendar-day", cell15.dateTimeMetadata?.pickerRole)
        assertEquals(true, cell15.selected)
        assertTrue(cell15.locatorCandidates.any { it.selector.contains("data-date=\"2025-05-15\"") })

        val steps = engine.parseInstructionsToSteps(
            rawInstructions = "Click May 14, 2025\nClick Next month",
            currentUrl = url,
            inspectionState = inspection
        )
        assertEquals(2, steps.size)
        assertTrue(steps[0].selector.contains("data-date=\"2025-05-14\"") || steps[0].selector.contains("May 14, 2025"))
        assertEquals("#next-month-btn", steps[1].selector)
    }

    @Test
    fun genderSelection_discoversSelectAriaRadiogroupHiddenInputLabelsAndSegmentedPills() = runBlocking {
        val url = "https://accounts.example.com/signup"
        val html = """
            <html>
            <body>
                <label for="gender-select">Biological Sex</label>
                <select id="gender-select" name="sex" autocomplete="sex">
                    <option value="">Select</option>
                    <option value="female">Female</option>
                    <option value="male">Male</option>
                </select>

                <div role="radiogroup" aria-label="Gender Identity">
                    <div role="radio" tabindex="0" aria-checked="true" data-gender="non-binary">Non-binary</div>
                    <div role="radio" tabindex="0" aria-checked="false" data-gender="prefer-not">Prefer not to say</div>
                </div>

                <fieldset>
                    <legend>Profile Gender</legend>
                    <input id="g-female" type="radio" name="profileGender" value="female" style="opacity: 0" />
                    <label for="g-female" class="gender-pill">Female</label>
                    <label class="gender-pill">
                        <input id="g-male" type="radio" name="profileGender" value="male" class="sr-only" checked />
                        Male
                    </label>
                </fieldset>

                <button id="gender-picker-btn" type="button" aria-haspopup="listbox" aria-expanded="false">Choose Gender</button>
                <button id="pill-other" type="button" aria-pressed="true" data-value="other">Other</button>
            </body>
            </html>
        """.trimIndent()

        UniversalDomScanner.attachHtmlFixture(webView, url, html)
        val inspection = engine.scanPageElements(webView, url, tabId = "tab_gender")
        assertEquals(DomScanStatus.COMPLETED, inspection.scanStatus)

        val selectEl = inspection.elements.firstOrNull { it.htmlId == "gender-select" }
        assertNotNull(selectEl)
        assertEquals(ControlKind.SELECT, selectEl!!.controlKind)

        val ariaNonBinary = inspection.elements.firstOrNull { it.relevantAttributes["data-gender"] == "non-binary" }
        assertNotNull(ariaNonBinary)
        assertEquals(ControlKind.RADIO, ariaNonBinary!!.controlKind)
        assertEquals(true, ariaNonBinary.checked)
        assertEquals("Gender Identity", ariaNonBinary.fieldsetLegend)
        assertEquals("Gender Identity", ariaNonBinary.radioGroupName)

        // Hidden radio inputs remain visible = false while promoted visible labels become RADIO controls
        val rawHiddenFemale = inspection.elements.firstOrNull { it.tagName == "input" && it.htmlId == "g-female" }
        assertTrue(rawHiddenFemale == null || !rawHiddenFemale.visible)

        val labelFemale = inspection.elements.firstOrNull { it.tagName == "label" && it.accessibleName == "Female" }
        val labelMale = inspection.elements.firstOrNull { it.tagName == "label" && it.accessibleName == "Male" }
        assertNotNull("Label for opacity:0 radio input should be promoted", labelFemale)
        assertNotNull("Wrapping label for sr-only radio input should be promoted", labelMale)
        assertEquals(ControlKind.RADIO, labelFemale!!.controlKind)
        assertEquals(ControlKind.RADIO, labelMale!!.controlKind)
        assertEquals("profileGender", labelFemale.radioGroupName)
        assertEquals("profileGender", labelMale.radioGroupName)
        assertEquals(true, labelMale.checked)
        assertEquals("Profile Gender", labelFemale.fieldsetLegend)
        assertTrue(labelFemale.locatorCandidates.any { it.selector.contains("label[for=\"g-female\"]") })

        val customTrigger = inspection.elements.firstOrNull { it.htmlId == "gender-picker-btn" }
        assertNotNull(customTrigger)
        assertEquals(ControlKind.CUSTOM_DROPDOWN, customTrigger!!.controlKind)
        assertEquals(DropdownKind.CUSTOM_COMBOBOX, customTrigger.dropdownKind)

        val pillOther = inspection.elements.firstOrNull { it.htmlId == "pill-other" }
        assertNotNull(pillOther)
        assertEquals(true, pillOther!!.checked)

        val steps = engine.parseInstructionsToSteps(
            rawInstructions = """
                Select "female" in Biological Sex
                Click Female
            """.trimIndent(),
            currentUrl = url,
            inspectionState = inspection
        )
        assertEquals(2, steps.size)
        assertEquals("#gender-select", steps[0].selector)
        assertEquals("Biological Sex", steps[0].targetLabel)
        assertTrue(steps[1].selector.contains("label[for=\"g-female\"]"))
    }

    @Test
    fun bottomSheetAndPhoneWithCountryCode_hidesClosedModalsAndDiscoversOpenCountryRowsAndTelInput() = runBlocking {
        val url = "https://accounts.example.com/phone"
        val html = """
            <html>
            <body>
                <form id="phone-form">
                    <button id="accordion-trigger" type="button" data-state="closed" aria-expanded="false">SMS Help</button>

                    <button id="country-code-trigger" type="button" aria-haspopup="dialog" data-dial-code="+1" aria-label="Country code +1 United States">+1</button>
                    <label for="phone-input">Phone Number</label>
                    <input id="phone-input" name="phoneNumber" type="tel" inputmode="tel" autocomplete="tel-national" placeholder="(555) 123-4567" />
                </form>

                <dialog id="closed-modal">
                    <button id="hidden-modal-btn" type="button">Should Stay Hidden</button>
                </dialog>

                <div data-bottom-sheet data-state="closed" aria-label="Closed sheet">
                    <button id="closed-sheet-btn" type="button">Closed Sheet Action</button>
                </div>

                <div id="country-sheet" role="dialog" aria-modal="true" aria-label="Select country code" data-bottom-sheet data-state="open">
                    <input id="country-search" type="search" placeholder="Search country or code" />
                    <ul role="listbox" aria-label="Countries">
                        <li role="option" data-country-code="US" data-dial-code="+1" aria-selected="true">United States (+1)</li>
                        <li role="option" data-country-code="GB" data-dial-code="+44">United Kingdom (+44)</li>
                        <li data-country-code="IN" data-dial-code="+91">India (+91)</li>
                    </ul>
                </div>
            </body>
            </html>
        """.trimIndent()

        UniversalDomScanner.attachHtmlFixture(webView, url, html)
        val inspection = engine.scanPageElements(webView, url, tabId = "tab_phone")
        assertEquals(DomScanStatus.COMPLETED, inspection.scanStatus)

        // Visible accordion trigger with data-state="closed" MUST NOT be hidden
        val accordionBtn = inspection.elements.firstOrNull { it.htmlId == "accordion-trigger" }
        assertNotNull("Accordion trigger with data-state=closed must remain visible", accordionBtn)
        assertTrue(accordionBtn!!.visible)

        // Closed <dialog> and closed [data-bottom-sheet] children MUST NOT be visible
        val closedDialogChild = inspection.elements.firstOrNull { it.htmlId == "hidden-modal-btn" }
        val closedSheetChild = inspection.elements.firstOrNull { it.htmlId == "closed-sheet-btn" }
        assertTrue(closedDialogChild == null || !closedDialogChild.visible)
        assertTrue(closedSheetChild == null || !closedSheetChild.visible)

        // Phone + country code trigger
        val ccTrigger = inspection.elements.firstOrNull { it.htmlId == "country-code-trigger" }
        assertNotNull(ccTrigger)
        assertEquals(ControlKind.CUSTOM_DROPDOWN, ccTrigger!!.controlKind)
        assertEquals("+1", ccTrigger.relevantAttributes["data-dial-code"])

        val phoneInput = inspection.elements.firstOrNull { it.htmlId == "phone-input" }
        assertNotNull(phoneInput)
        assertEquals("tel", phoneInput!!.inputType)
        assertEquals("tel", phoneInput.relevantAttributes["inputmode"])

        // Open bottom-sheet search input & country rows
        val countrySearch = inspection.elements.firstOrNull { it.htmlId == "country-search" }
        assertNotNull(countrySearch)
        assertTrue(countrySearch!!.visible)

        val ukRow = inspection.elements.firstOrNull { it.relevantAttributes["data-country-code"] == "GB" }
        val inRow = inspection.elements.firstOrNull { it.relevantAttributes["data-country-code"] == "IN" }
        assertNotNull(ukRow)
        assertNotNull("Country row with data-country-code even without role=option should be discovered", inRow)
        assertEquals(ControlKind.OPTION, ukRow!!.controlKind)
        assertEquals(ControlKind.OPTION, inRow!!.controlKind)
        assertEquals("+44", ukRow.relevantAttributes["data-dial-code"])
        assertTrue(ukRow.locatorCandidates.any { it.selector.contains("data-country-code=\"GB\"") })
        assertTrue(ukRow.parentContainerSummary.contains("country-sheet"))
        assertEquals("Select country code", ukRow.fieldsetLegend)

        // Instruction matching distinguishes country code from phone number
        val steps = engine.parseInstructionsToSteps(
            rawInstructions = """
                Click Country code +1
                Enter "UK" in Search country or code
                Click United Kingdom (+44)
                Enter "7700900123" in Phone Number
            """.trimIndent(),
            currentUrl = url,
            inspectionState = inspection
        )
        assertEquals(4, steps.size)
        assertEquals("#country-code-trigger", steps[0].selector)
        assertEquals("#country-search", steps[1].selector)
        assertTrue(steps[2].selector.contains("GB") || steps[2].selector.contains("+44"))
        assertEquals("#phone-input", steps[3].selector)
    }

    @Test
    fun productionScannerObserverAndRecorderScripts_includeSheetDateGenderAndCountryCodeSupport() {
        val scanScript = engine.universalScanner.buildUniversalScanScript("sess_s1", "tab_1", "https://example.com:443")
        assertTrue(scanScript.contains("WIDGET_DATA_ATTRS"))
        assertTrue(scanScript.contains("getHiddenControlForLabel"))
        assertTrue(scanScript.contains("isSheetOrDialogNode"))
        assertTrue(scanScript.contains("data-bottom-sheet"))
        assertTrue(scanScript.contains("data-country-code"))
        assertTrue(scanScript.contains("data-dial-code"))
        assertTrue(scanScript.contains("data-date"))
        assertTrue(scanScript.contains("gridcell"))

        val observerScript = engine.universalScanner.buildDynamicObserverInstallScript("sess_o1", "tab_1", "https://example.com:443")
        assertTrue(observerScript.contains("'data-state'"))
        assertTrue(observerScript.contains("'open'"))
        assertTrue(observerScript.contains("isNodeVisibleWithAncestors"))
        assertTrue(observerScript.contains("recordAddedSubtree(m.target, shadowDepth)"))
        assertTrue(observerScript.contains("dateTimeMetadata"))
        assertTrue(observerScript.contains("relevantAttributes"))

        val recorderScript = engine.buildRecorderInstallScript("sess_r1", "tab_1", "https://example.com/form")
        assertTrue(recorderScript.contains("data-country-code"))
        assertTrue(recorderScript.contains("data-dial-code"))
        assertTrue(recorderScript.contains("data-date"))
        assertTrue(recorderScript.contains("[role=\"gridcell\"]"))
        assertTrue(recorderScript.contains("[role=\"radio\"]"))
        assertTrue(recorderScript.contains("isHiddenToggleLabel"))
        assertTrue(recorderScript.contains("resolveFieldGroupLabel"))
        assertTrue(recorderScript.contains("flushPendingInput"))
    }

    @Test
    fun unlinkedBirthYearFieldGroup_discoversBirthYearAccessibleNameAndDateTimeMetadata() = runBlocking {
        val url = "https://account.fydeos.io/signup"
        val html = """
            <html>
            <body>
                <main>
                    <h1>Sign up</h1>
                    <p>Before creating an account, please confirm your age. We only ask for what is needed.</p>
                    <form>
                        <div class="form-group">
                            <label class="form-label">Birth year</label>
                            <div class="input-wrapper">
                                <input type="text" placeholder="2008" value="2008" />
                            </div>
                        </div>
                        <button type="submit">Next</button>
                    </form>
                </main>
            </body>
            </html>
        """.trimIndent()

        UniversalDomScanner.attachHtmlFixture(webView, url, html)
        val inspection = engine.scanPageElements(webView, url, tabId = "tab_fyde")
        assertEquals(DomScanStatus.COMPLETED, inspection.scanStatus)

        val birthYearInput = inspection.elements.firstOrNull { it.tagName == "input" }
        assertNotNull("Birth year input should be discovered", birthYearInput)
        assertEquals("Birth year", birthYearInput!!.accessibleName)
        assertEquals("Birth year", birthYearInput.label)
        assertNotNull("Birth year input should have dateTimeMetadata", birthYearInput.dateTimeMetadata)
        assertEquals("year", birthYearInput.dateTimeMetadata?.inputFormat)
        assertEquals("year-input", birthYearInput.dateTimeMetadata?.pickerRole)

        val steps = engine.parseInstructionsToSteps(
            rawInstructions = """
                Enter "2008" in Birth year
                Click Next
            """.trimIndent(),
            currentUrl = url,
            inspectionState = inspection
        )
        assertEquals(2, steps.size)
        assertEquals(AutomationActionType.INPUT, steps[0].actionType)
        assertEquals("2008", steps[0].value)
        assertEquals(birthYearInput.selector, steps[0].selector)
    }

    @Test
    fun birthYearRecognition_detectsNativeSelectCustomDropdownCalendarScrollableAndSeparateFieldsWhileExcludingUnrelatedNumerics() = runBlocking {
        val url = "https://accounts.example.com/age-verification"
        val html = """
            <html>
            <body>
                <form id="profile-form">
                    <label for="zip-code">ZIP Code</label>
                    <input id="zip-code" name="postalCode" type="number" placeholder="94105" />

                    <label for="otp-code">Verification Code (OTP)</label>
                    <input id="otp-code" name="otp" type="tel" maxlength="6" placeholder="123456" />

                    <label for="grad-year">Graduation Year</label>
                    <input id="grad-year" name="graduationYear" type="number" placeholder="2020" />

                    <label for="item-qty">Quantity</label>
                    <input id="item-qty" name="qty" type="number" value="1" />

                    <fieldset>
                        <legend>Date of Birth</legend>
                        <label for="dob-day">Day</label>
                        <input id="dob-day" name="day" type="number" placeholder="DD" />
                        <label for="dob-month">Month</label>
                        <select id="dob-month" name="month">
                            <option value="01">January</option>
                            <option value="02">February</option>
                        </select>
                        <label for="dob-year-input">Year</label>
                        <input id="dob-year-input" name="year" type="number" placeholder="YYYY" min="1900" max="2026" aria-describedby="dob-year-help" />
                        <span id="dob-year-help">Enter your 4-digit birth year</span>
                    </fieldset>

                    <label for="native-birth-year-select">Birth Year Select</label>
                    <select id="native-birth-year-select" name="birthYearSelect">
                        <option value="">Select Year</option>
                        <option value="1998">1998</option>
                        <option value="1999">1999</option>
                        <option value="2000">2000</option>
                    </select>

                    <div id="custom-year-dropdown" role="combobox" aria-haspopup="listbox" aria-expanded="true" aria-controls="custom-year-listbox" aria-label="Custom Birth Year">2000</div>
                    <ul id="custom-year-listbox" role="listbox" aria-label="Birth Year Options">
                        <li role="option" data-value="1997">1997</li>
                        <li role="option" data-value="1998" aria-selected="true">1998</li>
                    </ul>

                    <div id="calendar-sheet" role="dialog" aria-modal="true" aria-label="Select Date of Birth" data-bottom-sheet data-state="open">
                        <div class="year-picker" data-year-picker>
                            <button type="button" class="year-option" data-year="1995">1995</button>
                            <button type="button" class="year-option" data-year="1996">1996</button>
                        </div>
                    </div>
                </form>
            </body>
            </html>
        """.trimIndent()

        UniversalDomScanner.attachHtmlFixture(webView, url, html)
        val inspection = engine.scanPageElements(webView, url, tabId = "tab_birth_variants")
        assertEquals(DomScanStatus.COMPLETED, inspection.scanStatus)

        val byId = inspection.elements.filter { it.htmlId.isNotEmpty() }.associateBy { it.htmlId }

        // 1. Unrelated numeric fields must NOT have birth-year dateTimeMetadata
        assertNull("ZIP code must not be classified as a birth-year field", byId["zip-code"]?.dateTimeMetadata)
        assertNull("OTP code must not be classified as a birth-year field", byId["otp-code"]?.dateTimeMetadata)
        assertNull("Graduation year must not be classified as a birth-year field", byId["grad-year"]?.dateTimeMetadata)
        assertNull("Quantity must not be classified as a birth-year field", byId["item-qty"]?.dateTimeMetadata)

        // 2. Separate Day, Month, and Year fields inside a Date of Birth fieldset
        val dobDay = byId["dob-day"]
        val dobMonth = byId["dob-month"]
        val dobYearInput = byId["dob-year-input"]
        assertNotNull(dobDay)
        assertNotNull(dobMonth)
        assertNotNull(dobYearInput)
        assertEquals("day", dobDay!!.dateTimeMetadata?.inputFormat)
        assertEquals("month", dobMonth!!.dateTimeMetadata?.inputFormat)
        assertEquals("year", dobYearInput!!.dateTimeMetadata?.inputFormat)
        assertEquals("year-input", dobYearInput.dateTimeMetadata?.pickerRole)
        assertEquals("1900", dobYearInput.dateTimeMetadata?.min)
        assertEquals("2026", dobYearInput.dateTimeMetadata?.max)

        // 3. Native <select> with year options
        val nativeSelect = byId["native-birth-year-select"]
        assertNotNull(nativeSelect)
        assertEquals(ControlKind.SELECT, nativeSelect!!.controlKind)
        assertEquals("year", nativeSelect.dateTimeMetadata?.inputFormat)
        assertEquals("year-select", nativeSelect.dateTimeMetadata?.pickerRole)

        // 4. Custom dropdown / combobox
        val customDropdown = byId["custom-year-dropdown"]
        assertNotNull(customDropdown)
        assertEquals(ControlKind.CUSTOM_DROPDOWN, customDropdown!!.controlKind)
        assertNotNull(customDropdown.dateTimeMetadata)

        // 5. Calendar / bottom-sheet scrollable year-picker items
        val year1995 = inspection.elements.firstOrNull { it.relevantAttributes["data-year"] == "1995" }
        assertNotNull("Scrollable year option inside calendar bottom sheet must be discovered", year1995)

        // 6. When only the separate Day/Month/Year fields + unrelated numeric fields exist, "Enter 1998 in Birth Year"
        // resolves specifically to #dob-year-input and never to ZIP, OTP, Graduation Year, Day, or Month
        val separateOnlyHtml = """
            <html>
            <body>
                <form id="signup-form">
                    <label for="zip-code">ZIP Code</label>
                    <input id="zip-code" name="postalCode" type="number" placeholder="94105" />
                    <label for="grad-year">Graduation Year</label>
                    <input id="grad-year" name="graduationYear" type="number" placeholder="2020" />
                    <fieldset>
                        <legend>Date of Birth</legend>
                        <label for="dob-day">Day</label>
                        <input id="dob-day" name="day" type="number" placeholder="DD" />
                        <label for="dob-month">Month</label>
                        <input id="dob-month" name="month" type="number" placeholder="MM" />
                        <label for="dob-year">Year</label>
                        <input id="dob-year" name="year" type="number" placeholder="YYYY" />
                    </fieldset>
                </form>
            </body>
            </html>
        """.trimIndent()
        UniversalDomScanner.attachHtmlFixture(webView, url, separateOnlyHtml)
        val sepInspection = engine.scanPageElements(webView, url, tabId = "tab_sep_dob")
        val sepSteps = engine.parseInstructionsToSteps(
            rawInstructions = "Enter \"1998\" in Birth Year",
            currentUrl = url,
            inspectionState = sepInspection
        )
        assertEquals(1, sepSteps.size)
        assertEquals("#dob-year", sepSteps[0].selector)
        assertTrue(sepSteps[0].locatorCandidates.isNotEmpty())
    }

    @Test
    fun birthYearDisambiguation_flagsAmbiguityWhenMultipleBirthYearFieldsExistInsteadOfSelectingFirst() = runBlocking {
        val url = "https://insurance.example.com/dependents"
        val html = """
            <html>
            <body>
                <form id="dependents-form">
                    <div class="dependent-row">
                        <label class="field-label">Birth year</label>
                        <input name="birthYear" type="number" placeholder="YYYY" />
                    </div>
                    <div class="dependent-row">
                        <label class="field-label">Birth year</label>
                        <input name="birthYear" type="number" placeholder="YYYY" />
                    </div>
                </form>
            </body>
            </html>
        """.trimIndent()

        UniversalDomScanner.attachHtmlFixture(webView, url, html)
        val inspection = engine.scanPageElements(webView, url, tabId = "tab_ambig_dob")
        val match = engine.universalScanner.matchCandidatesForInstruction(
            clause = "Enter \"2012\" in Birth year",
            actionType = AutomationActionType.INPUT,
            targetLabel = "Birth year",
            inspectionState = inspection
        )
        assertTrue(
            "Multiple matching birth-year fields must flag ambiguity rather than selecting [0]",
            match.isAmbiguous
        )
        assertEquals(2, match.matchedElements.size)

        val steps = engine.parseInstructionsToSteps(
            rawInstructions = "Enter \"2012\" in Birth year",
            currentUrl = url,
            inspectionState = inspection
        )
        assertEquals(1, steps.size)
        assertTrue(
            "Parsed step must not bind single-element locatorCandidates when multiple birth-year fields match equally",
            steps[0].locatorCandidates.isEmpty()
        )
    }

    @Test
    fun bottomSheetAndCalendarCalibration_discoversOpenSheetTitleTimeSlotsAndHidesClosedSheets() = runBlocking {
        val url = "https://booking.example.com/schedule"
        val html = """
            <html>
            <body>
                <button id="open-sheet-btn" aria-haspopup="dialog">Choose Slot</button>

                <div class="bottom-sheet closed" data-bottom-sheet>
                    <h2 class="sheet-title">Hidden Sheet</h2>
                    <button id="hidden-sheet-btn">Should Not Be Discovered</button>
                </div>

                <div data-sheet data-visible="false">
                    <button id="invisible-sheet-btn">Invisible Sheet Action</button>
                </div>

                <div id="translated-sheet" data-bottom-sheet style="position: fixed; transform: translateY(100%)">
                    <button id="offscreen-sheet-btn">Offscreen Drawer Action</button>
                </div>

                <div id="active-sheet" class="bottom-sheet" data-bottom-sheet data-state="open">
                    <h2 class="sheet-title">Select Appointment</h2>
                    <div class="flatpickr-calendar open" role="application" aria-roledescription="calendar">
                        <div class="calendar-title">June 2026</div>
                        <button type="button" data-picker-role="prev-month" aria-label="Previous month">Prev</button>
                        <button type="button" data-picker-role="next-month" aria-label="Next month">Next</button>
                        <div role="gridcell" data-date="2026-06-15" aria-label="June 15, 2026">15</div>
                        <button type="button" id="cal-today" data-picker-role="today-action">Today</button>
                        <button type="button" id="cal-clear" data-picker-role="clear-action">Clear</button>
                    </div>
                    <div class="time-slots">
                        <button type="button" id="slot-0930" data-time="09:30">09:30 AM</button>
                        <button type="button" id="slot-1400" data-slot="14:00">02:00 PM</button>
                    </div>
                    <div id="«r9f»" role="button" tabindex="0">Sheet Continue</div>
                </div>
            </body>
            </html>
        """.trimIndent()

        UniversalDomScanner.attachHtmlFixture(webView, url, html)
        val inspection = engine.scanPageElements(webView, url, tabId = "tab_sheet_cal")

        // Closed / hidden / translated bottom sheets must not be exposed as visible interactive targets
        assertTrue(inspection.elements.none { it.visible && it.selector.contains("hidden-sheet-btn") })
        assertTrue(inspection.elements.none { it.visible && it.selector.contains("invisible-sheet-btn") })
        assertTrue(inspection.elements.none { it.visible && it.selector.contains("offscreen-sheet-btn") })

        // Active sheet elements and calendar actions must be discovered with sheet title context
        val dayCell = inspection.elements.firstOrNull { it.relevantAttributes["data-date"] == "2026-06-15" }
        assertNotNull("Calendar day cell should be discovered", dayCell)
        assertTrue(dayCell!!.visible)
        assertEquals("Select Appointment", dayCell.nearbyText.ifBlank { dayCell.fieldsetLegend })
        assertEquals("calendar-day", dayCell.dateTimeMetadata?.pickerRole)

        val todayBtn = inspection.elements.firstOrNull { it.selector == "#cal-today" }
        assertNotNull(todayBtn)
        assertTrue(todayBtn!!.visible)
        assertEquals("today-action", todayBtn.dateTimeMetadata?.pickerRole)

        val clearBtn = inspection.elements.firstOrNull { it.selector == "#cal-clear" }
        assertNotNull(clearBtn)
        assertTrue(clearBtn!!.visible)
        assertEquals("clear-action", clearBtn.dateTimeMetadata?.pickerRole)

        val timeSlot = inspection.elements.firstOrNull { it.selector == "#slot-0930" }
        assertNotNull("Time slot button should be discovered", timeSlot)
        assertTrue(timeSlot!!.visible)
        assertEquals("time-slot", timeSlot.dateTimeMetadata?.pickerRole)

        val slotBtn = inspection.elements.firstOrNull { it.selector == "#slot-1400" }
        assertNotNull("data-slot button should be discovered", slotBtn)
        assertTrue(slotBtn!!.visible)
        assertEquals("time-slot", slotBtn.dateTimeMetadata?.pickerRole)

        // React 19 ID «r9f» must not be treated as a stable ID selector
        val reactBtn = inspection.elements.firstOrNull { it.accessibleName.contains("Sheet Continue") }
        assertNotNull(reactBtn)
        assertFalse("React 19 unstable ID must not be emitted as #id", reactBtn!!.selector.contains("«r9f»"))

        val scanJs = engine.universalScanner.buildUniversalScanScript("s1", "t1", "https://booking.example.com:443")
        assertTrue(scanJs.contains("data-time"))
        assertTrue(scanJs.contains("data-slot"))
        assertTrue(scanJs.contains("sheet-title"))
        assertTrue(scanJs.contains("flatpickr-calendar"))
    }

    @Test
    fun signupButtonsLinksAndText_discoversCustomCtasSpaLinksAndReadableTextContent() = runBlocking {
        val url = "https://auth.example.com/signup"
        val html = """
            <html>
            <body>
                <h1>Create your account</h1>
                <p>Join thousands of developers building faster today.</p>
                <div role="alert">Email address is required</div>
                <div role="status">Registration open</div>
                <div id="ignored-noneditable" contenteditable="false">Static help box</div>

                <div id="tabindex-signup" tabindex="0">Sign up</div>
                <div id="class-signup" class="signup-btn">Create Free Account</div>
                <div id="action-signup" data-action="signup" style="cursor: pointer"><span>Sign Up Free</span></div>
                <button id="icon-signup" type="button"><img src="g.png" alt="Sign up with Google" /></button>
                <a id="role-btn-cta" role="button" href="#">Get Started Now</a>

                <a id="spa-link">Forgot Password</a>
                <a id="href-link" href="/privacy">Privacy Policy</a>
                <span id="role-link" role="link" data-href="/terms">Terms of Service</span>
                <button id="signin-btn" type="button">Sign in</button>
            </body>
            </html>
        """.trimIndent()

        UniversalDomScanner.attachHtmlFixture(webView, url, html)
        val inspection = engine.scanPageElements(webView, url, tabId = "tab_signup_links_text")

        val byId = inspection.elements.filter { it.htmlId.isNotEmpty() }.associateBy { it.htmlId }

        // 1. Signup buttons of all varieties discovered cleanly
        val tabIdxSignup = byId["tabindex-signup"]
        assertNotNull("div[tabindex=0] signup button must be discovered", tabIdxSignup)
        assertEquals(ControlKind.BUTTON, tabIdxSignup!!.controlKind)
        assertEquals("Sign up", tabIdxSignup.accessibleName)

        val classSignup = byId["class-signup"]
        assertNotNull("div.signup-btn must be discovered", classSignup)
        assertEquals(ControlKind.BUTTON, classSignup!!.controlKind)
        assertEquals("Create Free Account", classSignup.accessibleName)

        val actionSignup = byId["action-signup"]
        assertNotNull("[data-action] signup CTA with inner span must be discovered", actionSignup)
        assertEquals(ControlKind.BUTTON, actionSignup!!.controlKind)
        assertEquals("Sign Up Free", actionSignup.accessibleName)

        val iconSignup = byId["icon-signup"]
        assertNotNull("Icon-only button with img[alt] must be discovered", iconSignup)
        assertEquals("Sign up with Google", iconSignup!!.accessibleName)

        val roleBtnCta = byId["role-btn-cta"]
        assertNotNull("<a role=button> should classify as BUTTON", roleBtnCta)
        assertEquals(ControlKind.BUTTON, roleBtnCta!!.controlKind)

        // 2. SPA links (no href) & role="link" & normal <a href> discovered cleanly
        val spaLink = byId["spa-link"]
        assertNotNull("SPA <a> without href must be discovered", spaLink)
        assertEquals(ControlKind.LINK, spaLink!!.controlKind)
        assertEquals("Forgot Password", spaLink.accessibleName)

        val hrefLink = byId["href-link"]
        assertNotNull(hrefLink)
        assertEquals(ControlKind.LINK, hrefLink!!.controlKind)

        val roleLink = byId["role-link"]
        assertNotNull("<span role=link data-href> must be discovered", roleLink)
        assertEquals(ControlKind.LINK, roleLink!!.controlKind)
        assertEquals("Terms of Service", roleLink.accessibleName)

        // 3. Headings, paragraphs, alerts, and status messages discovered as ControlKind.TEXT (non-interactable)
        val textElements = inspection.elements.filter { it.controlKind == ControlKind.TEXT }
        assertTrue("Expected at least 4 readable text elements", textElements.size >= 4)
        assertTrue(inspection.summary.textElementsFound >= 4)
        assertTrue(textElements.any { it.accessibleName.contains("Create your account") })
        assertTrue(textElements.any { it.accessibleName.contains("Join thousands of developers") })
        assertTrue(textElements.any { it.accessibleName.contains("Email address is required") })
        assertTrue(textElements.any { it.accessibleName.contains("Registration open") })
        assertTrue("TEXT elements must remain non-interactable", textElements.all { !it.interactable })
        assertNull("contenteditable=false static box must remain excluded", byId["ignored-noneditable"])

        // 4. Instruction matching: "Sign up" vs "Sign in", links, and TEXT extraction
        val matchSignup = engine.universalScanner.matchCandidatesForInstruction(
            clause = "Click Sign up button",
            actionType = AutomationActionType.CLICK,
            targetLabel = "Sign up button",
            inspectionState = inspection
        )
        assertNotNull(matchSignup.primaryCandidate)
        assertEquals("Sign up", matchSignup.primaryCandidate!!.accessibleName)
        assertFalse(matchSignup.primaryCandidate!!.htmlId == "signin-btn")

        val matchSpaLink = engine.universalScanner.matchCandidatesForInstruction(
            clause = "Click Forgot Password link",
            actionType = AutomationActionType.CLICK,
            targetLabel = "Forgot Password link",
            inspectionState = inspection
        )
        assertNotNull(matchSpaLink.primaryCandidate)
        assertEquals("spa-link", matchSpaLink.primaryCandidate!!.htmlId)

        val matchTextExtract = engine.universalScanner.matchCandidatesForInstruction(
            clause = "Extract Create your account",
            actionType = AutomationActionType.EXTRACT,
            targetLabel = "Create your account",
            inspectionState = inspection
        )
        assertNotNull(matchTextExtract.primaryCandidate)
        assertEquals(ControlKind.TEXT, matchTextExtract.primaryCandidate!!.controlKind)

        val scanJs = engine.universalScanner.buildUniversalScanScript("s2", "t2", "https://auth.example.com:443")
        assertTrue(scanJs.contains("CLICKABLE_CLASS_RE"))
        assertTrue(scanJs.contains("isTextContentCandidate"))
        assertTrue(scanJs.contains("deferredTextNodes"))
        assertTrue(scanJs.contains("display === 'contents'"))
    }

    @Test
    fun complexSignupForm_prioritizesLabeledAndPlaceholderInputsWithConcurrentAttributes() = runBlocking {
        val url = "https://accounts.example.com/complex-signup"
        val html = """
            <html>
            <body>
                <header>
                    <a href="/pricing">Pricing</a>
                    <a href="/docs">Documentation</a>
                    <button id="header-help" type="button">Help</button>
                </header>
                <form id="complex-signup-form">
                    <div class="floating-field">
                        <input id="signup-first-name" name="first_name" type="text" placeholder="Jane" />
                        <label>First Name</label>
                    </div>
                    <div>
                        <label for="signup-work-email">Work Email</label>
                        <input id="signup-work-email" name="contact_field" type="email" aria-label="Work Email" placeholder="jane@company.com" />
                    </div>
                    <div>
                        <label for="signup-backup-email">Recovery Contact</label>
                        <input id="signup-backup-email" name="contact_field" type="email" aria-label="Recovery Email" placeholder="alt@example.com" />
                    </div>
                    <div>
                        <input id="signup-password" name="auth_secret" type="password" aria-label="Password" placeholder="Create password" />
                    </div>
                    <div>
                        <input id="signup-confirm-password" name="auth_secret" type="password" aria-label="Confirm password" placeholder="Re-enter password" />
                    </div>
                    <div>
                        <input id="signup-phone" name="mobile_number" type="tel" aria-label="Phone Number" placeholder="+1 555 0100" />
                    </div>
                    <button id="create-account-btn" type="submit">Create Account</button>
                </form>
            </body>
            </html>
        """.trimIndent()

        UniversalDomScanner.attachHtmlFixture(webView, url, html)
        val inspection = engine.scanPageElements(webView, url, tabId = "tab_complex_signup")

        // 1. Labeled / placeholdered input fields are prioritized ahead of nav links and generic buttons
        val inputIndices = inspection.elements.mapIndexedNotNull { idx, el ->
            if (el.tagName == "input" && el.htmlId.startsWith("signup-")) idx else null
        }
        val firstNonInputIdx = inspection.elements.indexOfFirst { it.tagName == "a" || it.tagName == "button" }
        assertEquals("Expected all 6 labeled/placeholdered signup inputs to be discovered", 6, inputIndices.size)
        assertTrue("Nav/button elements should exist in scan", firstNonInputIdx >= 0)
        assertTrue(
            "Labeled/placeholdered inputs must be prioritized before general links/buttons",
            inputIndices.maxOrNull()!! < firstNonInputIdx
        )

        // 2. Floating label after <input> is associated cleanly
        val firstNameEl = inspection.elements.first { it.htmlId == "signup-first-name" }
        assertEquals("First Name", firstNameEl.accessibleName)

        // 3. Colliding name="auth_secret" / name="contact_field" inputs gain unique composite locators via type + aria-label / placeholder
        val confirmPwEl = inspection.elements.first { it.htmlId == "signup-confirm-password" }
        assertTrue(
            "Composite type+aria-label locator should uniquely disambiguate colliding name attributes",
            confirmPwEl.locatorCandidates.any {
                it.isUnique && it.selector.contains("""type="password"""") && it.selector.contains("""aria-label="Confirm password"""")
            }
        )

        // 4. Concurrent attribute matching disambiguates every complex sign-up field accurately
        val workEmailMatch = engine.universalScanner.matchCandidatesForInstruction(
            clause = "Enter Work Email",
            actionType = AutomationActionType.INPUT,
            targetLabel = "Work Email",
            inspectionState = inspection
        )
        assertEquals("signup-work-email", workEmailMatch.primaryCandidate?.htmlId)

        val confirmPwMatch = engine.universalScanner.matchCandidatesForInstruction(
            clause = "Enter Confirm password",
            actionType = AutomationActionType.INPUT,
            targetLabel = "Confirm password",
            inspectionState = inspection
        )
        assertEquals("signup-confirm-password", confirmPwMatch.primaryCandidate?.htmlId)

        val primaryPwMatch = engine.universalScanner.matchCandidatesForInstruction(
            clause = "Fill Password",
            actionType = AutomationActionType.INPUT,
            targetLabel = "Password",
            inspectionState = inspection
        )
        assertEquals("signup-password", primaryPwMatch.primaryCandidate?.htmlId)

        val firstNameMatch = engine.universalScanner.matchCandidatesForInstruction(
            clause = "Input First Name",
            actionType = AutomationActionType.INPUT,
            targetLabel = "First Name",
            inspectionState = inspection
        )
        assertEquals("signup-first-name", firstNameMatch.primaryCandidate?.htmlId)

        val phoneMatch = engine.universalScanner.matchCandidatesForInstruction(
            clause = "Type Phone",
            actionType = AutomationActionType.INPUT,
            targetLabel = "Phone",
            inspectionState = inspection
        )
        assertEquals("signup-phone", phoneMatch.primaryCandidate?.htmlId)

        // 5. Verify live JS scanner and action script emit the concurrent prioritization logic
        val scanJs = engine.universalScanner.buildUniversalScanScript("s3", "t3", "https://accounts.example.com:443")
        assertTrue(scanJs.contains("isPrioritizedLabeledInputCandidate"))
        assertTrue(scanJs.contains("orderedInteractableNodes"))
    }

    @Test
    fun smartDomScanner_recognizesUnconventionalFieldLayoutsAndSemanticTypes() = runBlocking {
        val url = "https://enterprise.example.com/onboarding"
        val html = """
            <html>
            <body>
                <form id="onboarding-form">
                    <table>
                        <thead>
                            <tr><th>Role Title</th><th>Department Code</th></tr>
                        </thead>
                        <tbody>
                            <tr>
                                <th>Work Email</th>
                                <td><input id="tbl-email" type="email" autocomplete="email" /></td>
                            </tr>
                            <tr>
                                <td><input id="thead-role" /></td>
                                <td><input id="thead-dept" data-field="department_code" /></td>
                            </tr>
                        </tbody>
                    </table>

                    <dl>
                        <dt>Company Name</dt>
                        <dd><input id="dl-company" name="org" autocomplete="organization" /></dd>
                        <dt>Postal Code</dt>
                        <dd><input id="dl-zip" inputmode="numeric" autocomplete="postal-code" /></dd>
                    </dl>

                    <div class="row-without-label-class">
                        <span>Billing Street</span>
                        <input id="flex-street" autocomplete="street-address" />
                        <span class="error-msg">Required field.</span>
                    </div>

                    <div>
                        <input id="no-type-promo" placeholder="Enter coupon code" />
                    </div>

                    <div>
                        <input id="datalist-city" list="city-suggestions" aria-placeholder="Preferred City" />
                        <datalist id="city-suggestions">
                            <option value="Seattle" />
                            <option value="Austin" />
                            <option value="New York" />
                        </datalist>
                    </div>

                    <div>
                        <ion-input id="wc-phone" label="Mobile Phone" type="tel" inputmode="tel"></ion-input>
                        <md-outlined-text-field id="wc-notes" placeholder="Order Notes" data-field="order_notes"></md-outlined-text-field>
                    </div>

                    <div>
                        <input id="field-resume" type="file" accept=".pdf,.docx" aria-label="Upload Resume" />
                        <input id="field-accent" type="color" aria-label="Brand Color" />
                        <input id="field-budget" type="range" min="100" max="5000" aria-label="Monthly Budget" />
                        <textarea id="field-comments" placeholder="Additional Comments"></textarea>
                    </div>

                    <div>
                        <input id="secret-cvv" name="card_cvc" autocomplete="cc-csc" value="999" aria-label="Security Code" />
                        <input id="card-exp" name="exp" autocomplete="cc-exp" placeholder="MM/YY" />
                        <input id="card-holder" name="holder" autocomplete="cc-name" placeholder="Name on Card" />
                    </div>
                </form>
            </body>
            </html>
        """.trimIndent()

        UniversalDomScanner.attachHtmlFixture(webView, url, html)
        val inspection = engine.scanPageElements(webView, url, tabId = "tab_smart_fields")
        val byId = inspection.elements.associateBy { it.htmlId }

        // Table th/td and thead column header extraction
        assertEquals("Work Email", byId["tbl-email"]?.accessibleName)
        assertEquals(com.example.automation.FieldSemanticType.EMAIL, byId["tbl-email"]?.semanticType)
        assertEquals("Role Title", byId["thead-role"]?.accessibleName)
        assertEquals(com.example.automation.FieldSemanticType.JOB_TITLE, byId["thead-role"]?.semanticType)

        // Definition list <dt>/<dd>
        assertEquals("Company Name", byId["dl-company"]?.accessibleName)
        assertEquals(com.example.automation.FieldSemanticType.COMPANY, byId["dl-company"]?.semanticType)
        assertEquals("Postal Code", byId["dl-zip"]?.accessibleName)
        assertEquals("numeric", byId["dl-zip"]?.inputMode)
        assertEquals(com.example.automation.FieldSemanticType.POSTAL_CODE, byId["dl-zip"]?.semanticType)

        // Flex div row without "label" class + error-msg ignored
        assertEquals("Billing Street", byId["flex-street"]?.accessibleName)
        assertEquals(com.example.automation.FieldSemanticType.STREET_ADDRESS, byId["flex-street"]?.semanticType)

        // Input omitting type attribute prioritized + classified as promo code
        assertEquals(com.example.automation.FieldSemanticType.PROMO_CODE, byId["no-type-promo"]?.semanticType)
        assertEquals(ControlKind.STANDARD_INPUT, byId["no-type-promo"]?.controlKind)

        // aria-placeholder & <datalist> options
        assertEquals("Preferred City", byId["datalist-city"]?.accessibleName)
        assertEquals(com.example.automation.FieldSemanticType.CITY, byId["datalist-city"]?.semanticType)
        assertEquals(listOf("Seattle", "Austin", "New York"), byId["datalist-city"]?.datalistOptions)

        // Custom Web Components
        assertNotNull("<ion-input> must be discovered", byId["wc-phone"])
        assertEquals("Mobile Phone", byId["wc-phone"]?.accessibleName)
        assertEquals(ControlKind.STANDARD_INPUT, byId["wc-phone"]?.controlKind)
        assertEquals(com.example.automation.FieldSemanticType.PHONE, byId["wc-phone"]?.semanticType)

        assertNotNull("<md-outlined-text-field> must be discovered", byId["wc-notes"])
        assertEquals(com.example.automation.FieldSemanticType.MESSAGE_NOTES, byId["wc-notes"]?.semanticType)

        // Specialized input types
        assertEquals(com.example.automation.FieldSemanticType.FILE_UPLOAD, byId["field-resume"]?.semanticType)
        assertEquals(com.example.automation.FieldSemanticType.COLOR_PICKER, byId["field-accent"]?.semanticType)
        assertEquals(com.example.automation.FieldSemanticType.NUMERIC_RANGE, byId["field-budget"]?.semanticType)
        assertEquals(com.example.automation.FieldSemanticType.MESSAGE_NOTES, byId["field-comments"]?.semanticType)

        // Payment semantics & strict redaction on CVV
        assertEquals(com.example.automation.FieldSemanticType.CARD_CVV, byId["secret-cvv"]?.semanticType)
        assertEquals("", byId["secret-cvv"]?.safeValue)
        assertFalse(byId["secret-cvv"].toString().contains("999"))
        assertEquals(com.example.automation.FieldSemanticType.CARD_EXPIRY, byId["card-exp"]?.semanticType)
        assertEquals(com.example.automation.FieldSemanticType.CARDHOLDER_NAME, byId["card-holder"]?.semanticType)

        // Synonym & typo-tolerant instruction resolution
        val employerMatch = engine.universalScanner.matchCandidatesForInstruction(
            clause = "Enter Employer",
            actionType = AutomationActionType.INPUT,
            targetLabel = "Employer",
            inspectionState = inspection
        )
        assertEquals("dl-company", employerMatch.primaryCandidate?.htmlId)

        val zipSynonymMatch = engine.universalScanner.matchCandidatesForInstruction(
            clause = "Fill ZIP",
            actionType = AutomationActionType.INPUT,
            targetLabel = "ZIP",
            inspectionState = inspection
        )
        assertEquals("dl-zip", zipSynonymMatch.primaryCandidate?.htmlId)

        val emailTypoMatch = engine.universalScanner.matchCandidatesForInstruction(
            clause = "Type Emial",
            actionType = AutomationActionType.INPUT,
            targetLabel = "Emial",
            inspectionState = inspection
        )
        assertEquals("tbl-email", emailTypoMatch.primaryCandidate?.htmlId)

        // Intelligence summary surfaces semantic field types
        assertTrue(inspection.pageIntentSummary.contains("Fields:"))

        // Live JS script contains smart field recognition hooks
        val scanJs = engine.universalScanner.buildUniversalScanScript("s4", "t4", "https://enterprise.example.com:443")
        assertTrue(scanJs.contains("hasCustomFieldSemantics"))
        assertTrue(scanJs.contains("datalistOpts"))
        assertTrue(scanJs.contains("thead tr"))
        assertTrue(scanJs.contains("aria-placeholder"))
    }
}

