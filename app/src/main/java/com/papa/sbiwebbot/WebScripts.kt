//app/src/main/java/com/papa/sbiwebbot/WebScripts.kt
//ver 1.02-05
package com.papa.sbiwebbot

object WebScripts {

    /**
     * clickable elements -> JSON array
     * [{tag,text,href,xpath}]
     */
    fun inspectClickableScript(): String {
        // NOTE: returned value must be JSON.stringify(...) for evaluateJavascript.
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
              function isClickable(el){
                if(!el || el.nodeType!==1) return false;
                var tag=el.tagName.toLowerCase();
                if(tag==='a' || tag==='button') return true;
                var role=(el.getAttribute('role')||'').toLowerCase();
                if(role==='button' || role==='link') return true;
                var onclick=el.getAttribute('onclick');
                if(onclick && onclick.length>0) return true;
                return false;
              }
              var all=document.querySelectorAll('a,button,[role=button],[role=link],[onclick]');
              var out=[];
              for(var i=0;i<all.length;i++){
                var el=all[i];
                if(!isClickable(el)) continue;
                var t=norm(el.innerText||el.textContent||'');
                var href='';
                try { href=el.href||el.getAttribute('href')||''; } catch(e){ href=''; }
                var xp=xpathOf(el);
                if(!xp) continue;
                out.push({tag: el.tagName.toLowerCase(), text: t, href: href, xpath: xp});
              }
              return out;
            })();
        """.trimIndent()
    }

    fun clickByXpathScript(xpath: String): String {
        val safe = xpath.replace("\\", "\\\\").replace("'", "\\'")
        return """
            (function(){
              try{
                var xp = '$safe';
                var r = document.evaluate(xp, document, null, XPathResult.FIRST_ORDERED_NODE_TYPE, null);
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
     * Install click hook to capture USER manual taps (including radio/label).
     * It calls AndroidBridge.onUserClick(JSON-string).
     */
    fun installClickHookScript(): String {
        return """
            (function(){
              try{
                if(window.__sbiwebbot_clickhook_installed){ return 'already'; }
                window.__sbiwebbot_clickhook_installed = true;

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

                  // radio/checkbox/input
                  var tag = t.tagName.toLowerCase();
                  if(tag==='input'){
                    return t;
                  }
                  // label -> associated input if exists
                  if(tag==='label'){
                    var htmlFor = t.getAttribute('for');
                    if(htmlFor){
                      var inp = document.getElementById(htmlFor);
                      if(inp) return inp;
                    }
                    return t;
                  }
                  // anchor/button/role=button
                  var a = t.closest('a,button,[role=button],[role=link],[onclick],label,input');
                  return a || t;
                }

                function buildObj(el){
                  var tag = (el.tagName||'').toLowerCase();
                  var text = norm(el.innerText || el.textContent || el.value || '');
                  var href = '';
                  try { href = el.href || el.getAttribute('href') || ''; } catch(e){ href=''; }
                  var type = '';
                  try { type = el.type || ''; } catch(e){ type=''; }
                  return {
                    tag: tag,
                    type: type,
                    text: text,
                    href: href,
                    xpath: xpathOf(el)
                  };
                }

                document.addEventListener('click', function(ev){
                  try{
                    var el = pickTarget(ev.target);
                    if(!el) return;
                    var obj = buildObj(el);
                    if(!obj.xpath) return;

                    if(window.AndroidBridge && window.AndroidBridge.onUserClick){
                      window.AndroidBridge.onUserClick(JSON.stringify(obj));
                    }
                  }catch(e){}
                }, true);

                return 'installed';
              }catch(e){
                return 'error';
              }
            })();
        """.trimIndent()
    }

// ===== Legacy scripts (v1.01-xx) kept for compile compatibility =====
    // v1.02 explore-mode does not rely on these, but Web.kt still references them.
    fun popupAutoCloseScript(): String = "(function(){return 'noop';})();"

    fun deviceAuthPopupProceedScript(): String = "(function(){return 'noop';})();"

    fun authFromBodyScript(): String = "(function(){return 'noop';})();"

    fun autoLoginScript(user: String, pass: String): String = "(function(){return 'noop';})();"

    fun clickByTextScript(label: String, needle: String): String = "(function(){return 'noop';})();"

    fun submitDeviceAuthScript(code: String): String = "(function(){return 'noop';})();"

    fun rankingScript(): String = "(function(){return 'noop';})();"

    fun inspectScript(): String = inspectClickableScript()

}
