package uz.taskapp.voice;

interface VoiceModelClient {
    boolean configured();

    VoiceDraftPayload transcribeAndExtract(byte[] audio, String mimeType, String promptText);

    /** Same structured extraction as {@link #transcribeAndExtract}, but from plain text - no ASR step. */
    default VoiceDraftPayload extractFromText(String text, String promptText) {
        throw new UnsupportedOperationException();
    }
}
