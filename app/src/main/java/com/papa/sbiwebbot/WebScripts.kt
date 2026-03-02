//app/src/main/java/com/papa/sbiwebbot/WebScripts.kt
//ver 1.02-32
package com.papa.sbiwebbot

object WebScripts {

    /**
     * clickable elements -> JSON array string
     * [{tag,text,href,xpath,frameCss}]
     */
    fun inspectClickableScript(): String {
        // NOTE: return JSON.stringify(...) for evaluateJavascript.
        return """
            (function(){
              function norm(s){
                try { return (s||'').replace(/\s+/g,' ').trim(); } catch(e){ return ''; }
              }
              function xpathOf(el){
                if(!el || el.nodeType!==1) return '';
                if(el.id){
                  return "//*[@id='"+el.id+"']";
                }
                var parts=[];
                while(el && el.nodeType===1 && el.tagName.toLowerCase()!=='html'){
                  var tag=el.tagName.toLowerCase();
                  var sib=el;
                  var idx=1;
                  while((sib=sib.previousElementSibling)!=null){
                    if(sib.tagName.toLowerCase()===tag) idx++;
                  }
                  parts.unshift(tag+'['+idx+']');
                  el=el.parentElement;
                }
                return '//' + parts.join('/');
              }
              function textOf(el){
                var t='';
                try { t = norm(el.innerText || el.textContent || el.value || ''); } catch(e){ t=''; }
                if(!t){
                  try { t = norm(el.getAttribute('aria-label') || ''); } catch(e){ }
                }
                if(!t){
                  try { t = norm(el.getAttribute('title') || ''); } catch(e){ }
                }
                return t;
              }
              function hrefOf(el){
                var href='';
                try { href = el.href || el.getAttribute('href') || ''; } catch(e){ href=''; }
                return href;
              }
              function roleOf(el){
                try { return (el.getAttribute('role')||'').toLowerCase(); } catch(e){ return ''; }
              }
              function isClickable(el){
                if(!el || el.nodeType!==1) return false;
                var tag=el.tagName.toLowerCase();
                if(tag==='a' || tag==='button') return true;
                if(tag==='label') return true;
                if(tag==='select' || tag==='option') return true;
                if(tag==='input'){
                  var tp=(el.getAttribute('type')||'').toLowerCase();
                  if(tp==='radio' || tp==='checkbox' || tp==='button' || tp==='submit' || tp==='image') return true;
                }
                var role=roleOf(el);
                if(role==='button' || role==='link' || role==='tab') return true;
                var onclick='';
                try { onclick = el.getAttribute('onclick')||''; } catch(e){ onclick=''; }
                if(onclick && onclick.length>0) return true;
                // tabindex with key handler
                try {
                  var tb = el.getAttribute('tabindex');
                  if(tb!=null && tb!=='' && (role==='button' || role==='link')) return true;
                } catch(e){}
                return false;
              }

              function collect(doc, frameCss, out){
                var sel = 'a,button,input,label,select,option,[role=button],[role=link],[role=tab],[onclick]';
                var all=[];
                try { all = doc.querySelectorAll(sel); } catch(e){ all=[]; }
                for(var i=0;i<all.length;i++){
                  var el=all[i];
                  if(!isClickable(el)) continue;
                  var xp = xpathOf(el);
                  if(!xp) continue;
                  var t = textOf(el);
                  var href = hrefOf(el);
                  if(!t && !href) continue;
                  if(out.length>=1500) break;
                  out.push({tag: (el.tagName||'').toLowerCase(), text: t, href: href, xpath: xp, frameCss: frameCss});
                }
              }

              var out=[];
              collect(document, '', out);

              // iframe scan (same-origin only)
              var ifs=[];
              try { ifs = document.querySelectorAll('iframe'); } catch(e){ ifs=[]; }
              for(var i=0;i<ifs.length;i++){
                var f = ifs[i];
                var css = 'iframe:nth-of-type('+(i+1)+')';
                try{
                  if(f && f.contentDocument){
                    collect(f.contentDocument, css, out);
                  }
                }catch(e){}
              }

              return JSON.stringify(out);
            })();
        """.trimIndent()
    }

