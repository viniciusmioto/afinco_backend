package com.afinco.backend.statement;

import java.util.Objects;

/**
 * An uploaded PDF together with its already-extracted text, so parser selection and parsing share
 * one text extraction instead of reopening the document for each step.
 */
public record StatementDocument(byte[] content, String text) {

    public StatementDocument {
        Objects.requireNonNull(content, "Statement content must not be null");
        Objects.requireNonNull(text, "Statement text must not be null");
    }
}
