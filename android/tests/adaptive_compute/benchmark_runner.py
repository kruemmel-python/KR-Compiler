#!/usr/bin/env python3
"""Summarize real Adaptive Compute JSONL measurements; never invents energy data."""
import argparse, json, math, pathlib, statistics

FIELDS = ("initMs", "hostToDeviceMs", "kernelMs", "deviceToHostMs", "totalMs")

def load(path):
    rows = []
    for number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        if not line.strip():
            continue
        try:
            row = json.loads(line)
        except json.JSONDecodeError as exc:
            raise SystemExit(f"Ungültiges JSON in Zeile {number}: {exc}")
        if row.get("schema") != 1 or "operation" not in row or "backend" not in row:
            raise SystemExit(f"Unbekanntes Telemetrieformat in Zeile {number}")
        rows.append(row)
    return rows

def stats(values):
    return {"count": len(values), "min": min(values), "median": statistics.median(values),
            "mean": statistics.fmean(values), "max": max(values),
            "stdev": statistics.stdev(values) if len(values) > 1 else 0.0}

def summarize(rows):
    result = {"schema": 1, "measurements": len(rows), "groups": [],
              "energy": {"status": "NOT_MEASURED", "reason": "Keine Joule-Messwerte in der Telemetrie."}}
    groups = {}
    for row in rows:
        groups.setdefault((row["operation"], row["backend"]), []).append(row)
    for (operation, backend), group in sorted(groups.items()):
        entry = {"operation": operation, "backend": backend, "cold": {}, "warm": {},
                 "validationFailures": sum(1 for r in group if r.get("validated") and not r.get("validationOk")),
                 "fallbacks": sum(1 for r in group if "FALLBACK" in str(r.get("decision", "")))}
        for field in FIELDS:
            values = [float(r.get(field, 0.0)) for r in group]
            entry["cold"][field] = values[0]
            if len(values) > 1:
                entry["warm"][field] = stats(values[1:])
        result["groups"].append(entry)
    energies = [float(r["energyJoules"]) for r in rows if isinstance(r.get("energyJoules"), (int, float)) and math.isfinite(float(r["energyJoules"]))]
    if energies:
        result["energy"] = {"status": "MEASURED", "joules": stats(energies),
                            "warning": "Nur gültig mit dokumentierter externer oder Plattform-Messquelle."}
    return result

def main():
    p = argparse.ArgumentParser()
    p.add_argument("telemetry", type=pathlib.Path)
    p.add_argument("--output", type=pathlib.Path)
    args = p.parse_args()
    result = summarize(load(args.telemetry))
    text = json.dumps(result, ensure_ascii=False, indent=2) + "\n"
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(text, encoding="utf-8")
    else:
        print(text, end="")

if __name__ == "__main__": main()
