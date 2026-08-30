package org.example.yangi_ishxona.service;

/**
 * Business-rule violation carrying an i18n message key (see {@code i18n/messages_*.properties})
 * plus positional args, so the catching layer (bot / dashboard / Mini App API) can render it
 * in the acting user's language via {@link Messages#t}.
 */
public class DomainException extends RuntimeException {

    private final String messageKey;
    private final Object[] args;

    public DomainException(String messageKey, Object... args) {
        super(messageKey);
        this.messageKey = messageKey;
        this.args = args;
    }

    public String getMessageKey() {
        return messageKey;
    }

    public Object[] getArgs() {
        return args;
    }
}
