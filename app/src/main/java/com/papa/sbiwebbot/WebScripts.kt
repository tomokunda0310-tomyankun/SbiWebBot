//app/src/main/java/com/papa/sbiwebbot/WebScripts.kt
//ver 1.00-46
package com.papa.sbiwebbot

import org.json.JSONObject

object WebScripts {

    fun autoLoginScript(username: String, password: String): String {
        val qU = JSONObject.quote(username)
        val qP = JSONObject.quote(password)
        return """(function(){
            try {
                var u = ${qU};
                var p = ${qP};

                function setVal(el, v){
                    try{ el.focus(); }catch(e){}
                    try{ el.value = v; }catch(e){}
                    try{ el.dispatchEvent(new Event('input', {bubbles:true})); }catch(e){}
                    try{ el.dispatchEvent(new Event('change', {bubbles:true})); }catch(e){}
                }
                function clickEl(el){
                    try{ el.scrollIntoView({block:'center'}); }catch(e){}
                    try{ el.click(); }catch(e){}
                    try{ el.dispatchEvent(new MouseEvent('click',{view:window,bubbles:true,cancelable:true})); }catch(e){}
                }

                function findUser(){
                    var userEl = document.querySelector('input#userId, input[name="userId"], input[name="username"], input[id*="user" i], input[name*="user" i], input[id*="login" i], input[name*="login" i]');
                    if(!userEl){
                        var candU = Array.from(document.querySelectorAll('input[type="text"], input[type="email"], input:not([type])'));
                        userEl = candU.find(function(x){return x && x.offsetParent !== null;}) || null;
                    }
                    return userEl;
                }

                function findPass(){
                    var passEl = document.querySelector('input[type="password"], input#password, input[name="password"], input[id*="pass" i], input[name*="pass" i]');
                    if(!passEl){
                        var candP = Array.from(document.querySelectorAll('input'));
                        passEl = candP.find(function(x){return (x.type||'').toLowerCase()==='password' && x.offsetParent!==null;}) || null;
                    }
                    return passEl;
                }

                function findBtn(){
                    var btn = document.querySelector('#pw-btn, button#pw-btn, input#pw-btn, button[type="submit"], input[type="submit"], button[name="login"], button[id*="login" i], input[id*="login" i]');
                    if(!btn){
                        var all = Array.from(document.querySelectorAll('button,input[type="button"],input[type="submit"],a,[role="button"]'));
                        btn = all.find(function(e){
                            var t = (e.innerText || e.value || '').trim();
                            return t.indexOf('ログイン')>=0;
                        }) || null;
                    }
                    return btn;
                }

                var tried = 0;
                var maxTry = 25; // ~5s
                var done = false;
                function tick(){
                    if(done) return;
                    tried++;
                    var userEl = findUser();
                    var passEl = findPass();
                    if(userEl) setVal(userEl, u);
                    if(passEl) setVal(passEl, p);
                    var btn = findBtn();
                    if(userEl && passEl && btn){
                        clickEl(btn);
                        AndroidApp.log('autoLoginScript: clicked');
                        done = true;
                        return;
                    }
                    if(tried >= maxTry){
                        AndroidApp.log('autoLoginScript: element not ready (timeout)');
                        done = true;
                        return;
                    }
                    setTimeout(tick, 200);
                }
                tick();
            } catch(e){
                AndroidApp.log('autoLoginScript exception: ' + e);
            }
        })();""".trimIndent()
    }

