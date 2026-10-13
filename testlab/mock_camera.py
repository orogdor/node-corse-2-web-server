#!/usr/bin/env python3
"""Offline-friendly RTSP/ONVIF test camera; optional genuine H.264 RTP if ffmpeg is installed.

Usage:
  python3 -m testlab.mock_camera --host 127.0.0.1 --rtsp-port 9554 --onvif-port 8800
This is a TEST FIXTURE, not a manufacturer-specific Joolink cloud P2P emulator.
"""
import argparse
import base64
import hashlib
import os
import re
import socket
import socketserver
import struct
import subprocess
import tempfile
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from xml.sax.saxutils import escape

def annex_b_nals(data):
    parts = re.split(b"\x00\x00\x00\x01|\x00\x00\x01", data)
    return [p for p in parts if p]

def generate_h264():
    """Produce a small baseline encoded test-pattern clip using ffmpeg if present."""
    try:
        p = subprocess.run([
            "ffmpeg", "-hide_banner", "-loglevel", "error",
            "-f", "lavfi", "-i", "testsrc=size=320x180:rate=5",
            "-t", "2", "-pix_fmt", "yuv420p", "-c:v", "libx264",
            "-profile:v", "baseline", "-preset", "ultrafast",
            "-tune", "zerolatency", "-x264-params", "keyint=5:repeat-headers=1",
            "-f", "h264", "pipe:1",
        ], capture_output=True, timeout=20, check=True)
        return annex_b_nals(p.stdout)
    except (OSError, subprocess.CalledProcessError, subprocess.TimeoutExpired):
        return []

class CameraState:
    def __init__(self, host="127.0.0.1", rtsp_port=9554, nal_units=None):
        self.host=host
        self.rtsp_port=rtsp_port
        self.nals=nal_units or []
        self.ptz_commands=[]
        self.lock=threading.Lock()
        self.sessions=0
    def add_ptz(self, command):
        with self.lock:
            self.ptz_commands.append(command)

