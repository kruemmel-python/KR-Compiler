#!/usr/bin/env python3
"""Runs only locally available matrix rows and records explicit PASS/FAIL/SKIPPED states."""
import argparse, datetime, json, os, pathlib, platform, shutil, subprocess

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--manifest", type=pathlib.Path, default=pathlib.Path(__file__).with_name("regression_matrix.json"))
    ap.add_argument("--output", type=pathlib.Path, required=True)
    args = ap.parse_args()
    manifest = json.loads(args.manifest.read_text(encoding="utf-8"))
    rows = []
    for item in manifest["rows"]:
        reason = None
        if item.get("os") and item["os"].lower() not in platform.system().lower():
            reason = "Betriebssystem nicht aktiv"
        if item.get("requires") and not shutil.which(item["requires"]):
            reason = f"Werkzeug/Gerät fehlt: {item['requires']}"
        if reason:
            rows.append({**item, "status": "SKIPPED", "reason": reason})
            continue
        env = os.environ.copy()
        configured_workspace = pathlib.Path(manifest.get("workspace", "."))
        workspace = configured_workspace.resolve() if configured_workspace.exists() else pathlib.Path.cwd().resolve()
        env["TEMP"] = env["TMP"] = str((args.output.parent / "tmp").resolve())
        env["GRADLE_USER_HOME"] = str(workspace / ".gradle-user")
        pathlib.Path(env["TEMP"]).mkdir(parents=True, exist_ok=True)
        run = subprocess.run(item["command"], cwd=workspace, env=env,
                             text=True, capture_output=True, timeout=item.get("timeoutSeconds", 900))
        rows.append({**item, "status": "PASS" if run.returncode == 0 else "FAIL",
                     "exitCode": run.returncode, "stdout": run.stdout[-8000:], "stderr": run.stderr[-8000:]})
    report = {"schema": 1, "createdUtc": datetime.datetime.now(datetime.timezone.utc).isoformat(),
              "host": {"os": platform.platform(), "machine": platform.machine()}, "rows": rows}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    raise SystemExit(1 if any(r["status"] == "FAIL" for r in rows) else 0)

if __name__ == "__main__": main()
