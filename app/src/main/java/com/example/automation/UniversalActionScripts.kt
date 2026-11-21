package com.example.automation

import org.json.JSONArray
import org.json.JSONObject

/**
 * Phase 5 deterministic JavaScript execution, 6-step pre-action verification,
 * Shadow DOM / same-origin iframe traversal, post-action outcome verification,
 * and wait condition probe builders.
 *
 * Strictly zero external AI calls and zero blind [0] element fallbacks.
 */
object UniversalActionScripts {

    private fun buildCandidateSelectorsJson(step: AutomationStep): String {
        val arr = JSONArray()
        val seen = LinkedHashSet<String>()
        if (step.selector.isNotBlank() && seen.add(step.selector.trim())) {
            arr.put(step.selector.trim())
        }
        step.locatorCandidates
            .sortedWith(compareByDescending<LocatorCandidate> { it.isUnique }.thenByDescending { it.confidence })
            .forEach { cand ->
                val clean = cand.selector.trim()
                if (clean.isNotEmpty() && seen.add(clean)) {
                    arr.put(clean)
                }
            }
        return arr.toString()
    }

    fun buildTargetHelpersJs(
        labelHint: String,
        framePath: List<String> = emptyList(),
        shadowHostPath: List<String> = emptyList()
    ): String {
        val qLabel = JSONObject.quote(labelHint.trim())
        val qFrameChain = JSONArray(framePath).toString()
        val qShadowChain = JSONArray(shadowHostPath).toString()
        return """
            function apexNorm(s) {
                return (s || '').replace(/\s+/g, ' ').trim().toLowerCase();
            }
            function apexNormCompact(s) {
                return apexNorm(s).replace(/[\s_-]+/g, '');
            }
            function apexTokenizeSig(s) {
                return String(s || '')
                    .replace(/([a-z0-9])([A-Z])/g, '$1 $2')
                    .replace(/[_\-]+/g, ' ')
                    .replace(/\s+/g, ' ')
                    .trim()
                    .toLowerCase();
            }
            function apexEscapeCss(s) {
                return String(s || '').replace(/([!"#$%&'()*+,.\/:;<=>?@\[\\\]^`{|}~])/g, '\\$1');
            }
            function apexEscapeAttr(s) {
                return String(s || '').replace(/\\/g, '\\\\').replace(/"/g, '\\"');
            }
            function apexIsStableId(id) {
                if (!id) return false;
                const clean = String(id).trim();
                if (!clean || clean.length > 80 || /\s/.test(clean)) return false;
                if (/^(?:react-select-\d+.*|:r[0-9a-z]+:|«r[0-9a-z]+»|_r_[0-9a-z]+_|radix-.*-\d+|downshift-\d+.*|mui-\d+|ember\d+|el-id-\d+.*|headlessui-.*-\d+|[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}|_[0-9a-z]{7,}|\d{5,})$/i.test(clean)) return false;
                if (/^(?:css-[0-9a-z]{5,}|sc-[a-zA-Z0-9]{5,}|jsx-\d+|svelte-[0-9a-z]{5,}|tw-[0-9a-f]{6,}|style_[0-9a-f]{6,})$/.test(clean)) return false;
                if (/\d{6,}/.test(clean)) return false;
                return true;
            }
            function apexOwnDoc(node) {
                try {
                    if (!node) return document;
                    if (node.nodeType === 9) return node;
                    if (typeof ShadowRoot !== 'undefined' && node instanceof ShadowRoot) {
                        return node.ownerDocument || document;
                    }
                    return node.ownerDocument || document;
                } catch (_) {
                    return document;
                }
            }
            function apexOwnWin(doc) {
                try {
                    return (doc && doc.defaultView) ? doc.defaultView : window;
                } catch (_) {
                    return window;
                }
            }
            function apexIsConnected(el) {
                if (!el) return false;
                if (typeof el.isConnected === 'boolean') return el.isConnected;
                try {
                    const root = el.getRootNode ? el.getRootNode() : apexOwnDoc(el);
                    return Boolean(root && (root === document || root.host && apexIsConnected(root.host) || apexOwnDoc(el).documentElement.contains(el)));
                } catch (_) {
                    return true;
                }
            }
            function apexIsSheetOrDialogNode(node) {
                if (!node || node.nodeType !== 1 || !node.tagName) return false;
                const t = node.tagName.toLowerCase();
                if (t === 'dialog') return true;
                const r = String(node.getAttribute && node.getAttribute('role') || '').toLowerCase();
                if (r === 'dialog' || r === 'alertdialog' || r === 'listbox' || r === 'menu') return true;
                if (node.hasAttribute && (node.hasAttribute('aria-modal') || node.hasAttribute('data-bottom-sheet') ||
                    node.hasAttribute('data-sheet') || node.hasAttribute('data-drawer') ||
                    node.hasAttribute('data-modal') || node.hasAttribute('data-popup'))) return true;
                const cls = String(node.className || '').toLowerCase();
                return /(bottom-sheet|bottomsheet|action-sheet|modal-dialog|\bmodal\b|drawer|offcanvas|sheet-content|sheet-container|cdk-overlay-pane|muimodal-root|muidrawer-root|vaul-drawer|datepicker|date-picker|calendar-popup|calendar-popover|country-picker|timepicker|time-picker|flatpickr-calendar)/.test(cls);
            }
            function apexIsVisible(el) {
                if (!el || el.nodeType !== 1) return false;
                const type = (el.getAttribute && el.getAttribute('type') || '').toLowerCase();
                if (type === 'hidden') return false;
                let cur = el;
                let depth = 0;
                const rootVw = window.innerWidth || 1080;
                const rootVh = window.innerHeight || 1920;
                while (cur && cur.nodeType === 1 && depth < 32) {
                    const cTag = (cur.tagName || '').toLowerCase();
                    if (cur.hasAttribute && (cur.hasAttribute('hidden') || cur.hasAttribute('inert'))) return false;
                    if (String(cur.getAttribute && cur.getAttribute('aria-hidden') || '').toLowerCase() === 'true') return false;
                    const dState = String(cur.getAttribute && cur.getAttribute('data-state') || '').toLowerCase();
                    const dOpen = String(cur.getAttribute && cur.getAttribute('data-open') || '').toLowerCase();
                    const dVis = String(cur.getAttribute && cur.getAttribute('data-visible') || '').toLowerCase();
                    const curCls = String(cur.className || '').toLowerCase();
                    if (cTag === 'dialog' && !cur.hasAttribute('open') && dState !== 'open' && dOpen !== 'true') return false;
                    if (apexIsSheetOrDialogNode(cur) && (dState === 'closed' || dOpen === 'false' || dVis === 'false' || /(?:^|\s)(closed|is-closed|sheet-closed|drawer-closed)(?:\s|$)/.test(curCls))) return false;
                    try {
                        const win = apexOwnWin(apexOwnDoc(cur));
                        const style = win.getComputedStyle ? win.getComputedStyle(cur) : null;
                        if (style) {
                            if (style.display === 'none' || style.visibility === 'hidden') return false;
                            if (style.opacity !== '' && parseFloat(style.opacity) === 0) return false;
                            if (style.overflow === 'hidden' && style.maxHeight === '0px') return false;
                            if (apexIsSheetOrDialogNode(cur) && (style.position === 'fixed' || style.position === 'absolute') && !cur.hasAttribute('data-offscreen')) {
                                const cr = cur.getBoundingClientRect ? cur.getBoundingClientRect() : null;
                                const vw = (win && win.innerWidth) || rootVw;
                                const vh = (win && win.innerHeight) || rootVh;
                                if (cr && (cr.width === 0 || cr.height === 0 || cr.top >= vh - 1 || cr.bottom <= 1 || cr.left >= vw - 1 || cr.right <= 1)) return false;
                            }
                        }
                    } catch (_) {}
                    const rootNode = cur.getRootNode ? cur.getRootNode() : null;
                    cur = cur.parentElement || (rootNode && rootNode.host ? rootNode.host : null);
                    depth++;
                }
                try {
                    const rect = el.getBoundingClientRect ? el.getBoundingClientRect() : null;
                    if (rect && rect.width === 0 && rect.height === 0) {
                        const clients = el.getClientRects ? el.getClientRects() : null;
                        if (!clients || clients.length === 0) return false;
                    }
                } catch (_) {}
                return true;
            }
            function apexIsDisabled(el) {
                if (!el) return true;
                if (Boolean(el.disabled)) return true;
                if (String(el.getAttribute && el.getAttribute('aria-disabled') || '').toLowerCase() === 'true') return true;
                if (el.closest && el.closest('[inert], fieldset[disabled]')) return true;
                return false;
            }
            function apexIsReadOnly(el) {
                if (!el) return false;
                if (Boolean(el.readOnly)) return true;
                if (String(el.getAttribute && el.getAttribute('aria-readonly') || '').toLowerCase() === 'true') return true;
                return false;
            }
            function apexIsCustomPickerOrDropdown(el) {
                if (!el || !el.getAttribute) return false;
                const tag = (el.tagName || '').toLowerCase();
                const role = String(el.getAttribute('role') || '').toLowerCase();
                if (tag === 'select' || tag === 'option') return true;
                if (role === 'combobox' || role === 'listbox' || role === 'grid' || role === 'dialog' || role === 'menu' || role === 'application' ||
                    role === 'option' || role === 'menuitem' || role === 'menuitemradio' || role === 'menuitemcheckbox') return true;
                if (el.hasAttribute('aria-haspopup') || el.hasAttribute('aria-controls') || el.hasAttribute('aria-expanded')) return true;
                if (el.hasAttribute('data-datepicker') || el.hasAttribute('data-timepicker') || el.hasAttribute('data-time-picker') ||
                    el.hasAttribute('data-calendar') || el.hasAttribute('data-year-picker') || el.hasAttribute('data-birth-year') ||
                    el.hasAttribute('data-year') || el.hasAttribute('data-selected-date') || el.hasAttribute('data-time') || el.hasAttribute('data-slot') ||
                    el.hasAttribute('data-bottom-sheet') || el.hasAttribute('data-sheet') || el.hasAttribute('data-drawer')) return true;
                const cls = String(el.className || '').toLowerCase();
                if (/(datepicker|date-picker|timepicker|time-picker|flatpickr|year-picker|year-select|custom-select|dropdown-trigger|combobox|bottom-sheet|action-sheet)/.test(cls)) return true;
                return false;
            }
            function apexIsSensitiveEl(el) {
                if (!el) return false;
                const tag = (el.tagName || '').toLowerCase();
                const type = (el.getAttribute && el.getAttribute('type') || '').toLowerCase();
                if (tag === 'input' && type === 'password') return true;
                const hint = [
                    type,
                    el.getAttribute && el.getAttribute('name') || '',
                    el.id || '',
                    el.getAttribute && el.getAttribute('autocomplete') || '',
                    el.getAttribute && el.getAttribute('aria-label') || ''
                ].join(' ').toLowerCase();
                return /(password|passcode|pin\b|secret|token|otp|one-time-code|cvv|cvc|csc|cc-number|cc-csc|ssn|routing)/i.test(hint);
            }
            function apexDetectSecurityChallenge() {
                try {
                    const challengeEl = document.querySelector('[data-captcha-challenge="true"], .g-recaptcha, .h-captcha, .cf-turnstile, iframe[src*="recaptcha"], iframe[src*="turnstile"], iframe[src*="hcaptcha"]');
                    if (challengeEl && apexIsVisible(challengeEl)) {
                        return true;
                    }
                } catch (_) {}
                return false;
            }
            function apexGetFieldGroupLabel(el) {
                if (!el || !el.tagName) return '';
                const tag = (el.tagName || '').toLowerCase();
                const role = (el.getAttribute && el.getAttribute('role') || '').toLowerCase();
                if (tag === 'button' || tag === 'a' || tag === 'summary' || tag === 'option' || tag === 'label' ||
                    role === 'button' || role === 'link' || role === 'option' || role === 'menuitem') {
                    return '';
                }
                const type = (el.getAttribute && el.getAttribute('type') || '').toLowerCase();
                if (type === 'submit' || type === 'button' || type === 'reset' || type === 'hidden') return '';
                const doc = apexOwnDoc(el);
                function extractCandidate(node, requireExplicit) {
                    if (!node || node.nodeType !== 1 || node === el || (node.contains && node.contains(el))) return '';
                    const nTag = (node.tagName || '').toLowerCase();
                    if (nTag === 'h1' || nTag === 'h2' || nTag === 'h3' || nTag === 'h4' || nTag === 'h5' || nTag === 'h6' ||
                        nTag === 'button' || nTag === 'a' || nTag === 'input' || nTag === 'select' || nTag === 'textarea' || nTag === 'form') {
                        return '';
                    }
                    const forAttr = node.getAttribute && node.getAttribute('for');
                    if (forAttr && (!el.id || forAttr !== el.id) && doc && doc.getElementById && doc.getElementById(forAttr)) return '';
                    if (node.querySelector && node.querySelector('input:not([type="hidden"]), select, textarea, button, a[href]')) return '';
                    const nCls = String(node.className || '').toLowerCase();
                    const isExplicit = nTag === 'label' || nTag === 'legend' || /(?:^|[\s_-])(label|field-label|form-label|control-label|input-label)(?:$|[\s_-])/.test(nCls);
                    if (requireExplicit && !isExplicit && ((nTag !== 'span' && nTag !== 'div') || nCls.indexOf('label') < 0)) return '';
                    const raw = (node.innerText || node.textContent || '').replace(/\s+/g, ' ').trim();
                    if (!raw || raw.length > 48) return '';
                    if (!isExplicit && /[.!?]$/.test(raw)) return '';
                    return raw;
                }
                try {
                    const prev = el.previousElementSibling;
                    if (prev) {
                        const pCtrls = el.parentElement && el.parentElement.querySelectorAll
                            ? el.parentElement.querySelectorAll('input:not([type="hidden"]):not([type="submit"]):not([type="button"]), select, textarea').length
                            : 2;
                        const pTxt = extractCandidate(prev, pCtrls > 1);
                        if (pTxt) return pTxt;
                    }
                    const next = el.nextElementSibling;
                    if (next) {
                        const nTxt = extractCandidate(next, true);
                        if (nTxt) return nTxt;
                    }
                    let cur = el.parentElement;
                    let depth = 0;
                    while (cur && cur.nodeType === 1 && depth < 3) {
                        const cTag = (cur.tagName || '').toLowerCase();
                        if (cTag === 'form' || cTag === 'body' || cTag === 'html' || cTag === 'main' || cTag === 'section' || cTag === 'fieldset') break;
                        const ctrls = cur.querySelectorAll
                            ? cur.querySelectorAll('input:not([type="hidden"]):not([type="submit"]):not([type="button"]), select, textarea, [role="combobox"], [role="listbox"]')
                            : [];
                        if (ctrls.length <= 1) {
                            const children = cur.children ? Array.from(cur.children) : [];
                            let sawEl = false;
                            for (let i = 0; i < children.length; i++) {
                                const child = children[i];
                                if (child === el || (child.contains && child.contains(el))) {
                                    sawEl = true;
                                    continue;
                                }
                                const cTxt = extractCandidate(child, sawEl);
                                if (cTxt) return cTxt;
                            }
                            const cPrev = cur.previousElementSibling;
                            if (cPrev) {
                                const parentCtrls = cur.parentElement && cur.parentElement.querySelectorAll
                                    ? cur.parentElement.querySelectorAll('input:not([type="hidden"]):not([type="submit"]):not([type="button"]), select, textarea').length
                                    : 2;
                                const cpTxt = extractCandidate(cPrev, parentCtrls > 1);
                                if (cpTxt) return cpTxt;
                            }
                        }
                        cur = cur.parentElement;
                        depth++;
                    }
                } catch (_) {}
                return '';
            }
            function apexGetDirectSignalText(el) {
                if (!el) return '';
                const doc = apexOwnDoc(el);
                const root = (el.getRootNode && el.getRootNode()) || doc;
                const parts = [];
                try {
                    const aria = el.getAttribute && el.getAttribute('aria-label');
                    if (aria) parts.push(aria);
                    const lb = el.getAttribute && el.getAttribute('aria-labelledby');
                    if (lb) {
                        lb.split(/\s+/).forEach(function(refId) {
                            const ref = (root && root.getElementById ? root.getElementById(refId) : null) ||
                                (doc && doc.getElementById ? doc.getElementById(refId) : null);
                            if (ref) parts.push(ref.innerText || ref.textContent || '');
                        });
                    }
                    const db = el.getAttribute && el.getAttribute('aria-describedby');
                    if (db) {
                        db.split(/\s+/).forEach(function(refId) {
                            const ref = (root && root.getElementById ? root.getElementById(refId) : null) ||
                                (doc && doc.getElementById ? doc.getElementById(refId) : null);
                            if (ref) parts.push(ref.innerText || ref.textContent || '');
                        });
                    }
                    const ac = el.getAttribute && el.getAttribute('autocomplete');
                    if (ac) parts.push(ac);
                    const ph = el.getAttribute && el.getAttribute('placeholder');
                    if (ph) parts.push(ph);
                    const nm = el.getAttribute && el.getAttribute('name');
                    if (nm) parts.push(nm);
                    const ti = el.getAttribute && el.getAttribute('title');
                    if (ti) parts.push(ti);
                    if (el.id) {
                        parts.push(el.id);
                        const lbl = (root && root.querySelector ? root.querySelector('label[for="' + el.id.replace(/"/g, '\\"') + '"]') : null) ||
                            (doc && doc.querySelector ? doc.querySelector('label[for="' + el.id.replace(/"/g, '\\"') + '"]') : null);
                        if (lbl) parts.push(lbl.innerText || lbl.textContent || '');
                    }
                    const wrap = el.closest ? el.closest('label') : null;
                    if (wrap) parts.push(wrap.innerText || wrap.textContent || '');
                    const fgLbl = apexGetFieldGroupLabel(el);
                    if (fgLbl) parts.push(fgLbl);
                } catch (_) {}
                return apexNorm(parts.join(' '));
            }
            function apexIsUnrelatedNumericEl(el) {
                const sig = apexTokenizeSig(apexGetDirectSignalText(el));
                return /\b(zip|postal|postcode|phone|tel|mobile|otp|verification|passcode|pin|cvv|cvc|card|cc|ssn|tax|routing|account number|price|amount|salary|income|quantity|qty|ext|extension|street|apt|suite|floor|graduation|grad year|class of|vehicle|car year|model year|expir|cc exp)\b/i.test(sig);
            }
            function apexIsNonYearDatePartEl(el) {
                if (!el) return false;
                const ac = String(el.getAttribute && el.getAttribute('autocomplete') || '').toLowerCase();
                if (ac === 'bday-day' || ac === 'bday-month') return true;
                if (ac === 'bday-year') return false;
                const maxLen = parseInt(el.getAttribute && el.getAttribute('maxlength') || '0', 10);
                const ph = String(el.getAttribute && el.getAttribute('placeholder') || '').trim();
                if (maxLen === 4 || /^(yyyy|19\d{2}|20\d{2})$/i.test(ph)) return false;
                const tag = (el.tagName || '').toLowerCase();
                if (tag === 'select') {
                    const opts = Array.from(el.options || []);
                    let yrCnt = 0;
                    for (let i = 0; i < Math.min(opts.length, 40); i++) {
                        const ov = String(opts[i].value || '').trim();
                        const ot = String(opts[i].text || '').trim();
                        if (/^(19\d{2}|20\d{2})$/.test(ov) || /^(19\d{2}|20\d{2})$/.test(ot)) yrCnt++;
                    }
                    if (yrCnt >= 3) return false;
                }
                const sig = apexTokenizeSig(apexGetDirectSignalText(el));
                if (/\b(month|day)\b/i.test(sig) && !/\byear\b/i.test(sig)) return true;
                return false;
            }
            function apexParseDateParts(raw) {
                const s = String(raw || '').replace(/\s+/g, ' ').trim();
                if (!s) return null;
                function pad2(n) { return (n < 10 ? '0' : '') + n; }
                function makeParts(yStr, mNum, dNum) {
                    const y = parseInt(yStr, 10);
                    if (isNaN(y) || y < 1900 || y > 2099) return null;
                    if (mNum && (mNum < 1 || mNum > 12)) return null;
                    if (dNum && (dNum < 1 || dNum > 31)) return null;
                    return {
                        year: String(y),
                        month: mNum || 0,
                        day: dNum || 0,
                        iso: (mNum && dNum) ? (String(y) + '-' + pad2(mNum) + '-' + pad2(dNum)) : '',
                        isoMonth: mNum ? (String(y) + '-' + pad2(mNum)) : '',
                        raw: s
                    };
                }
                if (/^(19\d{2}|20\d{2})$/.test(s)) {
                    return makeParts(s, 0, 0);
                }
                const isoM = s.match(/^(19\d{2}|20\d{2})[\/\-.](\d{1,2})[\/\-.](\d{1,2})(?:\b.*)?$/);
                if (isoM) {
                    return makeParts(isoM[1], parseInt(isoM[2], 10), parseInt(isoM[3], 10));
                }
                const ymM = s.match(/^(19\d{2}|20\d{2})[\/\-.](\d{1,2})$/);
                if (ymM) {
                    return makeParts(ymM[1], parseInt(ymM[2], 10), 0);
                }
                const monthsMap = {
                    jan: 1, january: 1, feb: 2, february: 2, mar: 3, march: 3,
                    apr: 4, april: 4, may: 5, jun: 6, june: 6, jul: 7, july: 7,
                    aug: 8, august: 8, sep: 9, sept: 9, september: 9,
                    oct: 10, october: 10, nov: 11, november: 11, dec: 12, december: 12
                };
                const dmyNamed = s.match(/^(\d{1,2})(?:st|nd|rd|th)?[\s,.\-\/]+([a-zA-Z]{3,9})[\s,.\-\/]+(19\d{2}|20\d{2})$/i);
                if (dmyNamed) {
                    const mn = monthsMap[dmyNamed[2].toLowerCase()];
                    if (mn) return makeParts(dmyNamed[3], mn, parseInt(dmyNamed[1], 10));
                }
                const mdyNamed = s.match(/^([a-zA-Z]{3,9})[\s,.\-\/]+(\d{1,2})(?:st|nd|rd|th)?[\s,.\-\/]+(19\d{2}|20\d{2})$/i);
                if (mdyNamed) {
                    const mn = monthsMap[mdyNamed[1].toLowerCase()];
                    if (mn) return makeParts(mdyNamed[3], mn, parseInt(mdyNamed[2], 10));
                }
                const ymdNamed = s.match(/^(19\d{2}|20\d{2})[\s,.\-\/]+([a-zA-Z]{3,9})[\s,.\-\/]+(\d{1,2})(?:st|nd|rd|th)?$/i);
                if (ymdNamed) {
                    const mn = monthsMap[ymdNamed[2].toLowerCase()];
                    if (mn) return makeParts(ymdNamed[1], mn, parseInt(ymdNamed[3], 10));
                }
                const dmyNum = s.match(/^(\d{1,2})[\/\-.](\d{1,2})[\/\-.](\d{2}|\d{4})$/);
                if (dmyNum) {
                    const a = parseInt(dmyNum[1], 10);
                    const b = parseInt(dmyNum[2], 10);
                    let yRaw = dmyNum[3];
                    if (yRaw.length === 2) {
                        const yy = parseInt(yRaw, 10);
                        yRaw = (yy <= 29 ? '20' : '19') + (yy < 10 ? '0' + yy : String(yy));
                    }
                    if (b > 12 && a >= 1 && a <= 12) {
                        return makeParts(yRaw, a, b);
                    }
                    return makeParts(yRaw, b, a);
                }
                return null;
            }
            function apexDetectDateFieldRole(el) {
                if (!el || apexIsUnrelatedNumericEl(el)) return '';
                const tag = (el.tagName || '').toLowerCase();
                const type = String(el.getAttribute && el.getAttribute('type') || '').toLowerCase();
                if (tag === 'input' && type === 'date') return 'date-native';
                if (tag === 'input' && type === 'month') return 'month-native';
                const ac = String(el.getAttribute && el.getAttribute('autocomplete') || '').toLowerCase();
                if (ac === 'bday-year') return 'year';
                if (ac === 'bday-month') return 'month';
                if (ac === 'bday-day') return 'day';
                if (el.hasAttribute && (el.hasAttribute('data-year-picker') || el.hasAttribute('data-birth-year'))) return 'year';
                const ph = String(el.getAttribute && el.getAttribute('placeholder') || '').trim();
                const maxLen = parseInt(el.getAttribute && el.getAttribute('maxlength') || '0', 10);
                const minAttr = parseInt(el.getAttribute && el.getAttribute('min') || '0', 10);
                const maxAttr = parseInt(el.getAttribute && el.getAttribute('max') || '0', 10);
                const directSig = apexTokenizeSig(apexGetDirectSignalText(el));
                const fullSig = apexTokenizeSig(apexResolveLabel(el));
                const hasDobCtx = /(\bbirth\b|\bbday\b|\bdob\b|\bdate of birth\b|\bbirthday\b|\bbirthdate\b|\bborn\b|\bage verification\b|\bverify your age\b|\bconfirm your age\b)/.test(fullSig);
                if (tag === 'select') {
                    const opts = Array.from(el.options || []);
                    let yrCnt = 0;
                    let monthNameCnt = 0;
                    let numCount1to12 = 0;
                    let numMax = 0;
                    let numCnt = 0;
                    for (let i = 0; i < Math.min(opts.length, 48); i++) {
                        const ov = apexNorm(opts[i].value || '');
                        const ot = apexNorm(opts[i].text || '');
                        if (/^(19\d{2}|20\d{2})$/.test(ov) || /^(19\d{2}|20\d{2})$/.test(ot)) yrCnt++;
                        if (/^(jan|january|feb|february|mar|march|apr|april|may|jun|june|jul|july|aug|august|sep|sept|september|oct|october|nov|november|dec|december)\b/.test(ot)) monthNameCnt++;
                        const n = parseInt(ov || ot, 10);
                        if (!isNaN(n) && n >= 1 && n <= 31 && (/^\d{1,2}$/.test(ov) || /^\d{1,2}$/.test(ot))) {
                            numCnt++;
                            if (n <= 12) numCount1to12++;
                            if (n > numMax) numMax = n;
                        }
                    }
                    if (yrCnt >= 3 && (hasDobCtx || /\byear\b/.test(directSig))) return 'year';
                    if ((monthNameCnt >= 6 || (numCount1to12 >= 10 && numMax <= 12 && numCnt <= 13)) && (hasDobCtx || /\bmonth\b/.test(directSig))) return 'month';
                    if (numCnt >= 15 && numMax >= 28 && numMax <= 31 && (hasDobCtx || /\bday\b/.test(directSig))) return 'day';
                    if (yrCnt >= 10 && !/\b(expir|card|grad|vehicle|model)\b/.test(fullSig)) return 'year';
                }
                if (ac === 'bday') {
                    return (maxLen === 4 || /^(yyyy|19\d{2}|20\d{2})$/i.test(ph)) ? 'year' : 'full';
                }
                if (/^(yyyy[-/.]mm[-/.]dd|dd[-/.]mm[-/.]yyyy|mm[-/.]dd[-/.]yyyy)$/i.test(ph)) {
                    return 'full';
                }
                if (/^(yyyy|19\d{2}|20\d{2})$/i.test(ph) && (hasDobCtx || /\byear\b/.test(directSig))) return 'year';
                if (/^(mm)$/i.test(ph) && (hasDobCtx || /\bmonth\b/.test(directSig))) return 'month';
                if (/^(dd)$/i.test(ph) && (hasDobCtx || /\bday\b/.test(directSig))) return 'day';
                if (/(\bbirth year\b|\byear of birth\b|\bbday year\b|\bdob year\b|\bborn year\b|\bbirthyr\b)/.test(fullSig)) return 'year';
                if (/(\bbirth month\b|\bmonth of birth\b|\bbday month\b|\bdob month\b)/.test(fullSig)) return 'month';
                if (/(\bbirth day\b|\bday of birth\b|\bbday day\b|\bdob day\b)/.test(fullSig)) {
                    if (maxLen === 4 || (!isNaN(minAttr) && minAttr >= 1900)) return 'year';
                    return 'day';
                }
                if (hasDobCtx) {
                    if (/\byear\b/.test(directSig) || maxLen === 4 || (!isNaN(minAttr) && minAttr >= 1900 && maxAttr <= 2099)) return 'year';
                    if (/\bmonth\b/.test(directSig) && !/\bday\b|\byear\b/.test(directSig)) return 'month';
                    if (/\bday\b/.test(directSig) && !/\bmonth\b|\byear\b/.test(directSig)) return 'day';
                    return 'full';
                }
                return '';
            }
            function apexAdaptDateValueForTarget(target, rawVal) {
                const s = String(rawVal || '').trim();
                if (!s) return s;
                const parts = apexParseDateParts(s);
                if (!parts) return s;
                const role = apexDetectDateFieldRole(target);
                const tag = (target.tagName || '').toLowerCase();
                const type = String(target.getAttribute && target.getAttribute('type') || '').toLowerCase();
                const ph = String(target.getAttribute && target.getAttribute('placeholder') || '').trim();
                const maxLen = parseInt(target.getAttribute && target.getAttribute('maxlength') || '0', 10);
                function pad2(n) { return (n < 10 ? '0' : '') + n; }
                if (role === 'date-native') {
                    return parts.iso || s;
                }
                if (role === 'month-native') {
                    return parts.isoMonth || s;
                }
                if (role === 'year') {
                    return parts.year || s;
                }
                if (role === 'month' && parts.month > 0) {
                    if (tag === 'input' && type === 'number') return String(parts.month);
                    return maxLen === 1 ? String(parts.month) : pad2(parts.month);
                }
                if (role === 'day' && parts.day > 0) {
                    if (tag === 'input' && type === 'number') return String(parts.day);
                    return maxLen === 1 ? String(parts.day) : pad2(parts.day);
                }
                if (role === 'full' && parts.month > 0 && parts.day > 0) {
                    const phL = ph.toLowerCase();
                    if (/^yyyy[-/.]mm[-/.]dd$/.test(phL)) {
                        const sep = phL.indexOf('/') >= 0 ? '/' : (phL.indexOf('.') >= 0 ? '.' : '-');
                        return parts.year + sep + pad2(parts.month) + sep + pad2(parts.day);
                    }
                    if (/^mm[-/.]dd[-/.]yyyy$/.test(phL)) {
                        const sep = phL.indexOf('-') >= 0 ? '-' : (phL.indexOf('.') >= 0 ? '.' : '/');
                        return pad2(parts.month) + sep + pad2(parts.day) + sep + parts.year;
                    }
                    if (/^dd[-/.]mm[-/.]yyyy$/.test(phL)) {
                        const sep = phL.indexOf('-') >= 0 ? '-' : (phL.indexOf('.') >= 0 ? '.' : '/');
                        return pad2(parts.day) + sep + pad2(parts.month) + sep + parts.year;
                    }
                }
                return s;
            }
            function apexIsCountryCodeEl(el) {
                if (!el) return false;
                const ac = String(el.getAttribute && el.getAttribute('autocomplete') || '').toLowerCase();
                if (ac === 'tel-country-code') return true;
                if (el.hasAttribute && (el.hasAttribute('data-country-code') || el.hasAttribute('data-dial-code'))) return true;
                const sig = apexTokenizeSig(apexGetDirectSignalText(el));
                return /(\bcountry code\b|\bdial code\b|\bcalling code\b|\bphone code\b|\bisd\b|\btel country code\b|\bcountry prefix\b|\barea code prefix\b)/.test(sig);
            }
            function apexIsPhoneInputEl(el) {
                if (!el || apexIsCountryCodeEl(el)) return false;
                const tag = (el.tagName || '').toLowerCase();
                if (tag !== 'input') return false;
                const type = String(el.getAttribute && el.getAttribute('type') || '').toLowerCase();
                if (type === 'tel') return true;
                const ac = String(el.getAttribute && el.getAttribute('autocomplete') || '').toLowerCase();
                if (ac.indexOf('tel') === 0) return true;
                const sig = apexTokenizeSig(apexGetDirectSignalText(el));
                return /(\bphone\b|\bmobile\b|\btelephone\b|\bcell\b|\bwhatsapp\b|\bcontact number\b)/.test(sig) &&
                    !/(\bemail\b|\bpassword\b|\bzip\b|\bpostal\b|\bcode\b|\bverification\b|\botp\b)/.test(sig);
            }
            function apexHasSiblingCountryCode(el) {
                if (!el) return false;
                const container = el.closest ? (el.closest('form, fieldset, [role="group"]') || apexOwnDoc(el)) : apexOwnDoc(el);
                if (!container || !container.querySelectorAll) return false;
                const ctrls = container.querySelectorAll('select, input');
                for (let i = 0; i < ctrls.length; i++) {
                    const c = ctrls[i];
                    if (c !== el && apexIsVisible(c) && apexIsCountryCodeEl(c)) return true;
                }
                return false;
            }
            function apexExtractPhoneParts(rawVal) {
                const raw = String(rawVal || '').trim();
                const allDigits = raw.replace(/\D/g, '');
                let dialDigits = '';
                let nationalDigits = allDigits;
                const intlSepMatch = raw.match(/^\+(\d{1,3})[\s\-().]+(.+)$/);
                if (intlSepMatch) {
                    dialDigits = intlSepMatch[1];
                    nationalDigits = intlSepMatch[2].replace(/\D/g, '') || allDigits;
                } else if (raw.charAt(0) === '+' && allDigits.length > 10 && allDigits.length <= 14) {
                    const prefixLen = allDigits.length - 10;
                    if (prefixLen >= 1 && prefixLen <= 3) {
                        dialDigits = allDigits.slice(0, prefixLen);
                        nationalDigits = allDigits.slice(prefixLen);
                    }
                } else if (allDigits.length === 11 && allDigits.charAt(0) === '1') {
                    dialDigits = '1';
                    nationalDigits = allDigits.slice(1);
                }
                return { raw: raw, allDigits: allDigits, nationalDigits: nationalDigits, dialDigits: dialDigits };
            }
            function apexAdaptPhoneForInput(el, rawVal) {
                const parts = apexExtractPhoneParts(rawVal);
                if (!parts.allDigits) return String(rawVal || '');
                const type = String(el.getAttribute && el.getAttribute('type') || '').toLowerCase();
                const inputMode = String(el.getAttribute && el.getAttribute('inputmode') || '').toLowerCase();
                const pattern = String(el.getAttribute && el.getAttribute('pattern') || '');
                const ac = String(el.getAttribute && el.getAttribute('autocomplete') || '').toLowerCase();
                const maxLen = parseInt(el.getAttribute && el.getAttribute('maxlength') || '0', 10);
                const ph = String(el.getAttribute && el.getAttribute('placeholder') || '').trim();
                const isNumericOnly = type === 'number' ||
                    inputMode === 'numeric' ||
                    inputMode === 'decimal' ||
                    (pattern && /^\^?(\[0-9\]|\\d|\(\?:?\[0-9\]\))/.test(pattern) && pattern.indexOf('+') < 0 && pattern.indexOf('(') < 0);
                const hasSiblingCc = apexHasSiblingCountryCode(el);
                const wantsNational = hasSiblingCc || ac === 'tel-national' || (maxLen > 0 && maxLen <= parts.nationalDigits.length) ||
                    (parts.raw.length > 0 && maxLen > 0 && parts.raw.length > maxLen) ||
                    (/^\(?\d{3}\)?[-.\s]?\d{3}[-.\s]?\d{4}$/.test(ph) && parts.nationalDigits.length === 10);
                if (isNumericOnly) {
                    const chosenDigits = wantsNational ? parts.nationalDigits : parts.allDigits;
                    if (maxLen > 0 && chosenDigits.length > maxLen) {
                        if (parts.nationalDigits.length <= maxLen) return parts.nationalDigits;
                        return chosenDigits.slice(-maxLen);
                    }
                    return chosenDigits;
                }
                if (wantsNational) {
                    if (maxLen > 0 && parts.nationalDigits.length > maxLen) {
                        return parts.nationalDigits.slice(-maxLen);
                    }
                    return parts.nationalDigits;
                }
                if (maxLen > 0 && parts.raw.length > maxLen) {
                    return parts.nationalDigits.length <= maxLen ? parts.nationalDigits : parts.allDigits.slice(-maxLen);
                }
                return parts.raw;
            }
            function apexIsBirthYearCandidateEl(el) {
                if (!el || apexIsUnrelatedNumericEl(el) || apexIsNonYearDatePartEl(el)) return false;
                if (apexDetectDateFieldRole(el) === 'year') return true;
                const ac = String(el.getAttribute && el.getAttribute('autocomplete') || '').toLowerCase();
                if (ac === 'bday-year' || (el.hasAttribute && (el.hasAttribute('data-birth-year') || el.hasAttribute('data-year-picker')))) return true;
                const direct = apexTokenizeSig(apexGetDirectSignalText(el));
                const full = apexTokenizeSig(apexResolveLabel(el));
                if (/(\bbirth year\b|\byear of birth\b|\bbday year\b|\bdob year\b|\bborn year\b)/i.test(full)) return true;
                const hasDobCtx = /(\bbirth\b|\bbday\b|\bdob\b|\bdate of birth\b|\bbirthday\b|\bbirthdate\b|\bborn\b|\bconfirm your age\b|\bverify your age\b|\bage verification\b)/i.test(full);
                const ph = String(el.getAttribute && el.getAttribute('placeholder') || '').trim();
                const tag = (el.tagName || '').toLowerCase();
                if (hasDobCtx && (/\byear\b/i.test(direct) || /^(yyyy|19\d{2}|20\d{2})$/i.test(ph))) return true;
                if (tag === 'select') {
                    const opts = Array.from(el.options || []);
                    let yrCnt = 0;
                    for (let i = 0; i < Math.min(opts.length, 40); i++) {
                        const ov = String(opts[i].value || '').trim();
                        const ot = String(opts[i].text || '').trim();
                        if (/^(19\d{2}|20\d{2})$/.test(ov) || /^(19\d{2}|20\d{2})$/.test(ot)) yrCnt++;
                    }
                    if (yrCnt >= 2 && (hasDobCtx || /\byear\b/i.test(direct))) return true;
                }
                return false;
            }
            function apexResolveLabel(el) {
                const doc = apexOwnDoc(el);
                const root = (el.getRootNode && el.getRootNode()) || doc;
                let parts = [];
                try {
                    const aria = el.getAttribute && el.getAttribute('aria-label');
                    if (aria) parts.push(aria);
                    const lb = el.getAttribute && el.getAttribute('aria-labelledby');
                    if (lb) {
                        lb.split(/\s+/).forEach(function(refId) {
                            const ref = (root && root.getElementById ? root.getElementById(refId) : null) ||
                                (doc && doc.getElementById ? doc.getElementById(refId) : null);
                            if (ref) parts.push(ref.innerText || ref.textContent || '');
                        });
                    }
                    const db = el.getAttribute && el.getAttribute('aria-describedby');
                    if (db) {
                        db.split(/\s+/).forEach(function(refId) {
                            const ref = (root && root.getElementById ? root.getElementById(refId) : null) ||
                                (doc && doc.getElementById ? doc.getElementById(refId) : null);
                            if (ref) parts.push(ref.innerText || ref.textContent || '');
                        });
                    }
                    const ac = el.getAttribute && el.getAttribute('autocomplete');
                    if (ac) parts.push(ac);
                    const ph = el.getAttribute && el.getAttribute('placeholder');
                    if (ph) parts.push(ph);
                    const nm = el.getAttribute && el.getAttribute('name');
                    if (nm) parts.push(nm);
                    const ti = el.getAttribute && el.getAttribute('title');
                    if (ti) parts.push(ti);
                    if (el.id) {
                        parts.push(el.id);
                        const lbl = (root && root.querySelector ? root.querySelector('label[for="' + el.id.replace(/"/g, '\\"') + '"]') : null) ||
                            (doc && doc.querySelector ? doc.querySelector('label[for="' + el.id.replace(/"/g, '\\"') + '"]') : null);
                        if (lbl) parts.push(lbl.innerText || lbl.textContent || '');
                    }
                    const wrap = el.closest ? el.closest('label') : null;
                    if (wrap) parts.push(wrap.innerText || wrap.textContent || '');
                    const fgLbl = apexGetFieldGroupLabel(el);
                    if (fgLbl) parts.push(fgLbl);
                    const fs = el.closest ? el.closest('fieldset, [role="group"], [data-dob-group]') : null;
                    if (fs) {
                        const lg = fs.querySelector ? fs.querySelector('legend') : null;
                        if (lg) parts.push(lg.innerText || lg.textContent || '');
                        const fsAria = fs.getAttribute && fs.getAttribute('aria-label');
                        if (fsAria) parts.push(fsAria);
                        const fsLb = fs.getAttribute && fs.getAttribute('aria-labelledby');
                        if (fsLb) {
                            fsLb.split(/\s+/).forEach(function(refId) {
                                const ref = (root && root.getElementById ? root.getElementById(refId) : null) ||
                                    (doc && doc.getElementById ? doc.getElementById(refId) : null);
                                if (ref) parts.push(ref.innerText || ref.textContent || '');
                            });
                        }
                    }
                    const tag = (el.tagName || '').toLowerCase();
                    if (tag !== 'select') {
                        const vis = (el.innerText || el.textContent || '').trim();
                        if (vis) parts.push(vis);
                        else {
                            const alt = el.getAttribute && el.getAttribute('alt');
                            if (alt) parts.push(alt);
                            else if (el.querySelector) {
                                const imgEl = el.querySelector('img[alt]');
                                if (imgEl && imgEl.getAttribute('alt')) parts.push(imgEl.getAttribute('alt'));
                                const ariaDesc = el.querySelector('[aria-label]');
                                if (ariaDesc && ariaDesc.getAttribute('aria-label')) parts.push(ariaDesc.getAttribute('aria-label'));
                            }
                            if (!apexIsSensitiveEl(el) && el.value) parts.push(el.value);
                        }
                    }
                } catch (_) {}
                return apexNorm(parts.join(' '));
            }
            function apexScoreInputAttributes(el, hintNorm, hintCompact) {
                if (!el || !hintNorm) return { score: 0, matchedAttrs: 0, isLabeledInput: false };
                const tag = (el.tagName || '').toLowerCase();
                const typeAttr = apexNorm(el.getAttribute && el.getAttribute('type'));
                const role = apexNorm(el.getAttribute && el.getAttribute('role'));
                const isInput = (tag === 'input' && typeAttr !== 'hidden' && typeAttr !== 'submit' && typeAttr !== 'button' && typeAttr !== 'reset' && typeAttr !== 'image') ||
                    tag === 'textarea' || tag === 'select' || el.isContentEditable ||
                    role === 'textbox' || role === 'searchbox' || role === 'combobox' || role === 'listbox' || role === 'spinbutton';
                const accName = apexNorm(apexDescribeAccessibleName(el));
                const ariaLabel = apexNorm(el.getAttribute && el.getAttribute('aria-label'));
                const nameAttr = apexNorm(el.getAttribute && el.getAttribute('name'));
                const idAttr = apexNorm(el.id);
                const placeholder = apexNorm(el.getAttribute && el.getAttribute('placeholder'));
                const isLabeledInput = isInput && Boolean(accName || ariaLabel || placeholder);

                function evalSig(sig, wExact, wComp, wSub) {
                    if (!sig) return 0;
                    if (sig === hintNorm) return wExact;
                    const cSig = apexNormCompact(sig);
                    if (hintCompact && cSig === hintCompact) return wComp;
                    if (sig.indexOf(hintNorm) >= 0 || (hintCompact.length >= 4 && cSig.indexOf(hintCompact) >= 0)) return wSub;
                    return 0;
                }
                let score = 0;
                let matchedAttrs = 0;
                const sAcc = evalSig(accName, 95, 86, 62);
                const sAria = evalSig(ariaLabel, 34, 28, 18);
                const sName = evalSig(nameAttr, 30, 26, 16);
                const sId = evalSig(idAttr, 28, 24, 15);
                const sPh = evalSig(placeholder, 30, 26, 16);
                const sType = evalSig(typeAttr, 24, 20, 14);
                if (sAcc > 0) matchedAttrs++;
                if (sAria > 0) matchedAttrs++;
                if (sName > 0) matchedAttrs++;
                if (sId > 0) matchedAttrs++;
                if (sPh > 0) matchedAttrs++;
                if (sType > 0) matchedAttrs++;
                score += sAcc + sAria + sName + sId + sPh + sType;
                if (isInput && score > 0) {
                    if (hintNorm.indexOf('email') >= 0 && typeAttr === 'email') score += 26;
                    else if ((hintNorm.indexOf('password') >= 0 || hintNorm.indexOf('passcode') >= 0) && typeAttr === 'password') score += 28;
                    else if ((hintNorm.indexOf('phone') >= 0 || hintNorm.indexOf('mobile') >= 0) && typeAttr === 'tel') score += 24;
                    else if ((hintNorm.indexOf('website') >= 0 || hintNorm.indexOf('url') >= 0) && typeAttr === 'url') score += 24;
                    else if (hintNorm.indexOf('search') >= 0 && typeAttr === 'search') score += 22;
                    if (isLabeledInput) score += 24 + (Math.min(matchedAttrs, 4) * 8);
                }
                return { score: score, matchedAttrs: matchedAttrs, isLabeledInput: isLabeledInput };
            }
            function apexDescribeAccessibleName(el) {
                const doc = apexOwnDoc(el);
                const root = (el.getRootNode && el.getRootNode()) || doc;
                try {
                    const lb = el.getAttribute && el.getAttribute('aria-labelledby');
                    if (lb) {
                        const txt = lb.split(/\s+/).map(function(id) {
                            const ref = (root && root.getElementById ? root.getElementById(id) : null) || (doc && doc.getElementById ? doc.getElementById(id) : null);
                            return ref ? (ref.innerText || ref.textContent || '').trim() : '';
                        }).filter(Boolean).join(' ');
                        if (txt) return txt.slice(0, 100);
                    }
                    const aria = el.getAttribute && el.getAttribute('aria-label');
                    if (aria && aria.trim()) return aria.trim().slice(0, 100);
                    if (el.id) {
                        const lbl = (root && root.querySelector ? root.querySelector('label[for="' + apexEscapeAttr(el.id) + '"]') : null) ||
                            (doc && doc.querySelector ? doc.querySelector('label[for="' + apexEscapeAttr(el.id) + '"]') : null);
                        if (lbl && (lbl.innerText || lbl.textContent || '').trim()) {
                            return (lbl.innerText || lbl.textContent).replace(/\s+/g, ' ').trim().slice(0, 100);
                        }
                    }
                    const wrap = el.closest ? el.closest('label') : null;
                    if (wrap && (wrap.innerText || wrap.textContent || '').trim()) {
                        return (wrap.innerText || wrap.textContent).replace(/\s+/g, ' ').trim().slice(0, 100);
                    }
                    const fgLbl = apexGetFieldGroupLabel(el);
                    if (fgLbl) return fgLbl.slice(0, 100);
                    const ph = el.getAttribute && el.getAttribute('placeholder');
                    if (ph && ph.trim()) return ph.trim().slice(0, 100);
                    const db = el.getAttribute && el.getAttribute('aria-describedby');
                    if (db) {
                        const dbTxt = db.split(/\s+/).map(function(id) {
                            const ref = (root && root.getElementById ? root.getElementById(id) : null) || (doc && doc.getElementById ? doc.getElementById(id) : null);
                            return ref ? (ref.innerText || ref.textContent || '').trim() : '';
                        }).filter(Boolean).join(' ');
                        if (dbTxt) return dbTxt.slice(0, 100);
                    }
                    const tag = (el.tagName || '').toLowerCase();
                    if (tag !== 'select') {
                        const txt = (el.innerText || el.textContent || '').replace(/\s+/g, ' ').trim();
                        if (txt) return txt.slice(0, 100);
                        const alt = el.getAttribute && el.getAttribute('alt');
                        if (alt && alt.trim()) return alt.trim().slice(0, 100);
                        if (el.querySelector) {
                            const imgEl = el.querySelector('img[alt]');
                            if (imgEl && imgEl.getAttribute('alt')) return imgEl.getAttribute('alt').trim().slice(0, 100);
                            const ariaDesc = el.querySelector('[aria-label]');
                            if (ariaDesc && ariaDesc.getAttribute('aria-label')) return ariaDesc.getAttribute('aria-label').trim().slice(0, 100);
                            const svgTitle = el.querySelector('svg title');
                            if (svgTitle && (svgTitle.textContent || '').trim()) return svgTitle.textContent.trim().slice(0, 100);
                        }
                    }
                    const nm = el.getAttribute && el.getAttribute('name');
                    if (nm && nm.trim()) return nm.trim().slice(0, 100);
                } catch (_) {}
                return '';
            }
            function apexGetParentContext(el) {
                try {
                    const parts = [];
                    let p = el.parentElement;
                    let steps = 0;
                    while (p && p.nodeType === 1 && steps < 6 && parts.length < 2) {
                        const pt = (p.tagName || '').toLowerCase();
                        if (pt === 'form' || pt === 'header' || pt === 'nav' || pt === 'footer' || pt === 'main' || pt === 'aside' || pt === 'section' || pt === 'fieldset' ||
                            apexIsSheetOrDialogNode(p) || p.getAttribute('role') || p.getAttribute('aria-label') || apexIsStableId(p.id)) {
                            let desc = pt;
                            if (apexIsStableId(p.id)) desc += '#' + p.id;
                            else if (p.getAttribute('data-testid')) desc += '[data-testid="' + p.getAttribute('data-testid') + '"]';
                            else if (p.hasAttribute('data-bottom-sheet')) desc += '[data-bottom-sheet]';
                            else if (p.hasAttribute('data-sheet')) desc += '[data-sheet]';
                            else if (p.hasAttribute('data-drawer')) desc += '[data-drawer]';
                            else if (p.hasAttribute('data-calendar')) desc += '[data-calendar]';
                            else if (p.getAttribute('role')) desc += '[role="' + p.getAttribute('role') + '"]';
                            if (p.getAttribute('aria-label')) desc += ' "' + p.getAttribute('aria-label').slice(0, 32) + '"';
                            parts.unshift(desc);
                        }
                        p = p.parentElement;
                        steps++;
                    }
                    return parts.join(' > ').slice(0, 120);
                } catch (_) {
                    return '';
                }
            }
            function apexGetNearbyText(el) {
                try {
                    const fs = el.closest ? el.closest('fieldset') : null;
                    if (fs) {
                        const lg = fs.querySelector('legend');
                        if (lg && (lg.innerText || lg.textContent || '').trim()) {
                            return (lg.innerText || lg.textContent).replace(/\s+/g, ' ').trim().slice(0, 80);
                        }
                    }
                    let sib = el.previousElementSibling;
                    let tries = 0;
                    while (sib && tries < 2) {
                        if (!apexIsSensitiveEl(sib)) {
                            const txt = (sib.innerText || sib.textContent || '').replace(/\s+/g, ' ').trim();
                            if (txt) return txt.slice(0, 80);
                        }
                        sib = sib.previousElementSibling;
                        tries++;
                    }
                    if (el.parentElement && el.parentElement.previousElementSibling && !apexIsSensitiveEl(el.parentElement.previousElementSibling)) {
                        const ptxt = (el.parentElement.previousElementSibling.innerText || el.parentElement.previousElementSibling.textContent || '').replace(/\s+/g, ' ').trim();
                        if (ptxt) return ptxt.slice(0, 80);
                    }
                    const overlay = el.closest ? el.closest('dialog, [role="dialog"], [aria-modal="true"], [data-bottom-sheet], [data-sheet], [data-drawer], [data-calendar], [data-datepicker], .bottom-sheet, .modal, .calendar, .datepicker') : null;
                    if (overlay && overlay.querySelector) {
                        const hd = overlay.querySelector('.sheet-title, .modal-title, .calendar-title, [class*="month-year"], [role="heading"], h1, h2, h3');
                        if (hd && hd !== el && !apexIsSensitiveEl(hd)) {
                            const htxt = (hd.innerText || hd.textContent || '').replace(/\s+/g, ' ').trim();
                            if (htxt) return htxt.slice(0, 80);
                        }
                    }
                } catch (_) {}
                return '';
            }
            function apexGetContextsForEl(el) {
                const framePath = [];
                const shadowPath = [];
                try {
                    let rootNode = el.getRootNode ? el.getRootNode() : apexOwnDoc(el);
                    let guard = 0;
                    while (typeof ShadowRoot !== 'undefined' && rootNode instanceof ShadowRoot && rootNode.host && guard < 8) {
                        const h = rootNode.host;
                        const ht = (h.tagName || '').toLowerCase();
                        const hs = apexIsStableId(h.id) ? (ht + '#' + apexEscapeCss(h.id)) :
                            (h.getAttribute('data-testid') ? (ht + '[data-testid="' + apexEscapeAttr(h.getAttribute('data-testid')) + '"]') : ht);
                        shadowPath.unshift(hs);
                        rootNode = h.getRootNode ? h.getRootNode() : apexOwnDoc(h);
                        guard++;
                    }
                    let win = apexOwnWin(apexOwnDoc(el));
                    guard = 0;
                    while (win && win !== window && win.frameElement && guard < 6) {
                        const f = win.frameElement;
                        const fid = f.id || '';
                        const fname = f.getAttribute && f.getAttribute('name') || '';
                        const fsel = apexIsStableId(fid) ? ('iframe#' + apexEscapeCss(fid)) :
                            (fname ? ('iframe[name="' + apexEscapeAttr(fname) + '"]') : 'iframe');
                        framePath.unshift(fsel);
                        win = win.parent;
                        guard++;
                    }
                } catch (_) {}
                return { framePath: framePath, shadowHostPath: shadowPath };
            }
            function apexBuildRecommendedSelector(el) {
                const root = (el.getRootNode && el.getRootNode()) || apexOwnDoc(el);
                const tag = (el.tagName || '').toLowerCase() || '*';
                function isUniqueLocal(sel) {
                    try { return root.querySelectorAll(sel).length === 1; } catch (_) { return false; }
                }
                const cands = [];
                if (apexIsStableId(el.id)) cands.push('#' + apexEscapeCss(el.id));
                [
                    'data-testid', 'data-test', 'data-qa', 'data-cy', 'data-action',
                    'data-date', 'data-time', 'data-slot', 'data-country-code', 'data-dial-code',
                    'data-code', 'data-day', 'data-month', 'data-year', 'data-gender',
                    'data-value', 'data-option', 'data-tab', 'data-dismiss'
                ].forEach(function(a) {
                    const v = el.getAttribute && el.getAttribute(a);
                    if (v) cands.push(tag + '[' + a + '="' + apexEscapeAttr(v) + '"]');
                });
                const aria = el.getAttribute && el.getAttribute('aria-label');
                const role = el.getAttribute && el.getAttribute('role');
                if (aria) {
                    if (role) cands.push(tag + '[role="' + apexEscapeAttr(role) + '"][aria-label="' + apexEscapeAttr(aria) + '"]');
                    cands.push(tag + '[aria-label="' + apexEscapeAttr(aria) + '"]');
                }
                const nameAttr = el.getAttribute && el.getAttribute('name');
                if (nameAttr) cands.push(tag + '[name="' + apexEscapeAttr(nameAttr) + '"]');
                const hrefAttr = tag === 'a' && el.getAttribute ? el.getAttribute('href') : '';
                if (hrefAttr && hrefAttr !== '#' && hrefAttr.indexOf('javascript:') !== 0) {
                    cands.push('a[href="' + apexEscapeAttr(hrefAttr) + '"]');
                }
                const phAttr = el.getAttribute && el.getAttribute('placeholder');
                if (phAttr) cands.push(tag + '[placeholder="' + apexEscapeAttr(phAttr) + '"]');

                for (let i = 0; i < cands.length; i++) {
                    if (isUniqueLocal(cands[i])) {
                        const ctx = apexGetContextsForEl(el);
                        const prefix = ctx.shadowHostPath.length > 0 ? (ctx.shadowHostPath.join(' >>> ') + ' >>> ') :
                            (ctx.framePath.length > 0 ? (ctx.framePath.join(' >> ') + ' >> ') : '');
                        return prefix + cands[i];
                    }
                }
                // Container-scoped refinement
                let anc = el.parentElement;
                let aSteps = 0;
                while (anc && anc.nodeType === 1 && aSteps < 6) {
                    const at = (anc.tagName || '').toLowerCase();
                    const aRole = (anc.getAttribute && anc.getAttribute('role') || '').toLowerCase();
                    let aSel = '';
                    if (apexIsStableId(anc.id)) aSel = at + '#' + apexEscapeCss(anc.id);
                    else if (anc.getAttribute && anc.getAttribute('data-testid')) aSel = at + '[data-testid="' + apexEscapeAttr(anc.getAttribute('data-testid')) + '"]';
                    else if (anc.hasAttribute && anc.hasAttribute('data-bottom-sheet')) aSel = at + '[data-bottom-sheet]';
                    else if (anc.hasAttribute && anc.hasAttribute('data-sheet')) aSel = at + '[data-sheet]';
                    else if (anc.hasAttribute && anc.hasAttribute('data-drawer')) aSel = at + '[data-drawer]';
                    else if (anc.hasAttribute && anc.hasAttribute('data-datepicker')) aSel = at + '[data-datepicker]';
                    else if (anc.hasAttribute && anc.hasAttribute('data-calendar')) aSel = at + '[data-calendar]';
                    else if (anc.hasAttribute && anc.hasAttribute('aria-modal') && anc.getAttribute('aria-modal') === 'true') aSel = at + '[aria-modal="true"]';
                    else if (aRole === 'dialog' || aRole === 'alertdialog' || aRole === 'listbox' || aRole === 'menu' || aRole === 'grid') aSel = at + '[role="' + apexEscapeAttr(aRole) + '"]';
                    else if (at === 'dialog') aSel = anc.hasAttribute('open') ? 'dialog[open]' : 'dialog';
                    else if (anc.classList && anc.classList.contains('bottom-sheet')) aSel = at + '.bottom-sheet';
                    else if (anc.classList && anc.classList.contains('modal')) aSel = at + '.modal';
                    else if (at === 'form' || at === 'header' || at === 'nav' || at === 'footer' || at === 'main' || at === 'aside') aSel = at;
                    if (aSel) {
                        for (let i = 0; i < cands.length; i++) {
                            const scoped = aSel + ' ' + cands[i];
                            if (isUniqueLocal(scoped)) {
                                const ctx = apexGetContextsForEl(el);
                                const prefix = ctx.shadowHostPath.length > 0 ? (ctx.shadowHostPath.join(' >>> ') + ' >>> ') :
                                    (ctx.framePath.length > 0 ? (ctx.framePath.join(' >> ') + ' >> ') : '');
                                return prefix + scoped;
                            }
                        }
                    }
                    anc = anc.parentElement;
                    aSteps++;
                }
                // Structural CSS path fallback (up to 8 levels) - only break when uniquely matching
                const segs = [];
                let cur = el;
                let lvl = 0;
                while (cur && cur.nodeType === 1 && lvl < 8) {
                    const ct = (cur.tagName || '').toLowerCase();
                    if (!ct || ct === 'html' || ct === 'body') break;
                    let seg = '';
                    let anchored = false;
                    if (apexIsStableId(cur.id)) {
                        const idSeg = (cur === el && segs.length === 0) ? ('#' + apexEscapeCss(cur.id)) : (ct + '#' + apexEscapeCss(cur.id));
                        if (isUniqueLocal([idSeg].concat(segs).join(' > '))) {
                            segs.unshift(idSeg);
                            break;
                        }
                        seg = idSeg;
                        anchored = true;
                    } else {
                        const dtVal = cur.getAttribute && (cur.getAttribute('data-testid') || cur.getAttribute('data-test'));
                        if (dtVal) {
                            const dtAttr = cur.getAttribute('data-testid') ? 'data-testid' : 'data-test';
                            const dtSeg = ct + '[' + dtAttr + '="' + apexEscapeAttr(dtVal) + '"]';
                            if (isUniqueLocal([dtSeg].concat(segs).join(' > '))) {
                                segs.unshift(dtSeg);
                                break;
                            }
                            seg = dtSeg;
                            anchored = true;
                        }
                    }
                    if (!anchored) {
                        let nth = 1;
                        let s = cur.previousElementSibling;
                        while (s) {
                            if ((s.tagName || '').toLowerCase() === ct) nth++;
                            s = s.previousElementSibling;
                        }
                        let sameAfter = false;
                        let ns = cur.nextElementSibling;
                        while (ns) {
                            if ((ns.tagName || '').toLowerCase() === ct) { sameAfter = true; break; }
                            ns = ns.nextElementSibling;
                        }
                        seg = (nth > 1 || sameAfter) ? (ct + ':nth-of-type(' + nth + ')') : ct;
                    }
                    segs.unshift(seg);
                    if (isUniqueLocal(segs.join(' > '))) break;
                    cur = cur.parentElement;
                    lvl++;
                }
                const localFinal = segs.join(' > ') || tag;
                const ctx = apexGetContextsForEl(el);
                const prefix = ctx.shadowHostPath.length > 0 ? (ctx.shadowHostPath.join(' >>> ') + ' >>> ') :
                    (ctx.framePath.length > 0 ? (ctx.framePath.join(' >> ') + ' >> ') : '');
                return prefix + localFinal;
            }
            function apexCollectCandidateInfo(el, index) {
                const tag = (el.tagName || '').toLowerCase();
                const isSensitive = apexIsSensitiveEl(el);
                const role = (el.getAttribute && el.getAttribute('role') || '').toLowerCase() || (tag === 'a' ? 'link' : tag);
                const accessibleName = apexDescribeAccessibleName(el);
                const visibleText = isSensitive ? '' : (el.innerText || el.textContent || '').replace(/\s+/g, ' ').trim().slice(0, 100);
                const safeAttrs = {};
                const allowed = ['type', 'role', 'name', 'href', 'placeholder', 'aria-label', 'data-testid', 'data-test', 'data-qa', 'data-cy', 'data-action', 'data-date', 'data-time', 'data-slot', 'data-country-code', 'data-dial-code', 'data-value', 'data-option', 'data-dismiss'];
                allowed.forEach(function(k) {
                    if (isSensitive && k === 'placeholder') return;
                    const v = el.getAttribute && el.getAttribute(k);
                    if (v && String(v).trim()) safeAttrs[k] = String(v).trim().slice(0, 120);
                });
                if (apexIsStableId(el.id)) safeAttrs.id = String(el.id).trim();
                const ctx = apexGetContextsForEl(el);
                return {
                    index: index,
                    tagName: tag,
                    role: role,
                    accessibleName: accessibleName,
                    visibleText: visibleText,
                    attributes: safeAttrs,
                    parentContext: apexGetParentContext(el),
                    nearbyText: isSensitive ? '' : apexGetNearbyText(el),
                    framePath: ctx.framePath,
                    shadowHostPath: ctx.shadowHostPath,
                    isVisible: apexIsVisible(el),
                    isEnabled: !apexIsDisabled(el),
                    recommendedSelector: apexBuildRecommendedSelector(el)
                };
            }
            function apexQueryChain(fullSelector) {
                const raw = String(fullSelector || '').trim();
                if (!raw) return { ok: false, code: 'INVALID_SELECTOR', matches: [] };
                try {
                    const hasChain = raw.indexOf('>>') >= 0;
                    if (!hasChain) {
                        return { ok: true, matches: Array.from(document.querySelectorAll(raw)), doc: document };
                    }
                    const tokens = [];
                    let idx = 0;
                    let pendingOp = 'ROOT';
                    while (idx < raw.length) {
                        const nextTriple = raw.indexOf('>>>', idx);
                        const nextDouble = raw.indexOf('>>', idx);
                        let splitAt = -1;
                        let opLen = 0;
                        let nextOp = '';
                        if (nextTriple >= 0 && (nextDouble < 0 || nextTriple <= nextDouble)) {
                            splitAt = nextTriple;
                            opLen = 3;
                            nextOp = 'SHADOW';
                        } else if (nextDouble >= 0) {
                            splitAt = nextDouble;
                            opLen = 2;
                            nextOp = 'IFRAME';
                        }
                        if (splitAt < 0) {
                            tokens.push({ op: pendingOp, sel: raw.slice(idx).trim() });
                            break;
                        } else {
                            tokens.push({ op: pendingOp, sel: raw.slice(idx, splitAt).trim() });
                            pendingOp = nextOp;
                            idx = splitAt + opLen;
                        }
                    }
                    let currentRoots = [document];
                    for (let t = 0; t < tokens.length; t++) {
                        const tok = tokens[t];
                        if (!tok.sel) return { ok: false, code: 'INVALID_SELECTOR', matches: [] };
                        if (t > 0) {
                            const nextRoots = [];
                            for (let r = 0; r < currentRoots.length; r++) {
                                const host = currentRoots[r];
                                if (tok.op === 'SHADOW') {
                                    if (host && host.shadowRoot) {
                                        nextRoots.push(host.shadowRoot);
                                    } else {
                                        return { ok: false, code: 'ELEMENT_NOT_FOUND', matches: [], msg: 'Open shadowRoot unavailable on host.' };
                                    }
                                } else if (tok.op === 'IFRAME') {
                                    try {
                                        const subDoc = host && (host.contentDocument || (host.contentWindow && host.contentWindow.document));
                                        if (!subDoc) {
                                            return { ok: false, code: 'CROSS_ORIGIN_BLOCKED', matches: [], msg: 'Iframe document is cross-origin or unloaded.' };
                                        }
                                        nextRoots.push(subDoc);
                                    } catch (_) {
                                        return { ok: false, code: 'CROSS_ORIGIN_BLOCKED', matches: [], msg: 'Blocked cross-origin iframe access.' };
                                    }
                                }
                            }
                            currentRoots = nextRoots;
                        }
                        const stepMatches = [];
                        for (let r = 0; r < currentRoots.length; r++) {
                            const found = Array.from(currentRoots[r].querySelectorAll(tok.sel));
                            for (let f = 0; f < found.length; f++) stepMatches.push(found[f]);
                        }
                        if (t < tokens.length - 1) {
                            if (stepMatches.length === 0) {
                                return { ok: false, code: 'ELEMENT_NOT_FOUND', matches: [] };
                            }
                            if (stepMatches.length > 1) {
                                return { ok: false, code: 'ELEMENT_AMBIGUOUS', matches: stepMatches, msg: 'Multiple container hosts matched "' + tok.sel + '".' };
                            }
                            currentRoots = stepMatches;
                        } else {
                            return { ok: true, matches: stepMatches };
                        }
                    }
                    return { ok: false, code: 'ELEMENT_NOT_FOUND', matches: [] };
                } catch (e) {
                    return { ok: false, code: 'INVALID_SELECTOR', matches: [], msg: 'Invalid selector "' + raw + '": ' + String(e && e.message ? e.message : e) };
                }
            }
            function apexDeepQueryAll(cssSel) {
                const collected = [];
                function walk(root, depth) {
                    if (!root || depth > 4) return;
                    try {
                        const found = root.querySelectorAll(cssSel);
                        for (let i = 0; i < found.length; i++) collected.push(found[i]);
                        const all = root.querySelectorAll('*');
                        for (let i = 0; i < all.length; i++) {
                            const node = all[i];
                            if (node.shadowRoot) walk(node.shadowRoot, depth + 1);
                            if ((node.tagName || '').toLowerCase() === 'iframe') {
                                try {
                                    const subDoc = node.contentDocument || (node.contentWindow && node.contentWindow.document);
                                    if (subDoc) walk(subDoc, depth + 1);
                                } catch (_) {}
                            }
                        }
                    } catch (_) {}
                }
                walk(document, 0);
                return collected;
            }
            function apexResolveWithCandidates(candidateList, labelHint, defaultQuery, opts) {
                const options = opts || {};
                const requireWritable = Boolean(options.requireWritable);
                const allowOffscreen = Boolean(options.allowOffscreen);
                const ignoreDisabled = Boolean(options.ignoreDisabled);
                const normHint = apexNorm(labelHint || $qLabel);
                const compactHint = apexNormCompact(normHint);
                const frameHint = $qFrameChain;
                const shadowHint = $qShadowChain;
                const hintPrefix = (Array.isArray(shadowHint) && shadowHint.length > 0) ? (shadowHint.join(' >>> ') + ' >>> ') :
                    ((Array.isArray(frameHint) && frameHint.length > 0) ? (frameHint.join(' >> ') + ' >> ') : '');

                const attempts = [];
                let sawAmbiguous = false;
                let ambiguousCount = 0;
                let ambiguousElements = [];
                let blockedCode = '';
                let blockedMsg = '';
                let chosen = null;
                let matchedVia = '';
                let usedFallback = false;

                function classifyStrategy(sel, idx) {
                    if (sel.indexOf('>>>') >= 0) return 'SHADOW_DOM_CHAIN';
                    if (sel.indexOf('>>') >= 0) return 'IFRAME_CHAIN';
                    if (sel.indexOf('#') === 0 || (sel.indexOf('#') > 0 && sel.indexOf(' ') < 0)) return 'STABLE_ID';
                    if (sel.indexOf('[data-test') >= 0 || sel.indexOf('[data-qa') >= 0 || sel.indexOf('[data-cy') >= 0) return 'STABLE_DATA_ATTR';
                    if (sel.indexOf('[aria-label') >= 0) return 'ARIA_ROLE_AND_NAME';
                    if (sel.indexOf('[name=') >= 0) return 'STABLE_NAME';
                    if (sel.indexOf('[href=') >= 0) return 'HREF_ATTR';
                    if (sel.indexOf('[placeholder=') >= 0) return 'PLACEHOLDER_ATTR';
                    return idx === 0 ? 'RECORDED_PRIMARY' : 'SCOPED_CSS';
                }

                const selectorQueue = [];
                const rawQueue = Array.isArray(candidateList) ? candidateList : [];
                for (let i = 0; i < rawQueue.length; i++) {
                    const s = String(rawQueue[i] || '').trim();
                    if (!s) continue;
                    const scoped = (hintPrefix && s.indexOf('>>') < 0) ? (hintPrefix + s) : s;
                    if (selectorQueue.indexOf(scoped) < 0) selectorQueue.push(scoped);
                    if (scoped !== s && selectorQueue.indexOf(s) < 0) selectorQueue.push(s);
                }

                for (let idx = 0; idx < selectorQueue.length; idx++) {
                    const sel = selectorQueue[idx];
                    const qRes = apexQueryChain(sel);
                    if (!qRes.ok) {
                        attempts.push({
                            strategy: classifyStrategy(sel, idx),
                            selector: sel,
                            matchCount: qRes.matches ? qRes.matches.length : 0,
                            visibleCount: 0,
                            interactableCount: 0,
                            selected: false,
                            rejectedReason: qRes.code || 'QUERY_FAILED'
                        });
                        if (qRes.code === 'CROSS_ORIGIN_BLOCKED' || qRes.code === 'ELEMENT_AMBIGUOUS') {
                            blockedCode = qRes.code;
                            blockedMsg = qRes.msg || '';
                            if (qRes.code === 'ELEMENT_AMBIGUOUS' && qRes.matches && qRes.matches.length > ambiguousElements.length) {
                                ambiguousElements = qRes.matches.slice(0, 12);
                                ambiguousCount = qRes.matches.length;
                                sawAmbiguous = true;
                            }
                        } else if (qRes.code === 'INVALID_SELECTOR' && selectorQueue.length === 1 && !normHint) {
                            return {
                                ok: false,
                                code: 'INVALID_SELECTOR',
                                msg: qRes.msg || 'Invalid selector',
                                diagnostics: {
                                    recordedLocator: selectorQueue[0] || '',
                                    targetLabel: labelHint || $qLabel,
                                    totalMatches: 0,
                                    visibleMatches: 0,
                                    interactableMatches: 0,
                                    finalOutcome: 'INVALID_SELECTOR',
                                    attemptedCandidates: attempts
                                }
                            };
                        }
                        continue;
                    }
                    const connected = qRes.matches.filter(function(el) { return apexIsConnected(el); });
                    let candidates = connected.filter(function(el) { return allowOffscreen || apexIsVisible(el); });
                    const interactableCnt = candidates.filter(function(el) { return !apexIsDisabled(el); }).length;

                    if (candidates.length === 0 && qRes.matches.length > 0 && !allowOffscreen) {
                        attempts.push({
                            strategy: classifyStrategy(sel, idx),
                            selector: sel,
                            matchCount: qRes.matches.length,
                            visibleCount: 0,
                            interactableCount: 0,
                            selected: false,
                            rejectedReason: 'NOT_VISIBLE'
                        });
                        blockedCode = 'ELEMENT_NOT_INTERACTABLE';
                        blockedMsg = 'Target "' + sel + '" is hidden or not interactable.';
                        continue;
                    }
                    const isBirthYearQuery = /(birth\s*year|year\s*of\s*birth|bday[-_]?year|birth[-_]?year|dob[-_]?year|born\s*year)/i.test(normHint);
                    if (candidates.length > 1 && isBirthYearQuery) {
                        const birthYearFiltered = candidates.filter(function(el) {
                            return apexIsBirthYearCandidateEl(el);
                        });
                        if (birthYearFiltered.length > 0) {
                            candidates = birthYearFiltered;
                        }
                    }
                    if (candidates.length > 1 && normHint) {
                        const exact = candidates.filter(function(el) {
                            return apexResolveLabel(el) === normHint || apexNorm(apexDescribeAccessibleName(el)) === normHint;
                        });
                        if (exact.length === 1) {
                            candidates = exact;
                        } else if (exact.length > 1) {
                            candidates = exact;
                        } else {
                            const compactMatched = compactHint ? candidates.filter(function(el) {
                                return apexNormCompact(apexResolveLabel(el)) === compactHint ||
                                    apexNormCompact(apexDescribeAccessibleName(el)) === compactHint;
                            }) : [];
                            if (compactMatched.length === 1) {
                                candidates = compactMatched;
                            } else if (compactMatched.length > 1) {
                                candidates = compactMatched;
                            } else {
                                const narrowed = candidates.filter(function(el) {
                                    const lbl = apexResolveLabel(el);
                                    return lbl.indexOf(normHint) >= 0 || (compactHint.length >= 4 && apexNormCompact(lbl).indexOf(compactHint) >= 0);
                                });
                                if (narrowed.length > 0) candidates = narrowed;
                            }
                        }
                        if (candidates.length > 1) {
                            const ranked = candidates.map(function(el) {
                                return { el: el, res: apexScoreInputAttributes(el, normHint, compactHint) };
                            });
                            const bestScore = ranked.reduce(function(mx, it) { return Math.max(mx, it.res.score); }, 0);
                            if (bestScore > 0) {
                                const topRanked = ranked.filter(function(it) { return it.res.score >= bestScore - 5; }).map(function(it) { return it.el; });
                                if (topRanked.length > 0 && topRanked.length < candidates.length) {
                                    candidates = topRanked;
                                }
                            }
                        }
                    }
                    if (isBirthYearQuery && candidates.length > 0) {
                        candidates = candidates.filter(function(el) {
                            return !apexIsUnrelatedNumericEl(el) && !apexIsNonYearDatePartEl(el);
                        });
                    }
                    if (candidates.length === 1) {
                        chosen = candidates[0];
                        matchedVia = sel;
                        usedFallback = idx > 0;
                        attempts.push({
                            strategy: classifyStrategy(sel, idx),
                            selector: sel,
                            matchCount: qRes.matches.length,
                            visibleCount: candidates.length,
                            interactableCount: interactableCnt,
                            selected: true,
                            rejectedReason: ''
                        });
                        break;
                    } else if (candidates.length > 1) {
                        sawAmbiguous = true;
                        ambiguousCount = candidates.length;
                        if (ambiguousElements.length === 0 || candidates.length < ambiguousElements.length) {
                            ambiguousElements = candidates.slice(0, 12);
                        }
                        attempts.push({
                            strategy: classifyStrategy(sel, idx),
                            selector: sel,
                            matchCount: qRes.matches.length,
                            visibleCount: candidates.length,
                            interactableCount: interactableCnt,
                            selected: false,
                            rejectedReason: 'AMBIGUOUS_' + candidates.length + '_MATCHES'
                        });
                    } else {
                        attempts.push({
                            strategy: classifyStrategy(sel, idx),
                            selector: sel,
                            matchCount: 0,
                            visibleCount: 0,
                            interactableCount: 0,
                            selected: false,
                            rejectedReason: 'NO_MATCH'
                        });
                    }
                }

                if (!chosen && normHint && defaultQuery) {
                    const isBirthYearFallback = /(birth\s*year|year\s*of\s*birth|bday[-_]?year|birth[-_]?year|dob[-_]?year|born\s*year)/i.test(normHint);
                    const scopedQuery = hintPrefix ? (hintPrefix + defaultQuery) : '';
                    let pool = [];
                    if (scopedQuery) {
                        const sRes = apexQueryChain(scopedQuery);
                        if (sRes.ok) pool = sRes.matches;
                    }
                    if (pool.length === 0) {
                        pool = apexDeepQueryAll(defaultQuery);
                    }
                    pool = pool.filter(function(el) {
                        if (!apexIsConnected(el) || !(allowOffscreen || apexIsVisible(el))) return false;
                        if (isBirthYearFallback && (apexIsUnrelatedNumericEl(el) || apexIsNonYearDatePartEl(el))) return false;
                        return true;
                    });
                    const birthYearMatches = isBirthYearFallback ? pool.filter(function(el) { return apexIsBirthYearCandidateEl(el); }) : [];
                    const exact = pool.filter(function(el) {
                        return apexResolveLabel(el) === normHint || apexNorm(apexDescribeAccessibleName(el)) === normHint;
                    });
                    const compact = (exact.length === 0 && compactHint) ? pool.filter(function(el) {
                        return apexNormCompact(apexResolveLabel(el)) === compactHint ||
                            apexNormCompact(apexDescribeAccessibleName(el)) === compactHint;
                    }) : [];
                    const partial = (exact.length === 0 && compact.length === 0) ? pool.filter(function(el) {
                        const lbl = apexResolveLabel(el);
                        return lbl.indexOf(normHint) >= 0 || (compactHint.length >= 4 && apexNormCompact(lbl).indexOf(compactHint) >= 0);
                    }) : [];
                    let candidates = exact.length > 0 ? exact : (compact.length > 0 ? compact : (birthYearMatches.length > 0 ? birthYearMatches : partial));
                    if (candidates.length > 1) {
                        const ranked = candidates.map(function(el) {
                            return { el: el, res: apexScoreInputAttributes(el, normHint, compactHint) };
                        });
                        const bestScore = ranked.reduce(function(mx, it) { return Math.max(mx, it.res.score); }, 0);
                        if (bestScore > 0) {
                            const topRanked = ranked.filter(function(it) { return it.res.score >= bestScore - 5; }).map(function(it) { return it.el; });
                            if (topRanked.length > 0 && topRanked.length < candidates.length) {
                                candidates = topRanked;
                            }
                        }
                    }
                    if (candidates.length === 1) {
                        chosen = candidates[0];
                        matchedVia = 'label:' + normHint;
                        usedFallback = selectorQueue.length > 0;
                        attempts.push({
                            strategy: 'SCOPED_TEXT',
                            selector: 'label:' + normHint,
                            matchCount: candidates.length,
                            visibleCount: candidates.length,
                            interactableCount: candidates.filter(function(el) { return !apexIsDisabled(el); }).length,
                            selected: true,
                            rejectedReason: ''
                        });
                    } else if (candidates.length > 1) {
                        attempts.push({
                            strategy: 'SCOPED_TEXT',
                            selector: 'label:' + normHint,
                            matchCount: candidates.length,
                            visibleCount: candidates.length,
                            interactableCount: candidates.filter(function(el) { return !apexIsDisabled(el); }).length,
                            selected: false,
                            rejectedReason: 'AMBIGUOUS_' + candidates.length + '_MATCHES'
                        });
                        const ambigInfos = candidates.slice(0, 12).map(function(el, i) { return apexCollectCandidateInfo(el, i); });
                        return {
                            ok: false,
                            code: 'ELEMENT_AMBIGUOUS',
                            matchCount: candidates.length,
                            ambiguousCandidates: ambigInfos,
                            diagnostics: {
                                recordedLocator: rawQueue[0] || '',
                                targetLabel: labelHint || $qLabel,
                                totalMatches: candidates.length,
                                visibleMatches: candidates.length,
                                interactableMatches: candidates.filter(function(el) { return !apexIsDisabled(el); }).length,
                                finalOutcome: 'ELEMENT_AMBIGUOUS',
                                attemptedCandidates: attempts
                            },
                            msg: 'Ambiguous locator matched ' + candidates.length + ' elements for "' + (labelHint || $qLabel) + '".'
                        };
                    } else {
                        attempts.push({
                            strategy: 'SCOPED_TEXT',
                            selector: 'label:' + normHint,
                            matchCount: 0,
                            visibleCount: 0,
                            interactableCount: 0,
                            selected: false,
                            rejectedReason: 'NO_MATCH'
                        });
                    }
                }

                if (!chosen) {
                    if (sawAmbiguous) {
                        const ambigInfos = ambiguousElements.map(function(el, i) { return apexCollectCandidateInfo(el, i); });
                        return {
                            ok: false,
                            code: 'ELEMENT_AMBIGUOUS',
                            matchCount: ambiguousCount,
                            ambiguousCandidates: ambigInfos,
                            diagnostics: {
                                recordedLocator: rawQueue[0] || '',
                                targetLabel: labelHint || $qLabel,
                                totalMatches: ambiguousCount,
                                visibleMatches: ambigInfos.filter(function(c) { return c.isVisible; }).length,
                                interactableMatches: ambigInfos.filter(function(c) { return c.isVisible && c.isEnabled; }).length,
                                finalOutcome: 'ELEMENT_AMBIGUOUS',
                                attemptedCandidates: attempts
                            },
                            msg: 'Ambiguous locator matched ' + ambiguousCount + ' elements for "' + (labelHint || $qLabel) + '".'
                        };
                    }
                    if (blockedCode) {
                        return {
                            ok: false,
                            code: blockedCode,
                            msg: blockedMsg,
                            diagnostics: {
                                recordedLocator: rawQueue[0] || '',
                                targetLabel: labelHint || $qLabel,
                                totalMatches: 0,
                                visibleMatches: 0,
                                interactableMatches: 0,
                                finalOutcome: blockedCode,
                                attemptedCandidates: attempts
                            }
                        };
                    }
                    if (apexDetectSecurityChallenge()) {
                        return {
                            ok: false,
                            code: 'SECURITY_CHALLENGE_BLOCKED',
                            msg: 'Page shows an active security challenge or CAPTCHA; manual completion is required.',
                            diagnostics: {
                                recordedLocator: rawQueue[0] || '',
                                targetLabel: labelHint || $qLabel,
                                totalMatches: 0,
                                visibleMatches: 0,
                                interactableMatches: 0,
                                finalOutcome: 'SECURITY_CHALLENGE_BLOCKED',
                                attemptedCandidates: attempts
                            }
                        };
                    }
                    return {
                        ok: false,
                        code: 'ELEMENT_NOT_FOUND',
                        msg: 'Could not find target "' + (labelHint || $qLabel || (selectorQueue.length > 0 ? selectorQueue[0] : '')) + '".',
                        diagnostics: {
                            recordedLocator: rawQueue[0] || '',
                            targetLabel: labelHint || $qLabel,
                            totalMatches: 0,
                            visibleMatches: 0,
                            interactableMatches: 0,
                            finalOutcome: 'ELEMENT_NOT_FOUND',
                            attemptedCandidates: attempts
                        }
                    };
                }

                if (!apexIsConnected(chosen)) {
                    return {
                        ok: false,
                        code: 'ELEMENT_STALE',
                        msg: 'Target element is no longer attached to the DOM.',
                        diagnostics: {
                            recordedLocator: rawQueue[0] || '',
                            targetLabel: labelHint || $qLabel,
                            totalMatches: 1,
                            visibleMatches: 0,
                            interactableMatches: 0,
                            finalOutcome: 'ELEMENT_STALE',
                            attemptedCandidates: attempts
                        }
                    };
                }
                if (!allowOffscreen && !apexIsVisible(chosen)) {
                    return {
                        ok: false,
                        code: 'ELEMENT_NOT_INTERACTABLE',
                        msg: 'Target element "' + (labelHint || $qLabel) + '" is not visible.',
                        diagnostics: {
                            recordedLocator: rawQueue[0] || '',
                            targetLabel: labelHint || $qLabel,
                            totalMatches: 1,
                            visibleMatches: 0,
                            interactableMatches: 0,
                            finalOutcome: 'ELEMENT_NOT_INTERACTABLE',
                            attemptedCandidates: attempts
                        }
                    };
                }
                if (!ignoreDisabled && apexIsDisabled(chosen)) {
                    return {
                        ok: false,
                        code: 'ELEMENT_DISABLED',
                        msg: 'Target element "' + (labelHint || $qLabel) + '" is disabled.',
                        diagnostics: {
                            recordedLocator: rawQueue[0] || '',
                            targetLabel: labelHint || $qLabel,
                            totalMatches: 1,
                            visibleMatches: 1,
                            interactableMatches: 0,
                            finalOutcome: 'ELEMENT_DISABLED',
                            attemptedCandidates: attempts
                        }
                    };
                }
                if (requireWritable && apexIsReadOnly(chosen) && !apexIsCustomPickerOrDropdown(chosen)) {
                    return {
                        ok: false,
                        code: 'ELEMENT_NOT_INTERACTABLE',
                        msg: 'Target element "' + (labelHint || $qLabel) + '" is read-only.',
                        diagnostics: {
                            recordedLocator: rawQueue[0] || '',
                            targetLabel: labelHint || $qLabel,
                            totalMatches: 1,
                            visibleMatches: 1,
                            interactableMatches: 0,
                            finalOutcome: 'ELEMENT_NOT_INTERACTABLE',
                            attemptedCandidates: attempts
                        }
                    };
                }
                function apexGetElementRect(el) {
                    try {
                        if (!el || !el.getBoundingClientRect) return null;
                        const r = el.getBoundingClientRect();
                        let left = r.left;
                        let top = r.top;
                        let curWin = apexOwnWin(apexOwnDoc(el));
                        let safety = 0;
                        while (curWin && curWin !== window && curWin.frameElement && safety < 5) {
                            const fr = curWin.frameElement.getBoundingClientRect();
                            left += fr.left;
                            top += fr.top;
                            curWin = curWin.parent;
                            safety++;
                        }
                        const vw = window.innerWidth || document.documentElement.clientWidth || 1080;
                        const vh = window.innerHeight || document.documentElement.clientHeight || 1920;
                        return {
                            left: Math.round(left * 10) / 10,
                            top: Math.round(top * 10) / 10,
                            width: Math.round(Math.max(r.width, 1) * 10) / 10,
                            height: Math.round(Math.max(r.height, 1) * 10) / 10,
                            viewportWidth: vw,
                            viewportHeight: vh
                        };
                    } catch (_) {
                        return null;
                    }
                }
                function apexHighlightInteractedElement(el) {
                    try {
                        if (!el || !el.style) return;
                        if (!window.__apexHighlightedElements) {
                            window.__apexHighlightedElements = [];
                        }
                        const prevOutline = el.style.outline || '';
                        const prevOutlineOffset = el.style.outlineOffset || '';
                        const prevBoxShadow = el.style.boxShadow || '';
                        const prevTransition = el.style.transition || '';
                        el.style.transition = 'outline 0.14s ease, box-shadow 0.14s ease';
                        el.style.outline = '2.5px solid #00E5FF';
                        el.style.outlineOffset = '2px';
                        el.style.boxShadow = '0 0 0 4px rgba(0, 229, 255, 0.28), 0 0 18px rgba(0, 229, 255, 0.35)';
                        const entry = {
                            el: el,
                            outline: prevOutline,
                            outlineOffset: prevOutlineOffset,
                            boxShadow: prevBoxShadow,
                            transition: prevTransition
                        };
                        window.__apexHighlightedElements.push(entry);
                        setTimeout(function() {
                            try {
                                if (el && el.style) {
                                    el.style.outline = prevOutline;
                                    el.style.outlineOffset = prevOutlineOffset;
                                    el.style.boxShadow = prevBoxShadow;
                                    el.style.transition = prevTransition;
                                }
                            } catch (_) {}
                        }, 1400);
                    } catch (_) {}
                }
                apexHighlightInteractedElement(chosen);
                const resolvedRect = apexGetElementRect(chosen);
                return {
                    ok: true,
                    el: chosen,
                    doc: apexOwnDoc(chosen),
                    win: apexOwnWin(apexOwnDoc(chosen)),
                    resolvedSelector: matchedVia,
                    recoveredViaFallback: usedFallback,
                    sensitive: apexIsSensitiveEl(chosen),
                    frameChainHint: $qFrameChain,
                    shadowChainHint: $qShadowChain,
                    targetRect: resolvedRect,
                    diagnostics: {
                        recordedLocator: rawQueue[0] || '',
                        targetLabel: labelHint || $qLabel,
                        totalMatches: 1,
                        visibleMatches: 1,
                        interactableMatches: 1,
                        finalOutcome: 'RESOLVED',
                        attemptedCandidates: attempts,
                        targetRect: resolvedRect
                    }
                };
            }
            function apexResolveTarget(sel, labelHint, defaultQuery, opts) {
                const initialList = sel ? [sel] : [];
                return apexResolveWithCandidates(initialList, labelHint, defaultQuery, opts);
            }
        """.trimIndent()
    }

