package com.ff.turbonet;

import java.io.ByteArrayOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * NetCore — قلب الشبكة: فحص TCP-Ping لسيرفرات فري فاير الحقيقية + قياس DNS يدويًا.
 * 100% قانوني: قراءة فقط، لا يلمس اللعبة إطلاقًا.
 */
public final class NetCore {

    /** سيرفرات Garena Free Fire الحقيقية (نفس قائمة الأداة الأصلية) */
    public static final String[][] FF_SERVERS = {
        // {اسم, عنوان, منفذ}
        {"بنغلاديش — Events",  "ff-events.garena.com", "10000"},
        {"بنغلاديش — IP1",     "202.81.97.70",         "10000"},
        {"بنغلاديش — IP2",     "202.81.97.72",         "10000"},
        {"بنغلاديش — IP3",     "202.81.97.73",         "10000"},
        {"سنغافورة — Garena",  "ff.garena.com",         "10000"},
        {"سنغافورة — Login",   "loginff.garena.com",   "10000"},
        {"سنغافورة — IP",      "103.247.205.138",      "10000"},
        {"إندونيسيا — FF",     "freefiremobile.com",   "10000"},
        {"إندونيسيا — IP1",    "125.212.198.39",       "10000"},
        {"إندونيسيا — IP2",    "125.212.198.71",       "10000"},
        {"الهند — FF India",   "freefireind.in",       "10000"},
        {"الهند — PacketGM",   "dl.packetgm.com",      "10000"},
        {"MENA — CDN CF",      "ff.garena.com.cdn.cloudflare.net", "443"},
        {"MENA — CloudFront",  "d1k2ga1ciqxi0i.cloudfront.net",   "443"}
    };

    /** خوادم DNS المرشحة */
    public static final String[][] DNS_CANDIDATES = {
        {"Cloudflare", "1.1.1.1"},
        {"Cloudflare2", "1.0.0.1"},
        {"Google",     "8.8.8.8"},
        {"Google2",    "8.8.4.4"},
        {"Quad9",      "9.9.9.9"},
        {"OpenDNS",    "208.67.222.222"}
    };

    private NetCore() {}

    /** نتيجة فحص سيرفر واحد */
    public static class PingResult {
        public String name;
        public String host;
        public double avg = -1, min = -1, max = -1, jitter = -1;
        public int loss = 100;
        public int ok, total;

        public String quality() {
            if (avg < 0) return "تعذّر الوصول";
            if (avg < 60) return "ممتاز 🟢";
            if (avg < 100) return "جيد 🟡";
            if (avg < 160) return "متوسط 🟠";
            return "ضعيف 🔴";
        }
        public String colorTag() {
            if (avg < 0) return "🔴";
            if (avg < 60) return "🟢";
            if (avg < 100) return "🟡";
            return "🟠";
        }
    }

