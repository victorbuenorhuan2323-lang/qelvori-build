import os,pathlib
from urllib.parse import urlsplit
url=os.environ["LICENSE_SERVER_URL"].strip().rstrip("/")
p=urlsplit(url)
if p.scheme!="https" or not p.hostname or p.username or p.password or p.path or p.query or p.fragment:
 raise SystemExit("Informe uma URL HTTPS do servidor, sem caminhos ou credenciais.")
src=pathlib.Path("app/src/main/java/com/th/lisoprime/license/KeyGateActivity.java")
s=src.read_text()
assert "https://license.example.invalid" in s
src.write_text(s.replace("https://license.example.invalid",url))