    fun buildStepExecutionScript(step: AutomationStep): String {
        val candidatesJson = buildCandidateSelectorsJson(step)
        val qSel = JSONObject.quote(step.selector)
        val qVal = JSONObject.quote(step.value)
        val qLabel = JSONObject.quote(step.targetLabel)
        val qKey = JSONObject.quote(step.keyboardKey.ifBlank { step.value })
        val desiredCheckedLiteral = when (step.desiredCheckedState) {
            true -> "true"
            false -> "false"
            null -> "null"
        }
        val clearFirstLiteral = if (step.clearBeforeInput) "true" else "false"
        val defaultQuery = when (step.actionType) {
            AutomationActionType.INPUT, AutomationActionType.CLEAR, AutomationActionType.FILL_CREDENTIAL -> when {
                step.targetLabel.contains("email", ignoreCase = true) ->
                    "input[type=\"email\"], input[name*=\"email\"], input, textarea, [contenteditable]"
                step.targetLabel.contains("search", ignoreCase = true) ->
                    "input[type=\"search\"], input[name=\"q\"], input[type=\"text\"], textarea"
                else -> "input:not([type=\"hidden\"]):not([type=\"checkbox\"]):not([type=\"radio\"]):not([type=\"submit\"]):not([type=\"button\"]), textarea, select, [contenteditable=\"true\"], [contenteditable=\"plaintext-only\"], [role=\"textbox\"], [role=\"spinbutton\"], [role=\"combobox\"], [role=\"listbox\"], [data-datepicker], [data-year-picker], [data-birth-year], button[aria-haspopup]"
            }
            AutomationActionType.TOGGLE ->
                "input[type=\"checkbox\"], input[type=\"radio\"], [role=\"checkbox\"], [role=\"switch\"], [role=\"radio\"]"
            AutomationActionType.SELECT ->
                "select, [role=\"combobox\"], [role=\"listbox\"], [role=\"option\"], [role=\"dialog\"], [role=\"grid\"], [role=\"menu\"], [role=\"menuitem\"], [role=\"menuitemradio\"], [role=\"menuitemcheckbox\"], [data-datepicker], [data-timepicker], [data-time-picker], [data-calendar], [data-year-picker], [data-birth-year], [data-bottom-sheet], [data-sheet], [data-drawer], [data-time], [data-slot], [data-country-code], [data-dial-code], button[aria-haspopup], .flatpickr-input, .bottom-sheet li, .calendar td, input:not([type=\"hidden\"]):not([type=\"checkbox\"]):not([type=\"radio\"]):not([type=\"submit\"]):not([type=\"button\"]), textarea"
            AutomationActionType.SUBMIT ->
                "button[type=\"submit\"], input[type=\"submit\"], button, [role=\"button\"]"
            AutomationActionType.CLICK, AutomationActionType.DOUBLE_CLICK, AutomationActionType.LONG_PRESS ->
                "button, a, summary, label, input[type=\"button\"], input[type=\"submit\"], input[type=\"image\"], [role=\"button\"], [role=\"link\"], [role=\"tab\"], [role=\"menuitem\"], [role=\"menuitemradio\"], [role=\"menuitemcheckbox\"], [role=\"option\"], [role=\"gridcell\"], [role=\"switch\"], [role=\"radio\"], [role=\"checkbox\"], [role=\"treeitem\"], [aria-pressed], [aria-selected], [aria-current], [data-action], [data-cta], [data-signup], [data-href], [data-url], [data-link], [data-date], [data-day], [data-month], [data-year], [data-time], [data-slot], [data-datepicker], [data-timepicker], [data-calendar], [data-country-code], [data-dial-code], [data-value], [data-option], [data-tab], [data-dismiss], [data-close], [tabindex], [onclick], .btn, .button, .cta, .signup-btn, .calendar-day, .time-slot, .sheet-item, .flatpickr-day, .rdp-day"
            AutomationActionType.FOCUS, AutomationActionType.BLUR, AutomationActionType.KEY_PRESS ->
                "input:not([type=\"hidden\"]), textarea, select, button, a, [contenteditable], [tabindex], [role]"
            AutomationActionType.SCROLL ->
                "input, textarea, select, button, a, section, form, [role]"
            AutomationActionType.EXTRACT ->
                "article, h1, h2, h3, p, a, [role=\"alert\"], [role=\"status\"]"
            AutomationActionType.WAIT,
            AutomationActionType.NAVIGATE,
            AutomationActionType.CONDITION,
            AutomationActionType.LOOP,
            AutomationActionType.SET_VARIABLE,
            AutomationActionType.CALL_WORKFLOW,
            AutomationActionType.CLEAR_DATA,
            AutomationActionType.UNSUPPORTED -> "*"
        }
        val qDefaultQuery = JSONObject.quote(defaultQuery)
        val helpersJs = buildTargetHelpersJs(
            labelHint = step.targetLabel,
            framePath = step.framePath,
            shadowHostPath = step.shadowHostPath
        )

        val actionBody = when (step.actionType) {
            AutomationActionType.SCROLL -> buildScrollScriptBody(qSel, qVal, qLabel, qDefaultQuery)
            AutomationActionType.INPUT -> buildInputScriptBody(qVal, qLabel, qDefaultQuery, clearFirstLiteral)
            AutomationActionType.CLEAR -> buildClearScriptBody(qLabel, qDefaultQuery)
            AutomationActionType.TOGGLE -> buildToggleScriptBody(qVal, qLabel, qDefaultQuery, desiredCheckedLiteral)
            AutomationActionType.SELECT -> buildSelectScriptBody(qVal, qLabel, qDefaultQuery)
            AutomationActionType.FOCUS -> buildFocusBlurScriptBody(isFocus = true, qLabel = qLabel, qDefaultQuery = qDefaultQuery)
            AutomationActionType.BLUR -> buildFocusBlurScriptBody(isFocus = false, qLabel = qLabel, qDefaultQuery = qDefaultQuery)
            AutomationActionType.KEY_PRESS -> buildKeyPressScriptBody(qKey, qLabel, qDefaultQuery)
            AutomationActionType.EXTRACT -> buildExtractScriptBody(qSel, qLabel)
            AutomationActionType.DOUBLE_CLICK -> buildDoubleClickScriptBody(qLabel, qDefaultQuery)
            AutomationActionType.LONG_PRESS -> buildLongPressScriptBody(qLabel, qDefaultQuery)
            AutomationActionType.CLICK, AutomationActionType.SUBMIT ->
                buildClickOrSubmitScriptBody(isSubmit = step.actionType == AutomationActionType.SUBMIT, qLabel = qLabel, qDefaultQuery = qDefaultQuery)
            AutomationActionType.FILL_CREDENTIAL -> """
                return JSON.stringify({
                    ok: false,
                    code: 'CREDENTIAL_AUTH_REQUIRED',
                    msg: 'Credential fill requires authorized CredentialFillCoordinator execution.'
                });
            """.trimIndent()
            AutomationActionType.NAVIGATE,
            AutomationActionType.WAIT,
            AutomationActionType.CONDITION,
            AutomationActionType.LOOP,
            AutomationActionType.SET_VARIABLE,
            AutomationActionType.CALL_WORKFLOW,
            AutomationActionType.CLEAR_DATA -> """
                return JSON.stringify({ ok: true, code: 'NONE', msg: 'Handled natively by coordinator.' });
            """.trimIndent()
            AutomationActionType.UNSUPPORTED -> """
                return JSON.stringify({
                    ok: false,
                    code: 'UNSUPPORTED_ACTION',
                    msg: 'Unsupported workflow action type cannot be executed safely.'
                });
            """.trimIndent()
        }

        return """
            (function() {
                try {
                    $helpersJs
                    const CANDIDATE_SELECTORS = $candidatesJson;
                    function apexResolveOrderedTarget(defaultQuery, opts) {
                        const queue = CANDIDATE_SELECTORS.length > 0 ? CANDIDATE_SELECTORS : ($qSel ? [$qSel] : []);
                        if (queue.length === 1) {
                            return apexResolveTarget(queue[0], $qLabel, defaultQuery, opts);
                        }
                        if (queue.length === 0) {
                            return apexResolveTarget('', $qLabel, defaultQuery, opts);
                        }
                        return apexResolveWithCandidates(queue, $qLabel, defaultQuery, opts);
                    }
                    function apexExtractWantedYear(raw) {
                        const parts = apexParseDateParts(raw);
                        if (parts && parts.year) return parts.year;
                        const s = String(raw || '').trim();
                        if (/^(19\d{2}|20\d{2})$/.test(s)) return s;
                        const m = s.match(/\b(19\d{2}|20\d{2})\b/);
                        return m ? m[1] : '';
                    }
                    function apexDispatchMouseClick(el, win) {
                        if (!el) return;
                        try {
                            const w = win || window;
                            el.dispatchEvent(new MouseEvent('mousedown', { bubbles: true, cancelable: true, view: w }));
                            el.dispatchEvent(new MouseEvent('mouseup', { bubbles: true, cancelable: true, view: w }));
                        } catch (_) {}
                        try { el.click(); } catch (_) {}
                    }
                    function apexSelectNativeOption(target, wantedRaw, labelText, resolved) {
                        const opts = Array.from(target.options || []);
                        if (opts.length === 0) {
                            return { ok: false, code: 'OPTION_NOT_FOUND', msg: 'Select control "' + labelText + '" has no available options.' };
                        }
                        const dateRole = apexDetectDateFieldRole(target);
                        const dateParts = apexParseDateParts(wantedRaw);
                        const adaptedRaw = (dateParts && dateRole) ? apexAdaptDateValueForTarget(target, wantedRaw) : String(wantedRaw || '');
                        const wanted = apexNorm(adaptedRaw || wantedRaw || labelText);
                        const wantedYear = (dateParts && dateParts.year) ? dateParts.year : apexExtractWantedYear(wantedRaw || labelText);
                        const monthNamesFull = ['january', 'february', 'march', 'april', 'may', 'june', 'july', 'august', 'september', 'october', 'november', 'december'];
                        const monthNamesShort = ['jan', 'feb', 'mar', 'apr', 'may', 'jun', 'jul', 'aug', 'sep', 'oct', 'nov', 'dec'];
                        let exact = opts.filter(function(o) {
                            const ov = apexNorm(o.value);
                            const ot = apexNorm(o.text || o.label);
                            return ov === wanted || ot === wanted;
                        });
                        if (exact.length === 0 && dateParts) {
                            if (dateRole === 'year' && dateParts.year) {
                                exact = opts.filter(function(o) {
                                    const ov = apexNorm(o.value);
                                    const ot = apexNorm(o.text || o.label);
                                    return ov === dateParts.year || ot === dateParts.year;
                                });
                            } else if (dateRole === 'month' && dateParts.month > 0) {
                                const mStr = String(dateParts.month);
                                const mPad = (dateParts.month < 10 ? '0' : '') + mStr;
                                const mFull = monthNamesFull[dateParts.month - 1];
                                const mShort = monthNamesShort[dateParts.month - 1];
                                exact = opts.filter(function(o) {
                                    const ov = apexNorm(o.value);
                                    const ot = apexNorm(o.text || o.label);
                                    return ov === mStr || ov === mPad || ov === mFull || ov === mShort ||
                                        ot === mStr || ot === mPad || ot === mFull || ot === mShort ||
                                        ot.indexOf(mFull) === 0;
                                });
                            } else if (dateRole === 'day' && dateParts.day > 0) {
                                const dStr = String(dateParts.day);
                                const dPad = (dateParts.day < 10 ? '0' : '') + dStr;
                                exact = opts.filter(function(o) {
                                    const ov = apexNorm(o.value);
                                    const ot = apexNorm(o.text || o.label);
                                    return ov === dStr || ov === dPad || ot === dStr || ot === dPad;
                                });
                            }
                        }
                        if (exact.length === 0 && wantedYear && (!dateRole || dateRole === 'year')) {
                            exact = opts.filter(function(o) {
                                const ov = apexNorm(o.value);
                                const ot = apexNorm(o.text || o.label);
                                return ov === wantedYear || ot === wantedYear;
                            });
                        }
                        if (exact.length === 0 && wanted) {
                            const optTextBlob = opts.slice(0, 8).map(function(o) { return apexNorm((o.value || '') + ' ' + (o.text || '')); }).join(' ');
                            const looksLikeGender = /(\bmale\b|\bfemale\b|\bgender\b|\bnon[- ]?binary\b)/.test(optTextBlob + ' ' + apexTokenizeSig(apexResolveLabel(target)));
                            if (looksLikeGender) {
                                const gMap = {
                                    'female': ['female', 'f', 'woman'],
                                    'f': ['female', 'f', 'woman'],
                                    'woman': ['female', 'woman', 'f'],
                                    'male': ['male', 'm', 'man'],
                                    'm': ['male', 'm', 'man'],
                                    'man': ['male', 'man', 'm'],
                                    'non-binary': ['non-binary', 'nonbinary', 'non binary', 'nb', 'other', 'x'],
                                    'nonbinary': ['non-binary', 'nonbinary', 'non binary', 'nb', 'other', 'x'],
                                    'non binary': ['non-binary', 'nonbinary', 'non binary', 'nb', 'other', 'x'],
                                    'other': ['other', 'non-binary', 'nonbinary', 'x']
                                };
                                const aliases = gMap[wanted] || [];
                                if (aliases.length > 0) {
                                    exact = opts.filter(function(o) {
                                        const ov = apexNorm(o.value);
                                        const ot = apexNorm(o.text || o.label);
                                        return aliases.indexOf(ov) >= 0 || aliases.indexOf(ot) >= 0;
                                    });
                                }
                            }
                        }
                        if (exact.length === 0 && wanted) {
                            const dialM = wanted.match(/(?:^|[\s(])\+(\d{1,4})(?=$|[\s)])/) || wanted.match(/^\+?(\d{1,4})$/);
                            if (dialM && (apexIsCountryCodeEl(target) || wanted.charAt(0) === '+')) {
                                const dialDigits = dialM[1];
                                const dialRegex = new RegExp('(?:^|[\\s(+])\\+?' + dialDigits + '(?=$|[\\s)])');
                                const plusDialRegex = new RegExp('(?:^|[\\s(])\\+' + dialDigits + '(?=$|[\\s)])');
                                const dialMatches = opts.filter(function(o) {
                                    const ov = apexNorm(o.value);
                                    const ot = apexNorm(o.text || o.label);
                                    return plusDialRegex.test(ot) || plusDialRegex.test(ov) || dialRegex.test(ov) || dialRegex.test(ot);
                                });
                                if (dialMatches.length === 1) {
                                    exact = dialMatches;
                                } else if (dialMatches.length > 1) {
                                    const plusExact = dialMatches.filter(function(o) {
                                        return apexNorm(o.value) === ('+' + dialDigits) || apexNorm(o.text) === ('+' + dialDigits);
                                    });
                                    if (plusExact.length === 1) {
                                        exact = plusExact;
                                    } else if (dialDigits === '1' && wanted === '+1') {
                                        const usMatches = dialMatches.filter(function(o) {
                                            const ov = apexNorm(o.value);
                                            const ot = apexNorm(o.text || o.label);
                                            return ov === 'us' || ov === 'usa' || ot.indexOf('united states') >= 0 || /\bus\b/.test(ot);
                                        });
                                        exact = usMatches.length === 1 ? usMatches : dialMatches;
                                    } else {
                                        exact = dialMatches;
                                    }
                                }
                            }
                        }
                        let candidates = exact;
                        if (candidates.length === 0 && wanted) {
                            candidates = opts.filter(function(o) {
                                const ov = apexNorm(o.value);
                                const ot = apexNorm(o.text || o.label);
                                if (wantedYear && (/^(19\d{2}|20\d{2})$/.test(ov) || /^(19\d{2}|20\d{2})$/.test(ot))) {
                                    return ov === wantedYear || ot === wantedYear;
                                }
                                return (ov && ov.indexOf(wanted) >= 0) || (ot && ot.indexOf(wanted) >= 0);
                            });
                        }
                        if (candidates.length === 0) {
                            const yrVals = [];
                            for (let i = 0; i < opts.length; i++) {
                                const v = String(opts[i].value || opts[i].text || '').trim();
                                if (/^(19\d{2}|20\d{2})$/.test(v)) yrVals.push(parseInt(v, 10));
                            }
                            if (yrVals.length > 0 && wantedYear) {
                                const minY = Math.min.apply(null, yrVals);
                                const maxY = Math.max.apply(null, yrVals);
                                return {
                                    ok: false,
                                    code: 'OPTION_NOT_FOUND',
                                    msg: 'Year "' + wantedYear + '" is not available in "' + labelText + '" (available range: ' + minY + '..' + maxY + ').'
                                };
                            }
                            return {
                                ok: false,
                                code: 'OPTION_NOT_FOUND',
                                msg: 'Could not find option "' + (wantedRaw || labelText) + '" in "' + labelText + '".'
                            };
                        }
                        if (candidates.length > 1) {
                            const ambigInfos = candidates.slice(0, 12).map(function(el, i) { return apexCollectCandidateInfo(el, i); });
                            return {
                                ok: false,
                                code: 'ELEMENT_AMBIGUOUS',
                                matchCount: candidates.length,
                                ambiguousCandidates: ambigInfos,
                                msg: 'Multiple options (' + candidates.length + ') matched "' + (wantedRaw || labelText) + '" in "' + labelText + '".'
                            };
                        }
                        let chosenOpt = null;
                        if (candidates.length === 1) {
                            chosenOpt = candidates[0];
                        }
                        if (!chosenOpt) {
                            return { ok: false, code: 'OPTION_NOT_FOUND', msg: 'Could not find option "' + (wantedRaw || labelText) + '".' };
                        }
                        if (chosenOpt.disabled || apexIsDisabled(chosenOpt)) {
                            return {
                                ok: false,
                                code: 'ELEMENT_DISABLED',
                                msg: 'Option "' + (chosenOpt.text || chosenOpt.value) + '" is disabled in "' + labelText + '".'
                            };
                        }
                        const win = resolved.win || window;
                        const proto = win.HTMLSelectElement && win.HTMLSelectElement.prototype;
                        const descriptor = proto ? Object.getOwnPropertyDescriptor(proto, 'value') : null;
                        if (descriptor && descriptor.set) {
                            descriptor.set.call(target, chosenOpt.value);
                        } else {
                            target.value = chosenOpt.value;
                        }
                        chosenOpt.selected = true;
                        target.dispatchEvent(new Event('input', { bubbles: true }));
                        target.dispatchEvent(new Event('change', { bubbles: true }));
                        const verifiedNow = (target.value === chosenOpt.value && chosenOpt.selected === true);
                        if (!verifiedNow) {
                            return {
                                ok: false,
                                code: 'VERIFICATION_FAILED',
                                msg: 'Selected option "' + (chosenOpt.text || chosenOpt.value) + '" was not applied to "' + labelText + '" (actual value: "' + String(target.value || '') + '").'
                            };
                        }
                        return {
                            ok: true,
                            code: 'NONE',
                            selectedValue: target.value,
                            selectedText: (chosenOpt.text || '').trim(),
                            verified: true,
                            resolvedSelector: resolved.resolvedSelector || '',
                            recoveredViaFallback: Boolean(resolved.recoveredViaFallback),
                            diagnostics: resolved.diagnostics || null,
                            msg: 'Selected "' + (chosenOpt.text || chosenOpt.value) + '" in "' + labelText + '".'
                        };
                    }
                    function apexSelectCustomPickerOption(target, doc, win, wantedRaw, labelText, resolved) {
                        const dateRole = apexDetectDateFieldRole(target);
                        const dateParts = apexParseDateParts(wantedRaw);
                        const adaptedCustomRaw = (dateParts && (dateRole === 'year' || dateRole === 'month' || dateRole === 'day'))
                            ? apexAdaptDateValueForTarget(target, wantedRaw)
                            : wantedRaw;
                        const wanted = apexNorm(adaptedCustomRaw || labelText);
                        const wantedYear = (dateParts && dateParts.year) ? dateParts.year : apexExtractWantedYear(wantedRaw || labelText);
                        const wantedIsoDate = (dateRole !== 'year' && dateRole !== 'month' && dateRole !== 'day' && dateParts && dateParts.iso) ? dateParts.iso : '';
                        const wantedMonthNum = (dateParts && dateParts.month) ? dateParts.month : 0;
                        const wantedDayNum = (dateParts && dateParts.day) ? String(dateParts.day) : '';
                        const role = String(target.getAttribute && target.getAttribute('role') || '').toLowerCase();
                        const isTrigger = role === 'combobox' ||
                            Boolean(target.getAttribute && (target.getAttribute('aria-controls') || target.getAttribute('aria-haspopup'))) ||
                            target.hasAttribute('data-datepicker') ||
                            target.hasAttribute('data-timepicker') ||
                            target.hasAttribute('data-time-picker') ||
                            target.hasAttribute('data-calendar') ||
                            target.hasAttribute('data-year-picker') ||
                            target.hasAttribute('data-birth-year') ||
                            target.hasAttribute('data-bottom-sheet') ||
                            target.hasAttribute('data-sheet') ||
                            target.hasAttribute('data-drawer') ||
                            ((target.tagName || '').toLowerCase() === 'button');
                        const expandedBefore = String(target.getAttribute && target.getAttribute('aria-expanded') || '').toLowerCase();
                        const alreadyOpenedByApex = target.getAttribute && target.getAttribute('data-apex-opened') === 'true';
                        let newlyOpened = false;
                        if (isTrigger && expandedBefore !== 'true' && !alreadyOpenedByApex) {
                            apexDispatchMouseClick(target, win);
                            try { target.setAttribute('data-apex-opened', 'true'); } catch (_) {}
                            newlyOpened = true;
                        }

                        function findSearchContainers() {
                            const scopes = [];
                            const controlsId = target.getAttribute && (target.getAttribute('aria-controls') || target.getAttribute('aria-owns')) || '';
                            if (controlsId) {
                                controlsId.split(/\s+/).forEach(function(cid) {
                                    if (!cid) return;
                                    const cEl = (doc && doc.getElementById ? doc.getElementById(cid) : null) || document.getElementById(cid);
                                    if (cEl && scopes.indexOf(cEl) < 0) scopes.push(cEl);
                                });
                            }
                            if (role === 'listbox' || role === 'grid' || role === 'dialog' || role === 'application' ||
                                target.hasAttribute('data-datepicker') || target.hasAttribute('data-timepicker') ||
                                target.hasAttribute('data-calendar') || target.hasAttribute('data-year-picker') ||
                                target.hasAttribute('data-bottom-sheet') || target.hasAttribute('data-sheet') || target.hasAttribute('data-drawer')) {
                                if (scopes.indexOf(target) < 0) scopes.push(target);
                            }
                            const parentGroup = target.closest ? target.closest('dialog[open], [aria-modal="true"], [data-datepicker], [data-timepicker], [data-calendar], [data-year-picker], [data-bottom-sheet], [data-sheet], [data-drawer], [role="dialog"], [role="alertdialog"], [role="application"], .bottom-sheet, .action-sheet, .modal.show, .modal.open, .flatpickr-calendar, .rdp, .MuiPickersPopper-root, .mat-datepicker-content, .datepicker, .calendar, .year-picker, .custom-select, .dropdown') : null;
                            if (parentGroup && scopes.indexOf(parentGroup) < 0) scopes.push(parentGroup);
                            const openPopups = Array.from((doc || document).querySelectorAll(
                                'dialog[open], [aria-modal="true"], [role="listbox"], [role="dialog"], [role="alertdialog"], [role="grid"], [role="menu"], [role="application"], [data-datepicker], [data-timepicker], [data-calendar], [data-bottom-sheet], [data-sheet], [data-drawer], [data-year-picker], .bottom-sheet, .bottomsheet, .action-sheet, .modal.show, .modal.open, .flatpickr-calendar.open, .flatpickr-calendar, .rdp, .MuiPickersPopper-root, .mat-datepicker-content, .calendar-sheet, .timepicker-popup, .year-picker, .year-list, .year-wheel, .datepicker-dropdown, .calendar-popup, .dropdown-menu'
                            )).filter(function(el) { return apexIsConnected(el) && apexIsVisible(el); });
                            openPopups.forEach(function(p) {
                                if (scopes.indexOf(p) < 0) scopes.push(p);
                            });
                            if (scopes.length === 0) scopes.push(doc || document);
                            return scopes;
                        }

                        function getOptionTextAndValues(o) {
                            const vals = [];
                            if (!o || !o.getAttribute) return vals;
                            ['data-year', 'data-value', 'data-date', 'data-day', 'data-month', 'data-time', 'data-slot', 'data-country-code', 'data-dial-code', 'data-option', 'value', 'aria-label', 'title'].forEach(function(attr) {
                                const v = o.getAttribute(attr);
                                if (v && String(v).trim()) vals.push(apexNorm(v));
                            });
                            const rawTxt = (o.innerText || o.textContent || '').replace(/\s+/g, ' ').trim();
                            if (rawTxt && rawTxt.length <= 64) vals.push(apexNorm(rawTxt));
                            return vals;
                        }

                        function isOverflowOrDisabledDay(o) {
                            const cls = String(o.className || '').toLowerCase();
                            if (/(outside-month|prev-month|next-month|prevmonthday|nextmonthday|rdp-day_outside|disabled|unavailable)/.test(cls)) return true;
                            if (apexIsDisabled(o)) return true;
                            return false;
                        }

                        function collectOptionElements() {
                            const scopes = findSearchContainers();
                            const collected = [];
                            const optSelector = '[role="option"], [role="menuitem"], [role="menuitemradio"], [role="menuitemcheckbox"], [role="gridcell"], option, [data-year], [data-date], [data-day], [data-time], [data-slot], [data-country-code], [data-dial-code], [data-value], [data-option], .calendar-day, .time-slot, .sheet-item, .flatpickr-day, .rdp-day, .year-option, .year-item, .year-cell, li, button';
                            for (let s = 0; s < scopes.length; s++) {
                                const scope = scopes[s];
                                const found = Array.from(scope.querySelectorAll ? scope.querySelectorAll(optSelector) : []);
                                for (let i = 0; i < found.length; i++) {
                                    const el = found[i];
                                    if (el === target || (el.contains && el.contains(target))) continue;
                                    if (!apexIsConnected(el) || !apexIsVisible(el)) continue;
                                    const tagL = (el.tagName || '').toLowerCase();
                                    if ((tagL === 'li' || tagL === 'button') && el.querySelector && el.querySelector('[role="option"], [data-year], [data-value], [data-date], [data-time], [data-slot]')) continue;
                                    if (collected.indexOf(el) < 0) collected.push(el);
                                }
                            }
                            return collected;
                        }

                        function filterMatchingOptions(optionNodes) {
                            const exactOpts = optionNodes.filter(function(o) {
                                const tokens = getOptionTextAndValues(o);
                                if (tokens.indexOf(wanted) >= 0) return true;
                                if (wantedYear && tokens.indexOf(wantedYear) >= 0) return true;
                                return false;
                            });
                            if (exactOpts.length > 0) {
                                const nonOverflow = exactOpts.filter(function(o) { return !isOverflowOrDisabledDay(o); });
                                return nonOverflow.length > 0 ? nonOverflow : exactOpts;
                            }
                            if (wantedIsoDate && wantedDayNum) {
                                const isoMatched = optionNodes.filter(function(o) {
                                    if (isOverflowOrDisabledDay(o)) return false;
                                    const dd = apexNorm(o.getAttribute && o.getAttribute('data-date') || '');
                                    if (dd === wantedIsoDate) return true;
                                    const dayAttr = String(o.getAttribute && o.getAttribute('data-day') || '').trim();
                                    if (dayAttr && String(parseInt(dayAttr, 10)) === wantedDayNum) return true;
                                    return false;
                                });
                                if (isoMatched.length > 0) return isoMatched;
                            }
                            const partialOpts = optionNodes.filter(function(o) {
                                const tokens = getOptionTextAndValues(o);
                                for (let i = 0; i < tokens.length; i++) {
                                    const tk = tokens[i];
                                    if (wantedYear && /^(19\d{2}|20\d{2})$/.test(tk)) {
                                        if (tk === wantedYear) return true;
                                        continue;
                                    }
                                    if (wanted && tk.indexOf(wanted) >= 0) return true;
                                }
                                return false;
                            });
                            if (partialOpts.length > 1) {
                                const nonOverflow = partialOpts.filter(function(o) { return !isOverflowOrDisabledDay(o); });
                                if (nonOverflow.length > 0) return nonOverflow;
                            }
                            return partialOpts;
                        }

                        let allOptions = collectOptionElements();
                        let matched = filterMatchingOptions(allOptions);

                        // Bounded month navigation for calendar popups when exact ISO date YYYY-MM-DD isn't on the visible month
                        if (matched.length === 0 && wantedIsoDate && wantedYear && wantedMonthNum > 0) {
                            const monthNames = ['january', 'february', 'march', 'april', 'may', 'june', 'july', 'august', 'september', 'october', 'november', 'december'];
                            const targetYm = parseInt(wantedYear, 10) * 12 + (wantedMonthNum - 1);
                            const scopes = findSearchContainers();
                            for (let s = 0; s < scopes.length && matched.length === 0; s++) {
                                const sc = scopes[s];
                                const headerEl = sc.querySelector ? sc.querySelector('.calendar-title, [class*="month-year"], .flatpickr-current-month, [role="heading"], h2, h3') : null;
                                if (!headerEl) continue;
                                const prevBtn = sc.querySelector('[data-picker-role="prev-month"], .flatpickr-prev-month, button[aria-label*="prev" i], button[aria-label*="previous" i]');
                                const nextBtn = sc.querySelector('[data-picker-role="next-month"], .flatpickr-next-month, button[aria-label*="next" i]');
                                for (let step = 0; step < 12 && matched.length === 0; step++) {
                                    const hTxt = apexNorm(headerEl.innerText || headerEl.textContent || '');
                                    const yM = hTxt.match(/\b(19\d{2}|20\d{2})\b/);
                                    let mIdx = -1;
                                    for (let mi = 0; mi < monthNames.length; mi++) {
                                        if (hTxt.indexOf(monthNames[mi]) >= 0 || hTxt.indexOf(monthNames[mi].slice(0, 3)) >= 0) {
                                            mIdx = mi;
                                            break;
                                        }
                                    }
                                    if (!yM || mIdx < 0) break;
                                    const curYm = parseInt(yM[1], 10) * 12 + mIdx;
                                    if (curYm === targetYm) {
                                        // On right month: also allow matching active day-cell text if data-date wasn't present
                                        allOptions = collectOptionElements();
                                        matched = filterMatchingOptions(allOptions);
                                        if (matched.length === 0) {
                                            matched = allOptions.filter(function(o) {
                                                if (isOverflowOrDisabledDay(o)) return false;
                                                const txt = (o.innerText || o.textContent || '').trim();
                                                return txt === wantedDayNum && ((o.getAttribute && o.getAttribute('role') === 'gridcell') || /(day|cell)/i.test(String(o.className || '')));
                                            });
                                        }
                                        break;
                                    }
                                    const navBtn = curYm < targetYm ? nextBtn : prevBtn;
                                    if (!navBtn || !apexIsVisible(navBtn) || apexIsDisabled(navBtn)) break;
                                    apexDispatchMouseClick(navBtn, win);
                                    newlyOpened = true;
                                    allOptions = collectOptionElements();
                                    matched = filterMatchingOptions(allOptions);
                                }
                            }
                        }

                        // If inside a calendar dialog that has a year-switcher header button, click it to reveal year options
                        if (matched.length === 0 && wantedYear) {
                            const scopes = findSearchContainers();
                            for (let s = 0; s < scopes.length; s++) {
                                const yrSwitchers = Array.from(scopes[s].querySelectorAll(
                                    '[data-picker-role="year-nav"], [data-action*="year"], .year-switch, .calendar-year-toggle, .datepicker-year-switch, button[aria-label*="year" i], [role="button"][aria-label*="year" i]'
                                )).filter(function(btn) { return btn !== target && apexIsVisible(btn); });
                                if (yrSwitchers.length > 0) {
                                    apexDispatchMouseClick(yrSwitchers[0], win);
                                    newlyOpened = true;
                                    allOptions = collectOptionElements();
                                    matched = filterMatchingOptions(allOptions);
                                    break;
                                }
                            }
                        }

                        // If still not found, check if any native <select> for year exists inside the open calendar dialog
                        if (matched.length === 0 && wantedYear) {
                            const scopes = findSearchContainers();
                            for (let s = 0; s < scopes.length; s++) {
                                const innerSelects = Array.from(scopes[s].querySelectorAll('select')).filter(function(sel) {
                                    return sel !== target && apexIsVisible(sel);
                                });
                                for (let i = 0; i < innerSelects.length; i++) {
                                    const selRes = apexSelectNativeOption(innerSelects[i], wantedYear, labelText, resolved);
                                    if (selRes.ok) return selRes;
                                }
                            }
                        }

                        // Scrollable / virtualized year container discovery (Requirement 4)
                        if (matched.length === 0) {
                            const scopes = findSearchContainers();
                            const scrollContainers = [];
                            for (let s = 0; s < scopes.length; s++) {
                                const sc = scopes[s];
                                if (sc !== doc && sc !== document && sc.nodeType === 1) scrollContainers.push(sc);
                                const descendants = Array.from(sc.querySelectorAll ? sc.querySelectorAll(
                                    '[data-year-picker], [data-scrollable], .year-picker, .year-list, .year-wheel, [role="listbox"], ul, div'
                                ) : []);
                                for (let d = 0; d < descendants.length; d++) {
                                    const dEl = descendants[d];
                                    if (!apexIsVisible(dEl)) continue;
                                    const hasScrollRange = (dEl.scrollHeight > dEl.clientHeight && dEl.clientHeight > 0) ||
                                        dEl.hasAttribute('data-year-picker') ||
                                        dEl.hasAttribute('data-scrollable') ||
                                        /(year-picker|year-list|year-wheel|virtual-list|scroll)/.test(String(dEl.className || '').toLowerCase());
                                    if (hasScrollRange && scrollContainers.indexOf(dEl) < 0) {
                                        scrollContainers.push(dEl);
                                    }
                                }
                            }
                            for (let c = 0; c < scrollContainers.length && matched.length === 0; c++) {
                                const container = scrollContainers[c];
                                const maxScroll = Math.max(container.scrollHeight - (container.clientHeight || 200), 2400);
                                const stepPx = Math.max(Math.min(container.clientHeight || 160, 240), 100);
                                for (let step = 1; step <= 20 && matched.length === 0; step++) {
                                    const nextTop = Math.min(step * stepPx, maxScroll);
                                    container.scrollTop = nextTop;
                                    try { container.setAttribute('data-scroll-top', String(nextTop)); } catch (_) {}
                                    container.dispatchEvent(new Event('scroll', { bubbles: true }));
                                    allOptions = collectOptionElements();
                                    matched = filterMatchingOptions(allOptions);
                                    if (nextTop >= maxScroll && step > 4) break;
                                }
                            }
                        }

                        if (matched.length === 0) {
                            const retryCnt = parseInt(target.getAttribute && target.getAttribute('data-apex-dyn-retries') || '0', 10) || 0;
                            if ((newlyOpened || allOptions.length === 0) && retryCnt < 3) {
                                try { target.setAttribute('data-apex-dyn-retries', String(retryCnt + 1)); } catch (_) {}
                                return {
                                    ok: false,
                                    code: 'OPTION_NOT_FOUND',
                                    pendingDynamic: true,
                                    msg: 'Waiting for dynamic options to render for "' + (wantedRaw || labelText) + '" in "' + labelText + '".'
                                };
                            }
                            try { target.removeAttribute('data-apex-opened'); target.removeAttribute('data-apex-dyn-retries'); } catch (_) {}
                            return {
                                ok: false,
                                code: 'OPTION_NOT_FOUND',
                                msg: allOptions.length === 0
                                    ? ('No selectable options were rendered for "' + labelText + '" when attempting to select "' + (wantedRaw || labelText) + '".')
                                    : ('Option "' + (wantedRaw || labelText) + '" was not found among ' + allOptions.length + ' available options in "' + labelText + '".')
                            };
                        }

                        try { target.removeAttribute('data-apex-opened'); target.removeAttribute('data-apex-dyn-retries'); } catch (_) {}

                        if (matched.length > 1) {
                            const candidates = matched;
                            const ambigInfos = candidates.slice(0, 12).map(function(el, i) { return apexCollectCandidateInfo(el, i); });
                            return {
                                ok: false,
                                code: 'ELEMENT_AMBIGUOUS',
                                matchCount: candidates.length,
                                ambiguousCandidates: ambigInfos,
                                msg: 'Multiple options (' + candidates.length + ') matched "' + (wantedRaw || labelText) + '" in "' + labelText + '".'
                            };
                        }

                        const candidates = matched;
                        let optEl = null;
                        if (candidates.length === 1) {
                            optEl = candidates[0];
                        }
                        if (!optEl) {
                            return { ok: false, code: 'OPTION_NOT_FOUND', msg: 'Option "' + (wantedRaw || labelText) + '" not found.' };
                        }
                        if (apexIsDisabled(optEl)) {
                            return {
                                ok: false,
                                code: 'ELEMENT_DISABLED',
                                msg: 'Option "' + (wantedRaw || labelText) + '" is disabled in "' + labelText + '".'
                            };
                        }
                        if (typeof optEl.scrollIntoView === 'function') {
                            try { optEl.scrollIntoView({ behavior: 'auto', block: 'center', inline: 'nearest' }); } catch (_) {}
                        }
                        const confirmedText = (
                            optEl.getAttribute('data-year') ||
                            optEl.getAttribute('data-value') ||
                            optEl.getAttribute('data-date') ||
                            optEl.innerText ||
                            optEl.textContent ||
                            wantedRaw
                        ).replace(/\s+/g, ' ').trim();

                        const beforeTargetVal = String(target.value || '');
                        const beforeTargetTxt = apexNorm(target.innerText || target.textContent || '');
                        const beforeTargetData = apexNorm(
                            (target.getAttribute && (target.getAttribute('data-selected-year') || target.getAttribute('data-selected-date') || target.getAttribute('data-value') || target.getAttribute('data-year'))) || ''
                        );

                        apexDispatchMouseClick(optEl, win);

                        // Also check if a confirm/apply button inside a calendar dialog needs to be clicked ONLY if selection marked the option selected
                        const optSelectedAfterClick =
                            String(optEl.getAttribute && optEl.getAttribute('aria-selected') || '').toLowerCase() === 'true' ||
                            String(optEl.getAttribute && optEl.getAttribute('aria-checked') || '').toLowerCase() === 'true' ||
                            String(optEl.getAttribute && optEl.getAttribute('aria-current') || '').toLowerCase() === 'date' ||
                            String(optEl.getAttribute && optEl.getAttribute('data-selected') || '').toLowerCase() === 'true' ||
                            /(?:^|\s)(selected|active|is-selected|is-active)(?:\s|$)/i.test(String(optEl.className || ''));

                        const afterTargetVal = String(target.value || '');
                        const afterTargetTxt = apexNorm(target.innerText || target.textContent || '');
                        const afterTargetData = apexNorm(
                            (target.getAttribute && (target.getAttribute('data-selected-year') || target.getAttribute('data-selected-date') || target.getAttribute('data-value') || target.getAttribute('data-year'))) || ''
                        );
                        const normConfirmed = apexNorm(confirmedText);
                        const checkToken = wantedYear || wanted || normConfirmed;

                        let associatedInputUpdated = false;
                        const parentWrap = target.closest ? target.closest('[data-datepicker], [data-calendar], [data-year-picker], [data-bottom-sheet], [role="dialog"], .datepicker, .year-picker, .custom-select, .dropdown, form, fieldset') : null;
                        if (parentWrap && parentWrap.querySelectorAll) {
                            const inputs = Array.from(parentWrap.querySelectorAll('input'));
                            for (let i = 0; i < inputs.length; i++) {
                                const iv = apexNorm(inputs[i].value || '');
                                if (iv && (iv.indexOf(checkToken) >= 0 || iv === normConfirmed)) {
                                    associatedInputUpdated = true;
                                    break;
                                }
                            }
                        }

                        const targetValUpdated = afterTargetVal && (apexNorm(afterTargetVal).indexOf(checkToken) >= 0 || apexNorm(afterTargetVal) === normConfirmed);
                        const targetTxtUpdated = afterTargetTxt && (afterTargetTxt.indexOf(checkToken) >= 0 || afterTargetTxt.indexOf(normConfirmed) >= 0) && afterTargetTxt !== beforeTargetTxt;
                        const targetDataUpdated = afterTargetData && (afterTargetData.indexOf(checkToken) >= 0 || afterTargetData === normConfirmed);
                        const popupClosedWithUpdate = (!apexIsConnected(optEl) || !apexIsVisible(optEl)) &&
                            (targetValUpdated || targetTxtUpdated || targetDataUpdated || associatedInputUpdated || afterTargetVal !== beforeTargetVal);

                        const verifiedSelection = optSelectedAfterClick || targetValUpdated || targetTxtUpdated || targetDataUpdated || associatedInputUpdated || popupClosedWithUpdate;
                        if (!verifiedSelection) {
                            return {
                                ok: false,
                                code: 'VERIFICATION_FAILED',
                                msg: 'Clicked option "' + confirmedText + '" in "' + labelText + '", but the selection was not applied to the control.'
                            };
                        }

                        return {
                            ok: true,
                            code: 'NONE',
                            selectedValue: afterTargetVal || confirmedText,
                            selectedText: confirmedText,
                            verified: true,
                            resolvedSelector: resolved.resolvedSelector || '',
                            recoveredViaFallback: Boolean(resolved.recoveredViaFallback),
                            diagnostics: resolved.diagnostics || null,
                            msg: 'Selected "' + confirmedText + '" in "' + labelText + '".'
                        };
                    }
                    $actionBody
                } catch (err) {
                    return JSON.stringify({
                        ok: false,
                        code: 'JS_EXCEPTION',
                        msg: String(err && err.message ? err.message : err)
                    });
                }
            })();
        """.trimIndent()
    }