    /**
     * TCP-Ping: قياس زمن فتح اتصال TCP مع السيرفر (أدق من ICMP على أندرويد بدون روت).
     */
    public static PingResult tcpPing(String name, String host, int port, int count, int timeoutMs) {
        PingResult r = new PingResult();
        r.name = name;
        r.host = host;
        r.total = count;
        List<Double> times = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            long t0 = System.nanoTime();
            Socket s = null;
            try {
                s = new Socket();
                s.connect(new InetSocketAddress(host, port), timeoutMs);
                long t1 = System.nanoTime();
                times.add((t1 - t0) / 1e6);
                r.ok++;
            } catch (Exception ignored) {
            } finally {
                if (s != null) { try { s.close(); } catch (Exception ignored2) {} }
            }
            try { Thread.sleep(120); } catch (Exception ignored) {}
        }
        if (!times.isEmpty()) {
            double sum = 0, mn = Double.MAX_VALUE, mx = Double.MIN_VALUE;
            for (double t : times) { sum += t; if (t < mn) mn = t; if (t > mx) mx = t; }
            r.avg = sum / times.size();
            r.min = mn;
            r.max = mx;
            // الجيتر = متوسط الفرق بين قياسين متتاليين
            double jitSum = 0; int jitN = 0;
            for (int i = 1; i < times.size(); i++) {
                jitSum += Math.abs(times.get(i) - times.get(i - 1)); jitN++;
            }
            if (jitN > 0) r.jitter = jitSum / jitN;
            r.loss = (int) Math.round(100.0 * (count - r.ok) / count);
        }
        return r;
    }

    /** فحص كل السيرفرات بالتوازي (10 خيوط) وإرجاع النتائج مرتبة حسب الأسرع */
    public static List<PingResult> pingAll() {
        ExecutorService pool = Executors.newFixedThreadPool(10);
        List<Future<PingResult>> futures = new ArrayList<>();
        for (String[] srv : FF_SERVERS) {
            final String nm = srv[0], hs = srv[1];
            final int pt = Integer.parseInt(srv[2]);
            futures.add(pool.submit(() -> tcpPing(nm, hs, pt, 4, 3000)));
        }
        List<PingResult> out = new ArrayList<>();
        for (Future<PingResult> f : futures) {
            try { out.add(f.get(60, TimeUnit.SECONDS)); } catch (Exception ignored) {}
        }
        pool.shutdown();
        // ترتيب: الصالح أولًا ثم الأسرع
        out.sort((a, b) -> {
            if (a.avg < 0 && b.avg < 0) return 0;
            if (a.avg < 0) return 1;
            if (b.avg < 0) return -1;
            return Double.compare(a.avg, b.avg);
        });
        return out;
    }

    /** نتيجة قياس DNS */
    public static class DnsResult {
        public String name, ip;
        public double ms = -1;
        public boolean resolved;
    }

    /**
     * قياس DNS يدويًا: نبني باكت استعلام UDP ببروتوكول DNS الخام (بدون مكتبات).
     * نستعلم عن ff.garena.com من كل خادم ونقيس الزمن.
     */
    public static DnsResult queryDns(String name, String serverIp) {
        DnsResult r = new DnsResult();
        r.name = name;
        r.ip = serverIp;
        DatagramSocket sock = null;
        try {
            sock = new DatagramSocket();
            sock.setSoTimeout(2500);

            byte[] query = buildDnsQuery("ff.garena.com");
            long t0 = System.nanoTime();
            sock.send(new DatagramPacket(query, query.length,
                    InetAddress.getByName(serverIp), 53));
            byte[] buf = new byte[512];
            DatagramPacket resp = new DatagramPacket(buf, buf.length);
            sock.receive(resp);
            long t1 = System.nanoTime();
            r.ms = (t1 - t0) / 1e6;
            r.resolved = resp.getLength() > 12;
        } catch (Exception e) {
            r.resolved = false;
        } finally {
            if (sock != null) sock.close();
        }
        return r;
    }

    /** بناء باكت استعلام DNS خام (A record) */
    private static byte[] buildDnsQuery(String domain) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            // Header: ID عشوائي، RD=1
            int id = (int) (Math.random() * 0xFFFF);
            out.write((id >> 8) & 0xFF);
            out.write(id & 0xFF);
            out.write(0x01); out.write(0x00);      // flags: recursion desired
            out.write(0x00); out.write(0x01);      // QDCOUNT=1
            out.write(0x00); out.write(0x00);      // ANCOUNT
            out.write(0x00); out.write(0x00);      // NSCOUNT
            out.write(0x00); out.write(0x00);      // ARCOUNT
            // QNAME
            for (String label : domain.split("\\.")) {
                out.write(label.length());
                out.write(label.getBytes("US-ASCII"));
            }
            out.write(0x00);
            // QTYPE=A, QCLASS=IN
            out.write(0x00); out.write(0x01);
            out.write(0x00); out.write(0x01);
            return out.toByteArray();
        } catch (Exception e) {
            return new byte[]{0,1,1,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0};
        }
    }

    /** فحص كل خوادم DNS بالتوازي وترتيبها حسب الأسرع */
    public static List<DnsResult> dnsAll() {
        ExecutorService pool = Executors.newFixedThreadPool(6);
        List<Future<DnsResult>> futures = new ArrayList<>();
        for (String[] d : DNS_CANDIDATES) {
            final String nm = d[0], ip = d[1];
            futures.add(pool.submit(() -> {
                // أفضل قياس من محاولتين
                DnsResult a = queryDns(nm, ip);
                DnsResult b = queryDns(nm, ip);
                if (a.resolved && b.resolved) return (a.ms <= b.ms) ? a : b;
                return a.resolved ? a : b;
            }));
        }
        List<DnsResult> out = new ArrayList<>();
        for (Future<DnsResult> f : futures) {
            try { out.add(f.get(10, TimeUnit.SECONDS)); } catch (Exception ignored) {}
        }
        pool.shutdown();
        out.sort((a, b) -> {
            if (!a.resolved && !b.resolved) return 0;
            if (!a.resolved) return 1;
            if (!b.resolved) return -1;
            return Double.compare(a.ms, b.ms);
        });
        return out;
    }

    /** أفضل سيرفر (الأسرع صالح) */
    public static PingResult bestServer() {
        List<PingResult> all = pingAll();
        for (PingResult p : all) if (p.avg > 0) return p;
        return all.isEmpty() ? new PingResult() : all.get(0);
    }
}
