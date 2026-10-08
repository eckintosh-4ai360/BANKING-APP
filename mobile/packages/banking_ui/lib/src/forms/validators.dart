/// Form validators that mirror banking-core's rules for early feedback. The backend validates again and its
/// messages are shown when it disagrees.
abstract final class Validators {
  static final _phone = RegExp(r'^\+?[0-9]{7,15}$');
  static final _institution = RegExp(r'^[a-z0-9][a-z0-9-]{1,31}$');
  static final _otp = RegExp(r'^\d{6}$');

  static String? required(String? value, [String message = 'Required']) =>
      value == null || value.trim().isEmpty ? message : null;

  static String? institutionCode(String? value) {
    final text = value?.trim().toLowerCase() ?? '';
    return _institution.hasMatch(text) ? null : 'Enter your institution code';
  }

  static String? phoneNumber(String? value) {
    final text = value?.replaceAll(RegExp(r'[\s-]'), '') ?? '';
    return _phone.hasMatch(text) ? null : 'Enter a phone number of 7 to 15 digits';
  }

  static String? otp(String? value) => _otp.hasMatch(value?.trim() ?? '') ? null : 'Enter the 6-digit code';

  /// Backend policy: 12 to 128 characters with at least 6 different characters (plus a blocklist it checks itself).
  static String? newPassword(String? value) {
    final text = value ?? '';
    final length = text.runes.length;
    if (length < 12) {
      return 'Use at least 12 characters';
    }
    if (length > 128) {
      return 'Use at most 128 characters';
    }
    if (text.runes.toSet().length < 6) {
      return 'Use at least 6 different characters';
    }
    return null;
  }
}
