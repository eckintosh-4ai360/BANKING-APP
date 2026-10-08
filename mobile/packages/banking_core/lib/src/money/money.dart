import 'package:decimal/decimal.dart';

/// An amount of money exactly as banking-core calculated it.
///
/// Amounts arrive as decimal strings and are held as [Decimal]; there is deliberately no constructor from a
/// binary floating-point number and no arithmetic. The apps display balances and amounts; the backend calculates
/// them (spec rules: never trust amounts calculated by the frontend, never use frontend balances as authoritative).
/// A test in this package fails the build if a floating-point type appears in the money code.
final class Money implements Comparable<Money> {
  const Money._(this.amount, this.currency);

  /// Parses an API amount such as `"1250.00"`; throws [FormatException] for anything else.
  factory Money.parse(String amount, String currency) {
    final money = tryParse(amount, currency);
    if (money == null) {
      throw FormatException('Not a valid money amount', amount);
    }
    return money;
  }

  /// Zero in a currency, e.g. for an account that has no balance yet.
  factory Money.zero(String currency) => Money.parse('0', currency);

  static final _amountPattern = RegExp(r'^-?\d{1,30}(\.\d{1,10})?$');
  static final _currencyPattern = RegExp(r'^[A-Z]{3}$');

  static Money? tryParse(String? amount, String? currency) {
    if (amount == null || currency == null) {
      return null;
    }
    final trimmed = amount.trim();
    if (!_amountPattern.hasMatch(trimmed) || !_currencyPattern.hasMatch(currency)) {
      return null;
    }
    final value = Decimal.tryParse(trimmed);
    return value == null ? null : Money._(value, currency);
  }

  final Decimal amount;

  /// ISO 4217 code.
  final String currency;

  bool get isNegative => amount.sign < 0;

  bool get isZero => amount.sign == 0;

  /// The decimal string to send back to the API (never a JSON number).
  String toApiString() => amount.toString();

  @override
  int compareTo(Money other) {
    if (other.currency != currency) {
      throw ArgumentError('Cannot compare $currency with ${other.currency}');
    }
    return amount.compareTo(other.amount);
  }

  @override
  bool operator ==(Object other) => other is Money && other.currency == currency && other.amount == amount;

  @override
  int get hashCode => Object.hash(amount, currency);

  @override
  String toString() => '$currency ${amount.toString()}';
}
