package com.example.recme.audio

/**
 * Constants governing the audio capture pipeline, VAD inference, and storage limits.
 */
object AudioConstants {
    /** Audio sample rate in Hz (Silero VAD standard). */
    const val SAMPLE_RATE_HZ = 16000

    /** Number of audio channels (Mono). */
    const val CHANNEL_COUNT = 1

    /** PCM encoding bit depth. */
    const val BITS_PER_SAMPLE = 16

    /** Bytes per single PCM sample (16-bit = 2 bytes). */
    const val BYTES_PER_SAMPLE = 2

    /** Frame size in samples required by Silero VAD (32 ms @ 16 kHz). */
    const val FRAME_SIZE_SAMPLES = 512

    /** Duration of a single frame in milliseconds (512 / 16000 * 1000 = 32 ms). */
    const val FRAME_DURATION_MS = 32

    /** Pre-roll duration in milliseconds (~608 ms = 19 frames). */
    const val PRE_ROLL_MS = 608

    /** Post-roll duration in milliseconds (~608 ms = 19 frames). */
    const val POST_ROLL_MS = 608

    /** Number of 512-sample frames in a 608 ms pre/post-roll buffer (19 * 32ms = 608ms). */
    const val BUFFER_FRAME_COUNT = 19

    /** Default probability threshold for Silero VAD speech detection (0.0 to 1.0). */
    const val DEFAULT_VAD_THRESHOLD = 0.5f

    /** Default soft limit for automatic file splitting in megabytes (95 MB = ~49.5 minutes of active speech). */
    const val DEFAULT_MAX_FILE_SIZE_MB = 95f

    /** Default soft limit for automatic file splitting (95 MB in bytes). */
    const val DEFAULT_MAX_FILE_SIZE_BYTES = 95L * 1024L * 1024L

    /** Minimum allowable split size in megabytes. */
    const val MIN_FILE_SIZE_MB = 25f

    /** Maximum allowable split size in megabytes. */
    const val MAX_FILE_SIZE_MB = 700f

    /** Post-processing Opus compression bitrate in bits per second (32 kbps for high quality speech). */
    const val OPUS_BITRATE_BPS = 32000

    /** Maximum silence duration before resetting VAD recurrent hidden state (5000 ms). */
    const val MAX_SILENCE_RESET_MS = 5000L
}
