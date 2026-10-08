import 'dart:io';

import 'package:banking_core/banking_core.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  const format = MoneyFormat();

  group('Money', () {
    test('parses API decimal strings', () {
      final money = Money.parse('1250.50', 'GHS');
      expect(money.toApiString(), '1250.5');
      expect(money.isNegative, isFalse);
      expect(Money.parse('-0.01', 'GHS').isNegative, isTrue);
      expect(Money.zero('GHS').isZero, isTrue);
    });

    test('keeps every digit beyond 2^53', () {
      final money = Money.parse('9007199254740993.01', 'GHS');
      expect(money.toApiString(), '9007199254740993.01');
      expect(format.format(money), 'GH₵9,007,199,254,740,993.01');
    });

    test('refuses anything that is not a plain decimal amount', () {
      for (final input in ['', 'abc', '1e5', '1,000.00', '12.3.4', 'NaN', 'Infinity', ' ', '+5', '0x10']) {
        expect(Money.tryParse(input, 'GHS'), isNull, reason: input);
      }
      expect(Money.tryParse('10.00', 'cedi'), isNull);
      expect(() => Money.parse('abc', 'GHS'), throwsFormatException);
    });

    test('compares only within one currency', () {
      expect(Money.parse('10', 'GHS').compareTo(Money.parse('9.99', 'GHS')), greaterThan(0));
      expect(Money.parse('10.0', 'GHS'), Money.parse('10.00', 'GHS'));
      expect(() => Money.parse('1', 'GHS').compareTo(Money.parse('1', 'USD')), throwsArgumentError);
    });
  });

  group('MoneyFormat', () {
    test('formats with symbols, grouping and minor units', () {
      expect(format.format(Money.parse('1234.5', 'GHS')), 'GH₵1,234.50');
      expect(format.format(Money.parse('-25', 'GHS')), '-GH₵25.00');
      expect(format.format(Money.parse('1000000', 'NGN')), '₦1,000,000.00');
      expect(format.format(Money.parse('1500', 'XOF')), 'CFA1,500');
      expect(format.format(Money.parse('12.5', 'MAD')), 'MAD 12.50');
      expect(const MoneyFormat(useSymbol: false).format(Money.parse('999', 'GHS')), 'GHS 999.00');
    });

    test('never shows a negative zero', () {
      expect(format.format(Money.parse('-0.001', 'GHS')), 'GH₵0.00');
    });
  });

  // Exit-gate rule for Phase 1D: no binary floating point anywhere money is handled.
  test('money code contains no floating-point types', () {
    final files = [
      ...Directory('lib/src/money').listSync().whereType<File>(),
      File('../banking_ui/lib/src/widgets/money_text.dart'),
    ];
    expect(files.length, greaterThanOrEqualTo(3));
    final forbidden = RegExp(r'\b(double|num|float)\b|toDouble|\.parseDouble');
    for (final file in files) {
      final code = file
          .readAsLinesSync()
          .map((line) => line.replaceFirst(RegExp(r'//.*$'), ''))
          .join('\n');
      expect(forbidden.hasMatch(code), isFalse, reason: '${file.path} uses a floating-point type');
    }
  });
}
