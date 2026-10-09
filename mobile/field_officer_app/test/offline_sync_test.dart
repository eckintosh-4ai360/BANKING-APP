import 'package:banking_api/banking_api.dart';
import 'package:banking_core/banking_core.dart';
import 'package:drift/drift.dart' hide isNull, isNotNull;
import 'package:field_officer_app/src/offline/database_opener.dart';
import 'package:field_officer_app/src/offline/field_api.dart';
import 'package:field_officer_app/src/offline/field_database.dart';
import 'package:field_officer_app/src/offline/field_queue.dart';
import 'package:field_officer_app/src/offline/sync_engine.dart';
import 'package:flutter_test/flutter_test.dart';

const _account = CollectableAccount(accountId: 'acc-1', accountNumber: '1000000013', title: 'Ama Mensah', productType: 'SAVINGS', currency: 'GHS');
const _customer = MyCustomer(customerId: 'cus-1', customerNumber: 'CUS-1001', displayName: 'Ama Mensah', phone: null, accounts: [_account], susuPlans: []);

/// Behaves like banking-core's sync: remembers each client reference with its content, answers a resend from
/// what it stored, and counts what it actually posted.
class FakeFieldServer implements FieldApi {
  final Map<String, (String, String)> received = {};
  final List<String> posted = [];
  int syncCalls = 0;
  bool loseNextResponse = false;
  bool revoked = false;

  @override
  Future<String> registerDevice({required String deviceKey, required String name}) async => 'device-1';

  @override
  Future<OfficerSnapshot> me() async => const OfficerSnapshot(
      firstName: 'Kofi', currency: 'GHS', cashBalance: '0.00', maxOfflineAmount: '500.00', maxOfflineHours: 24, status: 'ACTIVE');

  @override
  Future<List<MyCustomer>> myCustomers() async => const [_customer];

  @override
  Future<SyncAnswer> sync(JsonMap request) async {
    syncCalls++;
    if (revoked) {
      throw const ApiException(kind: ApiErrorKind.rejected, message: 'This device is not registered to you.', statusCode: 403, code: 'DEVICE_NOT_REGISTERED');
    }
    final outcomes = <ItemOutcome>[];
    for (final item in (request['collections']! as List).cast<JsonMap>()) {
      final reference = item['clientReference']! as String;
      final content = '${item['sequenceNo']}|${item['accountId']}|${item['amount']}|${item['collectedAt']}';
      final known = received[reference];
      if (known != null) {
        outcomes.add(known.$1 == content
            ? ItemOutcome(clientReference: reference, status: 'DUPLICATE', originalStatus: known.$2, transactionReference: 'TXN-$reference')
            : ItemOutcome(clientReference: reference, status: 'CONFLICT', code: 'COLLECTION_CONFLICT', message: 'Reference reused'));
        continue;
      }
      final status = item['accountId'] == 'closed' ? 'REJECTED' : 'POSTED';
      received[reference] = (content, status);
      if (status == 'POSTED') {
        posted.add(reference);
      }
      outcomes.add(ItemOutcome(
        clientReference: reference,
        status: status,
        code: status == 'REJECTED' ? 'ACCOUNT_NOT_CREDITABLE' : null,
        message: status == 'REJECTED' ? 'The account cannot accept money.' : null,
        transactionReference: status == 'POSTED' ? 'TXN-$reference' : null,
        balanceAfter: status == 'POSTED' ? '100.00' : null,
      ));
    }
    final visits = [
      for (final visit in (request['visits']! as List).cast<JsonMap>())
        ItemOutcome(clientReference: visit['clientReference']! as String, status: 'RECORDED'),
    ];
    if (loseNextResponse) {
      loseNextResponse = false;
      throw const ApiException(kind: ApiErrorKind.network, message: 'No connection.');
    }
    return SyncAnswer(collections: outcomes, visits: visits, highestSequenceNo: received.length, missing: const []);
  }
}

