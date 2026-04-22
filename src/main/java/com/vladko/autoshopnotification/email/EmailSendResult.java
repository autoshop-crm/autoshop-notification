package com.vladko.autoshopnotification.email;

public record EmailSendResult(
        String provider,
        String providerMessageId,
        String providerMessageUuid,
        String providerMessageHref
) {

    public static EmailSendResult accepted(String provider) {
        return new EmailSendResult(provider, null, null, null);
    }
}
