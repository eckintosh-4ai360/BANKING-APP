import 'package:banking_api/banking_api.dart';
import 'package:banking_core/banking_core.dart';
import 'package:banking_ui/banking_ui.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../app/providers.dart';

class SignInScreen extends ConsumerStatefulWidget {
  const SignInScreen({super.key});

  @override
  ConsumerState<SignInScreen> createState() => _SignInScreenState();
}

class _SignInScreenState extends ConsumerState<SignInScreen> {
  final _form = GlobalKey<FormState>();
  final _institution = TextEditingController();
  final _username = TextEditingController();
  final _password = TextEditingController();
  ApiException? _error;

  @override
  void initState() {
    super.initState();
    // Pre-fill the institution used last time (read fresh on every visit, e.g. after signing out).
    ref.read(backendProvider).store.rememberedInstitution().then((code) {
      if (mounted && code != null && _institution.text.isEmpty) {
        _institution.text = code;
      }
    });
  }

  @override
  void dispose() {
    _institution.dispose();
    _username.dispose();
    _password.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    setState(() => _error = null);
    if (!(_form.currentState?.validate() ?? false)) {
      return;
    }
    final code = _institution.text.trim().toLowerCase();
    try {
      await ref.read(sessionProvider).signIn(
            StaffLoginRequest(tenantCode: code, username: _username.text.trim(), password: _password.text),
            institutionCode: code,
          );
    } on ApiException catch (error) {
      if (mounted) {
        setState(() => _error = error);
      }
    } finally {
      // The password is never kept around after an attempt.
      _password.clear();
    }
  }

  @override
  Widget build(BuildContext context) {
    final state = ref.watch(sessionStateProvider);
    final notice = state is SignedOut ? state.notice : null;

    return Scaffold(
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(Gaps.lg),
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 420),
              child: AutofillGroup(
                child: Form(
                  key: _form,
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      const InstitutionHeader(name: 'Field Officer', subtitle: 'Sign in with your staff account'),
                      const SizedBox(height: Gaps.lg),
                      if (_error != null) ...[NoticeBanner(_error!.message, error: true), const SizedBox(height: Gaps.md)],
                      if (_error == null && notice != null) ...[NoticeBanner(notice), const SizedBox(height: Gaps.md)],
                      TextFormField(
                        controller: _institution,
                        decoration: const InputDecoration(labelText: 'Institution code'),
                        autocorrect: false,
                        textInputAction: TextInputAction.next,
                        validator: Validators.institutionCode,
                      ),
                      const SizedBox(height: Gaps.md),
                      TextFormField(
                        controller: _username,
                        decoration: InputDecoration(labelText: 'Username', errorText: _error?.fieldError('username')),
                        autocorrect: false,
                        autofillHints: const [AutofillHints.username],
                        textInputAction: TextInputAction.next,
                        validator: (value) => Validators.required(value, 'Enter your username'),
                      ),
                      const SizedBox(height: Gaps.md),
                      PasswordField(
                        controller: _password,
                        validator: (value) => Validators.required(value, 'Enter your password'),
                        onSubmitted: (_) => _submit(),
                      ),
                      const SizedBox(height: Gaps.lg),
                      PrimaryButton(label: 'Sign in', onPressed: _submit),
                    ],
                  ),
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}
