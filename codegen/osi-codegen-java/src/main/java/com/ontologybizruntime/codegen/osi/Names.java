package com.ontologybizruntime.codegen.osi;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class Names {
  private Names() {}

  static String pascalFromTokens(List<String> tokens) {
    StringBuilder sb = new StringBuilder();
    for (String t : tokens) {
      if (t == null) {
        continue;
      }
      String s = t.trim();
      if (s.isEmpty()) {
        continue;
      }
      sb.append(Character.toUpperCase(s.charAt(0)));
      if (s.length() > 1) {
        sb.append(s.substring(1));
      }
    }
    return sb.toString();
  }

  static List<String> splitTokens(String raw) {
    if (raw == null) {
      return List.of();
    }
    String normalized = raw.replace('/', ' ')
        .replace('-', ' ')
        .replace('_', ' ')
        .replace('.', ' ')
        .replace(':', ' ');
    String[] parts = normalized.split("\\s+");
    List<String> out = new ArrayList<>();
    for (String p : parts) {
      if (p == null) {
        continue;
      }
      String s = p.trim();
      if (!s.isEmpty()) {
        out.add(s);
      }
    }
    return out;
  }

  static String toPascal(String raw) {
    return pascalFromTokens(splitTokens(raw));
  }

  static String toCamel(String raw) {
    String pascal = toPascal(raw);
    if (pascal.isEmpty()) {
      return pascal;
    }
    return Character.toLowerCase(pascal.charAt(0)) + pascal.substring(1);
  }

  static String safeJavaIdentifier(String raw, String fallback) {
    if (raw == null || raw.isBlank()) {
      return fallback;
    }
    String cleaned = raw.replaceAll("[^A-Za-z0-9_]", "_");
    if (!Character.isJavaIdentifierStart(cleaned.charAt(0))) {
      cleaned = "_" + cleaned;
    }
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < cleaned.length(); i++) {
      char c = cleaned.charAt(i);
      sb.append(Character.isJavaIdentifierPart(c) ? c : '_');
    }
    return sb.toString();
  }

  static String defaultBasePackageFromModelName(String modelName) {
    String base = modelName == null ? "model" : modelName;
    String[] parts = base.toLowerCase(Locale.ROOT).split("[^a-z0-9]+");
    StringBuilder sb = new StringBuilder("com.ontologybizruntime.generated");
    for (String p : parts) {
      if (p == null || p.isBlank()) {
        continue;
      }
      if (Character.isDigit(p.charAt(0))) {
        sb.append(".m").append(p);
      } else {
        sb.append(".").append(p);
      }
    }
    return sb.toString();
  }
}

