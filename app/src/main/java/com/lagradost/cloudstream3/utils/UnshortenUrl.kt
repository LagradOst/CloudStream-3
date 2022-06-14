package com.lagradost.cloudstream3.utils
import android.util.Base64
import com.lagradost.cloudstream3.app
import com.lagradost.nicehttp.NiceResponse
import java.net.URI
//Code heavily based on unshortenit.py form jigoop1 /kod_addon
class UnshortenUrl {

    val adfly_regex = """adf\.ly|j\.gs|q\.gs|u\.bb|ay\.gy|atominik\.com|tinyium\.com|microify\.com|threadsphere\.bid|clearload\.bid|activetect\.net|swiftviz\.net|briskgram\.net|activetect\.net|baymaleti\.net|thouth\.net|uclaut\.net|gloyah\.net|larati\.net|scuseami\.net"""
    val linkbucks_regex = """linkbucks\.com|any\.gs|cash4links\.co|cash4files\.co|dyo\.gs|filesonthe\.net|goneviral\.com|megaline\.co|miniurls\.co|qqc\.co|seriousdeals\.net|theseblogs\.com|theseforums\.com|tinylinks\.co|tubeviral\.com|ultrafiles\.net|urlbeat\.net|whackyvidz\.com|yyv\.co"""
    val adfocus_regex = """adfoc\.us"""
    val nuovo_indirizzo_regex = """mixdrop\.nuovoindirizzo\.com"""
    val nuovo_link_regex = """nuovolink\.com"""
    val lnxlu_regex = """lnx\.lu"""
    val shst_regex = """sh\.st|shorte\.st|sh\.st|clkmein\.com|viid\.me|xiw34\.com|corneey\.com|gestyy\.com|cllkme\.com|festyy\.com|destyy\.com|ceesty\.com"""
    val hrefli_regex = """href\.li"""
    val anonymz_regex = """anonymz\.com"""
    val shrink_service_regex = """shrink-service\.it"""
    val rapidcrypt_regex = """rapidcrypt\.net"""
    val linkup_regex = """linkup\.pro|buckler.link"""
    val linkhub_regex = """linkhub\.icu"""
    val swzz_regex = """swzz\.xyz"""
    val stayonline_regex = """stayonline\.pro"""
    val snip_regex = """[0-9a-z]+snip\.|uprotector\.xyz"""
    val linksafe_regex = """linksafe\.cc"""
    val protectlink_regex = """(?:s\.)?protectlink\.stream"""
    val uprot_regex = """uprot\.net"""
    val simple_iframe_regex = """cryptmango|xshield\.net|vcrypt\.club|isecure\.link"""
    val simple_redirect = """streamcrypt\.net/[^/]+|is\.gd|www\.vedere\.stream|isecure\.link"""
    val filecrypt_regex = """filecrypt\.cc"""

    val listRegex = listOf(nuovo_link_regex,adfly_regex, linkbucks_regex, adfocus_regex, lnxlu_regex, shst_regex, hrefli_regex, anonymz_regex,
        shrink_service_regex, rapidcrypt_regex, simple_iframe_regex, linkup_regex, linkhub_regex,
        swzz_regex, stayonline_regex, snip_regex, linksafe_regex, protectlink_regex, uprot_regex, simple_redirect,
        filecrypt_regex)