def make_rtsp_server(state, host, port):
    class ThreadedServer(socketserver.ThreadingMixIn, socketserver.TCPServer):
        allow_reuse_address=True
        daemon_threads=True
    class Handler(socketserver.BaseRequestHandler):
        def handle(self):
            sock=self.request
            sock.settimeout(8)
            reader=sock.makefile("rb")
            state.sessions+=1
            session_id=hashlib.sha256(str(id(self)).encode().strip()).hexdigest()[:8]
            lock=threading.Lock()
            running=threading.Event()
            running.set()
            stream_started=False
            seq=1
            def send_interleaved(nal, stamp):
                nonlocal seq
                if not nal:return
                max_payload=1000
                packets=[]
                if len(nal)<=max_payload:
                    packets=[nal]
                else:
                    # RFC 6184 H264 FU-A packetization for large NAL units.
                    header=nal[0]
                    fragments=[nal[i:i+max_payload-2] for i in range(1,len(nal),max_payload-2)]
                    for i,fragment in enumerate(fragments):
                        indicator=(header&0xe0)|28
                        unit=header&0x1f
                        fu=(0x80 if i==0 else 0)|(0x40 if i==len(fragments)-1 else 0)|unit
                        packets.append(bytes([indicator,fu])+fragment)
                with lock:
                    for i,payload in enumerate(packets):
                        packet=struct.pack("!BBHII",0x80,0x60|(0x80 if i==len(packets)-1 else 0),
                            seq&0xffff,stamp&0xffffffff,0x10203040)+payload
                        seq+=1
                        sock.sendall(b"$\x00"+struct.pack("!H",len(packet))+packet)
            def stream():
                stamp=90000
                while running.is_set():
                    nals=state.nals or [b"\x65"+bytes([0x42])*16]
                    try:
                        for nal in nals:
                            if not running.is_set():return
                            send_interleaved(nal,stamp)
                            stamp+=18000
                            time.sleep(.2)
                    except (OSError,TimeoutError):
                        return
            try:
                while running.is_set():
                    first=reader.readline(65536)
                    if not first:break
                    if first.startswith(b"$"):
                        # Do not parse RTCP from clients in mock server.
                        remaining=reader.read(3)
                        if len(remaining)<3:break
                        count=(remaining[1]<<8)|remaining[2]
                        reader.read(count);continue
                    if not first.endswith(b"\n"):break
                    lines=[]
                    while True:
                        line=reader.readline(65536)
                        if line in (b"\r\n",b"\n",b""):break
                        lines.append(line.decode("iso-8859-1").strip())
                    cseq="1"
                    for line in lines:
                        if line.lower().startswith("cseq:"):cseq=line.split(":",1)[1].strip()
                    method=first.decode("iso-8859-1").split()[0]
                    base=f"rtsp://{state.host}:{state.rtsp_port}/live/ch0"
                    sps=next((n for n in state.nals if n[0]&0x1f==7),None)
                    pps=next((n for n in state.nals if n[0]&0x1f==8),None)
                    fmtp=""
                    if sps and pps:
                        fmtp="a=fmtp:96 packetization-mode=1; sprop-parameter-sets="+base64.b64encode(sps).decode()+","+base64.b64encode(pps).decode()+"\r\n"
                    sdp=("v=0\r\no=- 1 1 IN IP4 "+state.host+"\r\ns=TestLab Joolink RTSP\r\n"
                         "t=0 0\r\na=control:*\r\nm=video 0 RTP/AVP 96\r\n"
                         "a=rtpmap:96 H264/90000\r\n"+fmtp+"a=control:"+base+"/trackID=0\r\n")
                    headers=[f"RTSP/1.0 200 OK",f"CSeq: {cseq}",f"Session: {session_id}"]
                    if method=="OPTIONS":
                        headers.append("Public: OPTIONS, DESCRIBE, SETUP, PLAY, TEARDOWN, GET_PARAMETER")
                        body=b""
                    elif method=="DESCRIBE":
                        headers.extend(["Content-Type: application/sdp",f"Content-Base: {base}/"])
                        body=sdp.encode("ascii")
                    elif method=="SETUP":
                        headers.append("Transport: RTP/AVP/TCP;unicast;interleaved=0-1")
                        body=b""
                    elif method=="PLAY":
                        headers.append(f"RTP-Info: url={base}/trackID=0;seq={seq};rtptime=90000")
                        body=b""
                    elif method in ("GET_PARAMETER","TEARDOWN"):
                        body=b""
                    else:
                        headers[0]="RTSP/1.0 405 Method Not Allowed"
                        body=b""
                    headers.append("Content-Length: "+str(len(body)))
                    response=("\r\n".join(headers)+"\r\n\r\n").encode()+body
                    with lock:sock.sendall(response)
                    if method=="PLAY" and not stream_started:
                        stream_started=True
                        threading.Thread(target=stream,daemon=True).start()
                    if method=="TEARDOWN":break
            except (OSError,ValueError):pass
            finally:
                running.clear()
                reader.close()
    return ThreadedServer((host,port),Handler)

