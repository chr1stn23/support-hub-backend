package com.chrisdev.supporthub.ticket.application;

import java.io.InputStream;

public record AttachmentDownload(String filename, String contentType, long sizeBytes, InputStream stream) {
}