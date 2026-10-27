cmake_minimum_required(VERSION 3.22.1)
project("voicerelay")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

find_package(oboe REQUIRED CONFIG)

add_library(voicerelay SHARED
    jni_bridge.cpp
    audio_engine.cpp
    jitter_buffer.cpp
    effects/yin_pitch_detector.cpp
    effects/pitch_shifter.cpp
    effects/autotune.cpp
    effects/voice_effects_chain.cpp
)

target_include_directories(voicerelay PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${CMAKE_CURRENT_SOURCE_DIR}/effects
)

target_link_libraries(voicerelay
    PRIVATE
    oboe::oboe
    log
    aaudio
    OpenSLES
)
