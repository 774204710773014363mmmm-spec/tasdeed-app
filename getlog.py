import json
import urllib.request

tok = open('gh_token.txt', encoding='utf-8').read().strip()

class NoAuthRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        newreq = super().redirect_request(req, fp, code, msg, headers, newurl)
        if newreq is not None:
            newreq.remove_header('Authorization')
        return newreq

opener = urllib.request.build_opener(NoAuthRedirect)

def api(path):
    req = urllib.request.Request('https://api.github.com' + path, headers={'Authorization': 'Bearer ' + tok, 'Accept': 'application/vnd.github+json'})
    return json.load(urllib.request.urlopen(req))

jobs = api('/repos/774204710773014363mmmm-spec/tasdeed-app/actions/runs/31646296373/jobs')['jobs']
jid = jobs[0]['id']
print('job', jid)
url = f'https://api.github.com/repos/774204710773014363mmmm-spec/tasdeed-app/actions/jobs/{jid}/logs'
logs = opener.open(urllib.request.Request(url, headers={'Authorization': 'Bearer ' + tok})).read().decode('utf-8', 'replace')
open('joblog.txt', 'w', encoding='utf-8').write(logs)
print('saved', len(logs))