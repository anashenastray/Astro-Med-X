<p align="center">
  <img src="docs/icon.png" width="140" alt="Astro Med-X Icon" style="border-radius: 28px; box-shadow: 0 8px 24px rgba(0,0,0,0.3);" />
</p>

<h1 align="center">Astro Med-X</h1>

<p align="center">
  <strong>Autonomous Spaceflight Clinical Decision Support & Astronaut Health Monitoring System</strong><br>
  Built with Kotlin & Jetpack Compose • Designed for Long-Duration Exploration Missions (Artemis & Mars Transit)
</p>

<p align="center">
  <a href="release/AstroMed-X.apk?raw=true"><strong>📥 Download Pre-Built APK (v1.0.0)</strong></a> •
  <a href="#-quick-start-testing--install-guide">Quick Install</a> •
  <a href="#-how-to-simulate-scenarios--test-capabilities">Scenario Simulator Guide</a> •
  <a href="#-core-app-features--capabilities">Feature Tour</a>
</p>

---

## ⚡ Quick Start: Testing & Install Guide

Testers do **not** need Android Studio or a computer. You can install and test directly on any Android phone or tablet (Android 7.0 / API 24+):

### Sideloading in 4 Steps:
1. **Download the APK**: Tap [**`release/AstroMed-X.apk`**](release/AstroMed-X.apk?raw=true) in your device's browser.
2. **Open the File**: Tap the completed download in your notifications or open your device's **Files / Downloads** app.
3. **Allow Installation**:
   - If prompted: *"For your security, your device is not allowed to install unknown apps from this source"*, tap **Settings** in the popup.
   - Toggle **Allow from this source** (or *Install unknown apps*).
4. **Launch**: Tap **Install**, then **Open**.

*(For developers connected via USB: `adb install release/AstroMed-X.apk`)*

---

## 🎮 How to Simulate Scenarios & Test Capabilities

Astro Med-X includes a **Built-in Mission Simulator** so judges and testers can trigger real physiological anomalies, environmental hazards, and hardware failures **without needing live medical sensors**:

### How to Open the Simulator:
1. Go to the **Vitals (Dashboard)** tab (the heart icon in the dock).
2. In the top-right header, tap the **amber scenario badge** (e.g. `CSA Bio-Monitor • Nominal` or `DONKI: NOMINAL`).
3. A dropdown menu with **7 Spaceflight Scenarios** will appear. Select any scenario to watch the app respond instantly!

```
┌─────────────────────────────────────────────────────────────┐
│  [CR] Commander Vance  DAY 142    [ CSA Bio-Monitor • Nominal ▾ ] │ <── TAP THIS
└─────────────────────────────────────────────────────────────┘
```

---

### What to Observe in Each Scenario:

