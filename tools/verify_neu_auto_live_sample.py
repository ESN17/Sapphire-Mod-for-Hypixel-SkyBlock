"""Explicit, bounded live DeepSeek check using two genuine NEU changes in an isolated fixture.

The sample library is test-only, stays under build/, and is never exported/published.
"""
import json
from unittest.mock import patch
import neu_auto_translate as n

def main():
    config=n.read(n.ROOT/'tools/neu-auto-config.json')
    config.update(state_directory='build/neu-auto-live-sample/state',output_directory='build/neu-auto-live-sample/pending',
                  max_items_per_run=2,max_requests_per_run=18,max_requests_per_day=20)
    _,items,_=n.load_payload(n.ROOT/config['baseline'])
    scan=n.read(n.ROOT/'translation-state/neu-auto/latest-scan.json')
    selected={'ANTI_BITE_SCARF','VINYL_SACK'}
    rows={item_id:{'id':item_id,**n.previous_source(value)} for item_id,(_,value) in items.items()}
    actual={c['id']:c['after'] for c in scan['changes']['items'] if c['id'] in selected}
    if actual.keys()!=selected:raise ValueError('Selected live fixtures no longer present; review fixtures before changing them')
    rows.update(actual)
    with patch.object(n,'source_snapshot',return_value=('sample-of-'+scan['neu_revision'],rows)):
        report=n.run(config)
    result=n.summary(report)|{'test_only':True,'selected_item_ids':sorted(selected),'failures':report.get('failed',[])}
    n.write(n.ROOT/'build/neu-auto-live-sample/result.json',result)
    print(json.dumps(result,ensure_ascii=False,indent=2))
    return 0 if report['status'] in ('pending_review','already_pending_review') else 1

if __name__=='__main__':raise SystemExit(main())
