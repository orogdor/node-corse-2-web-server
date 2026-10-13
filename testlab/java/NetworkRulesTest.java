package ee.joolinkviewer;

public final class NetworkRulesTest {
    private static void check(boolean value,String reason){
        if(!value)throw new AssertionError(reason);
    }
    public static void main(String[] args){
        check(RtspProxyCore.allowed("127.0.0.1",false),"localhost");
        check(RtspProxyCore.allowed("192.168.8.108",false),"LAN");
        check(RtspProxyCore.allowed("10.9.0.1",false),"RFC1918");
        check(RtspProxyCore.allowed("172.31.2.5",false),"RFC1918 range");
        check(!RtspProxyCore.allowed("172.32.1.1",true),"public 172");
        check(!RtspProxyCore.allowed("8.8.8.8",true),"public source");
        check(!RtspProxyCore.allowed("100.101.2.3",false),"VPN opt-in required");
        check(RtspProxyCore.allowed("100.101.2.3",true),"tailnet");
        check(!RtspProxyCore.allowed("100.128.0.1",true),"not CGNAT");
        check(!RtspProxyCore.allowed("1.2.3.999",true),"invalid octet");
        System.out.println("Network rules: PASS");
    }
}
