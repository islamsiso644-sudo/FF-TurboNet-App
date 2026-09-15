package com.ff.turbonet;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * FF TurboNet — تطبيق تحسين الشبكة القانوني لفري فاير.
 * بدون مكتبات خارجية — كل شيء Java + Android SDK قياسي.
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
        setContentView(R.layout.activity_main);

        tvResults = findViewById(R.id.tvResults);
        tvStatus  = findViewById(R.id.tvStatus);
        tvResults.setMovementMethod(new ScrollingMovementMethod());

        // فحص الروت في الخلفية
        worker.execute(() -> {
            rootAvailable = RootCore.hasRoot();
            ui.post(() -> tvStatus.setText(rootAvailable
                    ? "🔓 روت مكتشف — التحسينات العميقة متاحة"
                    : "📱 بدون روت — الفحص والمراقبة والنصائح تعمل (التحسينات العميقة تحتاج روت أو موديول Magisk المرفق)"));
        });

        appendResult("مرحبًا! 👋\n\nFF TurboNet جاهز.\n" +
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
        worker.execute(() -> {
            List<NetCore.PingResult> results = NetCore.pingAll();
            StringBuilder sb = new StringBuilder();
            sb.append("══════════════════════════\n");
            sb.append("📡 نتائج فحص سيرفرات فري فاير\n");
            sb.append("══════════════════════════\n\n");
            int rank = 1;
            for (NetCore.PingResult r : results) {
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
            NetCore.PingResult best = results.isEmpty() ? null : results.get(0);
            if (best != null && best.avg > 0) {
                sb.append("🏆 الأسرع لك: ").append(best.name)
                  .append(String.format(Locale.US, " (%.1f ms)", best.avg)).append("\n");
                sb.append("💡 منطقة ").append(region(best.host)).append(" هي الأفضل لجهازك.\n");
                if (best.avg < 60) sb.append("🟢 بينق ممتاز — جاهز للعب!\n");
                else if (best.avg < 100) sb.append("🟡 بينق جيد.\n");
                else sb.append("🟠 جرّب تغيير DNS من زر DNS، وتأكد من قوة الواي فاي.\n");
            }
            lastReport = sb.toString();
            ui.post(() -> { tvResults.setText(lastReport); tvResults.scrollTo(0, 0); });
        });
    }

    public void onDns(View v) {
        startTask("🌐 جارٍ اختبار 6 خوادم DNS…");
        worker.execute(() -> {
            List<NetCore.DnsResult> results = NetCore.dnsAll();
            StringBuilder sb = new StringBuilder();
            sb.append("══════════════════════════\n");
            sb.append("🌐 نتائج اختبار DNS\n");
            sb.append("══════════════════════════\n\n");
            for (NetCore.DnsResult r : results) {
                if (r.resolved) {
                    sb.append(String.format(Locale.US, "🟢 %-12s (%s)\n    زمن الاستجابة: %5.1f ms\n\n",
                            r.name, r.ip, r.ms));
                } else {
                    sb.append(String.format(Locale.US, "🔴 %-12s (%s)\n    لا يستجيب\n\n", r.name, r.ip));
                }
            }
            NetCore.DnsResult fastest = results.isEmpty() ? null : results.get(0);
            if (fastest != null && fastest.resolved) {
                sb.append("🏆 أسرع DNS: ").append(fastest.name)
                  .append(" (").append(fastest.ip).append(")\n\n");
                sb.append("💡 لتغيير DNS على جهازك (بدون روت):\n");
                sb.append("   الإعدادات ← الشبكة ← Wi-Fi ← اضغط مطولًا على شبكتك\n");
                sb.append("   ← تعديل ← خيارات متقدمة ← IP: ثابت\n");
                sb.append("   ← DNS 1: ").append(fastest.ip).append("\n");
                sb.append("   ← DNS 2: 1.0.0.1\n\n");
            }
            lastReport = sb.toString();
            ui.post(() -> { tvResults.setText(lastReport); tvResults.scrollTo(0, 0); });
        });
    }

    public void onTune(View v) {
        if (!rootAvailable) {
            showNoRootInfo();
            return;
        }
        startTask("🚀 جارٍ تطبيق تحسينات TCP…");
        worker.execute(() -> {
            List<String> log = RootCore.applyTuning();
            lastReport = String.join("\n", log);
            ui.post(() -> { tvResults.setText(lastReport); toast("تم تطبيق التحسينات ✅"); });
        });
    }

    public void onGame(View v) {
        if (!rootAvailable) {
            showNoRootInfo();
            return;
        }
        startTask("🎮 جارٍ تفعيل وضع اللعب…");
        worker.execute(() -> {
            List<String> log = RootCore.gameMode();
            lastReport = String.join("\n", log);
            ui.post(() -> { tvResults.setText(lastReport); toast("وضع اللعب مفعّل 🎮"); });
        });
    }

    public void onMonitor(View v) {
        if (monitoring) {
            monitoring = false;
            if (monitorThread != null) monitorThread.interrupt();
            tvStatus.setText(monitoring ? "" : "⏸️ المراقبة متوقفة");
            appendResult("⏸️ تم إيقاف المراقبة.");
            return;
        }
        monitoring = true;
        tvStatus.setText("📊 مراقبة حية — اضغط الزر مجددًا للإيقاف");
        appendResult("📊 بدأت المراقبة الحية لسيرفر فري فاير الأسرع…\n(اضغط الزر مرة أخرى للإيقاف)\n\n");
        monitorThread = new Thread(() -> {
            NetCore.PingResult best = NetCore.bestServer();
            String host = (best != null && best.host != null) ? best.host : "ff.garena.com";
            String nm = (best != null && best.name != null) ? best.name : "Garena";
            ui.post(() -> appendResult("🎯 المراقبة على: " + nm + " (" + host + ")\n"));
            int round = 1;
            double sum = 0; int n = 0; int loss = 0; int rounds = 0;
            while (monitoring) {
                NetCore.PingResult r = NetCore.tcpPing("", host, 10000, 3, 3000);
                rounds++;
                final String line;
                if (r.avg >= 0) {
                    sum += r.avg; n++;
                    loss += r.loss;
                    String emoji = r.avg < 60 ? "🟢" : r.avg < 100 ? "🟡" : r.avg < 160 ? "🟠" : "🔴";
                    line = String.format(Locale.US, "%s [%02d] %5.1f ms | جيتر %4.1f | فقد %d%%",
                            emoji, round, r.avg, r.jitter, r.loss);
                } else {
                    loss += 100;
                    line = String.format(Locale.US, "🔴 [%02d] انقطاع!", round);
                }
                ui.post(() -> appendResult(line + "\n"));
                round++;
                try { Thread.sleep(2000); } catch (InterruptedException e) { break; }
            }
            if (n > 0) {
                final String summary = String.format(Locale.US,
                        "\n━━━━━━━━━━━━━━\n📈 الملخص: متوسط %.1f ms | فقد %d%% عبر %d جولة",
                        sum / n, loss / rounds, rounds);
                ui.post(() -> appendResult(summary));
            }
        });
        monitorThread.start();
    }

    public void onUndo(View v) {
        if (!rootAvailable) {
            showNoRootInfo();
            return;
        }
        startTask("↩️ جارٍ استرجاع الإعدادات الافتراضية…");
        worker.execute(() -> {
            List<String> log = RootCore.undo();
            lastReport = String.join("\n", log);
            ui.post(() -> { tvResults.setText(lastReport); toast("تم التراجع ✅"); });
        });
    }

    public void onCopy(View v) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("FF-TurboNet", lastReport.isEmpty()
                ? tvResults.getText().toString() : lastReport));
        toast("تم نسخ النتائج 📋");
    }

    public void onShare(View v) {
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_TEXT, (lastReport.isEmpty()
                ? tvResults.getText().toString() : lastReport)
                + "\n\n— أُنشئ بواسطة FF TurboNet ⚡ (أداة قانونية 100%)");
        startActivity(Intent.createChooser(i, "مشاركة النتائج"));
    }

    /* ============ أدوات مساعدة ============ */

    private void startTask(String msg) {
        ui.post(() -> { tvResults.setText(msg + "\n…"); tvStatus.setText("⏳ جارٍ العمل…"); });
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
