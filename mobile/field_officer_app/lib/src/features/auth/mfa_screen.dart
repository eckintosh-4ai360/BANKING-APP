import 'package:banking_core/banking_core.dart';
import 'package:banking_ui/banking_ui.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../app/providers.dart';

class MfaScreen extends ConsumerStatefulWidget {
  const MfaScreen({super.key});

  @override
  ConsumerState<MfaScreen> createState() => _MfaScreenState();
}

class _MfaScreenState extends ConsumerState<MfaScreen> {
  final _code = TextEditingController();
  String? _error;
  bool _busy = false;

  @override
  void dispose() {
    _code.dispose();
    super.dispose();
  }

  Future<void> _verify() async {
    final problem = Validators.otp(_code.text);
    if (problem != null) {
      setState(() => _error = problem);
      return;
    }
    if (_busy) {
      return;
    }
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      await ref.read(sessionProvider).verifyMfa(_code.text.trim());
    } on ApiException catch (error) {
      if (mounted) {
        setState(() => _error = error.message);
      }
      _code.clear();
    } finally {
      if (mounted) {
        setState(() => _busy = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Verification')),
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.all(Gaps.lg),
          children: [
            Text('Enter the 6-digit code from your authenticator app.', style: Theme.of(context).textTheme.bodyLarge),
            const SizedBox(height: Gaps.lg),
            OtpField(controller: _code, enabled: !_busy, errorText: _error, onCompleted: (_) => _verify()),
            const SizedBox(height: Gaps.lg),
            PrimaryButton(label: 'Verify', onPressed: _busy ? null : _verify),
            const SizedBox(height: Gaps.sm),
            TextButton(
              onPressed: _busy ? null : () => ref.read(sessionProvider).signOut(),
              child: const Text('Use a different account'),
            ),
          ],
        ),
      ),
    );
  }
}
