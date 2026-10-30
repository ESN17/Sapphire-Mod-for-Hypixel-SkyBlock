"""Minimal official DeepSeek JSON client. Never logs credentials or HTTP response bodies."""
import json
import os
import urllib.request
import urllib.error
from pathlib import Path
from urllib.parse import urlparse

class ApiError(RuntimeError):
    def __init__(self, message, retryable=False):
        super().__init__(message); self.retryable=retryable

class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        raise ApiError('API redirect refused; credentials were not forwarded')

class DeepSeek:
    def __init__(self, config=Path('deepseek.local.json')):
        self.config=json.loads(Path(config).read_text(encoding='utf-8-sig'))
        self.key=os.environ.get('DEEPSEEK_API_KEY') or self.config.get('api_key','').strip()
        if not self.key:raise ApiError('Configure api_key locally or DEEPSEEK_API_KEY')
        base=self.config.get('base_url','https://api.deepseek.com').rstrip('/')
        url=urlparse(base)
        if url.scheme!='https' or url.hostname!='api.deepseek.com' or url.username or url.password or url.query or url.fragment or url.path not in ('','/v1'):
            raise ApiError('This client only sends credentials to the official HTTPS DeepSeek endpoint')
        self.url=base+'/chat/completions'
    def complete(self, system, data):
        body={'model':self.config.get('model','deepseek-flash'),
              'messages':[{'role':'system','content':system},{'role':'user','content':json.dumps(data,ensure_ascii=False)}],
              'response_format':{'type':'json_object'},'thinking':{'type':'disabled'},'stream':False,
              'max_tokens':int(self.config.get('max_output_tokens',12000)),'temperature':0.1}
        request=urllib.request.Request(self.url,data=json.dumps(body,ensure_ascii=False).encode(),
            headers={'Authorization':'Bearer '+self.key,'Content-Type':'application/json'},method='POST')
        try:
            with urllib.request.build_opener(NoRedirect).open(request,timeout=int(self.config.get('timeout_seconds',120))) as response:
                raw=response.read(8*1024*1024+1)
                if len(raw)>8*1024*1024:raise ApiError('API response exceeded size limit')
        except urllib.error.HTTPError as e:
            raise ApiError('DeepSeek HTTP '+str(e.code),e.code in (408,429,500,502,503,504)) from None
        except (urllib.error.URLError,TimeoutError,OSError):raise ApiError('DeepSeek connection or timeout error',True) from None
        try:
            envelope=json.loads(raw);choice=envelope['choices'][0]
            if choice.get('finish_reason')!='stop':raise ApiError('Incomplete API output; batch must be retried smaller',True)
            result=json.loads(choice['message']['content'])
            if not isinstance(result,dict):raise ValueError('object required')
            usage={k:v for k,v in envelope.get('usage',{}).items() if k in ('prompt_tokens','completion_tokens','total_tokens','prompt_cache_hit_tokens','prompt_cache_miss_tokens') and isinstance(v,int)}
            return result,usage
        except (ValueError,TypeError,KeyError,IndexError):raise ApiError('Invalid JSON output from API',True) from None

if __name__=='__main__':
    try:
        result,usage=DeepSeek().complete('Return json only: {"ok":true}.',{'connection_test':True})
        if result.get('ok') is not True:raise ApiError('Unexpected connection-test output')
        print(json.dumps({'connection':'ok','usage':usage}))
    except ApiError as e:print(str(e));raise SystemExit(1)
