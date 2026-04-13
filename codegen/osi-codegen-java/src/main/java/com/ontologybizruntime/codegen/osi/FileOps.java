package com.ontologybizruntime.codegen.osi;

import com.squareup.javapoet.JavaFile;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

final class FileOps {
  private FileOps() {}

  static void ensureDir(Path dir) throws IOException {
    Files.createDirectories(dir);
  }

  static void writeString(Path file, String content) throws IOException {
    ensureDir(file.getParent());
    Files.writeString(file, content, StandardCharsets.UTF_8);
  }

  static void writeJava(Path srcRoot, JavaFile javaFile) throws IOException {
    Path outDir = srcRoot;
    ensureDir(outDir);
    javaFile.writeTo(outDir);
  }

  static void deleteRecursively(Path path) throws IOException {
    if (!Files.exists(path)) {
      return;
    }
    try (var walk = Files.walk(path)) {
      walk.sorted((a, b) -> b.compareTo(a)).forEach(p -> {
        try {
          Files.deleteIfExists(p);
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      });
    } catch (RuntimeException e) {
      if (e.getCause() instanceof IOException ioe) {
        throw ioe;
      }
      throw e;
    }
  }
}

