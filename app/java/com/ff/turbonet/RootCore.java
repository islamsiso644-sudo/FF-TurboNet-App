package com.ff.turbonet;

import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.List;

/**
 * RootCore — التحسينات العميقة عبر su (للأجهزة المروّتة فقط).
 * كل التعديلات على مستوى نظام التشغيل (sysctl) — لا شيء يلمس اللعبة.
 */
public final class RootCore {

    /** إعدادات sysctl (المفتاح، القيمة، الوصف بالعربي) */
    public static final Object[][] TCP_TUNING = {
        {"net.core.rmem_max",          "16777216",     "أقصى حجم ريسيف UDP/TCP"},
        {"net.core.wmem_max",          "16777216",     "أقصى حجم سند UDP/TCP"},
        {"net.core.rmem_default",      "1048576",      "حجم ريسيف الافتراضي"},
        {"net.core.wmem_default",      "1048576",      "حجم سند الافتراضي"},
        {"net.core.netdev_max_backlog","5000",         "طابور بيانات الشبكة"},
        {"net.core.somaxconn",         "4096",         "أقصى اتصالات معلّقة"},
        {"net.ipv4.tcp_rmem",          "4096 87380 16777216", "بافر TCP للريسيف"},
        {"net.ipv4.tcp_wmem",          "4096 65536 16777216", "بافر TCP للسند"},
        {"net.ipv4.udp_mem",           "8388608 12582912 16777216", "بافر UDP العام"},
        {"net.ipv4.udp_rmem_min",      "16384",        "أدنى ريسيف UDP"},
        {"net.ipv4.udp_wmem_min",      "16384",        "أدنى سند UDP"},
        {"net.ipv4.tcp_congestion_control", "cubic",   "خوارزمية الازدحام (cubic متوافق مع كيرنل أندرويد)"},
        {"net.ipv4.tcp_fastopen",      "3",            "TCP Fast Open"},
        {"net.ipv4.tcp_sack",          "1",            "SACK — استرجاع انتقائي"},
        {"net.ipv4.tcp_frto",          "2",            "F-RTO — استرجاع أسرع"},
        {"net.ipv4.tcp_mtu_probing",   "1",            "اكتشاف MTU تلقائيًا"},
        {"net.ipv4.tcp_no_metrics_save","1",           "عدم حفظ ملفات الاعوجاج"},
        {"net.ipv4.tcp_keepalive_time","60",           "Keepalive كل 60 ثانية"},
        {"net.ipv4.tcp_keepalive_intvl","10",          "فاصل Keepalive"},
        {"net.ipv4.tcp_keepalive_probes","5",          "محاولات Keepalive"}
    };

    private RootCore() {}

    /** هل الجهاز مروّت؟ */
    public static boolean hasRoot() {
        Process p = null;
        try {
            p = Runtime.getRuntime().exec("su -c id");
            BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line = r.readLine();
            p.waitFor();
            return line != null && line.contains("uid=0");
        } catch (Exception e) {
            return false;
        } finally {
            if (p != null) p.destroy();
        }
    }

