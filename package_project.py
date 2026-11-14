#!/usr/bin/env python3
"""Package the CodeLearn Duo project (including dotfiles) into CodeLearnDuo.zip."""
import os
import zipfile

ROOT = os.path.dirname(os.path.abspath(__file__))
OUTPUT = os.path.join(ROOT, "CodeLearnDuo.zip")
SKIP_DIRS = {".git", "build", ".gradle", ".idea", "__pycache__"}
SKIP_FILES = {"local.properties", "CodeLearnDuo.zip", ".DS_Store"}


def main():
    count = 0
    with zipfile.ZipFile(OUTPUT, "w", zipfile.ZIP_DEFLATED) as zf:
        for current, dirs, files in os.walk(ROOT):
            dirs[:] = sorted(d for d in dirs if d not in SKIP_DIRS)
            for name in sorted(files):
                if name in SKIP_FILES:
                    continue
                full = os.path.join(current, name)
                rel = os.path.relpath(full, ROOT).replace(os.sep, "/")
                zf.write(full, "CodeLearnDuo/" + rel)
                count += 1
    print("Packaged {} files into {}".format(count, OUTPUT))


if __name__ == "__main__":
    main()
