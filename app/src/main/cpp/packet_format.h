#ifndef VOICE_RELAY_PACKET_FORMAT_H
#define VOICE_RELAY_PACKET_FORMAT_H

#include <cstdint>

#pragma pack(push, 1)

constexpr uint32_t PACKET_MAGIC = 0x5652454C; // 'VREL'

enum class CodecType : uint8_t {
    PCM16_48K = 0,
    PCM16_24K = 1,
    PCM16_16K = 2
};

struct AudioPacketHeader {
    uint32_t magic;          // 'VREL'
    uint32_t seq;            // Monotonic sequence number
    uint64_t timestampMs;    // Sender monotonic timestamp in ms
    uint16_t sampleRate;     // 48000, 24000, 16000
    uint16_t numSamples;     // Number of 16-bit PCM samples in payload
    uint8_t  codec;          // CodecType
    uint8_t  flags;          // Bit 0: IsKeepAlive, Bit 1: IsEchoReply
    uint16_t checksum;       // Simple header checksum
};

#pragma pack(pop)

constexpr size_t HEADER_SIZE = sizeof(AudioPacketHeader);
constexpr size_t MAX_SAMPLES_PER_FRAME = 960; // Up to 20ms at 48kHz
constexpr size_t MAX_PACKET_SIZE = HEADER_SIZE + (MAX_SAMPLES_PER_FRAME * sizeof(int16_t));

#endif // VOICE_RELAY_PACKET_FORMAT_H
