package com.wx.fbsir.business.board.oauth;

import java.util.Objects;
import org.springframework.stereotype.Component;

/** CSPRNG source for opaque exchange identifiers and bearer-token material. */
@Component
public final class BoardOAuthTokenMaterialGenerator {
    private final BoardOAuthCrypto crypto;

    public BoardOAuthTokenMaterialGenerator() {
        this(new BoardOAuthCrypto());
    }

    BoardOAuthTokenMaterialGenerator(BoardOAuthCrypto crypto) {
        this.crypto = Objects.requireNonNull(crypto, "crypto");
    }

    public String generateFamilyId() {
        return crypto.generateOpaqueSecret();
    }

    public String generateAccessToken() {
        return crypto.generateOpaqueSecret();
    }

    public String generateRefreshToken() {
        return crypto.generateOpaqueSecret();
    }

    public String generateReceiptId() {
        return crypto.generateOpaqueSecret();
    }

    public String generateCorrelationId() {
        return crypto.generateOpaqueSecret();
    }

    @Override
    public String toString() {
        return "BoardOAuthTokenMaterialGenerator[REDACTED]";
    }
}
