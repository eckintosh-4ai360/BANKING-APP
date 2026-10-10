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
import 'activation_screen.dart';

/// Becoming a customer in the app: the phone number is verified with a texted code, then the person registers
/// with their name, date of birth, a password and a transaction PIN. Their details and documents follow in the
/// sign-up stages, and staff review them before any account opens.
class SignUpScreen extends ConsumerStatefulWidget {
  const SignUpScreen({super.key, this.today});

  /// For tests; the device's date otherwise.
  final DateTime? today;

  @override
  ConsumerState<SignUpScreen> createState() => _SignUpScreenState();
}

class _SignUpScreenState extends ConsumerState<SignUpScreen> {
  final _phoneForm = GlobalKey<FormState>();
  final _registration = GlobalKey<FormState>();
  final _phone = TextEditingController();
  final _code = TextEditingController();
  final _firstName = TextEditingController();
  final _lastName = TextEditingController();
  final _password = TextEditingController();
  final _passwordAgain = TextEditingController();
  final _pin = TextEditingController();
  final _pinAgain = TextEditingController();
  DateTime? _dateOfBirth;
  CodeSent? _sent;
  String? _error;
  String? _codeError;
  String? _dateError;
  bool _busy = false;

  @override
  void dispose() {
    for (final controller in [_phone, _code, _firstName, _lastName, _password, _passwordAgain, _pin, _pinAgain]) {
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
          .startSignUp(institutionCode: branding.code, phoneNumber: _phone.text.replaceAll(RegExp(r'[\s-]'), ''));
      setState(() => _sent = sent);
    });
  }

  Future<void> _pickDateOfBirth() async {
    final today = widget.today ?? DateTime.now();
    final picked = await showDatePicker(
      context: context,
      initialDate: _dateOfBirth ?? DateTime(today.year - 25, today.month, today.day),
      firstDate: DateTime(today.year - 120),
      lastDate: today,
      helpText: 'Date of birth',
    );
    if (picked != null) {
      setState(() {
        _dateOfBirth = picked;
        _dateError = null;
      });
    }
  }

  Future<void> _register(InstitutionBranding branding) async {
    final sent = _sent;
    final dateOfBirth = _dateOfBirth;
    final codeProblem = Validators.otp(_code.text);
    final valid = _registration.currentState?.validate() ?? false;
    setState(() {
      _codeError = codeProblem;
      _dateError = dateOfBirth == null ? 'Choose your date of birth' : null;
    });
    if (sent == null || dateOfBirth == null || codeProblem != null || !valid) {
      return;
    }
    await _run(() => ref.read(customerApiProvider).completeSignUp(
          institutionCode: branding.code,
          challengeToken: sent.challengeToken,
          code: _code.text.trim(),
          firstName: _firstName.text.trim(),
          lastName: _lastName.text.trim(),
          dateOfBirth: dateOfBirth,
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
        title: const Text('Open an account'),
        leading: BackButton(onPressed: () => sent == null ? context.go(Routes.welcome) : setState(() => _sent = null)),
      ),
      body: SafeArea(
        child: sent == null
            ? Form(
                key: _phoneForm,
                child: ListView(
                  padding: const EdgeInsets.all(Gaps.lg),
                  children: [
                    Text('Become a customer of ${branding.displayName} from your phone. First, we check your phone number.'),
                    const SizedBox(height: Gaps.sm),
                    Text(
                      'You will need your identity document and a few minutes. Staff check your details before your account opens.',
                      style: Theme.of(context).textTheme.bodySmall,
                    ),
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
                key: _registration,
                child: AutofillGroup(
                  child: ListView(
                    padding: const EdgeInsets.all(Gaps.lg),
                    children: [
                      Text('We sent a code to ${sent.sentTo}. Enter it and tell us who you are.'),
                      const SizedBox(height: Gaps.lg),
                      if (_error != null) ...[NoticeBanner(_error!, error: true), const SizedBox(height: Gaps.md)],
                      OtpField(controller: _code, enabled: !_busy, errorText: _codeError),
                      const SizedBox(height: Gaps.lg),
                      TextFormField(
                        controller: _firstName,
                        textCapitalization: TextCapitalization.words,
                        autofillHints: const [AutofillHints.givenName],
                        textInputAction: TextInputAction.next,
                        decoration: const InputDecoration(labelText: 'First name'),
                        validator: (value) => Validators.required(value, 'Enter your first name'),
                      ),
                      const SizedBox(height: Gaps.md),
                      TextFormField(
                        controller: _lastName,
                        textCapitalization: TextCapitalization.words,
                        autofillHints: const [AutofillHints.familyName],
                        textInputAction: TextInputAction.next,
                        decoration: const InputDecoration(labelText: 'Last name'),
                        validator: (value) => Validators.required(value, 'Enter your last name'),
                      ),
                      const SizedBox(height: Gaps.md),
                      InputDecorator(
                        decoration: InputDecoration(labelText: 'Date of birth', errorText: _dateError),
                        child: InkWell(
                          onTap: _busy ? null : _pickDateOfBirth,
                          child: Text(_dateOfBirth == null ? 'Choose' : formatDate(_dateOfBirth!)),
                        ),
                      ),
                      const SizedBox(height: Gaps.lg),
                      NewPasswordFields(password: _password, confirmation: _passwordAgain, label: 'Password'),
                      const SizedBox(height: Gaps.lg),
                      NewPinFields(pin: _pin, confirmation: _pinAgain, label: 'Transaction PIN'),
                      const SizedBox(height: Gaps.lg),
                      PrimaryButton(label: 'Register', onPressed: _busy ? null : () => _register(branding)),
                      TextButton(onPressed: _busy ? null : () => setState(() => _sent = null), child: const Text('Send a new code')),
                    ],
                  ),
                ),
              ),
      ),
    );
  }
}
