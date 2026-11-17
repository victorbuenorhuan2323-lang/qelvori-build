import concurrent.futures
import pathlib
import urllib.request
import zipfile

tools = pathlib.Path(__file__).resolve().parent / "tools"
tools.mkdir(exist_ok=True)

downloads = {
    "platform.zip": "https://dl.google.com/android/repository/platform-35_r02.zip",
    "buildtools.zip": "https://dl.google.com/android/repository/build-tools_r35_linux.zip",
    "ecj.jar": "https://repo.maven.apache.org/maven2/org/eclipse/jdt/ecj/3.39.0/ecj-3.39.0.jar",
}

def download(item):
    name, url = item
    destination = tools / name
    print(f"Baixando {name}", flush=True)
    urllib.request.urlretrieve(url, destination)
    if name.endswith(".zip"):
        with zipfile.ZipFile(destination) as archive:
            archive.extractall(tools / "sdk")
        destination.unlink()

with concurrent.futures.ThreadPoolExecutor(max_workers=3) as executor:
    list(executor.map(download, downloads.items()))

for path in (tools / "sdk").rglob("*"):
    if path.is_file() and path.name in {"aapt2", "zipalign", "aapt", "aidl", "d8", "apksigner"}:
        path.chmod(0o755)

print("Compilador preparado.")
