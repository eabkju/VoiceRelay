# VoiceRelay ProGuard Rules
-keepclasseswithmembernames class * {
    native <methods>;
}
-keep class com.voicerelay.app.audio.AudioEngineBridge { *; }
