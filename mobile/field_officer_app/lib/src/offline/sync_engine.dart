import 'dart:convert';

import 'package:banking_api/banking_api.dart';
import 'package:banking_core/banking_core.dart';
import 'package:drift/drift.dart';

import 'field_api.dart';
import 'field_database.dart';
import 'field_queue.dart';

/// What one sync did.
class SyncReport {
  const SyncReport({this.sent = 0, this.accepted = 0, this.rejected = 0, this.conflicts = 0, this.missing = const [], this.error, this.deviceRevoked = false});

  final int sent;
  final int accepted;
  final int rejected;
  final int conflicts;

  /// Collection numbers the server says it never received.
  final List<({int from, int to})> missing;

  /// Why the sync stopped early (items not answered stay queued and go with the next sync).
  final String? error;

  /// A supervisor revoked this phone: nothing more is sent from it.
  final bool deviceRevoked;

  bool get ok => error == null;
}

/// Sends what the phone recorded offline and refreshes what it keeps (limits, customers, plans).
///
/// Items go oldest first, at most [batchSize] at a time, each with the client reference and content it was recorded
/// with, so a sync interrupted at any point is simply repeated: the server answers items it already has from what
/// it stored (DUPLICATE) and never posts them again.
class SyncEngine {
  SyncEngine({required this.db, required this.api, required this.deviceKey, required this.deviceName, DateTime Function()? clock})
      : _clock = clock ?? DateTime.now;

  static const batchSize = 100;
  static const _maxBatches = 50;

  final FieldDatabase db;
  final FieldApi api;
  final String deviceKey;
  final String deviceName;
  final DateTime Function() _clock;
  Future<SyncReport>? _running;

  /// Syncs now; a sync already under way is joined rather than started twice.
  Future<SyncReport> sync() => _running ??= _sync().whenComplete(() => _running = null);

  Future<SyncReport> _sync() async {
    var sent = 0;
    var accepted = 0;
    var rejected = 0;
    var conflicts = 0;
    var missing = const <({int from, int to})>[];
    try {
      final deviceId = await _registration();
      for (var batch = 0; batch < _maxBatches; batch++) {
        final collections = await (db.select(db.queuedCollections)
              ..where((row) => row.status.isInValues(FieldQueue.pending))
              ..orderBy([(row) => OrderingTerm.asc(row.sequenceNo)])
              ..limit(batchSize))
            .get();
        final visits = await (db.select(db.queuedVisits)
              ..where((row) => row.status.isInValues(FieldQueue.pending))
              ..limit(batchSize))
            .get();
        if (collections.isEmpty && visits.isEmpty) {
          break;
        }
        await _markSent(collections, visits);
        sent += collections.length + visits.length;
        final answer = await api.sync({
          'deviceId': deviceId,
          'collections': [for (final row in collections) _collectionJson(row)],
          'visits': [for (final row in visits) _visitJson(row)],
        });
        missing = answer.missing;
        await db.transaction(() async {
          for (final outcome in answer.collections) {
            final status = _applyCollection(outcome);
            await (db.update(db.queuedCollections)..where((row) => row.clientReference.equals(outcome.clientReference))).write(status);
            switch (status.status.value) {
              case QueueStatus.accepted:
                accepted++;
              case QueueStatus.rejected:
                rejected++;
              case QueueStatus.conflict:
                conflicts++;
              case QueueStatus.queued || QueueStatus.sent:
                break;
            }
          }
          for (final outcome in answer.visits) {
            await (db.update(db.queuedVisits)..where((row) => row.clientReference.equals(outcome.clientReference))).write(_applyVisit(outcome));
          }
        });
      }
      await _refresh();
      return SyncReport(sent: sent, accepted: accepted, rejected: rejected, conflicts: conflicts, missing: missing);
    } on ApiException catch (error) {
      return SyncReport(
        sent: sent,
        accepted: accepted,
        rejected: rejected,
        conflicts: conflicts,
        missing: missing,
        error: error.message,
        deviceRevoked: error.code == 'DEVICE_NOT_REGISTERED',
      );
    }
  }

