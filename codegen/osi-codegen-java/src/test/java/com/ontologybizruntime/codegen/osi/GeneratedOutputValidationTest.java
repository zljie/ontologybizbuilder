package com.ontologybizruntime.codegen.osi;

import com.ontologybizruntime.codegen.osi.model.ActionTypeDef;
import com.ontologybizruntime.codegen.osi.model.DatasetDef;
import com.ontologybizruntime.codegen.osi.model.SemanticModelDef;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class GeneratedOutputValidationTest {

  @TempDir
  Path tmpDir;

  @Test
  void generatedFilesMatchActionPathsFromYaml() throws Exception {
    Path repoRoot = findRepoRoot(Path.of(System.getProperty("user.dir")));
    Path modelPath = repoRoot.resolve("ontologyraw/example/food_semantic_model.yaml");
    assertTrue(Files.exists(modelPath), "model yaml must exist: " + modelPath);

    List<SemanticModelDef> models = new OsiModelParser().parse(modelPath);
    SemanticModelDef target = models.stream()
        .filter(m -> "restaurant_management_model".equals(m.name()))
        .findFirst()
        .orElseThrow();

    String basePackage = "com.ontologybizruntime.generated.test";
    GeneratorConfig cfg = new GeneratorConfig(modelPath, tmpDir, basePackage, target.name(), true);
    new OsiSpringBootGenerator().generate(cfg);

    Path projectDir = tmpDir.resolve("restaurant_management_model-service");
    assertTrue(Files.exists(projectDir.resolve("pom.xml")));
    assertTrue(Files.exists(projectDir.resolve("src/main/resources/application.yml")));
    assertTrue(Files.exists(projectDir.resolve("openapi.yaml")));

    String openapi = Files.readString(projectDir.resolve("openapi.yaml"));
    assertFalse(openapi.isBlank());

    String pkgPath = basePackage.replace('.', '/');
    Path javaRoot = projectDir.resolve("src/main/java").resolve(pkgPath);

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

  @Test
  void generatedDatasetDtosExist() throws Exception {
    Path repoRoot = findRepoRoot(Path.of(System.getProperty("user.dir")));
    Path modelPath = repoRoot.resolve("ontologyraw/example/food_semantic_model.yaml");

    List<SemanticModelDef> models = new OsiModelParser().parse(modelPath);
    SemanticModelDef target = models.stream()
        .filter(m -> "restaurant_management_model".equals(m.name()))
        .findFirst()
        .orElseThrow();

    String basePackage = "com.ontologybizruntime.generated.test";
    GeneratorConfig cfg = new GeneratorConfig(modelPath, tmpDir, basePackage, target.name(), true);
    new OsiSpringBootGenerator().generate(cfg);

    Path projectDir = tmpDir.resolve("restaurant_management_model-service");
    String pkgPath = basePackage.replace('.', '/');
    Path dtoDir = projectDir.resolve("src/main/java").resolve(pkgPath).resolve("dto");

    for (DatasetDef ds : target.datasets()) {
      String dto = Names.toPascal(ds.name()) + "Dto.java";
      assertTrue(Files.exists(dtoDir.resolve(dto)), "dataset dto must exist: " + dto);
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
    for (int i = 0; i < 8; i++) {
      if (p == null) {
        break;
      }
      Path marker = p.resolve("ontologyraw/example/food_semantic_model.yaml");
      if (Files.exists(marker)) {
        return p;
      }
      p = p.getParent();
    }
    throw new IllegalStateException("repo root not found from: " + start);
  }
}
