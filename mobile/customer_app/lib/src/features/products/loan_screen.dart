import 'package:banking_core/banking_core.dart';
import 'package:banking_ui/banking_ui.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../api/models.dart';
import '../../app/providers.dart';
import '../../widgets/common.dart';

/// A loan: what is owed, what is due next, the schedule and the payments made. The customer repays from the loan's
/// repayment account; the server splits the payment into penalty, interest and principal.
class LoanScreen extends ConsumerWidget {
  const LoanScreen({super.key, required this.loanId});

  final String loanId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final detail = ref.watch(loanProvider(loanId));
    return Scaffold(
      appBar: AppBar(title: Text(detail.value?.loan.loanNumber ?? 'Loan')),
      body: SafeArea(
        child: RefreshIndicator(
          onRefresh: () => ref.refresh(loanProvider(loanId).future),
          child: AsyncBody(
            value: detail,
            onRetry: () => ref.invalidate(loanProvider(loanId)),
            builder: (detail) => _LoanView(detail: detail),
          ),
        ),
      ),
    );
  }
}

class _LoanView extends ConsumerWidget {
  const _LoanView({required this.detail});

  final LoanDetail detail;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final loan = detail.loan;
    final text = Theme.of(context).textTheme;
    return ListView(
      padding: const EdgeInsets.all(Gaps.md),
      children: [
        Text(loan.productName, style: text.titleLarge),
        Text(humanize(loan.status)),
        const SizedBox(height: Gaps.md),
        if (loan.daysPastDue > 0)
          NoticeBanner('This loan is ${loan.daysPastDue} days late. Please pay what is overdue to avoid penalties.', error: true),
        InfoRow('Borrowed', MoneyText(loan.principal)),
        InfoRow('Principal still owed', MoneyText(loan.principalOutstanding)),
        if (!loan.interestDue.isZero) InfoRow('Interest due', MoneyText(loan.interestDue)),
        if (!loan.penaltyDue.isZero) InfoRow('Penalties due', MoneyText(loan.penaltyDue)),
        if (!loan.arrears.isZero) InfoRow('Overdue', MoneyText(loan.arrears)),
        if (loan.nextDueDate != null && loan.nextDueAmount != null)
          InfoRow('Next payment', Text('${const MoneyFormat().format(loan.nextDueAmount!)} on ${formatDate(loan.nextDueDate!)}')),
        if (detail.payoff != null) InfoRow('To pay it off today', MoneyText(detail.payoff!)),
        if (loan.annualRate != null) InfoRow('Interest rate', Text('${loan.annualRate}% a year')),
        if (loan.maturityDate != null) InfoRow('Last payment due', Text(formatDate(loan.maturityDate!))),
        const SizedBox(height: Gaps.md),
        if (loan.isActive) ActionButton(label: 'Repay', onPressed: () => _repay(context, ref, detail)),
        const SizedBox(height: Gaps.lg),
        Text('Schedule', style: text.titleMedium),
        for (final installment in detail.schedule)
          ListTile(
            contentPadding: EdgeInsets.zero,
            leading: CircleAvatar(child: Text('${installment.number}')),
            title: Text(formatDate(installment.dueDate)),
            subtitle: Text(installment.outstanding.isZero ? 'Paid' : '${humanize(installment.status)} · ${const MoneyFormat().format(installment.outstanding)} to pay'),
            trailing: MoneyText(installment.paid),
          ),
        if (detail.repayments.isNotEmpty) ...[
          const SizedBox(height: Gaps.md),
          Text('Payments', style: text.titleMedium),
          for (final repayment in detail.repayments)
            ListTile(
              contentPadding: EdgeInsets.zero,
              title: MoneyText(repayment.amount),
              subtitle: Text(repayment.businessDate == null ? '' : formatDate(repayment.businessDate!)),
            ),
        ],
      ],
    );
  }

  Future<void> _repay(BuildContext context, WidgetRef ref, LoanDetail detail) async {
    final receipt = await showModalBottomSheet<LoanRepaymentReceipt>(
      context: context,
      isScrollControlled: true,
      builder: (_) => _RepaySheet(detail: detail),
    );
    if (receipt == null) {
      return;
    }
    ref
      ..invalidate(loanProvider(detail.loan.id))
      ..invalidate(loansProvider)
      ..invalidate(accountsProvider);
    if (context.mounted) {
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(
        content: Text(receipt.settled
            ? 'Thank you. The loan is repaid in full.'
            : 'Thank you. ${const MoneyFormat().format(receipt.repayment.amount)} was paid.'),
      ));
    }
  }
}

