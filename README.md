# 🖐️ GestureFlow — Touchless Media Controller

> Control your Android device's media and volume using only hand gestures — no touch required.

![Platform](https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white)
![Language](https://img.shields.io/badge/Language-Kotlin-7F52FF?logo=kotlin&logoColor=white)
![MediaPipe](https://img.shields.io/badge/ML-MediaPipe-FF6F00?logo=google&logoColor=white)
![OpenCV](https://img.shields.io/badge/Vision-OpenCV-5C3EE8?logo=opencv&logoColor=white)
![License](https://img.shields.io/badge/License-MIT-blue)

---

## 📖 Overview

**GestureFlow** is a real-time Android application that uses your device's camera to recognize hand gestures and translate them into media control actions — play, pause, next track, volume control, and mute — all without touching your screen.

Built with **MediaPipe Hand Landmark Detection**, **CameraX**, and **OpenCV**, GestureFlow demonstrates a practical on-device ML pipeline with a clean, modular Kotlin architecture.

---

## ✨ Features

| Gesture | Action |
|---|---|
| 🤏 Pinch (thumb + index) | Volume Up / Down |
| 🖐️ Open Palm | Play / Pause |
| ✌️ Two Fingers (index + middle) | Next Track |
| ✊ Fist | Mute / Unmute |

- **Real-time hand skeleton overlay** drawn on camera feed
- **Gesture label** displayed on screen with confidence feedback
- **Live volume bar** visualizing current audio level
- **Smooth gesture debouncing** to prevent accidental triggers
- Fully **on-device** — no internet, no cloud, no data sent anywhere

---

## 🛠️ Tech Stack

| Component | Technology |
|---|---|
| Language | Kotlin |
| Camera | CameraX |
| Hand Tracking | MediaPipe Hands (21 landmarks) |
| Image Processing | OpenCV |
| Media Control | Android AudioManager |
| Min SDK | API 26 (Android 8.0) |
| Target SDK | API 34 (Android 14) |

---

## 🏗️ Architecture

```
CameraX (Live Feed)
       ↓
MediaPipe Hand Tracking
       ↓
Landmark Extraction (21 keypoints)
       ↓
GestureClassifier (rule-based logic)
       ↓
MediaActionController
       ↓
Android AudioManager / MediaSession
```

---

## 📁 Project Structure

```
app/src/main/java/com/gestureflow/
├── camera/
│   └── CameraManager.kt          # CameraX setup & frame pipeline
├── vision/
│   └── HandGestureDetector.kt    # MediaPipe initialization & landmark extraction
├── gestures/
│   └── GestureClassifier.kt      # Gesture recognition logic
├── controllers/
│   └── MediaController.kt        # Maps gestures → Android media actions
├── ui/
│   └── OverlayView.kt            # Canvas overlay: skeleton, labels, volume bar
└── MainActivity.kt               # Entry point & lifecycle management
```

---

## 🧠 Gesture Detection Logic

GestureFlow uses **MediaPipe's 21 hand landmarks** to classify gestures geometrically.

### Key Landmark Indices

```
 0  → Wrist
 4  → Thumb Tip
 8  → Index Finger Tip
12  → Middle Finger Tip
16  → Ring Finger Tip
20  → Pinky Tip
```

### 1. Volume Control — Pinch Gesture

Measures the Euclidean distance between the **thumb tip (4)** and **index tip (8)**:

```kotlin
val distance = hypot(
    thumbTip.x - indexTip.x,
    thumbTip.y - indexTip.y
)
val volume = map(distance, MIN_DIST, MAX_DIST, 0, maxVolume)
audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0)
```

### 2. Play / Pause — Open Palm

All four fingers extended (tip y < PIP joint y):

```kotlin
val isOpenPalm =
    landmarks[8].y  < landmarks[6].y  &&   // index
    landmarks[12].y < landmarks[10].y &&   // middle
    landmarks[16].y < landmarks[14].y &&   // ring
    landmarks[20].y < landmarks[18].y      // pinky
```

### 3. Next Track — Two Fingers (Peace Sign)

Index and middle extended, ring and pinky folded:

```kotlin
val isTwoFingers =
    landmarks[8].y  < landmarks[6].y  &&   // index extended
    landmarks[12].y < landmarks[10].y &&   // middle extended
    landmarks[16].y > landmarks[14].y &&   // ring folded
    landmarks[20].y > landmarks[18].y      // pinky folded
```

### 4. Mute — Fist

All fingertips below their respective knuckles:

```kotlin
val isFist =
    landmarks[8].y  > landmarks[6].y  &&
    landmarks[12].y > landmarks[10].y &&
    landmarks[16].y > landmarks[14].y &&
    landmarks[20].y > landmarks[18].y
```

---

## 🚀 Getting Started

### Prerequisites

- Android Studio Hedgehog (2023.1.1) or later
- Android device or emulator with API 26+
- Physical device recommended (camera performance)

### Installation

1. **Clone the repository**
   ```bash
   git clone https://github.com/yourusername/gestureflow.git
   cd gestureflow
   ```

2. **Open in Android Studio**
   ```
   File → Open → select the project root folder
   ```

3. **Sync Gradle**
   Android Studio will automatically sync dependencies. Ensure you have an internet connection on first build.

4. **Run on device**
   ```
   Run → Run 'app'  (Shift+F10)
   ```

5. **Grant camera permission** when prompted on device.

---

## 📦 Dependencies

Add to `app/build.gradle`:

```groovy
dependencies {
    // CameraX
    implementation "androidx.camera:camera-core:1.3.0"
    implementation "androidx.camera:camera-camera2:1.3.0"
    implementation "androidx.camera:camera-lifecycle:1.3.0"
    implementation "androidx.camera:camera-view:1.3.0"

    // MediaPipe
    implementation "com.google.mediapipe:tasks-vision:0.10.8"

    // OpenCV
    implementation "org.opencv:opencv:4.8.0"
}
```

---

## 📅 Development Roadmap

| Day | Milestone |
|---|---|
| **Day 1** | Project setup · CameraX integration · MediaPipe hand landmark display |
| **Day 2** | Landmark extraction · finger state calculation · Open Palm & Fist detection |
| **Day 3** | Full gesture recognition (all 4 gestures) · GestureClassifier module |
| **Day 4** | MediaController integration · AudioManager volume & playback control |
| **Day 5** | UI polish · skeleton overlay · gesture labels · volume bar · demo recording |

---

## 📸 UI Overlay

The live camera feed displays:

```
┌────────────────────────────────┐
│                                │
│    [hand skeleton drawn]       │
│                                │
│  Gesture: VOLUME CONTROL       │
│  Volume:  ████████░░  72%      │
│                                │
└────────────────────────────────┘
```

- **Hand skeleton**: lines connecting all 21 landmarks
- **Gesture label**: current detected gesture in real time
- **Volume bar**: animated fill showing audio level

---

## 🔐 Permissions

Declared in `AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />
```

---

## 🧪 Testing the Gestures

| Test | Expected Result |
|---|---|
| Hold open palm in front of camera | Play/Pause toggled |
| Slowly pinch and open fingers | Volume changes smoothly |
| Show peace sign (2 fingers up) | Next track triggered |
| Make a fist | Audio muted |

**Tips:**
- Ensure good, even lighting
- Keep hand 30–60 cm from camera
- Face palm toward the camera lens

---

## 🙌 Acknowledgements

- [MediaPipe by Google](https://developers.google.com/mediapipe) — hand landmark model
- [CameraX](https://developer.android.com/training/camerax) — Android camera API
- [OpenCV](https://opencv.org/) — image processing

---

## 👤 Author

Built as a portfolio project demonstrating real-time on-device ML on Android.

> *"Developed a real-time Android touchless media controller using MediaPipe hand landmark detection and gesture recognition to control volume and media playback without screen interaction."*
