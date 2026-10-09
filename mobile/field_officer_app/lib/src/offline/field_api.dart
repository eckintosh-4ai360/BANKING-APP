import 'package:banking_api/banking_api.dart';
import 'package:banking_core/banking_core.dart';

/// The officer as of the last sync: the limits the phone enforces offline and the cash the ledger says they carry.
class OfficerSnapshot {
  const OfficerSnapshot({
    required this.firstName,
    required this.currency,
    required this.cashBalance,
    required this.maxOfflineAmount,
    required this.maxOfflineHours,
    required this.status,
  });

  factory OfficerSnapshot.fromJson(Object? data) {
    final officer = asJsonMap(asJsonMap(data)['officer'], 'officer');
    return OfficerSnapshot(
      firstName: readString(officer, 'firstName'),
      currency: readString(officer, 'currency'),
      cashBalance: _amount(officer, 'cashBalance'),
      maxOfflineAmount: _amount(officer, 'maxOfflineAmount'),
      maxOfflineHours: _int(officer, 'maxOfflineHours'),
      status: readString(officer, 'status'),
    );
  }

  final String firstName;
  final String currency;
  final String cashBalance;
  final String maxOfflineAmount;
  final int maxOfflineHours;
  final String status;

  JsonMap toJson() => {
        'officer': {
          'firstName': firstName,
          'currency': currency,
          'cashBalance': cashBalance,
          'maxOfflineAmount': maxOfflineAmount,
          'maxOfflineHours': maxOfflineHours,
          'status': status,
        },
      };
}

class CollectableAccount {
  const CollectableAccount({required this.accountId, required this.accountNumber, required this.title, required this.productType, required this.currency});

  factory CollectableAccount.fromJson(Object? data) {
    final json = asJsonMap(data, 'account');
    return CollectableAccount(
      accountId: readString(json, 'accountId'),
      accountNumber: readString(json, 'accountNumber'),
      title: readString(json, 'title'),
      productType: readString(json, 'productType'),
      currency: readString(json, 'currency'),
    );
  }

  final String accountId;
  final String accountNumber;
  final String title;
  final String productType;
  final String currency;

  JsonMap toJson() => {'accountId': accountId, 'accountNumber': accountNumber, 'title': title, 'productType': productType, 'currency': currency};
}

class CollectablePlan {
  const CollectablePlan({
    required this.planId,
    required this.planNumber,
    required this.accountId,
    required this.contributionAmount,
    required this.currency,
    required this.frequencyCode,
    required this.nextDue,
    required this.missed,
    required this.unpaidScheduled,
  });

  factory CollectablePlan.fromJson(Object? data) {
    final json = asJsonMap(data, 'plan');
    return CollectablePlan(
      planId: readString(json, 'planId'),
      planNumber: readString(json, 'planNumber'),
      accountId: readString(json, 'accountId'),
      contributionAmount: _amount(json, 'contributionAmount'),
      currency: readString(json, 'currency'),
      frequencyCode: readString(json, 'frequencyCode'),
      nextDue: readOptionalString(json, 'nextDue'),
      missed: _int(json, 'missed'),
      unpaidScheduled: _int(json, 'unpaidScheduled'),
    );
  }

  final String planId;
  final String planNumber;
  final String accountId;
  final String contributionAmount;
  final String currency;
  final String frequencyCode;
  final String? nextDue;
  final int missed;

  /// Contributions scheduled and unpaid: the most one collection may pay.
  final int unpaidScheduled;

  JsonMap toJson() => {
        'planId': planId,
        'planNumber': planNumber,
        'accountId': accountId,
        'contributionAmount': contributionAmount,
        'currency': currency,
        'frequencyCode': frequencyCode,
        'nextDue': nextDue,
        'missed': missed,
        'unpaidScheduled': unpaidScheduled,
      };
}

class MyCustomer {
  const MyCustomer({required this.customerId, required this.customerNumber, required this.displayName, required this.phone, required this.accounts, required this.susuPlans});

  factory MyCustomer.fromJson(Object? data) {
    final json = asJsonMap(data, 'customer');
    return MyCustomer(
      customerId: readString(json, 'customerId'),
      customerNumber: readString(json, 'customerNumber'),
      displayName: readString(json, 'displayName'),
      phone: readOptionalString(json, 'phone'),
      accounts: _list(json, 'accounts').map(CollectableAccount.fromJson).toList(growable: false),
      susuPlans: _list(json, 'susuPlans').map(CollectablePlan.fromJson).toList(growable: false),
    );
  }

