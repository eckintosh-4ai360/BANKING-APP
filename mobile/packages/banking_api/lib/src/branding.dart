import 'json.dart';

/// Public white-label branding of an institution (`GET /api/v1/public/institutions/{code}/branding`).
class InstitutionBranding {
  const InstitutionBranding({
    required this.code,
    required this.displayName,
    required this.primaryColor,
    required this.secondaryColor,
    required this.baseCurrency,
    required this.locale,
    required this.enabledFeatures,
    this.logoUrl,
    this.supportEmail,
    this.supportPhone,
  });

  factory InstitutionBranding.fromJson(Object? data) {
    final json = asJsonMap(data, 'branding');
    return InstitutionBranding(
      code: readString(json, 'code'),
      displayName: readString(json, 'displayName'),
      logoUrl: readOptionalString(json, 'logoUrl'),
      primaryColor: readString(json, 'primaryColor'),
      secondaryColor: readString(json, 'secondaryColor'),
      supportEmail: readOptionalString(json, 'supportEmail'),
      supportPhone: readOptionalString(json, 'supportPhone'),
      baseCurrency: readString(json, 'baseCurrency'),
      locale: readString(json, 'locale'),
      enabledFeatures: Set.unmodifiable(readStringList(json, 'enabledFeatures')),
    );
  }

  final String code;
  final String displayName;
  final String? logoUrl;

  /// Hex colour such as `#0B3B60`, validated by the backend.
  final String primaryColor;
  final String secondaryColor;
  final String? supportEmail;
  final String? supportPhone;
  final String baseCurrency;
  final String locale;
  final Set<String> enabledFeatures;

  bool isEnabled(String feature) => enabledFeatures.contains(feature);
}
