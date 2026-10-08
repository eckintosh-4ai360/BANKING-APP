import 'package:banking_api/banking_api.dart';
import 'package:banking_core/banking_core.dart';
import 'package:banking_ui/banking_ui.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

Widget host(Widget child) => MaterialApp(theme: BankingTheme.light(), home: Scaffold(body: Center(child: child)));

void main() {
  group('theme', () {
    test('parses only #RRGGBB colours', () {
      expect(parseHexColor('#0B3B60'), const Color(0xFF0B3B60));
      for (final bad in [null, '', '0B3B60', '#0B3B6', '#GGGGGG', 'red', '#0B3B60FF']) {
        expect(parseHexColor(bad), isNull, reason: '$bad');
      }
    });

    test('uses the institution colours and falls back safely', () {
      const branding = InstitutionBranding(
        code: 'demo-mfi',
        displayName: 'Demo MFI',
        primaryColor: '#7A1F5C',
        secondaryColor: '#118844',
        baseCurrency: 'GHS',
        locale: 'en-GH',
        enabledFeatures: {},
      );
      expect(BankingTheme.light(branding).colorScheme.primary, const Color(0xFF7A1F5C));
      expect(BankingTheme.light(branding).colorScheme.secondary, const Color(0xFF118844));
      expect(BankingTheme.light().colorScheme.primary, BankingTheme.fallbackPrimary);
      expect(BankingTheme.dark(branding).brightness, Brightness.dark);
    });
  });

  testWidgets('PrimaryButton runs its action once even when tapped repeatedly', (tester) async {
    var runs = 0;
    await tester.pumpWidget(host(PrimaryButton(
      label: 'Sign in',
      onPressed: () {
        runs++;
        return Future<void>.delayed(const Duration(milliseconds: 300));
      },
    )));

    await tester.tap(find.text('Sign in'));
    await tester.pump();
    expect(find.byType(CircularProgressIndicator), findsOneWidget);
    await tester.tap(find.byType(FilledButton));
    await tester.tap(find.byType(FilledButton));
    await tester.pump(const Duration(milliseconds: 400));

    expect(runs, 1);
    expect(find.text('Sign in'), findsOneWidget);
  });

  group('money widgets', () {
    testWidgets('MoneyText formats server amounts and marks negatives', (tester) async {
      await tester.pumpWidget(host(MoneyText(Money.parse('-1250.5', 'GHS'))));
      final text = tester.widget<Text>(find.text('-GH₵1,250.50'));
      expect(text.style?.color, BankingTheme.light().colorScheme.error);
    });

    testWidgets('BalanceText starts hidden and reveals on request', (tester) async {
      await tester.pumpWidget(host(BalanceText(Money.parse('5400', 'GHS'), asOf: '08 Oct 2026, 10:00')));
      expect(find.text('GH₵5,400.00'), findsNothing);
      expect(find.text('••••••'), findsOneWidget);
      expect(find.text('As of 08 Oct 2026, 10:00'), findsOneWidget);

      await tester.tap(find.byTooltip('Show balance'));
      await tester.pump();
      expect(find.text('GH₵5,400.00'), findsOneWidget);

      await tester.tap(find.byTooltip('Hide balance'));
      await tester.pump();
      expect(find.text('GH₵5,400.00'), findsNothing);
    });
  });

  group('code entry', () {
    testWidgets('PinKeypad never shows digits and hands over the PIN once complete', (tester) async {
      String? pin;
      await tester.pumpWidget(host(PinKeypad(length: 4, onCompleted: (value) => pin = value)));

      for (final digit in ['1', '9', '7']) {
        await tester.tap(find.widgetWithText(OutlinedButton, digit));
        await tester.pump();
      }
      await tester.tap(find.byTooltip('Delete'));
      await tester.pump();
      expect(find.bySemanticsLabel('2 of 4 digits entered'), findsOneWidget);
      expect(find.text('197'), findsNothing);

      await tester.tap(find.widgetWithText(OutlinedButton, '3'));
      await tester.tap(find.widgetWithText(OutlinedButton, '4'));
      await tester.pump();

      expect(pin, '1934');
      expect(find.bySemanticsLabel('0 of 4 digits entered'), findsOneWidget);
    });

    testWidgets('OtpField accepts digits only and completes at six', (tester) async {
      final controller = TextEditingController();
      String? completed;
      await tester.pumpWidget(host(SizedBox(width: 300, child: OtpField(controller: controller, onCompleted: (code) => completed = code))));

      await tester.enterText(find.byType(TextField), '12a34');
      expect(controller.text, '1234');
      expect(completed, isNull);

      await tester.enterText(find.byType(TextField), '123456789');
      expect(controller.text, '123456');
      expect(completed, '123456');
    });
  });

  group('common widgets', () {
    testWidgets('PasswordField hides text until asked', (tester) async {
      final controller = TextEditingController(text: 'secret');
      await tester.pumpWidget(host(SizedBox(width: 300, child: PasswordField(controller: controller))));
      expect(tester.widget<EditableText>(find.byType(EditableText)).obscureText, isTrue);
      await tester.tap(find.byTooltip('Show password'));
      await tester.pump();
      expect(tester.widget<EditableText>(find.byType(EditableText)).obscureText, isFalse);
    });

    testWidgets('InstitutionHeader falls back to initials and ignores non-https logos', (tester) async {
      await tester.pumpWidget(host(const InstitutionHeader(name: 'Akwaaba Savings and Loans', logoUrl: 'http://cdn.example/logo.png')));
      expect(find.text('AS'), findsOneWidget);
      expect(find.byType(Image), findsNothing);
      expect(InstitutionHeader.initialsOf('  '), '?');
    });

    testWidgets('FeatureTile shows why a feature is unavailable instead of a dead button', (tester) async {
      var opened = false;
      await tester.pumpWidget(host(Column(children: [
        FeatureTile(icon: Icons.people, title: 'Customers', onTap: () => opened = true),
        const FeatureTile(icon: Icons.savings, title: 'Collections', unavailableReason: 'Arrives with susu collections'),
      ])));
      await tester.tap(find.text('Collections'));
      expect(find.text('Arrives with susu collections'), findsOneWidget);
      await tester.tap(find.text('Customers'));
      expect(opened, isTrue);
    });

    testWidgets('ErrorView offers a retry', (tester) async {
      var retried = false;
      await tester.pumpWidget(host(ErrorView(message: 'No connection', onRetry: () => retried = true)));
      await tester.tap(find.text('Try again'));
      expect(retried, isTrue);
    });
  });

  group('InactivityGuard', () {
    testWidgets('times out without interaction and resets on touch', (tester) async {
      var timeouts = 0;
      await tester.pumpWidget(host(InactivityGuard(
        timeout: const Duration(minutes: 5),
        enabled: true,
        onTimeout: () => timeouts++,
        child: const SizedBox(width: 200, height: 200, child: Text('content')),
      )));

      await tester.pump(const Duration(minutes: 4));
      await tester.tap(find.text('content'));
      await tester.pump(const Duration(minutes: 4));
      expect(timeouts, 0);

      await tester.pump(const Duration(minutes: 2));
      expect(timeouts, 1);
    });

    testWidgets('times out when the app comes back after a long absence', (tester) async {
      var now = DateTime.utc(2026, 10, 8, 10);
      var timeouts = 0;
      await tester.pumpWidget(host(InactivityGuard(
        timeout: const Duration(minutes: 5),
        enabled: true,
        clock: () => now,
        onTimeout: () => timeouts++,
        child: const Text('content'),
      )));

      tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.inactive);
      tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.hidden);
      tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.paused);
      now = now.add(const Duration(minutes: 30));
      tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.hidden);
      tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.inactive);
      tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.resumed);
      await tester.pump();

      expect(timeouts, 1);
    });

    testWidgets('does nothing while disabled', (tester) async {
      var timeouts = 0;
      await tester.pumpWidget(host(InactivityGuard(
        timeout: const Duration(minutes: 5),
        enabled: false,
        onTimeout: () => timeouts++,
        child: const Text('content'),
      )));
      await tester.pump(const Duration(hours: 1));
      expect(timeouts, 0);
    });
  });

  group('Validators', () {
    test('mirror the backend rules', () {
      expect(Validators.institutionCode('Demo-MFI'), isNull);
      expect(Validators.institutionCode('x'), isNotNull);
      expect(Validators.phoneNumber('+233 24 123 4567'), isNull);
      expect(Validators.phoneNumber('12-34'), isNotNull);
      expect(Validators.otp('123456'), isNull);
      expect(Validators.otp('12345a'), isNotNull);
      expect(Validators.newPassword('short'), contains('12'));
      expect(Validators.newPassword('aaaaaaaaaaab'), contains('6 different'));
      expect(Validators.newPassword('correct horse battery'), isNull);
      expect(Validators.required('  '), 'Required');
    });
  });
}