void main() {
  late FieldDatabase db;
  late FakeFieldServer server;
  late FieldQueue queue;
  late SyncEngine engine;
  var now = DateTime.utc(2027, 3, 1, 9);

  setUp(() async {
    now = DateTime.utc(2027, 3, 1, 9);
    db = FieldDatabaseOpener.openInMemory();
    server = FakeFieldServer();
    queue = FieldQueue(db, clock: () => now);
    engine = SyncEngine(db: db, api: server, deviceKey: 'phone-123456', deviceName: 'Test phone', clock: () => now);
  });

  tearDown(() => db.close());

  Future<List<QueuedCollection>> collections() =>
      (db.select(db.queuedCollections)..orderBy([(row) => OrderingTerm.asc(row.sequenceNo)])).get();

  test('the phone needs one sync to learn its limits before collecting', () async {
    await expectLater(queue.recordCollection(customer: _customer, account: _account, amount: '10.00'), throwsA(isA<OfflineLimitException>()));
    final report = await engine.sync();
    expect(report.ok, isTrue);
    expect((await queue.watchSummary().first).officer?.maxOfflineAmount, '500.00');
    expect(await queue.watchCustomers().first, hasLength(1));
  });

  test('collections are numbered 1, 2, 3 and kept as typed', () async {
    await engine.sync();
    await queue.recordCollection(customer: _customer, account: _account, amount: '10.50');
    await queue.recordCollection(customer: _customer, account: _account, amount: '20');
    await queue.recordCollection(customer: _customer, account: _account, amount: '0.75', note: 'market day');

    final rows = await collections();
    expect(rows.map((row) => row.sequenceNo), [1, 2, 3]);
    expect(rows.map((row) => row.amount), ['10.50', '20', '0.75']);
    expect(rows.map((row) => row.clientReference).toSet(), hasLength(3));
    expect(rows.first.collectedAt, '2027-03-01T09:00:00.000Z');
    expect((await queue.watchSummary().first).pendingAmount, '31.25');
    await expectLater(queue.recordCollection(customer: _customer, account: _account, amount: '1.005'), throwsFormatException);
    await expectLater(queue.recordCollection(customer: _customer, account: _account, amount: '0'), throwsFormatException);
  });

  test('offline cash above the limit or older than the offline hours is refused', () async {
    await engine.sync();
    await queue.recordCollection(customer: _customer, account: _account, amount: '450.00');
    await expectLater(queue.recordCollection(customer: _customer, account: _account, amount: '50.01'),
        throwsA(isA<OfflineLimitException>().having((error) => error.message, 'message', contains('500.00'))));
    await queue.recordCollection(customer: _customer, account: _account, amount: '50.00');

    now = now.add(const Duration(hours: 25));
    await expectLater(queue.recordCollection(customer: _customer, account: _account, amount: '1.00'),
        throwsA(isA<OfflineLimitException>().having((error) => error.message, 'message', contains('24 hours'))));
    await engine.sync();
    await queue.recordCollection(customer: _customer, account: _account, amount: '1.00');
  });

  test('a lost answer is resent unchanged and posts once, however often the batch is replayed', () async {
    await engine.sync();
    for (final amount in ['10.00', '20.00', '30.00']) {
      await queue.recordCollection(customer: _customer, account: _account, amount: amount);
    }
    server.loseNextResponse = true;
    final interrupted = await engine.sync();
    expect(interrupted.ok, isFalse);
    expect(server.posted, hasLength(3), reason: 'the server posted them; the phone never heard');
    expect((await collections()).map((row) => row.status), everyElement(QueueStatus.sent));

    final retried = await engine.sync();
    expect(retried.accepted, 3);
    expect((await collections()).map((row) => row.status), everyElement(QueueStatus.accepted));
    expect((await collections()).first.transactionReference, startsWith('TXN-'));

    for (var replay = 0; replay < 3; replay++) {
      await db.update(db.queuedCollections).write(const QueuedCollectionsCompanion(status: Value(QueueStatus.queued)));
      final again = await engine.sync();
      expect(again.accepted, 3);
    }
    expect(server.posted, hasLength(3));
    expect((await collections()).map((row) => row.attempts), everyElement(5));
  });

  test('rejections and conflicts come back with the server’s reason', () async {
    await engine.sync();
    await queue.recordCollection(customer: _customer, account: _account, amount: '10.00');
    const closed = CollectableAccount(accountId: 'closed', accountNumber: '1000000099', title: 'Old account', productType: 'SAVINGS', currency: 'GHS');
    await queue.recordCollection(customer: _customer, account: closed, amount: '5.00');
    await engine.sync();
    final first = (await collections()).first;
    server.received[first.clientReference] = ('something else', 'POSTED');
    await (db.update(db.queuedCollections)..where((row) => row.clientReference.equals(first.clientReference)))
        .write(const QueuedCollectionsCompanion(status: Value(QueueStatus.queued)));

    final report = await engine.sync();
    final rows = await collections();
    expect(rows[0].status, QueueStatus.conflict);
    expect(rows[0].serverCode, 'COLLECTION_CONFLICT');
    expect(rows[1].status, QueueStatus.rejected);
    expect(rows[1].serverMessage, 'The account cannot accept money.');
    expect(report.conflicts, 1);
    expect((await queue.watchSummary().first).needsAttention, 2);
  });

  test('a revoked phone stops sending and keeps what it holds', () async {
    await engine.sync();
    await queue.recordCollection(customer: _customer, account: _account, amount: '10.00');
    await queue.recordVisit(customer: _customer, purpose: 'COLLECTION', outcome: 'MET');
    server.revoked = true;

    final report = await engine.sync();
    expect(report.deviceRevoked, isTrue);
    expect(server.posted, isEmpty);
    expect((await collections()).single.status, QueueStatus.sent);

    server.revoked = false;
    await engine.sync();
    expect((await collections()).single.status, QueueStatus.accepted);
    expect((await db.select(db.queuedVisits).get()).single.status, QueueStatus.accepted);
  });
}
