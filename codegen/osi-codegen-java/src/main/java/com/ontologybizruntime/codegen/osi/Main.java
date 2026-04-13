package com.ontologybizruntime.codegen.osi;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public final class Main {

  public static void main(String[] args) throws Exception {
    ParsedArgs parsed = ParsedArgs.parse(args);
    if (parsed.help) {
      System.out.println(ParsedArgs.usage());
      return;
    }

    if (parsed.modelPath == null || parsed.outputDir == null) {
      System.err.println("Missing required args: --model <path> --output <dir>");
      System.err.println();
      System.err.println(ParsedArgs.usage());
      System.exit(2);
      return;
    }

    GeneratorConfig config = new GeneratorConfig(
        Path.of(parsed.modelPath),
        Path.of(parsed.outputDir),
        parsed.basePackage,
        parsed.onlyModelName,
        parsed.overwrite
    );
    new OsiSpringBootGenerator().generate(config);
  }

  static final class ParsedArgs {
    final boolean help;
    final String modelPath;
    final String outputDir;
    final String basePackage;
    final String onlyModelName;
    final boolean overwrite;

    ParsedArgs(
        boolean help,
        String modelPath,
        String outputDir,
        String basePackage,
        String onlyModelName,
        boolean overwrite
    ) {
      this.help = help;
      this.modelPath = modelPath;
      this.outputDir = outputDir;
      this.basePackage = basePackage;
      this.onlyModelName = onlyModelName;
      this.overwrite = overwrite;
    }

    static ParsedArgs parse(String[] args) {
      Map<String, String> kv = new LinkedHashMap<>();
      boolean help = false;
      boolean overwrite = false;
      for (int i = 0; i < args.length; i++) {
        String a = args[i];
        if ("--help".equals(a) || "-h".equals(a)) {
          help = true;
          continue;
        }
        if ("--overwrite".equals(a)) {
          overwrite = true;
          continue;
        }
        if (a.startsWith("--")) {
          String key = a.substring(2);
          String value = null;
          if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
            value = args[i + 1];
            i++;
          }
          kv.put(key, value);
        }
      }

      return new ParsedArgs(
          help,
          kv.get("model"),
          kv.get("output"),
          kv.get("base-package"),
          kv.get("only-model"),
          overwrite
      );
    }

    static String usage() {
      return String.join(
          System.lineSeparator(),
          "osi-codegen-java",
          "",
          "Usage:",
          "  java -jar osi-codegen-java-0.1.0.jar --model <semantic_model.yaml> --output <dir> [options]",
          "",
          "Required:",
          "  --model <path>        Path to OSI semantic model YAML",
          "  --output <dir>        Output directory (will create <modelName>-service subdir)",
          "",
          "Options:",
          "  --base-package <pkg>  Base Java package for generated code (default: derived from model name)",
          "  --only-model <name>   Only generate the specified semantic_model.name",
          "  --overwrite           Overwrite existing output subdir if exists",
          "  --help, -h            Show help",
          ""
      );
    }
  }
}

