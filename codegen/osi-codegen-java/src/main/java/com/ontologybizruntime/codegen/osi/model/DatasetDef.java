package com.ontologybizruntime.codegen.osi.model;

import java.util.List;

public record DatasetDef(
    String name,
    String source,
    String description,
    List<FieldDef> fields,
    List<ActionTypeDef> actionTypes
) {}

