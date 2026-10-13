# RecoveryShield — Voluntary On-Device Recovery & Protection System

RecoveryShield is a native Android application engineered to help users voluntarily quit pornography and sexually explicit content addiction. Built with a **privacy-first, local-detection, backend-first architecture**, RecoveryShield empowers users to uphold commitments they have chosen for themselves without shaming, external tracking, or privacy invasion.

---

## 1. System Architecture Overview

The system operates strictly on-device using a modular layered architecture:

```
+-------------------------------------------------------------------------+
|                              Presentation Layer                         |
|   MainActivity (MainScreen.kt)  |  InterventionActivity  |  Test Harness|
+------------------------------------+------------------------------------+
                                     |
                                     v
+-------------------------------------------------------------------------+
|                               Domain Layer                              |
|   ProtectionStateMachine  |  DetectionPolicy  |  PermissionOrchestrator |
|   InterventionController  |  DisableProtectionManager  |  CreditManager |
+------------------------------------+------------------------------------+
                                     |
                                     v
+-------------------------------------------------------------------------+
|                             Protection Core                             |
|   RecoveryAccessibilityService  ->  EventNormalizer  ->  Classifier    |
|   (DeterministicRuleDetector: Lexical + Contextual + Density Signals)   |
+------------------------------------+------------------------------------+
                                     |
                                     v
+-------------------------------------------------------------------------+
|                                Data Layer                               |
|   RecoveryDatabase (Room)  |  SettingsRepository  |  RewardLedger       |
|   AuditLogDao  |  DisableRequestDao  |  FalsePositiveDao                |
+-------------------------------------------------------------------------+
```

---

## 2. Protection State Machine Diagram

The protection lifecycle is managed by a deterministic state machine (`ProtectionStateMachine.kt`):

```
                        +-------------+
                        |  FIRST_RUN  |
                        +------+------+
                               |
                               v
                       +---------------+
                       | SETUP_REQUIRED|
                       +-------+-------+
                               |
            +------------------+------------------+
            | (Permissions Missing)               | (Permissions Granted)
            v                                     v
  +---------------------+               +--------------------+
  | PERMISSION_REQUIRED |<------------->| READY_BUT_INACTIVE |
  +----------+----------+               +---------+----------+
             |                                    | (Voluntary Activate)
             |                                    v
             |                              +------------+
             |                     +------->|   ACTIVE   |<-------+
             |                     |        +-----+------+        |
             |                     |              |               |
             |       (Navigation / |              | (Triggered)   | (Cancel)
             |            Dismiss) |              v               |
             |                     |      +--------------+        |
             |                     +------+ INTERVENTION |        |
             |                            +--------------+        |
             |                                                    |
             | (Cooldown Requested)                               |
             +-------------------->+-----------------+            |
                                   | COOLDOWN_ACTIVE |------------+
                                   +--------+--------+
                                            | (2-Hour Cooldown Expires)
                                            v
                                +----------------------+
                                | DISABLE_CONFIRMATION |
                                +-----------+----------+
                                            | (Confirmed by user)
                                            v
                                      +----------+
                                      | DISABLED |
                                      +----+-----+
                                           | (Re-enable)
                                           +--------> READY_BUT_INACTIVE

* Any state when accessibility is removed unexpectedly: Transitions to DEGRADED.
* From DEGRADED: Transitions back to ACTIVE once permission is restored.
```

---

## 3. Permission Matrix

| Permission | Category | Purpose | Fallback / Graceful Degradation |
| :--- | :--- | :--- | :--- |
| `BIND_ACCESSIBILITY_SERVICE` | Accessibility Service | Observes window content locally to detect explicit content indicators and perform compliant navigation (`GLOBAL_ACTION_BACK` / `GLOBAL_ACTION_HOME`). | App transitions to `DEGRADED`. Falsely reporting active protection is strictly prevented. |
| `SYSTEM_ALERT_WINDOW` | Overlay Window | Displays the 5-second countdown intervention screen over triggering content. | Falls back to launching `InterventionActivity` as a standard task or executing immediate global navigation. |
| `POST_NOTIFICATIONS` | Runtime (API 33+) | Informs the user of cooling-off countdown status and completion. | Cooldown runs locally in persistent database; user can check timer inside the app. |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Special Battery Policy | Prevents aggressive OEM task killers from terminating the background service. | System warns user of possible delayed service behavior on OEMs like Xiaomi/Huawei. |
| `RECEIVE_BOOT_COMPLETED` | Normal (Install-time) | Restores persisted protection state and reconciles cooldowns on device reboot. | State is checked and reconciled upon first application launch. |

