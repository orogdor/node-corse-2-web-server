"""Offline integration tests for mock ONVIF + RTSP and production Java TCP relay core."""
import os
import shutil
import socket
import struct
import subprocess
import tempfile
import threading
import time
import unittest
import urllib.request
from pathlib import Path
from testlab.mock_camera import CameraState, make_rtsp_server, make_onvif_server, generate_h264

ROOT=Path(__file__).resolve().parents[1]
JAVA=ROOT/"app/src/main/java/ee/joolinkviewer/RtspProxyCore.java"
HARNESS=ROOT/"testlab/java/RelayHarness.java"
RULES=ROOT/"testlab/java/NetworkRulesTest.java"

def free_port():
    s=socket.socket()
    s.bind(("127.0.0.1",0))
    port=s.getsockname()[1]
    s.close()
    return port

def response(reader):
    head=b""
    while not head.endswith(b"\r\n\r\n"):
        item=reader.read(1)
        if not item:raise EOFError("RTSP connection closed")
        head+=item
        if len(head)>65536:raise RuntimeError("oversize headers")
    size=0
    for line in head.split(b"\r\n"):
        if line.lower().startswith(b"content-length:"):
            size=int(line.split(b":",1)[1])
    return head+reader.read(size)

def exchange(sock,reader,command,cseq=1):
    sock.sendall((command+"\r\nCSeq: "+str(cseq)+"\r\nUser-Agent: HomeJoolink-TestLab\r\n\r\n").encode("ascii"))
    return response(reader)

class RelayTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.tmp=tempfile.TemporaryDirectory()
        cls.classes=cls.tmp.name
        subprocess.run(["javac","-encoding","UTF-8","-d",cls.classes,str(JAVA),str(HARNESS),str(RULES)],
            check=True,capture_output=True,timeout=30)
        cls.state=CameraState("127.0.0.1",0,generate_h264())
        cls.rtsp=make_rtsp_server(cls.state,"127.0.0.1",0)
        cls.state.rtsp_port=cls.rtsp.server_address[1]
        cls.onvif=make_onvif_server(cls.state,"127.0.0.1",0)
        cls.onvif_port=cls.onvif.server_address[1]
        threading.Thread(target=cls.rtsp.serve_forever,daemon=True).start()
        threading.Thread(target=cls.onvif.serve_forever,daemon=True).start()
        cls.relay_port=free_port()
        cls.proc=subprocess.Popen(["java","-cp",cls.classes,"ee.joolinkviewer.RelayHarness",
            f"rtsp://127.0.0.1:{cls.state.rtsp_port}/live/ch0",str(cls.relay_port),"false"],
            stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True)
        # Process may take a moment to bind. Handle errors immediately.
        for _ in range(70):
            if cls.proc.poll() is not None:raise RuntimeError(cls.proc.stderr.read())
            try:
                with socket.create_connection(("127.0.0.1",cls.relay_port),timeout=.3):
                    break
            except OSError:time.sleep(.08)
        else:raise RuntimeError("RTSP relay not listening")
    @classmethod
    def tearDownClass(cls):
        cls.proc.terminate()
        try:cls.proc.wait(timeout=3)
        except subprocess.TimeoutExpired:cls.proc.kill()
        cls.rtsp.shutdown();cls.rtsp.server_close()
        cls.onvif.shutdown();cls.onvif.server_close()
        cls.tmp.cleanup()
    def test_01_network_acl(self):
        run=subprocess.run(["java","-cp",self.classes,"ee.joolinkviewer.NetworkRulesTest"],
            capture_output=True,text=True,timeout=5)
        self.assertEqual(run.returncode,0,run.stdout+run.stderr)
    def test_02_rtsp_description_through_relay(self):
        with socket.create_connection(("127.0.0.1",self.relay_port),timeout=4) as s:
            s.settimeout(4)
            reader=s.makefile("rb")
            url=f"rtsp://127.0.0.1:{self.relay_port}/live/ch0"
            option=exchange(s,reader,f"OPTIONS {url} RTSP/1.0")
            self.assertIn(b"200 OK",option)
            description=exchange(s,reader,f"DESCRIBE {url} RTSP/1.0",2)
            self.assertIn(b"application/sdp",description)
            self.assertIn(b"TestLab Joolink RTSP",description)
            self.assertIn(f":{self.relay_port}/live/ch0".encode(),description)
            self.assertNotIn(f":{self.state.rtsp_port}/live/ch0".encode(),description)
    def test_03_multiple_viewers_reconnect(self):
        url=f"rtsp://127.0.0.1:{self.relay_port}/live/ch0"
        for _ in range(3):
            connections=[socket.create_connection(("127.0.0.1",self.relay_port),timeout=3) for _ in range(2)]
            try:
                for s in connections:
                    s.settimeout(3)
                    answer=exchange(s,s.makefile("rb"),f"OPTIONS {url} RTSP/1.0")
                    self.assertIn(b"200 OK",answer)
            finally:
                for s in connections:s.close()
    def test_04_rtp_interleaved_over_proxy(self):
        with socket.create_connection(("127.0.0.1",self.relay_port),timeout=4) as s:
            s.settimeout(5)
            reader=s.makefile("rb")
            base=f"rtsp://127.0.0.1:{self.relay_port}/live/ch0"
            description=exchange(s,reader,f"DESCRIBE {base} RTSP/1.0",1)
            self.assertIn(b"200 OK",description)
            s.sendall((f"SETUP {base}/trackID=0 RTSP/1.0\r\nCSeq: 2\r\n"
                "Transport: RTP/AVP/TCP;unicast;interleaved=0-1\r\n\r\n").encode())
            self.assertIn(b"interleaved=0-1",response(reader))
            self.assertIn(b"200 OK",exchange(s,reader,f"PLAY {base} RTSP/1.0",3))
            self.assertEqual(reader.read(1),b"$")
            channel,length=struct.unpack("!BH",reader.read(3))
            packet=reader.read(length)
            self.assertEqual(channel,0)
            self.assertGreater(length,12)
            self.assertEqual(packet[0]>>6,2)
    def test_05_onvif_ptz_and_stream_uri(self):
        url=f"http://127.0.0.1:{self.onvif_port}/onvif/device_service"
        def soap(body):
            request=urllib.request.Request(url,
                data=("<Envelope>"+body+"</Envelope>").encode(),
                headers={"Content-Type":"application/soap+xml"})
            return urllib.request.urlopen(request,timeout=3).read()
        cap=soap("<GetCapabilities/>")
        self.assertIn(b"<tt:PTZ>",cap)
        self.assertIn(b"onvif/media_service",cap)
        profiles=soap("<GetProfiles/>")
        self.assertIn(b"testprofile",profiles)
        uri=soap("<GetStreamUri/>")
        self.assertIn(f":{self.state.rtsp_port}/live/ch0".encode(),uri)
        soap("<ContinuousMove/>")
        soap("<Stop/>")
        self.assertIn("move",self.state.ptz_commands)
        self.assertIn("stop",self.state.ptz_commands)
    def test_06_boot_receiver_manifest(self):
        xml=(ROOT/"app/src/main/AndroidManifest.xml").read_text()
        self.assertIn("RECEIVE_BOOT_COMPLETED",xml)
        self.assertIn(".RelayBootReceiver",xml)
        self.assertIn("LEANBACK_LAUNCHER",xml)

if __name__=="__main__":
    unittest.main()
