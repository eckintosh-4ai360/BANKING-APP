import 'dart:io';
import 'dart:math';

import 'package:banking_core/banking_core.dart';
import 'package:drift/drift.dart';
import 'package:drift/native.dart';
import 'package:path/path.dart' as p;
import 'package:path_provider/path_provider.dart';
import 'package:sqlite3/sqlite3.dart';

import 'field_database.dart';

/// Opens the field app's database encrypted. The key is 256 random bits made on first use and kept in the platform
/// keystore (never in the database or the app files); the SQLite build is SQLite3MultipleCiphers (see the
/// workspace pubspec). If encryption is not available the database is not opened at all: collections are never
/// written to a plain file.
abstract final class FieldDatabaseOpener {
  static const keyName = 'field.database.key';
  static const fileName = 'field_queue.db';

  /// The app's database in its support directory.
  static Future<FieldDatabase> open(KeyValueStore keys) async {
    final directory = await getApplicationSupportDirectory();
    return openFile(File(p.join(directory.path, fileName)), keys);
  }

  static Future<FieldDatabase> openFile(File file, KeyValueStore keys) async {
    final key = await _key(keys);
    return FieldDatabase(NativeDatabase.createInBackground(file, setup: (database) => unlock(database, key)));
  }

  /// A database in memory (tests). It is never written anywhere, so it takes no key, but it runs on the same
  /// encrypting build.
  static FieldDatabase openInMemory() {
    // Streams close at once when their last listener goes, so widget tests end without pending timers.
    return FieldDatabase(DatabaseConnection(NativeDatabase.memory(setup: requireEncryptingBuild), closeStreamsSynchronously: true));
  }

  /// Applies the key before anything else touches the database. Plain SQLite would ignore the key silently, so the
  /// encrypting build is proven next; reading the schema then fails here, not later, when the key does not open an
  /// existing file.
  static void unlock(Database database, String hexKey) {
    database.execute("PRAGMA key = \"x'$hexKey'\"");
    requireEncryptingBuild(database);
    database.select('SELECT count(*) FROM sqlite_master');
  }

  static void requireEncryptingBuild(Database database) {
    try {
      database.select('SELECT sqlite3mc_version()');
    } on SqliteException catch (error) {
      throw StateError('The encrypted SQLite build is missing ($error); refusing to store collections unencrypted.');
    }
  }

  static Future<String> _key(KeyValueStore keys) async {
    final existing = await keys.read(keyName);
    if (existing != null && RegExp(r'^[0-9a-f]{64}$').hasMatch(existing)) {
      return existing;
    }
    final random = Random.secure();
    final created = List.generate(32, (_) => random.nextInt(256).toRadixString(16).padLeft(2, '0')).join();
    await keys.write(keyName, created);
    return created;
  }
}
