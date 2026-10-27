# VoiceRelay

**VoiceRelay** is a real-time low-latency audio relay and voice-changing Android application built with **Kotlin**, **Jetpack Compose (Material 3)**, and **C++ NDK (Oboe / AAudio / OpenSL ES)**.

It enables phone-to-phone real-time wireless audio streaming (using custom RFCOMM Bluetooth Classic sockets or local Wi-Fi UDP) and local mic-to-speaker amplification with live sample-accurate DSP voice transformation effects.

---

## Features

- **3 Operating Roles**:
  1. **MIC (Sender)**: Captures phone microphone at 48 kHz mono, applies selected DSP voice effect, packs low-overhead audio transport frames, and streams over Bluetooth Classic or Wi-Fi UDP.
  2. **SPEAKER (Receiver)**: Listens for incoming connections (`BluetoothServerSocket.accept()` or UDP port 45454), feeds packets into an adaptive jitter buffer (target 40–80 ms) with Packet Loss Concealment (PLC), and plays through the device speaker or connected Bluetooth headphones.
  3. **LOCAL AMPLIFIER**: Real-time microphone to speaker amplifier with zero-allocation in-memory loopback and live effects. Includes acoustic feedback precaution banner.
- **Voice Effects DSP Engine (C++17, Zero Allocations in Callback)**:
  - **Chipmunk**: Pitch shift +7 semitones with upper formant boost.
  - **Child**: Pitch shift +4 semitones with warm formant lift.
  - **Talking Tom**: Pitch shift +9 semitones with cartoon vocal timbre.
  - **Deep Monster**: Pitch drop -6 semitones with dark formant depth.
  - **Flanger**: Modulated 1–10 ms delay with variable LFO rate, depth, and feedback.
  - **Auto-Tune**: Real-time YIN pitch detection, musical scale snapping (Chromatic, Major, Minor, Pentatonic), and retune-speed control (from natural correction to robotic T-Pain hard snap).
  - **Robot**: 50 Hz ring modulator generating harmonic sideband metallic timbre.
  - **Echo**: 150–400 ms feedback circular delay line.
  - **Reverb**: Schroeder-Freeverb 4-comb and 2-allpass filter acoustic simulation.
  - **Limiter & Gain**: Master volume slider with polynomial soft-knee limiter to prevent digital clipping.
  - **Clickless Crossfading**: Smooth 10 ms crossfade buffer when switching presets to eliminate clicks.
- **Connection & Transport**:
  - **Bluetooth Classic RFCOMM**: Custom service UUID `fa87c0d0-afac-11de-8a39-0800200c9a66` (phone-to-phone RFCOMM socket). Automatic reconnection on drop.
  - **Wi-Fi UDP**: Ultra low-latency local network transmission via Datagram sockets.
  - **Codec Options**: 48 kHz PCM16 (crystal clarity), 24 kHz PCM16 (balanced), 16 kHz PCM16 (low bandwidth).
  - **Bluetooth Headset Mic Support**: Toggle between phone built-in microphone and Bluetooth SCO headset mic.
- **Android Architecture**:
  - Foreground Service (`AudioRelayService`) with `microphone` and `connectedDevice` types and persistent notification so streaming continues with screen off.
  - Runtime permissions with clean rationale dialogs.
  - Audio focus management with `ACTION_AUDIO_BECOMING_NOISY` protection when headphones are disconnected.
  - MVVM pattern with Kotlin Coroutines and `StateFlow`.

---

## Build Steps

### Prerequisites
- Android SDK Platform 34 (API 34)
- Android NDK (r25c or newer)
- JDK 17
- CMake 3.22.1+

### Build Debug APK
```bash
./gradlew assembleDebug
```
Output: `app/build/outputs/apk/debug/app-debug.apk`

### Build and Sign Release APK
The project includes a pre-generated release keystore located in `keystore/voicerelay-release.jks`.
Credentials are configured in `keystore/keystore.properties`:
- **Keystore path**: `keystore/voicerelay-release.jks`
- **Keystore password**: `voicerelay2026`
- **Key alias**: `voicerelay`
- **Key password**: `voicerelay2026`

To compile and sign the release APK:
```bash
./gradlew assembleRelease
```
Output: `app/build/outputs/apk/release/app-release.apk`

---

## Two-Phone Test Procedure

### 1. Bluetooth Mode (Standard)
1. Install `VoiceRelay.apk` on **Phone A** and **Phone B**.
2. Pair both phones in Android Bluetooth Settings beforehand.
3. Launch VoiceRelay on **Phone B** (Receiver):
   - Select **Speaker (Receiver)** role.
   - Tap **START AUDIO RELAY**. The connection status chip will show **Listening**.
4. Launch VoiceRelay on **Phone A** (Sender):
   - Select **Mic (Sender)** role.
   - Tap the **Settings (gear)** icon -> Select **Bluetooth** tab.
   - Tap **Phone B** from the Bonded Devices list.
5. Tap **START AUDIO RELAY** on Phone A.
6. Speak into Phone A's microphone. The transformed audio will stream and play from Phone B's speaker in real time!
7. Switch effect presets on Phone A (e.g. Chipmunk, Robot, Auto-Tune) to test live DSP transitions.

### 2. Wi-Fi UDP Mode (Ultra-Low Latency)
1. Ensure both phones are connected to the same Wi-Fi router / mobile hotspot.
2. Launch VoiceRelay on **Phone B** (Speaker):
   - Tap **Settings** -> tap **Wi-Fi UDP** tab.
   - Note the **Local Device IP** shown (e.g. `192.168.1.105`).
   - Tap **START AUDIO RELAY**.
3. On **Phone A** (Mic):
   - Tap **Settings** -> tap **Wi-Fi UDP** tab.
   - Enter Phone B's IP address (e.g. `192.168.1.105`) and port `45454`.
   - Tap **Connect Wi-Fi Stream**.
   - Tap **START AUDIO RELAY**.

---

## Known Limits & Latency Characteristics

- **Bluetooth Classic RFCOMM Latency**: ~**150 – 300 ms**.
  - Bluetooth Classic baseband framing, radio scheduling, and serial RFCOMM packet fragmentation introduce ~150–250 ms round trip.
  - This is expected behavior for wireless Bluetooth RFCOMM phone-to-phone data channels (A2DP cannot be used between two Android phones).
- **Wi-Fi Mode Latency**: ~**40 – 80 ms**.
  - UDP datagrams bypass transport handshakes and stream directly between devices over local LAN with typical latency under 60 ms.
- **Acoustic Feedback**:
  - When Phone A and Phone B are placed in the same room at high speaker volumes, the microphone will pick up the speaker audio and cause positive acoustic feedback squealing.
  - **Recommendation**: Wear headphones on the sender/receiver or keep devices separated by a distance.
