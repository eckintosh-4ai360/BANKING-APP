import 'dart:convert';
import 'dart:io';

import 'package:banking_core/banking_core.dart';
import 'package:field_officer_app/src/offline/database_opener.dart';
import 'package:field_officer_app/src/offline/field_database.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('the queue is encrypted on disk and opens only with its key', () async {
    final directory = await Directory.systemTemp.createTemp('field-db-');
    final file = File('${directory.path}/queue.db');
    final keys = MemoryKeyValueStore();

    final database = await FieldDatabaseOpener.openFile(file, keys);
    await database.into(database.cachedCustomers).insert(CachedCustomersCompanion.insert(
          customerId: 'c-1',
          customerNumber: 'CUS-1001',
          displayName: 'Ama Mensah',
          payload: '{}',
          position: 0,
        ));
    await database.close();

    final raw = await file.readAsBytes();
    expect(latin1.decode(raw, allowInvalid: true), isNot(contains('Ama Mensah')));
    expect(latin1.decode(raw.take(16).toList(), allowInvalid: true), isNot(startsWith('SQLite format 3')));
    expect(await keys.read(FieldDatabaseOpener.keyName), matches(RegExp(r'^[0-9a-f]{64}$')));

    final reopened = await FieldDatabaseOpener.openFile(file, keys);
    final customers = await reopened.select(reopened.cachedCustomers).get();
    expect(customers.single.displayName, 'Ama Mensah');
    await reopened.close();

    final wrongKey = await FieldDatabaseOpener.openFile(file, MemoryKeyValueStore());
    await expectLater(wrongKey.select(wrongKey.cachedCustomers).get(), throwsA(anything));
    await wrongKey.close();
    await directory.delete(recursive: true);
  });

  test('a new database starts numbering collections at 1', () async {
    final database = FieldDatabaseOpener.openInMemory();
    final state = await (database.select(database.deviceStates)..where((row) => row.id.equals(1))).getSingle();
    expect(state.nextSequenceNo, 1);
    expect(state.registrationId, isNull);
    await database.close();
  });
}
