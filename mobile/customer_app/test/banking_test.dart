import 'package:banking_core/testing.dart';
import 'package:customer_app/src/features/onboarding/document_picker.dart';
import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'support.dart';

Map<String, Object?> _stage(String code, String title, String state, {bool required = true}) =>
    {'code': code, 'title': title, 'required': required, 'state': state};

Map<String, Object?> progressJson({bool personalDone = false, String? nextStage = 'PERSONAL_DETAILS', String compliance = 'TO_DO', List<Map<String, Object?>> documents = const []}) => {
      'customerNumber': '0000001234',
      'status': 'PENDING',
      'kycStatus': 'IN_PROGRESS',
      'nextStage': nextStage,
      'stages': [
        _stage('PHONE', 'Verify your phone', 'DONE'),
        _stage('REGISTRATION', 'Register', 'DONE'),
        _stage('PERSONAL_DETAILS', 'Personal details', personalDone ? 'DONE' : 'TO_DO'),
        _stage('IDENTITY_DOCUMENT', 'Identity document', 'TO_DO'),
        _stage('SELFIE', 'Selfie', 'TO_DO'),
        _stage('ADDRESS', 'Address', 'TO_DO'),
        _stage('EMPLOYMENT', 'Work or business', 'TO_DO'),
        _stage('NEXT_OF_KIN', 'Next of kin', 'TO_DO'),
        _stage('SIGNATURE', 'Signature', 'TO_DO', required: false),
        _stage('RISK_PROFILE', 'About your account', 'TO_DO'),
        _stage('COMPLIANCE', 'Verification', compliance),
        _stage('ACTIVATION', 'Open your account', 'TO_DO'),
      ],
      'requirements': [
        {'code': 'SELFIE', 'description': 'Photograph of the customer', 'captured': false, 'accepted': false},
      ],
      'profile': {'firstName': 'Esi', 'lastName': 'Boateng', 'dateOfBirth': '1995-04-12'},
      'documents': documents,
    };