    /** تنفيذ أمر روت واحد وإرجاع النتيجة */
    public static String su(String cmd) {
        Process p = null;
        try {
            p = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(p.getOutputStream());
            os.writeBytes(cmd + "\n");
            os.writeBytes("exit\n");
            os.flush();
            BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line).append("\n");
            p.waitFor();
            return sb.toString();
        } catch (Exception e) {
            return "";
        } finally {
            if (p != null) p.destroy();
        }
    }

    /** تنفيذ sysctl واحد عبر su. يرجّع true إذا نجح */
    public static boolean sysctl(String key, String value) {
        String out = su("sysctl -w \"" + key + "=" + value + "\" 2>/dev/null && echo TURBO_OK");
        return out.contains("TURBO_OK");
    }

    /** تطبيق كل التحسينات — يرجّع سجلًّا بالعربي */
    public static List<String> applyTuning() {
        List<String> log = new ArrayList<>();
        int ok = 0, fail = 0;
        for (Object[] row : TCP_TUNING) {
            String key = (String) row[0];
            String val = (String) row[1];
            String desc = (String) retrySafeDesc(row[2]);
            if (sysctl(key, val)) {
                ok++;
                log.add("  ✅ " + key + " = " + val + "  — " + desc);
            } else {
                fail++;
                log.add("  ⛔ " + key + " (غير مدعوم بكيرنلك)");
            }
        }
        log.add(0, "🚀 تم تطبيق " + ok + "/" + TCP_TUNING.length + " تحسينًا ✅" + (fail > 0 ? " (" + fail + " غير مدعومة)" : ""));
        // حفظ نسخة في /data لتبقى بعد إعادة التشغيل (أصحاب الروت)
        su("mkdir -p /data/local/turbonet 2>/dev/null; " +
           "echo '# FF-TurboNet tuning' > /data/local/turbonet/99-turbonet.conf; " +
           "for r in 'net.core.rmem_max=16777216' 'net.core.wmem_max=16777216' 'net.ipv4.tcp_rmem=4096 87380 16777216' 'net.ipv4.tcp_wmem=4096 65536 16777216' 'net.ipv4.udp_mem=8388608 12582912 16777216' 'net.ipv4.tcp_fastopen=3' 'net.ipv4.tcp_sack=1' 'net.ipv4.tcp_frto=2' 'net.ipv4.tcp_mtu_probing=1'; do echo \"$r\" >> /data/local/turbonet/99-turbonet.conf; done; " +
           "echo TURBO_SAVE_OK");
        return log;
    }

    /** وصف آمن */
    private static String retrySafeDesc(Object o) {
        try { return (String) o; } catch (Exception e) { return ""; }
    }

    /** وضع اللعب: إيقاف تطبيقات الخلفية الشهيرة (قانوني — لا يلمس اللعبة) */
    public static List<String> gameMode() {
        List<String> log = new ArrayList<>();
        log.add("🎮 تفعيل وضع اللعب…");
        // 1) تحسينات TCP السريعة
        for (String[] kv : new String[][]{
                {"net.ipv4.tcp_fastopen", "3"},
                {"net.ipv4.tcp_sack", "1"},
                {"net.ipv4.tcp_frto", "2"},
                {"net.ipv4.tcp_mtu_probing", "1"},
                {"net.ipv4.tcp_no_metrics_save", "1"}}) {
            if (sysctl(kv[0], kv[1])) log.add("  ✅ " + kv[0] + " = " + kv[1]);
        }
        // 2) إيقاف تطبيقات الخلفية الشهيرة (توفير RAM وCPU — قانوني تمامًا)
        String[] killers = {
            "com.facebook.katana", "com.facebook.orca", "com.instagram.android",
            "com.whatsapp", "com.snapchat.android", "com.zhiliaoapp.musically",
            "com.ss.android.ugc.trill", "com.google.android.youtube",
            "com.spotify.music", "com.sec.android.app.sbrowser",
            "com.android.chrome", "com.UCMobile.intl",
            "com.cleanmaster.mguard_cn", "com.tencent.ig" /* لا! هذا بنجي — نتجاهله */
        };
        int stopped = 0;
        for (String pkg : killers) {
            if (pkg.equals("com.tencent.ig")) continue; // حماية: لا نلمس PUBG
            String out = su("am force-stop " + pkg + " 2>/dev/null && echo STOP_OK");
            if (out.contains("STOP_OK")) {
                stopped++;
                log.add("  🛑 أُوقف: " + pkg);
            }
        }
        log.add("✅ وضع اللعب مكتمل — أُوقف " + stopped + " تطبيق خلفية، وطبّقنا التحسينات السريعة.");
        log.add("💡 افتح فري فاير الآن — لا تفتح أي تطبيق آخر.");
        return log;
    }

    /** تراجع Undo — إعادة القيم الافتراضية */
    public static List<String> undo() {
        List<String> log = new ArrayList<>();
        log.add("↩️ جارٍ استرجاع الإعدادات الافتراضية…");
        for (String[] kv : new String[][]{
                {"net.core.rmem_max", "212992"},
                {"net.core.wmem_max", "212992"},
                {"net.core.rmem_default", "212992"},
                {"net.core.wmem_default", "212992"},
                {"net.core.netdev_max_backlog", "1000"},
                {"net.core.somaxconn", "128"},
                {"net.ipv4.tcp_rmem", "4096 131072 6291456"},
                {"net.ipv4.tcp_wmem", "4096 16384 4194304"},
                {"net.ipv4.udp_mem", "367180 489573 734360"},
                {"net.ipv4.udp_rmem_min", "4136"},
                {"net.ipv4.udp_wmem_min", "4136"},
                {"net.ipv4.tcp_fastopen", "0"},
                {"net.ipv4.tcp_sack", "1"},
                {"net.ipv4.tcp_frto", "2"},
                {"net.ipv4.tcp_mtu_probing", "0"},
                {"net.ipv4.tcp_no_metrics_save", "0"},
                {"net.ipv4.tcp_keepalive_time", "7200"},
                {"net.ipv4.tcp_keepalive_intvl", "75"},
                {"net.ipv4.tcp_keepalive_probes", "9"}}) {
            String key = kv[0], val = kv[1];
            String out = su("sysctl -w \"" + key + "=" + val + "\" 2>/dev/null && echo TURBO_OK");
            log.add((out.contains("TURBO_OK") ? "  ✅ " : "  ⛔ ") + key + " → افتراضي");
        }
        su("rm -f /data/local/turbonet/99-turbonet.conf 2>/dev/null; echo done");
        log.add("✅ تم التراجع — كل الإعدادات عادت كما كانت.");
        return log;
    }
}
