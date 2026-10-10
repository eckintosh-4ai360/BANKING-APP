import 'package:banking_core/banking_core.dart';
import 'package:banking_ui/banking_ui.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../api/models.dart';
import '../../app/providers.dart';
import '../../widgets/common.dart';

/// The transaction PIN, the password, trusted devices, signed-in sessions and text alerts. Every change here is
/// also texted to the customer.
class SecurityScreen extends ConsumerWidget {
  const SecurityScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final profile = ref.watch(profileProvider).value;
    final smsAlerts = ref.watch(smsAlertsProvider);
    final devices = ref.watch(devicesProvider);
    final sessions = ref.watch(sessionsProvider);
    final text = Theme.of(context).textTheme;
    return Scaffold(
      appBar: AppBar(title: const Text('Security')),
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.all(Gaps.md),
          children: [
            if (profile?.pinLocked ?? false)
              const NoticeBanner('Your PIN is locked after too many wrong attempts. Reset it to make payments again.', error: true),
            ListTile(
              leading: const Icon(Icons.pin_outlined),
              title: const Text('Change PIN'),
              onTap: () => _sheet(context, const _ChangePinSheet()),
            ),
            ListTile(
              leading: const Icon(Icons.lock_reset_outlined),
              title: const Text('Forgot your PIN?'),
              subtitle: const Text('Reset it with a texted code and your password'),
              onTap: () => _sheet(context, const _ResetPinSheet()),
            ),
            ListTile(
              leading: const Icon(Icons.password_outlined),
              title: const Text('Change password'),
              subtitle: const Text('Signs out your other devices'),
              onTap: () => _sheet(context, const _ChangePasswordSheet()),
            ),
            switch (smsAlerts) {
              AsyncData(:final value) => SwitchListTile(
                  secondary: const Icon(Icons.sms_outlined),
                  title: const Text('Text me about money in and out'),
                  subtitle: const Text('Security notices are always texted'),
                  value: value,
                  onChanged: (enabled) async {
                    await ref.read(customerApiProvider).setSmsAlerts(enabled);
                    ref.invalidate(smsAlertsProvider);
                  },
                ),
              _ => const SizedBox.shrink(),
            },
            const Divider(),
            Text('Trusted devices', style: text.titleMedium),
            AsyncBody(
              value: devices,
              onRetry: () => ref.invalidate(devicesProvider),
              builder: (devices) => Column(
                children: [
                  for (final device in devices.where((device) => device.status == 'ACTIVE'))
                    ListTile(
                      contentPadding: EdgeInsets.zero,
                      leading: const Icon(Icons.smartphone),
                      title: Text(device.current ? '${device.name} (this phone)' : device.name),
                      subtitle: Text('Trusted since ${formatDate(device.boundAt)}'
                          '${device.lastSeenAt == null ? '' : ' · last used ${formatDateTime(device.lastSeenAt!)}'}'),
                      trailing: device.current
                          ? null
                          : TextButton(onPressed: () => _revokeDevice(context, ref, device), child: const Text('Remove')),
                    ),
                ],
              ),
            ),
            const Divider(),
            Text('Signed in', style: text.titleMedium),
            AsyncBody(
              value: sessions,
              onRetry: () => ref.invalidate(sessionsProvider),
              builder: (sessions) => Column(
                children: [
                  for (final session in sessions.where((session) => session.status == 'ACTIVE'))
                    ListTile(
                      contentPadding: EdgeInsets.zero,
                      leading: const Icon(Icons.login),
                      title: Text(session.current ? '${session.deviceName ?? 'This phone'} (now)' : session.deviceName ?? 'Another phone'),
                      subtitle: Text('Since ${formatDateTime(session.createdAt)}'),
                      trailing: session.current
                          ? null
                          : TextButton(
                              onPressed: () async {
                                await ref.read(customerApiProvider).endSession(session.id);
                                ref.invalidate(sessionsProvider);
                              },
                              child: const Text('Sign out'),
                            ),
                    ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  static Future<void> _sheet(BuildContext context, Widget sheet) =>
      showModalBottomSheet<void>(context: context, isScrollControlled: true, builder: (_) => sheet);

  Future<void> _revokeDevice(BuildContext context, WidgetRef ref, TrustedDevice device) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text('Remove ${device.name}?'),
        content: const Text('It is signed out, and signing in on it again needs a texted code.'),
        actions: [
          TextButton(onPressed: () => Navigator.of(context).pop(false), child: const Text('Cancel')),
          FilledButton(onPressed: () => Navigator.of(context).pop(true), child: const Text('Remove')),
        ],
      ),
    );
    if (confirmed != true) {
      return;
    }
    await ref.read(customerApiProvider).revokeDevice(device.id);
    ref
      ..invalidate(devicesProvider)
      ..invalidate(sessionsProvider);
  }
}

/// A bottom sheet with a form, an error banner and a submit button.
class _FormSheet extends StatelessWidget {
  const _FormSheet({required this.title, required this.formKey, required this.children, required this.submitLabel, required this.onSubmit, this.error});

  final String title;
  final GlobalKey<FormState> formKey;
  final List<Widget> children;
  final String submitLabel;
  final Future<void> Function() onSubmit;
  final String? error;

  @override
  Widget build(BuildContext context) => Padding(
        padding: EdgeInsets.fromLTRB(Gaps.lg, Gaps.lg, Gaps.lg, Gaps.lg + MediaQuery.viewInsetsOf(context).bottom),
        child: Form(
          key: formKey,
          child: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Text(title, style: Theme.of(context).textTheme.titleLarge),
                const SizedBox(height: Gaps.md),
                if (error != null) ...[NoticeBanner(error!, error: true), const SizedBox(height: Gaps.md)],
                ...children,
                const SizedBox(height: Gaps.lg),
                PrimaryButton(label: submitLabel, onPressed: onSubmit),
              ],
            ),
          ),
        ),
      );
}

