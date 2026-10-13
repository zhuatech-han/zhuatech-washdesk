#!/usr/bin/env python3
# Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
"""检查公开文件、品牌、实际截图、非商业许可和私有凭据排除。知华科技 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。"""
import hashlib,json,re,subprocess
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
QR={'logo.jpg':'90bf87ff294f5ee8cdaef4b10155139173d97d93a88d7ea9679b9721ba05c962','wechat-zhuatech.png':'a1205aeec110016ca889693892250a11d449489f64d27c714816b73c3fc645e1','wechat-zhuatech2.png':'98df6f15d17f94b88bc8bc115262b264fab0cfb5e6ca9443aaaf4143c5275215'}

def main():
    checks=[]
    def check(ok,label):
        if not ok:raise AssertionError(label)
        checks.append(label)
    for name,digest in QR.items():check(hashlib.sha256((ROOT/'frontend/public/brand'/name).read_bytes()).hexdigest()==digest,'original brand bytes '+name)
    license=(ROOT/'LICENSE').read_text();check('Non-Commercial' in license and '上海如静知华信息科技有限公司' in license and 'zhuatech2' in license,'own non-commercial LICENSE')
    for lang in ['README.md','README.en.md']:
        text=(ROOT/lang).read_text();check('](README.md)' in text and '](README.en.md)' in text,'language switch '+lang);check('https://www.zhuatech.cn/' in text,'official website '+lang)
        refs=re.findall(r'!\[[^\]]*\]\(([^)]+)\)|<img[^>]+src="([^"]+)"',text);screens=[]
        for pair in refs:
            value=next(v for v in pair if v);check(not value.startswith(('/','http')),'stable relative image '+value);path=ROOT/value;check(path.is_file(),'image exists '+value)
            data=path.read_bytes();check(data[:3]==b'\xff\xd8\xff' or data[:8]==b'\x89PNG\r\n\x1a\n','actual raster bytes '+value)
            if value.startswith('docs/screenshots/'):screens.append(value)
        check(len(set(screens))>=6,'six real page categories '+lang)
        if lang=='README.md':
            check(all('frontend/public/brand/'+n in text for n in QR if n.startswith('wechat')),'two CN QR paths');check('商业授权或深度定制开发请联系知华科技' in text,'CN commercial contact')
        else:
            check('wechat-' not in text and '<img' not in text,'EN no WeChat QR');check(all(v in text for v in ['mailto:han@zhuatech.cn','mailto:jack@zhuatech.cn','https://wa.me/8617521234993']),'EN contacts')
    tracked=subprocess.run(['git','ls-files','-z'],cwd=ROOT,check=True,stdout=subprocess.PIPE).stdout.decode().split('\0')
    check(bool(tracked[0]),'git tracks release files')
    private={}
    if (ROOT/'.env').exists():private=dict(line.split('=',1) for line in (ROOT/'.env').read_text().splitlines() if '=' in line and not line.startswith('#'))
    for relative in filter(None,tracked):
        check(not any(x in Path(relative).parts for x in ['node_modules','target','output','private-backups','.git']), 'no private/build path '+relative)
        check(relative not in ['.env'] and not relative.endswith('.zip'),'no private environment/archive '+relative)
        path=ROOT/relative
        if path.suffix in ['.jpg','.png']:continue
        text=path.read_text()
        for key in ['MYSQL_ROOT_PASSWORD','DATABASE_PASSWORD','ADMIN_PASSWORD']:
            check(not private.get(key) or private[key] not in text,'no actual credential '+relative+' '+key)
        check(not re.search(r'gh[pousr]_[A-Za-z0-9]{25,}|-----BEGIN (?:RSA |OPENSSH )?PRIVATE KEY',text),'no token or private key '+relative)
        if path.suffix in ['.java','.vue','.js','.css','.py','.sql','.yaml','.yml'] or path.name in ['Dockerfile','nginx.conf']:
            check('上海如静知华信息科技有限公司' in text[:650] and 'https://www.zhuatech.cn/' in text[:650] and 'zhuatech2' in text[:650],'source attribution '+relative)
    docker=(ROOT/'backend/Dockerfile').read_text();check(not re.search(r'-D(?:skipTests|maven.test.skip)',docker),'Docker tests not skipped');check('spotless:check clean package' in docker,'Docker runs checked tests')
    package=json.loads((ROOT/'frontend/package.json').read_text());check(package['license']=='SEE LICENSE IN ../LICENSE','package license consistent');check('jsbarcode' in package['dependencies'],'barcode dependency locked')
    subprocess.run(['git','diff','--check'],cwd=ROOT,check=True)
    print(json.dumps({'status':'passed','checks':len(checks),'qr':'original hashes preserved','license':'source-available non-commercial','screenshots':'six or more per language'},ensure_ascii=False))

if __name__=='__main__':main()