    fun clickByXpathScript(xpath: String, frameCss: String?): String {
        val safeXpath = xpath.replace("\\", "\\\\").replace("'", "\\'")
        val safeFrame = (frameCss ?: "").replace("\\", "\\\\").replace("'", "\\'")
        return """
            (function(){
              try{
                var xp = '$safeXpath';
                var frameCss = '$safeFrame';
                var doc = document;
                if(frameCss && frameCss.length>0){
                  var ifr = document.querySelector(frameCss);
                  if(ifr && ifr.contentDocument){
                    doc = ifr.contentDocument;
                  }else{
                    return 'false';
                  }
                }
                var r = doc.evaluate(xp, doc, null, XPathResult.FIRST_ORDERED_NODE_TYPE, null);
                var el = r.singleNodeValue;
                if(!el){ return 'false'; }
                el.click();
                return 'true';
              }catch(e){
                return 'false';
              }
            })();
        """.trimIndent()
    }

    /**
     * Install click hook to capture USER manual taps.
     * It calls AndroidBridge.onUserClick(JSON-string).
     * Also tries to install into same-origin iframes.
     */
    fun installClickHookScript(): String {
        return """
            (function(){
              try{
                function norm(s){
                  try { return (s||'').replace(/\s+/g,' ').trim(); } catch(e){ return ''; }
                }
                function xpathOf(el){
                  if(!el || el.nodeType!==1) return '';
                  if(el.id){ return "//*[@id='"+el.id+"']"; }
                  var parts=[];
                  while(el && el.nodeType===1 && el.tagName.toLowerCase()!=='html'){
                    var tag=el.tagName.toLowerCase();
                    var sib=el;
                    var idx=1;
                    while((sib=sib.previousElementSibling)!=null){
                      if(sib.tagName.toLowerCase()===tag) idx++;
                    }
                    parts.unshift(tag+'['+idx+']');
                    el=el.parentElement;
                  }
                  return '//' + parts.join('/');
                }
                function pickTarget(t){
                  if(!t || t.nodeType!==1) return null;
                  var tag = t.tagName.toLowerCase();
                  if(tag==='input' || tag==='select' || tag==='option') return t;
                  if(tag==='label'){
                    var htmlFor = t.getAttribute('for');
                    if(htmlFor){
                      var inp = document.getElementById(htmlFor);
                      if(inp) return inp;
                    }
                    return t;
                  }
                  var a = t.closest('a,button,[role=button],[role=link],[role=tab],[onclick],label,input,select,option');
                  return a || t;
                }
                function buildObj(el, frameCss){
                  var tag = (el.tagName||'').toLowerCase();
                  var text = norm(el.innerText || el.textContent || el.value || '');
                  if(!text){
                    try { text = norm(el.getAttribute('aria-label')||''); } catch(e){}
                  }
                  var href = '';
                  try { href = el.href || el.getAttribute('href') || ''; } catch(e){ href=''; }
                  var type = '';
                  try { type = el.type || ''; } catch(e){ type=''; }
                  var name = '';
                  try { name = el.name || el.getAttribute('name') || ''; } catch(e){ name=''; }
                  var idv = '';
                  try { idv = el.id || ''; } catch(e){ idv=''; }
                  var role = '';
                  try { role = (el.getAttribute('role')||''); } catch(e){ role=''; }
                  var aria = '';
                  try { aria = (el.getAttribute('aria-label')||''); } catch(e){ aria=''; }
                  return {
                    tag: tag,
                    type: type,
                    name: name,
                    id: idv,
                    role: role,
                    aria: aria,
                    text: text,
                    href: href,
                    xpath: xpathOf(el),
                    frameCss: frameCss || ''
                  };
                }
                function installOn(doc, frameCss){
                  try{
                    if(doc.__sbiwebbot_clickhook_installed){ return; }
                    doc.__sbiwebbot_clickhook_installed = true;

                    doc.addEventListener('click', function(ev){
                      try{
                        var el = pickTarget(ev.target);
                        if(!el) return;
                        var obj = buildObj(el, frameCss);
                        if(!obj.xpath) return;
                        if(window.AndroidBridge && window.AndroidBridge.onUserClick){
                          window.AndroidBridge.onUserClick(JSON.stringify(obj));
                        }
                      }catch(e){}
                    }, true);

                    doc.addEventListener('change', function(ev){
                      try{
                        var el = pickTarget(ev.target);
                        if(!el) return;
                        var obj = buildObj(el, frameCss);
                        if(!obj.xpath) return;
                        obj.event = 'change';
                        try{
                          if((el.tagName||'').toLowerCase()==='select'){
                            var opt = el.options && el.selectedIndex>=0 ? el.options[el.selectedIndex] : null;
                            if(opt){
                              obj.selectedText = norm(opt.textContent||opt.innerText||'');
                              obj.selectedValue = (opt.value||'');
                            }
                          }
                        }catch(e){}
                        if(window.AndroidBridge && window.AndroidBridge.onUserClick){
                          window.AndroidBridge.onUserClick(JSON.stringify(obj));
                        }
                      }catch(e){}
                    }, true);
                  }catch(e){}
                }

                installOn(document, '');

                var ifs=[];
                try { ifs = document.querySelectorAll('iframe'); } catch(e){ ifs=[]; }
                for(var i=0;i<ifs.length;i++){
                  var f = ifs[i];
                  var css = 'iframe:nth-of-type('+(i+1)+')';
                  try{
                    if(f && f.contentDocument){
                      installOn(f.contentDocument, css);
                    }
                  }catch(e){}
                }
                return 'installed';
              }catch(e){
                return 'error';
              }
            })();
        """.trimIndent()
    }

