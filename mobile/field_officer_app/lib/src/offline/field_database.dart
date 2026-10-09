import 'package:drift/drift.dart';

part 'field_database.g.dart';

/// Where an item recorded offline stands. `queued` → `sent` (a sync carried it; no answer yet) → `accepted`,
/// `rejected` or `conflict` once the server answered. A `sent` item is simply sent again: the server recognises its
/// client reference, so a retry never posts twice.
enum QueueStatus { queued, sent, accepted, rejected, conflict }

/// Cash taken from a customer, waiting to reach the server or already answered.
///
/// Amounts are decimal strings, exactly as entered (never a double). Times are the ISO-8601 UTC strings sent to the
/// server, stored as text so a resend carries exactly the same value (the server compares them).
class QueuedCollections extends Table {
  TextColumn get clientReference => text()();
  IntColumn get sequenceNo => integer().unique()();
  TextColumn get customerId => text()();
  TextColumn get customerName => text()();
  TextColumn get accountId => text()();
  TextColumn get accountNumber => text()();
  TextColumn get susuPlanId => text().nullable()();
  TextColumn get amount => text()();
  TextColumn get currency => text()();
  TextColumn get collectedAt => text()();
  TextColumn get note => text().nullable()();
  TextColumn get status => textEnum<QueueStatus>()();
  TextColumn get serverCode => text().nullable()();
  TextColumn get serverMessage => text().nullable()();
  TextColumn get transactionReference => text().nullable()();
  TextColumn get balanceAfter => text().nullable()();
  IntColumn get attempts => integer().withDefault(const Constant(0))();
  TextColumn get lastAttemptAt => text().nullable()();

  @override
  Set<Column<Object>> get primaryKey => {clientReference};
}

class QueuedVisits extends Table {
  TextColumn get clientReference => text()();
  TextColumn get customerId => text()();
  TextColumn get customerName => text()();
  TextColumn get purpose => text()();
  TextColumn get outcome => text()();
  TextColumn get notes => text().nullable()();
  TextColumn get visitedAt => text()();
  TextColumn get status => textEnum<QueueStatus>()();
  TextColumn get serverCode => text().nullable()();
  TextColumn get serverMessage => text().nullable()();
  IntColumn get attempts => integer().withDefault(const Constant(0))();

  @override
  Set<Column<Object>> get primaryKey => {clientReference};
}

/// The officer's customers as of the last sync, with the accounts and susu plans a collection can go to (JSON).
class CachedCustomers extends Table {
  TextColumn get customerId => text()();
  TextColumn get customerNumber => text()();
  TextColumn get displayName => text()();
  TextColumn get phone => text().nullable()();
  TextColumn get payload => text()();
  IntColumn get position => integer()();

  @override
  Set<Column<Object>> get primaryKey => {customerId};
}

/// One row: the phone's registration, its next collection number, and the officer's profile and limits as of the
/// last sync (JSON).
class DeviceStates extends Table {
  IntColumn get id => integer()();
  TextColumn get registrationId => text().nullable()();
  IntColumn get nextSequenceNo => integer().withDefault(const Constant(1))();
  TextColumn get lastSyncedAt => text().nullable()();
  TextColumn get officer => text().nullable()();

  @override
  Set<Column<Object>> get primaryKey => {id};
}

@DriftDatabase(tables: [QueuedCollections, QueuedVisits, CachedCustomers, DeviceStates])
class FieldDatabase extends _$FieldDatabase {
  FieldDatabase(super.executor);

  @override
  int get schemaVersion => 1;

  @override
  MigrationStrategy get migration => MigrationStrategy(
        onCreate: (migrator) async {
          await migrator.createAll();
          await into(deviceStates).insert(DeviceStatesCompanion.insert(id: const Value(1)));
        },
      );
}
