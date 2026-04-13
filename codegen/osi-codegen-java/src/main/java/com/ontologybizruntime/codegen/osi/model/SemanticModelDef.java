package com.ontologybizruntime.codegen.osi.model;

import java.util.List;

public record SemanticModelDef(
    String name,
    String description,
    List<DatasetDef> datasets
) {}

