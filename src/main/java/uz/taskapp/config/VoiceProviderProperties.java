package uz.taskapp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("voice")
public record VoiceProviderProperties(String provider) {
}
