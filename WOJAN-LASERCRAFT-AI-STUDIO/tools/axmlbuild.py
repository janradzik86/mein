#!/usr/bin/env python3
"""axmlbuild.py — generuje binarny AndroidManifest.xml (AXML) z XML-a.

Używa biblioteki pyaxml (Rust) — działa w środowisku budowania bez aapt2.
Wejście: zwykły manifest XML; wyjście: binarny AXML gotowy do APK.
"""

import sys


MANIFEST_TEMPLATE = """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="{package}"
    android:versionCode="{version_code}"
    android:versionName="{version_name}">
  <uses-sdk android:minSdkVersion="{min_sdk}" android:targetSdkVersion="{target_sdk}"/>
{permissions}  <application
      android:label="{label}"
      android:allowBackup="true"
      android:usesCleartextTraffic="true"{theme_attr}>
{activities}
  </application>
</manifest>
"""

ACTIVITY_TEMPLATE = """    <activity android:name="{name}"{exported}{parent}>
{intent}
    </activity>"""


DEFAULT_PERMISSIONS = ["android.permission.INTERNET"]


def build_manifest(package, activities, label="WOJAN AI FACTORY", version_code=3,
                   version_name="1.0.2", min_sdk=21, target_sdk=34,
                   permissions=None, theme=""):
    perms = DEFAULT_PERMISSIONS if permissions is None else permissions
    theme_attr = f'\n      android:theme="{theme}"' if theme else ""
    perm_xml = "".join(
        f'  <uses-permission android:name="{p}"/>\n' for p in perms)
    acts = []
    for i, act in enumerate(activities):
        exported = ' android:exported="true"' if i == 0 else ' android:exported="false"'
        parent = ""
        if act.get("parent"):
            parent = f' android:theme="@android:style/Theme.DeviceDefault"'
        intent = ""
        if i == 0:
            intent = """      <intent-filter>
        <action android:name="android.intent.action.MAIN"/>
        <category android:name="android.intent.category.LAUNCHER"/>
      </intent-filter>"""
        acts.append(ACTIVITY_TEMPLATE.format(name=act["name"], exported=exported,
                                             parent=parent, intent=intent))
    xml = MANIFEST_TEMPLATE.format(package=package, version_code=version_code,
                                   version_name=version_name, min_sdk=min_sdk,
                                   target_sdk=target_sdk, label=label,
                                   permissions=perm_xml, theme_attr=theme_attr,
                                   activities="\n".join(acts))
    return xml


def xml_to_axml(xml_text: str) -> bytes:
    from pyaxml import AXML
    a = AXML()
    a.from_xml(xml_text)
    a.compute()
    return a.pack()


def main():
    import argparse
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", required=True)
    ap.add_argument("--package", default="pl.wojan.lasercraft")
    ap.add_argument("--label", default="WOJAN LaserCraft AI")
    ap.add_argument("--version-code", type=int, default=1)
    ap.add_argument("--version-name", default="1.0.0")
    ap.add_argument("--min-sdk", type=int, default=24)
    ap.add_argument("--target-sdk", type=int, default=36)
    ap.add_argument("--theme", default="")
    ap.add_argument("--permission", action="append", default=[])
    ap.add_argument("--activity", action="append", default=[])
    ap.add_argument("--print-xml", action="store_true")
    args = ap.parse_args()
    activities = [{"name": n} for n in args.activity] or [
        {"name": args.package + ".MainActivity"}]
    xml = build_manifest(args.package, activities, label=args.label,
                         version_code=args.version_code, version_name=args.version_name,
                         min_sdk=args.min_sdk, target_sdk=args.target_sdk,
                         permissions=args.permission or DEFAULT_PERMISSIONS,
                         theme=args.theme)
    if args.print_xml:
        print(xml)
        return 0
    data = xml_to_axml(xml)
    with open(args.out, "wb") as fh:
        fh.write(data)
    print(f"{args.out}: {len(data)} B AXML")
    return 0


if __name__ == "__main__":
    sys.exit(main())