    fun inspectScript(): String {
        return """(function() {
            function getXP(el) {
                if (el.id) return 'id("' + el.id + '")';
                var path = '';
                while (el && el.nodeType === 1) {
                    var i = 1, s = el.previousSibling;
                    while (s) { if (s.nodeType === 1 && s.tagName === el.tagName) i++; s = s.previousSibling; }
                    path = '/' + el.tagName.toLowerCase() + '[' + i + ']' + path;
                    el = el.parentNode;
                }
                return path;
            }

            function txtOf(e) {
                try {
                    return ((e.innerText || e.value || (e.getAttribute && (e.getAttribute('aria-label') || e.getAttribute('alt'))) || e.title || '') + '').trim();
                } catch(ex) {
                    return '';
                }
            }

            function findSixDigitsSafe(body) {
                // avoid tokens like LAR_000003 or ABC123456 (letters/underscore around digits)
                var re = /(^|[^0-9A-Za-z_])(\d{6})(?![0-9A-Za-z_])/g;
                var list = [];
                var m;
                while ((m = re.exec(body)) !== null) {
                    list.push(m[2]);
                }
                return list;
            }

            function findNearKeyword(body, kw, range) {
                var idx = body.indexOf(kw);
                if (idx < 0) return null;
                var sub = body.substring(idx, Math.min(body.length, idx + range));
                var list = findSixDigitsSafe(sub);
                if (list && list.length > 0) return list[0];
                return null;
            }

            try {
                function isVisible(e){
                    try {
                        if(!e) return false;
                        if(e.offsetParent !== null) return true;
                        var r = e.getClientRects();
                        return r && r.length > 0;
                    } catch(ex){
                        return false;
                    }
                }

                function labelOf(e){
                    var t = '';
                    try {
                        t = (e.innerText || e.value || (e.getAttribute && (e.getAttribute('aria-label') || e.getAttribute('alt'))) || e.title || '').trim();
                    } catch(ex) {}
                    if(!t){
                        try { t = (e.textContent || '').trim(); } catch(ex) {}
                    }
                    // ボタン系は子要素に文字が入ることが多い
                    if(!t){
                        try {
                            var s = e.querySelector && e.querySelector('span,div,label');
                            if(s) t = (s.innerText || s.textContent || '').trim();
                        } catch(ex) {}
                    }
                    return (t || '').replace(/\s+/g,' ').trim();
                }

                var elements = [];
                // clickable を優先して収集
                var clickables = Array.from(document.querySelectorAll('button,a,input,[role="button"],[onclick]'));
                for (var i=0;i<clickables.length;i++){
                    var e = clickables[i];
                    if(!isVisible(e)) continue;
                    var txt = labelOf(e);
                    if(!txt) continue;
                    elements.push({tag:(e.tagName||'').toLowerCase(), xpath:getXP(e), text:txt.substring(0, 80)});
                }

                // 重要: 「認証コード入力画面に進む」がポップアップ内で取れない場合があるので追加探索
                var key = '認証コード入力画面に進む';
                if (document.body && (document.body.innerText||'').indexOf(key) >= 0) {
                    var all = Array.from(document.querySelectorAll('*'));
                    for (var j=0;j<all.length && j<6000;j++){
                        var n = all[j];
                        var t2 = labelOf(n);
                        if(!t2) continue;
                        if(t2.indexOf(key) >= 0){
                            elements.push({tag:(n.tagName||'').toLowerCase(), xpath:getXP(n), text:t2.substring(0, 80)});
                        }
                    }
                }

                // Auth code detection:
                // - first: try near "認証コード" / "認証番号"
                // - fallback: OTP pages -> pick last safe 6-digit
                var body = (document.body && document.body.innerText) ? document.body.innerText : '';
                var url = location.href || '';

                var isOtp = (url.indexOf('/otp/confirm') >= 0 || url.indexOf('/otp/entry') >= 0);

                if (!window.__authSent) {
                    var code = null;
                    code = code || findNearKeyword(body, '認証コード', 200);
                    code = code || findNearKeyword(body, '認証番号', 200);

                    if (!code && isOtp) {
                        var list = findSixDigitsSafe(body);
                        if (list && list.length > 0) code = list[list.length - 1];
                    }

                    if (code) {
                        window.__authSent = true;
                        AndroidApp.onAuth(code);
                    }
                }

                AndroidApp.sendRecSnapshot(location.href, document.title, JSON.stringify(elements));
            } catch(e) {
                AndroidApp.log('inspectScript exception: ' + e);
                AndroidApp.sendElements(JSON.stringify([]));
            }
        })();""".trimIndent()
    }