    // ===== Legacy scripts kept for compile compatibility =====
    fun popupAutoCloseScript(): String = "(function(){return 'noop';})();"
    fun deviceAuthPopupProceedScript(): String = "(function(){return 'noop';})();"
    fun authFromBodyScript(): String = "(function(){return 'noop';})();"
    fun autoLoginScript(user: String, pass: String): String = "(function(){return 'noop';})();"
    fun clickByTextScript(label: String, needle: String): String = "(function(){return 'noop';})();"
    fun submitDeviceAuthScript(code: String): String = "(function(){return 'noop';})();"
    fun rankingScript(): String = "(function(){return 'noop';})();"
    fun inspectScript(): String = inspectClickableScript()

    /**
     * Extract SBI stock detail URLs from ranking page.
     * returns JSON.stringify([{url, code}])
     */
    fun extractSbiStockLinksScript(limit: Int): String {
        val lim = if (limit <= 0) 10 else limit
        return """
            (function(){
              try{
                var a = document.querySelectorAll('a[href]');
                var out=[];
                function pickCode(h){
                  try{
                    var m = /stock_sec_code=([0-9]{4})/.exec(h);
                    if(m && m[1]) return m[1];
                    m = /stock_sec_code_mul=([0-9]{4})/.exec(h);
                    if(m && m[1]) return m[1];
                    m = /i_stock_sec=([0-9]{4})/.exec(h);
                    if(m && m[1]) return m[1];
                  }catch(e){}
                  return '';
                }
                function absUrl(h){
                  try{
                    if(!h) return '';
                    if(h.indexOf('http://')===0 || h.indexOf('https://')===0) return h;
                    return new URL(h, location.href).toString();
                  }catch(e){ return ''; }
                }
                function isAllowed(u){
                  try{
                    if(!u) return false;
                    if(u.indexOf('login.sbisec.co.jp')>=0) return false;
                    if(u.indexOf('www.sbisec.co.jp/ETGate/')<0) return false;
                    if(u.indexOf('OutSide=on')<0) return false;
                    // stock detail markers
                    if(u.indexOf('_ActionID=stockDetail')<0 &&
                       u.indexOf('WPLETsiR001Idtl10')<0 &&
                       u.indexOf('stock_sec_code_mul=')<0 &&
                       u.indexOf('i_stock_sec=')<0 &&
                       u.indexOf('stock_sec_code=')<0) return false;
                    return true;
                  }catch(e){ return false; }
                }
                for(var i=0;i<a.length;i++){
                  var raw='';
                  try{ raw = a[i].getAttribute('href') || ''; }catch(e){ raw=''; }
                  var u = absUrl(raw);
                  if(!isAllowed(u)) continue;
                  var code = pickCode(u);
                  if(!code) continue;
                  // de-dup by code
                  var dup=false;
                  for(var j=0;j<out.length;j++){ if(out[j].code===code){ dup=true; break; } }
                  if(dup) continue;

                  out.push({url:u, code:code});
                  if(out.length>=lim) break;
                }
                return JSON.stringify(out);
              }catch(e){
                return JSON.stringify([]);
              }
            })();
        """.trimIndent()
    }

