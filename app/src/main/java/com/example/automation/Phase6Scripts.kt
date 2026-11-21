package com.example.automation

import org.json.JSONArray
import org.json.JSONObject

object Phase6Scripts {

    private fun buildCandidateListJson(
        primarySelector: String,
        locatorCandidates: List<LocatorCandidate>
    ): String {
        val arr = JSONArray()
        val seen = LinkedHashSet<String>()
        if (primarySelector.isNotBlank() && seen.add(primarySelector.trim())) {
            arr.put(primarySelector.trim())
        }
        locatorCandidates
            .sortedWith(compareByDescending<LocatorCandidate> { it.isUnique }.thenByDescending { it.confidence })
            .forEach { cand ->
                val clean = cand.selector.trim()
                if (clean.isNotEmpty() && seen.add(clean)) {
                    arr.put(clean)
                }
            }
        return arr.toString()
    }

    fun buildConditionProbeScript(
        operator: ConditionOperator,
        selector: String,
        targetLabel: String,
        expectedValue: String,
        locatorCandidates: List<LocatorCandidate> = emptyList(),
        framePath: List<String> = emptyList(),
        shadowHostPath: List<String> = emptyList()
    ): String {
        val helpers = UniversalActionScripts.buildTargetHelpersJs(
            labelHint = targetLabel,
            framePath = framePath,
            shadowHostPath = shadowHostPath
        )
        val qOp = JSONObject.quote(operator.id)
        val qSel = JSONObject.quote(selector.trim())
        val qHint = JSONObject.quote(targetLabel.trim())
        val qExpected = JSONObject.quote(expectedValue)
        val candidatesJson = buildCandidateListJson(selector, locatorCandidates)

        return """
            (function() {
                try {
                    $helpers
                    const op = $qOp;
                    const sel = $qSel;
                    const hint = $qHint;
                    const expected = $qExpected;
                    const candidateList = $candidatesJson;

                    function findTarget(allowHidden) {
                        const res = apexResolveWithCandidates(
                            candidateList,
                            hint,
                            'button, a, input, select, textarea, h1, h2, h3, p, span, div, [role]',
                            { allowOffscreen: allowHidden, ignoreDisabled: true }
                        );
                        if (res && res.ok && res.el) {
                            return { found: true, el: res.el };
                        }
                        if (res && res.code === 'INVALID_SELECTOR') {
                            return { found: false, invalid: true, code: 'INVALID_SELECTOR', msg: res.msg || 'Invalid selector' };
                        }
                        if (res && res.code === 'CROSS_ORIGIN_BLOCKED') {
                            return { found: false, invalid: true, code: 'CROSS_ORIGIN_BLOCKED', msg: res.msg || 'Cross-origin frame blocked' };
                        }
                        if (res && res.code === 'ELEMENT_NOT_INTERACTABLE' && !allowHidden) {
                            return findTarget(true);
                        }
                        return { found: false, msg: (res && res.msg) || 'Element not found' };
                    }

                    const target = findTarget(true);
                    if (target.invalid) {
                        return JSON.stringify({ ok: false, matched: false, code: target.code, msg: target.msg });
                    }
                    if (op === 'ELEMENT_EXISTS') {
                        return JSON.stringify({ ok: true, matched: Boolean(target.found), detail: target.found ? 'Element exists' : 'Element missing' });
                    }
                    if (op === 'ELEMENT_NOT_EXISTS') {
                        return JSON.stringify({ ok: true, matched: !target.found, detail: !target.found ? 'Element absent' : 'Element present' });
                    }
                    if (!target.found || !target.el) {
                        return JSON.stringify({ ok: true, matched: false, detail: target.msg || 'Element not found' });
                    }
                    const el = target.el;
                    if (op === 'ELEMENT_VISIBLE') {
                        const vis = apexIsVisible(el);
                        return JSON.stringify({ ok: true, matched: vis, detail: vis ? 'Element visible' : 'Element hidden' });
                    }
                    if (op === 'ELEMENT_ENABLED') {
                        const en = !apexIsDisabled(el);
                        return JSON.stringify({ ok: true, matched: en, detail: en ? 'Element enabled' : 'Element disabled' });
                    }
                    if (op === 'ELEMENT_CONTAINS_TEXT') {
                        const txt = String(el.innerText || el.textContent || el.value || '').trim();
                        const m = txt.toLowerCase().indexOf(String(expected || '').toLowerCase()) >= 0;
                        return JSON.stringify({ ok: true, matched: m, detail: m ? 'Matched text' : 'Text mismatch' });
                    }
                    if (op === 'INPUT_VALUE_EQUALS') {
                        const tag = (el.tagName || '').toLowerCase();
                        if (apexIsSensitiveEl(el)) {
                            return JSON.stringify({ ok: false, matched: false, code: 'EXTRACTION_SENSITIVE_BLOCKED', msg: 'Cannot inspect sensitive credential field in condition' });
                        }
                        const val = tag === 'select' ? String(el.value || '') : String(el.value != null ? el.value : (el.textContent || ''));
                        const m = val === String(expected || '');
                        return JSON.stringify({ ok: true, matched: m, detail: m ? 'Input matches' : 'Input value mismatch' });
                    }
                    if (op === 'CHECKBOX_CHECKED') {
                        const ariaChk = String(el.getAttribute && el.getAttribute('aria-checked') || '').toLowerCase();
                        const isChecked = Boolean(el.checked) || ariaChk === 'true';
                        return JSON.stringify({ ok: true, matched: isChecked, detail: isChecked ? 'Checked' : 'Unchecked' });
                    }
                    return JSON.stringify({ ok: false, matched: false, code: 'CONDITION_INVALID', msg: 'Unsupported DOM condition ' + op });
                } catch (e) {
                    return JSON.stringify({ ok: false, matched: false, code: 'JS_EXCEPTION', msg: (e && e.message) ? e.message : String(e) });
                }
            })();
        """.trimIndent()
    }

