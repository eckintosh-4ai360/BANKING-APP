import 'package:banking_core/banking_core.dart';
import 'package:banking_ui/banking_ui.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../app/providers.dart';

/// Shown when the account still has the temporary password an administrator issued.
class PasswordChangeScreen extends ConsumerStatefulWidget {
  const PasswordChangeScreen({super.key});

  @override
  ConsumerState<PasswordChangeScreen> createState() => _PasswordChangeScreenState();
}

class _PasswordChangeScreenState extends ConsumerState<PasswordChangeScreen> {
  final _form = GlobalKey<FormState>();
  final _current = TextEditingController();
  final _next = TextEditingController();
  final _confirm = TextEditingController();
  ApiException? _error;

  @override
  void dispose() {
    _current.dispose();
    _next.dispose();
    _confirm.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    setState(() => _error = null);
    if (!(_form.currentState?.validate() ?? false)) {
      return;
    }
    try {
      await ref.read(sessionProvider).changePassword(currentPassword: _current.text, newPassword: _next.text);
    } on ApiException catch (error) {
      if (mounted) {
        setState(() => _error = error);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Choose a new password'),
        actions: [TextButton(onPressed: () => ref.read(sessionProvider).signOut(), child: const Text('Sign out'))],
      ),
      body: SafeArea(
        child: Form(
          key: _form,
          child: ListView(
            padding: const EdgeInsets.all(Gaps.lg),
            children: [
              const Text('Your password was set by an administrator. Choose your own to continue.'),
              const SizedBox(height: Gaps.lg),
              if (_error != null) ...[NoticeBanner(_error!.message, error: true), const SizedBox(height: Gaps.md)],
              PasswordField(
                controller: _current,
                label: 'Current password',
                textInputAction: TextInputAction.next,
                validator: (value) => Validators.required(value, 'Enter your current password'),
              ),
              const SizedBox(height: Gaps.md),
              PasswordField(
                controller: _next,
                label: 'New password',
                autofillHints: const [AutofillHints.newPassword],
                textInputAction: TextInputAction.next,
                validator: (value) {
                  final problem = Validators.newPassword(value) ?? _error?.fieldError('newPassword');
                  if (problem == null && value == _current.text) {
                    return 'Choose a password you have not used';
                  }
                  return problem;
                },
              ),
              const SizedBox(height: Gaps.md),
              PasswordField(
                controller: _confirm,
                label: 'Confirm new password',
                autofillHints: const [AutofillHints.newPassword],
                validator: (value) => value == _next.text ? null : 'The passwords do not match',
                onSubmitted: (_) => _submit(),
              ),
              const SizedBox(height: Gaps.lg),
              PrimaryButton(label: 'Save and continue', onPressed: _submit),
            ],
          ),
        ),
      ),
    );
  }
}