    private fun buildScrollScriptBody(
        qSel: String,
        qVal: String,
        qLabel: String,
        qDefaultQuery: String
    ): String = """
        const scrollDir = ($qVal || '').trim().toLowerCase();
        const axisMatch = scrollDir.match(/x:\s*(-?\d+)\s*,\s*y:\s*(-?\d+)/);
        const parsedDeltaX = axisMatch ? parseInt(axisMatch[1], 10) : 0;
        const parsedDeltaY = axisMatch ? parseInt(axisMatch[2], 10) : parseInt(scrollDir, 10);
        const hasNumericDelta = Boolean(axisMatch) || (!isNaN(parsedDeltaY) && parsedDeltaY !== 0);
        const hasSpecificTarget = CANDIDATE_SELECTORS.length > 0 || Boolean(($qSel || '').trim());
        if (hasSpecificTarget) {
            const resolved = apexResolveOrderedTarget($qDefaultQuery, { allowOffscreen: true, ignoreDisabled: true });
            if (!resolved.ok) return JSON.stringify(resolved);
            const target = resolved.el;
            const win = resolved.win || window;
            if (hasNumericDelta) {
                const dx = axisMatch ? parsedDeltaX : 0;
                const dy = isNaN(parsedDeltaY) ? 0 : parsedDeltaY;
                if (typeof target.scrollBy === 'function') {
                    try { target.scrollBy(dx, dy); } catch (_) {
                        target.scrollLeft = (target.scrollLeft || 0) + dx;
                        target.scrollTop = (target.scrollTop || 0) + dy;
                    }
                } else {
                    target.scrollLeft = (target.scrollLeft || 0) + dx;
                    target.scrollTop = (target.scrollTop || 0) + dy;
                }
                try { target.dispatchEvent(new Event('scroll', { bubbles: true })); } catch (_) {}
            } else if (typeof target.scrollIntoView === 'function') {
                target.scrollIntoView({ behavior: 'auto', block: 'center', inline: 'nearest' });
            }
            const rect = target.getBoundingClientRect ? target.getBoundingClientRect() : null;
            const vh = win.innerHeight || 1920;
            const inView = rect ? (rect.bottom >= 0 && rect.top <= vh) : true;
            return JSON.stringify({
                ok: true,
                code: 'NONE',
                inViewport: inView,
                scrollTop: target.scrollTop || 0,
                scrollLeft: target.scrollLeft || 0,
                resolvedSelector: resolved.resolvedSelector || '',
                recoveredViaFallback: Boolean(resolved.recoveredViaFallback),
                diagnostics: resolved.diagnostics || null,
                msg: 'Scrolled "' + ($qLabel || 'target') + '".'
            });
        }
        const beforeX = window.scrollX || window.pageXOffset || 0;
        const beforeY = window.scrollY || window.pageYOffset || 0;
        const maxScroll = Math.max(
            0,
            Math.max(
                document.documentElement ? document.documentElement.scrollHeight : 0,
                document.body ? document.body.scrollHeight : 0
            ) - (window.innerHeight || 800)
        );
        if (scrollDir === 'top') {
            window.scrollTo(0, 0);
        } else if (scrollDir === 'bottom') {
            window.scrollTo(0, maxScroll);
        } else if (axisMatch) {
            window.scrollBy(parsedDeltaX, parsedDeltaY);
        } else {
            const numericDelta = parseInt(scrollDir, 10);
            const deltaY = !isNaN(numericDelta) && numericDelta !== 0 ? numericDelta : (scrollDir === 'up' ? -600 : 600);
            window.scrollBy(0, deltaY);
        }
        const afterX = window.scrollX || window.pageXOffset || 0;
        const afterY = window.scrollY || window.pageYOffset || 0;
        return JSON.stringify({
            ok: true,
            code: 'NONE',
            moved: afterY !== beforeY || afterX !== beforeX,
            scrollX: afterX,
            scrollY: afterY,
            msg: 'Scrolled page (' + (scrollDir || 'down') + ') to y=' + Math.round(afterY) + '.'
        });
    """.trimIndent()

