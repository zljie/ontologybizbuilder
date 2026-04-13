package com.ontologybizruntime.codegen.osi.model;

import java.util.Map;

public record JsonSchemaProperty(
    String type,
    String description,
    Map<String, JsonSchemaProperty> properties,
    JsonSchemaProperty items
) {}

