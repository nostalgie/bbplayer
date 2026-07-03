#!/usr/bin/env bash
# Deploy KidsVideoPlayer to a connected Android device in kiosk mode.
# Target device: Honor 50 Lite (MagicOS 7.1, Android 13) or any API 26+ device.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
PKG="com.dima.kidsvideoplayer"
ADMIN="$PKG/.admin.MyDeviceAdminReceiver"
ACTIVITY="$PKG/.MainActivity"

cd "$ROOT"

echo "==> Building debug APK..."
./gradlew assembleDebug

if ! adb get-state >/dev/null 2>&1; then
    echo ""
    echo "ERROR: No device connected."
    echo "Connect Honor 50 Lite via USB and enable USB debugging:"
    echo "  Settings → About phone → tap Build number 7×"
    echo "  Settings → Developer options → USB debugging"
    exit 1
fi

echo "==> Device: $(adb shell getprop ro.product.model 2>/dev/null || echo unknown)"
echo "    Android: $(adb shell getprop ro.build.version.release 2>/dev/null || echo unknown)"
echo "    Manufacturer: $(adb shell getprop ro.product.manufacturer 2>/dev/null || echo unknown)"

echo "==> Installing APK..."
adb install -r "$APK"

echo "==> Setting Device Owner (requires no accounts on device)..."
if adb shell dpm set-device-owner "$ADMIN" 2>&1; then
    echo "    Device Owner set successfully."
else
    echo ""
    echo "WARNING: Could not set Device Owner."
    echo "  - Remove all accounts (Google, Honor ID) or factory reset"
    echo "  - Or enable Screen pinning: Settings → Security → Screen pinning"
    echo "  Continuing with install only..."
fi

echo "==> Launching app..."
adb shell am start -n "$ACTIVITY"

echo ""
echo "Done. Expected behavior:"
echo "  - App auto-starts in full kiosk (Lock Task + Device Owner policies)"
echo "  - Parent access: long press gear icon (top-right) 3 seconds"
echo "  - Full de-kiosk: parent screen → 'Снять киоск' → PIN 1234"
echo "  - Emergency ADB exit:"
echo "      adb shell dpm remove-active-admin $ADMIN"
echo "      adb uninstall $PKG"
echo "  - Grant 'All files access' if prompted (Android 13+)"