---

## 4. Threat & Privacy Model

### What is Processed Locally
- Window title, package name, and interactive text fragments on the current screen.
- Unicode NFKD sanitization, deobfuscation, whole-token regex matching.
- Sliding-window trigger density counts.

### What is NEVER Collected or Uploaded
- **Zero Cloud Network Calls**: The detection pipeline has zero internet requirements or cloud endpoints.
- **No Remote OCR / LLMs**: No images, screen buffers, or texts leave the device.
- **No Screenshots**: Screenshots are never captured, compressed, or stored.
- **No Passwords or Private Chats**: Input fields flagged with `isPassword` are skipped at the accessibility traversal layer. Private communication apps are preserved without wholesale blocking.
- **No Browsing History Stored**: Raw URLs and search queries are discarded after local token classification.

### Logging Integrity
- Production logs use strictly enumerated, privacy-safe codes (`SafeEventCode`), such as `HIGH_CONFIDENCE_INTERVENTION`, `COOLDOWN_STARTED`, and `FALSE_POSITIVE_REPORTED`.
- Explicit keywords and sensitive screen texts are strictly forbidden in logs and release builds.

---

## 5. Detection Pipeline Flow

```
[Screen Content Change]
         |
         v
[RecoveryAccessibilityService]
         |
         v  (Debounce: 250ms threshold; filter to window state & content changes)
[EventNormalizer]
         |  (Unicode NFKD, strip accents, skip password fields, max 25 nodes, max depth 4)
         v
[NormalizedUiEvent]
         |
         +------------------------+------------------------+
         |                        |                        |
         v                        v                        v
 [Signal Layer A:         [Signal Layer B:         [Signal Layer C:
     Lexical]                Contextual]           Repetition Density]
  Deobfuscate text       Check package name       Sliding window check
  Whole-word regex       Suppression filters:     Multiple recent hits
  Boundary matching      - Recovery groups        boost confidence
  Category scoring       - Medical / Clinical     by up to +0.20
                         - Settings
         |                        |                        |
         +------------------------+------------------------+
                                  |
                                  v
                    [Detection Decision Engine]
                                  |
         +------------------------+------------------------+
         |                                                 |
         v                                                 v
(Confidence >= 0.85)                               (Confidence < 0.85)
HIGH_CONFIDENCE_TRIGGER                            POSSIBLE_TRIGGER / NO_ACTION
         |                                                 |
         v                                                 v
[InterventionController]                          (No aggressive block; logged
 - 10s loop suppression window                     safely for diagnostic density)
 - 5s countdown timer
 - Compliant Back/Home navigation
```

---

## 6. Test Matrix & Acceptance Verification

| Component | Test File | Scenarios Covered |
| :--- | :--- | :--- |
| **State Machine** | `ProtectionStateMachineTest.kt` | Legal lifecycle transitions, invalid transition rejection, cooldown workflow, permission revocation degradation, permission restoration recovery. |
| **Detection Engine** | `DeterministicRuleDetectorTest.kt` | Direct explicit content, adversarial spacing (`p o r n`), leetspeak (`p0rn0`, `h4rdc0re`), benign substring isolation (`corner`, `hornet`, `corn`), recovery context suppression, clinical medical context suppression. |
| **Synthetic Harness** | `SyntheticDetectorHarnessTest.kt` | Benchmark across 8 standardized fixtures verifying 100% precision and 100% recall. |
| **Event Sanitizer** | `EventNormalizerTest.kt` | Unicode decomposition, diacritics stripping, blank/null handling. |
| **Screenshot Verification** | `GreetingScreenshotTest.kt` | Roborazzi visual regression test for the primary protection card. |

---

## 7. Exact File Paths for Important Classes

### Domain Models & State
- `/app/src/main/java/com/example/recoveryshield/domain/model/ProtectionState.kt`
- `/app/src/main/java/com/example/recoveryshield/domain/model/PermissionState.kt`
- `/app/src/main/java/com/example/recoveryshield/domain/model/DetectionModels.kt`
- `/app/src/main/java/com/example/recoveryshield/domain/model/InterventionModels.kt`
- `/app/src/main/java/com/example/recoveryshield/domain/model/CooldownModels.kt`
- `/app/src/main/java/com/example/recoveryshield/domain/model/CreditModels.kt`
- `/app/src/main/java/com/example/recoveryshield/domain/model/DiagnosticsModels.kt`
- `/app/src/main/java/com/example/recoveryshield/domain/model/SupportModels.kt`
- `/app/src/main/java/com/example/recoveryshield/domain/model/UserSettings.kt`
- `/app/src/main/java/com/example/recoveryshield/domain/state/ProtectionStateMachine.kt`