    private fun buildInputScriptBody(
        qVal: String,
        qLabel: String,
        qDefaultQuery: String,
        clearFirstLiteral: String
    ): String = """
        const resolved = apexResolveOrderedTarget($qDefaultQuery, { requireWritable: true });
        if (!resolved.ok) return JSON.stringify(resolved);
        const target = resolved.el;
        const doc = resolved.doc || document;
        const win = resolved.win || window;
        const tag = (target.tagName || '').toLowerCase();
        const type = (target.getAttribute && target.getAttribute('type') || '').toLowerCase();
        const role = (target.getAttribute && target.getAttribute('role') || '').toLowerCase();
        let val = String($qVal);
        if (typeof target.scrollIntoView === 'function') {
            try { target.scrollIntoView({ behavior: 'auto', block: 'center', inline: 'nearest' }); } catch (_) {}
        }
        try { target.focus(); } catch (_) {}

        if (tag === 'select') {
            return JSON.stringify(apexSelectNativeOption(target, val, $qLabel, resolved));
        }

        const isCustomPickerTrigger = (tag !== 'input' && tag !== 'textarea' && apexIsCustomPickerOrDropdown(target)) ||
            (tag === 'input' && (apexIsReadOnly(target) || role === 'combobox' || target.hasAttribute('aria-controls') || target.hasAttribute('aria-haspopup') || target.hasAttribute('data-year-picker')));
        if (isCustomPickerTrigger && val) {
            return JSON.stringify(apexSelectCustomPickerOption(target, doc, win, val, $qLabel, resolved));
        }

        const isDateTimeNative = tag === 'input' && ['date', 'datetime-local', 'time', 'month', 'week'].indexOf(type) >= 0;
        if (isDateTimeNative && (type === 'date' || type === 'month')) {
            val = apexAdaptDateValueForTarget(target, val);
        }
        if (isDateTimeNative) {
            const validDate = type !== 'date' || /^\d{4}-\d{2}-\d{2}$/.test(val);
            const validTime = type !== 'time' || /^\d{2}:\d{2}(:\d{2})?$/.test(val);
            const validDateTimeLocal = type !== 'datetime-local' || /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(:\d{2})?$/.test(val);
            const validMonth = type !== 'month' || /^\d{4}-\d{2}$/.test(val);
            const validWeek = type !== 'week' || /^\d{4}-W\d{2}$/.test(val);
            if (!validDate || !validTime || !validDateTimeLocal || !validMonth || !validWeek) {
                return JSON.stringify({
                    ok: false,
                    code: 'VERIFICATION_FAILED',
                    msg: 'Invalid ' + type + ' value format for "' + $qLabel + '".'
                });
            }
            const minVal = target.getAttribute('min') || '';
            const maxVal = target.getAttribute('max') || '';
            if ((minVal && val < minVal) || (maxVal && val > maxVal)) {
                return JSON.stringify({
                    ok: false,
                    code: 'VERIFICATION_FAILED',
                    msg: 'Date/time value for "' + $qLabel + '" is outside [' + minVal + '..' + maxVal + '].'
                });
            }
        }

        const isPhoneField = apexIsPhoneInputEl(target);
        const isCountryCodeField = apexIsCountryCodeEl(target);
        if (tag === 'input' && !isDateTimeNative) {
            val = apexAdaptDateValueForTarget(target, val);
        }
        if (tag === 'input' && isPhoneField) {
            val = apexAdaptPhoneForInput(target, val);
        } else if (tag === 'input' && isCountryCodeField && type === 'number') {
            const ccDigits = val.replace(/\D/g, '');
            if (ccDigits) val = ccDigits;
        }

        // If full date YYYY-MM-DD is provided to a 4-digit birth-year input field, extract the 4-digit year
        if (tag === 'input' && !isDateTimeNative && /^\d{4}-\d{2}-\d{2}$/.test(val) && apexIsBirthYearCandidateEl(target)) {
            const maxLen = parseInt(target.getAttribute('maxlength') || '0', 10);
            const ac = String(target.getAttribute('autocomplete') || '').toLowerCase();
            const ph = String(target.getAttribute('placeholder') || '').trim();
            if (maxLen === 4 || ac === 'bday-year' || /^(yyyy|19\d{2}|20\d{2})$/i.test(ph) || type === 'number') {
                val = val.slice(0, 4);
            }
        }

        if (tag === 'input' && (type === 'number' || apexIsBirthYearCandidateEl(target)) && /^-?\d+$/.test(val.trim())) {
            const numVal = parseInt(val.trim(), 10);
            const minAttr = target.getAttribute('min') || target.getAttribute('data-min') || '';
            const maxAttr = target.getAttribute('max') || target.getAttribute('data-max') || '';
            const minNum = minAttr ? parseInt(minAttr, 10) : NaN;
            const maxNum = maxAttr ? parseInt(maxAttr, 10) : NaN;
            if ((!isNaN(minNum) && numVal < minNum) || (!isNaN(maxNum) && numVal > maxNum)) {
                return JSON.stringify({
                    ok: false,
                    code: 'VERIFICATION_FAILED',
                    msg: 'Numeric/year value "' + val + '" for "' + $qLabel + '" is outside allowed range [' + minAttr + '..' + maxAttr + '].'
                });
            }
        }

        const ceAttr = (target.getAttribute && target.getAttribute('contenteditable') || '').toLowerCase();
        const isContentEditable = ceAttr === 'true' || ceAttr === 'plaintext-only' || Boolean(target.isContentEditable);

        if (tag !== 'input' && tag !== 'textarea' && !isContentEditable) {
            return JSON.stringify({
                ok: false,
                code: 'ELEMENT_NOT_INTERACTABLE',
                msg: 'Target "' + $qLabel + '" (' + tag + ') does not accept text input.'
            });
        }

        if (isContentEditable && tag !== 'input' && tag !== 'textarea') {
            if ($clearFirstLiteral) {
                target.textContent = '';
            }
            if ('innerText' in target && ceAttr !== 'plaintext-only') {
                target.innerText = val;
            } else {
                target.textContent = val;
            }
            try { target.dispatchEvent(new Event('beforeinput', { bubbles: true, cancelable: true })); } catch (_) {}
            target.dispatchEvent(new Event('input', { bubbles: true }));
            target.dispatchEvent(new Event('change', { bubbles: true }));
            const actualCe = apexNorm(target.innerText || target.textContent || '');
            const expectedCe = apexNorm(val);
            if (expectedCe && actualCe !== expectedCe) {
                return JSON.stringify({
                    ok: false,
                    code: 'VERIFICATION_FAILED',
                    msg: 'Contenteditable value verification failed for "' + $qLabel + '".'
                });
            }
            return JSON.stringify({
                ok: true,
                code: 'NONE',
                verified: true,
                resolvedSelector: resolved.resolvedSelector || '',
                recoveredViaFallback: Boolean(resolved.recoveredViaFallback),
                diagnostics: resolved.diagnostics || null,
                msg: 'Updated rich text "' + $qLabel + '".'
            });
        }

        const proto = tag === 'textarea' ? win.HTMLTextAreaElement && win.HTMLTextAreaElement.prototype : win.HTMLInputElement && win.HTMLInputElement.prototype;
        const descriptor = proto ? Object.getOwnPropertyDescriptor(proto, 'value') : null;
        function applyDomValue(nextVal) {
            if ($clearFirstLiteral) {
                if (descriptor && descriptor.set) descriptor.set.call(target, '');
                else target.value = '';
            }
            if (descriptor && descriptor.set) {
                descriptor.set.call(target, nextVal);
            } else {
                target.value = nextVal;
            }
            try { target.dispatchEvent(new Event('beforeinput', { bubbles: true, cancelable: true })); } catch (_) {}
            target.dispatchEvent(new Event('input', { bubbles: true }));
            target.dispatchEvent(new Event('change', { bubbles: true }));
            return String(target.value || '');
        }
        function doesRetainedMatch(candidateVal, actualRetained) {
            if (actualRetained === candidateVal) return true;
            if (resolved.sensitive && candidateVal.length > 0 && actualRetained.length === candidateVal.length) return true;
            if (type === 'number' && /^-?\d+$/.test(candidateVal.trim()) && /^-?\d+$/.test(actualRetained.trim())) {
                return parseInt(actualRetained.trim(), 10) === parseInt(candidateVal.trim(), 10);
            }
            const cDigits = candidateVal.replace(/\D/g, '');
            const aDigits = actualRetained.replace(/\D/g, '');
            if (isPhoneField && cDigits.length >= 6 && aDigits.length >= 6) {
                return aDigits === cDigits || cDigits.endsWith(aDigits) || aDigits.endsWith(cDigits);
            }
            if (isCountryCodeField && cDigits.length >= 1 && cDigits.length <= 4 && aDigits === cDigits) {
                return true;
            }
            return false;
        }
        let retained = applyDomValue(val);
        let retainedMatches = doesRetainedMatch(val, retained);
        if (!retainedMatches && isPhoneField) {
            const pParts = apexExtractPhoneParts(String($qVal));
            const fallbacks = [pParts.nationalDigits, pParts.allDigits];
            for (let fi = 0; fi < fallbacks.length && !retainedMatches; fi++) {
                const fb = fallbacks[fi];
                if (fb && fb !== val) {
                    val = fb;
                    retained = applyDomValue(val);
                    retainedMatches = doesRetainedMatch(val, retained);
                }
            }
        } else if (!retainedMatches && isCountryCodeField) {
            const ccDigits = val.replace(/\D/g, '');
            if (ccDigits && ccDigits !== val) {
                val = ccDigits;
                retained = applyDomValue(val);
                retainedMatches = doesRetainedMatch(val, retained);
            }
        }
        if (!retainedMatches) {
            return JSON.stringify({
                ok: false,
                code: 'VERIFICATION_FAILED',
                msg: 'Input value "' + val + '" did not persist in "' + $qLabel + '" (actual: "' + retained + '").'
            });
        }
        const safeEcho = resolved.sensitive ? '••••' : ('"' + val.slice(0, 24) + '"');
        return JSON.stringify({
            ok: true,
            code: 'NONE',
            selectedValue: retained,
            verified: true,
            resolvedSelector: resolved.resolvedSelector || '',
            recoveredViaFallback: Boolean(resolved.recoveredViaFallback),
            diagnostics: resolved.diagnostics || null,
            msg: 'Entered ' + safeEcho + ' into "' + $qLabel + '".'
        });
    """.trimIndent()

