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
        setState(() => _error = _messageFor(error, branding));
      }
    } finally {
      _password.clear();
    }
  }

  /// The customer sign-in service is part of customer digital banking; until the institution's server offers it,
  /// say so plainly instead of showing a misleading "wrong password".
  String _messageFor(ApiException error, InstitutionBranding branding) {
    final unavailable = error.statusCode == 404 || error.code == 'UNAUTHENTICATED' || error.code == 'NOT_FOUND';
    if (!unavailable) {
      return error.message;
    }
    final contact = branding.supportPhone ?? branding.supportEmail;
    return 'Mobile banking sign-in is not available yet.${contact == null ? '' : ' Please contact $contact.'}';
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
              ],
            ),
          ),
        ),
      ),
    );
  }
}
