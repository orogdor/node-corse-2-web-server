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
NEW="ee.joolin2.decoder.v4"
CAMERA="com/jooan/qiaoanzhilian/ui/activity/play/CameraPlayerActivity.smali"
HELPER="Lcom/jooan/qiaoanzhilian/ui/activity/play/VideoSettings;"
CRASH="Lcom/jooan/qiaoanzhilian/ui/activity/play/Joolin2CrashReporter;"
DIAGNOSTICS="com.jooan.qiaoanzhilian.ui.activity.play.Joolin2DiagnosticsActivity"
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

def guard_application_oncreate(text):
    """Keep diagnostic process alive even if vendor Application.onCreate fails."""
    pattern=re.compile(r'(?ms)^\.method[^\n]*\bonCreate\(\)V\n.*?^\.end method')
    matches=list(pattern.finditer(text))
    if len(matches)!=1:
        raise ValueError("Cannot safely locate one Application.onCreate")
    m=matches[0]
    original=m.group()
    local=re.search(r'(?m)^(\s*\.locals\s+)(\d+)(\s*)$',original)
    if not local:raise ValueError("Application.onCreate lacks .locals")
    n=int(local.group(2))
    if n>240:raise ValueError("Application.onCreate locals too large")
    updated=original[:local.start(2)]+str(n+1)+original[local.end(2):]
    local2=re.search(r'(?m)^(\s*\.locals\s+)(\d+)(\s*)$',updated)
    gate=(
        "\n    invoke-static {p0}, "+CRASH+
        "->guard(Landroid/app/Application;)Z"+
        "\n    move-result v"+str(n)+
        "\n    if-eqz v"+str(n)+", :joolin2_vendor_application"+
        "\n    return-void"+
        "\n    :joolin2_vendor_application"
    )
    updated=updated[:local2.end()]+gate+updated[local2.end():]
    return text[:m.start()]+updated+text[m.end():]

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
    app_class=app.get(ANDROID+"name")
    if app_class:app_class=qualified(app_class)
    app.set(ANDROID+"label","Joolin2 v4")
    app.set(ANDROID+"allowBackup","false")
    root.attrib.pop(ANDROID+"sharedUserId",None)
    # Interpose diagnostic launcher, forwarding normally to the original launch.
    launcher_name=None
    for component in app.findall("activity")+app.findall("activity-alias"):
        for filter_node in list(component.findall("intent-filter")):
            actions={x.get(ANDROID+"name") for x in filter_node.findall("action")}
            categories={x.get(ANDROID+"name") for x in filter_node.findall("category")}
            if ("android.intent.action.MAIN" in actions and
                    "android.intent.category.LAUNCHER" in categories):
                if launcher_name is not None:raise ValueError("multiple launcher activities")
                launcher_name=qualified(component.get(ANDROID+"name"))
                component.remove(filter_node)
    if launcher_name is None:raise ValueError("original launcher intent not found")
    meta=ET.SubElement(app,"meta-data")
    meta.set(ANDROID+"name","joolin2.original_launcher")
    meta.set(ANDROID+"value",launcher_name)
    diag=ET.SubElement(app,"activity")
    diag.set(ANDROID+"name",DIAGNOSTICS)
    diag.set(ANDROID+"exported","true")
    diag.set(ANDROID+"process",":diagnostics")
    diag.set(ANDROID+"label","Joolin2 v4")
    filt=ET.SubElement(diag,"intent-filter")
    action=ET.SubElement(filt,"action")
    action.set(ANDROID+"name","android.intent.action.MAIN")
    category=ET.SubElement(filt,"category")
    category.set(ANDROID+"name","android.intent.category.LAUNCHER")
    tree.write(file,encoding="utf-8",xml_declaration=True)
    print("Joolin2 diagnostics launch original activity:",launcher_name)
    print("Patched manifest app ID:",NEW)
    return app_class

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument("--decoded",required=True)
    args=parser.parse_args()
    base=pathlib.Path(args.decoded)
    manifest=base/"AndroidManifest.xml"
    if not manifest.exists():raise SystemExit("AndroidManifest.xml is absent")
    app_class=patch_manifest(manifest)
    # Rebind three or fewer direct package constants for app-local intents.
    hits=[]
    for file in base.glob("smali*/**/*.smali"):
        source=file.read_text(encoding="utf-8")
        changed,count=re.subn(
            r'(\\bconst-string(?:/jumbo)?\\s+[^,]+,\\s*")'+re.escape(OLD)+r'(")',
            lambda m:m.group(1)+NEW+m.group(2),source)
        if count:
            file.write_text(changed,encoding="utf-8")
            hits.append((file.relative_to(base).as_posix(),count))
    print("Rebound hardcoded package constants:",hits)
    if not app_class:raise ValueError("Application class missing; cannot instrument")
    app_path=app_class.replace(".","/")+".smali"
    apps=list(base.glob("smali*/"+app_path))
    if len(apps)!=1:raise ValueError("Application class not found: "+app_class)
    contents=apps[0].read_text(encoding="utf-8")
    contents=guard_application_oncreate(contents)
    apps[0].write_text(contents,encoding="utf-8")
    print("Crash recorder inserted in:",app_class)
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