    /** Find PTS link in current page (best-effort). returns string URL or empty */
    fun findPtsLinkScript(): String {
        return """
            (function(){
              try{
                var a = document.querySelectorAll('a[href]');
                for(var i=0;i<a.length;i++){
                  var h='';
                  try{ h = a[i].href || a[i].getAttribute('href') || ''; }catch(e){ h=''; }
                  if(!h) continue;
                  if(h.indexOf('exchange_code=PTS')>=0) return h;
                  if(h.indexOf('getInfoOfCurrentMarket')>=0 && h.indexOf('PTS')>=0) return h;
                }
                return '';
              }catch(e){
                return '';
              }
            })();
        """.trimIndent()
    }

    /** Capture outerHTML. returns string */
    fun outerHtmlScript(): String = "(function(){try{return document.documentElement.outerHTML||'';}catch(e){return '';} })();"

    /** Capture body innerText (for snippet). returns string */
    fun bodyTextScript(): String = "(function(){try{return document.body? (document.body.innerText||'') : '';}catch(e){return '';} })();"


    fun fillLoginAndSubmitScript(user: String, pass: String): String {
        // Best-effort: find username/password inputs and click a login button.
        // SBI pages vary (and may A/B test), so keep heuristics broad.
        val u = user.replace("\\", "\\\\").replace("'", "\\'")
        val p = pass.replace("\\", "\\\\").replace("'", "\\'")
        return """(function(){
            try{
              var USER = '$u';
              var PASS = '$p';
              function norm(s){ return (s||'').replace(/\s+/g,' ').trim().toLowerCase(); }
              function setVal(el, v){
                try{
                  el.focus();
                  el.value = v;
                  el.dispatchEvent(new Event('input', {bubbles:true}));
                  el.dispatchEvent(new Event('change', {bubbles:true}));
                  el.blur();
                }catch(e){}
              }
              function scoreUser(el){
                var a = norm(el.getAttribute('id')) + ' ' + norm(el.getAttribute('name')) + ' ' + norm(el.getAttribute('placeholder')) + ' ' + norm(el.getAttribute('aria-label'));
                var t = norm(el.getAttribute('type'));
                var s = 0;
                if (t==='text' || t==='email' || t==='tel' || t==='' ) s += 1;
                if (a.indexOf('user')>=0 || a.indexOf('login')>=0 || a.indexOf('id')>=0 || a.indexOf('account')>=0) s += 3;
                if (a.indexOf('ユーザ')>=0 || a.indexOf('ﾕｰｻﾞ')>=0 || a.indexOf('ユーザー')>=0) s += 4;
                if (a.indexOf('pass')>=0 || t==='password') s -= 5;
                return s;
              }
              function scorePass(el){
                var a = norm(el.getAttribute('id')) + ' ' + norm(el.getAttribute('name')) + ' ' + norm(el.getAttribute('placeholder')) + ' ' + norm(el.getAttribute('aria-label'));
                var t = norm(el.getAttribute('type'));
                var s = 0;
                if (t==='password') s += 6;
                if (a.indexOf('pass')>=0) s += 5;
                if (a.indexOf('password')>=0) s += 5;
                if (a.indexOf('ログイン')>=0) s += 1;
                return s;
              }
              var inputs = Array.prototype.slice.call(document.querySelectorAll('input'));
              var userEl = null, passEl = null, bestU=-999, bestP=-999;
              for (var i=0;i<inputs.length;i++){
                var el = inputs[i];
                var su = scoreUser(el);
                if (su>bestU){ bestU=su; userEl=el; }
                var sp = scorePass(el);
                if (sp>bestP){ bestP=sp; passEl=el; }
              }
              if (userEl) setVal(userEl, USER);
              if (passEl) setVal(passEl, PASS);

              // Click a login button
              function textOf(el){
                return norm(el.innerText||el.textContent||el.value||el.getAttribute('aria-label')||'');
              }
              var btns = Array.prototype.slice.call(document.querySelectorAll('button,input[type=submit],input[type=button],a[role=button]'));
              var clicked = false;
              for (var j=0;j<btns.length;j++){
                var b = btns[j];
                var t = textOf(b);
                if (t.indexOf('ログイン')>=0){
                  try{ b.click(); clicked=true; break; }catch(e){}
                }
              }
              return JSON.stringify({ok:true, userFound:!!userEl, passFound:!!passEl, clicked:clicked, bestU:bestU, bestP:bestP});
            }catch(e){
              return JSON.stringify({ok:false, err:String(e)});
            }
          })();"""
    }

}