    fun rankingScript(): String {
        return """(function() {
            function pickTable() {
                var tables = Array.from(document.getElementsByTagName('table'));
                for (var i=0; i<tables.length; i++) {
                    var t = tables[i];
                    var txt = (t.innerText || '').replace(/\s+/g,' ').trim();
                    if (txt.indexOf('順位') >= 0 && (txt.indexOf('銘柄') >= 0 || txt.indexOf('コード') >= 0)) return t;
                }
                return null;
            }

            function tableToJson(table) {
                var rows = Array.from(table.getElementsByTagName('tr'));
                var items = [];
                for (var r=0; r<rows.length; r++) {
                    var cells = Array.from(rows[r].children).map(function(c){ return (c.innerText||'').replace(/\s+/g,' ').trim(); });
                    if (cells.length === 0) continue;

                    // header skip
                    if (r === 0 && (cells.join(' ').indexOf('順位') >= 0 || cells.join(' ').indexOf('銘柄') >= 0)) continue;

                    var joined = cells.join(' ');
                    var m = joined.match(/^(\d+)\s+(\d{4})\s+(.+?)\s/);
                    var rank = null, code = null, name = null;

                    if (m) {
                        rank = m[1]; code = m[2]; name = m[3];
                    } else {
                        var mr = (cells[0]||'').match(/^(\d+)$/);
                        if (mr) rank = mr[1];

                        for (var k=0; k<cells.length; k++) {
                            var mc = (cells[k]||'').match(/(\d{4})/);
                            if (mc) { code = mc[1]; break; }
                        }
                        if (cells.length >= 3 && !name) name = cells[2];
                    }

                    items.push({ rank: rank, code: code, name: name, rawCells: cells });
                }
                return {
                    ok: true,
                    source: 'dom_table',
                    url: location.href,
                    title: document.title,
                    items: items
                };
            }

            try {
                var table = pickTable();
                if (!table) {
                    AndroidApp.onRanking(JSON.stringify({ ok:false, reason:'table_not_found', url:location.href, title:document.title }));
                    return;
                }
                AndroidApp.onRanking(JSON.stringify(tableToJson(table)));
            } catch(e) {
                AndroidApp.onRanking(JSON.stringify({ ok:false, reason:'exception', message:String(e), url:location.href, title:document.title }));
            }
        })();""".trimIndent()
    }

    fun clickByTextScript(label: String, needle: String): String {
        val qLabel = JSONObject.quote(label)
        val qNeedle = JSONObject.quote(needle)
        return """(function() {
            try {
                function txtOf(el){
                    var t = '';
                    try { t = (el.innerText || el.value || (el.getAttribute && (el.getAttribute('aria-label') || el.getAttribute('alt'))) || el.title || '').trim(); } catch(e){}
                    return t;
                }

                function tryClick(el){
                    try { el.scrollIntoView({block:'center'}); } catch(e){}
                    try { el.click(); return true; } catch(e){}
                    try {
                        var ev = new MouseEvent('click', {view: window, bubbles: true, cancelable: true});
                        el.dispatchEvent(ev);
                        return true;
                    } catch(e){}
                    return false;
                }

                var needle = ${qNeedle};
                var all = Array.from(document.querySelectorAll('*'));
                var cand = [];
                for (var i=0;i<all.length;i++){
                    var e = all[i];
                    var t = txtOf(e);
                    if (!t) continue;
                    if (t.indexOf(needle) >= 0) cand.push(e);
                }

                function score(e){
                    var tag = (e.tagName||'').toLowerCase();
                    var s = 0;
                    if (tag === 'button') s += 50;
                    if (tag === 'input') s += 45;
                    if (tag === 'a') s += 40;
                    if (e.getAttribute && e.getAttribute('role') === 'button') s += 35;
                    if (e.onclick) s += 30;
                    var t = txtOf(e);
                    if (t === needle) s += 25;
                    return s;
                }
                cand.sort(function(a,b){return score(b)-score(a);});

                var ok = false;
                for (var j=0;j<cand.length && j<20;j++){
                    if (tryClick(cand[j])) { ok = true; break; }
                }

                if(!ok && cand.length>0){
                    var p = cand[0];
                    for (var k=0;k<6 && p; k++){
                        if (tryClick(p)) { ok = true; break; }
                        p = p.parentElement;
                    }
                }

                AndroidApp.onClickResult(${qLabel}, ok);
            } catch(e) {
                AndroidApp.log('clickByTextScript exception: ' + e);
                AndroidApp.onClickResult(${qLabel}, false);
            }
        })();""".trimIndent()
    }


