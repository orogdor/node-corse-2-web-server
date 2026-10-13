package ee.joolinkviewer;

/** Command line launcher of exactly the relay core used by the Android foreground service. */
public final class RelayHarness {
    public static void main(String[] args)throws Exception {
        if(args.length<3)throw new IllegalArgumentException("sourceUrl listenPort vpnEnabled");
        try(RtspProxyCore relay=new RtspProxyCore(args[0],Integer.parseInt(args[1]),Boolean.parseBoolean(args[2]))){
            relay.start();
            System.out.println("READY "+args[1]);
            System.out.flush();
            Thread.sleep(20000);
        }
    }
}
