#!/usr/bin/env python3
"""Extract Unix ZIPs without losing executable bits or symbolic links."""
import argparse
import os
import shutil
import stat
import zipfile


def restore_link(destination: str, member: zipfile.ZipInfo, archive: zipfile.ZipFile) -> None:
    path = os.path.join(destination, member.filename)
    target = archive.read(member).decode("utf-8")
    if os.path.lexists(path):
        if os.path.isdir(path) and not os.path.islink(path):
            shutil.rmtree(path)
        else:
            os.unlink(path)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    os.symlink(target, path)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("archive")
    parser.add_argument("destination")
    parser.add_argument("--repair-symlinks", action="store_true")
    args = parser.parse_args()
    os.makedirs(args.destination, exist_ok=True)
    with zipfile.ZipFile(args.archive) as archive:
        members = archive.infolist()
        if not args.repair_symlinks:
            archive.extractall(args.destination)
        for member in members:
            mode = member.external_attr >> 16
            path = os.path.join(args.destination, member.filename)
            if stat.S_ISLNK(mode):
                restore_link(args.destination, member, archive)
            elif not args.repair_symlinks and mode and os.path.exists(path):
                os.chmod(path, mode & 0o777)


if __name__ == "__main__":
    main()