    suspend fun unshorten(uri: String, type : String?) : String{
        var uri = uri
        while (true){
            uri = uri.trim()
            var olduri = uri
            val domain = URI(uri).host

            if (URI(uri).host==null) {
                return "No domain found in URI!"
            }
            if (Regex(adfly_regex).find(domain) != null || type == "adfly") {
                uri = unshorten_adfly(uri)
            }
            if (Regex(linkup_regex).find(domain) != null || type == "linkup") {
                uri = unshorten_linkup(uri)
            }
            if (Regex(nuovo_link_regex).find(domain) != null || type == "nuovolink") {
                uri = unshorten_nuovo_link(uri)
            }
            if (Regex(nuovo_indirizzo_regex).find(domain) != null || type == "nuovoindirizzo") {
                uri = unshorten_nuovo_indirizzo(uri)
            }
            if (Regex(linksafe_regex).find(domain) != null || type == "linksafe") {
                uri = unshorten_linksafe(uri)
            }
            if (Regex(uprot_regex).find(domain) != null || type == "uprot") {
                uri = unshorten_uprot(uri)
            }
            /*
            if (Regex(linkbucks_regex).find(domain) != null || type == "linkbucks") {
                uri = unshorten_linkbucks(uri)
            }
            if (Regex(adfocus_regex).find(domain) != null || type == "adfocus") {
                uri = unshorten_adfocus(uri)
            }
            if (Regex(lnxlu_regex).find(domain) != null || type == "lnxlu") {
                uri = unshorten_lnxlu(uri)
            }
            if (Regex(shst_regex).find(domain) != null || type == "shst") {
                uri = unshorten_shst(uri)
            }
            if (Regex(hrefli_regex).find(domain) != null || type == "hrefli") {
                uri = unshorten_hrefli(uri)
            }
            if (Regex(anonymz_regex).find(domain) != null || type == "anonymz") {
                uri = unshorten_anonymz(uri)
            }
            if (Regex(shrink_service_regex).find(domain) != null || type == "shrink") {
                uri = unshorten_shrink(uri)
            }
            if (Regex(rapidcrypt_regex).find(domain) != null || type == "rapidcrypt") {
                uri = unshorten_rapidcrypt(uri)
            }
            if (Regex(simple_iframe_regex).find(domain) != null || type == "simple") {
                uri = unshorten_simple(uri)
            }

            if (Regex(linkhub_regex).find(domain) != null || type == "linkhub") {
                uri = unshorten_linkhub(uri)
            }
            if (Regex(swzz_regex).find(domain) != null || type == "swzz") {
                uri = unshorten_swzz(uri)
            }
            if (Regex(stayonline_regex).find(domain) != null || type == "stayonline") {
                uri = unshorten_stayonline(uri)
            }
            if (Regex(snip_regex).find(domain) != null || type == "snip") {
                uri = unshorten_snip(uri)
            }

            if (Regex(protectlink_regex).find(domain) != null || type == "protectlink") {
                uri = unshorten_protectlink(uri)
            }

            if (Regex(simple_redirect).find(domain) != null || type == "simple") {
                uri = app.get(uri).url
            }
            if (Regex(filecrypt_regex).find(domain) != null || type == "filecrypt") {
                uri = unshorten_filecrypt(uri)
            }

    */
            if (uri == olduri){
                break
            }
        }
        return uri
    }
    suspend fun unshorten_adfly(uri: String): String{
        val html = app.get(uri).text
        val ysmm = Regex("""var ysmm =.*\;?""").findAll(html).map { it.value }.toList()

        if (ysmm.size > 0){
            val ysmm1 = ysmm[0].replace(Regex("""var ysmm \= \'|\'\;"""),"")
            var left = ""
            var right = ""

            for (c in ysmm1.chunked(2)) {
                left += c[0]
                right = c[1] + right
            }
            val encoded_uri = (left + right).toMutableList()
            val numbers = encoded_uri.mapIndexed{i,n-> Pair(i,n)}.filter{it.second.isDigit()}
            for (el in numbers.chunked(2)){
                val xor = (el[0].second).code.xor(el[1].second.code)
                if (xor < 10){
                    encoded_uri[el[0].first] = xor.digitToChar()
                }
            }
            val encodedbytearray = encoded_uri.map { it.code.toByte() }.toByteArray()
            var decoded_uri = Base64.decode(encodedbytearray,Base64.DEFAULT).decodeToString().dropLast(16).drop(16)

            if (Regex("""go\.php\?u\=""").find(decoded_uri) != null){
                decoded_uri = Base64.decode(decoded_uri.replace(Regex("""(.*?)u="""),""),Base64.DEFAULT).decodeToString()
            }

            return decoded_uri
        }
        else {
            return uri
        }
        return uri
    }
    suspend fun unshorten_linkup(uri: String): String {

        var r: NiceResponse? = null
        var uri = uri
        if (uri.contains("/tv/")) {
            uri = uri.replace("/tv/", "/tva/")
        }
        else if (uri.contains("delta")) {
            uri = uri.replace("/delta/", "/adelta/")
        }
        else if (uri.contains("/ga/") || uri.contains("/ga2/")) {
            uri = Base64.decode(uri.split('/').last().toByteArray(), Base64.DEFAULT).decodeToString().trim()
        }
        else if (uri.contains("/speedx/")) {
            uri = uri.replace("http://linkup.pro/speedx", "http://speedvideo.net")
        }
        else {
            r = app.get(uri, allowRedirects = true)
            uri = r.url
            var link = Regex("<iframe[^<>]*src=\\'([^'>]*)\\'[^<>]*>").findAll(r.text).map { it.value }.toList()
            if (link.isEmpty()) {
                link = Regex("""action="(?:[^/]+.*?/[^/]+/([a-zA-Z0-9_]+))">""").findAll(r.text).map { it.value }.toList()
            }
            /*if (link.isEmpty()) {  //NEED HELP ON THIS ONE
                link =
                    Regex("""\${'$'}\("a\.redirect"\)\.attr\("href",\s*"\s*(http[^"]+)""").findAll(r.text).map { it.value }.toList()
            }
             */
            if (link.isNotEmpty()) {
                uri = link.toString()
            }
        }
        val short = Regex("""^https?://.*?(https?://.*)""").findAll(uri).map { it.value }.toList()
        if (short.isNotEmpty()){
            uri = short[0]
        }
        if (r==null){
            r = app.get(
                uri,
                allowRedirects = false)
            if (r.headers["location"]!= null){
                uri = r.headers["location"].toString()
            }
        }
        if (uri.contains("snip.")) {
            if (uri.contains("out_generator")) {
                uri = Regex("url=(.*)\$").find( uri)!!.value
            }
            else if (uri.contains("/decode/")) {
                uri = app.get(uri, allowRedirects = true).url
            }
        }
        return uri

    }
    fun unshorten_linksafe(uri:String) : String {
        return Base64.decode(uri.split("?url=").last().toByteArray(), Base64.DEFAULT).decodeToString()
    }
    suspend fun unshorten_nuovo_indirizzo(uri:String) : String {
        val soup = app.get(uri, allowRedirects = true)
        val header = soup.headers["refresh"]
        val link : String= if (header != null) {
            soup.headers["refresh"]!!.substringAfter("=") }
        else{
            "non trovato"
        }
        return link
    }
    suspend fun unshorten_nuovo_link(uri:String) : String {
        return app.get(uri, allowRedirects = true).document.selectFirst("a")!!.attr("href")

    }
    suspend  fun unshorten_uprot(uri: String): String {
        val page = app.get(uri).text
        Regex("""<a[^>]+href="([^"]+)""").findAll(page).map { it.value.replace("""<a href="""","") }.toList().forEach {  link ->
            if (link.contains("https://maxstream.video") || link.contains("https://uprot.net") && link != uri){
                return link
            }
        }
        return uri
    }

//TO BE COMPLETED

/*
    def _unshorten_linkbucks(self, uri):
    '''
    (Attempt) to decode linkbucks content. HEAVILY based on the OSS jDownloader codebase.
    This has necessidated a license change.

    '''
    if config.is_xbmc():
    import xbmc

    r = httptools.downloadpage(uri, timeout=self._timeout)

    firstGet = time.time()

    baseloc = r.url

    if "/notfound/" in r.url or \
    "(>Link Not Found<|>The link may have been deleted by the owner|To access the content, you must complete a quick survey\.)" in r.data:
    return uri, 'Error: Link not found or requires a survey!'

    link = None

    content = r.data

    regexes = [
    r"<div id=\"lb_header\">.*?/a>.*?<a.*?href=\"(.*?)\".*?class=\"lb",
    r"AdBriteInit\(\"(.*?)\"\)",
    r"Linkbucks\.TargetUrl = '(.*?)';",
    r"Lbjs\.TargetUrl = '(http://[^<>\"]*?)'",
    r"src=\"http://static\.linkbucks\.com/tmpl/mint/img/lb\.gif\" /></a>.*?<a href=\"(.*?)\"",
    r"id=\"content\" src=\"([^\"]*)",
    ]

    for regex in regexes:
    if self.inValidate(link):
    link = find_in_text(regex, content)

    if self.inValidate(link):
    match = find_in_text(r"noresize=\"[0-9+]\" src=\"(http.*?)\"", content)
    if match:
    link = find_in_text(r"\"frame2\" frameborder.*?src=\"(.*?)\"", content)

    if self.inValidate(link):
    scripts = re.findall("(<script type=\"text/javascript\">[^<]+</script>)", content)
    if not scripts:
    return uri, "No script bodies found?"

    js = False

    for script in scripts:
    # cleanup
    script = re.sub(r"[\r\n\s]+\/\/\s*[^\r\n]+", "", script)
    if re.search(r"\s*var\s*f\s*=\s*window\['init'\s*\+\s*'Lb'\s*\+\s*'js'\s*\+\s*''\];[\r\n\s]+", script):
    js = script

    if not js:
    return uri, "Could not find correct script?"

    token = find_in_text(r"Token\s*:\s*'([a-f0-9]{40})'", js)
    if not token:
    token = find_in_text(r"\?t=([a-f0-9]{40})", js)

    assert token

    authKeyMatchStr = r"A(?:'\s*\+\s*')?u(?:'\s*\+\s*')?t(?:'\s*\+\s*')?h(?:'\s*\+\s*')?K(?:'\s*\+\s*')?e(?:'\s*\+\s*')?y"
    l1 = find_in_text(r"\s*params\['" + authKeyMatchStr + r"'\]\s*=\s*(\d+?);", js)
    l2 = find_in_text(
    r"\s*params\['" + authKeyMatchStr + r"'\]\s*=\s?params\['" + authKeyMatchStr + r"'\]\s*\+\s*(\d+?);",
    js)

    if any([not l1, not l2, not token]):
    return uri, "Missing required tokens?"

    authkey = int(l1) + int(l2)

    p1_url = urljoin(baseloc, "/director/?t={tok}".format(tok=token))
    r2 = httptools.downloadpage(p1_url, timeout=self._timeout)

    p1_url = urljoin(baseloc, "/scripts/jquery.js?r={tok}&{key}".format(tok=token, key=l1))
    r2 = httptools.downloadpage(p1_url, timeout=self._timeout)

    time_left = 5.033 - (time.time() - firstGet)
    if config.is_xbmc():
    xbmc.sleep(max(time_left, 0) * 1000)
    else:
    time.sleep(5 * 1000)

    p3_url = urljoin(baseloc, "/intermission/loadTargetUrl?t={tok}&aK={key}&a_b=false".format(tok=token,
    key=str(authkey)))
    r3 = httptools.downloadpage(p3_url, timeout=self._timeout)

    resp_json = json.loads(r3.data)
    if "Url" in resp_json:
    return resp_json['Url'], r3.code

    return "Wat", "wat"
*/

/*
    def inValidate(self, s):
    # Original conditional:
    # (s == null || s != null && (s.matches("[\r\n\t ]+") || s.equals("") || s.equalsIgnoreCase("about:blank")))
    if not s:
    return True

    if re.search("[\r\n\t ]+", s) or s.lower() == "about:blank":
    return True
    else:
    return False
*/

/*
    def _unshorten_adfocus(self, uri):
    orig_uri = uri
    try:

    r = httptools.downloadpage(uri, timeout=self._timeout)
    html = r.data

    adlink = re.findall("click_url =.*;", html)

    if len(adlink) > 0:
    uri = re.sub('^click_url = "|"\;$', '', adlink[0])
    if re.search(r'http(s|)\://adfoc\.us/serve/skip/\?id\=', uri):
    http_header = dict()
    http_header["Host"] = "adfoc.us"
    http_header["Referer"] = orig_uri

    r = httptools.downloadpage(uri, headers=http_header, timeout=self._timeout)

    uri = r.url
    return uri, r.code
    else:
    return uri, 'No click_url variable found'
    except Exception as e:
    return uri, str(e)
*/

/*
    def _unshorten_lnxlu(self, uri):
    try:
    r = httptools.downloadpage(uri, timeout=self._timeout)
    html = r.data

    code = re.findall('/\?click\=(.*)\."', html)

    if len(code) > 0:
    payload = {'click': code[0]}
    r = httptools.downloadpage(
    'http://lnx.lu?' + urlencode(payload),
    timeout=self._timeout)
    return r.url, r.code
    else:
    return uri, 'No click variable found'
    except Exception as e:
    return uri, str(e)
*/

/*
    def _unshorten_shst(self, uri):
    try:
    # act like a crawler
    r = httptools.downloadpage(uri, timeout=self._timeout, headers=[['User-Agent', '']])
    uri = r.url
    # html = r.data
    # session_id = re.findall(r'sessionId\:(.*?)\"\,', html)
    # if len(session_id) > 0:
    #     session_id = re.sub(r'\s\"', '', session_id[0])
    #
    #     http_header = dict()
    #     http_header["Content-Type"] = "application/x-www-form-urlencoded"
    #     http_header["Host"] = "sh.st"
    #     http_header["Referer"] = uri
    #     http_header["Origin"] = "http://sh.st"
    #     http_header["X-Requested-With"] = "XMLHttpRequest"
    #
    #     if config.is_xbmc():
    #         import xbmc
    #         xbmc.sleep(5 * 1000)
    #     else:
    #         time.sleep(5 * 1000)
    #
    #     payload = {'adSessionId': session_id, 'callback': 'c'}
    #     r = httptools.downloadpage(
    #         'http://sh.st/shortest-url/end-adsession?' +
    #         urlencode(payload),
    #         headers=http_header,
    #         timeout=self._timeout)
    #     response = r.data[6:-2].decode('utf-8')
    #
    #     if r.code == 200:
    #         resp_uri = json.loads(response)['destinationUrl']
    #         if resp_uri is not None:
    #             uri = resp_uri
    #         else:
    #             return uri, 'Error extracting url'
    #     else:
    #         return uri, 'Error extracting url'

    return uri, r.code

    except Exception as e:
    return uri, str(e)
*/

/*
    def _unshorten_hrefli(self, uri):
    try:
    # Extract url from query
    parsed_uri = urlparse(uri)
    extracted_uri = parsed_uri.query
    if not extracted_uri:
    return uri, 200
    # Get url status code
    r = httptools.downloadpage(
    extracted_uri,
    timeout=self._timeout,
    follow_redirects=False)
    return r.url, r.code
    except Exception as e:
    return uri, str(e)
*/

/*
    def _unshorten_anonymz(self, uri):
    # For the moment they use the same system as hrefli
    return self._unshorten_hrefli(uri)
*/

/*
    def _unshorten_shrink_service(self, uri):
    try:
    r = httptools.downloadpage(uri, timeout=self._timeout, cookies=False)
    html = r.data

    uri = re.findall(r"<input type='hidden' name='\d+' id='\d+' value='([^']+)'>", html)[0]

    from core import scrapertools
    uri = scrapertools.decodeHtmlentities(uri)

    uri = uri.replace("&sol;", "/") \
    .replace("&colon;", ":") \
    .replace("&period;", ".") \
    .replace("&excl;", "!") \
    .replace("&num;", "#") \
    .replace("&quest;", "?") \
    .replace("&lowbar;", "_")

    return uri, r.code

    except Exception as e:
    return uri, str(e)
*/

/*
    def _unshorten_rapidcrypt(self, uri):
    try:
    r = httptools.downloadpage(uri, timeout=self._timeout, cookies=False)
    html = r.data
    html = html.replace("'",'"')

    if 'embed' in uri:
    uri = re.findall(r'<a class="play-btn" href=(?:")?([^">]+)', html)[0]
    else:
    uri = re.findall(r'<a class="push_button blue" href=(?:")?([^">]+)', html)[0]
    return uri, r.code

    except Exception as e:
    return uri, 0
*/

/*
    def _unshorten_simple_iframe(self, uri):
    try:
    r = httptools.downloadpage(uri, timeout=self._timeout, cookies=False)
    html = r.data

    uri = re.findall(r'<iframe\s+src="([^"]+)', html)[0]

    return uri, r.code

    except Exception as e:
    return uri, str(e)
*/

/*
    def _unshorten_vcrypt(self, uri):
    httptools.set_cookies({'domain': 'vcrypt.net', 'name': 'saveMe', 'value': '1'})
    httptools.set_cookies({'domain': 'vcrypt.pw', 'name': 'saveMe', 'value': '1'})
    try:
    headers = {}
    if 'myfoldersakstream.php' in uri or '/verys/' in uri:
    return uri, 0
    r = None

    if 'shield' in uri.split('/')[-2]:
    uri = decrypt_aes(uri.split('/')[-1], b"naphajU2usWUswec")
    else:
    spl = uri.split('/')
    spl[0] = 'http:'

    if 'sb/' in uri or 'akv/' in uri or 'wss/' in uri:
    import datetime, hashlib
    from base64 import b64encode
    # ip = urlopen('https://api.ipify.org/').read()
    ip = b'31.220.1.77'
    day = datetime.date.today().strftime('%Y%m%d')
    if PY3: day = day.encode()
    headers = {
        "Cookie": hashlib.md5(ip+day).hexdigest() + "=1;saveMe=1"
    }
    spl[3] += '1'
    if spl[3] in ['wss1', 'sb1']:
    spl[4] = b64encode(spl[4].encode('utf-8')).decode('utf-8')

    uri = '/'.join(spl)
    r = httptools.downloadpage(uri, timeout=self._timeout, headers=headers, follow_redirects=False, verify=False)
    if 'Wait 1 hour' in r.data:
    uri = ''
    logger.error('IP bannato da vcrypt, aspetta un ora')
    else:
    uri = r.headers['location']
    return uri, r.code if r else 200
    except Exception as e:
    logger.error(e)
    return uri, 0
*/

/*
    def _unshorten_linkhub(self, uri):
    try:
    r = httptools.downloadpage(uri, follow_redirect=True, timeout=self._timeout, cookies=False)
    if 'get/' in r.url:
    uri = 'https://linkhub.icu/view/' + re.search('\.\./view/([^"]+)', r.data).group(1)
    logger.info(uri)
    r = httptools.downloadpage(uri, follow_redirect=True, timeout=self._timeout, cookies=False)
    links = re.findall('<a href="(http[^"]+)', r.data)
    if len(links) == 1:
    uri = links[0]
    else:
    uri = "\n".join(links)  # folder
    return uri, r.code
    except Exception as e:
    return uri, str(e)
*/

/*
    def _unshorten_swzz(self, uri):
    try:
    r = httptools.downloadpage(uri)
    if r.url != uri:
    return r.url, r.code
    data = r.data
    if "link =" in data or 'linkId = ' in data:
    uri = scrapertools.find_single_match(data, 'link(?:Id)? = "([^"]+)"')
    if 'http' not in data:
    uri = 'https:' + uri
    else:
    match = scrapertools.find_single_match(data, r'<meta name="og:url" content="([^"]+)"')
    match = scrapertools.find_single_match(data, r'URL=([^"]+)">') if not match else match

    if not match:
    from lib import jsunpack

    try:
    data = scrapertools.find_single_match(data.replace('\n', ''),
    r"(eval\s?\(function\(p,a,c,k,e,d.*?)</script>")
    data = jsunpack.unpack(data)

    logger.debug("##### play /link/ unpack ##\n%s\n##" % data)
    except:
    logger.debug("##### The content is yet unpacked ##\n%s\n##" % data)

    uri = scrapertools.find_single_match(data, r'var link(?:\s)?=(?:\s)?"([^"]+)";')
    else:
    uri = match
    if uri.startswith('/'):
    uri = "http://swzz.xyz" + uri
    if not "vcrypt" in data:
    uri = httptools.downloadpage(data).data
    return uri, r.code
    except Exception as e:
    return uri, str(e)
*/

/*
    def _unshorten_stayonline(self, uri):
    # from core.support import dbg;dbg()
    try:
    id = uri.split('/')[-2]
    reqUrl = 'https://stayonline.pro/ajax/linkView.php'
    p = urlencode({"id": id, "ref": ""})
    time.sleep(1)
    r = httptools.downloadpage(reqUrl, post=p, headers={'Referer': uri})
    data = r.data
    try:
    import json
    uri = json.loads(data)['data']['value']
    except:
    uri = scrapertools.find_single_match(data, r'"value"\s*:\s*"([^"]+)"')
    uri = httptools.downloadpage(uri, only_headers=True).url
    return uri, r.code
    except Exception as e:
    return uri, str(e)
*/

/*
    def _unshorten_snip(self, uri):
    if 'out_generator' in uri:
    new_uri = re.findall('url=(.*)$', uri)[0]
    if not new_uri.startswith('http'):
    new_uri = httptools.downloadpage(uri, follow_redirects=False).headers['Location']
    uri = new_uri
    if '/decode/' in uri:
    uri = decrypt_aes(uri.split('/')[-1], b"whdbegdhsnchdbeh")

    # scheme, netloc, path, query, fragment = urlsplit(uri)
    # splitted = path.split('/')
    # splitted[1] = 'outlink'
    # r = httptools.downloadpage(uri, follow_redirects=False, post={'url': splitted[2]})
    # if 'location' in r.headers and r.headers['location']:
    #     new_uri = r.headers['location']
    # else:
    #     r = httptools.downloadpage(scheme + '://' + netloc + "/".join(splitted) + query + fragment,
    #                                follow_redirects=False, post={'url': splitted[2]})
    #     if 'location' in r.headers and r.headers['location']:
    #         new_uri = r.headers['location']
    # if new_uri and new_uri != uri:
    #     uri = new_uri
    return uri, 200

*/

/*
    def _unshorten_protectlink(self, uri):
    if '?data=' in uri:
    return b64decode(uri.split('?data=')[-1]).decode(), 200
    else:
    return httptools.downloadpage(uri, only_headers=True, follow_redirects=False).headers.get('location', uri), 200


    # container, for returning only the first result
    */

/*
    def _unshorten_filecrypt(self, uri):
    url = ''
    try:
    fc = FileCrypt(uri)
    url = fc.unshorten(fc.list_files()[0][1])
    except:
    import traceback
    logger.error(traceback.format_exc())
    if url:
    return url, 200
    else:
    return uri, 200


 */




}

