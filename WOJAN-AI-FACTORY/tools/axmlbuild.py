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
  <uses-permission android:name="android.permission.INTERNET"/>
  <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE"/>
  <application
      android:label="{label}"
      android:allowBackup="true"
      android:usesCleartextTraffic="true">
{activities}
  </application>
</manifest>
"""

ACTIVITY_TEMPLATE = """    <activity android:name="{name}"{exported}{parent}>
{intent}
    </activity>"""


def build_manifest(package, activities, label="WOJAN AI FACTORY", version_code=4,
                   version_name="1.0.3", min_sdk=21, target_sdk=34):
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
                                   activities="\n".join(acts))
    return xml


def xml_to_axml(xml_text: str) -> bytes:
    from pyaxml import AXML
    a = AXML()
    a.from_xml(xml_text)
    a.compute()
    return a.pack()


def main():
    # szybkie użycie: python3 axmlbuild.py out.axml  (manifest z parametrów CL)
    out = sys.argv[1]
    package = sys.argv[2] if len(sys.argv) > 2 else "pl.wojan.factory"
    acts = sys.argv[3:]
    activities = [{"name": n} for n in acts] or [{"name": package + ".MainActivity"}]
    xml = build_manifest(package, activities)
    data = xml_to_axml(xml)
    open(out, "wb").write(data)
    print(f"{out}: {len(data)} B AXML")
    return 0


if __name__ == "__main__":
    sys.exit(main())
