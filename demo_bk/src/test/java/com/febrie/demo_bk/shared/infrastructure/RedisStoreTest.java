package com.febrie.demo_bk.shared.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

class RedisStoreTest {

    @Test
    void serializedNegativeMarkerShouldBeReadableAsOriginalString() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        Jackson2JsonRedisSerializer<Object> serializer =
                new Jackson2JsonRedisSerializer<>(objectMapper, Object.class);

        @SuppressWarnings("unchecked")
        RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
        doReturn(serializer).when(redisTemplate).getValueSerializer();
        RedisStore redisStore = new RedisStore(
                redisTemplate,
                mock(StringRedisTemplate.class),
                objectMapper
        );

        String serialized = redisStore.serializeValue("__NULL__");
        Object deserialized = serializer.deserialize(
                serialized.getBytes(StandardCharsets.UTF_8)
        );

        assertThat(serialized).isEqualTo("\"__NULL__\"");
        assertThat(deserialized).isEqualTo("__NULL__");
    }
}