void _done(BuildContext context, String message) {
  final messenger = ScaffoldMessenger.of(context);
  Navigator.of(context).pop();
  messenger.showSnackBar(SnackBar(content: Text(message)));
}

class _ChangePinSheet extends ConsumerStatefulWidget {
  const _ChangePinSheet();

  @override
  ConsumerState<_ChangePinSheet> createState() => _ChangePinSheetState();
}

class _ChangePinSheetState extends ConsumerState<_ChangePinSheet> {
  final _form = GlobalKey<FormState>();
  final _current = TextEditingController();
  final _pin = TextEditingController();
  final _pinAgain = TextEditingController();
  String? _error;

  @override
  void dispose() {
    for (final controller in [_current, _pin, _pinAgain]) {
      controller.dispose();
    }
    super.dispose();
  }

  Future<void> _save() async {
    if (!(_form.currentState?.validate() ?? false)) {
      return;
    }
    try {
      await ref.read(customerApiProvider).changePin(currentPin: _current.text, newPin: _pin.text);
      ref.invalidate(profileProvider);
      if (mounted) {
        _done(context, 'Your PIN is changed.');
      }
    } on ApiException catch (error) {
      setState(() => _error = error.message);
    }
  }

  @override
  Widget build(BuildContext context) => _FormSheet(
        title: 'Change PIN',
        formKey: _form,
        error: _error,
        submitLabel: 'Change PIN',
        onSubmit: _save,
        children: [
          PinField(controller: _current, label: 'Current PIN'),
          const SizedBox(height: Gaps.md),
          NewPinFields(pin: _pin, confirmation: _pinAgain),
        ],
      );
}

class _ResetPinSheet extends ConsumerStatefulWidget {
  const _ResetPinSheet();

  @override
  ConsumerState<_ResetPinSheet> createState() => _ResetPinSheetState();
}

