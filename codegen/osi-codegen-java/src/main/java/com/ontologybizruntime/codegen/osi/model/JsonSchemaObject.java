package com.ontologybizruntime.codegen.osi.model;

import java.util.List;
import java.util.Map;

public record JsonSchemaObject(
    String type,
    boolean additionalProperties,
    List<String> required,
    Map<String, JsonSchemaProperty> properties
) {}

