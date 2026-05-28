---
trigger: model_decision
description: Load when writing any code that reads from or writes to Redis.
---

# Skill: Redis Key Conventions

## When to use
Any Redis read, write, or TTL operation in any service implementation.

## Key naming convention

```
{module}:{type}:{id}
```

Examples from implemented code:
| Key | Module | Type | ID |
|-----|--------|------|----|
| `auth:blacklist:{jti}` | auth | blacklist | JWT `jti` claim |
| `auth:ratelimit:{key}` | auth | ratelimit | caller key |
| `auth:token:email-verification:{sha256}` | auth | token:email-verification | SHA-256 of raw token |
| `auth:token:password-reset:{sha256}` | auth | token:password-reset | SHA-256 of raw token |

Planned convention for other modules: `app:{domain}:{id}` for single entries, `app:{domain}:list` for collections.

## TTL strategy

- Set TTL on the `set()` call — never as a separate `EXPIRE` unless using a Lua script.
- Token blacklist: TTL = remaining access token lifetime (auto-evicts at natural expiry).
- Email verification token: TTL 24h.
- Password reset token: TTL 15m.
- Rate limit window: TTL = window duration (set atomically via Lua).
- Do **not** store data in Redis that must survive a full Redis flush — use PostgreSQL for durable data.

## Lua script pattern

Use Lua scripts when multiple Redis commands must be atomic (INCR + EXPIRE, GET + DEL, etc.):

```java
private static final String SCRIPT = "local val = redis.call('GET', KEYS[1]) "
        + "if val then redis.call('DEL', KEYS[1]) end "
        + "return val";

private final RedisScript<String> script = new DefaultRedisScript<>(SCRIPT, String.class);

String result = redisTemplate.execute(script, List.of(key), args...);
```

- Inline comment explaining the atomicity reason is required above the `execute` call.
- Use `StringRedisTemplate` (not `RedisTemplate<Object, Object>`) — all keys and values are strings.

## Redis vs PostgreSQL decision

| Use Redis | Use PostgreSQL |
|-----------|----------------|
| TTL-bound, ephemeral tokens (email verification, password reset) | Durable tokens (refresh tokens, user data) |
| Rate limit counters | Business records |
| JWT blacklist (self-expiring) | Audit logs |
| Session state | Everything requiring ACID guarantees |

## Output contract
- Key follows `{module}:{type}:{id}` format
- TTL set at write time (not as a separate call unless in a Lua script)
- Lua scripts used for multi-command atomicity
- `StringRedisTemplate` used
- Inline comment on Lua execute calls

## Checklist
- [ ] Key format follows `{module}:{type}:{id}`
- [ ] TTL set at write time
- [ ] Multi-step operations use Lua scripts
- [ ] `StringRedisTemplate` used (not generic `RedisTemplate`)
- [ ] Inline comment explains Lua atomicity reason
- [ ] Ephemeral-only data goes to Redis; durable data goes to PostgreSQL
