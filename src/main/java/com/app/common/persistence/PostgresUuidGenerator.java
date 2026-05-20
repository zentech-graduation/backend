package com.app.common.persistence;

import java.util.UUID;

import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.id.IdentifierGenerator;

/**
 * Hibernate identifier generator that delegates UUID creation to PostgreSQL {@code
 * gen_random_uuid()}.
 */
public class PostgresUuidGenerator implements IdentifierGenerator {

    @Override
    public Object generate(SharedSessionContractImplementor session, Object object) {
        return session.createNativeQuery("select gen_random_uuid()", UUID.class).getSingleResult();
    }
}
