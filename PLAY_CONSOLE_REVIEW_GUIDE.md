# Google Play Console Submission & Review Guide for Blockify

Use this guide when uploading your app to the **Google Play Console**. Copy and paste the provided answers into the respective Play Console declaration forms to ensure smooth, 100% policy-compliant approval without rejections.

---

## 1. App Access (Reviewer Test Credentials)
Google Play testers must be able to log in to review all features.
* **Go to**: *Policy and programs > App content > App access*
* **Select**: *"All or some functionality is restricted"*
* **Click**: *"Add new instructions"*
  * **Name**: `Test Account Access`
  * **Username / Email**: `tester@blockify.app` (or any valid email registered in Firebase)
  * **Password**: `TestPass123!`
  * **Explanation / Notes for Tester**:
    ```text
    Please log in using the credentials provided above or use the "Continue with Email" sign-in option. Once inside, you can access the full feature set including the dashboard, trigger settings, and the voluntary Accessibility Service protection.
    ```

---

## 2. Accessibility API Declaration Form
Google Play strictly reviews all apps using `BIND_ACCESSIBILITY_SERVICE`.
* **Go to**: *Policy and programs > App content > Accessibility API declaration*
* **Select**: *"Yes, my app uses the Accessibility API"*
* **Primary Purpose**: Select **Parental Control / Device & Screen Time Management / Focus Tool**
* **Prominent In-App Disclosure Confirmation**:
  * Check the box confirming: *"My app includes a prominent in-app disclosure that is shown before the user is directed to the device's accessibility settings."*
* **Justification / Explanation text to copy-paste**:
  ```text
  Blockify uses the Android AccessibilityService API solely for real-time adult content filtering, impulse prevention, and digital wellbeing. 

  Active window titles and browser URL bars are inspected 100% locally on-device in real-time to match against the user's active blocklist and trigger keywords. 

  Blockify does not collect, record, or transmit any screen text, keystrokes, passwords, or personal communications. All processing is strictly executed on-device, and zero accessibility data is sent to external servers or third-party advertising networks. 

  The app presents a prominent in-app disclosure dialog with explicit "Agree & Turn On" and "Deny" buttons before the user is navigated to system settings.
  ```
* **Video Demonstration Requirement**:
  * Record a 30 to 45 second screen recording showing:
    1. Opening Blockify.
    2. Tapping the "Accessibility Service" card.
    3. The Prominent In-App Disclosure dialog appearing with clear "Agree & Turn On" and "Deny" buttons.
    4. Tapping "Agree & Turn On" and being directed to Android Accessibility Settings.
  * Upload this video to YouTube as **Unlisted** and paste the link in the Play Console.

---

## 3. Package Visibility (`QUERY_ALL_PACKAGES`) Declaration
Google Play requires a declaration when an app declares `QUERY_ALL_PACKAGES`.
* **Go to**: *Policy and programs > App content > Broad package visibility*
* **Permitted Category**: Select **App functionality / Parental Control / App Blocking**
* **Justification text to copy-paste**:
  ```text
  Blockify is a focus shield and content blocker application. The QUERY_ALL_PACKAGES permission is required for the core app blocking functionality, allowing users to view their installed apps and select which distracting apps (e.g. social media, web browsers) should be blocked or restricted during focus sessions. Without this permission, the user cannot choose and configure app-level protections.
  ```

---

## 4. Device Administration (Anti-Uninstall Protection)
Google Play monitors Device Admin to ensure apps are not classified as unwanted software.
* **Reviewer Notes / Description**:
  ```text
  Device Administration in Blockify is an optional self-discipline and parental control protection designed to prevent impulsive deactivation or uninstallation during vulnerable moments.

  Users have full control and can easily disable or deactivate the protection at any time using the in-app Emergency Stop feature or standard Android Device Administration settings.
  ```

---

## 5. Target Audience & Content Rating
* **Target Age Group**: Select **18 and over only** (Do **NOT** select children under 13 or families, as that introduces COPPA and strict family ad policies).
* **Content Rating Questionnaire**:
  * Blockify does not generate native offensive content, it blocks it. Answer the IARC questionnaire truthfully as a utility/tool application.

---

## 6. Data Safety Section
In the **Data Safety** form:
* **Data Collected**:
  * **User Account Info (Email, User ID)**:
    * Purpose: Account management, authentication, and subscription restoration.
    * Shared with third parties? **No**.
  * **App Performance / Crash Logs**:
    * Collected? **Yes** (via standard Firebase Crashlytics if used, or No if deactivated).
* **Data NOT Collected**:
  * Screen text, URLs, browsing history, keystrokes, messages, or files: **NOT COLLECTED** (Processed locally only).

---

## 7. Privacy Policy URL
Host your `app/src/main/assets/privacy_policy.html` on a public URL:
1. **GitHub Pages (Free)**:
   * Create a free repository on GitHub (e.g. `blockify-privacy`).
   * Put `index.html` (copy of `privacy_policy.html`) into it.
   * Go to *Settings > Pages* and enable GitHub Pages.
   * Your URL will be `https://<your-username>.github.io/blockify-privacy/`.
2. **Firebase Hosting (Free)**:
   * Run `firebase deploy --only hosting` to host `privacy_policy.html` directly under your `blockify-10b16.web.app/privacy.html` domain.
* Paste this live public link into the **Store Listing > Privacy Policy** field.

---

## 8. Final Checklist Before Releasing
- [x] Production AdMob App ID set in `AndroidManifest.xml` (`ca-app-pub-1169067879526368~9189357169`).
- [x] Prominent Accessibility Disclosure with "Agree" & "Deny" buttons added in `AccessibilityDisclosureDialog.kt`.
- [x] Device Admin description clearly states optional self-discipline & Emergency Stop support.
- [ ] Provide tester credentials in Play Console *App Access*.
- [ ] Record and attach 40s Unlisted YouTube video for Accessibility API declaration.
- [ ] Select 18+ Target Audience.
