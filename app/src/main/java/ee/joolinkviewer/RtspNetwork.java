package ee.joolinkviewer;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Searches only the phone's current Wi-Fi /24 segment. Never scans public networks. */
final class RtspNetwork {
    static final int[] PORTS = {554, 5540, 8554, 10554};
    static final String[] PATHS = {
            "/live/ch0", "/live/ch00_0", "/onvif1",
            "/stream1", "/h264Preview_01_main", "/live/0", "/Streaming/Channels/101"
    };

    interface ScanCallback {
        void found(String ip, int port);
        void finish(String message);
    }

    static String wifiBase(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return null;
        Network network = cm.getActiveNetwork();
        if (network == null) return null;
        NetworkCapabilities nc = cm.getNetworkCapabilities(network);
        if (nc == null || !nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return null;
        LinkProperties lp = cm.getLinkProperties(network);
        if (lp == null) return null;
        for (LinkAddress addr : lp.getLinkAddresses()) {
            InetAddress ip = addr.getAddress();
            if (ip instanceof Inet4Address && isPrivate(ip.getHostAddress())) {
                String[] bytes = ip.getHostAddress().split("\\.");
                if (bytes.length == 4) return bytes[0] + "." + bytes[1] + "." + bytes[2] + ".";
            }
        }
        return null;
    }
    static boolean isPrivate(String ip) {
        String[] parts = ip.split("\\.");
        if (parts.length != 4) return false;
        try {
            int a = Integer.parseInt(parts[0]), b = Integer.parseInt(parts[1]);
            return a == 10 || (a == 192 && b == 168) || (a == 172 && b >= 16 && b <= 31);
        } catch (Exception e) { return false; }
    }

    static void scanHosts(String base, ScanCallback cb) {
        ExecutorService pool = Executors.newFixedThreadPool(32);
        ExecutorCompletionService<HostPorts> service = new ExecutorCompletionService<>(pool);
        try {
            for (int n = 1; n <= 254; n++) {
                final String ip = base + n;
                service.submit(() -> {
                    HostPorts found = new HostPorts(ip);
                    for (int port : PORTS) if (open(ip, port, 210)) found.ports.add(port);
                    return found;
                });
            }
            int total = 0;
            for (int i = 0; i < 254; i++) {
                HostPorts found = service.take().get();
                for (int p : found.ports) { cb.found(found.ip, p); total++; }
            }
            cb.finish(total == 0 ? "RTSP-porte ei leitud. Võib olla pilvepõhine kaamera või teine WiFi."
                    : "Valmis: " + total + " võimalikku kaamera ühendust.");
        } catch (Exception e) { cb.finish("Otsing katkes: " + e.getMessage()); }
        finally { pool.shutdownNow(); }
    }
    private static class HostPorts {
        final String ip;
        final List<Integer> ports = new ArrayList<>();
        HostPorts(String ip) { this.ip = ip; }
    }
    static boolean open(String ip, int port, int ms) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(ip, port), ms);
            return true;
        } catch (Exception ignored) { return false; }
    }
    static String findPath(String ip, int port) {
        String firstAuth = null;
        for (String path : PATHS) {
            String url = "rtsp://" + ip + ":" + port + path;
            int status = describe(ip, port, url);
            if (status == 200) return url;
            if (status == 401 && firstAuth == null) firstAuth = url;
        }
        // Some cameras send 401 before they check path. The user can change the path manually.
        return firstAuth == null ? "rtsp://" + ip + ":" + port + PATHS[0] : firstAuth;
    }
    private static int describe(String ip, int port, String url) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(ip, port), 500);
            socket.setSoTimeout(750);
            OutputStream stream = socket.getOutputStream();
            String request = "DESCRIBE " + url + " RTSP/1.0\r\nCSeq: 1\r\nAccept: application/sdp\r\nUser-Agent: JoolinkViewer/1.0\r\n\r\n";
            stream.write(request.getBytes(StandardCharsets.US_ASCII));
            stream.flush();
            String line = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII)).readLine();
            if (line != null && line.startsWith("RTSP/")) {
                String[] words = line.split(" ");
                if (words.length > 1) return Integer.parseInt(words[1]);
            }
        } catch (Exception ignored) {}
        return -1;
    }
    static String stripCredentials(String rtsp) {
        return rtsp == null ? "" : rtsp.replaceFirst("(?i)^(rtsp://)[^/@]+@", "$1");
    }
    static String withCredentials(String url, String user, String pass) {
        String safeUrl = stripCredentials(url.trim());
        if (user == null || user.isEmpty()) return url.trim();
        int sep = safeUrl.indexOf("://");
        if (sep < 0) return safeUrl;
        String auth = Uri.encode(user, "") + (pass == null || pass.isEmpty() ? "" : ":" + Uri.encode(pass, ""));
        return safeUrl.substring(0, sep + 3) + auth + "@" + safeUrl.substring(sep + 3);
    }
    static boolean validUrl(String url) {
        try {
            Uri u = Uri.parse(url);
            return "rtsp".equalsIgnoreCase(u.getScheme()) && u.getHost() != null && !u.getHost().isEmpty();
        } catch (Exception ex) { return false; }
    }
}
