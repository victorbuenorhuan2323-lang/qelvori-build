#!/usr/bin/env python3
"""Experimental binary AndroidManifest launcher patch, no apktool required."""
import sys,zipfile,struct
u16=lambda b,i:struct.unpack_from('<H',b,i)[0]
u32=lambda b,i:struct.unpack_from('<I',b,i)[0]
p16=lambda x:struct.pack('<H',x)
p32=lambda x:struct.pack('<I',x)
def patch(b):
 assert u16(b,0)==3,'Unexpected Android manifest format'
 p=8
 assert u16(b,p)==1,'Missing XML string pool'
 size=u32(b,p+4);count=u32(b,p+8)
 assert u32(b,p+12)==0 and u32(b,p+16)==0,'Unsupported stringpool'
 start=u32(b,p+20)
 strings=[]
 for i in range(count):
  at=p+start+u32(b,p+28+4*i)
  n=u16(b,at);at+=2
  if n&0x8000:n=((n&0x7fff)<<16)|u16(b,at);at+=2
  strings.append(b[at:at+2*n].decode('utf-16le'))
 original='com.th.lisoprime.MainActivity'
 replacement='com.th.lisoprime.license.KeyGateActivity'
 assert original in strings
 pool=b[p:p+size];data=pool[start:]
 appendix=p16(len(replacement))+replacement.encode('utf-16le')+b'\x00\x00'
 newdata=data+appendix
 newdata+=b'\x00'*((-len(newdata))%4)
 newpool=bytearray(pool[:28]+pool[28:28+count*4]+p32(len(data))+newdata)
 struct.pack_into('<I',newpool,4,len(newpool))
 struct.pack_into('<I',newpool,8,count+1)
 struct.pack_into('<I',newpool,20,start+4)
 pos=8+size;depth=0;origdepth=None;begin=None;end=None;chunks=[]
 while pos<len(b):
  t=u16(b,pos);z=u32(b,pos+4)
  if z<=0:raise ValueError('Malformed chunk')
  if t==0x0102:
   if strings[u32(b,pos+20)]=='activity':
    for j in range(u16(b,pos+28)):
     a=pos+36+20*j
     if strings[u32(b,a+4)]=='name':
      idx=u32(b,a+16)
      if idx<len(strings) and strings[idx]==original:
       begin=pos;origdepth=depth
   depth+=1
  elif t==0x0103:
   depth-=1
   if begin is not None and end is None and depth==origdepth:end=pos
  chunks.append((pos,t,b[pos:pos+z]))
  pos+=z
 if begin is None or end is None:raise ValueError('Launcher not found')
 closing=next(x for q,t,x in chunks if q==end)
 output=bytearray(b[:8]+newpool)
 for q,t,chunk in chunks:
  if q!=begin:output+=chunk;continue
  native=bytearray(chunk);gate=bytearray(chunk)
  for j in range(u16(chunk,28)):
   a=36+20*j;attr=strings[u32(chunk,a+4)]
   if attr=='name':
    struct.pack_into('<I',gate,a+16,count)
    if u32(gate,a+8)!=0xffffffff:struct.pack_into('<I',gate,a+8,count)
   if attr=='exported':struct.pack_into('<I',native,a+16,0)
  output+=native+closing+gate
 struct.pack_into('<I',output,4,len(output))
 return bytes(output)
def main(src,dex,dest):
 with zipfile.ZipFile(src) as inp,zipfile.ZipFile(dest,'w') as out:
  assert 'classes2.dex' not in inp.namelist()
  for obj in inp.infolist():
   name=obj.filename
   if name.startswith('META-INF/') and (name.endswith(('.SF','.RSA','.DSA','.EC')) or name=='META-INF/MANIFEST.MF'):continue
   content=inp.read(obj)
   if name=='AndroidManifest.xml':content=patch(content)
   out.writestr(obj,content)
  out.writestr('classes2.dex',open(dex,'rb').read(),compress_type=zipfile.ZIP_STORED)
if __name__=='__main__':
 if len(sys.argv)!=4:raise SystemExit('Usage: patch_axml.py original.apk keygate.dex modified.apk')
 main(*sys.argv[1:])
