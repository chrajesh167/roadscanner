package com.roadscanner.authservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Generic Redis client wiring only. Per docs/services/auth-service/database-design.md,
 * Redis here holds a fast-lookup revocation cache derived from Postgres, which is always the
 * source of truth. The revocation-cache-specific adapter (implementing the RevocationCache
 * outbound port) is business logic and is added in adapter.out.cache alongside token
 * revocation, per implementation-roadmap.md step 6 — deliberately not built today.
 *
 * String serialization is used for both keys and values: revocation-cache entries are simple
 * (a token identifier mapping to a revocation marker), so there is no need for a JSON/object
 * serializer that would add complexity without benefit here.
 */
@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new StringRedisSerializer());
        template.afterPropertiesSet();
        return template;
    }
}
