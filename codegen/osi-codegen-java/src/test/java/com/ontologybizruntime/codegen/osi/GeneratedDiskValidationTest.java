package com.ontologybizruntime.codegen.osi;

import com.ontologybizruntime.codegen.osi.model.ActionTypeDef;
import com.ontologybizruntime.codegen.osi.model.DatasetDef;
import com.ontologybizruntime.codegen.osi.model.SemanticModelDef;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class GeneratedDiskValidationTest {

  @Test
  void generatedFolderMatchesYamlPaths() throws Exception {
    Path repoRoot = findRepoRoot(Path.of(System.getProperty("user.dir")));
    Path modelPath = repoRoot.resolve("ontologyraw/example/food_semantic_model.yaml");
    assertTrue(Files.exists(modelPath), "model yaml must exist: " + modelPath);

    List<SemanticModelDef> models = new OsiModelParser().parse(modelPath);
    SemanticModelDef target = models.stream()
        .filter(m -> "restaurant_management_model".equals(m.name()))
        .findFirst()
        .orElseThrow();

    Path projectDir = repoRoot.resolve("generated/restaurant_management_model-service");
    assertTrue(Files.exists(projectDir), "generated project must exist: " + projectDir);
    assertTrue(Files.exists(projectDir.resolve("openapi.yaml")));

    String openapi = Files.readString(projectDir.resolve("openapi.yaml"));
    assertFalse(openapi.isBlank());

    String basePackage = detectBasePackage(projectDir);
    assertNotNull(basePackage);

    Path javaRoot = projectDir.resolve("src/main/java").resolve(basePackage.replace('.', '/'));

    List<ActionTypeDef> actions = collectActions(target.datasets());
    assertFalse(actions.isEmpty());

    for (ActionTypeDef a : actions) {
      String id = a.id();
      assertTrue(id != null && !id.isBlank());

      String expectedPath = "/api/" + id;
      assertTrue(openapi.contains("  " + expectedPath + ":\n"), "openapi must contain path: " + expectedPath);

      String controllerName = controllerNameFor(a);
      Path controllerFile = javaRoot.resolve("controller").resolve(controllerName + ".java");
      assertTrue(Files.exists(controllerFile), "controller must exist: " + controllerFile);
      String controller = Files.readString(controllerFile);
      assertTrue(controller.contains("\"/" + id + "\""), "controller must map action path: " + id);
    }
  }

  private static List<ActionTypeDef> collectActions(List<DatasetDef> datasets) {
    List<ActionTypeDef> out = new ArrayList<>();
    for (DatasetDef ds : datasets) {
      if (ds.actionTypes() != null) {
        out.addAll(ds.actionTypes());
      }
    }
    return out;
  }

  private static String controllerNameFor(ActionTypeDef a) {
    String key = a.aggregate();
    if (key == null || key.isBlank()) {
      key = a.entityName();
    }
    if (key == null || key.isBlank()) {
      key = "Actions";
    }
    return Names.toPascal(key) + "Controller";
  }

  private static Path findRepoRoot(Path start) {
    Path p = start;
    for (int i = 0; i < 10; i++) {
      if (p == null) {
        break;
      }
      if (Files.exists(p.resolve("ontologyraw/example/food_semantic_model.yaml"))) {
        return p;
      }
      p = p.getParent();
    }
    throw new IllegalStateException("repo root not found from: " + start);
  }

  private static String detectBasePackage(Path projectDir) throws Exception {
    Path srcMainJava = projectDir.resolve("src/main/java");
    if (!Files.exists(srcMainJava)) {
      throw new IllegalStateException("src/main/java not found: " + srcMainJava);
    }
    try (var walk = Files.walk(srcMainJava)) {
      Path app = walk
          .filter(p -> p.getFileName().toString().equals("Application.java"))
          .findFirst()
          .orElse(null);
      if (app == null) {
        throw new IllegalStateException("Application.java not found under: " + srcMainJava);
      }
      for (String line : Files.readAllLines(app)) {
        String t = line.trim();
        if (t.startsWith("package ") && t.endsWith(";")) {
          return t.substring("package ".length(), t.length() - 1).trim();
        }
      }
      throw new IllegalStateException("package declaration not found: " + app);
    }
  }
}