    fun popupAutoCloseScript(): String {
        // Close common modal/dialog overlays by text and role/class heuristics.
        return """(function() {
            try {
                function txtOf(el){
                    var t = '';
                    try { t = (el.innerText || el.value || (el.getAttribute && (el.getAttribute('aria-label') || el.getAttribute('alt'))) || el.title || '').trim(); } catch(e){}
                    return t;
                }
                function tryClick(el){
                    if(!el) return false;
                    try { el.scrollIntoView({block:'center'}); } catch(e){}
                    try { el.click(); return true; } catch(e){}
                    try {
                        var ev = new MouseEvent('click', {view: window, bubbles: true, cancelable: true});
                        el.dispatchEvent(ev);
                        return true;
                    } catch(e){}
                    return false;
                }

                // 1) role=dialog / aria-modal / obvious modal containers
                var dialogs = Array.from(document.querySelectorAll('[role="dialog"],[aria-modal="true"],.modal,.Modal,.dialog,.Dialog,#modal,#dialog'));
                // 2) candidates for close/ok buttons inside dialogs
                // NOTE: 「次へ/続行」等を全体DOMで押すと、広告リンク等を誤タップするので禁止。
                var words = ['閉じる','×','OK','ＯＫ','確認','同意','キャンセル'];
                var clicked = false;

                function scan(root){
                    var nodes = Array.from((root||document).querySelectorAll('button,a,input,[role="button"],[onclick]'));
                    for (var i=0;i<nodes.length;i++){
                        var e = nodes[i];
                        var t = txtOf(e);
                        if(!t) continue;
                        for (var j=0;j<words.length;j++){
                            if (t.indexOf(words[j]) >= 0) {
                                if (tryClick(e)) return true;
                            }
                        }
                    }
                    return false;
                }

                // try within dialogs first
                for (var d=0; d<dialogs.length && !clicked; d++){
                    clicked = scan(dialogs[d]);
                }
                // fallback全体走査は危険（広告リンク等を誤クリックする）のでやらない

                AndroidApp.log('popupAutoCloseScript clicked=' + clicked);
            } catch(e) {
                AndroidApp.log('popupAutoCloseScript exception: ' + e);
            }
        })();""".trimIndent()
    }