| Scenario | What It Simulates | What to Watch on Screen |
| :--- | :--- | :--- |
| **1. CSA Bio-Monitor • Nominal** | Baseline deep-space transit cruise. Normal physiological homeostasis. | • All vital cards display green **NOMINAL** status.<br>• Heart rate ~72 BPM, SpO2 ~98%, Blood Pressure ~112/78.<br>• Ambient radiation is stable at 0.64 mSv/d. |
| **2. NASA DONKI Solar Particle Surge** | Solar Particle Event (SPE) space weather storm hitting the spacecraft. | • Radiation Dosimeter spikes to **>5.35 mSv/d** with a bright red **CRITICAL ALERT** banner.<br>• Recommends the storm-shelter countermeasure protocol.<br>• Tap the alert banner to jump straight to the radiation checklist. |
| **3. NASA HRP EVA Cardio Spike (Rule 5.2)** | Strenuous extravehicular spacewalk activity inducing acute cardiac strain. | • Heart rate spikes to **104–110 BPM** with red **ATTENTION REQUIRED** badge.<br>• Blood Pressure rises to **142/88 mmHg**; SpO2 dips.<br>• Canvas trend unrolls a cardiovascular anomaly flag (`Rule 5.2`). |
| **4. CDME CO2 > 3mmHg + Hypercapnia** | Elevated cabin pCO2 pocket due to scrubber saturation. | • Module Environmental Telemetry shows elevated **pCO2 (>3.2 mmHg)**.<br>• Respiratory rate increases to **24 RPM** with compensatory tachycardia.<br>• Respiratory System highlights warning on the Body Map. |
| **5. Curiosity MSL-RAD Mars Cruise** | Deep-space cruise radiation profile based on Curiosity MSL-RAD telemetry. | • Background dosimeter adjusts to **1.84 mSv/d** galactic cosmic ray (GCR) exposure rate.<br>• Immune & hematopoietic risk scores adapt dynamically on the body scan. |
| **6. CSA Bio-Monitor & RAD Disconnect** | Loose chest strap sensor or wireless biosensor dropout. | • Heart Rate and Respiratory Rate switch to grey **OFFLINE / NO SIGNAL**.<br>• Autonomous estimation engine kicks in; historical charts show shaded **[GAP]** regions. |
| **7. Deep-Space Link Blackout** | Conjunction or orbital occultation cutting Earth telemetry communication. | • Offline caching activates; all manual logs and protocol steps queue in the encrypted local store. |

---

## ✨ Core App Features & Capabilities

```
┌─────────────────────────────────────────────────────────────────────────┐
│                          AURA HEALTH DOCK                               │
│     [♥ Vitals]    [📈 Trends]    [🧍 Body]    [✚ Protocols]    [✦ AI Coach] │
└─────────────────────────────────────────────────────────────────────────┘
```

### 1. 🫀 Live Vitals Dashboard
- **2×2 Environmental Cabin Telemetry**: Real-time readouts of Cabin $\text{pCO}_2$, Module Temperature, Radiation Flux, and Cabin Pressure.
- **2-Column Clinical Vital Cards**: Heart Rate, Blood Pressure, $\text{SpO}_2$, Respiratory Rate, Core Temperature, Radiation Dose, Hydration Level, and Sleep Duration.
- **Filter Pills**: Tap `ALL`, `ATTENTION`, `CARDIO`, or `META` to filter cards dynamically.
- **One-Tap Detail**: Tap any vital card to open its expanded 24-hour telemetry chart.

---

### 2. 📈 Interactive Telemetry & Trend Analytics
- **Continuous Unrolling Graphs**: Vector canvas plotting live readings against:
  - **Nominal Zone (Green Shaded Band)**: Personal baseline bounds.
  - **Critical Bounds (Red Dashed Lines)**: NASA physiological safety limits.
  - **Signal Integrity**: Solid lines for live sensor data, dashed for estimated data, and shaded markers for sensor disconnections.
- **Timeframe Selector**: Toggle between **24 Hours**, **7 Days**, and **30 Days** to track circadian drift and chronic spaceflight deconditioning.
- **Statistical Summary**: Displays Peak Value with exact timestamp, 24H Average, Minimum, Packet Validity Ratio (%), and Baseline Drift Delta.

---

### 3. 🧍 3D Anatomical Body Map HUD
- **Interactive Anatomical Model**: Vector human avatar with dynamic, glowing physiological hotspots.
- **6 Key Physiological Systems**:
  - **Cardiovascular**: Cephalad fluid shift, orthostatic intolerance, stroke volume decline.
  - **Neurological & Ocular (SANS)**: Spaceflight-Associated Neuro-ocular Syndrome, optic disc edema, intracranial pressure.
  - **Pulmonary & Respiratory**: Environmental hypercapnia, ventilation perfusion shifts.
  - **Musculoskeletal**: 1–1.5% monthly bone mineral density loss, calf muscle atrophy.
  - **Metabolic & Renal**: Dehydration, nephrolithiasis (kidney stone) risk.
  - **Immune & Radiation**: DNA double-strand breaks, latent viral reactivation.
