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

/// Setting up mobile banking as an existing customer: customer number and phone on file, then a texted code with a
/// new password and transaction PIN. This installation becomes the first trusted device.
class ActivationScreen extends ConsumerStatefulWidget {
  const ActivationScreen({super.key});

  @override
  ConsumerState<ActivationScreen> createState() => _ActivationScreenState();
}

class _ActivationScreenState extends ConsumerState<ActivationScreen> {
  final _details = GlobalKey<FormState>();
  final _credentials = GlobalKey<FormState>();
  final _customerNumber = TextEditingController();
  final _phone = TextEditingController();
  final _code = TextEditingController();
  final _password = TextEditingController();
  final _passwordAgain = TextEditingController();
  final _pin = TextEditingController();
  final _pinAgain = TextEditingController();
  CodeSent? _sent;
  String? _error;
  String? _codeError;
  bool _busy = false;

  @override
  void dispose() {
    for (final controller in [_customerNumber, _phone, _code, _password, _passwordAgain, _pin, _pinAgain]) {
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
    if (!(_details.currentState?.validate() ?? false)) {
      return;
    }
    await _run(() async {
      final sent = await ref.read(customerApiProvider).startActivation(
            institutionCode: branding.code,
            customerNumber: _customerNumber.text.trim(),
            phoneNumber: _phone.text.replaceAll(RegExp(r'[\s-]'), ''),
          );
      setState(() => _sent = sent);
    });
  }

  Future<void> _complete(InstitutionBranding branding) async {
    final sent = _sent;
    final codeProblem = Validators.otp(_code.text);
    final valid = _credentials.currentState?.validate() ?? false;
    setState(() => _codeError = codeProblem);
    if (sent == null || codeProblem != null || !valid) {
      return;
    }
    await _run(() => ref.read(customerApiProvider).completeActivation(
          institutionCode: branding.code,
          challengeToken: sent.challengeToken,
          code: _code.text.trim(),
          password: _password.text,
          pin: _pin.text,
          deviceName: deviceNameFor(Theme.of(context).platform),
        ));
  }

  @override
  Widget build(BuildContext context) {
    final branding = ref.watch(brandingProvider).requireValue;
    final sent = _sent;
    return Scaffold(
      appBar: AppBar(
        title: const Text('Set up mobile banking'),
        leading: BackButton(onPressed: () => sent == null ? context.go(Routes.welcome) : setState(() => _sent = null)),
      ),
      body: SafeArea(
        child: sent == null
            ? Form(
                key: _details,
                child: ListView(
                  padding: const EdgeInsets.all(Gaps.lg),
                  children: [
                    Text('Already a customer of ${branding.displayName}? Enter your customer number and the phone number we have for you.'),
                    const SizedBox(height: Gaps.lg),
                    if (_error != null) ...[NoticeBanner(_error!, error: true), const SizedBox(height: Gaps.md)],
                    TextFormField(
                      controller: _customerNumber,
                      keyboardType: TextInputType.number,
                      textInputAction: TextInputAction.next,
                      decoration: const InputDecoration(labelText: 'Customer number'),
                      validator: (value) => Validators.required(value, 'Enter your customer number'),
                    ),
                    const SizedBox(height: Gaps.md),
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
                key: _credentials,
                child: AutofillGroup(
                  child: ListView(
                    padding: const EdgeInsets.all(Gaps.lg),
                    children: [
                      Text('If your details match, we sent a code to ${sent.sentTo}. Enter it and choose your password and PIN.'),
                      const SizedBox(height: Gaps.lg),
                      if (_error != null) ...[NoticeBanner(_error!, error: true), const SizedBox(height: Gaps.md)],
                      OtpField(controller: _code, enabled: !_busy, errorText: _codeError),
                      const SizedBox(height: Gaps.lg),
                      NewPasswordFields(password: _password, confirmation: _passwordAgain, label: 'Password'),
                      const SizedBox(height: Gaps.lg),
                      NewPinFields(pin: _pin, confirmation: _pinAgain, label: 'Transaction PIN'),
                      const SizedBox(height: Gaps.lg),
                      PrimaryButton(label: 'Set up', onPressed: _busy ? null : () => _complete(branding)),
                      TextButton(onPressed: _busy ? null : () => setState(() => _sent = null), child: const Text('Send a new code')),
                    ],
                  ),
                ),
              ),
      ),
    );
  }
}

/// How this installation is named in the customer's list of trusted devices.
String deviceNameFor(TargetPlatform platform) => switch (platform) {
      TargetPlatform.android => 'Android phone',
      TargetPlatform.iOS => 'iPhone',
      _ => 'Mobile device',
    };
