import 'package:banking_api/banking_api.dart';
import 'package:test/test.dart';

void main() {
  group('TokenResponse', () {
    test('parses a token pair', () {
      final tokens = TokenResponse.fromJson({
        'tokenType': 'Bearer',
        'accessToken': 'access',
        'accessTokenExpiresAt': '2026-10-08T10:10:00Z',
        'refreshToken': 's.tenant.secret',
        'refreshTokenExpiresAt': '2026-10-08T18:00:00Z',
        'passwordChangeRequired': true,
        'mfaEnrollmentRequired': false,
        'mfaRequired': false,
        'mfaChallengeToken': null,
        'mfaChallengeExpiresAt': null,
      });
      expect(tokens.hasTokens, isTrue);
      expect(tokens.passwordChangeRequired, isTrue);
      expect(tokens.accessTokenExpiresAt, DateTime.utc(2026, 10, 8, 10, 10));
    });

    test('parses an MFA challenge without tokens', () {
      final tokens = TokenResponse.fromJson({
        'mfaRequired': true,
        'mfaChallengeToken': 'challenge',
        'mfaChallengeExpiresAt': '2026-10-08T10:05:00Z',
      });
      expect(tokens.mfaRequired, isTrue);
      expect(tokens.hasTokens, isFalse);
    });

    test('never prints tokens or passwords', () {
      final tokens = TokenResponse.fromJson({'accessToken': 'secret-access', 'refreshToken': 'secret-refresh'});
      expect(tokens.toString(), isNot(contains('secret')));
      const login = StaffLoginRequest(tenantCode: 'demo-mfi', username: 'teller', password: 'Demo@Pass2026');
      expect(login.toString(), isNot(contains('Demo@Pass2026')));
      const customer = CustomerLoginRequest(institutionCode: 'demo-mfi', phoneNumber: '0241234567', password: 'pw');
      expect(customer.toString(), isNot(contains('0241234567')));
    });

    test('rejects malformed instants', () {
      expect(() => TokenResponse.fromJson({'accessTokenExpiresAt': 'yesterday'}), throwsFormatException);
    });
  });

  group('StaffProfile', () {
    test('parses /me', () {
      final profile = StaffProfile.fromJson({
        'id': 's1',
        'tenantId': 't1',
        'tenantCode': 'demo-mfi',
        'institutionName': 'Demo MFI',
        'username': 'fieldofficer',
        'firstName': 'Kofi',
        'lastName': 'Boateng',
        'email': 'kofi@example.test',
        'homeBranchId': 'b1',
        'allBranchesAccess': false,
        'roles': [
          {'id': 'r1', 'code': 'FIELD_OFFICER', 'name': 'Field officer'},
        ],
        'permissions': ['customer.view', 'customer.create'],
        'passwordChangeRequired': false,
      });
      expect(profile.displayName, 'Kofi Boateng');
      expect(profile.roles.single.code, 'FIELD_OFFICER');
      expect(profile.can('customer.create'), isTrue);
      expect(profile.can('kyc.approve'), isFalse);
    });

    test('fails loudly on a missing field', () {
      expect(() => StaffProfile.fromJson({'id': 's1'}), throwsFormatException);
    });
  });

  group('InstitutionBranding', () {
    test('parses public branding', () {
      final branding = InstitutionBranding.fromJson({
        'code': 'demo-mfi',
        'displayName': 'Demo MFI',
        'logoUrl': null,
        'primaryColor': '#0B3B60',
        'secondaryColor': '#F2A900',
        'supportEmail': 'help@demo.test',
        'supportPhone': '+233302000000',
        'baseCurrency': 'GHS',
        'locale': 'en-GH',
        'enabledFeatures': ['SAVINGS'],
      });
      expect(branding.isEnabled('SAVINGS'), isTrue);
      expect(branding.isEnabled('LOANS'), isFalse);
    });
  });

  group('envelopes', () {
    test('unwraps success data', () {
      final value = unwrapData({'success': true, 'message': 'OK', 'data': 'x'}, (data) => data);
      expect(value, 'x');
    });

    test('parses error bodies with field errors', () {
      final error = ApiErrorBody.tryParse({
        'success': false,
        'code': 'VALIDATION_FAILED',
        'message': 'Invalid request',
        'traceId': 'trace-1',
        'errors': [
          {'field': 'username', 'message': 'must not be blank'},
        ],
      });
      expect(error?.code, 'VALIDATION_FAILED');
      expect(error?.fieldErrors.single.field, 'username');
      expect(ApiErrorBody.tryParse('<html>'), isNull);
      expect(ApiErrorBody.tryParse({'success': true}), isNull);
    });
  });
}