- **Countermeasure Prescriptions**: Tap any body zone to view mapped NASA Human Research Program (HRP) countermeasures and onboard exercise hardware (**ARED**, **CEVIS**, **T2 Treadmill**).
- **Interactive Symptom Checklist**: Check off symptoms (e.g., *Head fullness*, *Visual blurring*, *Calf soreness*) to calculate an updated composite risk score.

---

### 4. 📋 Autonomous Clinical Protocols & Decision Support
- **Interactive Emergency Checklists**: Step-by-step procedures for critical spaceflight events:
  - *Acute Hypoxia / Cabin Decompression*
  - *SANS Acute Visual Assessment*
  - *Module Hypercapnia & CO2 Scrubber Bypass*
  - *Solar Particle Event (SPE) Bunker Shelter Procedure*
  - *Post-EVA Orthostatic Recovery*
- **Step Tracking**: Check off steps as you execute them; status indicators update from `IN PROGRESS` to `RESOLVED`.
- **Encrypted Flight Surgeon Delta Log**: Audit trail recording every vital anomaly, protocol step completion, and crew interaction.
- **One-Tap Sharing**:
  - **Copy Log**: Instantly copies the full clinical telemetry report to clipboard.
  - **Share to Flight Surgeon**: Opens Android's native share sheet to export telemetry to email, messaging, or local files.

---

### 5. 🤖 Onboard AI Flight Surgeon (AI Coach)
- **Autonomous Clinical Guidance**: Designed for deep-space missions where transmission delay to Earth makes real-time Mission Control contact impossible.
- **Quick-Prompt Chips**: Tap preset chips for rapid queries:
  - *"Explain SANS countermeasures"*
  - *"Tachycardia triage protocol"*
  - *"Radiation storm sheltering rules"*
  - *"Evaluate current crew readiness"*
- **Deterministic Safety Fallback**: Even without an active internet connection, the autonomous interlock engine evaluates vitals and hazard matrices deterministically.

---

### 6. 💧 One-Tap Quick Log
- **Hydration Logging**: Tap the center Quick Log button in the dock to log a +500 mL water intake increment with a single tap.
- **Manual Crew Inputs**: Record manual blood pressure, body temperature, or sleep logs when external sensors are uncoupled.

---

## 🎨 Spaceflight Ergonomics & Nordic UI

- **Nordic Light Palette**: Clear, high-contrast visual hierarchy engineered to minimize cognitive strain during crew fatigue and emergency scenarios.
- **Microgravity Touch Standards**: Generous `48dp × 48dp` touch targets formatted for gloved astronaut interaction and hand tremors during high-G or vibration phases.
- **Triple-Encoded Status**: Every indicator pairs **Color** + **Icon** + **Explicit Text** (e.g., `CRITICAL`, `NOMINAL`, `OFFLINE`) ensuring zero ambiguity under varying cabin lighting conditions.

---

## 📦 Repository Structure

```
Astro-Med-X/
├── app/
│   ├── src/main/kotlin/com/example/astromerged/
│   │   └── MainActivity.kt               # Unified Jetpack Compose Application & Engine
│   ├── src/main/res/
│   │   ├── mipmap-*/                     # Custom Space Medical App Icons (WebP)
│   │   ├── mipmap-anydpi-v26/            # Adaptive Launcher Icons
│   │   └── values/                       # Themes, Colors, and Strings
│   └── build.gradle.kts                  # Gradle Dependencies & SDK Config
├── docs/
│   ├── icon.png                          # App Icon Artwork
│   └── screenshot.png                    # Live Tablet Capture
├── release/
│   └── AstroMed-X.apk                    # Pre-Compiled Android APK (v1.0.0)
└── README.md                             # User Guide & Testing Documentation
```

---

<p align="center">
  <strong>Developed for Spaceflight Human Exploration & Autonomous Medical Operations</strong>
</p>
