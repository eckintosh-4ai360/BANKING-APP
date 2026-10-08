import 'package:flutter_secure_storage/flutter_secure_storage.dart';

/// Small string store. Production uses the platform keystore (Android Keystore / iOS Keychain); tests use memory.
abstract interface class KeyValueStore {
  Future<String?> read(String key);

  Future<void> write(String key, String value);

  Future<void> delete(String key);
}

/// Hardware-backed storage for the refresh token and device identity.
///
/// iOS: `unlocked_this_device` keeps items out of iCloud backups and off other devices. Android: values are
/// encrypted with AES-GCM under a key held in the Android Keystore (the plugin's defaults); if the keystore is
/// reset the values are discarded and the user simply signs in again.
class SecureKeyValueStore implements KeyValueStore {
  SecureKeyValueStore({FlutterSecureStorage? storage})
      : _storage = storage ??
            const FlutterSecureStorage(
              iOptions: IOSOptions(accessibility: KeychainAccessibility.unlocked_this_device),
              aOptions: AndroidOptions(),
            );

  final FlutterSecureStorage _storage;

  @override
  Future<String?> read(String key) => _storage.read(key: key);

  @override
  Future<void> write(String key, String value) => _storage.write(key: key, value: value);

  @override
  Future<void> delete(String key) => _storage.delete(key: key);
}

class MemoryKeyValueStore implements KeyValueStore {
  MemoryKeyValueStore([Map<String, String>? initial]) : values = {...?initial};

  final Map<String, String> values;

  @override
  Future<String?> read(String key) async => values[key];

  @override
  Future<void> write(String key, String value) async => values[key] = value;

  @override
  Future<void> delete(String key) async => values.remove(key);
}
