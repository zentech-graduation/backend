---
trigger: model_decision
description: Load when creating or modifying a MapStruct mapper interface in any module.
---

# Skill: MapStruct Mapper

## When to use
Creating or editing any interface in `modules/{module}/mapper/`.

## Input required
- Source entity class(es)
- Target DTO class(es)
- Any fields that need explicit mapping (name mismatch, multi-source aggregation)

## Steps

1. Declare the interface with class-level Javadoc:
   ```java
   /** Maps {@link SourceEntity} entities to {module}-layer DTOs. */
   @Mapper(componentModel = "spring")
   public interface {Module}Mapper { }
   ```
2. For simple same-name mappings, MapStruct auto-maps — no `@Mapping` needed.
3. For name mismatches or multi-source methods, use explicit `@Mapping`:
   ```java
   @Mapping(source = "entity.field", target = "dtoField")
   TargetDto toTargetDto(SourceEntity entity, boolean extraParam);
   ```
4. Do **not** add `@Data`, `@Getter`, `@Setter`, or other Lombok annotations — MapStruct generates its own implementation class; adding Lombok to the interface causes compile errors.
5. Add method-level Javadoc **only** if the mapping involves non-obvious transformation logic (e.g., aggregating from multiple sources, computed fields).
6. Do not add field-level annotations or any Spring beans inside the interface.
7. The Spring component model makes MapStruct-generated impls injectable as `@Autowired` / constructor-injected beans.

## Output contract
- Interface in `modules/{module}/mapper/{Module}Mapper.java`
- Annotated `@Mapper(componentModel = "spring")`
- Class-level Javadoc present
- No Lombok annotations on the interface
- Explicit `@Mapping` only where auto-mapping would be incorrect

## Checklist
- [ ] `@Mapper(componentModel = "spring")` present
- [ ] Class-level Javadoc written
- [ ] No Lombok annotations on the mapper interface
- [ ] `@Mapping` used only for non-trivial or name-mismatched fields
- [ ] Compiles without ambiguous mapping warnings
