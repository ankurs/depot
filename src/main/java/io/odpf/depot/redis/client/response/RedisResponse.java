package io.odpf.depot.redis.client.response;

public interface RedisResponse {
    String getMessage();

    boolean isFailed();
}
