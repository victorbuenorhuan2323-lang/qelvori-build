"""Qelvoriflix license API. Deploy on HTTPS with persistent DATA_DIR."""
import base64
import hashlib
import hmac
import os
import re
import secrets
import sqlite3
import threading
import time
from contextlib import contextmanager
from pathlib import Path
from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field

BASE=Path(__file__).parent
DATA=Path(os.environ.get("DATA_DIR",str(BASE/"data")))
USER=os.environ.get("ADMIN_USER","admin")
PASSWORD=os.environ.get("ADMIN_PASSWORD","")
SECRET=os.environ.get("SESSION_SECRET","")
PEPPER=os.environ.get("KEY_PEPPER","")
if len(PASSWORD)<12 or len(SECRET)<32 or len(PEPPER)<32:
    raise RuntimeError("Set ADMIN_PASSWORD (12+), SESSION_SECRET (32+), KEY_PEPPER (32+)")
DATA.mkdir(parents=True,exist_ok=True)
DB=DATA/"licenses.sqlite3"
LOCK=threading.RLock()
RATE_LOCK=threading.Lock()
RATE={}
ALPHABET="ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
PATTERN=re.compile(r"^QELV(?:-[A-HJ-NP-Z2-9]{5}){4}$")
DEVICE=re.compile(r"^[A-Za-z0-9_-]{16,128}$")
DAYS={2:14,4:28,8:56}
app=FastAPI(title="Qelvoriflix Key API",docs_url=None,redoc_url=None,openapi_url=None)

@contextmanager
def connection():
    with sqlite3.connect(DB,timeout=20) as db:
        db.row_factory=sqlite3.Row
        db.execute("PRAGMA busy_timeout=20000")
        db.execute("PRAGMA journal_mode=WAL")
        yield db

with LOCK,connection() as db:
    db.execute("""CREATE TABLE IF NOT EXISTS licenses (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      key_hash TEXT NOT NULL UNIQUE,
      hint TEXT NOT NULL,
      note TEXT NOT NULL DEFAULT '',
      weeks INTEGER NOT NULL,
      created_at INTEGER NOT NULL,
      activated_at INTEGER,
      expires_at INTEGER,
      device_id TEXT,
      revoked_at INTEGER,
      last_seen_at INTEGER
    )""")

@app.middleware("http")
async def headers(request,call_next):
    response=await call_next(request)
    response.headers["Cache-Control"]="no-store"
    response.headers["X-Content-Type-Options"]="nosniff"
    return response

def now():
    return int(time.time())

def digest(raw):
    return hmac.new(PEPPER.encode(),raw.strip().upper().encode(),hashlib.sha256).hexdigest()

def state(row):
    if row["revoked_at"] is not None: return "revoked"
    if row["activated_at"] is None: return "unused"
    if row["expires_at"] is not None and row["expires_at"]<=now(): return "expired"
    return "active"

def rejected(status,message,http=403):
    return JSONResponse({"ok":False,"valid":False,"status":status,"message":message},status_code=http)

def limit(ip,category,count,window):
    t=time.monotonic()
    with RATE_LOCK:
        if len(RATE)>2000: RATE.clear()
        name=(ip,category)
        history=[x for x in RATE.get(name,[]) if t-x<window]
        if len(history)>=count:
            RATE[name]=history
            return False
        history.append(t)
        RATE[name]=history
        return True

class Access(BaseModel):
    key:str=Field(min_length=12,max_length=100)
    device_id:str=Field(min_length=16,max_length=128)

class Login(BaseModel):
    username:str=Field(min_length=1,max_length=100)
    password:str=Field(min_length=1,max_length=300)

class CreateKey(BaseModel):
    weeks:int
    note:str=Field(default="",max_length=80)

def new_token():
    msg=f"{USER}:{now()}:{secrets.token_urlsafe(18)}"
    text=base64.urlsafe_b64encode(msg.encode()).decode().rstrip("=")
    sign=hmac.new(SECRET.encode(),text.encode(),hashlib.sha256).hexdigest()
    return text+"."+sign

def valid_token(request):
    auth=request.headers.get("authorization","")
    if not auth.startswith("Bearer "):return False
    try:
        raw,tag=auth[7:].split(".",1)
        expected=hmac.new(SECRET.encode(),raw.encode(),hashlib.sha256).hexdigest()
        if not hmac.compare_digest(tag,expected):return False
        decoded=base64.urlsafe_b64decode(raw+"="*((-len(raw))%4)).decode()
        username,issued,_=decoded.split(":",2)
        start=int(issued)
        return username==USER and start<=now()<start+8*3600
    except (ValueError,UnicodeError,IndexError):return False

def json_row(row):
    st=state(row)
    remaining=max(0,(row["expires_at"] or 0)-now()) if st=="active" else 0
    return {
      "id":row["id"],"hint":row["hint"],"note":row["note"],
      "weeks":row["weeks"],"status":st,"created_at":row["created_at"],
      "activated_at":row["activated_at"],"expires_at":row["expires_at"],
      "last_seen_at":row["last_seen_at"],"device_bound":row["device_id"] is not None,
      "remaining_seconds":remaining
    }

@app.get("/health")
def health():
    return {"ok":True}

