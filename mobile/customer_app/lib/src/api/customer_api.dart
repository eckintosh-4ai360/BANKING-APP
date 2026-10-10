import 'package:banking_api/banking_api.dart';
import 'package:banking_core/banking_core.dart';

import 'models.dart';
import 'onboarding.dart';

/// The customer API of banking-core. Money movements carry an idempotency key made once per submission and reused
/// on retry, so the server moves the money once; the transaction PIN goes only in the request that needs it.
class CustomerApi {
  /// [_client] is the signed-in client, [_public] the one for public endpoints.
  CustomerApi({required this._client, required this._public, required this._session});

  final ApiClient _client;
  final ApiClient _public;
  final SessionController _session;

  static const _auth = '/api/v1/customer/auth';
  static const _base = '/api/v1/customer';

  // ---------------------------------------------------------------------------------- setting up and signing up

  /// Texts a code to set up mobile banking when the customer number and phone match (the answer looks the same
  /// either way).
  Future<CodeSent> startActivation({required String institutionCode, required String customerNumber, required String phoneNumber}) =>
      _public.post('$_auth/activation', CodeSent.fromJson,
          body: {'institutionCode': institutionCode, 'customerNumber': customerNumber, 'phoneNumber': phoneNumber});

  /// Chooses the password and PIN with the texted code; this installation becomes trusted and the customer is signed in.
  Future<void> completeActivation({
    required String institutionCode,
    required String challengeToken,
    required String code,
    required String password,
    required String pin,
    String? deviceName,
  }) =>
      _session.exchange('$_auth/activation/complete', {
        'challengeToken': challengeToken,
        'code': code,
        'password': password,
        'pin': pin,
        'deviceName': ?deviceName,
      }, institutionCode: institutionCode);

  /// Signing up as a new customer: texts a code to the phone number.
  Future<CodeSent> startSignUp({required String institutionCode, required String phoneNumber}) =>
      _public.post('$_auth/sign-up', CodeSent.fromJson, body: {'institutionCode': institutionCode, 'phoneNumber': phoneNumber});

  /// Registers with the texted code and signs in; the details are completed in the sign-up stages.
  Future<void> completeSignUp({
    required String institutionCode,
    required String challengeToken,
    required String code,
    required String firstName,
    required String lastName,
    required DateTime dateOfBirth,
    required String password,
    required String pin,
    String? deviceName,
  }) =>
      _session.exchange('$_auth/sign-up/complete', {
        'challengeToken': challengeToken,
        'code': code,
        'firstName': firstName,
        'lastName': lastName,
        'dateOfBirth': isoDate(dateOfBirth),
        'password': password,
        'pin': pin,
        'deviceName': ?deviceName,
      }, institutionCode: institutionCode);

  Future<CodeSent> startPasswordReset({required String institutionCode, required String phoneNumber}) =>
      _public.post('$_auth/password/reset', CodeSent.fromJson, body: {'institutionCode': institutionCode, 'phoneNumber': phoneNumber});

  /// A new password with the texted code and the transaction PIN; every session ends.
  Future<void> completePasswordReset({required String challengeToken, required String code, required String pin, required String newPassword}) =>
      _public.post('$_auth/password/reset/complete', (_) {},
          body: {'challengeToken': challengeToken, 'code': code, 'pin': pin, 'newPassword': newPassword});

  // ---------------------------------------------------------------------------------------------- the customer

  Future<CustomerProfile> me() => _client.get('$_base/me', CustomerProfile.fromJson);

  // -------------------------------------------------------------------------------------------------- accounts

  Future<AccountsOverview> accounts() => _client.get('$_base/accounts', AccountsOverview.fromJson);

  Future<AccountStatement> statement(String accountId, {DateTime? from, DateTime? to}) => _client.get(
        '$_base/accounts/$accountId/statement',
        AccountStatement.fromJson,
        query: {'from': ?(from == null ? null : isoDate(from)), 'to': ?(to == null ? null : isoDate(to))},
      );