    private fun buildClearScriptBody(
        qLabel: String,
        qDefaultQuery: String
    ): String = """
        const resolved = apexResolveOrderedTarget($qDefaultQuery, { requireWritable: true });
        if (!resolved.ok) return JSON.stringify(resolved);
        const target = resolved.el;
        const win = resolved.win || window;
        const tag = (target.tagName || '').toLowerCase();
        const ceAttr = (target.getAttribute && target.getAttribute('contenteditable') || '').toLowerCase();
        const isContentEditable = ceAttr === 'true' || ceAttr === 'plaintext-only' || Boolean(target.isContentEditable);
        try { target.focus(); } catch (_) {}

        if (isContentEditable && tag !== 'input' && tag !== 'textarea') {
            target.textContent = '';
            target.dispatchEvent(new Event('input', { bubbles: true }));
            target.dispatchEvent(new Event('change', { bubbles: true }));
            if ((target.textContent || '').trim().length > 0) {
                return JSON.stringify({
                    ok: false,
                    code: 'VERIFICATION_FAILED',
                    msg: 'Failed to clear contenteditable "' + $qLabel + '".'
                });
            }
        } else {
            const proto = tag === 'textarea' ? win.HTMLTextAreaElement && win.HTMLTextAreaElement.prototype : win.HTMLInputElement && win.HTMLInputElement.prototype;
            const descriptor = proto ? Object.getOwnPropertyDescriptor(proto, 'value') : null;
            if (descriptor && descriptor.set) descriptor.set.call(target, '');
            else target.value = '';
            target.dispatchEvent(new Event('input', { bubbles: true }));
            target.dispatchEvent(new Event('change', { bubbles: true }));
            if (String(target.value || '').length > 0) {
                return JSON.stringify({
                    ok: false,
                    code: 'VERIFICATION_FAILED',
                    msg: 'Input "' + $qLabel + '" still contains text after clear.'
                });
            }
        }
        return JSON.stringify({
            ok: true,
            code: 'NONE',
            verified: true,
            resolvedSelector: resolved.resolvedSelector || '',
            recoveredViaFallback: Boolean(resolved.recoveredViaFallback),
            diagnostics: resolved.diagnostics || null,
            msg: 'Cleared "' + $qLabel + '".'
        });
    """.trimIndent()

