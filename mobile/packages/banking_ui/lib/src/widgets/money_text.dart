import 'package:banking_core/banking_core.dart';
import 'package:flutter/material.dart';

/// Displays an amount calculated by the backend. The app never computes money; it only formats it.
class MoneyText extends StatelessWidget {
  const MoneyText(this.money, {super.key, this.style, this.format = const MoneyFormat()});

  final Money money;
  final TextStyle? style;
  final MoneyFormat format;

  @override
  Widget build(BuildContext context) {
    final base = style ?? DefaultTextStyle.of(context).style;
    final color = money.isNegative ? Theme.of(context).colorScheme.error : null;
    return Text(
      format.format(money),
      style: base.copyWith(color: color, fontFeatures: const [FontFeature.tabularFigures()]),
    );
  }
}

/// A balance that starts hidden in public places and is revealed on request.
class BalanceText extends StatefulWidget {
  const BalanceText(this.money, {super.key, this.initiallyHidden = true, this.style, this.asOf});

  final Money money;
  final bool initiallyHidden;
  final TextStyle? style;

  /// For offline data: shown as "as of …" so a cached balance is never mistaken for a live one.
  final String? asOf;

  @override
  State<BalanceText> createState() => _BalanceTextState();
}

class _BalanceTextState extends State<BalanceText> {
  late bool _hidden = widget.initiallyHidden;

  @override
  Widget build(BuildContext context) {
    final style = widget.style ?? Theme.of(context).textTheme.headlineSmall;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      mainAxisSize: MainAxisSize.min,
      children: [
        Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            if (_hidden)
              Semantics(label: 'Balance hidden', child: ExcludeSemantics(child: Text('••••••', style: style)))
            else
              MoneyText(widget.money, style: style),
            IconButton(
              tooltip: _hidden ? 'Show balance' : 'Hide balance',
              icon: Icon(_hidden ? Icons.visibility_outlined : Icons.visibility_off_outlined),
              onPressed: () => setState(() => _hidden = !_hidden),
            ),
          ],
        ),
        if (widget.asOf != null)
          Text('As of ${widget.asOf}', style: Theme.of(context).textTheme.bodySmall),
      ],
    );
  }
}
