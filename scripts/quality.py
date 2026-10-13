#!/usr/bin/env python3
# Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
"""独立全新测试库验证；仅生成QA资料，私有状态不提交。知华科技 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。"""
import argparse,base64,json,os,secrets,urllib.request,urllib.error,uuid
from datetime import datetime,timedelta,timezone
from pathlib import Path
from http_client import Client,env


def command(**kwargs):
    return dict(kwargs,requestId=str(uuid.uuid4()))


def upload(client,garment,version,data,status=200):
    boundary='qa'+uuid.uuid4().hex
    raw=(f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="qa.png"\r\nContent-Type: image/png\r\n\r\n'.encode()+data+f'\r\n--{boundary}--\r\n'.encode())
    if not client.csrf:client.csrf=client.request('/auth/csrf')
    r=urllib.request.Request(client.base+f'/api/garments/{garment}/photos?version={version}',data=raw,headers={'Content-Type':'multipart/form-data; boundary='+boundary,client.csrf['header']:client.csrf['token']},method='POST')
    try:
        with client.opener.open(r,timeout=180) as out:code,body=out.status,out.read()
    except urllib.error.HTTPError as out:code,body=out.code,out.read()
    assert code==status,(code,body[:100])
    client.checks.append({'path':f'/garments/{garment}/photos','status':code})
    return json.loads(body)


def main():
    p=argparse.ArgumentParser();p.add_argument('--base',default='http://127.0.0.1:8158');p.add_argument('--env-file');p.add_argument('--state',default='output/washdesk-quality-state.json');a=p.parse_args()
    settings=env(a.env_file);admin=Client(a.base);anonymous=Client(a.base);anonymous.request('/orders',status=401)
    me=admin.login(settings.get('ADMIN_USERNAME','admin'),settings['ADMIN_PASSWORD']);assert 'passwordHash' not in me
    suffix=uuid.uuid4().hex[:8];private_pw='Aa9'+secrets.token_hex(20)
    roles=admin.request('/admin/roles');role=lambda scope:next(r['id'] for r in roles if r['scope']==scope)
    account=admin.request('/admin/users',{'username':'qa-'+suffix,'displayName':'QA 验收账号','password':private_pw,'roleId':role('CUSTOMER'),'departmentId':1,'enabled':True})
    cust=lambda name,billing='RETAIL',credit=0,accountId=None:admin.request('/masters/customers',{'departmentId':1,'name':name,'contact':'','billing':billing,'currency':'CNY','creditLimit':credit,'accountId':accountId,'enabled':True})
    retail=cust('QA 零售验收客户',accountId=account['id']);corporate=cust('QA 企业月结验收','MONTHLY',100000)
    account=admin.request('/admin/users/'+str(account['id']),dict(account,roleId=role('ALL'),password=''),method='PUT')
    service=admin.request('/masters/services',{'departmentId':1,'name':'QA 外套干洗','nameEn':'QA Coat dry cleaning','category':'DRYCLEAN','currency':'CNY','price':3200,'enabled':True})
    rack=admin.request('/masters/racks',{'departmentId':1,'code':'QA-'+suffix,'name':'QA A区挂架','capacity':8,'enabled':True})
    date=(datetime.now(timezone(timedelta(hours=8)))+timedelta(days=2)).date().isoformat();month=datetime.now(timezone(timedelta(hours=8))).strftime('%Y-%m')
    def order(c,n=1):
        b=command(departmentId=1,customerId=c['id'],customerVersion=c['version'],dueDate=date,note='QA 流程验证资料',items=[{'serviceId':service['id'],'serviceVersion':service['version'],'description':'QA 外套 '+str(i+1),'conditionNote':'QA 领口痕迹已记录；用于验收'} for i in range(n)])
        d=admin.request('/orders',b);assert admin.request('/orders',b)['order']['id']==d['order']['id'];return d
    def detail(d):return admin.request('/orders/'+str(d['order']['id']))
    def transition(d,i,action,status=200,note='QA 工序核对'):
        d=detail(d);g=d['garments'][i];return admin.request(f'/garments/{g["id"]}/{action}',command(version=g['version'],note=note,rackId=rack['id']),status=status)
    def ready(d,n):
        for i in range(n):
            for action in ['start','ready','rack']:transition(d,i,action)
        return detail(d)
    def payment(d,amount,ref=None,status=200):
        d=detail(d);return admin.request('/payments/ORDER/'+str(d['order']['id']),command(version=d['order']['version'],kind='PAYMENT',amount=amount,reference=ref or 'QA-P-'+uuid.uuid4().hex,method='CASH'),status=status)
    def pickup(d,indices,status=200):
        d=detail(d);return admin.request('/orders/'+str(d['order']['id'])+'/pickup',command(version=d['order']['version'],barcodes=[d['garments'][i]['barcode'] for i in indices],receiver='QA 取衣验收人',note='QA 交接核对'),status=status)
    d=order(retail,5);oid=d['order']['id'];g=d['garments'][0]
    # Small valid PNG verifies binary persistence, not a fabricated customer photograph.
    png=base64.b64decode('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=')
    photo=upload(admin,g['id'],g['version'],png);assert admin.request('/photos/'+str(photo['id']),binary=True)==png
    d=ready(d,5);pickup(d,[0],409);payment(d,15900);payment(d,101,status=409);payment(d,100);d=detail(d)
    g=d['garments'][0];b=command(version=g['version'],note='QA 返洗核验');admin.request('/garments/'+str(g['id'])+'/rewash',b);admin.request('/garments/'+str(g['id'])+'/rewash',b);d=detail(d);assert d['garments'][0]['rewashCount']==1 and d['garments'][0]['rackId'] is None
    transition(d,0,'ready');transition(d,0,'rack');d=detail(d);d=pickup(d,[0,1,2]);assert d['delivered']==3 and d['racked']==2 and d['order']['state']=='OPEN'
    partial=detail(d);pickup(d,[0],409);d=pickup(d,[3,4]);assert d['order']['state']=='COMPLETED' and d['balance']==0 and len(d['pickups'])==2
    transition(d,4,'rewash',409)
    cancelled=order(retail);pmt=payment(cancelled,3200);cancelled=detail(cancelled);admin.request('/orders/'+str(cancelled['order']['id'])+'/cancel',command(version=cancelled['order']['version'],note='QA 取消退款'));cancelled=detail(cancelled)
    refund=command(version=cancelled['order']['version'],kind='REFUND',amount=3200,originalId=pmt['id'],reference='QA-R-'+suffix,method='BANK');admin.request('/payments/ORDER/'+str(cancelled['order']['id']),refund);admin.request('/payments/ORDER/'+str(cancelled['order']['id']),refund);assert detail(cancelled)['paid']==0
    corp=order(corporate,2);corp=ready(corp,2);payment(corp,100,status=409);pickup(corp,[0]);corp=pickup(corp,[1]);invoice=admin.request('/invoices',command(customerId=corporate['id'],month=month));iid=invoice['invoice']['id'];admin.request('/invoices',command(customerId=corporate['id'],month=month),status=409)
    b=command(version=invoice['invoice']['version'],kind='PAYMENT',amount=6400,reference='QA-I-'+suffix,method='BANK');admin.request('/payments/INVOICE/'+str(iid),b);admin.request('/payments/INVOICE/'+str(iid),b);assert admin.request('/invoices/'+str(iid))['balance']==0
    # Customer privacy, private photos and live role enforcement.
    account=admin.request('/admin/users/'+str(account['id']),dict(account,roleId=role('CUSTOMER'),password=''),method='PUT')
    customer=Client(a.base);customer.login(account['username'],private_pw);customer.request('/orders/'+str(oid));customer.request('/orders/'+str(corp['order']['id']),status=403);customer.request('/photos/'+str(photo['id']),binary=True);customer.request('/catalog',status=403);customer.request('/admin/users',status=403);customer.request('/reports',status=403)
    qa=Client(a.base);qa.login(account['username'],private_pw);modified=dict(account,roleId=next(r['id'] for r in roles if r['name']=='加工质检'),password='');account=admin.request('/admin/users/'+str(account['id']),modified,method='PUT');assert qa.request('/catalog')['customers']==[];qa.request('/orders/'+str(oid)+'/pickup',command(version=d['order']['version'],barcodes=[],receiver='QA'),status=403);qa.request('/admin/users',status=403)
    account=admin.request('/admin/users/'+str(account['id']),dict(account,roleId=role('ALL'),password=''),method='PUT');qa.request('/admin/users');assert all('passwordHash' not in row for row in admin.request('/admin/users'))
    # Cross-store denial uses actual second-store resources.
    dept=admin.request('/admin/departments',{'name':'QA 第二门店','zone':'UTC','enabled':True});other_customer=admin.request('/masters/customers',{'departmentId':dept['id'],'name':'QA Other store','contact':'','billing':'RETAIL','currency':'CNY','creditLimit':0,'enabled':True})
    account=admin.request('/admin/users/'+str(account['id']),dict(account,roleId=next(r['id'] for r in roles if r['name']=='前台'),password=''),method='PUT');qa.request('/masters/customers/'+str(other_customer['id']),dict(other_customer,name='QA forbidden'),status=403,method='PUT');account=admin.request('/admin/users/'+str(account['id']),dict(account,roleId=role('ALL'),password=''),method='PUT')
    # Quote version changes must not silently alter an intake charge.
    old_service=dict(service)
    service=admin.request('/masters/services/'+str(service['id']),dict(service,price=3300),method='PUT')
    admin.request('/orders',command(departmentId=1,customerId=retail['id'],customerVersion=retail['version'],dueDate=date,note='QA stale quote',items=[{'serviceId':old_service['id'],'serviceVersion':old_service['version'],'description':'QA stale quote','conditionNote':'QA'}]),status=409)
    service=admin.request('/masters/services/'+str(service['id']),dict(service,price=3200),method='PUT')
    # A five-piece order kept racked for manual browser validation.
    ui_order=order(retail,5);ui_order=ready(ui_order,5);payment(ui_order,16000);ui_order=detail(ui_order)
    summary=admin.request('/reports');assert summary['currencies']['CNY']['receivable']==0;admin.request('/audit');admin.request('/security-audit');csv=admin.request('/export',binary=True);assert b'zhuatech2' not in csv
    state={'base':a.base,'username':account['username'],'password':private_pw,'accountId':account['id'],'account':account,'roles':roles,'customerAccount':account,'retail':retail,'uiOrderId':ui_order['order']['id'],'garments':ui_order['garments'],'partialOrder':partial,'completedOrderId':oid,'invoiceId':iid,'photoId':photo['id'],'photoSha256':__import__('hashlib').sha256(png).hexdigest(),'report':summary,'checks':admin.checks+qa.checks+customer.checks+anonymous.checks}
    path=Path(a.state);path.parent.mkdir(parents=True,exist_ok=True);fd=os.open(path,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600)
    with os.fdopen(fd,'w')as f:json.dump(state,f,ensure_ascii=False,indent=2)
    print(json.dumps({'status':'passed','httpChecks':len(state['checks']),'flow':'five garments: pickup 3 then 2; corporate statement; refunds; private photo; live permissions'},ensure_ascii=False))

if __name__=='__main__':main()