  // ------------------------------------------------------------------------------------------------- transfers

  Future<TransferDestination> destination(String accountNumber) =>
      _client.post('$_base/transfers/destination', TransferDestination.fromJson, body: {'accountNumber': accountNumber});

  /// [amount] is the decimal string the customer typed, sent as is; the server checks it against the balance and
  /// the institution's limits.
  Future<TransferReceipt> transfer({
    required String idempotencyKey,
    required String fromAccountId,
    String? beneficiaryId,
    String? toAccountNumber,
    required String amount,
    String? narration,
    required String pin,
  }) =>
      _client.post(
        '$_base/transfers',
        TransferReceipt.fromJson,
        idempotencyKey: idempotencyKey,
        body: {
          'fromAccountId': fromAccountId,
          'beneficiaryId': ?beneficiaryId,
          'toAccountNumber': ?toAccountNumber,
          'amount': amount,
          'narration': ?narration,
          'pin': pin,
        },
      );

  Future<List<Beneficiary>> beneficiaries() => _client.get('$_base/beneficiaries', (data) => parseList(data, Beneficiary.fromJson));

  Future<Beneficiary> addBeneficiary({required String accountNumber, required String nickname, required String pin}) => _client.post(
        '$_base/beneficiaries',
        (data) => Beneficiary.fromJson(asJsonMap(data)),
        body: {'type': 'INTERNAL', 'accountNumber': accountNumber, 'nickname': nickname, 'favourite': false, 'pin': pin},
      );

  Future<void> removeBeneficiary(String id) => _client.delete('$_base/beneficiaries/$id');

  // ----------------------------------------------------------------------------------------- loans and susu

  Future<List<CustomerLoan>> loans() => _client.get('$_base/loans', (data) => parseList(data, CustomerLoan.fromJson));

  Future<LoanDetail> loan(String id) => _client.get('$_base/loans/$id', LoanDetail.fromJson);

  /// From the loan's repayment account, confirmed with the PIN.
  Future<LoanRepaymentReceipt> repayLoan({required String idempotencyKey, required String loanId, required String amount, required String pin}) =>
      _client.post('$_base/loans/$loanId/repayments', LoanRepaymentReceipt.fromJson,
          idempotencyKey: idempotencyKey, body: {'amount': amount, 'pin': pin});

  Future<List<SusuPlan>> susuPlans() => _client.get('$_base/susu-plans', (data) => parseList(data, SusuPlan.fromJson));

  Future<SusuPlanDetail> susuPlan(String id) => _client.get('$_base/susu-plans/$id', SusuPlanDetail.fromJson);

  // ------------------------------------------------------------------------------------------- notifications

  Future<NotificationPage> notifications({int page = 0}) =>
      _client.get('$_base/notifications', NotificationPage.fromJson, query: {'page': page, 'size': 30});

  Future<int> unreadNotifications() => _client.get('$_base/notifications/unread', (data) {
        final count = asJsonMap(data)['count'];
        if (count is int) {
          return count;
        }
        throw const FormatException('Expected "count" to be a whole number');
      });

  Future<void> markRead(String id) => _client.post('$_base/notifications/$id/read', (_) {});

  Future<void> markAllRead() => _client.post('$_base/notifications/read', (_) {});

  Future<bool> smsAlerts() => _client.get('$_base/notifications/preferences', (data) => readBool(asJsonMap(data), 'smsAlerts'));

  Future<bool> setSmsAlerts(bool enabled) =>
      _client.put('$_base/notifications/preferences', (data) => readBool(asJsonMap(data), 'smsAlerts'), body: {'smsAlerts': enabled});

  /// Registers this installation for push notifications with the token the platform's push service issued.
  Future<void> registerPush({required String provider, required String token}) =>
      _client.put('$_base/notifications/push', (_) {}, body: {'provider': provider, 'token': token});

  // ------------------------------------------------------------------------------------------------ security