### Data & Persistence
- `/app/src/main/java/com/example/recoveryshield/data/local/ProtectionEntities.kt`
- `/app/src/main/java/com/example/recoveryshield/data/local/ProtectionDaos.kt`
- `/app/src/main/java/com/example/recoveryshield/data/local/RecoveryDatabase.kt`
- `/app/src/main/java/com/example/recoveryshield/data/repository/ProtectionRepository.kt`
- `/app/src/main/java/com/example/recoveryshield/data/settings/SettingsRepository.kt`

### Protection & Detection Engine
- `/app/src/main/java/com/example/recoveryshield/protection/engine/ProtectionEngine.kt`
- `/app/src/main/java/com/example/recoveryshield/protection/service/RecoveryAccessibilityService.kt`
- `/app/src/main/java/com/example/recoveryshield/protection/detector/EventNormalizer.kt`
- `/app/src/main/java/com/example/recoveryshield/protection/detector/LocalClassifier.kt`
- `/app/src/main/java/com/example/recoveryshield/protection/detector/DeterministicRuleDetector.kt`
- `/app/src/main/java/com/example/recoveryshield/protection/detector/AllowlistManager.kt`
- `/app/src/main/java/com/example/recoveryshield/protection/detector/SyntheticDetectorHarness.kt`
- `/app/src/main/java/com/example/recoveryshield/protection/intervention/InterventionController.kt`
- `/app/src/main/java/com/example/recoveryshield/protection/intervention/InterventionActivity.kt`
- `/app/src/main/java/com/example/recoveryshield/protection/disable/DisableProtectionManager.kt`

### Permissions, Support, Ads, Diagnostics
- `/app/src/main/java/com/example/recoveryshield/permissions/PermissionOrchestrator.kt`
- `/app/src/main/java/com/example/recoveryshield/support/SupportRepository.kt`
- `/app/src/main/java/com/example/recoveryshield/ads/AdProvider.kt`
- `/app/src/main/java/com/example/recoveryshield/points/RewardLedger.kt`
- `/app/src/main/java/com/example/recoveryshield/points/ProtectionCreditManager.kt`
- `/app/src/main/java/com/example/recoveryshield/diagnostics/DeviceCompatibilityRepository.kt`
- `/app/src/main/java/com/example/recoveryshield/workers/BootReceiver.kt`

### Presentation
- `/app/src/main/java/com/example/MainActivity.kt`
- `/app/src/main/java/com/example/recoveryshield/ui/MainViewModel.kt`
- `/app/src/main/java/com/example/recoveryshield/ui/MainScreen.kt`

---

## 8. Platform Limitations

1. **Forcible App Termination**:
   Android does not grant non-system apps the ability to forcibly kill or terminate third-party processes (`killBackgroundProcesses` is advisory only). RecoveryShield uses permitted `performGlobalAction(GLOBAL_ACTION_BACK)` followed by `GLOBAL_ACTION_HOME` and brings an intervention activity to the foreground. Where OS restrictions prevent navigation, the app displays manual exit guidance without claiming false closure.

2. **Uninstallation Prevention**:
   Android's security model grants device owners full authority to uninstall user apps. RecoveryShield voluntarily rejects using AccessibilityService exploits or deceptive DeviceAdmin overlays to break system Settings. The cooling-off period is a commitment device; uninstallability remains standard per Android platform rules.

3. **OEM Background Restrictions**:
   Aggressive OEM battery optimizers (MIUI, EMUI, ColorOS) may delay or throttle accessibility events during deep sleep. Users are directed to the official `ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS` intent to mitigate this.

---

## 9. V2 Deferred Roadmap

- `TODO(V2-Classifier)`: On-device LiteRT/TFLite multimodal classification model implementing `LocalClassifier`.
- `TODO(V2-Sync)`: Encrypted opt-in peer-to-peer accountability sync (`CloudSyncRepository`).
- `TODO(V2-Ads)`: Production Google Mobile Ads SDK integration implementing `AdProvider`.
- `TODO(V2-Hardware)`: Keystore-backed cryptographic tamper-proofing for stored cooldown tokens.
