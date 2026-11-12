package com.reeltrip.infra.ai;

/** LLM 응답이 정한 JSON 모양이 아닐 때. GeminiLlmClient가 이 경우에만 한 번 다시 호출해요. */
class MalformedLlmResponseException extends RuntimeException {

    MalformedLlmResponseException(String message) {
        super(message);
    }

    MalformedLlmResponseException(String message, Throwable cause) {
        super(message, cause);
    }
}
