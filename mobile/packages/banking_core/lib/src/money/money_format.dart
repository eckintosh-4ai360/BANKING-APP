import 'money.dart';

/// Formats [Money] for display by working on the decimal string itself, so every digit of a large balance is kept.
class MoneyFormat {
  const MoneyFormat({this.groupSeparator = ',', this.decimalSeparator = '.', this.useSymbol = true});

  final String groupSeparator;
  final String decimalSeparator;

  /// Local symbol (GH₵) when true, ISO code (GHS) otherwise.
  final bool useSymbol;

  /// Minor units per currency; anything not listed uses two.
  static const _minorUnits = {'XOF': 0, 'XAF': 0, 'UGX': 0, 'RWF': 0, 'JPY': 0, 'KRW': 0};

  static const _symbols = {
    'GHS': 'GH₵',
    'NGN': '₦',
    'KES': 'KSh',
    'ZAR': 'R',
    'USD': r'$',
    'EUR': '€',
    'GBP': '£',
    'XOF': 'CFA',
  };

  static int minorUnitsOf(String currency) => _minorUnits[currency] ?? 2;

  /// `GH₵1,234.50`, `-GH₵25.00`, or `GHS 1,234.50` without symbols. Amounts with more decimals than the currency
  /// uses are rounded half-up for display only.
  String format(Money money) {
    final digits = minorUnitsOf(money.currency);
    final fixed = money.amount.abs().toStringAsFixed(digits);
    final parts = fixed.split('.');
    final whole = _group(parts.first);
    final fraction = parts.length > 1 ? '$decimalSeparator${parts[1]}' : '';
    final sign = money.isNegative && fixed.replaceAll(RegExp('[0.]'), '').isNotEmpty ? '-' : '';
    final unit = useSymbol ? (_symbols[money.currency] ?? '${money.currency} ') : '${money.currency} ';
    return '$sign$unit$whole$fraction';
  }

  String _group(String digits) {
    final buffer = StringBuffer();
    for (var index = 0; index < digits.length; index++) {
      if (index > 0 && (digits.length - index) % 3 == 0) {
        buffer.write(groupSeparator);
      }
      buffer.write(digits[index]);
    }
    return buffer.toString();
  }
}
