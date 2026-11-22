# Build the APK without Android Studio (GitHub does it)

1. Make a free account at github.com.
2. Click + (top right) > New repository. Name: mcserver. Create it.
3. Click "uploading an existing file". On your PC, drag the CONTENTS of this folder
   (app, .github, build.gradle.kts, settings.gradle.kts, gradle.properties) into the page.
   Make sure the folders keep their structure. Click Commit changes.
   If .github won't upload: Add file > Create new file, type
   .github/workflows/build.yml as the name and paste the file's text.
4. Click the Actions tab. "Build APK" runs by itself (about 3-6 minutes). Wait for the green tick.
5. Click the finished run > Artifacts > McServer-apk. Download, unzip, send app-debug.apk to your phone.
6. Install it (allow "install unknown apps").

# On the phone (no PC)
1. Install Termux from F-Droid, open it, run:
   mkdir -p ~/.termux && echo "allow-external-apps=true" >> ~/.termux/termux.properties
2. Close Termux fully and reopen it once.
3. Settings > Apps > Termux > Battery: Unrestricted; allow Display over other apps.
4. Open MC Server app > tick EULA > Install > Start > playit link.
