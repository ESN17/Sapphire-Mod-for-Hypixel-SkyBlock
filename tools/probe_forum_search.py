"""Read-only public search probe. Never log the anonymous CSRF token or cookies."""
from html.parser import HTMLParser
import http.cookiejar
import urllib.request
import urllib.parse

class Form(HTMLParser):
    token = ''
    def handle_starttag(self, tag, attrs):
        a = dict(attrs)
        if tag == 'input' and a.get('name') == '_xfToken':
            self.token = a.get('value', '')

if __name__ == '__main__':
    opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
    headers = {'User-Agent': 'Mozilla/5.0'}
    with opener.open(urllib.request.Request('https://hypixel.net/search/?q=Voidgloom&c[title_only]=1', headers=headers), timeout=15) as r:
        parser = Form(); parser.feed(r.read(400000).decode())
    body = urllib.parse.urlencode({'keywords': 'Voidgloom guide', '_xfToken': parser.token, 'c[title_only]': '1', 'order': 'date'}).encode()
    try:
        with opener.open(urllib.request.Request('https://hypixel.net/search/search', data=body, headers=headers), timeout=15) as r:
            text = r.read(400000).decode()
            import re
            print('search response:', r.status, r.url, len(text))
            print('thread results:', sorted(set(re.findall(r'href="(/threads/[^"#?]+)', text)))[:8])
    except Exception as e:
        print('public search unavailable:', type(e).__name__, str(e))
