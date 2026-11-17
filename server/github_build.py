import base64
import gzip
import io
import json
import os
import re
from pathlib import Path

from server import compile_apk, validate

with open(os.environ["GITHUB_EVENT_PATH"], encoding="utf-8") as event:
    encoded = json.load(event)["inputs"]["project"]

if len(encoded) > 64000:
    raise ValueError("Projeto compactado acima do limite.")

compressed = base64.b64decode(encoded, validate=True)
with gzip.GzipFile(fileobj=io.BytesIO(compressed)) as archive:
    content = archive.read(1_600_001)

if len(content) > 1_600_000:
    raise ValueError("Projeto descompactado acima do limite.")

project = json.loads(content)
project_id = project["id"]

if not re.fullmatch(r"[0-9a-f]{32}", project_id):
    raise ValueError("Identificador de projeto inválido.")
if not project["files"]:
    raise ValueError("Projeto sem código.")

package = "com.qelvori.generated.p" + project_id[:20]
files = validate(project["files"])

for entry in files:
    if entry["path"].startswith("java/"):
        entry["content"] = entry["content"].replace("com.generated.app", package)

compile_apk(
    Path("github-output").resolve(),
    files,
    str(project["name"])[:70],
    package,
)
