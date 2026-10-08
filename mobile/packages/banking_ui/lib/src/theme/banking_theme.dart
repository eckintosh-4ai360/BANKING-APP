import 'package:banking_api/banking_api.dart';
import 'package:flutter/material.dart';

/// Spacing scale used across the apps.
abstract final class Gaps {
  static const xs = 4.0;
  static const sm = 8.0;
  static const md = 16.0;
  static const lg = 24.0;
  static const xl = 32.0;
}

/// Parses `#RRGGBB` (the backend's validated format). Returns null for anything else.
Color? parseHexColor(String? value) {
  if (value == null || !RegExp(r'^#[0-9A-Fa-f]{6}$').hasMatch(value)) {
    return null;
  }
  return Color(0xFF000000 | int.parse(value.substring(1), radix: 16));
}

/// Material 3 themes built from an institution's branding, so one codebase serves every white-label app.
abstract final class BankingTheme {
  static const fallbackPrimary = Color(0xFF0B3B60);
  static const fallbackSecondary = Color(0xFFF2A900);

  static ThemeData light([InstitutionBranding? branding]) => _build(branding, Brightness.light);

  static ThemeData dark([InstitutionBranding? branding]) => _build(branding, Brightness.dark);

  static ThemeData _build(InstitutionBranding? branding, Brightness brightness) {
    final primary = parseHexColor(branding?.primaryColor) ?? fallbackPrimary;
    final secondary = parseHexColor(branding?.secondaryColor) ?? fallbackSecondary;
    final scheme = ColorScheme.fromSeed(
      seedColor: primary,
      brightness: brightness,
      primary: brightness == Brightness.light ? primary : null,
      secondary: brightness == Brightness.light ? secondary : null,
    );
    return ThemeData(
      useMaterial3: true,
      colorScheme: scheme,
      visualDensity: VisualDensity.standard,
      inputDecorationTheme: const InputDecorationTheme(border: OutlineInputBorder()),
      filledButtonTheme: FilledButtonThemeData(
        style: FilledButton.styleFrom(minimumSize: const Size.fromHeight(48)),
      ),
      outlinedButtonTheme: OutlinedButtonThemeData(
        style: OutlinedButton.styleFrom(minimumSize: const Size.fromHeight(48)),
      ),
    );
  }
}
