#!/bin/bash
# ═══════════════════════════════════════════════
# FF TurboNet — سكريبت بناء APK يدوي (بدون Gradle)
# ═══════════════════════════════════════════════
set -e

SDK=/opt/android-sdk
BT=$SDK/build-tools/34.0.0
PLATFORM=$SDK/platforms/android-34/android.jar
APP=/workspace/ff-turbonet-app
BUILD=$APP/build

echo "── [1/6] تنظيف + تجهيز مجلد البناء"
rm -rf "$BUILD"
mkdir -p "$BUILD/compiled" "$BUILD/dex" "$BUILD/apk"

echo "── [2/6] aapt2 compile — تجميع الموارد"
find "$APP/app/res" -name "*.xml" > "$BUILD/res_list.txt"
"$BT/aapt2" compile --dir "$APP/app/res" -o "$BUILD/compiled/res.zip"

echo "── [3/6] aapt2 link — ربط الموارد بالمانيفست"
"$BT/aapt2" link \
    -o "$BUILD/apk/base.apk" \
    -I "$PLATFORM" \
    --manifest "$APP/app/AndroidManifest.xml" \
    --java "$BUILD/gen" \
    --min-sdk-version 24 \
    --target-sdk-version 34 \
    --version-code 1 \
    --version-name "1.0" \
    "$BUILD/compiled/res.zip"

echo "── [4/6] javac — ترجمة Java"
mkdir -p "$BUILD/classes"
find "$APP/app/java" -name "*.java" > "$BUILD/java_list.txt"
find "$BUILD/gen" -name "*.java" >> "$BUILD/java_list.txt" 2>/dev/null || true
javac -source 8 -target 8 \
    -bootclasspath "$PLATFORM" \
    -classpath "$PLATFORM:$APP/stubs/core-lambda-stubs.jar" \
    -d "$BUILD/classes" \
    @"$BUILD/java_list.txt" 2>&1 | grep -vE "^(Note:|warning:)" || true

# التحقق من الترجمة
if [ ! -f "$BUILD/classes/com/ff/turbonet/MainActivity.class" ]; then
    echo "ERROR: فشل javac — MainActivity.class غير موجود!"
    javac -source 8 -target 8 -bootclasspath "$PLATFORM" -classpath "$PLATFORM:$APP/stubs/core-lambda-stubs.jar" -d "$BUILD/classes" @"$BUILD/java_list.txt"
    exit 1
fi
echo "     ✅ الترجمة نجحت"

echo "── [5/6] d8 — تحويل إلى dex"
find "$BUILD/classes" -name "*.class" > "$BUILD/class_list.txt"
"$BT/d8" \
    --release \
    --min-api 24 \
    --lib "$PLATFORM" \
    --output "$BUILD/dex" \
    $(cat "$BUILD/class_list.txt")
if [ ! -f "$BUILD/dex/classes.dex" ]; then
    echo "ERROR: فشل d8!"
    exit 1
fi
echo "     ✅ dex جاهز"

echo "── [6/6] تجميع + توقيع APK"
cd "$BUILD"
cp apk/base.apk unsigned.apk
cd dex && zip -j ../unsigned.apk classes.dex && cd ..
"$BT/zipalign" -f 4 unsigned.apk aligned.apk

# توليد مفتاح توقيع (إن لم يوجد)
if [ ! -f "$APP/turbonet.keystore" ]; then
    keytool -genkeypair -v \
        -keystore "$APP/turbonet.keystore" \
        -alias turbonet \
        -keyalg RSA -keysize 2048 -validity 10000 \
        -storepass turbonet2024 \
        -keypass turbonet2024 \
        -dname "CN=FF TurboNet, OU=Net, O=TurboNet, L=Cairo, C=EG" \
        2>&1 | tail -2
fi

"$BT/apksigner" sign \
    --ks "$APP/turbonet.keystore" \
    --ks-key-alias turbonet \
    --ks-pass pass:turbonet2024 \
    --key-pass pass:turbonet2024 \
    --out "$APP/FF-TurboNet.apk" \
    aligned.apk

echo "── التحقق النهائي"
"$BT/apksigner" verify --print-certs "$APP/FF-TurboNet.apk" | head -4
ls -lh "$APP/FF-TurboNet.apk"
echo "BUILD-DONE ✅"