    private fun buildToggleScriptBody(
        qVal: String,
        qLabel: String,
        qDefaultQuery: String,
        desiredCheckedLiteral: String
    ): String = """
        const resolved = apexResolveOrderedTarget($qDefaultQuery, { requireWritable: true });
        if (!resolved.ok) return JSON.stringify(resolved);
        let target = resolved.el;
        const doc = resolved.doc || document;
        let tag = (target.tagName || '').toLowerCase();
        let type = (target.getAttribute && target.getAttribute('type') || '').toLowerCase();
        let role = (target.getAttribute && target.getAttribute('role') || '').toLowerCase();
        const desiredState = $desiredCheckedLiteral;
        const isNativeToggle = tag === 'input' && (type === 'checkbox' || type === 'radio');
        const isAriaToggle = role === 'checkbox' || role === 'switch' || role === 'radio';
        if (!isNativeToggle && !isAriaToggle) {
            return JSON.stringify({
                ok: false,
                code: 'ELEMENT_NOT_INTERACTABLE',
                msg: 'Target "' + $qLabel + '" is not a checkbox, switch, or radio button.'
            });
        }
        const rawValOption = String($qVal || '').trim();
        const normValOption = apexNorm(rawValOption);
        const isRadio = (tag === 'input' && type === 'radio') || role === 'radio';
        const isBooleanOption = !normValOption || ['checked', 'unchecked', 'true', 'false'].indexOf(normValOption) >= 0;
        if (isRadio && !isBooleanOption) {
            const gAliasMap = {
                'female': ['female', 'f', 'woman'],
                'f': ['female', 'f', 'woman'],
                'male': ['male', 'm', 'man'],
                'm': ['male', 'm', 'man'],
                'non-binary': ['non-binary', 'nonbinary', 'non binary', 'nb', 'other', 'x'],
                'nonbinary': ['non-binary', 'nonbinary', 'non binary', 'nb', 'other', 'x'],
                'non binary': ['non-binary', 'nonbinary', 'non binary', 'nb', 'other', 'x'],
                'other': ['other', 'non-binary', 'nonbinary', 'x']
            };
            const acceptedTokens = gAliasMap[normValOption] || [normValOption];
            const radioName = target.getAttribute && target.getAttribute('name') || '';
            let groupRadios = [];
            if (radioName) {
                groupRadios = Array.from((doc || document).querySelectorAll('input[type="radio"][name="' + apexEscapeAttr(radioName) + '"]'));
            }
            if (groupRadios.length <= 1 && target.closest) {
                const groupWrap = target.closest('[role="radiogroup"], fieldset, form');
                if (groupWrap && groupWrap.querySelectorAll) {
                    groupRadios = Array.from(groupWrap.querySelectorAll('input[type="radio"], [role="radio"]'));
                }
            }
            const visibleRadios = groupRadios.filter(function(r) { return apexIsConnected(r) && apexIsVisible(r) && !apexIsDisabled(r); });
            const matchedRadio = visibleRadios.filter(function(r) {
                const rv = apexNorm(r.value || r.getAttribute && r.getAttribute('data-value') || '');
                const rl = apexNorm(apexResolveLabel(r));
                for (let ai = 0; ai < acceptedTokens.length; ai++) {
                    const tok = acceptedTokens[ai];
                    if (rv === tok || rl === tok) return true;
                    const rx = new RegExp('(?:^|\\b)' + apexEscapeCss(tok) + '(?:\\b|$)');
                    if (tok.length > 1 && rx.test(rl)) return true;
                }
                return false;
            });
            if (matchedRadio.length === 0) {
                return JSON.stringify({
                    ok: false,
                    code: 'OPTION_NOT_FOUND',
                    msg: 'Could not find radio option "' + rawValOption + '" for "' + $qLabel + '".'
                });
            }
            target = matchedRadio[0];
            tag = (target.tagName || '').toLowerCase();
            type = (target.getAttribute && target.getAttribute('type') || '').toLowerCase();
            role = (target.getAttribute && target.getAttribute('role') || '').toLowerCase();
        }
        if (typeof target.scrollIntoView === 'function') {
            try { target.scrollIntoView({ behavior: 'auto', block: 'center', inline: 'nearest' }); } catch (_) {}
        }
        function readState(el) {
            if ((el.tagName || '').toLowerCase() === 'input' && ((el.getAttribute && el.getAttribute('type') || '').toLowerCase() === 'checkbox' || (el.getAttribute && el.getAttribute('type') || '').toLowerCase() === 'radio')) {
                return Boolean(el.checked);
            }
            return String(el.getAttribute('aria-checked') || '').toLowerCase() === 'true';
        }
        const beforeChecked = readState(target);
        const targetWanted = (isRadio && !isBooleanOption)
            ? true
            : (desiredState !== null ? desiredState : (isRadio ? true : !beforeChecked));
        if (beforeChecked !== targetWanted) {
            target.click();
            let afterClick = readState(target);
            if (afterClick !== targetWanted && tag === 'input') {
                target.checked = targetWanted;
                target.dispatchEvent(new Event('input', { bubbles: true }));
                target.dispatchEvent(new Event('change', { bubbles: true }));
                afterClick = readState(target);
            }
            if (afterClick !== targetWanted) {
                return JSON.stringify({
                    ok: false,
                    code: 'VERIFICATION_FAILED',
                    msg: 'Toggle state did not become ' + targetWanted + ' for "' + $qLabel + '".'
                });
            }
        }
        const finalState = readState(target);
        return JSON.stringify({
            ok: true,
            code: 'NONE',
            checked: finalState,
            selectedValue: !isBooleanOption ? rawValOption : String(finalState),
            verified: true,
            resolvedSelector: resolved.resolvedSelector || '',
            recoveredViaFallback: Boolean(resolved.recoveredViaFallback),
            diagnostics: resolved.diagnostics || null,
            msg: (finalState ? 'Selected "' : 'Deselected "') + (!isBooleanOption ? rawValOption : $qLabel) + '".'
        });
    """.trimIndent()

