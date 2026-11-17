import os
import re
import subprocess
import zipfile
from pathlib import Path, PurePosixPath
from xml.sax.saxutils import quoteattr

TOOLS = Path(__file__).resolve().parent / "tools"
MAIN = "java/com/generated/app/MainActivity.java"


def validate(files):
    if not isinstance(files, list) or not 1 <= len(files) <= 80:
        raise ValueError("O projeto precisa ter entre 1 e 80 arquivos.")

    result = []
    seen = set()
    total = 0
    for entry in files:
        path = entry["path"]
        content = entry["content"]
        if not isinstance(path, str) or not isinstance(content, str):
            raise ValueError("Arquivo inválido.")

        relative = PurePosixPath(path)
        if relative.is_absolute() or ".." in relative.parts or "\\" in path:
            raise ValueError("Caminho inválido.")
        if str(relative) != path or path in seen:
            raise ValueError("Caminho inválido ou duplicado.")
        if not (
            (path.startswith("java/") and path.endswith(".java"))
            or path.startswith(("res/", "assets/"))
        ):
            raise ValueError("Use arquivos em java/, res/ ou assets/.")

        seen.add(path)
        total += len(content.encode("utf-8"))
        if total > 1_500_000:
            raise ValueError("Projeto acima de 1,5 MB.")

        result.append({"path": path, "content": content})

    if MAIN not in seen:
        raise ValueError("O projeto precisa de MainActivity.java.")
    return result


def run(*args, env=None):
    subprocess.run([str(arg) for arg in args], check=True, timeout=100, env=env)


def compile_apk(folder, files, name, package):
    sdk = TOOLS / "sdk"
    build_tools = sdk / "android-15"
    android_jar = next(sdk.glob("*/android.jar"))
    compiler = TOOLS / "ecj.jar"

    folder = Path(folder).resolve()
    source = folder / "src"
    generated = folder / "generated"
    classes = folder / "classes"
    dex = folder / "dex"
    for directory in (source, generated, classes, dex):
        directory.mkdir(parents=True, exist_ok=True)

    for entry in validate(files):
        destination = source / entry["path"]
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_text(entry["content"], encoding="utf-8")

    uses_camera = any(
        "android.hardware.camera2" in entry["content"]
        or re.search(r"\bCameraManager\b|\bCameraDevice\b|\bManifest\.permission\.CAMERA\b", entry["content"])
        or "android.permission.CAMERA" in entry["content"]
        for entry in files if entry["path"].endswith(".java")
    )
    camera_manifest = (
        '<uses-permission android:name="android.permission.CAMERA"/>'
        '<uses-feature android:name="android.hardware.camera.any" android:required="false"/>'
        if uses_camera else ""
    )
    manifest = source / "AndroidManifest.xml"
    manifest.write_text(
        f"""<manifest xmlns:android="http://schemas.android.com/apk/res/android"
 package={quoteattr(package)} android:versionCode="1" android:versionName="1.0">
 <uses-sdk android:minSdkVersion="26" android:targetSdkVersion="35"/>
 <uses-permission android:name="android.permission.INTERNET"/>
 {camera_manifest}
 <application android:label={quoteattr(name)}
  android:theme="@android:style/Theme.Material.NoActionBar"
  android:allowBackup="false" android:usesCleartextTraffic="false">
  <activity android:name=".MainActivity" android:exported="true">
   <intent-filter>
    <action android:name="android.intent.action.MAIN"/>
    <category android:name="android.intent.category.LAUNCHER"/>
   </intent-filter>
  </activity>
 </application>
</manifest>""",
        encoding="utf-8",
    )

    base = folder / "base.apk"
    aapt = build_tools / "aapt2"
    link = [
        aapt, "link", "-o", base, "-I", android_jar, "--manifest", manifest,
        "--java", generated, "--auto-add-overlay",
    ]
    if (source / "res").is_dir():
        resources = folder / "resources.zip"
        run(aapt, "compile", "--dir", source / "res", "-o", resources)
        link.append(resources)
    if (source / "assets").is_dir():
        link.extend(["-A", source / "assets"])
    run(*link)

    java_sources = list((source / "java").rglob("*.java"))
    java_sources.extend(generated.rglob("*.java"))
    run(
        "java", "-jar", compiler, "-8", "-proc:none", "-classpath",
        android_jar, "-d", classes, *java_sources,
    )
    run(
        "java", "-cp", build_tools / "lib/d8.jar", "com.android.tools.r8.D8",
        "--min-api", "26", "--lib", android_jar, "--output", dex,
        *classes.rglob("*.class"),
    )

    with zipfile.ZipFile(base, "a", zipfile.ZIP_DEFLATED) as archive:
        for file in dex.glob("*.dex"):
            archive.write(file, file.name)

    aligned = folder / "aligned.apk"
    apk = folder / "app.apk"
    keystore = folder / "test-key.p12"
    env = dict(os.environ, QELVORI_SIGN_PASS="qelvori-test-build")
    run(build_tools / "zipalign", "-f", "4", base, aligned)
    run(
        "keytool", "-genkeypair", "-keystore", keystore, "-storetype", "PKCS12",
        "-storepass:env", "QELVORI_SIGN_PASS", "-alias", "builder",
        "-keyalg", "RSA", "-keysize", "2048", "-validity", "10000",
        "-dname", "CN=Qelvori Test Build", env=env,
    )

    signer = build_tools / "lib/apksigner.jar"
    run(
        "java", "-jar", signer, "sign", "--ks", keystore,
        "--ks-pass", "env:QELVORI_SIGN_PASS", "--out", apk, aligned, env=env,
    )
    run("java", "-jar", signer, "verify", apk)
    return apk
