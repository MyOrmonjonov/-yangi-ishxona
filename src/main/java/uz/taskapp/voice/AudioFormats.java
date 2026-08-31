package uz.taskapp.voice;

/** Maps an audio MIME type to the file extension/format token each provider's API expects. */
final class AudioFormats {
    private AudioFormats() {
    }

    /** Filename extension for multipart uploads (Groq/Whisper-compatible transcription endpoints). */
    static String extensionFor(String mimeType) {
        if (mimeType == null) return "wav";
        String type = mimeType.toLowerCase();
        if (type.contains("ogg")) return "ogg";
        if (type.contains("mpeg") || type.contains("mp3")) return "mp3";
        if (type.contains("mp4") || type.contains("aac") || type.contains("m4a")) return "m4a";
        if (type.contains("webm")) return "webm";
        if (type.contains("flac")) return "flac";
        return "wav";
    }

    /**
     * OpenRouter's {@code input_audio} format only accepts "wav" or "mp3" - anything else is
     * approximated to the closer of the two since the API has no room for other containers.
     */
    static String openRouterFormat(String mimeType) {
        return "wav".equals(extensionFor(mimeType)) ? "wav" : "mp3";
    }
}