    private fun buildSelectScriptBody(
        qVal: String,
        qLabel: String,
        qDefaultQuery: String
    ): String = """
        const resolved = apexResolveOrderedTarget($qDefaultQuery, { requireWritable: true });
        if (!resolved.ok) return JSON.stringify(resolved);
        const target = resolved.el;
        const doc = resolved.doc || document;
        const win = resolved.win || window;
        const tag = (target.tagName || '').toLowerCase();
        const role = (target.getAttribute && target.getAttribute('role') || '').toLowerCase();
        let wantedRaw = String($qVal || '').trim();
        if (typeof target.scrollIntoView === 'function') {
            try { target.scrollIntoView({ behavior: 'auto', block: 'center', inline: 'nearest' }); } catch (_) {}
        }

        const targetType = (target.getAttribute && target.getAttribute('type') || '').toLowerCase();
        if (!wantedRaw && (tag === 'input' || tag === 'textarea' || tag === 'select')) {
            try { target.focus(); } catch (_) {}
            try { target.click(); } catch (_) {}
            return JSON.stringify({
                ok: true,
                code: 'NONE',
                verified: true,
                resolvedSelector: resolved.resolvedSelector || '',
                recoveredViaFallback: Boolean(resolved.recoveredViaFallback),
                diagnostics: resolved.diagnostics || null,
                msg: 'Focused "' + $qLabel + '".'
            });
        }
        if (tag === 'input' && ['date', 'datetime-local', 'time', 'month', 'week'].indexOf(targetType) >= 0) {
            if (targetType === 'date' || targetType === 'month') {
                wantedRaw = apexAdaptDateValueForTarget(target, wantedRaw);
            }
            const validDate = targetType !== 'date' || /^\d{4}-\d{2}-\d{2}$/.test(wantedRaw);
            const validTime = targetType !== 'time' || /^\d{2}:\d{2}(:\d{2})?$/.test(wantedRaw);
            const validDtLocal = targetType !== 'datetime-local' || /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(:\d{2})?$/.test(wantedRaw);
            const validMonth = targetType !== 'month' || /^\d{4}-\d{2}$/.test(wantedRaw);
            const validWeek = targetType !== 'week' || /^\d{4}-W\d{2}$/.test(wantedRaw);
            if (!wantedRaw || !validDate || !validTime || !validDtLocal || !validMonth || !validWeek) {
                return JSON.stringify({
                    ok: false,
                    code: 'VERIFICATION_FAILED',
                    msg: 'Invalid ' + targetType + ' value format for "' + $qLabel + '".'
                });
            }
            const minVal = target.getAttribute('min') || '';
            const maxVal = target.getAttribute('max') || '';
            if ((minVal && wantedRaw < minVal) || (maxVal && wantedRaw > maxVal)) {
                return JSON.stringify({
                    ok: false,
                    code: 'VERIFICATION_FAILED',
                    msg: 'Date/time value for "' + $qLabel + '" is outside [' + minVal + '..' + maxVal + '].'
                });
            }
            target.value = wantedRaw;
            target.dispatchEvent(new Event('input', { bubbles: true }));
            target.dispatchEvent(new Event('change', { bubbles: true }));
            if (String(target.value || '') !== wantedRaw) {
                return JSON.stringify({
                    ok: false,
                    code: 'VERIFICATION_FAILED',
                    msg: 'Date/time value did not persist in "' + $qLabel + '".'
                });
            }
            return JSON.stringify({
                ok: true,
                code: 'NONE',
                selectedValue: target.value,
                verified: true,
                resolvedSelector: resolved.resolvedSelector || '',
                recoveredViaFallback: Boolean(resolved.recoveredViaFallback),
                msg: 'Set ' + targetType + ' value "' + wantedRaw + '" for "' + $qLabel + '".'
            });
        }

        if (tag === 'select') {
            return JSON.stringify(apexSelectNativeOption(target, wantedRaw, $qLabel, resolved));
        }

        if (tag === 'option' || role === 'option' || role === 'menuitem' || role === 'menuitemradio' ||
            (role === 'gridcell' && !target.querySelector('[role="gridcell"]')) ||
            (target.hasAttribute('data-year') && !target.hasAttribute('data-year-picker'))) {
            if (apexIsDisabled(target)) {
                return JSON.stringify({ ok: false, code: 'ELEMENT_DISABLED', msg: 'Option "' + $qLabel + '" is disabled.' });
            }
            apexDispatchMouseClick(target, win);
            if (tag === 'option' && target.parentElement && target.parentElement.tagName && target.parentElement.tagName.toLowerCase() === 'select') {
                target.selected = true;
                target.parentElement.value = target.value;
                target.parentElement.dispatchEvent(new Event('input', { bubbles: true }));
                target.parentElement.dispatchEvent(new Event('change', { bubbles: true }));
            }
            return JSON.stringify({
                ok: true,
                code: 'NONE',
                verified: true,
                resolvedSelector: resolved.resolvedSelector || '',
                recoveredViaFallback: Boolean(resolved.recoveredViaFallback),
                diagnostics: resolved.diagnostics || null,
                msg: 'Selected option "' + $qLabel + '".'
            });
        }

        const isDirectTextInput = (tag === 'input' || tag === 'textarea') &&
            ['button', 'submit', 'reset', 'checkbox', 'radio', 'hidden'].indexOf(targetType) < 0 &&
            !apexIsReadOnly(target) &&
            role !== 'combobox' && role !== 'listbox' &&
            !target.getAttribute('aria-controls') && !target.getAttribute('aria-haspopup') &&
            !target.hasAttribute('data-datepicker') && !target.hasAttribute('data-year-picker');

        if (isDirectTextInput && wantedRaw) {
            if (tag === 'input') {
                wantedRaw = apexAdaptDateValueForTarget(target, wantedRaw);
            }
            if (tag === 'input' && /^\d{4}-\d{2}-\d{2}$/.test(wantedRaw) && apexIsBirthYearCandidateEl(target)) {
                const maxLen = parseInt(target.getAttribute('maxlength') || '0', 10);
                const ac = String(target.getAttribute('autocomplete') || '').toLowerCase();
                const ph = String(target.getAttribute('placeholder') || '').trim();
                if (maxLen === 4 || ac === 'bday-year' || /^(yyyy|19\d{2}|20\d{2})$/i.test(ph) || targetType === 'number') {
                    wantedRaw = wantedRaw.slice(0, 4);
                }
            }
            if (tag === 'input' && (targetType === 'number' || apexIsBirthYearCandidateEl(target)) && /^-?\d+$/.test(wantedRaw)) {
                const numVal = parseInt(wantedRaw, 10);
                const minAttr = target.getAttribute('min') || target.getAttribute('data-min') || '';
                const maxAttr = target.getAttribute('max') || target.getAttribute('data-max') || '';
                const minNum = minAttr ? parseInt(minAttr, 10) : NaN;
                const maxNum = maxAttr ? parseInt(maxAttr, 10) : NaN;
                if ((!isNaN(minNum) && numVal < minNum) || (!isNaN(maxNum) && numVal > maxNum)) {
                    return JSON.stringify({
                        ok: false,
                        code: 'VERIFICATION_FAILED',
                        msg: 'Year/numeric value "' + wantedRaw + '" for "' + $qLabel + '" is outside allowed range [' + minAttr + '..' + maxAttr + '].'
                    });
                }
            }
            try { target.focus(); } catch (_) {}
            const proto = tag === 'textarea' ? win.HTMLTextAreaElement && win.HTMLTextAreaElement.prototype : win.HTMLInputElement && win.HTMLInputElement.prototype;
            const descriptor = proto ? Object.getOwnPropertyDescriptor(proto, 'value') : null;
            if (descriptor && descriptor.set) {
                descriptor.set.call(target, wantedRaw);
            } else {
                target.value = wantedRaw;
            }
            try { target.dispatchEvent(new Event('beforeinput', { bubbles: true, cancelable: true })); } catch (_) {}
            target.dispatchEvent(new Event('input', { bubbles: true }));
            target.dispatchEvent(new Event('change', { bubbles: true }));
            const actualVal = String(target.value || '');
            const selectDirectMatches = actualVal === wantedRaw ||
                (targetType === 'number' && /^-?\d+$/.test(wantedRaw.trim()) && /^-?\d+$/.test(actualVal.trim()) && parseInt(actualVal.trim(), 10) === parseInt(wantedRaw.trim(), 10));
            if (!selectDirectMatches) {
                return JSON.stringify({
                    ok: false,
                    code: 'VERIFICATION_FAILED',
                    msg: 'Value "' + wantedRaw + '" did not persist in "' + $qLabel + '" (actual: "' + actualVal + '").'
                });
            }
            return JSON.stringify({
                ok: true,
                code: 'NONE',
                selectedValue: actualVal,
                verified: true,
                resolvedSelector: resolved.resolvedSelector || '',
                recoveredViaFallback: Boolean(resolved.recoveredViaFallback),
                diagnostics: resolved.diagnostics || null,
                msg: 'Entered "' + wantedRaw + '" into "' + $qLabel + '".'
            });
        }

        // Custom dropdown / combobox / listbox / React/Vue datepicker / calendar dialog / scrollable year picker
        if (wantedRaw || apexIsCustomPickerOrDropdown(target)) {
            return JSON.stringify(apexSelectCustomPickerOption(target, doc, win, wantedRaw, $qLabel, resolved));
        }

        target.click();
        return JSON.stringify({
            ok: true,
            code: 'NONE',
            resolvedSelector: resolved.resolvedSelector || '',
            recoveredViaFallback: Boolean(resolved.recoveredViaFallback),
            diagnostics: resolved.diagnostics || null,
            msg: 'Activated "' + $qLabel + '".'
        });
    """.trimIndent()

