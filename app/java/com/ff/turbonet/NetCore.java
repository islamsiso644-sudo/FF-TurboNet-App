package com.ff.turbonet;

import java.io.ByteArrayOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * NetCore — قلب الشبكة: فحص TCP-Ping لسيرفرات فري فاير الحقيقية + قياس DNS يدويًا.
 * 100% قانوني: قراءة فقط، لا يلمس اللعبة إطلاقًا.
 * ملاحظة توافق: بدون أي لامدات — فئات داخلية مجهولة فقط (متوافق مع كل الأجهزة).
 */
public final class NetCore {

    /** سيرفرات Garena Free Fire الحقيقية */
    public static final String[][] FF_SERVERS = {
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

    /** TCP-Ping: قياس زمن فتح اتصال TCP (أدق من ICMP بدون روت) */
    public static PingResult tcpPing(String name, String host, int port, int count, int timeoutMs) {
        PingResult r = new PingResult();
        r.name = name;
        r.host = host;
        r.total = count;
        List<Double> times = new ArrayList<Double>();
        for (int i = 0; i < count; i++) {
            long t0 = System.nanoTime();
            Socket s = null;
            try {
                s = new Socket();
                s.connect(new InetSocketAddress(host, port), timeoutMs);
                long t1 = System.nanoTime();
                times.add(Double.valueOf((t1 - t0) / 1e6));
                r.ok++;
            } catch (Exception ignored) {
            } finally {
                if (s != null) { try { s.close(); } catch (Exception ignored2) {} }
            }
            try { Thread.sleep(120); } catch (Exception ignored) {}
        }
        if (!times.isEmpty()) {
            double sum = 0, mn = Double.MAX_VALUE, mx = Double.MIN_VALUE;
            for (int i = 0; i < times.size(); i++) {
                double t = times.get(i).doubleValue();
                sum += t;
                if (t < mn) mn = t;
                if (t > mx) mx = t;
            }
            r.avg = sum / times.size();
            r.min = mn;
            r.max = mx;
            double jitSum = 0; int jitN = 0;
            for (int i = 1; i < times.size(); i++) {
                jitSum += Math.abs(times.get(i).doubleValue() - times.get(i - 1).doubleValue());
                jitN++;
            }
            if (jitN > 0) r.jitter = jitSum / jitN;
            r.loss = (int) Math.round(100.0 * (count - r.ok) / count);
        }
        return r;
    }

    /** فحص كل السيرفرات بالتوازي (10 خيوط) وترتيبها حسب الأسرع */
    public static List<PingResult> pingAll() {
        ExecutorService pool = Executors.newFixedThreadPool(10);
        List<Future<PingResult>> futures = new ArrayList<Future<PingResult>>();
        for (int i = 0; i < FF_SERVERS.length; i++) {
            final String nm = FF_SERVERS[i][0];
            final String hs = FF_SERVERS[i][1];
            final int pt = Integer.parseInt(FF_SERVERS[i][2]);
            futures.add(pool.submit(new Callable<PingResult>() {
                @Override
                public PingResult call() {
                    return tcpPing(nm, hs, pt, 4, 3000);
                }
            }));
        }
        List<PingResult> out = new ArrayList<PingResult>();
        for (int i = 0; i < futures.size(); i++) {
            try { out.add(futures.get(i).get(60, TimeUnit.SECONDS)); } catch (Exception ignored) {}
        }
        pool.shutdown();
        Collections.sort(out, new Comparator<PingResult>() {
            @Override
            public int compare(PingResult a, PingResult b) {
                if (a.avg < 0 && b.avg < 0) return 0;
                if (a.avg < 0) return 1;
                if (b.avg < 0) return -1;
                return Double.compare(a.avg, b.avg);
            }
        });
        return out;
    }

    /** نتيجة قياس DNS */
    public static class DnsResult {
        public String name, ip;
        public double ms = -1;
        public boolean resolved;
    }

    /** قياس DNS يدويًا ببروتوكول DNS خام (بدون مكتبات) */
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
            int id = (int) (Math.random() * 0xFFFF);
            out.write((id >> 8) & 0xFF);
            out.write(id & 0xFF);
            out.write(0x01); out.write(0x00);
            out.write(0x00); out.write(0x01);
            out.write(0x00); out.write(0x00);
            out.write(0x00); out.write(0x00);
            out.write(0x00); out.write(0x00);
            String[] labels = domain.split("\\.");
            for (int i = 0; i < labels.length; i++) {
                byte[] lb = labels[i].getBytes("US-ASCII");
                out.write(lb.length);
                out.write(lb);
            }
            out.write(0x00);
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
        List<Future<DnsResult>> futures = new ArrayList<Future<DnsResult>>();
        for (int i = 0; i < DNS_CANDIDATES.length; i++) {
            final String nm = DNS_CANDIDATES[i][0];
            final String ip = DNS_CANDIDATES[i][1];
            futures.add(pool.submit(new Callable<DnsResult>() {
                @Override
                public DnsResult call() {
                    DnsResult a = queryDns(nm, ip);
                    DnsResult b = queryDns(nm, ip);
                    if (a.resolved && b.resolved) return (a.ms <= b.ms) ? a : b;
                    return a.resolved ? a : b;
                }
            }));
        }
        List<DnsResult> out = new ArrayList<DnsResult>();
        for (int i = 0; i < futures.size(); i++) {
            try { out.add(futures.get(i).get(10, TimeUnit.SECONDS)); } catch (Exception ignored) {}
        }
        pool.shutdown();
        Collections.sort(out, new Comparator<DnsResult>() {
            @Override
            public int compare(DnsResult a, DnsResult b) {
                if (!a.resolved && !b.resolved) return 0;
                if (!a.resolved) return 1;
                if (!b.resolved) return -1;
                return Double.compare(a.ms, b.ms);
            }
        });
        return out;
    }

    /** أفضل سيرفر (الأسرع صالح) */
    public static PingResult bestServer() {
        List<PingResult> all = pingAll();
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).avg > 0) return all.get(i);
        }
        return all.isEmpty() ? new PingResult() : all.get(0);
    }
}
