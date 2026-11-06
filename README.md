# Split Bill

Split Bill is an Android app for splitting Malaysian restaurant receipts. It shows each diner's share in ringgit (RM) and tracks what the person who paid the restaurant is still owed.

## Build and run

1. Open this repository in Android Studio.
2. Install the Android SDK Platform 37 and SDK Build Tools 36.0.0 from SDK Manager if prompted.
3. Let Gradle sync, then select **Run** with an Android 16 (API 36) or later device or emulator.

On Windows, you can also run `gradlew.bat :app:assembleDebug` from the project directory. On macOS or Linux, use `./gradlew :app:assembleDebug`. The debug APK is written to `app/build/outputs/apk/debug/`.

## Using the app

1. Put the person who paid the restaurant first in the diner list. Add the items and assign each one to the diners who ordered it.
2. Enter SST and the restaurant's service charge as exact receipt amounts or percentage rates. Use the receipt adjustment for any rounding difference.
3. Tap **Calculate split**. Record full or partial payments as they arrive.
4. Tap **Save bill** to keep the receipt on your device and reopen it from **Bill history**.
5. Tap **Share result** to send the breakdown through WhatsApp, email, or another compatible app installed on your device.

Shared items are divided equally among the selected diners. SST and service charges are allocated according to each diner's food subtotal. Whole-sen rounding ensures that the individual shares add up to the receipt total. The app stores bills and payment records locally.

## Project files

- `app/src/main/java/my/splitmakan/MainActivity.java` contains the screens, local bill history, and sharing flow.
- `app/src/main/java/my/splitmakan/BillMath.java` handles ringgit formatting and whole-sen allocation.
- `app/src/main/AndroidManifest.xml` and `app/src/main/res/` define the Android app and its resources.

The APK has been tested on an Android 17 emulator for installation, upgrades, saving and reopening bills, recording payments, and opening the Android share menu. The minimum supported version is Android 16.
