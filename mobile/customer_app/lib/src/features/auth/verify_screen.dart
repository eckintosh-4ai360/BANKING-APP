import 'package:banking_core/banking_core.dart';
import 'package:banking_ui/banking_ui.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../app/providers.dart';

/// Second step of sign-in: the one-time code.
class VerifyScreen extends ConsumerStatefulWidget {
  const VerifyScreen({super.key});

  @override
  ConsumerState<VerifyScreen> createState() => _VerifyScreenState();
}

class _VerifyScreenState extends ConsumerState<VerifyScreen> {
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
    if (problem != null || _busy) {
      setState(() => _error = problem ?? _error);
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
      appBar: AppBar(title: const Text('Verify it’s you')),
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.all(Gaps.lg),
          children: [
            const Text('Enter the 6-digit verification code.'),
            const SizedBox(height: Gaps.lg),
            OtpField(controller: _code, enabled: !_busy, errorText: _error, onCompleted: (_) => _verify()),
            const SizedBox(height: Gaps.lg),
            PrimaryButton(label: 'Verify', onPressed: _busy ? null : _verify),
            TextButton(onPressed: _busy ? null : () => ref.read(sessionProvider).signOut(), child: const Text('Cancel')),
          ],
        ),
      ),
    );
  }
}