    fun submitDeviceAuthScript(code: String): String {
        val qCode = JSONObject.quote(code)
        return """(function() {
            try {
                function isVisible(el){
                    try {
                        var r = el.getBoundingClientRect();
                        return r.width > 0 && r.height > 0;
                    } catch(e){ return true; }
                }

                function setValue(el, v){
                    try { el.focus(); } catch(e){}
                    try { el.value = v; } catch(e){}
                    try {
                        el.dispatchEvent(new Event('input', {bubbles:true}));
                        el.dispatchEvent(new Event('change', {bubbles:true}));
                    } catch(e){}
                }

                function txtOf(el){
                    var t = '';
                    try { t = (el.innerText || el.value || (el.getAttribute && (el.getAttribute('aria-label') || el.getAttribute('alt'))) || el.title || '').trim(); } catch(e){}
                    return t;
                }

                function tryClick(el){
                    try { el.scrollIntoView({block:'center'}); } catch(e){}
                    try { el.click(); return true; } catch(e){}
                    try {
                        var ev = new MouseEvent('click', {view: window, bubbles: true, cancelable: true});
                        el.dispatchEvent(ev);
                        return true;
                    } catch(e){}
                    return false;
                }

                // 1) input探す
                var inputs = Array.from(document.querySelectorAll('input'))
                    .filter(function(i){
                        var t = (i.type || '').toLowerCase();
                        return (t === '' || t === 'text' || t === 'tel' || t === 'number' || t === 'password') && isVisible(i);
                    });

                if (inputs.length === 0) {
                    AndroidApp.onClickResult('device_auth_input', false);
                    return;
                }

                setValue(inputs[0], """ + qCode + """);
                AndroidApp.onClickResult('device_auth_input', true);

                // 2) 認証ボタン探す
                var needles = ['認証', '送信', '確認', 'OK', '次へ'];
                var all = Array.from(document.querySelectorAll('button,input,a,[role="button"],[onclick]'));
                var cand = [];
                for (var i=0;i<all.length;i++){
                    var e = all[i];
                    if (!isVisible(e)) continue;
                    var t = txtOf(e);
                    if (!t) continue;
                    for (var k=0;k<needles.length;k++){
                        if (t.indexOf(needles[k]) >= 0) { cand.push(e); break; }
                    }
                }

                var ok = false;
                for (var j=0;j<cand.length && j<15;j++){
                    if (tryClick(cand[j])) { ok = true; break; }
                }

                if (!ok) {
                    try {
                        var f = inputs[0].form;
                        if (f) { f.submit(); ok = true; }
                    } catch(e){}
                }

                AndroidApp.onClickResult('device_auth_submit', ok);
            } catch(e) {
                AndroidApp.log('submitDeviceAuthScript exception: ' + e);
                AndroidApp.onClickResult('device_auth_submit', false);
            }
        })();""".trimIndent()
    }



