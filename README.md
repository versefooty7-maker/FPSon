# FPS Pulse

Real FPS overlay for Android games. No root, no Shizuku, no ADB.

## What it does
- Floating overlay: FPS, frame time, 1% low, FPS graph, battery %, charge/discharge current (mA),
  battery temperature, CPU temperature (when the phone exposes it), RAM usage
- Colour changes with performance (green / yellow / red) and the border turns red when the phone is hot
- Games tab: PLAY starts the overlay and opens the game in one tap
- Boost tab: RAM boost, heat status, cache cleaner shortcuts
- History tab: min / avg / max FPS, 1% low and max temperature of every session
- Settings: overlay size, opacity, compact mode, which rows to show, target FPS, heat warning level

## How the FPS is measured (it is real, not guessed)
The app asks for Android's screen-capture permission and mirrors the screen into a tiny hidden
buffer. Android only delivers a new image when the display really composes a new frame, so counting
those images gives the true on-screen frame rate of whatever is running. Nothing is recorded or saved.

Honest limits:
- Android asks for the capture permission every time you press START (system rule, cannot be skipped).
- A static screen shows 0 FPS because nothing is being drawn.
- The overlay redraws once per second, so the number can be off by about 1 FPS.
- It measures what reaches the display. It does not read the game's internal frame counter.

## How to test that it is not fake
1. Play a 30 fps video: the overlay should show about 30. Play a 60 fps video: about 60.
2. Open a static screen (home screen, nothing moving): it should drop to 0.
3. Scroll a long web page: the number should jump around with your finger.
4. Compare with Developer options > "Profile GPU rendering", or with `adb shell dumpsys SurfaceFlinger --latency <layer>`.

## Build the APK (no Android Studio needed)
1. Create a new GitHub repository and upload everything in this folder (keep the `.github` folder).
2. Open the **Actions** tab, run **Build APK** (it also runs on every push).
3. When it finishes, download the `FpsPulse-apk` artifact. Inside is the installable `.apk`.

Or with Android Studio: open this folder and press Run. Requires JDK 17, Android SDK 34.

## Install
Copy the APK to the phone, allow "install unknown apps", install. On first start grant:
"Display over other apps", notifications, then accept the screen-capture prompt.

Minimum Android 8.0 (API 26). Built and targeted for Android 14.
