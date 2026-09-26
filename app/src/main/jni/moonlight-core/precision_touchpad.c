// Extend the input queue without modifying the moonlight-common-c submodule.
#include "moonlight-common-c/src/InputStream.c"

#define PRECISION_TOUCHPAD_MAGIC 0x55000008
#define PRECISION_TOUCHPAD_FEATURE 0x04

// Version 1: little-endian fields, except the standard big-endian packet size.
#pragma pack(push, 1)
typedef struct {
    NV_INPUT_HEADER header;
    uint8_t version, command, count, reserved;
    uint32_t sequence, time, width, height;
    struct { uint32_t id, x, y; } contacts[5];
} PRECISION_TOUCHPAD_PACKET;
#pragma pack(pop)

int LiSendPrecisionTouchpadFrame(uint32_t sequence, uint32_t time, uint32_t width, uint32_t height,
                                uint8_t command, uint8_t count, const uint32_t* contacts) {
    if (!initialized) return -2;
    if (!(SunshineFeatureFlags & PRECISION_TOUCHPAD_FEATURE)) return LI_ERR_UNSUPPORTED;
    if (count > 5 || command < 1 || command > 2 || (count && !contacts)) return -1;

    PPACKET_HOLDER holder = allocatePacketHolder(sizeof(PRECISION_TOUCHPAD_PACKET));
    if (!holder) return -1;
    holder->channelId = CTRL_CHANNEL_TOUCH;
    holder->enetPacketFlags = ENET_PACKET_FLAG_RELIABLE;
    PRECISION_TOUCHPAD_PACKET* packet = (PRECISION_TOUCHPAD_PACKET*)&holder->packet;
    memset(packet, 0, sizeof(*packet));
    packet->header.size = BE32(sizeof(*packet) - sizeof(uint32_t));
    packet->header.magic = LE32(PRECISION_TOUCHPAD_MAGIC);
    packet->version = 1;
    packet->command = command;
    packet->count = count;
    packet->sequence = LE32(sequence);
    packet->time = LE32(time);
    packet->width = LE32(width);
    packet->height = LE32(height);
    for (int i = 0; i < count; i++) {
        packet->contacts[i].id = LE32(contacts[i * 3]);
        packet->contacts[i].x = LE32(contacts[i * 3 + 1]);
        packet->contacts[i].y = LE32(contacts[i * 3 + 2]);
    }
    int err = LbqOfferQueueItem(&packetQueue, holder, &holder->entry);
    if (err != LBQ_SUCCESS) freePacketHolder(holder);
    return err;
}