def make_onvif_server(state, host, port):
    class Handler(BaseHTTPRequestHandler):
        def log_message(self, format, *args):pass
        def do_POST(self):
            length=min(int(self.headers.get("Content-Length","0")),65536)
            body=self.rfile.read(length).decode("utf-8","replace")
            def service(path):
                return f"http://{state.host}:{self.server.server_port}{path}"
            if "GetCapabilities" in body or "GetServices" in body:
                answer=(
                    "<tds:GetCapabilitiesResponse><tds:Capabilities>"
                    f"<tt:Media><tt:XAddr>{service('/onvif/media_service')}</tt:XAddr></tt:Media>"
                    f"<tt:PTZ><tt:XAddr>{service('/onvif/ptz_service')}</tt:XAddr></tt:PTZ>"
                    f"<tt:Imaging><tt:XAddr>{service('/onvif/imaging_service')}</tt:XAddr></tt:Imaging>"
                    "</tds:Capabilities></tds:GetCapabilitiesResponse>")
            elif "GetProfiles" in body:
                answer=("<trt:GetProfilesResponse><trt:Profiles token='testprofile'>"
                    "<tt:Name>TestLab PTZ</tt:Name>"
                    "<tt:VideoSourceConfiguration><tt:SourceToken>source1</tt:SourceToken></tt:VideoSourceConfiguration>"
                    "<tt:VideoEncoderConfiguration><tt:Encoding>H264</tt:Encoding>"
                    "<tt:Resolution><tt:Width>320</tt:Width><tt:Height>180</tt:Height></tt:Resolution>"
                    "</tt:VideoEncoderConfiguration></trt:Profiles></trt:GetProfilesResponse>")
            elif "GetStreamUri" in body:
                link=f"rtsp://{state.host}:{state.rtsp_port}/live/ch0"
                answer=f"<trt:GetStreamUriResponse><trt:MediaUri><tt:Uri>{link}</tt:Uri></trt:MediaUri></trt:GetStreamUriResponse>"
            elif "ContinuousMove" in body:
                state.add_ptz("move")
                answer="<tptz:ContinuousMoveResponse/>"
            elif "GotoHomePosition" in body:
                state.add_ptz("home")
                answer="<tptz:GotoHomePositionResponse/>"
            elif "<tptz:Stop" in body or "Stop>" in body:
                state.add_ptz("stop")
                answer="<tptz:StopResponse/>"
            elif "SetImagingSettings" in body:
                state.add_ptz("night")
                answer="<timg:SetImagingSettingsResponse/>"
            else:
                answer="<tds:GetServicesResponse/>"
            response=(
                "<s:Envelope xmlns:s='http://www.w3.org/2003/05/soap-envelope'"
                " xmlns:tds='http://www.onvif.org/ver10/device/wsdl'"
                " xmlns:trt='http://www.onvif.org/ver10/media/wsdl'"
                " xmlns:tptz='http://www.onvif.org/ver20/ptz/wsdl'"
                " xmlns:timg='http://www.onvif.org/ver20/imaging/wsdl'"
                " xmlns:tt='http://www.onvif.org/ver10/schema'>"
                "<s:Body>"+answer+"</s:Body></s:Envelope>").encode()
            self.send_response(200);self.send_header("Content-Type","application/soap+xml")
            self.send_header("Content-Length",str(len(response)));self.end_headers()
            self.wfile.write(response)
    return ThreadingHTTPServer((host,port),Handler)

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument("--host",default="127.0.0.1")
    parser.add_argument("--rtsp-port",type=int,default=9554)
    parser.add_argument("--onvif-port",type=int,default=8800)
    parser.add_argument("--no-video",action="store_true",
                        help="Use dummy RTP if ffmpeg is unavailable")
    args=parser.parse_args()
    nals=[] if args.no_video else generate_h264()
    state=CameraState(args.host,args.rtsp_port,nals)
    rtsp=make_rtsp_server(state,args.host,args.rtsp_port)
    onvif=make_onvif_server(state,args.host,args.onvif_port)
    threading.Thread(target=rtsp.serve_forever,daemon=True).start()
    print(f"RTSP: rtsp://{args.host}:{args.rtsp_port}/live/ch0",flush=True)
    print(f"ONVIF: http://{args.host}:{args.onvif_port}/onvif/device_service",flush=True)
    print("H264 generated" if nals else "Dummy RTP test-only (install ffmpeg for real video)",flush=True)
    try:onvif.serve_forever()
    except KeyboardInterrupt:pass
    finally:onvif.server_close();rtsp.shutdown();rtsp.server_close()

if __name__=="__main__":main()
