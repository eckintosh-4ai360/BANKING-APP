import 'package:dio/dio.dart';

import '../api/institution_api.dart';
import '../config/app_config.dart';
import '../network/api_client.dart';
import '../network/auth_interceptor.dart';
import '../storage/key_value_store.dart';
import '../storage/session_store.dart';
import 'session_controller.dart';

/// Everything an app needs to talk to banking-core, wired once at startup.
class BankingBackend {
  BankingBackend._({required this.session, required this.publicClient, required this.authorizedClient, required this.store});

  /// [httpAdapter] replaces the network in tests.
  static Future<BankingBackend> create({
    required AppConfig config,
    required AuthEndpoints endpoints,
    required String appName,
    KeyValueStore? keyValueStore,
    HttpClientAdapter? httpAdapter,
    DateTime Function()? clock,
  }) async {
    final store = SessionStore(keyValueStore ?? SecureKeyValueStore());
    final deviceId = await store.deviceId();

    final publicDio = createDio(config, deviceId: deviceId, appName: appName);
    final authorizedDio = createDio(config, deviceId: deviceId, appName: appName);
    if (httpAdapter != null) {
      publicDio.httpClientAdapter = httpAdapter;
      authorizedDio.httpClientAdapter = httpAdapter;
    }
    final publicClient = ApiClient(publicDio);
    final session = SessionController(client: publicClient, store: store, endpoints: endpoints, clock: clock);
    authorizedDio.interceptors.add(AuthInterceptor(session, authorizedDio));

    return BankingBackend._(session: session, publicClient: publicClient, authorizedClient: ApiClient(authorizedDio), store: store);
  }

  final SessionController session;

  /// For public endpoints (branding) and the session's own credential exchanges.
  final ApiClient publicClient;

  /// For everything that needs a signed-in user; adds and refreshes the bearer token.
  final ApiClient authorizedClient;
  final SessionStore store;

  InstitutionApi get institutions => InstitutionApi(publicClient);

  StaffApi get staff => StaffApi(authorizedClient);
}
