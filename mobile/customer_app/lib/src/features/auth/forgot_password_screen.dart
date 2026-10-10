import 'package:banking_api/banking_api.dart';
import 'package:banking_core/banking_core.dart';
import 'package:banking_ui/banking_ui.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../api/models.dart';
import '../../app/providers.dart';
import '../../app/router.dart';
import '../../widgets/common.dart';

/// A forgotten password: a code texted to the phone and the transaction PIN together set a new one, so a stolen SIM
/// alone is not enough. Every session ends; the customer then signs in again.
class ForgotPasswordScreen extends ConsumerStatefulWidget {
  const ForgotPasswordScreen({super.key});

  @override
  ConsumerState<ForgotPasswordScreen> createState() => _ForgotPasswordScreenState();
}

class _ForgotPasswordScreenState extends ConsumerState<ForgotPasswordScreen> {
  final _phoneForm = GlobalKey<FormState>();
  final _resetForm = GlobalKey<FormState>();
  final _phone = TextEditingController();
  final _code = TextEditingController();
  final _pin = TextEditingController();
  final _password = TextEditingController();
  final _passwordAgain = TextEditingController();
  CodeSent? _sent;
  bool _done = false;
  String? _error;
  String? _codeError;
  bool _busy = false;

  @override
  void dispose() {
    for (final controller in [_phone, _code, _pin, _password, _passwordAgain]) {
      controller.dispose();
    }
    super.dispose();
  }

  Future<void> _run(Future<void> Function() action) async {
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      await action();
    } on ApiException catch (error) {
      if (mounted) {
        setState(() => _error = error.message);
      }
    } finally {
      if (mounted) {
        setState(() => _busy = false);
      }
    }
  }

  Future<void> _requestCode(InstitutionBranding branding) async {
    if (!(_phoneForm.currentState?.validate() ?? false)) {
      return;
    }
    await _run(() async {
      final sent = await ref
          .read(customerApiProvider)
          .startPasswordReset(institutionCode: branding.code, phoneNumber: _phone.text.replaceAll(RegExp(r'[\s-]'), ''));
      setState(() => _sent = sent);
    });
  }

  Future<void> _reset() async {
    final sent = _sent;
    final codeProblem = Validators.otp(_code.text);
    final valid = _resetForm.currentState?.validate() ?? false;
    setState(() => _codeError = codeProblem);
    if (sent == null || codeProblem != null || !valid) {
      return;
    }
    await _run(() async {
      await ref.read(customerApiProvider).completePasswordReset(
            challengeToken: sent.challengeToken,
            code: _code.text.trim(),
            pin: _pin.text,
            newPassword: _password.text,
          );
      setState(() => _done = true);
    });
  }

  @override
  Widget build(BuildContext context) {
    final branding = ref.watch(brandingProvider).requireValue;
    final sent = _sent;
    return Scaffold(
      appBar: AppBar(title: const Text('Reset your password'), leading: BackButton(onPressed: () => context.go(Routes.signIn))),
      body: SafeArea(
        child: _done
            ? ListView(
                padding: const EdgeInsets.all(Gaps.lg),
                children: [
                  const NoticeBanner('Your password is changed. Sign in with the new one.'),
                  const SizedBox(height: Gaps.lg),
                  PrimaryButton(label: 'Sign in', onPressed: () async => context.go(Routes.signIn)),
                ],
              )
            : sent == null
                ? Form(
                    key: _phoneForm,
                    child: ListView(
                      padding: const EdgeInsets.all(Gaps.lg),
                      children: [
                        const Text('Enter the phone number you sign in with. We will text you a code.'),
                        const SizedBox(height: Gaps.lg),
                        if (_error != null) ...[NoticeBanner(_error!, error: true), const SizedBox(height: Gaps.md)],
                        TextFormField(
                          controller: _phone,
                          keyboardType: TextInputType.phone,
                          autofillHints: const [AutofillHints.telephoneNumber],
                          decoration: const InputDecoration(labelText: 'Phone number'),
                          validator: Validators.phoneNumber,
                        ),
                        const SizedBox(height: Gaps.lg),
                        PrimaryButton(label: 'Send me a code', onPressed: _busy ? null : () => _requestCode(branding)),
                      ],
                    ),
                  )
                : Form(
                    key: _resetForm,
                    child: ListView(
                      padding: const EdgeInsets.all(Gaps.lg),
                      children: [
                        Text('If the number has mobile banking, we sent a code to ${sent.sentTo}.'),
                        const SizedBox(height: Gaps.lg),
                        if (_error != null) ...[NoticeBanner(_error!, error: true), const SizedBox(height: Gaps.md)],
                        OtpField(controller: _code, enabled: !_busy, errorText: _codeError),
                        const SizedBox(height: Gaps.md),
                        PinField(controller: _pin),
                        const SizedBox(height: Gaps.lg),
                        NewPasswordFields(password: _password, confirmation: _passwordAgain),
                        const SizedBox(height: Gaps.lg),
                        PrimaryButton(label: 'Change password', onPressed: _busy ? null : _reset),
                      ],
                    ),
                  ),
      ),
    );
  }
}