  Future<String> _registration() async {
    final state = await (db.select(db.deviceStates)..where((row) => row.id.equals(1))).getSingle();
    if (state.registrationId != null) {
      return state.registrationId!;
    }
    final id = await api.registerDevice(deviceKey: deviceKey, name: deviceName);
    await (db.update(db.deviceStates)..where((row) => row.id.equals(1))).write(DeviceStatesCompanion(registrationId: Value(id)));
    return id;
  }

  Future<void> _markSent(List<QueuedCollection> collections, List<QueuedVisit> visits) => db.transaction(() async {
        final at = _clock().toUtc().toIso8601String();
        for (final row in collections) {
          await (db.update(db.queuedCollections)..where((r) => r.clientReference.equals(row.clientReference)))
              .write(QueuedCollectionsCompanion(status: const Value(QueueStatus.sent), attempts: Value(row.attempts + 1), lastAttemptAt: Value(at)));
        }
        for (final row in visits) {
          await (db.update(db.queuedVisits)..where((r) => r.clientReference.equals(row.clientReference)))
              .write(QueuedVisitsCompanion(status: const Value(QueueStatus.sent), attempts: Value(row.attempts + 1)));
        }
      });

  /// The officer's limits and the customers to collect from, as the server knows them now.
  Future<void> _refresh() async {
    final officer = await api.me();
    final customers = await api.myCustomers();
    await db.transaction(() async {
      await (db.update(db.deviceStates)..where((row) => row.id.equals(1))).write(DeviceStatesCompanion(
            officer: Value(jsonEncode(officer.toJson())),
            lastSyncedAt: Value(_clock().toUtc().toIso8601String()),
          ));
      await db.delete(db.cachedCustomers).go();
      for (final (index, customer) in customers.indexed) {
        await db.into(db.cachedCustomers).insert(CachedCustomersCompanion.insert(
              customerId: customer.customerId,
              customerNumber: customer.customerNumber,
              displayName: customer.displayName,
              phone: Value(customer.phone),
              payload: jsonEncode({
                'customerId': customer.customerId,
                'customerNumber': customer.customerNumber,
                'displayName': customer.displayName,
                'phone': customer.phone,
                'accounts': [for (final account in customer.accounts) account.toJson()],
                'susuPlans': [for (final plan in customer.susuPlans) plan.toJson()],
              }),
              position: index,
            ));
      }
    });
  }

  static JsonMap _collectionJson(QueuedCollection row) => {
        'clientReference': row.clientReference,
        'sequenceNo': row.sequenceNo,
        'customerId': row.customerId,
        'accountId': row.accountId,
        if (row.susuPlanId != null) 'susuPlanId': row.susuPlanId,
        'amount': row.amount,
        'currency': row.currency,
        'collectedAt': row.collectedAt,
        if (row.note != null) 'note': row.note,
      };

  static JsonMap _visitJson(QueuedVisit row) => {
        'clientReference': row.clientReference,
        'customerId': row.customerId,
        'purpose': row.purpose,
        'outcome': row.outcome,
        if (row.notes != null) 'notes': row.notes,
        'visitedAt': row.visitedAt,
      };

  static QueuedCollectionsCompanion _applyCollection(ItemOutcome outcome) {
    final first = outcome.status == 'DUPLICATE' ? outcome.originalStatus : outcome.status;
    final status = switch (first) {
      'POSTED' => QueueStatus.accepted,
      'REJECTED' => QueueStatus.rejected,
      'CONFLICT' => QueueStatus.conflict,
      _ => QueueStatus.sent,
    };
    return QueuedCollectionsCompanion(
      status: Value(status),
      serverCode: Value(outcome.code),
      serverMessage: Value(outcome.message),
      transactionReference: Value(outcome.transactionReference),
      balanceAfter: outcome.balanceAfter == null ? const Value.absent() : Value(outcome.balanceAfter),
    );
  }

  static QueuedVisitsCompanion _applyVisit(ItemOutcome outcome) {
    final status = switch (outcome.status) {
      'RECORDED' || 'DUPLICATE' => QueueStatus.accepted,
      'REJECTED' => QueueStatus.rejected,
      'CONFLICT' => QueueStatus.conflict,
      _ => QueueStatus.sent,
    };
    return QueuedVisitsCompanion(status: Value(status), serverCode: Value(outcome.code), serverMessage: Value(outcome.message));
  }
}
