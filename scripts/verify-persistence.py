#!/usr/bin/env python3
# Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
"""重启或备份恢复后检查实际业务和照片散列。知华科技 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。"""
import argparse,json,hashlib
from pathlib import Path
from http_client import Client
p=argparse.ArgumentParser();p.add_argument('--state',required=True);p.add_argument('--base');a=p.parse_args();s=json.loads(Path(a.state).read_text());c=Client(a.base or s['base']);c.login(s['username'],s['password']);assert c.request('/reports')==s['report'];d=c.request('/orders/'+str(s['completedOrderId']));assert d['order']['state']=='COMPLETED' and d['delivered']==5 and d['balance']==0 and len(d['pickups'])==2;assert c.request('/invoices/'+str(s['invoiceId']))['balance']==0;assert hashlib.sha256(c.request('/photos/'+str(s['photoId']),binary=True)).hexdigest()==s['photoSha256'];print('Business records, payments, pickups, invoices and private photo hash preserved.')
