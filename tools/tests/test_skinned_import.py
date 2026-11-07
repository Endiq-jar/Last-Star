#!/usr/bin/env python3
import json
import os
import subprocess
import sys
import tempfile

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SCRIPT = os.path.join(ROOT, "tools", "import_skinned.py")
IDENTITY = [1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1]


def asset():
    return {
        "format": "last-star-skinned", "version": 1,
        "bones": [{"name": "root", "parent": -1, "inverse_bind": IDENTITY}],
        "vertices": [
            {"position": [0, 0, 0], "normal": [0, 1, 0],
             "influences": [[0, 0.25], [0, 0.75]]},
            {"position": [1, 0, 0], "normal": [0, 1, 0],
             "influences": [[0, 1.0]]},
            {"position": [0, 0, 1], "normal": [0, 1, 0],
             "influences": [[0, 1.0]]},
        ],
        "indices": [0, 1, 2],
        "clips": [{"name": "idle", "duration": 1.0, "frames": [
            {"time": 0.0, "local_matrices": [IDENTITY]},
            {"time": 1.0, "local_matrices": [IDENTITY]},
        ]}],
    }


def main():
    with tempfile.TemporaryDirectory() as temp:
        source = os.path.join(temp, "source.json")
        output = os.path.join(temp, "actor.lsskin")
        with open(source, "w", encoding="utf-8") as f:
            json.dump(asset(), f)
        result = subprocess.run([sys.executable, SCRIPT, source, output],
                                text=True, capture_output=True)
        assert result.returncode == 0, result.stderr
        text = open(output, encoding="utf-8").read()
        assert text.startswith("LSSKIN 1\nbones 1\n")
        assert "vertices 3\n" in text and "indices 3\n" in text and "clip idle 1.0 2\n" in text
        # The compiler preserves the normalized weights and emits four slots.
        vertex_line = text.split("vertices 3\n", 1)[1].splitlines()[0].split()
        assert len(vertex_line) == 18
        assert abs(float(vertex_line[11]) - 0.25) < 1e-6
        assert abs(float(vertex_line[13]) - 0.75) < 1e-6

        bad = asset()
        bad["vertices"][0]["influences"] = [[3, 1.0]]
        with open(source, "w", encoding="utf-8") as f:
            json.dump(bad, f)
        result = subprocess.run([sys.executable, SCRIPT, source, output],
                                text=True, capture_output=True)
        assert result.returncode != 0 and "invalid joint" in result.stderr
    print("skinned import\n  ok   JSON to LSSKIN conversion\n  ok   normalized 4-slot weights\n  ok   invalid joints rejected\n\nchecks: 3   failures: 0\nRESULT: PASS")


if __name__ == "__main__":
    main()
