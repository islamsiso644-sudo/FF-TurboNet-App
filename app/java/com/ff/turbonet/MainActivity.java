package com.ff.turbonet;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * FF TurboNet — تطبيق تحسين الشبكة القانوني لفري فاير.
 * بدون مكتبات خارجية — بدون لامدات (توافق كامل مع كل الأجهزة).
 */
public class MainActivity extends Activity {

    private TextView tvResults, tvStatus;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private boolean rootAvailable = false;
    private String lastReport = "";
    private volatile boolean monitoring = false;
    private Thread monitorThread;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setContentView(R.layout.activity_main);
            tvResults = findViewById(R.id.tvResults);
            tvStatus  = findViewById(R.id.tvStatus);
            tvResults.setMovementMethod(new ScrollingMovementMethod());
        } catch (Throwable t) {
            // حتى لو فشل شيء في الواجهة — لا ننهار: نعرض النص الخام
            android.util.Log.e("TurboNet", "UI init failed", t);
            TextView fallback = new TextView(this);
            fallback.setText("خطأ في الواجهة: " + t);
            setContentView(fallback);
            return;
        }

        // فحص الروت في الخلفية
        worker.execute(new Runnable() {
            @Override
            public void run() {
                rootAvailable = RootCore.hasRoot();
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        tvStatus.setText(rootAvailable
                                ? "🔓 روت مكتشف — التحسينات العميقة متاحة"
                                : "📱 بدون روت — الفحص والمراقبة والنصائح تعمل (التحسينات العميقة تحتاج روت أو موديول Magisk المرفق)");
                    }
                });
            }
        });

        appendResult("مرحبًا! 👋\n\nFF TurboNet v1.1 جاهز.\n" +
                "• 📡 فحص البينق: يقيس زمن الوصول لكل سيرفرات فري فاير الحقيقية\n" +
                "• 🌐 اختبار DNS: يجد أسرع DNS ويقترح الأفضل\n" +
                "• 🚀 تحسينات TCP: تعديلات نظام الشبكة (تتطلب روت)\n" +
                "• 🎮 وضع اللعب: إيقاف تطبيقات الخلفية + تحسينات سريعة\n" +
                "• 📊 مراقبة حية: بينق متجدد أثناء اللعب\n\n" +
                "اختر زرًّا من الأعلى للبدء… ✅");
    }

    /* ============ الأزرار ============ */

    public void onPing(View v) {
        startTask("📡 جارٍ فحص سيرفرات فري فاير (14 سيرفرًا بالتوازي)…\nقد يستغرق حتى 30 ثانية…");
        worker.execute(new Runnable() {
            @Override
            public void run() {
                List<NetCore.PingResult> results;
                try {
                    results = NetCore.pingAll();
                } catch (Throwable t) {
                    results = null;
                    lastErr = t;
                }
                final List<NetCore.PingResult> res = results;
                final StringBuilder sb = new StringBuilder();
                if (res == null) {
                    sb.append("⛔ حدث خطأ غير متوقع أثناء الفحص:\n")
                      .append(lastErr == null ? "غير معروف" : android.util.Log.getStackTraceString(lastErr));
                } else {
                    sb.append("══════════════════════════\n");
                    sb.append("📡 نتائج فحص سيرفرات فري فاير\n");
                    sb.append("══════════════════════════\n\n");
                    int rank = 1;
                    for (int i = 0; i < res.size(); i++) {
                        NetCore.PingResult r = res.get(i);
                        if (r.avg < 0) {
                            sb.append(String.format(Locale.US, "🔴 %s\n    %s — تعذّر الوصول\n\n", r.name, r.host));
                        } else {
                            sb.append(String.format(Locale.US,
                                    "%s #%d %s\n    %s\n    متوسط %5.1f ms | أدنى %5.1f | أقصى %5.1f | جيتر %4.1f | فقد %d%%\n    التقييم: %s\n\n",
                                    r.colorTag(), rank, r.name, r.host,
                                    r.avg, r.min, r.max, r.jitter, r.loss, r.quality()));
                            rank++;
                        }
                    }
                    NetCore.PingResult best = res.isEmpty() ? null : res.get(0);
                    if (best != null && best.avg > 0) {
                        sb.append("🏆 الأسرع لك: ").append(best.name)
                          .append(String.format(Locale.US, " (%.1f ms)", best.avg)).append("\n");
                        sb.append("💡 منطقة ").append(region(best.host)).append(" هي الأفضل لجهازك.\n");
                        if (best.avg < 60) sb.append("🟢 بينق ممتاز — جاهز للعب!\n");
                        else if (best.avg < 100) sb.append("🟡 بينق جيد.\n");
                        else sb.append("🟠 جرّب تغيير DNS من زر DNS، وتأكد من قوة الواي فاي.\n");
                    }
                }
                lastReport = sb.toString();
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        tvResults.setText(lastReport);
                        tvResults.scrollTo(0, 0);
                    }
                });
            }
        });
    }

    public void onDns(View v) {
        startTask("🌐 جارٍ اختبار 6 خوادم DNS…");
        worker.execute(new Runnable() {
            @Override
            public void run() {
                List<NetCore.DnsResult> results;
                try {
                    results = NetCore.dnsAll();
                } catch (Throwable t) {
                    results = null;
                }
                final List<NetCore.DnsResult> res = results;
                final StringBuilder sb = new StringBuilder();
                if (res == null) {
                    sb.append("⛔ خطأ في اختبار DNS — جرّب مجددًا.\n");
                } else {
                    sb.append("══════════════════════════\n");
                    sb.append("🌐 نتائج اختبار DNS\n");
                    sb.append("══════════════════════════\n\n");
                    for (int i = 0; i < res.size(); i++) {
                        NetCore.DnsResult r = res.get(i);
                        if (r.resolved) {
                            sb.append(String.format(Locale.US, "🟢 %-12s (%s)\n    زمن الاستجابة: %5.1f ms\n\n",
                                    r.name, r.ip, r.ms));
                        } else {
                            sb.append(String.format(Locale.US, "🔴 %-12s (%s)\n    لا يستجيب\n\n", r.name, r.ip));
                        }
                    }
                    NetCore.DnsResult fastest = res.isEmpty() ? null : res.get(0);
                    if (fastest != null && fastest.resolved) {
                        sb.append("🏆 أسرع DNS: ").append(fastest.name)
                          .append(" (").append(fastest.ip).append(")\n\n");
                        sb.append("💡 لتغيير DNS على جهازك (بدون روت):\n");
                        sb.append("   الإعدادات ← الشبكة ← Wi-Fi ← اضغط مطولًا على شبكتك\n");
                        sb.append("   ← تعديل ← خيارات متقدمة ← IP: ثابت\n");
                        sb.append("   ← DNS 1: ").append(fastest.ip).append("\n");
                        sb.append("   ← DNS 2: 1.0.0.1\n\n");
                    }
                }
                lastReport = sb.toString();
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        tvResults.setText(lastReport);
                        tvResults.scrollTo(0, 0);
                    }
                });
            }
        });
    }

    public void onTune(View v) {
        if (!rootAvailable) { showNoRootInfo(); return; }
        startTask("🚀 جارٍ تطبيق تحسينات TCP…");
        worker.execute(new Runnable() {
            @Override
            public void run() {
                List<String> log;
                try { log = RootCore.applyTuning(); }
                catch (Throwable t) { log = null; }
                if (log == null) { log = new ArrayList<String>(); log.add("⛔ خطأ — تأكد من منح صلاحية su للتطبيق."); }
                final List<String> logF = log;
                lastReport = joinLines(logF);
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        tvResults.setText(lastReport);
                        toast("تم تطبيق التحسينات ✅");
                    }
                });
            }
        });
    }

    public void onGame(View v) {
        if (!rootAvailable) { showNoRootInfo(); return; }
        startTask("🎮 جارٍ تفعيل وضع اللعب…");
        worker.execute(new Runnable() {
            @Override
            public void run() {
                List<String> log;
                try { log = RootCore.gameMode(); }
                catch (Throwable t) { log = null; }
                if (log == null) { log = new ArrayList<String>(); log.add("⛔ خطأ — تأكد من منح صلاحية su للتطبيق."); }
                final List<String> logF = log;
                lastReport = joinLines(logF);
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        tvResults.setText(lastReport);
                        toast("وضع اللعب مفعّل 🎮");
                    }
                });
            }
        });
    }

    public void onMonitor(View v) {
        if (monitoring) {
            monitoring = false;
            if (monitorThread != null) monitorThread.interrupt();
            tvStatus.setText("⏸️ المراقبة متوقفة");
            appendResult("\n⏸️ تم إيقاف المراقبة.");
            return;
        }
        monitoring = true;
        tvStatus.setText("📊 مراقبة حية — اضغط الزر مجددًا للإيقاف");
        appendResult("📊 بدأت المراقبة الحية لسيرفر فري فاير الأسرع…\n(اضغط الزر مرة أخرى للإيقاف)\n\n");
        monitorThread = new Thread(new Runnable() {
            @Override
            public void run() {
                NetCore.PingResult best;
                try { best = NetCore.bestServer(); }
                catch (Throwable t) { best = null; }
                String host = (best != null && best.host != null) ? best.host : "ff.garena.com";
                String nm = (best != null && best.name != null) ? best.name : "Garena";
                final String fHost = host, fName = nm;
                ui.post(new Runnable() {
                    @Override
                    public void run() { appendResult("🎯 المراقبة على: " + fName + " (" + fHost + ")\n"); }
                });
                int round = 1;
                double sum = 0; int n = 0; int lossSum = 0; int rounds = 0;
                while (monitoring && !Thread.currentThread().isInterrupted()) {
                    NetCore.PingResult r;
                    try { r = NetCore.tcpPing("", host, 10000, 3, 3000); }
                    catch (Throwable t) { break; }
                    rounds++;
                    final String line;
                    if (r.avg >= 0) {
                        sum += r.avg; n++;
                        lossSum += r.loss;
                        String emoji = r.avg < 60 ? "🟢" : r.avg < 100 ? "🟡" : r.avg < 160 ? "🟠" : "🔴";
                        line = String.format(Locale.US, "%s [%02d] %5.1f ms | جيتر %4.1f | فقد %d%%",
                                emoji, round, r.avg, r.jitter, r.loss);
                    } else {
                        lossSum += 100;
                        line = String.format(Locale.US, "🔴 [%02d] انقطاع!", round);
                    }
                    ui.post(new Runnable() {
                        @Override
                        public void run() { appendResult(line + "\n"); }
                    });
                    round++;
                    try { Thread.sleep(2000); } catch (InterruptedException e) { break; }
                }
                if (n > 0) {
                    final double fSum = sum; final int fN = n, fLoss = lossSum, fRounds = rounds;
                    ui.post(new Runnable() {
                        @Override
                        public void run() {
                            appendResult(String.format(Locale.US,
                                    "\n━━━━━━━━━━━━━━\n📈 الملخص: متوسط %.1f ms | فقد %d%% عبر %d جولة",
                                    fSum / fN, fLoss / fRounds, fRounds));
                        }
                    });
                }
            }
        });
        monitorThread.start();
    }

    public void onUndo(View v) {
        if (!rootAvailable) { showNoRootInfo(); return; }
        startTask("↩️ جارٍ استرجاع الإعدادات الافتراضية…");
        worker.execute(new Runnable() {
            @Override
            public void run() {
                List<String> log;
                try { log = RootCore.undo(); }
                catch (Throwable t) { log = null; }
                if (log == null) { log = new ArrayList<String>(); log.add("⛔ خطأ — تأكد من منح صلاحية su للتطبيق."); }
                final List<String> logF = log;
                lastReport = joinLines(logF);
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        tvResults.setText(lastReport);
                        toast("تم التراجع ✅");
                    }
                });
            }
        });
    }

    public void onCopy(View v) {
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            String txt = lastReport.isEmpty() ? tvResults.getText().toString() : lastReport;
            cm.setPrimaryClip(ClipData.newPlainText("FF-TurboNet", txt));
            toast("تم نسخ النتائج 📋");
        } catch (Throwable t) {
            toast("تعذّر النسخ");
        }
    }

    public void onShare(View v) {
        try {
            String txt = (lastReport.isEmpty() ? tvResults.getText().toString() : lastReport)
                    + "\n\n— أُنشئ بواسطة FF TurboNet ⚡ (أداة قانونية 100%)";
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("text/plain");
            i.putExtra(Intent.EXTRA_TEXT, txt);
            startActivity(Intent.createChooser(i, "مشاركة النتائج"));
        } catch (Throwable t) {
            toast("تعذّرت المشاركة");
        }
    }

    /* ============ أزرار التحسين بدون روت ============ */

    public void onNoRoot(View v) {
        StringBuilder sb = new StringBuilder();
        sb.append("🔧 التحسين الحقيقي بدون روت — اتبع بالترتيب:\n\n");
        sb.append("1️⃣ Private DNS (الأهم — يسرّع تحليل سيرفرات اللعبة):\n");
        sb.append("   اضغط زر 🌐 Private DNS بالأعلى ← سيفتح الإعداد\n");
        sb.append("   اختر \"اسم مضيف خاص\" واكتب:  dns.google\n");
        sb.append("   (أو 1dot1dot1dot1.cloudflare-dns.com)\n\n");
        sb.append("2️⃣ DNS ثابت للواي فاي:\n");
        sb.append("   اضغط زر 📶 إعدادات Wi-Fi ← اضغط مطولًا على شبكتك\n");
        sb.append("   ← تعديل ← خيارات متقدمة ← IP: ثابت\n");
        sb.append("   ← DNS 1: 1.1.1.1   DNS 2: 1.0.0.1\n\n");
        sb.append("3️⃣ استثناء البطارية (يمنع النظام من تقييد الشبكة):\n");
        sb.append("   اضغط زر 🔋 استثناء البطارية ← اختر \"غير مقيّد\"\n\n");
        sb.append("4️⃣ تقييد بيانات الخلفية لفري فاير:\n");
        sb.append("   اضغط زر 🚫 بيانات الخلفية ← أوقف \"بيانات الخلفية\"\n\n");
        sb.append("5️⃣ قبل اللعب: أغلق كل التطبيقات من قائمة المهام\n\n");
        sb.append("✅ هذه التحسينات حقيقية وتعمل بدون روت وبدون حاسوب.\n");
        sb.append("💡 الأهم هو Private DNS — جرّبه أولًا وقس الفرق بزر 📡 فحص البينق.\n");
        lastReport = sb.toString();
        tvResults.setText(lastReport);
    }

    public void onPrivateDns(View v) {
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("dns", "dns.google"));
            toast("نُسخ: dns.google — الصقه في خانة اسم المضيف");
        } catch (Throwable t) {}
        boolean opened = false;
        try {
            Intent i = new Intent("android.settings.PRIVATE_DNS_SETTINGS");
            startActivity(i);
            opened = true;
        } catch (Throwable t) {}
        if (!opened) {
            try {
                Intent i = new Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS);
                startActivity(i);
                opened = true;
            } catch (Throwable t) {}
        }
        StringBuilder sb = new StringBuilder();
        sb.append("🌐 Private DNS — أهم تحسين بدون روت\n\n");
        sb.append("الخطوات:\n");
        sb.append("1. اختر \"اسم مضيف خاص\" (Private DNS provider hostname)\n");
        sb.append("2. اكتب:  dns.google\n");
        sb.append("   (نسخته لك تلقائيًا — الصقها)\n");
        sb.append("3. اضغط حفظ\n\n");
        sb.append("بدائل سريعة:\n");
        sb.append("  • dns.google  (Google — الأسرع غالبًا)\n");
        sb.append("  • 1dot1dot1dot1.cloudflare-dns.com  (Cloudflare)\n");
        sb.append("  • dns.quad9.net  (Quad9)\n\n");
        sb.append("💡 بعد التطبيق: أعد فحص البينق بزر 📡 وقارن.\n");
        sb.append("⚠️ لو توقّف الإنترنت، ارجع لـ\"تلقائي\".\n");
        lastReport = sb.toString();
        tvResults.setText(lastReport);
    }

    public void onWifi(View v) {
        try {
            startActivity(new Intent(android.provider.Settings.ACTION_WIFI_SETTINGS));
        } catch (Throwable t) {
            try { startActivity(new Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS)); } catch (Throwable t2) {}
        }
        StringBuilder sb = new StringBuilder();
        sb.append("📶 ضبط DNS ثابت للواي فاي\n\n");
        sb.append("1. اضغط مطولًا على اسم شبكتك المتصلة\n");
        sb.append("2. اختر \"تعديل الشبكة\" أو \"إدارة إعدادات الشبكة\"\n");
        sb.append("3. افتح \"خيارات متقدمة\"\n");
        sb.append("4. غيّر \"إعدادات IP\" من DHCP إلى \"ثابت\"\n");
        sb.append("5. في خانة DNS 1 اكتب:  1.1.1.1\n");
        sb.append("   وفي DNS 2 اكتب:  1.0.0.1\n");
        sb.append("6. احفظ\n\n");
        sb.append("💡 هذا يقلل زمن تحليل أسماء سيرفرات اللعبة.\n");
        sb.append("⚠️ احفظ إعداداتك القديمة قبل التغيير للرجوع عند الحاجة.\n");
        lastReport = sb.toString();
        tvResults.setText(lastReport);
    }

    public void onBattery(View v) {
        boolean opened = false;
        try {
            Intent i = new Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
            startActivity(i);
            opened = true;
        } catch (Throwable t) {}
        if (!opened) {
            try { startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS)); } catch (Throwable t) {}
        }
        StringBuilder sb = new StringBuilder();
        sb.append("🔋 استثناء البطارية\n\n");
        sb.append("الخطوات:\n");
        sb.append("1. اضغط \"غير مقيّد\" أو \"عدم التحسين\"\n");
        sb.append("2. ابحث عن FF TurboNet وفري فاير وفعّلهما\n\n");
        sb.append("💡 يمنع النظام من تقييد الشبكة أثناء اللعب\n");
        sb.append("   (يقلل القفزات المفاجئة في البينق).\n");
        lastReport = sb.toString();
        tvResults.setText(lastReport);
    }

    public void onBgData(View v) {
        boolean opened = false;
        try {
            Intent i = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            i.setData(android.net.Uri.parse("package:com.dts.freefireth"));
            startActivity(i);
            opened = true;
        } catch (Throwable t) {}
        if (!opened) {
            try { startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS)); } catch (Throwable t) {}
        }
        StringBuilder sb = new StringBuilder();
        sb.append("🚫 تقييد بيانات الخلفية لفري فاير\n\n");
        sb.append("الخطوات:\n");
        sb.append("1. في صفحة التطبيق ← \"البيانات\" أو \"استخدام البيانات\"\n");
        sb.append("2. أوقف \"بيانات الخلفية\" (Background data)\n\n");
        sb.append("💡 يمنع اللعبة من استهلاك الشبكة في الخلفية\n");
        sb.append("   ويجعل الاتصال أثناء اللعب أكثر ثباتًا.\n");
        sb.append("⚠️ لو لم تُفتح صفحة اللعبة، افتحها يدويًا من:\n");
        sb.append("   الإعدادات ← التطبيقات ← فري فاير ← البيانات.\n");
        lastReport = sb.toString();
        tvResults.setText(lastReport);
    }

    public void onGameGuide(View v) {
        StringBuilder sb = new StringBuilder();
        sb.append("📖 دليل تقليل اللاق داخل فري فاير\n\n");
        sb.append("⚙️ إعدادات اللعبة:\n");
        sb.append("  • الرسومات: \"سلس\" (Smooth) — أهم إعداد للاق\n");
        sb.append("  • معدل الإطارات: \"عالي\" فقط لو جهازك قوي\n");
        sb.append("  • أوقف: الظلال، التأثيرات، الجودة العالية\n");
        sb.append("  • أوقف \"الرسومات عالية الدقة\"\n\n");
        sb.append("📶 الشبكة:\n");
        sb.append("  • استخدم 5GHz لو قريب من الراوتر، 2.4GHz لو بعيد\n");
        sb.append("  • لا تلعب والجهاز يشحن (يسخّن ويبطئ)\n");
        sb.append("  • أوقف التحميلات والتحديثات التلقائية\n\n");
        sb.append("📱 الجهاز:\n");
        sb.append("  • أغلق كل التطبيقات من قائمة المهام\n");
        sb.append("  • شغّل \"وضع عدم الإزعاج\"\n");
        sb.append("  • فرّغ مساحة (10%+ حرة)\n");
        sb.append("  • أعد تشغيل الهاتف قبل جلسة لعب طويلة\n\n");
        sb.append("✅ كل هذه إعدادات مشروعة 100% ولا علاقة لها بالحظر.\n");
        lastReport = sb.toString();
        tvResults.setText(lastReport);
    }

    /* ============ أدوات مساعدة ============ */

    private static String joinLines(List<String> lines) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            sb.append(lines.get(i)).append('\n');
        }
        return sb.toString();
    }

    /** آخر خطأ (في حالة فشل الفحص) — يُحفظ في متغير ثابت */
    private static Throwable lastErr = null;

    private static String t0() {
        // مساعد صغير: يُرجّع تمثيلًا نصيًا لآخر خطأ مسجّل
        return lastErr == null ? "غير معروف" : android.util.Log.getStackTraceString(lastErr);
    }

    private void startTask(String msg) {
        ui.post(new Runnable() {
            String m;
            @Override public void run() { tvResults.setText(m + "\n…"); tvStatus.setText("⏳ جارٍ العمل…"); }
            Runnable set(String msg) { m = msg; return this; }
        }.set(msg));
    }

    private void appendResult(final String s) {
        tvResults.append(s);
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    private void showNoRootInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append("📱 جهازك بدون روت — لا مشكلة!\n\n");
        sb.append("ما يزال يعمل الآن 100%:\n");
        sb.append("  ✅ 📡 فحص البينق (14 سيرفر حقيقي)\n");
        sb.append("  ✅ 🌐 اختبار DNS + إرشاد التغيير اليدوي\n");
        sb.append("  ✅ 📊 المراقبة الحية أثناء اللعب\n");
        sb.append("  ✅ 📋 النسخ والمشاركة\n\n");
        sb.append("للوصول للتحسينات العميقة اختر واحدًا:\n");
        sb.append("  1️⃣ موديول Magisk المرفق (تثبيت من تطبيق Magisk)\n");
        sb.append("     — يفعّل كل التحسينات تلقائيًا عند الإقلاع\n");
        sb.append("  2️⃣ أذونات ADB من الكمبيوتر (شبه-روت)\n\n");
        sb.append("💡 نصائح فورية بدون أي شيء:\n");
        sb.append("  • أغلق التطبيقات من قائمة المهام قبل اللعب\n");
        sb.append("  • اقترب من الراوتر أو استخدم 5GHz\n");
        sb.append("  • أوقف التحميلات والتحديثات التلقائية\n");
        sb.append("  • شغّل \"وضع عدم الإزعاج\" لمنع الإشعارات\n");
        sb.append("  • ثبّت الشبكة على 2.4GHz إذا كنت بعيدًا\n");
        lastReport = sb.toString();
        tvResults.setText(lastReport);
    }

    private String region(String host) {
        if (host.contains("garena.com.cdn") || host.contains("cloudfront")) return "MENA (الأقرب عربيًا)";
        if (host.startsWith("202.81.")) return "بنغلاديش";
        if (host.startsWith("103.247.")) return "سنغافورة";
        if (host.startsWith("125.212.")) return "إندونيسيا";
        if (host.contains("freefireind") || host.contains("packetgm")) return "الهند";
        return "سنغافورة";
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        monitoring = false;
        if (monitorThread != null) monitorThread.interrupt();
        worker.shutdownNow();
    }
}
