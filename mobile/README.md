# Mobile apps

Flutter pub workspace (Flutter 3.47, Dart 3.13) with two apps and three shared packages:

| Member | What it is |
|---|---|
| `packages/banking_api` | Typed models of the banking-core API (pure Dart). Secrets are redacted from `toString`. |
| `packages/banking_core` | Dio client, auth interceptor, session (single-flight token refresh), secure storage, `Money`. `package:banking_core/testing.dart` holds a fake backend for tests. |
| `packages/banking_ui` | Design system: theme from institution branding, buttons, password/OTP fields, PIN keypad, money and balance text, inactivity guard. |
| `field_officer_app` | Staff app: sign-in with MFA and forced password change, officer home. |
| `customer_app` | White-label customer app: branding bootstrap, welcome, sign-in, home skeleton. |

## Run

```bash
cd mobile
flutter pub get

# Field officer app against a local backend (Android emulator reaches the host at 10.0.2.2)
cd field_officer_app && flutter run --dart-define=API_BASE_URL=http://10.0.2.2:8080

# Customer app: one build per institution
cd customer_app && flutter run --dart-define=API_BASE_URL=http://10.0.2.2:8080 --dart-define=INSTITUTION_CODE=demo-mfi
```

On an iOS simulator use `http://localhost:8080`. Release builds refuse a non-HTTPS `API_BASE_URL`.

## Verify

```bash
flutter analyze                       # whole workspace
for m in packages/banking_api packages/banking_core packages/banking_ui field_officer_app customer_app; do
  (cd $m && flutter test) || exit 1
done
```

See [docs/architecture/05-phase1-blueprint.md](../docs/architecture/05-phase1-blueprint.md) §11 for the design decisions.
