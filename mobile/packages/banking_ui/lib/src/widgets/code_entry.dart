import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

/// On-screen keypad for a transaction PIN. Digits are never displayed (only dots), never put in a text field the
/// keyboard or autofill can see, and the PIN is handed to [onCompleted] once complete, then cleared.
class PinKeypad extends StatefulWidget {
  const PinKeypad({super.key, required this.onCompleted, this.length = 4, this.enabled = true});

  final int length;
  final bool enabled;
  final ValueChanged<String> onCompleted;

  @override
  State<PinKeypad> createState() => _PinKeypadState();
}

class _PinKeypadState extends State<PinKeypad> {
  final _digits = StringBuffer();

  void _press(String digit) {
    if (!widget.enabled || _digits.length >= widget.length) {
      return;
    }
    setState(() => _digits.write(digit));
    if (_digits.length == widget.length) {
      final pin = _digits.toString();
      setState(_digits.clear);
      widget.onCompleted(pin);
    }
  }

  void _backspace() {
    if (_digits.isEmpty) {
      return;
    }
    final current = _digits.toString();
    setState(() {
      _digits
        ..clear()
        ..write(current.substring(0, current.length - 1));
    });
  }

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        Semantics(
          label: '${_digits.length} of ${widget.length} digits entered',
          child: Row(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              for (var index = 0; index < widget.length; index++)
                Container(
                  key: ValueKey('pin-dot-$index'),
                  margin: const EdgeInsets.all(8),
                  width: 14,
                  height: 14,
                  decoration: BoxDecoration(
                    shape: BoxShape.circle,
                    color: index < _digits.length ? scheme.primary : null,
                    border: Border.all(color: scheme.outline),
                  ),
                ),
            ],
          ),
        ),
        const SizedBox(height: 16),
        for (final row in const [
          ['1', '2', '3'],
          ['4', '5', '6'],
          ['7', '8', '9'],
          ['', '0', '⌫'],
        ])
          Row(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              for (final key in row)
                Padding(
                  padding: const EdgeInsets.all(6),
                  child: SizedBox.square(
                    dimension: 64,
                    child: key.isEmpty
                        ? null
                        : key == '⌫'
                            ? IconButton(
                                tooltip: 'Delete',
                                onPressed: widget.enabled ? _backspace : null,
                                icon: const Icon(Icons.backspace_outlined),
                              )
                            : OutlinedButton(
                                style: OutlinedButton.styleFrom(shape: const CircleBorder(), padding: EdgeInsets.zero),
                                onPressed: widget.enabled ? () => _press(key) : null,
                                child: Text(key, style: Theme.of(context).textTheme.titleLarge),
                              ),
                  ),
                ),
            ],
          ),
      ],
    );
  }
}

/// Six-digit one-time code (authenticator or SMS). Digits only; supports platform autofill of one-time codes.
class OtpField extends StatelessWidget {
  const OtpField({super.key, required this.controller, this.onCompleted, this.errorText, this.enabled = true, this.length = 6});

  final TextEditingController controller;
  final ValueChanged<String>? onCompleted;
  final String? errorText;
  final bool enabled;
  final int length;

  @override
  Widget build(BuildContext context) {
    return TextField(
      controller: controller,
      enabled: enabled,
      autofocus: true,
      keyboardType: TextInputType.number,
      autofillHints: const [AutofillHints.oneTimeCode],
      textAlign: TextAlign.center,
      style: Theme.of(context).textTheme.headlineSmall?.copyWith(letterSpacing: 12),
      maxLength: length,
      inputFormatters: [FilteringTextInputFormatter.digitsOnly, LengthLimitingTextInputFormatter(length)],
      decoration: InputDecoration(labelText: 'Verification code', counterText: '', errorText: errorText),
      onChanged: (value) {
        if (value.length == length) {
          onCompleted?.call(value);
        }
      },
    );
  }
}