  final String customerId;
  final String customerNumber;
  final String displayName;
  final String? phone;
  final List<CollectableAccount> accounts;
  final List<CollectablePlan> susuPlans;
}

/// The server's answer for one collection (or visit) of a sync.
class ItemOutcome {
  const ItemOutcome({required this.clientReference, required this.status, this.originalStatus, this.code, this.message, this.transactionReference, this.balanceAfter});

  factory ItemOutcome.fromJson(Object? data) {
    final json = asJsonMap(data, 'result');
    final balance = json['balanceAfter'];
    return ItemOutcome(
      clientReference: readString(json, 'clientReference'),
      status: readString(json, 'status'),
      originalStatus: readOptionalString(json, 'originalStatus'),
      code: readOptionalString(json, 'code'),
      message: readOptionalString(json, 'message'),
      transactionReference: readOptionalString(json, 'transactionReference'),
      balanceAfter: balance?.toString(),
    );
  }

  final String clientReference;

  /// POSTED, DUPLICATE, REJECTED or CONFLICT (collections); RECORDED, DUPLICATE, REJECTED or CONFLICT (visits).
  final String status;
  final String? originalStatus;
  final String? code;
  final String? message;
  final String? transactionReference;
  final String? balanceAfter;
}

class SyncAnswer {
  const SyncAnswer({required this.collections, required this.visits, required this.highestSequenceNo, required this.missing});

  factory SyncAnswer.fromJson(Object? data) {
    final json = asJsonMap(data, 'sync');
    return SyncAnswer(
      collections: _list(json, 'collections').map(ItemOutcome.fromJson).toList(growable: false),
      visits: _list(json, 'visits').map(ItemOutcome.fromJson).toList(growable: false),
      highestSequenceNo: _int(json, 'highestSequenceNo'),
      missing: _list(json, 'missing').map((range) {
        final value = asJsonMap(range, 'range');
        return (from: _int(value, 'from'), to: _int(value, 'to'));
      }).toList(growable: false),
    );
  }

  final List<ItemOutcome> collections;
  final List<ItemOutcome> visits;
  final int highestSequenceNo;

  /// Collection numbers the server never received.
  final List<({int from, int to})> missing;
}

/// The field endpoints of banking-core.
abstract interface class FieldApi {
  /// Registers this phone for the signed-in officer (again returns the same registration).
  Future<String> registerDevice({required String deviceKey, required String name});

  Future<OfficerSnapshot> me();

  Future<List<MyCustomer>> myCustomers();

  Future<SyncAnswer> sync(JsonMap request);
}

class HttpFieldApi implements FieldApi {
  HttpFieldApi(this._client);

  final ApiClient _client;

  @override
  Future<String> registerDevice({required String deviceKey, required String name}) => _client.post(
        '/api/v1/field/devices',
        (data) => readString(asJsonMap(data, 'device'), 'id'),
        body: {'deviceKey': deviceKey, 'name': name},
      );

  @override
  Future<OfficerSnapshot> me() => _client.get('/api/v1/field/me', OfficerSnapshot.fromJson);

  @override
  Future<List<MyCustomer>> myCustomers() => _client.get(
        '/api/v1/field/me/customers',
        (data) => (data as List<Object?>? ?? const []).map(MyCustomer.fromJson).toList(growable: false),
      );

  @override
  Future<SyncAnswer> sync(JsonMap request) => _client.post('/api/v1/field/sync', SyncAnswer.fromJson, body: request);
}

String _amount(JsonMap json, String key) {
  final value = json[key];
  if (value is String) {
    return value;
  }
  if (value is num) {
    // Never parsed as a double: the server sends decimal text; a bare number keeps its printed digits.
    return value.toString();
  }
  throw FormatException('Expected "$key" to be an amount');
}

int _int(JsonMap json, String key) {
  final value = json[key];
  if (value is int) {
    return value;
  }
  throw FormatException('Expected "$key" to be an integer');
}

List<Object?> _list(JsonMap json, String key) {
  final value = json[key];
  if (value == null) {
    return const [];
  }
  if (value is List) {
    return value.cast<Object?>();
  }
  throw FormatException('Expected "$key" to be a list');
}
