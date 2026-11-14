package com.reeltrip.infra.storage;

import java.io.IOException;

public interface StorageClient {
    String store(String objectName, byte[] bytes, String contentType) throws IOException;
}
