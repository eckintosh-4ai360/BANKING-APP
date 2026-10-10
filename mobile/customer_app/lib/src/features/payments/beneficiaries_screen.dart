import 'package:banking_core/banking_core.dart';
import 'package:banking_ui/banking_ui.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../api/models.dart';
import '../../app/providers.dart';
import '../../widgets/common.dart';

/// Saved accounts of the institution the customer pays often. Adding one needs the PIN, and for a while afterwards
/// transfers to it are limited (a takeover usually adds a beneficiary before emptying the accounts).
class BeneficiariesScreen extends ConsumerWidget {
  const BeneficiariesScreen({super.key});

  Future<void> _remove(BuildContext context, WidgetRef ref, Beneficiary beneficiary) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text('Remove ${beneficiary.nickname}?'),
        content: const Text('You can add them again later.'),
        actions: [
          TextButton(onPressed: () => Navigator.of(context).pop(false), child: const Text('Cancel')),
          FilledButton(onPressed: () => Navigator.of(context).pop(true), child: const Text('Remove')),
        ],
      ),
    );
    if (confirmed != true) {
      return;
    }
    try {
      await ref.read(customerApiProvider).removeBeneficiary(beneficiary.id);
      ref.invalidate(beneficiariesProvider);
    } on ApiException catch (error) {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(error.message)));
      }
    }
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final beneficiaries = ref.watch(beneficiariesProvider);
    return Scaffold(
      appBar: AppBar(title: const Text('Beneficiaries')),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => showModalBottomSheet<void>(
          context: context,
          isScrollControlled: true,
          builder: (_) => const _AddBeneficiarySheet(),
        ),
        icon: const Icon(Icons.person_add_alt_outlined),
        label: const Text('Add'),
      ),
      body: SafeArea(
        child: AsyncBody(
          value: beneficiaries,
          onRetry: () => ref.invalidate(beneficiariesProvider),
          builder: (beneficiaries) => beneficiaries.isEmpty
              ? const Center(child: Padding(padding: EdgeInsets.all(Gaps.lg), child: Text('No beneficiaries yet.')))
              : ListView(
                  children: [
                    for (final beneficiary in beneficiaries)
                      ListTile(
                        leading: const Icon(Icons.person_outline),
                        title: Text(beneficiary.nickname),
                        subtitle: Text([
                          beneficiary.accountNumber,
                          ?beneficiary.name,
                          if (beneficiary.coolingDownUntil != null && beneficiary.coolingDownUntil!.isAfter(DateTime.now().toUtc()))
                            'new: limited until ${formatDateTime(beneficiary.coolingDownUntil!)}',
                        ].join(' · ')),
                        trailing: IconButton(
                          tooltip: 'Remove',
                          icon: const Icon(Icons.delete_outline),
                          onPressed: () => _remove(context, ref, beneficiary),
                        ),
                      ),
                  ],
                ),
        ),
      ),
    );
  }
}

class _AddBeneficiarySheet extends ConsumerStatefulWidget {
  const _AddBeneficiarySheet();

  @override
  ConsumerState<_AddBeneficiarySheet> createState() => _AddBeneficiarySheetState();
}

class _AddBeneficiarySheetState extends ConsumerState<_AddBeneficiarySheet> {
  final _form = GlobalKey<FormState>();
  final _accountNumber = TextEditingController();
  final _nickname = TextEditingController();
  TransferDestination? _destination;
  String? _error;
  bool _busy = false;

  @override
  void dispose() {
    _accountNumber.dispose();
    _nickname.dispose();
    super.dispose();
  }

  Future<void> _lookUp() async {
    setState(() {
      _destination = null;
      _error = null;
    });
    try {
      final destination = await ref.read(customerApiProvider).destination(_accountNumber.text.trim());
      setState(() => _destination = destination);
    } on ApiException catch (error) {
      setState(() => _error = error.message);
    }
  }

  Future<void> _save() async {
    if (!(_form.currentState?.validate() ?? false)) {
      return;
    }
    if (_destination == null) {
      await _lookUp();
      if (_destination == null) {
        return;
      }
    }
    if (!mounted) {
      return;
    }
    final pin = await askForPin(context, action: 'Add ${_destination!.name} (${_destination!.accountNumber}) as a beneficiary.');
    if (pin == null) {
      return;
    }
    setState(() => _busy = true);
    try {
      await ref.read(customerApiProvider).addBeneficiary(accountNumber: _destination!.accountNumber, nickname: _nickname.text.trim(), pin: pin);
      ref.invalidate(beneficiariesProvider);
      if (mounted) {
        Navigator.of(context).pop();
      }
    } on ApiException catch (error) {
      setState(() => _error = error.message);
    } finally {
      if (mounted) {
        setState(() => _busy = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) => Padding(
        padding: EdgeInsets.fromLTRB(Gaps.lg, Gaps.lg, Gaps.lg, Gaps.lg + MediaQuery.viewInsetsOf(context).bottom),
        child: Form(
          key: _form,
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Text('Add a beneficiary', style: Theme.of(context).textTheme.titleLarge),
              const SizedBox(height: Gaps.sm),
              const Text('An account of this institution. Bank and mobile money accounts come later.'),
              const SizedBox(height: Gaps.md),
              if (_error != null) ...[NoticeBanner(_error!, error: true), const SizedBox(height: Gaps.md)],
              TextFormField(
                controller: _accountNumber,
                keyboardType: TextInputType.number,
                decoration: InputDecoration(
                  labelText: 'Account number',
                  suffixIcon: IconButton(tooltip: 'Check the account', icon: const Icon(Icons.search), onPressed: _lookUp),
                ),
                validator: (value) => Validators.required(value, 'Enter the account number'),
                onChanged: (_) => setState(() => _destination = null),
              ),
              if (_destination != null)
                ListTile(contentPadding: EdgeInsets.zero, leading: const Icon(Icons.verified_user_outlined), title: Text(_destination!.name)),
              const SizedBox(height: Gaps.md),
              TextFormField(
                controller: _nickname,
                maxLength: 60,
                textCapitalization: TextCapitalization.words,
                decoration: const InputDecoration(labelText: 'Name to show'),
                validator: (value) => Validators.required(value, 'Give them a name'),
              ),
              const SizedBox(height: Gaps.md),
              ActionButton(label: 'Save', busy: _busy, onPressed: _save),
            ],
          ),
        ),
      );
}