        fun deviceAuthPopupProceedScript(): String {
        val qLabel = JSONObject.quote("device_auth_popup_proceed")
        val key1 = JSONObject.quote("認証コード入力画面に進む")
        val key2 = JSONObject.quote("入力画面に進む")
        val key3 = JSONObject.quote("認証コード入力画面")
        return """(function(){
            try {
                var label = ${qLabel};
                var keys = [${key1}, ${key2}, ${key3}, '進む'];

                function norm(s){
                    return (s||'').toString().replace(/\u00a0/g,' ').replace(/\s+/g,' ').trim();
                }
                function labelOf(e){
                    var t = '';
                    try { t = (e.innerText || e.value || (e.getAttribute && (e.getAttribute('aria-label') || e.getAttribute('alt'))) || e.title || '').trim(); } catch(ex){}
                    if(!t){ try { t = (e.textContent || '').trim(); } catch(ex){} }
                    return norm(t);
                }
                function isVisible(e){
                    try {
                        if(!e) return false;
                        var r = e.getBoundingClientRect();
                        if(r.width < 8 || r.height < 8) return false;
                        var cs = window.getComputedStyle(e);
                        if(!cs) return false;
                        if(cs.visibility === 'hidden' || cs.display === 'none' || cs.opacity === '0') return false;
                        return true;
                    } catch(ex){ return false; }
                }
                function tryClick(el){
                    if(!el) return false;
                    try { el.scrollIntoView({block:'center'}); } catch(e){}
                    try { el.focus(); } catch(e){}
                    try { el.click(); return true; } catch(e){}
                    try {
                        var ev1 = new MouseEvent('mousedown', {view: window, bubbles: true, cancelable: true});
                        var ev2 = new MouseEvent('mouseup', {view: window, bubbles: true, cancelable: true});
                        var ev3 = new MouseEvent('click', {view: window, bubbles: true, cancelable: true});
                        el.dispatchEvent(ev1); el.dispatchEvent(ev2); el.dispatchEvent(ev3);
                        return true;
                    } catch(e){}
                    return false;
                }

                function findCandidates(){
                    var sel = 'button,input[type="button"],input[type="submit"],a,[role="button"],[onclick],label,div,span';
                    var all = Array.from(document.querySelectorAll(sel));
                    var cand = [];
                    for(var i=0;i<all.length;i++){
                        var e = all[i];
                        if(!isVisible(e)) continue;
                        var t = labelOf(e);
                        if(!t) continue;
                        for(var k=0;k<keys.length;k++){
                            if(t.indexOf(keys[k]) >= 0){
                                cand.push(e);
                                break;
                            }
                        }
                    }
                    // prefer clickable tags first
                    function score(e){
                        var tag = (e.tagName||'').toLowerCase();
                        var s = 0;
                        if(tag==='button') s += 60;
                        if(tag==='input') s += 55;
                        if(tag==='a') s += 50;
                        if(e.getAttribute && e.getAttribute('role')==='button') s += 45;
                        if(e.onclick) s += 35;
                        var t = labelOf(e);
                        if(t === keys[0]) s += 30;
                        if(t.indexOf('進む')>=0) s += 10;
                        return s;
                    }
                    cand.sort(function(a,b){ return score(b)-score(a); });
                    return cand;
                }

                function captureRec(){
                    function getXP(el) {
                        if (el.id) return 'id("' + el.id + '")';
                        var path = '';
                        while (el && el.nodeType === 1) {
                            var i = 1, s = el.previousSibling;
                            while (s) { if (s.nodeType === 1 && s.tagName === el.tagName) i++; s = s.previousSibling; }
                            path = '/' + el.tagName.toLowerCase() + '[' + i + ']' + path;
                            el = el.parentNode;
                        }
                        return path;
                    }
                    function txtOf(e){
                        return norm((e.innerText || e.value || (e.getAttribute && (e.getAttribute('aria-label') || e.getAttribute('alt'))) || e.title || e.textContent || ''));
                    }
                    var tags = ['input','button','a','span','div','img','label','li','td'];
                    var elements = [];
                    tags.forEach(function(t){
                        Array.from(document.getElementsByTagName(t)).forEach(function(e){
                            var txt = txtOf(e);
                            if(txt.length > 0){
                                elements.push({tag:t, xpath:getXP(e), text:txt.substring(0,30)});
                            }
                        });
                    });
                    Array.from(document.querySelectorAll('[role="button"],[onclick]')).forEach(function(e){
                        var txt = txtOf(e);
                        if(txt.length>0){
                            elements.push({tag:(e.tagName||'').toLowerCase(), xpath:getXP(e), text:txt.substring(0,30)});
                        }
                    });
                    AndroidApp.sendRecSnapshot(location.href, document.title, JSON.stringify(elements));
                }

                function attempt(n){
                    var cand = findCandidates();
                    AndroidApp.log('deviceAuthPopupProceed: attempt=' + n + ' cand=' + cand.length);
                    var ok = false;

                    for(var i=0;i<cand.length && i<20;i++){
                        if(tryClick(cand[i])) { ok = true; break; }
                        // parent fallback
                        var p = cand[i].parentElement;
                        for(var j=0;j<4 && p && !ok;j++){
                            if(tryClick(p)) { ok = true; break; }
                            p = p.parentElement;
                        }
                        if(ok) break;
                    }

                    if(ok){
                        AndroidApp.onClickResult(label, true);
                        // clickの結果DOMが変わるので少し待ってからREC採取
                        setTimeout(function(){ captureRec(); }, 700);
                        return;
                    }

                    if(n < 6){
                        setTimeout(function(){ attempt(n+1); }, 350);
                    } else {
                        AndroidApp.onClickResult(label, false);
                        // 失敗しても現状のRECを採取しておく
                        try { captureRec(); } catch(e){}
                    }
                }

                attempt(0);
            } catch(e) {
                AndroidApp.onClickResult(${qLabel}, false);
            }
        })();""".trimIndent()
    }

}