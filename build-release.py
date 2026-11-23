import argparse
import json
import os
import pathlib
import shutil
import subprocess

root = pathlib.Path(__file__).resolve().parent
parser = argparse.ArgumentParser()
parser.add_argument("versions", nargs="*")
args = parser.parse_args()
matrix = json.loads((root / "ports/versions.json").read_text())
output = root / "release/1.0"
output.mkdir(parents=True, exist_ok=True)
for mc in args.versions or matrix:
    project = root / "ports" / ("fabric" if mc.startswith("26.") else "fabric-legacy")
    log = root / f"qa-build-{mc}.log"
    wrapper = [str(root / "gradlew.bat")] if os.name == "nt" else ["sh", str(root / "gradlew")]
    command = wrapper + [ "-p", str(project), f"-Pmc={mc}", f"-Papi={matrix[mc]['fabricApi']}", "build", "neoJar", "--console=plain"]
    if mc.startswith("26."):
        command.append("forgeJar")
    with log.open("w", encoding="utf-8") as stream:
        result = subprocess.run(command, stdout=stream, stderr=subprocess.STDOUT, env=os.environ)
    if result.returncode:
        print(f"{mc}: failed, see {log.name}", flush=True)
        continue
    if mc == "26.3":
        with log.open("a", encoding="utf-8") as stream:
            result = subprocess.run(wrapper + ["-p", str(root), "build", "--console=plain"], stdout=stream, stderr=subprocess.STDOUT, env=os.environ)
        if result.returncode:
            print("26.3: main Fabric build failed", flush=True)
            continue
    for loader in ["fabric", "neoforge"] + (["forge"] if mc.startswith("26.") else []):
        source = project / "build/libs" / f"simply-journal-{mc}-{loader}-1.0.jar"
        if mc == "26.3" and loader == "fabric":
            source = root / "build/libs/simply-journal-1.0.jar"
        destination = output / ("unverified-forge" if loader == "forge" else "")
        destination.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source, destination / f"simply-journal-{mc}-{loader}-1.0.jar")
    print(f"{mc}: built", flush=True)
