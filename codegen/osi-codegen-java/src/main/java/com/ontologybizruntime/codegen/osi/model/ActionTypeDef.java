package com.ontologybizruntime.codegen.osi.model;

import java.util.List;

public record ActionTypeDef(
    String id,
    String title,
    String description,
    String kind,
    String operation,
    String aggregate,
    String entityName,
    String idempotency,
    JsonSchemaObject inputSchema,
    List<String> tags
) {}