void main() {
  final now = DateTime.utc(2026, 10, 8, 10);
  late FakeBackend server;

  setUp(() => server = FakeBackend());

  Future<void> tallScreen(WidgetTester tester) async {
    await tester.binding.setSurfaceSize(const Size(800, 2400));
    addTearDown(() => tester.binding.setSurfaceSize(null));
  }

  Future<void> tapText(WidgetTester tester, String text) async {
    await tester.tap(find.text(text).last);
    await tester.pumpAndSettle();
  }

  Future<void> fill(WidgetTester tester, String label, String value) async {
    await tester.enterText(find.widgetWithText(TextFormField, label), value);
    await tester.pump();
  }

  testWidgets('an existing customer sets up mobile banking with a texted code, a password and a PIN', (tester) async {
    await tallScreen(tester);
    serveHome(server);
    server
      ..reply('POST', '/api/v1/customer/auth/activation', Reply(202, {'success': true, 'message': 'sent', 'data': codeSentJson(now)}))
      ..reply('POST', '/api/v1/customer/auth/activation/complete', Reply.ok(tokenJson(access: 'access-1', refresh: 'c.t.refresh-1', now: now)));
    await startApp(tester, server, now: now);

    await tapText(tester, 'Set up mobile banking');
    await fill(tester, 'Customer number', '0000001234');
    await fill(tester, 'Phone number', '024 123 4567');
    await tapText(tester, 'Send me a code');
    expect(find.textContaining('we sent a code to +233******567'), findsOneWidget);

    await tester.enterText(find.widgetWithText(TextField, 'Verification code'), '123456');
    await fill(tester, 'Password', 'Kente-Weaver-2027');
    await fill(tester, 'Repeat the password', 'Kente-Weaver-2027');
    await fill(tester, 'Transaction PIN', '1234');
    await fill(tester, 'Repeat the PIN', '1234');
    await tapText(tester, 'Set up');
    expect(find.text('Avoid one digit repeated or a run such as 1234'), findsOneWidget);
    expect(server.calls('POST', '/api/v1/customer/auth/activation/complete'), isEmpty);

    await fill(tester, 'Transaction PIN', '2580');
    await fill(tester, 'Repeat the PIN', '2580');
    await tapText(tester, 'Set up');

    expect(find.text('Hello, Abena Mensah'), findsOneWidget);
    expect(server.calls('POST', '/api/v1/customer/auth/activation').single.data,
        {'institutionCode': 'demo-mfi', 'customerNumber': '0000001234', 'phoneNumber': '0241234567'});
    final completion = server.calls('POST', '/api/v1/customer/auth/activation/complete').single.data as Map;
    expect(completion['challengeToken'], 'tenant-1.challenge-1');
    expect(completion['code'], '123456');
    expect(completion['pin'], '2580');
    expect(completion['password'], 'Kente-Weaver-2027');
  });

  testWidgets('a new phone is confirmed with a texted code', (tester) async {
    serveHome(server);
    server
      ..reply('POST', '/api/v1/customer/auth/login', Reply.ok(mfaChallengeJson(now)))
      ..reply('POST', '/api/v1/customer/auth/device/verify', Reply.ok(tokenJson(access: 'access-1', refresh: 'c.t.refresh-1', now: now)));
    await startApp(tester, server, now: now);

    await tapText(tester, 'Sign in');
    await fill(tester, 'Phone number', '0241234567');
    await fill(tester, 'Password', 'Kente-Weaver-2027');
    await tester.tap(find.widgetWithText(FilledButton, 'Sign in'));
    await tester.pumpAndSettle();
    expect(find.textContaining('This is a new phone'), findsOneWidget);

    await tester.enterText(find.byType(TextField), '654321');
    await tester.pumpAndSettle();

    expect(find.text('Hello, Abena Mensah'), findsOneWidget);
    expect(server.calls('POST', '/api/v1/customer/auth/device/verify').single.data, {'challengeToken': 'challenge-1', 'code': '654321'});
  });

  testWidgets('a transfer retried after a lost answer reuses its idempotency key, so money moves once', (tester) async {
    await tallScreen(tester);
    serveHome(server);
    var attempts = 0;
    server
      ..reply('POST', '/api/v1/customer/transfers/destination', Reply.ok({'accountNumber': '1000000024', 'name': 'Kofi M.', 'currency': 'GHS'}))
      ..on('POST', '/api/v1/customer/transfers', (request) async {
        attempts++;
        if (attempts == 1) {
          throw DioException(requestOptions: request, type: DioExceptionType.receiveTimeout);
        }
        return Reply.ok({
          'transactionId': 'txn-1',
          'reference': 'TXN-20261008-ABC',
          'amount': '250.00',
          'fee': '0.00',
          'currency': 'GHS',
          'fromAccountNumber': '1000000016',
          'toAccountNumber': '1000000024',
          'toName': 'Kofi M.',
          'availableAfter': '1250.00',
          'postedAt': '2026-10-08T10:00:00Z',
        });
      });
    await startApp(tester, server, now: now, signedIn: true);

    expect(find.text('Hello, Abena Mensah'), findsOneWidget);
    await tapText(tester, 'Payments');
    await tapText(tester, 'Send money');
    await tapText(tester, 'Account no.');
    await fill(tester, 'Account number', '1000000024');
    await tester.tap(find.byTooltip('Check the account'));
    await tester.pumpAndSettle();
    expect(find.text('Kofi M.'), findsOneWidget);
    await fill(tester, 'Amount', '250.00');

    await tester.tap(find.widgetWithText(FilledButton, 'Send'));
    await tester.pumpAndSettle();
    await enterPin(tester, '2580');
    expect(find.textContaining('Sending again is safe'), findsOneWidget);

    await tester.tap(find.widgetWithText(FilledButton, 'Send'));
    await tester.pumpAndSettle();
    await enterPin(tester, '2580');

    expect(find.text('Money sent'), findsOneWidget);
    expect(find.text('TXN-20261008-ABC'), findsOneWidget);
    final calls = server.calls('POST', '/api/v1/customer/transfers');
    expect(calls, hasLength(2));
    expect(calls.first.headers['Idempotency-Key'], isNotEmpty);
    expect(calls.last.headers['Idempotency-Key'], calls.first.headers['Idempotency-Key']);
    expect(calls.last.data, {'fromAccountId': 'account-1', 'toAccountNumber': '1000000024', 'amount': '250.00', 'pin': '2580'});
  });

  testWidgets('a refused transfer gets a new idempotency key next time', (tester) async {
    await tallScreen(tester);
    serveHome(server, accounts: [accountJson(), accountJson(id: 'account-2', number: '1000000032', title: 'Savings pot')]);
    server.reply('POST', '/api/v1/customer/transfers', Reply.error(422, 'WRONG_PIN', 'The transaction PIN is wrong.'));
    await startApp(tester, server, now: now, signedIn: true);

    await tapText(tester, 'Payments');
    await tapText(tester, 'Send money');
    await tapText(tester, 'My account');
    await tester.tap(find.widgetWithText(DropdownButtonFormField<String>, 'To'));
    await tester.pumpAndSettle();
    await tapText(tester, 'Savings pot · 1000000032');
    await fill(tester, 'Amount', '20');
    for (var attempt = 0; attempt < 2; attempt++) {
      await tester.tap(find.widgetWithText(FilledButton, 'Send'));
      await tester.pumpAndSettle();
      await enterPin(tester, '9753');
    }

    expect(find.text('The transaction PIN is wrong.'), findsOneWidget);
    final calls = server.calls('POST', '/api/v1/customer/transfers');
    expect(calls, hasLength(2));
    expect(calls.last.headers['Idempotency-Key'], isNot(calls.first.headers['Idempotency-Key']));
    expect((calls.first.data as Map)['toAccountNumber'], '1000000032');
  });

  testWidgets('someone new signs up and works through the stages', (tester) async {
    await tallScreen(tester);
    serveHome(server, profile: profileJson(status: 'PENDING'), accounts: const []);
    var personal = false;
    server
      ..reply('POST', '/api/v1/customer/auth/sign-up', Reply(202, {'success': true, 'message': 'sent', 'data': codeSentJson(now)}))
      ..reply('POST', '/api/v1/customer/auth/sign-up/complete', Reply.ok(tokenJson(access: 'access-1', refresh: 'c.t.refresh-1', now: now)))
      ..on('GET', '/api/v1/customer/onboarding', (_) async => Reply.ok(progressJson(personalDone: personal, nextStage: personal ? 'IDENTITY_DOCUMENT' : 'PERSONAL_DETAILS')))
      ..on('PUT', '/api/v1/customer/onboarding/personal', (_) async {
        personal = true;
        return Reply.ok(progressJson(personalDone: true, nextStage: 'IDENTITY_DOCUMENT'));
      });
    await startApp(tester, server, now: now);

    await tapText(tester, 'Not a customer yet? Open an account');
    await fill(tester, 'Phone number', '0201234567');
    await tapText(tester, 'Send me a code');
    await tester.enterText(find.widgetWithText(TextField, 'Verification code'), '123456');
    await fill(tester, 'First name', 'Esi');
    await fill(tester, 'Last name', 'Boateng');
    await tapText(tester, 'Choose');
    await tapText(tester, 'OK');
    await fill(tester, 'Password', 'Kente-Weaver-2027');
    await fill(tester, 'Repeat the password', 'Kente-Weaver-2027');
    await fill(tester, 'Transaction PIN', '2580');
    await fill(tester, 'Repeat the PIN', '2580');
    await tapText(tester, 'Register');

    expect(find.text('Your customer number is 0000001234'), findsOneWidget);
    final registration = server.calls('POST', '/api/v1/customer/auth/sign-up/complete').single.data as Map;
    expect(registration['firstName'], 'Esi');
    expect(registration['dateOfBirth'], matches(RegExp(r'^\d{4}-\d{2}-\d{2}$')));
    expect(find.text('Optional'), findsOneWidget, reason: 'the signature is not required by this tier');
    expect(find.text('Submit for review'), findsNothing);

    await tapText(tester, 'Personal details');
    await tester.tap(find.widgetWithText(DropdownButtonFormField<String>, 'Gender'));
    await tester.pumpAndSettle();
    await tapText(tester, 'Female');
    await tapText(tester, 'Save');

    expect(server.calls('PUT', '/api/v1/customer/onboarding/personal').single.data,
        {'firstName': 'Esi', 'lastName': 'Boateng', 'dateOfBirth': '1995-04-12', 'gender': 'FEMALE', 'nationality': 'GH'});
    expect(find.text('Your customer number is 0000001234'), findsOneWidget);
  });

  testWidgets('a selfie is photographed and uploaded for review', (tester) async {
    await tallScreen(tester);
    serveHome(server, profile: profileJson(status: 'PENDING'), accounts: const []);
    var uploaded = false;
    server
      ..on('GET', '/api/v1/customer/onboarding', (_) async => Reply.ok(progressJson(
            documents: [if (uploaded) {'id': 'doc-1', 'documentType': 'SELFIE', 'reviewStatus': 'PENDING'}],
          )))
      ..on('POST', '/api/v1/customer/onboarding/documents', (_) async {
        uploaded = true;
        return Reply.ok(progressJson(documents: [
          {'id': 'doc-1', 'documentType': 'SELFIE', 'reviewStatus': 'PENDING'},
        ]));
      });
    final picks = <bool>[];
    await startApp(tester, server, now: now, signedIn: true, picker: ({required bool camera, required bool selfie}) async {
      picks.add(selfie);
      return const PickedDocument(bytes: [0xFF, 0xD8, 0xFF, 0xE0], fileName: 'me.jpg');
    });

    await tapText(tester, 'Selfie');
    expect(find.text('No photo yet'), findsOneWidget);
    await tapText(tester, 'Take photo of your photo');

    expect(find.text('Uploaded'), findsOneWidget);
    expect(picks, [true]);
    final upload = server.calls('POST', '/api/v1/customer/onboarding/documents').single.data as FormData;
    expect(Map.fromEntries(upload.fields), {'documentType': 'SELFIE'});
    expect(upload.files.single.value.filename, 'me.jpg');
  });

  testWidgets('a loan is repaid with the amount the server quoted and the PIN', (tester) async {
    await tallScreen(tester);
    serveHome(server);
    final loan = {
      'id': 'loan-1',
      'loanNumber': 'LN-0001',
      'productName': 'Business loan',
      'currency': 'GHS',
      'principal': '1200.00',
      'annualRate': '24',
      'installments': 6,
      'status': 'ACTIVE',
      'daysPastDue': 0,
      'principalOutstanding': '1200.00',
      'interestDue': '19.35',
      'arrears': '0.00',
      'nextDueDate': '2026-11-01',
      'nextDueAmount': '224.00',
    };
    server
      ..reply('GET', '/api/v1/customer/loans', Reply.ok([loan]))
      ..reply('GET', '/api/v1/customer/loans/loan-1', Reply.ok({
            'loan': loan,
            'schedule': [
              {'number': 1, 'dueDate': '2026-11-01', 'principalDue': '200.00', 'interestDue': '24.00', 'paid': '0.00', 'outstanding': '224.00', 'status': 'DUE'},
            ],
            'repayments': const <Object?>[],
            'payoff': {'asOf': '2026-10-08', 'total': '1219.35'},
          }))
      ..reply('POST', '/api/v1/customer/loans/loan-1/repayments', Reply(201, {
            'success': true,
            'message': 'Repaid',
            'data': {
              'repayment': {'id': 'rep-1', 'amount': '224.00', 'principal': '200.00', 'interest': '24.00', 'penalty': '0.00'},
              'loan': loan,
              'settled': false,
            },
          }));
    await startApp(tester, server, now: now, signedIn: true);

    await tapText(tester, 'Payments');
    await tapText(tester, 'Repay a loan');
    await tapText(tester, 'Business loan · LN-0001');
    expect(find.text('GH₵1,219.35'), findsWidgets);
    await tester.tap(find.widgetWithText(FilledButton, 'Repay'));
    await tester.pumpAndSettle();
    await tester.tap(find.widgetWithText(FilledButton, 'Pay'));
    await tester.pumpAndSettle();
    await enterPin(tester, '2580');

    expect(find.text('Thank you. GH₵224.00 was paid.'), findsOneWidget);
    final call = server.calls('POST', '/api/v1/customer/loans/loan-1/repayments').single;
    expect(call.data, {'amount': '224.00', 'pin': '2580'});
    expect(call.headers['Idempotency-Key'], isNotEmpty);
  });

  testWidgets('the PIN is changed in security, and the inbox marks a notification read', (tester) async {
    await tallScreen(tester);
    serveHome(server);
    server
      ..reply('GET', '/api/v1/customer/notifications/unread', Reply.ok({'count': 1}))
      ..reply('GET', '/api/v1/customer/notifications', Reply.ok({
            'items': [
              {'id': 'n-1', 'category': 'TRANSACTION', 'title': 'Deposit received', 'body': 'GHS 500.00 was paid into your account ending 0016.', 'createdAt': '2026-10-08T09:00:00Z', 'read': false},
            ],
            'page': 0,
            'size': 30,
            'totalItems': 1,
            'totalPages': 1,
          }))
      ..reply('POST', '/api/v1/customer/notifications/n-1/read', Reply.ok(null))
      ..reply('GET', '/api/v1/customer/notifications/preferences', Reply.ok({'smsAlerts': true}))
      ..reply('GET', '/api/v1/customer/security/devices', Reply.ok([
            {'id': 'd-1', 'name': 'Android phone', 'status': 'ACTIVE', 'boundAt': '2026-10-01T09:00:00Z', 'current': true},
          ]))
      ..reply('GET', '/api/v1/customer/security/sessions', Reply.ok(const <Object?>[]))
      ..reply('PUT', '/api/v1/customer/security/pin', Reply.ok(null));
    await startApp(tester, server, now: now, signedIn: true);

    expect(find.text('1'), findsOneWidget, reason: 'unread badge');
    await tapText(tester, 'Inbox');
    await tapText(tester, 'Deposit received');
    expect(server.calls('POST', '/api/v1/customer/notifications/n-1/read'), hasLength(1));

    await tapText(tester, 'More');
    await tapText(tester, 'Security');
    expect(find.text('Android phone (this phone)'), findsOneWidget);
    await tapText(tester, 'Change PIN');
    await fill(tester, 'Current PIN', '2580');
    await fill(tester, 'New transaction PIN', '1397');
    await fill(tester, 'Repeat the PIN', '1397');
    await tester.tap(find.widgetWithText(FilledButton, 'Change PIN'));
    await tester.pumpAndSettle();

    expect(find.text('Your PIN is changed.'), findsOneWidget);
    expect(server.calls('PUT', '/api/v1/customer/security/pin').single.data, {'currentPin': '2580', 'newPin': '1397'});
  });
}