    private fun buildFocusBlurScriptBody(
        isFocus: Boolean,
        qLabel: String,
        qDefaultQuery: String
    ): String = """
        const resolved = apexResolveOrderedTarget($qDefaultQuery, {});
        if (!resolved.ok) return JSON.stringify(resolved);
        const target = resolved.el;
        const doc = resolved.doc || document;
        const root = (target.getRootNode && target.getRootNode()) || doc;
        if (typeof target.scrollIntoView === 'function') {
            try { target.scrollIntoView({ behavior: 'auto', block: 'center', inline: 'nearest' }); } catch (_) {}
        }
        if (${if (isFocus) "true" else "false"}) {
            if (typeof target.focus !== 'function') {
                return JSON.stringify({ ok: false, code: 'ELEMENT_NOT_INTERACTABLE', msg: 'Element cannot be focused.' });
            }
            target.focus();
            target.dispatchEvent(new Event('focus', { bubbles: false }));
            const activeMatch = (root && root.activeElement === target) || doc.activeElement === target;
            if (!activeMatch && target.tabIndex < 0 && !/^(input|textarea|select|button|a)$/.test((target.tagName || '').toLowerCase())) {
                return JSON.stringify({ ok: false, code: 'VERIFICATION_FAILED', msg: 'Element "' + $qLabel + '" did not receive focus.' });
            }
            return JSON.stringify({
                ok: true,
                code: 'NONE',
                focused: true,
                verified: true,
                resolvedSelector: resolved.resolvedSelector || '',
                recoveredViaFallback: Boolean(resolved.recoveredViaFallback),
                diagnostics: resolved.diagnostics || null,
                msg: 'Focused "' + $qLabel + '".'
            });
        } else {
            if (typeof target.blur === 'function') target.blur();
            target.dispatchEvent(new Event('blur', { bubbles: false }));
            const stillFocused = (root && root.activeElement === target) || doc.activeElement === target;
            if (stillFocused) {
                return JSON.stringify({ ok: false, code: 'VERIFICATION_FAILED', msg: 'Element "' + $qLabel + '" remained focused after blur.' });
            }
            return JSON.stringify({
                ok: true,
                code: 'NONE',
                blurred: true,
                verified: true,
                resolvedSelector: resolved.resolvedSelector || '',
                recoveredViaFallback: Boolean(resolved.recoveredViaFallback),
                diagnostics: resolved.diagnostics || null,
                msg: 'Blurred "' + $qLabel + '".'
            });
        }
    """.trimIndent()

    private fun buildKeyPressScriptBody(
        qKey: String,
        qLabel: String,
        qDefaultQuery: String
    ): String = """
        const rawKey = String($qKey || '').trim() || 'Enter';
        const lowerKey = rawKey.toLowerCase();
        const keyMap = {
            'enter': { key: 'Enter', code: 'Enter', keyCode: 13 },
            'return': { key: 'Enter', code: 'Enter', keyCode: 13 },
            'escape': { key: 'Escape', code: 'Escape', keyCode: 27 },
            'esc': { key: 'Escape', code: 'Escape', keyCode: 27 },
            'tab': { key: 'Tab', code: 'Tab', keyCode: 9 },
            'space': { key: ' ', code: 'Space', keyCode: 32 },
            ' ': { key: ' ', code: 'Space', keyCode: 32 },
            'arrowdown': { key: 'ArrowDown', code: 'ArrowDown', keyCode: 40 },
            'down': { key: 'ArrowDown', code: 'ArrowDown', keyCode: 40 },
            'arrowup': { key: 'ArrowUp', code: 'ArrowUp', keyCode: 38 },
            'up': { key: 'ArrowUp', code: 'ArrowUp', keyCode: 38 },
            'arrowleft': { key: 'ArrowLeft', code: 'ArrowLeft', keyCode: 37 },
            'left': { key: 'ArrowLeft', code: 'ArrowLeft', keyCode: 37 },
            'arrowright': { key: 'ArrowRight', code: 'ArrowRight', keyCode: 39 },
            'right': { key: 'ArrowRight', code: 'ArrowRight', keyCode: 39 },
            'backspace': { key: 'Backspace', code: 'Backspace', keyCode: 8 }
        };
        const spec = keyMap[lowerKey] || { key: rawKey, code: rawKey, keyCode: rawKey.charCodeAt(0) || 0 };
        let target = null;
        let resolvedMeta = { resolvedSelector: '', recoveredViaFallback: false };
        if (CANDIDATE_SELECTORS.length > 0 || ($qLabel || '').trim().length > 0) {
            const resolved = apexResolveOrderedTarget($qDefaultQuery, {});
            if (!resolved.ok) return JSON.stringify(resolved);
            target = resolved.el;
            resolvedMeta = resolved;
        } else {
            target = document.activeElement || document.body;
        }
        if (!target) {
            return JSON.stringify({ ok: false, code: 'ELEMENT_NOT_FOUND', msg: 'No target element available for key press.' });
        }
        try { if (typeof target.focus === 'function') target.focus(); } catch (_) {}
        const evInit = { key: spec.key, code: spec.code, keyCode: spec.keyCode, which: spec.keyCode, bubbles: true, cancelable: true };
        const downNotCanceled = target.dispatchEvent(new KeyboardEvent('keydown', evInit));
        target.dispatchEvent(new KeyboardEvent('keypress', evInit));
        target.dispatchEvent(new KeyboardEvent('keyup', evInit));

        if (downNotCanceled) {
            const tag = (target.tagName || '').toLowerCase();
            if (spec.key === 'Enter') {
                if (tag === 'button' || tag === 'a' || target.getAttribute('role') === 'button') {
                    target.click();
                } else if (target.form && !target.form.dataset.apexSubmitted) {
                    target.form.dataset.apexSubmitted = '1';
                    if (typeof target.form.requestSubmit === 'function') target.form.requestSubmit();
                    else target.form.submit();
                }
            } else if (spec.key === ' ' && (tag === 'button' || tag === 'input' || target.getAttribute('role') === 'checkbox' || target.getAttribute('role') === 'button')) {
                target.click();
            } else if (spec.key === 'Escape' && target.getAttribute('aria-expanded') === 'true') {
                target.setAttribute('aria-expanded', 'false');
            }
        }
        return JSON.stringify({
            ok: true,
            code: 'NONE',
            key: spec.key,
            verified: true,
            resolvedSelector: resolvedMeta.resolvedSelector || '',
            recoveredViaFallback: Boolean(resolvedMeta.recoveredViaFallback),
            diagnostics: resolvedMeta.diagnostics || null,
            msg: 'Pressed ' + spec.key + ' on "' + ($qLabel || 'active element') + '".'
        });
    """.trimIndent()

    private fun buildClickOrSubmitScriptBody(
        isSubmit: Boolean,
        qLabel: String,
        qDefaultQuery: String
    ): String = """
        const resolved = apexResolveOrderedTarget($qDefaultQuery, {});
        if (!resolved.ok) return JSON.stringify(resolved);
        const target = resolved.el;
        const doc = resolved.doc || document;
        const tag = (target.tagName || '').toLowerCase();
        const form = target.form || (target.closest ? target.closest('form') : null);
        if (${if (isSubmit) "true" else "false"}) {
            if (target.dataset && target.dataset.apexSubmitted === '1') {
                return JSON.stringify({ ok: false, code: 'DUPLICATE_EXECUTION', msg: 'Prevented duplicate form submission for "' + $qLabel + '".' });
            }
            if (form && form.dataset && form.dataset.apexSubmitted === '1') {
                return JSON.stringify({ ok: false, code: 'DUPLICATE_EXECUTION', msg: 'Form was already submitted in this page session.' });
            }
        }
        if (typeof target.scrollIntoView === 'function') {
            try { target.scrollIntoView({ behavior: 'auto', block: 'center', inline: 'nearest' }); } catch (_) {}
        }
        const preExpanded = target.getAttribute ? target.getAttribute('aria-expanded') : null;
        const prePressed = target.getAttribute ? target.getAttribute('aria-pressed') : null;
        const preSelected = target.getAttribute ? target.getAttribute('aria-selected') : null;
        const preChecked = typeof target.checked === 'boolean' ? target.checked : null;
        const preHref = tag === 'a' ? (target.getAttribute('href') || '') : '';
        const preCount = doc.body ? doc.body.getElementsByTagName('*').length : 0;

        try { if (typeof target.focus === 'function') target.focus(); } catch (_) {}
        if (${if (isSubmit) "true" else "false"}) {
            if (target.dataset) target.dataset.apexSubmitted = '1';
            if (form && form.dataset) form.dataset.apexSubmitted = '1';
            if (tag === 'form') {
                if (typeof target.requestSubmit === 'function') target.requestSubmit();
                else target.submit();
            } else {
                target.click();
            }
        } else {
            target.click();
        }

        const postExpanded = target.getAttribute ? target.getAttribute('aria-expanded') : null;
        const postPressed = target.getAttribute ? target.getAttribute('aria-pressed') : null;
        const postSelected = target.getAttribute ? target.getAttribute('aria-selected') : null;
        const postChecked = typeof target.checked === 'boolean' ? target.checked : null;
        const postCount = doc.body ? doc.body.getElementsByTagName('*').length : 0;
        const stateDelta = (preExpanded !== postExpanded) || (prePressed !== postPressed) ||
            (preSelected !== postSelected) || (preChecked !== postChecked) ||
            (preCount !== postCount) || Boolean(preHref) || Boolean(form);

        return JSON.stringify({
            ok: true,
            code: 'NONE',
            verified: true,
            stateChanged: stateDelta,
            mayTriggerNavigation: Boolean(preHref || ${if (isSubmit) "true" else "false"} || (tag === 'button' && ((target.getAttribute('type') || 'submit').toLowerCase() === 'submit') && Boolean(form))),
            resolvedSelector: resolved.resolvedSelector || '',
            recoveredViaFallback: Boolean(resolved.recoveredViaFallback),
            diagnostics: resolved.diagnostics || null,
            msg: '${if (isSubmit) "Submitted" else "Activated"} "' + $qLabel + '".'
        });
    """.trimIndent()

    private fun buildDoubleClickScriptBody(
        qLabel: String,
        qDefaultQuery: String
    ): String = """
        const resolved = apexResolveOrderedTarget($qDefaultQuery, {});
        if (!resolved.ok) return JSON.stringify(resolved);
        const target = resolved.el;
        const win = resolved.win || window;
        if (typeof target.scrollIntoView === 'function') {
            try { target.scrollIntoView({ behavior: 'auto', block: 'center', inline: 'nearest' }); } catch (_) {}
        }
        try { if (typeof target.focus === 'function') target.focus(); } catch (_) {}
        try {
            target.dispatchEvent(new MouseEvent('mousedown', { bubbles: true, cancelable: true, detail: 1, view: win }));
            target.dispatchEvent(new MouseEvent('mouseup', { bubbles: true, cancelable: true, detail: 1, view: win }));
            target.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true, detail: 1, view: win }));
            target.dispatchEvent(new MouseEvent('mousedown', { bubbles: true, cancelable: true, detail: 2, view: win }));
            target.dispatchEvent(new MouseEvent('mouseup', { bubbles: true, cancelable: true, detail: 2, view: win }));
            target.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true, detail: 2, view: win }));
            target.dispatchEvent(new MouseEvent('dblclick', { bubbles: true, cancelable: true, detail: 2, view: win }));
        } catch (_) {
            try { target.click(); target.click(); } catch (_) {}
        }
        return JSON.stringify({
            ok: true,
            code: 'NONE',
            verified: true,
            resolvedSelector: resolved.resolvedSelector || '',
            recoveredViaFallback: Boolean(resolved.recoveredViaFallback),
            diagnostics: resolved.diagnostics || null,
            msg: 'Double-clicked "' + $qLabel + '".'
        });
    """.trimIndent()

    private fun buildLongPressScriptBody(
        qLabel: String,
        qDefaultQuery: String
    ): String = """
        const resolved = apexResolveOrderedTarget($qDefaultQuery, {});
        if (!resolved.ok) return JSON.stringify(resolved);
        const target = resolved.el;
        const win = resolved.win || window;
        if (typeof target.scrollIntoView === 'function') {
            try { target.scrollIntoView({ behavior: 'auto', block: 'center', inline: 'nearest' }); } catch (_) {}
        }
        try { if (typeof target.focus === 'function') target.focus(); } catch (_) {}
        try {
            if (typeof PointerEvent !== 'undefined') {
                target.dispatchEvent(new PointerEvent('pointerdown', { bubbles: true, cancelable: true, pointerType: 'touch' }));
            }
            target.dispatchEvent(new MouseEvent('mousedown', { bubbles: true, cancelable: true, view: win }));
            target.dispatchEvent(new MouseEvent('contextmenu', { bubbles: true, cancelable: true, view: win }));
            if (typeof PointerEvent !== 'undefined') {
                target.dispatchEvent(new PointerEvent('pointerup', { bubbles: true, cancelable: true, pointerType: 'touch' }));
            }
            target.dispatchEvent(new MouseEvent('mouseup', { bubbles: true, cancelable: true, view: win }));
        } catch (_) {}
        return JSON.stringify({
            ok: true,
            code: 'NONE',
            verified: true,
            resolvedSelector: resolved.resolvedSelector || '',
            recoveredViaFallback: Boolean(resolved.recoveredViaFallback),
            diagnostics: resolved.diagnostics || null,
            msg: 'Long-pressed "' + $qLabel + '".'
        });
    """.trimIndent()

    private fun buildExtractScriptBody(qSel: String, qLabel: String): String = """
        const q = String($qSel || '').trim() || 'h1, h2, h3, article, p';
        const nodes = apexDeepQueryAll(q)
            .filter(function(el) { return apexIsConnected(el) && apexIsVisible(el) && !apexIsSensitiveEl(el); })
            .slice(0, 12);
        const extracted = nodes.map(function(n) {
            return (n.innerText || n.textContent || '').replace(/\s+/g, ' ').trim();
        }).filter(function(t) { return t.length > 0; });
        return JSON.stringify({
            ok: true,
            code: 'NONE',
            count: extracted.length,
            msg: 'Extracted ' + extracted.length + ' text items for "' + $qLabel + '".'
        });
    """.trimIndent()

    fun buildWaitProbeScript(condition: WaitConditionSpec): String {
        val qSel = JSONObject.quote(condition.selector)
        val qLabel = JSONObject.quote(condition.targetLabel)
        val qAttr = JSONObject.quote(condition.attributeName)
        val qAttrVal = JSONObject.quote(condition.expectedAttributeValue)
        val qText = JSONObject.quote(condition.expectedText)
        val qCondType = JSONObject.quote(condition.type.id)
        val helpers = buildTargetHelpersJs(condition.targetLabel)
        return """
            (function() {
                try {
                    $helpers
                    const condType = $qCondType;
                    const sel = $qSel;
                    const labelHint = $qLabel;
                    const docUrl = String(document.URL || window.location && window.location.href || '');
                    const ready = String(document.readyState || 'loading');
                    const domCount = document.body ? document.body.getElementsByTagName('*').length : 0;
                    const textLen = document.body ? String(document.body.innerText || document.body.textContent || '').trim().length : 0;
                    const sig = ready + ':' + domCount + ':' + textLen;

                    if (condType === 'CONTENT_STABILIZED') {
                        return JSON.stringify({
                            satisfied: ready === 'complete' && Boolean(document.body),
                            readyState: ready,
                            url: docUrl,
                            signature: sig
                        });
                    }
                    if (condType === 'URL_CHANGE' || condType === 'ORIGIN_CHANGE' || condType === 'NAVIGATION_COMPLETED') {
                        return JSON.stringify({
                            satisfied: ready === 'complete',
                            readyState: ready,
                            url: docUrl,
                            signature: sig
                        });
                    }
                    if (condType === 'DOM_STATE_CHANGE') {
                        const expectedText = $qText;
                        const attrName = $qAttr;
                        const attrExpected = $qAttrVal;
                        if (sel || labelHint) {
                            const res = apexResolveTarget(sel, labelHint, '*', { allowOffscreen: true, ignoreDisabled: true });
                            if (!res.ok) return JSON.stringify({ satisfied: false, reason: res.msg || res.code, url: docUrl, signature: sig });
                            const el = res.el;
                            let ok = true;
                            if (attrName) {
                                const actualAttr = String(el.getAttribute ? (el.getAttribute(attrName) || '') : '');
                                ok = attrExpected ? (actualAttr === attrExpected) : actualAttr.length > 0;
                            }
                            if (ok && expectedText) {
                                const actualTxt = String(el.value || el.innerText || el.textContent || '');
                                ok = actualTxt.toLowerCase().indexOf(expectedText.toLowerCase()) >= 0;
                            }
                            return JSON.stringify({ satisfied: ok, url: docUrl, signature: sig });
                        } else {
                            const bodyTxt = document.body ? String(document.body.innerText || document.body.textContent || '') : '';
                            const ok = expectedText ? (bodyTxt.toLowerCase().indexOf(expectedText.toLowerCase()) >= 0) : (ready === 'complete');
                            return JSON.stringify({ satisfied: ok, url: docUrl, signature: sig });
                        }
                    }

                    const matches = sel ? apexQueryChain(sel) : { ok: true, matches: apexDeepQueryAll('*') };
                    const connected = (matches.matches || []).filter(function(el) {
                        if (!apexIsConnected(el)) return false;
                        if (!sel && labelHint) {
                            return apexResolveLabel(el).indexOf(apexNorm(labelHint)) >= 0;
                        }
                        return true;
                    });

                    if (condType === 'ELEMENT_DISAPPEARS') {
                        const anyVisible = connected.some(function(el) { return apexIsVisible(el); });
                        return JSON.stringify({ satisfied: !anyVisible, matchCount: connected.length, url: docUrl, signature: sig });
                    }
                    if (condType === 'ELEMENT_APPEARS') {
                        const anyVisible = connected.some(function(el) { return apexIsVisible(el); });
                        return JSON.stringify({ satisfied: anyVisible, matchCount: connected.length, url: docUrl, signature: sig });
                    }
                    if (condType === 'ELEMENT_INTERACTABLE') {
                        const anyReady = connected.some(function(el) { return apexIsVisible(el) && !apexIsDisabled(el); });
                        return JSON.stringify({ satisfied: anyReady, matchCount: connected.length, url: docUrl, signature: sig });
                    }
                    return JSON.stringify({ satisfied: false, url: docUrl, signature: sig });
                } catch (e) {
                    return JSON.stringify({ satisfied: false, error: String(e && e.message ? e.message : e) });
                }
            })();
        """.trimIndent()
    }

    fun buildHighlightSelectorScript(
        selector: String,
        targetLabel: String = "",
        scrollIntoView: Boolean = true
    ): String {
        val qSel = JSONObject.quote(selector)
        val qLabel = JSONObject.quote(targetLabel)
        val scrollLit = if (scrollIntoView) "true" else "false"
        return """
            (function() {
                try {
                    ${buildTargetHelpersJs(targetLabel)}
                    const resolved = apexResolveTarget($qSel, $qLabel, '*', { allowOffscreen: true, ignoreDisabled: true });
                    if (!resolved.ok) {
                        return JSON.stringify({ ok: false, code: resolved.code || 'ELEMENT_NOT_FOUND', msg: resolved.msg || 'Element not found' });
                    }
                    const target = resolved.el;
                    if ($scrollLit && target.scrollIntoView) {
                        target.scrollIntoView({ behavior: 'smooth', block: 'center', inline: 'nearest' });
                    }
                    apexHighlightInteractedElement(target);
                    const rect = apexGetElementRect(target);
                    return JSON.stringify({
                        ok: true,
                        resolvedSelector: resolved.resolvedSelector || $qSel,
                        tagName: (target.tagName || '').toLowerCase(),
                        targetRect: rect
                    });
                } catch (e) {
                    return JSON.stringify({ ok: false, error: String(e && e.message ? e.message : e) });
                }
            })();
        """.trimIndent()
    }

    fun buildClearHighlightsScript(): String {
        return """
            (function() {
                try {
                    const list = window.__apexHighlightedElements || [];
                    for (let i = 0; i < list.length; i++) {
                        const item = list[i];
                        if (item && item.el && item.el.style) {
                            item.el.style.outline = item.outline || '';
                            item.el.style.outlineOffset = item.outlineOffset || '';
                            item.el.style.boxShadow = item.boxShadow || '';
                            item.el.style.transition = item.transition || '';
                        }
                    }
                    window.__apexHighlightedElements = [];
                    return JSON.stringify({ ok: true });
                } catch (e) {
                    return JSON.stringify({ ok: false });
                }
            })();
        """.trimIndent()
    }
}
