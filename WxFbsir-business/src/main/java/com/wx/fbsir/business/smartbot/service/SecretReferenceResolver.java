package com.wx.fbsir.business.smartbot.service;

/** Resolves an opaque credential reference without persisting the plaintext. */
public interface SecretReferenceResolver {
    String resolve(String secretReference);
}