    fun buildStructuredExtractScript(
        step: AutomationStep,
        spec: ExtractionSpec
    ): String {
        val helpers = UniversalActionScripts.buildTargetHelpersJs(
            labelHint = step.targetLabel,
            framePath = step.framePath,
            shadowHostPath = step.shadowHostPath
        )
        val qMode = JSONObject.quote(spec.mode.id)
        val qSel = JSONObject.quote(step.selector.trim())
        val qHint = JSONObject.quote(step.targetLabel.trim())
        val qAttr = JSONObject.quote(spec.attributeName.trim().lowercase())
        val qMetaKey = JSONObject.quote(spec.metaKey.trim().lowercase())
        val allowMulti = spec.allowMultiple
        val maxItems = spec.maxItems.coerceIn(1, Phase6Limits.MAX_EXTRACT_LIST_ITEMS)
        val rejectAmbiguous = spec.rejectAmbiguous
        val candidateListJson = buildCandidateListJson(step.selector, step.locatorCandidates)

        return """
            (function() {
                try {
                    $helpers
                    const mode = $qMode;
                    const sel = $qSel;
                    const hint = $qHint;
                    const attrName = $qAttr;
                    const metaKey = $qMetaKey;
                    const allowMultiple = $allowMulti;
                    const maxItems = $maxItems;
                    const rejectAmbiguous = $rejectAmbiguous;
                    const candidateList = $candidateListJson;

                    if (mode === 'PAGE_TITLE') {
                        const title = String(document.title || '');
                        return JSON.stringify({ ok: true, code: 'NONE', value: title, items: [title], count: 1, msg: 'Extracted page title' });
                    }
                    if (mode === 'PAGE_URL') {
                        const u = String(window.location && window.location.href || '');
                        return JSON.stringify({ ok: true, code: 'NONE', value: u, items: [u], count: 1, msg: 'Extracted page URL' });
                    }
                    if (mode === 'PAGE_META') {
                        const blockedMeta = ['csrf', 'xsrf', 'auth', 'token', 'secret', 'api-key', 'session'];
                        if (metaKey && blockedMeta.some(function(b) { return metaKey.indexOf(b) !== -1; })) {
                            return JSON.stringify({ ok: false, code: 'EXTRACTION_SENSITIVE_BLOCKED', msg: 'Blocked sensitive meta key: ' + metaKey });
                        }
                        if (metaKey) {
                            const safeMetaEsc = metaKey.replace(/"/g, '\\"');
                            const mEl = document.querySelector('meta[name="' + safeMetaEsc + '"], meta[property="' + safeMetaEsc + '"]');
                            if (!mEl) {
                                return JSON.stringify({ ok: false, code: 'ELEMENT_NOT_FOUND', msg: 'Meta tag "' + metaKey + '" not found' });
                            }
                            const content = String(mEl.getAttribute('content') || '');
                            return JSON.stringify({ ok: true, code: 'NONE', value: content, items: [content], count: 1, msg: 'Extracted metadata ' + metaKey });
                        }
                        const desc = document.querySelector('meta[name="description"]');
                        const val = desc ? String(desc.getAttribute('content') || '') : String(document.title || '');
                        return JSON.stringify({ ok: true, code: 'NONE', value: val, items: [val], count: 1, msg: 'Extracted page metadata' });
                    }

                    const allowedAttrs = ['href', 'src', 'alt', 'title', 'aria-label', 'data-testid', 'data-test', 'role', 'type', 'name', 'placeholder', 'lang'];
                    if (mode === 'ATTRIBUTE' && allowedAttrs.indexOf(attrName) === -1) {
                        return JSON.stringify({ ok: false, code: 'EXTRACTION_SENSITIVE_BLOCKED', msg: 'Attribute "' + attrName + '" is not in the extraction allowlist' });
                    }

                    function isSensitiveForExtract(el) {
                        if (!el) return false;
                        if (apexIsSensitiveEl(el)) return true;
                        const tag = (el.tagName || '').toLowerCase();
                        const type = String(el.getAttribute && el.getAttribute('type') || '').toLowerCase();
                        return tag === 'input' && (type === 'password' || type === 'hidden');
                    }

                    function readValueFromEl(el) {
                        if (!el) return { ok: false, code: 'ELEMENT_NOT_FOUND', msg: 'Element null' };
                        if (isSensitiveForExtract(el)) {
                            return { ok: false, code: 'EXTRACTION_SENSITIVE_BLOCKED', msg: 'Extraction blocked on sensitive credential, OTP, or payment field' };
                        }
                        const tag = (el.tagName || '').toLowerCase();
                        if (mode === 'INPUT_VALUE') {
                            if (tag === 'select') {
                                return { ok: true, val: String(el.value || '') };
                            }
                            return { ok: true, val: String(el.value != null ? el.value : (el.innerText || el.textContent || '')) };
                        }
                        if (mode === 'SELECTED_OPTION') {
                            if (tag === 'select') {
                                const opt = el.options && el.selectedIndex >= 0 ? el.options[el.selectedIndex] : null;
                                const txt = opt ? String(opt.text || opt.value || '') : String(el.value || '');
                                return { ok: true, val: txt.trim() };
                            }
                            return { ok: true, val: String((el.getAttribute && el.getAttribute('aria-valuetext')) || el.value || el.innerText || '').trim() };
                        }
                        if (mode === 'LINK_URL') {
                            const href = el.href || (el.getAttribute && el.getAttribute('href')) || '';
                            if (!href || /^javascript:/i.test(String(href).trim())) {
                                return { ok: false, code: 'EXTRACTION_SENSITIVE_BLOCKED', msg: 'Element does not have a safe link URL' };
                            }
                            return { ok: true, val: String(href) };
                        }
                        if (mode === 'ATTRIBUTE') {
                            const aVal = el.getAttribute ? (el.getAttribute(attrName) || '') : '';
                            return { ok: true, val: String(aVal) };
                        }
                        const rawTxt = (tag === 'input' || tag === 'textarea')
                            ? (el.value || '')
                            : (el.innerText || el.textContent || '');
                        return { ok: true, val: String(rawTxt).replace(/\s+/g, ' ').trim() };
                    }

                    if (allowMultiple) {
                        let nodes = [];
                        if (sel) {
                            const q = apexQueryChain(sel);
                            if (!q.ok) {
                                return JSON.stringify({ ok: false, code: q.code || 'INVALID_SELECTOR', msg: q.msg || 'Invalid selector' });
                            }
                            nodes = q.matches;
                        } else if (hint) {
                            const hNorm = apexNorm(hint);
                            nodes = apexDeepQueryAll('h1, h2, h3, h4, p, li, a, span, td, [data-testid]').filter(function(n) {
                                return apexResolveLabel(n).indexOf(hNorm) >= 0;
                            });
                        }
                        if (nodes.length === 0) {
                            return JSON.stringify({ ok: false, code: 'ELEMENT_NOT_FOUND', msg: 'No elements matched for extraction' });
                        }
                        const limited = nodes.slice(0, maxItems);
                        const extracted = [];
                        for (let i = 0; i < limited.length; i++) {
                            const r = readValueFromEl(limited[i]);
                            if (!r.ok) {
                                return JSON.stringify({ ok: false, code: r.code, msg: r.msg });
                            }
                            extracted.push(r.val);
                        }
                        return JSON.stringify({
                            ok: true,
                            code: 'NONE',
                            value: extracted.join(', '),
                            items: extracted,
                            count: extracted.length,
                            msg: 'Extracted ' + extracted.length + ' items'
                        });
                    }

                    if (sel && rejectAmbiguous && !hint) {
                        const q = apexQueryChain(sel);
                        if (!q.ok) {
                            return JSON.stringify({ ok: false, code: q.code || 'INVALID_SELECTOR', msg: q.msg || 'Invalid selector' });
                        }
                        const connected = q.matches.filter(function(el) { return apexIsConnected(el); });
                        if (connected.length > 1) {
                            return JSON.stringify({
                                ok: false,
                                code: 'EXTRACTION_AMBIGUOUS',
                                msg: 'Ambiguous extraction target: selector "' + sel + '" matched ' + connected.length + ' elements'
                            });
                        }
                    }

                    const resolved = apexResolveWithCandidates(
                        candidateList,
                        hint,
                        'h1, h2, h3, p, span, a, input:not([type="password"]), select, textarea, td, [role]',
                        { allowOffscreen: true, ignoreDisabled: true }
                    );
                    if (!resolved || !resolved.ok || !resolved.el) {
                        const code = (resolved && resolved.code === 'ELEMENT_AMBIGUOUS' && rejectAmbiguous)
                            ? 'EXTRACTION_AMBIGUOUS'
                            : ((resolved && resolved.code) || 'ELEMENT_NOT_FOUND');
                        return JSON.stringify({
                            ok: false,
                            code: code,
                            msg: (resolved && resolved.msg) || 'Extraction target not found'
                        });
                    }
                    const singleRes = readValueFromEl(resolved.el);
                    if (!singleRes.ok) {
                        return JSON.stringify({ ok: false, code: singleRes.code, msg: singleRes.msg });
                    }
                    return JSON.stringify({
                        ok: true,
                        code: 'NONE',
                        value: singleRes.val,
                        items: [singleRes.val],
                        count: 1,
                        msg: 'Extracted "' + singleRes.val.slice(0, 40) + '"'
                    });
                } catch (e) {
                    return JSON.stringify({ ok: false, code: 'JS_EXCEPTION', msg: (e && e.message) ? e.message : String(e) });
                }
            })();
        """.trimIndent()
    }
}