def validate_license(request,data,activate):
    ip=request.client.host if request.client else "unknown"
    if not limit(ip,"activate" if activate else "verify",80 if activate else 3000,300):
        return rejected("rate_limited","Muitas tentativas",429)
    key=data.key.strip().upper()
    if not PATTERN.fullmatch(key) or not DEVICE.fullmatch(data.device_id):
        return rejected("invalid_key","Key ou dispositivo inválido")
    with LOCK,connection() as db:
        row=db.execute("SELECT * FROM licenses WHERE key_hash=?",(digest(key),)).fetchone()
        if row is None:return rejected("invalid_key","Key não encontrada")
        st=state(row)
        if st=="revoked":return rejected("revoked","A Key foi revogada")
        if st=="expired":return rejected("expired","A Key expirou")
        current=now()
        if row["activated_at"] is None:
            if not activate:return rejected("not_activated","Ative a Key primeiro")
            expires=current+DAYS[row["weeks"]]*86400
            db.execute("UPDATE licenses SET activated_at=?,expires_at=?,device_id=?,last_seen_at=? WHERE id=?",
                       (current,expires,data.device_id,current,row["id"]))
        elif row["device_id"] and not hmac.compare_digest(row["device_id"],data.device_id):
            return rejected("device_mismatch","Key vinculada a outro aparelho")
        else:
            if row["device_id"] is None:
                if not activate:return rejected("device_mismatch","Reative sua Key neste aparelho")
                db.execute("UPDATE licenses SET device_id=? WHERE id=?",(data.device_id,row["id"]))
            db.execute("UPDATE licenses SET last_seen_at=? WHERE id=?",(current,row["id"]))
        row=db.execute("SELECT * FROM licenses WHERE id=?",(row["id"],)).fetchone()
        return {"ok":True,"valid":True,"status":"active","expires_at":row["expires_at"],
                "remaining_seconds":max(0,row["expires_at"]-current),"next_check_seconds":30}

@app.post("/api/v1/activate")
def activate(request:Request,data:Access):
    return validate_license(request,data,True)

@app.post("/api/v1/verify")
def verify(request:Request,data:Access):
    return validate_license(request,data,False)

@app.post("/api/admin/login")
def admin_login(request:Request,data:Login):
    ip=request.client.host if request.client else "unknown"
    if not limit(ip,"admin-login",12,600):return rejected("rate_limited","Muitas tentativas",429)
    if not (hmac.compare_digest(data.username,USER) and hmac.compare_digest(data.password,PASSWORD)):
        return rejected("unauthorized","Usuário ou senha incorretos",401)
    return {"ok":True,"token":new_token(),"expires_in":28800}

@app.get("/api/admin/keys")
def admin_list(request:Request):
    if not valid_token(request):return rejected("unauthorized","Faça login novamente",401)
    with connection() as db:
        rows=db.execute("SELECT * FROM licenses ORDER BY id DESC LIMIT 500").fetchall()
        total=db.execute("SELECT COUNT(*) FROM licenses").fetchone()[0]
    return {"ok":True,"keys":[json_row(row) for row in rows],
            "total":total,"server_time":now(),"next_check_seconds":30}

@app.post("/api/admin/keys")
def admin_create(request:Request,data:CreateKey):
    if not valid_token(request):return rejected("unauthorized","Faça login novamente",401)
    if data.weeks not in DAYS:return rejected("invalid_plan","Escolha 2, 4 ou 8 semanas",400)
    with LOCK,connection() as db:
        for _ in range(8):
            key="QELV-"+"-".join("".join(secrets.choice(ALPHABET) for _ in range(5)) for _ in range(4))
            try:
                db.execute("INSERT INTO licenses(key_hash,hint,note,weeks,created_at) VALUES(?,?,?,?,?)",
                           (digest(key),"QELV-••••-••••-••••-"+key[-5:],data.note.strip(),data.weeks,now()))
                row=db.execute("SELECT * FROM licenses WHERE key_hash=?",(digest(key),)).fetchone()
                return {"ok":True,"key":key,"license":json_row(row)}
            except sqlite3.IntegrityError:continue
    return rejected("try_again","Não foi possível gerar Key",503)

@app.post("/api/admin/keys/{row_id}/{action}")
def admin_modify(request:Request,row_id:int,action:str):
    if not valid_token(request):return rejected("unauthorized","Faça login novamente",401)
    if action not in {"revoke","restore","reset-device"}:
        return rejected("invalid_action","Ação inválida",400)
    with LOCK,connection() as db:
        row=db.execute("SELECT * FROM licenses WHERE id=?",(row_id,)).fetchone()
        if row is None:return rejected("not_found","Key não encontrada",404)
        if action=="revoke":
            db.execute("UPDATE licenses SET revoked_at=? WHERE id=?",(now(),row_id))
        elif action=="restore":
            db.execute("UPDATE licenses SET revoked_at=NULL WHERE id=?",(row_id,))
        else:
            db.execute("UPDATE licenses SET device_id=NULL WHERE id=?",(row_id,))
        row=db.execute("SELECT * FROM licenses WHERE id=?",(row_id,)).fetchone()
        return {"ok":True,"license":json_row(row)}

@app.delete("/api/admin/keys/{row_id}")
def admin_delete_key(request:Request,row_id:int):
    """Exclusão permanente da licença, protegida pelo token de administrador.

    Após a exclusão, a validação da Key responderá invalid_key.
    """
    if not valid_token(request):
        return rejected("unauthorized","Faça login novamente",401)
    with LOCK, connection() as db:
        row=db.execute("SELECT id,hint FROM licenses WHERE id=?",(row_id,)).fetchone()
        if row is None:
            return rejected("not_found","Key não encontrada",404)
        db.execute("DELETE FROM licenses WHERE id=?",(row_id,))
        return {"ok":True,"deleted":True,"id":row_id,"hint":row["hint"]}