  Future<List<TrustedDevice>> devices() => _client.get('$_base/security/devices', (data) => parseList(data, TrustedDevice.fromJson));

  /// The device is no longer trusted and its sessions end.
  Future<void> revokeDevice(String id) => _client.delete('$_base/security/devices/$id');

  Future<List<SignedInSession>> sessions() => _client.get('$_base/security/sessions', (data) => parseList(data, SignedInSession.fromJson));

  Future<void> endSession(String id) => _client.delete('$_base/security/sessions/$id');

  Future<void> changePin({required String currentPin, required String newPin}) =>
      _client.put('$_base/security/pin', (_) {}, body: {'currentPin': currentPin, 'newPin': newPin});

  /// Texts a code to reset a forgotten (or locked) PIN.
  Future<CodeSent> startPinReset() => _client.post('$_base/security/pin/reset', CodeSent.fromJson);

  Future<void> completePinReset({required String challengeToken, required String code, required String password, required String newPin}) =>
      _client.post('$_base/security/pin/reset/complete', (_) {},
          body: {'challengeToken': challengeToken, 'code': code, 'password': password, 'newPin': newPin});

  // ------------------------------------------------------------------------------------------------- signing up

  static const _onboarding = '$_base/onboarding';

  /// Where the person is in signing up. Throws `NOT_SIGNING_UP` (409) for a customer who did not sign up in the app.
  Future<OnboardingProgress> onboarding() => _client.get(_onboarding, OnboardingProgress.fromJson);

  Future<List<IdType>> idTypes() => _client.get('$_onboarding/id-types', (data) => parseList(data, IdType.fromJson));

  Future<OnboardingProgress> savePersonal(JsonMap details) =>
      _client.put('$_onboarding/personal', OnboardingProgress.fromJson, body: details);

  Future<OnboardingProgress> saveEmployment(JsonMap details) =>
      _client.put('$_onboarding/employment', OnboardingProgress.fromJson, body: details);

  Future<OnboardingProgress> saveIdentification(JsonMap details) =>
      _client.put('$_onboarding/identification', OnboardingProgress.fromJson, body: details);

  /// A photo or scan (JPEG, PNG or PDF; the server checks the content): ID_FRONT, ID_BACK, SELFIE, SIGNATURE or
  /// PROOF_OF_ADDRESS.
  Future<OnboardingProgress> uploadDocument({required String documentType, required List<int> bytes, required String fileName}) =>
      _client.upload('$_onboarding/documents', OnboardingProgress.fromJson,
          bytes: bytes, fileName: fileName, fields: {'documentType': documentType});

  Future<OnboardingProgress> saveAddress(JsonMap address) =>
      _client.put('$_onboarding/address', OnboardingProgress.fromJson, body: address);

  Future<OnboardingProgress> saveNextOfKin(JsonMap nextOfKin) =>
      _client.put('$_onboarding/next-of-kin', OnboardingProgress.fromJson, body: nextOfKin);

  Future<OnboardingProgress> saveRiskProfile(RiskProfile answers) =>
      _client.put('$_onboarding/risk-profile', OnboardingProgress.fromJson, body: answers.toJson());

  /// Submits the details for review by the institution's staff.
  Future<OnboardingProgress> submitSignUp() => _client.post('$_onboarding/submit', OnboardingProgress.fromJson);

  /// Opens the first account once the details are approved (opening it again returns the same account).
  Future<OnboardingProgress> openFirstAccount() => _client.post('$_onboarding/account', OnboardingProgress.fromJson);

  /// Changes the password; the server ends the other sessions and issues fresh tokens for this one.
  Future<void> changePassword({required String currentPassword, required String newPassword}) =>
      _session.changePassword(currentPassword: currentPassword, newPassword: newPassword);
}

/// `2027-03-01`.
String isoDate(DateTime date) =>
    '${date.year.toString().padLeft(4, '0')}-${date.month.toString().padLeft(2, '0')}-${date.day.toString().padLeft(2, '0')}';
