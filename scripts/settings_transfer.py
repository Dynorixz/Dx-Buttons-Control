"""Transfer explicit JSON files with debug run-as; no accessibility settings are modified."""
import argparse
import pathlib
import subprocess
import json

PACKAGE = "com.dynorixz.buttonscontrol"

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("mode", choices=["export", "import"])
    parser.add_argument("file", type=pathlib.Path)
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--serial")
    args = parser.parse_args()
    adb = [args.adb] + (["-s", args.serial] if args.serial else [])
    if args.mode == "export":
        data = subprocess.run(adb + ["exec-out", "run-as", PACKAGE, "cat", "files/export/dx-buttons-settings.json"], check=True, capture_output=True).stdout
        json.loads(data.decode("utf-8"))
        args.file.write_bytes(data)
        print("Exported. Create the local JSON in the app before running this command.")
    else:
        data = args.file.read_bytes()
        if len(data) > 131072:
            raise ValueError("File too large")
        json.loads(data.decode("utf-8-sig"))
        # The command is fixed, not composed from file paths or JSON. Data travels only on stdin.
        subprocess.run(adb + ["shell", "-T", "run-as", PACKAGE, "sh", "-c", "'mkdir -p files/import && cat > files/import/dx-buttons-settings.json'"], input=data, check=True)
        print("Transferred. Press Import from local file in the app to validate and apply.")

if __name__ == "__main__":
    main()
