import 'dart:convert';

import 'package:banking_core/banking_core.dart';
import 'package:decimal/decimal.dart';
import 'package:drift/drift.dart';

import 'field_api.dart';
import 'field_database.dart';

/// Recording is refused because the phone holds too much, or too old, unsynced cash (spec risk F13).
class OfflineLimitException implements Exception {
  const OfflineLimitException(this.message);

  final String message;

  @override
  String toString() => message;
}

/// What the home screen shows about the queue.
class QueueSummary {
  const QueueSummary({required this.pendingCollections, required this.pendingAmount, required this.pendingVisits, required this.needsAttention, required this.lastSyncedAt, required this.officer});

  final int pendingCollections;

  /// Total of the collections not yet answered by the server, as a decimal string.
  final String pendingAmount;
  final int pendingVisits;

  /// Rejected or conflicting items: the officer must sort them out with a supervisor.
  final int needsAttention;
  final DateTime? lastSyncedAt;
  final OfficerSnapshot? officer;
}

/// The phone's record of collections and visits taken offline. Each collection gets the device's next number
/// (1, 2, 3, ...) and a fresh client reference in one transaction, so numbers are never reused and never skipped.
class FieldQueue {
  FieldQueue(this.db, {DateTime Function()? clock}) : _clock = clock ?? DateTime.now;

  final FieldDatabase db;
  final DateTime Function() _clock;

  static const pending = [QueueStatus.queued, QueueStatus.sent];
  static final _amountPattern = RegExp(r'^(0|[1-9]\d{0,14})(\.\d{1,2})?$');

  /// Records cash just taken from a customer.
  ///
  /// [amount] is the decimal text the officer typed. Refused when the phone has not synced yet (no limits known),
  /// when the cash held offline would exceed the officer's limit, or when the oldest unsynced collection is older
  /// than the officer's offline hours.
  Future<QueuedCollection> recordCollection({
    required MyCustomer customer,
    required CollectableAccount account,
    CollectablePlan? plan,
    required String amount,
    String? note,
  }) async {
    final typed = amount.trim();
    if (!_amountPattern.hasMatch(typed) || Decimal.parse(typed) <= Decimal.zero) {
      throw const FormatException('Enter an amount such as 25.00');
    }
    return db.transaction(() async {
      final state = await _state();
      final officer = _officer(state);
      if (officer == null) {
        throw const OfflineLimitException('Sync once before collecting, so the phone knows your limits.');
      }
      if (account.currency != officer.currency) {
        throw OfflineLimitException('You collect in ${officer.currency} only.');
      }
      final now = _clock().toUtc();
      final unsynced = await (db.select(db.queuedCollections)..where((row) => row.status.isInValues(pending))).get();
      final oldest = unsynced.map((row) => DateTime.parse(row.collectedAt)).fold<DateTime?>(null, (earliest, at) => earliest == null || at.isBefore(earliest) ? at : earliest);
      if (oldest != null && now.difference(oldest) > Duration(hours: officer.maxOfflineHours)) {
        throw OfflineLimitException('Sync first: a collection has waited more than ${officer.maxOfflineHours} hours.');
      }
      final held = unsynced.fold(Decimal.zero, (sum, row) => sum + Decimal.parse(row.amount)) + Decimal.parse(typed);
      if (held > Decimal.parse(officer.maxOfflineAmount)) {
        throw OfflineLimitException('Sync first: you may hold at most ${officer.currency} ${officer.maxOfflineAmount} offline.');
      }

      final sequenceNo = state.nextSequenceNo;
      await (db.update(db.deviceStates)..where((row) => row.id.equals(1)))
          .write(DeviceStatesCompanion(nextSequenceNo: Value(sequenceNo + 1)));
      final row = QueuedCollectionsCompanion.insert(
        clientReference: randomUuid(),
        sequenceNo: sequenceNo,
        customerId: customer.customerId,
        customerName: customer.displayName,
        accountId: account.accountId,
        accountNumber: account.accountNumber,
        susuPlanId: Value(plan?.planId),
        amount: typed,
        currency: account.currency,
        collectedAt: _instant(now),
        note: Value(note == null || note.trim().isEmpty ? null : note.trim()),
        status: QueueStatus.queued,
      );
      await db.into(db.queuedCollections).insert(row);
      return (db.select(db.queuedCollections)..where((r) => r.clientReference.equals(row.clientReference.value))).getSingle();
    });
  }

  /// Records a visit (no cash, no limits).
  Future<QueuedVisit> recordVisit({required MyCustomer customer, required String purpose, required String outcome, String? notes}) async {
    final row = QueuedVisitsCompanion.insert(
      clientReference: randomUuid(),
      customerId: customer.customerId,
      customerName: customer.displayName,
      purpose: purpose,
      outcome: outcome,
      notes: Value(notes == null || notes.trim().isEmpty ? null : notes.trim()),
      visitedAt: _instant(_clock().toUtc()),
      status: QueueStatus.queued,
    );
    await db.into(db.queuedVisits).insert(row);
    return (db.select(db.queuedVisits)..where((r) => r.clientReference.equals(row.clientReference.value))).getSingle();
  }

  /// Newest first.
  Stream<List<QueuedCollection>> watchCollections() =>
      (db.select(db.queuedCollections)..orderBy([(row) => OrderingTerm.desc(row.sequenceNo)])).watch();

  Stream<List<QueuedVisit>> watchVisits() =>
      (db.select(db.queuedVisits)..orderBy([(row) => OrderingTerm.desc(row.visitedAt)])).watch();

  Stream<QueueSummary> watchSummary() {
    final collections = db.select(db.queuedCollections).watch();
    return collections.asyncMap((rows) async {
      final visits = await db.select(db.queuedVisits).get();
      final state = await _state();
      final unsynced = rows.where((row) => pending.contains(row.status));
      return QueueSummary(
        pendingCollections: unsynced.length,
        pendingAmount: unsynced.fold(Decimal.zero, (sum, row) => sum + Decimal.parse(row.amount)).toStringAsFixed(2),
        pendingVisits: visits.where((visit) => pending.contains(visit.status)).length,
        needsAttention: rows.where((row) => row.status == QueueStatus.rejected || row.status == QueueStatus.conflict).length,
        lastSyncedAt: state.lastSyncedAt == null ? null : DateTime.parse(state.lastSyncedAt!),
        officer: _officer(state),
      );
    });
  }

  /// The officer's customers as of the last sync.
  Stream<List<MyCustomer>> watchCustomers() =>
      (db.select(db.cachedCustomers)..orderBy([(row) => OrderingTerm.asc(row.position)])).watch().map((rows) => [
            for (final row in rows) MyCustomer.fromJson(jsonDecode(row.payload)),
          ]);

  Future<DeviceState> _state() => (db.select(db.deviceStates)..where((row) => row.id.equals(1))).getSingle();

  static OfficerSnapshot? _officer(DeviceState state) =>
      state.officer == null ? null : OfficerSnapshot.fromJson(jsonDecode(state.officer!));

  /// UTC, to the millisecond, as sent to the server (and resent unchanged).
  static String _instant(DateTime time) =>
      DateTime.fromMillisecondsSinceEpoch(time.millisecondsSinceEpoch, isUtc: true).toIso8601String();
}