/// An amount the server quoted, as the customer would type it (`224.00`).
String _plain(Money? money) => money == null ? '' : money.amount.toStringAsFixed(MoneyFormat.minorUnitsOf(money.currency));

class _RepaySheet extends ConsumerStatefulWidget {
  const _RepaySheet({required this.detail});

  final LoanDetail detail;

  @override
  ConsumerState<_RepaySheet> createState() => _RepaySheetState();
}

class _RepaySheetState extends ConsumerState<_RepaySheet> {
  final _form = GlobalKey<FormState>();
  late final _amount = TextEditingController(text: _plain(widget.detail.loan.nextDueAmount));
  String? _error;
  String? _idempotencyKey;
  bool _busy = false;

  @override
  void dispose() {
    _amount.dispose();
    super.dispose();
  }

  Future<void> _pay() async {
    if (!(_form.currentState?.validate() ?? false)) {
      return;
    }
    final loan = widget.detail.loan;
    final amount = _amount.text.trim();
    final pin = await askForPin(context, action: 'Pay ${loan.currency} $amount towards loan ${loan.loanNumber}.');
    if (pin == null || !mounted) {
      return;
    }
    final key = _idempotencyKey ??= newIdempotencyKey();
    setState(() => _busy = true);
    try {
      final receipt = await ref.read(customerApiProvider).repayLoan(idempotencyKey: key, loanId: loan.id, amount: amount, pin: pin);
      if (mounted) {
        Navigator.of(context).pop(receipt);
      }
    } on ApiException catch (error) {
      setState(() {
        final refused = error.kind == ApiErrorKind.rejected || error.kind == ApiErrorKind.unauthenticated;
        if (refused) {
          _idempotencyKey = null;
        }
        _error = refused ? error.message : '${error.message} Paying again is safe: it is taken only once.';
      });
    } finally {
      if (mounted) {
        setState(() => _busy = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final detail = widget.detail;
    return Padding(
      padding: EdgeInsets.fromLTRB(Gaps.lg, Gaps.lg, Gaps.lg, Gaps.lg + MediaQuery.viewInsetsOf(context).bottom),
      child: Form(
        key: _form,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text('Repay ${detail.loan.loanNumber}', style: Theme.of(context).textTheme.titleLarge),
            const SizedBox(height: Gaps.sm),
            const Text('Paid from the loan’s repayment account.'),
            if (detail.payoff != null) ...[
              const SizedBox(height: Gaps.sm),
              Row(children: [const Text('To pay it off today: '), MoneyText(detail.payoff!)]),
              Align(
                alignment: Alignment.centerLeft,
                child: TextButton(
                  onPressed: () => setState(() {
                    _amount.text = _plain(detail.payoff);
                    _idempotencyKey = null;
                  }),
                  child: const Text('Pay it all off'),
                ),
              ),
            ],
            const SizedBox(height: Gaps.md),
            if (_error != null) ...[NoticeBanner(_error!, error: true), const SizedBox(height: Gaps.md)],
            TextFormField(
              controller: _amount,
              keyboardType: const TextInputType.numberWithOptions(decimal: true),
              decoration: InputDecoration(labelText: 'Amount', prefixText: '${detail.loan.currency} '),
              validator: Validators.amount,
              onChanged: (_) => _idempotencyKey = null,
            ),
            const SizedBox(height: Gaps.md),
            ActionButton(label: 'Pay', busy: _busy, onPressed: _pay),
          ],
        ),
      ),
    );
  }
}
