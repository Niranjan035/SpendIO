package com.spendio.auth.dto.response;

/** Generic acknowledgement body for endpoints without a resource payload (D.2). */
public record MessageResponse(String message) {

    public static MessageResponse of(String message) {
        return new MessageResponse(message);
    }
}
