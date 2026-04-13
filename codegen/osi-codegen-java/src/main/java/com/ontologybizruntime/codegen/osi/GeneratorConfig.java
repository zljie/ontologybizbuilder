package com.ontologybizruntime.codegen.osi;

import java.nio.file.Path;

public record GeneratorConfig(
    Path modelPath,
    Path outputDir,
    String basePackage,
    String onlyModelName,
    boolean overwrite
) {}

