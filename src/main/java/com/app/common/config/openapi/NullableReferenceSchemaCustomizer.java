package com.app.common.config.openapi;

import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.stereotype.Component;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;

/**
 * Rewrites a nullable object reference into a schema an instance can actually satisfy.
 *
 * <p>A property declared {@code @Schema(nullable = true)} whose type is another schema is emitted
 * by springdoc as {@code {"type": "null", "$ref": "..."}}. Under JSON Schema 2020-12, which OpenAPI
 * 3.1 uses, a {@code $ref} keeps its sibling keywords rather than replacing them, so that schema
 * asserts the value is null <em>and</em> matches the referenced object. Nothing satisfies both. A
 * generator reading it emits either an impossible type or, more often, silently drops the
 * nullability and hands the consumer a non-optional field that arrives null.
 *
 * <p>The satisfiable form is a union, which is what this rewrites it to:
 *
 * <pre>
 *   {"oneOf": [{"$ref": "..."}, {"type": "null"}]}
 * </pre>
 *
 * <p>Applied as a document-wide pass rather than per declaration, because the defect is in how the
 * annotation is rendered rather than in how it is written: every author who writes {@code nullable
 * = true} on an object-typed property is correct, and every one of them produced this shape.
 */
@Component
public class NullableReferenceSchemaCustomizer implements OpenApiCustomizer {

    private static final String NULL_TYPE = "null";

    @Override
    public void customise(OpenAPI openApi) {
        if (openApi.getComponents() == null || openApi.getComponents().getSchemas() == null) {
            return;
        }
        // Identity-keyed rather than equals-keyed: two distinct properties can be structurally
        // equal schemas, and a schema graph with a self-reference would otherwise not terminate.
        Set<Schema<?>> seen =
                java.util.Collections.newSetFromMap(new IdentityHashMap<Schema<?>, Boolean>());
        openApi.getComponents().getSchemas().values().forEach(schema -> visit(schema, seen));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void visit(Schema schema, Set<Schema<?>> seen) {
        if (schema == null || !seen.add(schema)) {
            return;
        }
        Map<String, Schema> properties = schema.getProperties();
        if (properties != null) {
            for (Map.Entry<String, Schema> entry : new ArrayList<>(properties.entrySet())) {
                Schema property = entry.getValue();
                if (isNullableReference(property)) {
                    properties.put(entry.getKey(), toUnion(property));
                } else {
                    visit(property, seen);
                }
            }
        }
        visit(schema.getItems(), seen);
        visitAll(schema.getOneOf(), seen);
        visitAll(schema.getAnyOf(), seen);
        visitAll(schema.getAllOf(), seen);
        if (schema.getAdditionalProperties() instanceof Schema<?> additional) {
            visit(additional, seen);
        }
    }

    @SuppressWarnings("rawtypes")
    private void visitAll(Collection<Schema> schemas, Set<Schema<?>> seen) {
        if (schemas != null) {
            schemas.forEach(schema -> visit(schema, seen));
        }
    }

    @SuppressWarnings("rawtypes")
    private static boolean isNullableReference(Schema schema) {
        return schema != null && schema.get$ref() != null && declaresNull(schema);
    }

    @SuppressWarnings("rawtypes")
    private static boolean declaresNull(Schema schema) {
        if (NULL_TYPE.equals(schema.getType())) {
            return true;
        }
        Set<String> types = schema.getTypes();
        return types != null && types.contains(NULL_TYPE);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Schema toUnion(Schema nullableReference) {
        Schema reference = new Schema().$ref(nullableReference.get$ref());
        // Both keywords are set because the two dialects read different ones: 3.0 serialises
        // `type`, and 3.1 serialises `types`. Setting only one produced an empty branch, which is
        // the "anything goes" schema and quietly makes the union meaningless.
        Schema nullBranch = new Schema().type(NULL_TYPE);
        nullBranch.setTypes(Set.of(NULL_TYPE));
        Schema union = new Schema();
        union.setOneOf(List.of(reference, nullBranch));
        // The description lives on the property rather than on the referenced schema, so it has to
        // survive the rewrite or the field loses the sentence explaining when it is null.
        union.setDescription(nullableReference.getDescription());
        return union;
    }
}
