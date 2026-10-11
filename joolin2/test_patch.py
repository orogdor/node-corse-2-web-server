#!/usr/bin/env python3
"""Unit checks for side-by-side package name rewrite and smali hooks."""
import pathlib,sys,tempfile
sys.path.insert(0,str(pathlib.Path(__file__).parent))
from patch import hook_method,patch_manifest,HELPER,NEW

METHODS=""".class public Lcom/jooan/qiaoanzhilian/ui/activity/play/CameraPlayerActivity;
.super Landroid/app/Activity;
.method protected onCreate(Landroid/os/Bundle;)V
    .locals 1
    invoke-super {p0, p1}, Landroid/app/Activity;->onCreate(Landroid/os/Bundle;)V
    return-void
.end method
.method protected onResume()V
    .locals 0
    invoke-super {p0}, Landroid/app/Activity;->onResume()V
    return-void
.end method
.method private showHardOrSoftMonitor()V
    .locals 0
    return-void
.end method
.method private updateSoftDecode()V
    .locals 0
    return-void
.end method
.method private updateHardDecode()V
    .locals 0
    return-void
.end method
.method private initPlayerUI()V
    .locals 0
    return-void
.end method
"""
def test_hooks():
    code=METHODS
    install="invoke-static {p0}, "+HELPER+"->install(Landroid/app/Activity;)V"
    apply="invoke-static {p0}, "+HELPER+"->apply(Landroid/app/Activity;)V"
    code,yes=hook_method(code,"onCreate","Landroid/os/Bundle;","return",install)
    assert yes and code.count("VideoSettings;->install")==1
    code,yes=hook_method(code,"onResume","","return",apply)
    assert yes
    for name in ("showHardOrSoftMonitor","updateSoftDecode","updateHardDecode","initPlayerUI"):
        code,yes=hook_method(code,name,"[^)]*","entry",apply)
        assert yes
    assert code.count("VideoSettings;->apply")==5
    old=code
    code,yes=hook_method(code,"showHardOrSoftMonitor","[^)]*","entry",apply)
    assert yes and code==old
    print("Decoder smali hook tests passed")

def test_manifest():
    xml='''<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
package="com.jooan.qiaoanzhilian.gp">
<application android:label="@string/app_name" android:name=".MainApp">
<activity android:name=".MainActivity">
<intent-filter>
<action android:name="android.intent.action.MAIN"/>
<category android:name="android.intent.category.LAUNCHER"/>
</intent-filter>
</activity>
<service android:name="com.jooan.service.Helper"/>
<provider android:name=".Provider" android:authorities="com.jooan.qiaoanzhilian.gp.fileprovider"
 android:readPermission="com.jooan.qiaoanzhilian.gp.permission.PUSH_PROVIDER"/>
<permission android:name="com.jooan.qiaoanzhilian.gp.permission.JPUSH_MESSAGE"/>
<permission android:name="com.jooan.qiaoanzhilian.gp.permission.PUSH_PROVIDER"/>
<uses-permission android:name="com.jooan.qiaoanzhilian.gp.permission.JPUSH_MESSAGE"/>
</application></manifest>'''
    with tempfile.TemporaryDirectory() as tmp:
        path=pathlib.Path(tmp)/"AndroidManifest.xml"
        path.write_text(xml,encoding="utf-8")
        patch_manifest(path)
        text=path.read_text(encoding="utf-8")
        assert 'package="'+NEW+'"' in text
        assert "Joolin2" in text
        assert 'com.jooan.qiaoanzhilian.gp.MainActivity' in text
        assert NEW+'.fileprovider' in text
        assert NEW+'.permission.JPUSH_MESSAGE' in text
        assert NEW+'.permission.PUSH_PROVIDER' in text
        assert 'android:readPermission="'+NEW+'.permission.PUSH_PROVIDER"' in text
        assert 'name="com.jooan.qiaoanzhilian.gp.permission.' not in text
        assert 'joolin2.original_launcher' in text
        assert 'com.jooan.qiaoanzhilian.gp.MainActivity' in text
        assert 'Joolin2DiagnosticsActivity' in text
        assert text.count('android.intent.category.LAUNCHER')==1
    print("Independent package manifest tests passed")

if __name__=="__main__":
    test_hooks()
    test_manifest()
