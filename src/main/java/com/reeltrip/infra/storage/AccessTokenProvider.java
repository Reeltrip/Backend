package com.reeltrip.infra.storage;

import java.io.IOException;

public interface AccessTokenProvider {
    String token() throws IOException;
}
