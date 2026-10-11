#!/usr/bin/env python3
"""Private-source APK interoperability patch for Joolin2.
Input: apktool-decoded user-supplied Joolink APK. The APK is never committed.
This changes app ID/label and adds a video menu to CameraPlayerActivity.
"""
import argparse
import pathlib
import re
import xml.etree.ElementTree as ET

OLD="com.jooan.qiaoanzhilian.gp"
NEW="ee.joolin2.decoder"
CAMERA="com/jooan/qiaoanzhilian/ui/activity/play/CameraPlayerActivity.smali"
HELPER="Lcom/jooan/qiaoanzhilian/ui/activity/play/VideoSettings;"
ANDROID="{http://schemas.android.com/apk/res/android}"

def hook_method(text,name,signature,position,hook,required=True):
    pattern=re.compile(r'(?ms)^\.method[^\n]*\b'+re.escape(name)+
                       r'\('+signature+r'\)[^\n]*\n.*?^\.end method\s*$')
    matches=list(pattern.finditer(text))
    if not matches:
        if required:raise ValueError(name+": compatible method not found")
        return text,False
    if len(matches)!=1:
        raise ValueError(name+": ambiguous overloaded methods, "+str(len(matches)))
    m=matches[0]
    current=m.group()
    if hook in current:return text,True
    if position=="entry":
        # Place invocation after .locals / .registers; p0 is valid here.
        register=re.search(r'(?m)^\s*\.(?:locals|registers)\s+\d+\s*$',current)
        if not register:raise ValueError(name+": .locals/.registers not found")
        modified=current[:register.end()]+"\n    "+hook+current[register.end():]
    elif position=="return":
        count=0
        lines=[]
        for line in current.splitlines():
            if re.fullmatch(r'\s*return-void\s*',line):
                lines.append("    "+hook)
                count+=1
            lines.append(line)
        if not count:raise ValueError(name+": return-void not found")
        modified="\n".join(lines)
    else:raise ValueError(position)
    return text[:m.start()]+modified+text[m.end():],True

def qualified(name):
    if not name:return name
    if name.startswith("."):return OLD+name
    if "." not in name:return OLD+"."+name
    return name

def patch_manifest(file):
    # Use APKTool's decoded text manifest, not direct binary XML edits.
    ET.register_namespace("android","http://schemas.android.com/apk/res/android")
    tree=ET.parse(file)
    root=tree.getroot()
    actual=root.attrib.get("package")
    if actual!=OLD:raise ValueError("Source APK package mismatch: "+str(actual))
    # Prequalify relative component class names before changing the application ID.
    for item in root.iter():
        if item.tag in {"application","activity","activity-alias",
                        "receiver","service","provider","instrumentation"}:
            name=item.get(ANDROID+"name")
            if name:item.set(ANDROID+"name",qualified(name))
            if item.tag=="activity-alias":
                target=item.get(ANDROID+"targetActivity")
                if target:item.set(ANDROID+"targetActivity",qualified(target))
        # Installing alongside the genuine Joolink must not declare any of its
        # custom permissions: Android rejects duplicate permission owners.
        if item.tag in {"permission","permission-group","permission-tree",
                        "uses-permission","uses-permission-sdk-23",
                        "uses-permission-sdk-m"}:
            original=item.get(ANDROID+"name")
            if original and original.startswith(OLD+"."):
                item.set(ANDROID+"name",NEW+original[len(OLD):])
        # Also update permission references and all provider authority names.
        for key in ("authorities","permission","readPermission","writePermission"):
            attr=ANDROID+key
            value=item.get(attr)
            if value and OLD in value:
                item.set(attr,value.replace(OLD,NEW))
    root.set("package",NEW)
    app=root.find("application")
    if app is None:raise ValueError("application element absent")
    app.set(ANDROID+"label","Joolin2")
    app.set(ANDROID+"allowBackup","false")
    # Signature-specific sharedUserId would prevent the parallel install.
    root.attrib.pop(ANDROID+"sharedUserId",None)
    tree.write(file,encoding="utf-8",xml_declaration=True)
    print("Patched manifest app ID:",NEW)

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument("--decoded",required=True)
    args=parser.parse_args()
    base=pathlib.Path(args.decoded)
    manifest=base/"AndroidManifest.xml"
    if not manifest.exists():raise SystemExit("AndroidManifest.xml is absent")
    patch_manifest(manifest)
    matches=list(base.glob("smali*/"+CAMERA))
    if len(matches)!=1:
        raise SystemExit("Expected one CameraPlayerActivity.smali, got "+str(len(matches)))
    target=matches[0]
    contents=target.read_text(encoding="utf-8")
    install="invoke-static {p0}, "+HELPER+"->install(Landroid/app/Activity;)V"
    apply="invoke-static {p0}, "+HELPER+"->apply(Landroid/app/Activity;)V"
    # install at the end of onCreate, after Android UI exists
    contents,_=hook_method(contents,"onCreate","Landroid/os/Bundle;","return",install)
    # reapply after resuming; apply must also run *before* video is selected
    contents,_=hook_method(contents,"onResume","","return",apply,required=False)
    successful=[]
    for name in ("showHardOrSoftMonitor","updateSoftDecode","updateHardDecode","initPlayerUI"):
        contents,changed=hook_method(contents,name,"[^)]*","entry",apply,required=False)
        if changed:successful.append(name)
    if "showHardOrSoftMonitor" not in successful:
        raise ValueError("Video monitor selector absent: source APK changed, abort rather than ship inert menu")
    print("Vendor decode hooks found:",successful)
    target.write_text(contents,encoding="utf-8")
    print("Video hooks:",", ".join(successful) if successful else "onResume only")
    print("Patched",target)

if __name__=="__main__":
    main()
