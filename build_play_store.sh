#!/usr/bin/env bash
set -e

echo "=========================================================="
echo "   Blockify Play Store AAB & APK Build Script"
echo "=========================================================="

WORKSPACE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$WORKSPACE_DIR"

# 1. Check / Install Java 17
if ! command -v javac &> /dev/null; then
    echo ">> Java not found. Installing OpenJDK 17..."
    if command -v sudo &> /dev/null; then
        sudo apt-get update -y && sudo apt-get install -y openjdk-17-jdk
    else
        apt-get update -y && apt-get install -y openjdk-17-jdk
    fi
fi

# 2. Check / Setup Android SDK
# Reconcile existing SDK environment variables to avoid Gradle mismatch error
if [ -n "$ANDROID_SDK_ROOT" ] && [ -d "$ANDROID_SDK_ROOT" ]; then
    export ANDROID_HOME="$ANDROID_SDK_ROOT"
elif [ -n "$ANDROID_HOME" ] && [ -d "$ANDROID_HOME" ]; then
    export ANDROID_SDK_ROOT="$ANDROID_HOME"
fi

# If neither is set or exists, download and install Android SDK
if [ -z "$ANDROID_HOME" ] || [ ! -d "$ANDROID_HOME" ]; then
    DEFAULT_SDK_DIR="$HOME/android-sdk"
    if [ ! -d "$DEFAULT_SDK_DIR/cmdline-tools/latest" ]; then
        echo ">> Setting up Android Commandline Tools in $DEFAULT_SDK_DIR..."
        mkdir -p "$DEFAULT_SDK_DIR/cmdline-tools"
        cd "$DEFAULT_SDK_DIR/cmdline-tools"
        curl -sS -o cmdline-tools.zip "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"
        unzip -q cmdline-tools.zip
        mv cmdline-tools latest
        rm -f cmdline-tools.zip
        cd "$WORKSPACE_DIR"
    fi
    export ANDROID_HOME="$DEFAULT_SDK_DIR"
    export ANDROID_SDK_ROOT="$DEFAULT_SDK_DIR"
    export PATH="$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools"

    # Accept licenses and install platform 36
    echo ">> Installing Android SDK platforms;android-36 and build-tools;36.0.0..."
    yes | "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --licenses > /dev/null 2>&1 || true
    "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" "platforms;android-36" "build-tools;36.0.0" > /dev/null 2>&1 || true
fi

# Ensure both variables point to the exact same path
export ANDROID_SDK_ROOT="$ANDROID_HOME"
echo ">> Android SDK verified at: $ANDROID_HOME"

# 3. Create Upload Keystore if not present
if [ ! -f "$WORKSPACE_DIR/my-upload-key.jks" ]; then
    echo ">> Creating upload keystore (my-upload-key.jks) for Google Play Console..."
    keytool -genkeypair -v \
        -keystore "$WORKSPACE_DIR/my-upload-key.jks" \
        -alias upload \
        -keyalg RSA \
        -keysize 2048 \
        -validity 10000 \
        -storepass blockifyupload123 \
        -keypass blockifyupload123 \
        -dname "CN=Blockify, OU=Mobile, O=Blockify, L=City, S=State, C=US"
    echo ">> Keystore created successfully!"
else
    echo ">> Existing upload keystore found at: $WORKSPACE_DIR/my-upload-key.jks"
fi

# 4. Make gradlew executable
chmod +x ./gradlew 2>/dev/null || true

# 5. Build Release AAB (for Google Play Console) and APKs
echo ">> Building Release AAB (for Google Play Console Production Review)..."
if [ -f "./gradlew" ]; then
    GRADLE_CMD="./gradlew"
else
    GRADLE_CMD="gradle"
fi

STORE_PASSWORD=blockifyupload123 KEY_PASSWORD=blockifyupload123 $GRADLE_CMD :app:bundleRelease :app:assembleRelease :app:assembleDebug --no-daemon

# 6. Copy outputs to PLAY_STORE_OUTPUTS folder
OUTPUT_DIR="$WORKSPACE_DIR/PLAY_STORE_OUTPUTS"
mkdir -p "$OUTPUT_DIR"

if [ -f "$WORKSPACE_DIR/app/build/outputs/bundle/release/app-release.aab" ]; then
    cp -f "$WORKSPACE_DIR/app/build/outputs/bundle/release/app-release.aab" "$OUTPUT_DIR/app-release.aab"
fi

if [ -f "$WORKSPACE_DIR/app/build/outputs/apk/release/app-release.apk" ]; then
    cp -f "$WORKSPACE_DIR/app/build/outputs/apk/release/app-release.apk" "$OUTPUT_DIR/app-release.apk"
fi

if [ -f "$WORKSPACE_DIR/app/build/outputs/apk/debug/app-debug.apk" ]; then
    cp -f "$WORKSPACE_DIR/app/build/outputs/apk/debug/app-debug.apk" "$OUTPUT_DIR/app-debug.apk"
fi

echo ""
echo "=========================================================="
echo "   BUILD COMPLETE! Files ready in PLAY_STORE_OUTPUTS"
echo "=========================================================="
ls -lh "$OUTPUT_DIR"
echo ""
echo "1. GOOGLE PLAY CONSOLE: PLAY_STORE_OUTPUTS/app-release.aab"
echo "2. DIRECT DEVICE INSTALL: PLAY_STORE_OUTPUTS/app-release.apk"
echo "3. DEBUG TEST APK: PLAY_STORE_OUTPUTS/app-debug.apk"
echo "=========================================================="