class _ResetPinSheetState extends ConsumerState<_ResetPinSheet> {
  final _form = GlobalKey<FormState>();
  final _code = TextEditingController();
  final _password = TextEditingController();
  final _pin = TextEditingController();
  final _pinAgain = TextEditingController();
  CodeSent? _sent;
  String? _error;

  @override
  void dispose() {
    for (final controller in [_code, _password, _pin, _pinAgain]) {
      controller.dispose();
    }
    super.dispose();
  }

  Future<void> _requestCode() async {
    try {
      final sent = await ref.read(customerApiProvider).startPinReset();
      setState(() {
        _sent = sent;
        _error = null;
      });
    } on ApiException catch (error) {
      setState(() => _error = error.message);
    }
  }

  Future<void> _reset() async {
    final sent = _sent;
    final codeProblem = Validators.otp(_code.text);
    if (codeProblem != null) {
      setState(() => _error = codeProblem);
      return;
    }
    if (sent == null || !(_form.currentState?.validate() ?? false)) {
      return;
    }
    try {
      await ref.read(customerApiProvider).completePinReset(
            challengeToken: sent.challengeToken,
            code: _code.text.trim(),
            password: _password.text,
            newPin: _pin.text,
          );
      ref.invalidate(profileProvider);
      if (mounted) {
        _done(context, 'Your PIN is reset.');
      }
    } on ApiException catch (error) {
      setState(() => _error = error.message);
    }
  }

  @override
  Widget build(BuildContext context) {
    final sent = _sent;
    if (sent == null) {
      return _FormSheet(
        title: 'Reset your PIN',
        formKey: _form,
        error: _error,
        submitLabel: 'Text me a code',
        onSubmit: _requestCode,
        children: const [Text('We will text a code to the phone number you sign in with.')],
      );
    }
    return _FormSheet(
      title: 'Reset your PIN',
      formKey: _form,
      error: _error,
      submitLabel: 'Reset PIN',
      onSubmit: _reset,
      children: [
        Text('We sent a code to ${sent.sentTo}.'),
        const SizedBox(height: Gaps.md),
        OtpField(controller: _code),
        const SizedBox(height: Gaps.md),
        PasswordField(controller: _password, validator: (value) => Validators.required(value, 'Enter your password')),
        const SizedBox(height: Gaps.md),
        NewPinFields(pin: _pin, confirmation: _pinAgain),
      ],
    );
  }
}

class _ChangePasswordSheet extends ConsumerStatefulWidget {
  const _ChangePasswordSheet();

  @override
  ConsumerState<_ChangePasswordSheet> createState() => _ChangePasswordSheetState();
}

class _ChangePasswordSheetState extends ConsumerState<_ChangePasswordSheet> {
  final _form = GlobalKey<FormState>();
  final _current = TextEditingController();
  final _password = TextEditingController();
  final _passwordAgain = TextEditingController();
  String? _error;

  @override
  void dispose() {
    for (final controller in [_current, _password, _passwordAgain]) {
      controller.dispose();
    }
    super.dispose();
  }

  Future<void> _save() async {
    if (!(_form.currentState?.validate() ?? false)) {
      return;
    }
    try {
      await ref.read(customerApiProvider).changePassword(currentPassword: _current.text, newPassword: _password.text);
      ref.invalidate(sessionsProvider);
      if (mounted) {
        _done(context, 'Your password is changed. Your other devices are signed out.');
      }
    } on ApiException catch (error) {
      setState(() => _error = error.message);
    }
  }

  @override
  Widget build(BuildContext context) => _FormSheet(
        title: 'Change password',
        formKey: _form,
        error: _error,
        submitLabel: 'Change password',
        onSubmit: _save,
        children: [
          PasswordField(controller: _current, label: 'Current password', validator: (value) => Validators.required(value, 'Enter your password')),
          const SizedBox(height: Gaps.md),
          NewPasswordFields(password: _password, confirmation: _passwordAgain),
        ],
      );
}
