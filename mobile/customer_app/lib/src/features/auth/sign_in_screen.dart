import 'package:banking_api/banking_api.dart';
import 'package:banking_core/banking_core.dart';
import 'package:banking_ui/banking_ui.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../app/providers.dart';
import '../../app/router.dart';

class SignInScreen extends ConsumerStatefulWidget {
  const SignInScreen({super.key});

  @override
  ConsumerState<SignInScreen> createState() => _SignInScreenState();
}

class _SignInScreenState extends ConsumerState<SignInScreen> {
  final _form = GlobalKey<FormState>();
  final _phone = TextEditingController();
  final _password = TextEditingController();
  String? _error;

  @override
  void dispose() {
    _phone.dispose();
    _password.dispose();
    super.dispose();
  }

  Future<void> _submit(InstitutionBranding branding) async {
    setState(() => _error = null);
    if (!(_form.currentState?.validate() ?? false)) {
      return;
    }
    try {
      await ref.read(sessionProvider).signIn(
            CustomerLoginRequest(
              institutionCode: branding.code,
              phoneNumber: _phone.text.replaceAll(RegExp(r'[\s-]'), ''),
              password: _password.text,
            ),
            institutionCode: branding.code,
          );
    } on ApiException catch (error) {
      if (mounted) {
        setState(() => _error = error.message);
      }
    } finally {
      _password.clear();
    }
  }

  @override
  Widget build(BuildContext context) {
    final branding = ref.watch(brandingProvider).requireValue;
    return Scaffold(
      appBar: AppBar(
        title: const Text('Sign in'),
        leading: BackButton(onPressed: () => context.go(Routes.welcome)),
      ),
      body: SafeArea(
        child: AutofillGroup(
          child: Form(
            key: _form,
            child: ListView(
              padding: const EdgeInsets.all(Gaps.lg),
              children: [
                Text(branding.displayName, style: Theme.of(context).textTheme.titleLarge),
                const SizedBox(height: Gaps.lg),
                if (_error != null) ...[NoticeBanner(_error!, error: true), const SizedBox(height: Gaps.md)],
                TextFormField(
                  controller: _phone,
                  keyboardType: TextInputType.phone,
                  autofillHints: const [AutofillHints.telephoneNumber],
                  textInputAction: TextInputAction.next,
                  decoration: const InputDecoration(labelText: 'Phone number'),
                  validator: Validators.phoneNumber,
                ),
                const SizedBox(height: Gaps.md),
                PasswordField(
                  controller: _password,
                  validator: (value) => Validators.required(value, 'Enter your password'),
                  onSubmitted: (_) => _submit(branding),
                ),
                const SizedBox(height: Gaps.lg),
                PrimaryButton(label: 'Sign in', onPressed: () => _submit(branding)),
                TextButton(onPressed: () => context.go(Routes.forgotPassword), child: const Text('Forgot your password?')),
                const SizedBox(height: Gaps.sm),
                Text(
                  'Signing in on a new phone? We will text a code to the number you sign in with.',
                  style: Theme.of(context).textTheme.bodySmall,
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
