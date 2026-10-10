import 'package:banking_api/banking_api.dart';
import 'package:banking_core/banking_core.dart';
import 'package:banking_ui/banking_ui.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../api/customer_api.dart';
import '../../api/onboarding.dart';
import '../../app/providers.dart';
import '../../widgets/common.dart';
import 'document_picker.dart';

/// One stage of signing up, from its route (`personal-details`, `identity-document`, ...).
class SignUpStageScreen extends ConsumerWidget {
  const SignUpStageScreen({super.key, required this.stage});

  final String stage;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final progress = ref.watch(onboardingProvider);
    final code = stage.toUpperCase().replaceAll('-', '_');
    return AsyncBody(
      value: progress,
      onRetry: () => ref.invalidate(onboardingProvider),
      builder: (progress) => switch (code) {
        'PERSONAL_DETAILS' => _PersonalForm(progress: progress),
        'EMPLOYMENT' => _EmploymentForm(progress: progress),
        'IDENTITY_DOCUMENT' => _IdentityForm(progress: progress),
        'SELFIE' => _DocumentsStage(
            title: 'Selfie',
            intro: 'A clear photo of your face, in good light, without glasses or a hat.',
            documents: const [(type: 'SELFIE', label: 'Your photo', selfie: true, required: true)],
            progress: progress,
          ),
        'SIGNATURE' => _DocumentsStage(
            title: 'Signature',
            intro: 'Sign on a white sheet of paper and take a photo of it.',
            documents: const [(type: 'SIGNATURE', label: 'Your signature', selfie: false, required: true)],
            progress: progress,
          ),
        'ADDRESS' => _AddressForm(progress: progress),
        'NEXT_OF_KIN' => _NextOfKinForm(progress: progress),
        'RISK_PROFILE' => _RiskProfileForm(progress: progress),
        _ => Scaffold(appBar: AppBar(), body: const ErrorView(message: 'This step does not exist.')),
      },
    );
  }
}

/// Saves a stage: the server's answer replaces the progress, then back to the stages.
Future<void> _save(BuildContext context, WidgetRef ref, Future<OnboardingProgress> Function(CustomerApi api) call) async {
  try {
    await call(ref.read(customerApiProvider));
    ref.invalidate(onboardingProvider);
    if (context.mounted) {
      Navigator.of(context).maybePop();
    }
  } on ApiException catch (error) {
    if (context.mounted) {
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(error.message)));
    }
  }
}

class _StageScaffold extends StatelessWidget {
  const _StageScaffold({required this.title, required this.children, this.formKey, this.onSave, this.intro});

  final String title;
  final String? intro;
  final GlobalKey<FormState>? formKey;
  final List<Widget> children;
  final Future<void> Function()? onSave;

  @override
  Widget build(BuildContext context) {
    final body = ListView(
      padding: const EdgeInsets.all(Gaps.lg),
      children: [
        if (intro != null) ...[Text(intro!), const SizedBox(height: Gaps.lg)],
        ...children,
        if (onSave != null) ...[const SizedBox(height: Gaps.lg), PrimaryButton(label: 'Save', onPressed: onSave)],
      ],
    );
    return Scaffold(
      appBar: AppBar(title: Text(title)),
      body: SafeArea(child: formKey == null ? body : Form(key: formKey, child: body)),
    );
  }
}

Widget _gap() => const SizedBox(height: Gaps.md);

String? _optional(TextEditingController controller) => controller.text.trim().isEmpty ? null : controller.text.trim();

DropdownButtonFormField<String> _choice(
  String label,
  String? value,
  Map<String, String> options,
  ValueChanged<String?> onChanged, {
  bool required = true,
}) =>
    DropdownButtonFormField<String>(
      initialValue: options.containsKey(value) ? value : null,
      decoration: InputDecoration(labelText: label),
      items: [for (final option in options.entries) DropdownMenuItem(value: option.key, child: Text(option.value))],
      validator: required ? (selected) => selected == null ? 'Choose one' : null : null,
      onChanged: onChanged,
    );

/// A date chosen from a calendar.
class _DateField extends StatelessWidget {
  const _DateField({required this.label, required this.value, required this.onChanged, required this.first, required this.last, this.error});

  final String label;
  final DateTime? value;
  final ValueChanged<DateTime> onChanged;
  final DateTime first;
  final DateTime last;
  final String? error;

  @override
  Widget build(BuildContext context) => InputDecorator(
        decoration: InputDecoration(labelText: label, errorText: error),
        child: InkWell(
          onTap: () async {
            final picked = await showDatePicker(
              context: context,
              initialDate: value ?? (last.isBefore(DateTime.now()) ? last : DateTime.now()),
              firstDate: first,
              lastDate: last,
              helpText: label,
            );
            if (picked != null) {
              onChanged(picked);
            }
          },
          child: Text(value == null ? 'Choose' : formatDate(value!)),
        ),
      );
}

// ----------------------------------------------------------------------------------------------- personal

class _PersonalForm extends ConsumerStatefulWidget {
  const _PersonalForm({required this.progress});

  final OnboardingProgress progress;

  @override
  ConsumerState<_PersonalForm> createState() => _PersonalFormState();
}

class _PersonalFormState extends ConsumerState<_PersonalForm> {
  final _form = GlobalKey<FormState>();
  late final OnboardingProfile _profile = widget.progress.profile;
  late final _firstName = TextEditingController(text: _profile.firstName);
  late final _middleName = TextEditingController(text: _profile.middleName);
  late final _lastName = TextEditingController(text: _profile.lastName);
  late final _email = TextEditingController(text: _profile.email);
  late final _nationality = TextEditingController(text: _profile.nationality);
  late DateTime? _dateOfBirth = _profile.dateOfBirth;
  late String? _gender = _profile.gender;
  late String? _maritalStatus = _profile.maritalStatus;
  String? _dateError;

  @override
  void dispose() {
    for (final controller in [_firstName, _middleName, _lastName, _email, _nationality]) {
      controller.dispose();
    }
    super.dispose();
  }

  Future<void> _submit() async {
    final valid = _form.currentState?.validate() ?? false;
    setState(() => _dateError = _dateOfBirth == null ? 'Choose your date of birth' : null);
    if (!valid || _dateOfBirth == null) {
      return;
    }
    await _save(context, ref, (api) => api.savePersonal({
          'firstName': _firstName.text.trim(),
          'middleName': ?_optional(_middleName),
          'lastName': _lastName.text.trim(),
          'dateOfBirth': isoDate(_dateOfBirth!),
          'gender': _gender,
          'nationality': _nationality.text.trim().toUpperCase(),
          'maritalStatus': ?_maritalStatus,
          'email': ?_optional(_email),
        }));
  }

  @override
  Widget build(BuildContext context) {
    final branding = ref.watch(brandingProvider).value;
    if (_nationality.text.isEmpty) {
      _nationality.text = branding == null ? '' : countryOf(branding);
    }
    final today = DateTime.now();
    return _StageScaffold(
      title: 'Personal details',
      intro: 'As written on your identity document.',
      formKey: _form,
      onSave: _submit,
      children: [
        TextFormField(
          controller: _firstName,
          textCapitalization: TextCapitalization.words,
          decoration: const InputDecoration(labelText: 'First name'),
          validator: (value) => Validators.required(value, 'Enter your first name'),
        ),
        _gap(),
        TextFormField(controller: _middleName, textCapitalization: TextCapitalization.words, decoration: const InputDecoration(labelText: 'Middle name (optional)')),
        _gap(),
        TextFormField(
          controller: _lastName,
          textCapitalization: TextCapitalization.words,
          decoration: const InputDecoration(labelText: 'Last name'),
          validator: (value) => Validators.required(value, 'Enter your last name'),
        ),
        _gap(),
        _DateField(
          label: 'Date of birth',
          value: _dateOfBirth,
          error: _dateError,
          first: DateTime(today.year - 120),
          last: today,
          onChanged: (date) => setState(() => _dateOfBirth = date),
        ),
        _gap(),
        _choice('Gender', _gender, const {'FEMALE': 'Female', 'MALE': 'Male', 'OTHER': 'Other', 'UNDISCLOSED': 'Prefer not to say'},
            (value) => setState(() => _gender = value)),
        _gap(),
        TextFormField(
          controller: _nationality,
          textCapitalization: TextCapitalization.characters,
          maxLength: 2,
          decoration: const InputDecoration(labelText: 'Nationality (country code, e.g. GH)', counterText: ''),
          validator: (value) => RegExp(r'^[A-Za-z]{2}$').hasMatch(value?.trim() ?? '') ? null : 'Enter a 2-letter country code',
        ),
        _gap(),
        _choice(
          'Marital status',
          _maritalStatus,
          const {'SINGLE': 'Single', 'MARRIED': 'Married', 'DIVORCED': 'Divorced', 'WIDOWED': 'Widowed', 'SEPARATED': 'Separated', 'UNDISCLOSED': 'Prefer not to say'},
          (value) => setState(() => _maritalStatus = value),
          required: false,
        ),
        _gap(),
        TextFormField(
          controller: _email,
          keyboardType: TextInputType.emailAddress,
          autofillHints: const [AutofillHints.email],
          decoration: const InputDecoration(labelText: 'Email (optional)'),
          validator: (value) => value == null || value.trim().isEmpty || RegExp(r'^\S+@\S+\.\S+$').hasMatch(value.trim()) ? null : 'Enter a valid email',
        ),
      ],
    );
  }
}

/// The institution's country, from its locale (`en-GH` → `GH`).
String countryOf(InstitutionBranding branding) {
  final parts = branding.locale.split(RegExp('[-_]'));
  return parts.length > 1 && parts.last.length == 2 ? parts.last.toUpperCase() : '';
}

// --------------------------------------------------------------------------------------------- employment

class _EmploymentForm extends ConsumerStatefulWidget {
  const _EmploymentForm({required this.progress});

  final OnboardingProgress progress;

  @override
  ConsumerState<_EmploymentForm> createState() => _EmploymentFormState();
}

class _EmploymentFormState extends ConsumerState<_EmploymentForm> {
  final _form = GlobalKey<FormState>();
  late final OnboardingProfile _profile = widget.progress.profile;
  late String? _status = _profile.employmentStatus;
  late String? _income = _profile.monthlyIncomeBand;
  late final _occupation = TextEditingController(text: _profile.occupation);
  late final _employer = TextEditingController(text: _profile.employerName);

  @override
  void dispose() {
    _occupation.dispose();
    _employer.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (!(_form.currentState?.validate() ?? false)) {
      return;
    }
    await _save(context, ref, (api) => api.saveEmployment({
          'employmentStatus': _status,
          'occupation': ?_optional(_occupation),
          'employerName': ?_optional(_employer),
          'monthlyIncomeBand': ?_income,
        }));
  }

  @override
  Widget build(BuildContext context) => _StageScaffold(
        title: 'Work or business',
        formKey: _form,
        onSave: _submit,
        children: [
          _choice(
            'What you do',
            _status,
            const {
              'EMPLOYED': 'Employed',
              'SELF_EMPLOYED': 'Self-employed or own a business',
              'STUDENT': 'Student',
              'RETIRED': 'Retired',
              'UNEMPLOYED': 'Not working',
              'OTHER': 'Other',
            },
            (value) => setState(() => _status = value),
          ),
          _gap(),
          TextFormField(controller: _occupation, maxLength: 100, decoration: const InputDecoration(labelText: 'Occupation or trade (e.g. trader, teacher)')),
          _gap(),
          TextFormField(controller: _employer, maxLength: 150, decoration: const InputDecoration(labelText: 'Employer or business name (optional)')),
          _gap(),
          _choice(
            'Monthly income (optional)',
            _income,
            const {'UNDER_1000': 'Under 1,000', '1000_TO_5000': '1,000 to 5,000', '5000_TO_20000': '5,000 to 20,000', 'OVER_20000': 'Over 20,000'},
            (value) => setState(() => _income = value),
            required: false,
          ),
        ],
      );
}

// ---------------------------------------------------------------------------------------------- identity

class _IdentityForm extends ConsumerStatefulWidget {
  const _IdentityForm({required this.progress});

  final OnboardingProgress progress;

  @override
  ConsumerState<_IdentityForm> createState() => _IdentityFormState();
}

class _IdentityFormState extends ConsumerState<_IdentityForm> {
  final _form = GlobalKey<FormState>();
  final _number = TextEditingController();
  late String? _type = widget.progress.identification?.idTypeCode;
  DateTime? _expiry;
  String? _expiryError;

  @override
  void dispose() {
    _number.dispose();
    super.dispose();
  }

  Future<void> _submit(List<IdType> types) async {
    final type = types.where((candidate) => candidate.code == _type).firstOrNull;
    final valid = _form.currentState?.validate() ?? false;
    setState(() => _expiryError = type != null && type.requiresExpiry && _expiry == null ? 'Choose the expiry date' : null);
    if (!valid || type == null || _expiryError != null) {
      return;
    }
    await _save(context, ref, (api) => api.saveIdentification({
          'idTypeCode': type.code,
          'idNumber': _number.text.trim(),
          'issuingCountry': ?(countryOf(ref.read(brandingProvider).requireValue).isEmpty ? null : countryOf(ref.read(brandingProvider).requireValue)),
          'expiryDate': ?(_expiry == null ? null : isoDate(_expiry!)),
        }));
  }

  @override
  Widget build(BuildContext context) {
    final types = ref.watch(idTypesProvider);
    final saved = widget.progress.identification;
    final today = DateTime.now();
    return AsyncBody(
      value: types,
      onRetry: () => ref.invalidate(idTypesProvider),
      builder: (types) {
        final type = types.where((candidate) => candidate.code == _type).firstOrNull;
        return _StageScaffold(
          title: 'Identity document',
          intro: 'The number of your identity document, then photos of it.',
          formKey: _form,
          children: [
            if (saved != null) ...[
              ListTile(
                contentPadding: EdgeInsets.zero,
                leading: const Icon(Icons.badge_outlined),
                title: Text(types.where((candidate) => candidate.code == saved.idTypeCode).firstOrNull?.name ?? saved.idTypeCode),
                subtitle: Text('${saved.idNumberMasked}${saved.expiryDate == null ? '' : ' · expires ${formatDate(saved.expiryDate!)}'}'),
              ),
              const Text('Enter it again below only to replace it.'),
              _gap(),
            ],
            _choice('Document', _type, {for (final type in types) type.code: type.name}, (value) => setState(() => _type = value)),
            _gap(),
            TextFormField(
              controller: _number,
              textCapitalization: TextCapitalization.characters,
              decoration: InputDecoration(labelText: 'Document number', helperText: type?.formatHint),
              validator: (value) => Validators.required(value, 'Enter the document number'),
            ),
            if (type?.requiresExpiry ?? false) ...[
              _gap(),
              _DateField(
                label: 'Expiry date',
                value: _expiry,
                error: _expiryError,
                first: today,
                last: DateTime(today.year + 20),
                onChanged: (date) => setState(() => _expiry = date),
              ),
            ],
            const SizedBox(height: Gaps.lg),
            PrimaryButton(label: saved == null ? 'Save' : 'Replace', onPressed: () => _submit(types)),
            const SizedBox(height: Gaps.xl),
            _DocumentUploads(
              progress: widget.progress,
              documents: const [
                (type: 'ID_FRONT', label: 'Front of the document', selfie: false, required: true),
                (type: 'ID_BACK', label: 'Back of the document', selfie: false, required: false),
              ],
            ),
          ],
        );
      },
    );
  }
}

// ---------------------------------------------------------------------------------------------- documents

typedef _DocumentSlot = ({String type, String label, bool selfie, bool required});

class _DocumentsStage extends StatelessWidget {
  const _DocumentsStage({required this.title, required this.intro, required this.documents, required this.progress});

  final String title;
  final String intro;
  final List<_DocumentSlot> documents;
  final OnboardingProgress progress;

  @override
  Widget build(BuildContext context) => _StageScaffold(
        title: title,
        intro: intro,
        children: [
          _DocumentUploads(progress: progress, documents: documents),
          const SizedBox(height: Gaps.lg),
          PrimaryButton(label: 'Done', onPressed: () async => Navigator.of(context).maybePop()),
        ],
      );
}

/// Photo slots: each shows whether a photo is uploaded and how the reviewer judged it, and takes or picks one.
class _DocumentUploads extends ConsumerStatefulWidget {
  const _DocumentUploads({required this.progress, required this.documents});

  final OnboardingProgress progress;
  final List<_DocumentSlot> documents;

  @override
  ConsumerState<_DocumentUploads> createState() => _DocumentUploadsState();
}

class _DocumentUploadsState extends ConsumerState<_DocumentUploads> {
  String? _uploading;

  Future<void> _upload(_DocumentSlot slot, {required bool camera}) async {
    final picked = await ref.read(documentPickerProvider)(camera: camera, selfie: slot.selfie);
    if (picked == null) {
      return;
    }
    setState(() => _uploading = slot.type);
    try {
      await ref.read(customerApiProvider).uploadDocument(documentType: slot.type, bytes: picked.bytes, fileName: picked.fileName);
      ref.invalidate(onboardingProvider);
    } on ApiException catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(error.message)));
      }
    } finally {
      if (mounted) {
        setState(() => _uploading = null);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final progress = ref.watch(onboardingProvider).value ?? widget.progress;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        for (final slot in widget.documents) ...[
          Card(
            child: Padding(
              padding: const EdgeInsets.all(Gaps.md),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('${slot.label}${slot.required ? '' : ' (optional)'}', style: Theme.of(context).textTheme.titleSmall),
                  const SizedBox(height: Gaps.xs),
                  Text(switch (progress.document(slot.type)?.reviewStatus) {
                    null => 'No photo yet',
                    'ACCEPTED' => 'Uploaded and accepted',
                    'REJECTED' => 'Not accepted: please take a new photo',
                    _ => 'Uploaded',
                  }),
                  const SizedBox(height: Gaps.sm),
                  if (_uploading == slot.type)
                    const LinearProgressIndicator()
                  else
                    Wrap(
                      spacing: Gaps.sm,
                      children: [
                        OutlinedButton.icon(
                          onPressed: _uploading == null ? () => _upload(slot, camera: true) : null,
                          icon: const Icon(Icons.photo_camera_outlined),
                          label: Text('Take photo of ${slot.label.toLowerCase()}'),
                        ),
                        TextButton(
                          onPressed: _uploading == null ? () => _upload(slot, camera: false) : null,
                          child: const Text('Choose from gallery'),
                        ),
                      ],
                    ),
                ],
              ),
            ),
          ),
          const SizedBox(height: Gaps.sm),
        ],
      ],
    );
  }
}

// ------------------------------------------------------------------------------------------------ address

class _AddressForm extends ConsumerStatefulWidget {
  const _AddressForm({required this.progress});

  final OnboardingProgress progress;

  @override
  ConsumerState<_AddressForm> createState() => _AddressFormState();
}

class _AddressFormState extends ConsumerState<_AddressForm> {
  final _form = GlobalKey<FormState>();
  late final OnboardingAddress? _address = widget.progress.address;
  late final _line1 = TextEditingController(text: _address?.line1);
  late final _line2 = TextEditingController(text: _address?.line2);
  late final _city = TextEditingController(text: _address?.city);
  late final _district = TextEditingController(text: _address?.district);
  late final _region = TextEditingController(text: _address?.region);
  late final _digitalAddress = TextEditingController(text: _address?.digitalAddress);
  late final _landmark = TextEditingController(text: _address?.landmark);

  @override
  void dispose() {
    for (final controller in [_line1, _line2, _city, _district, _region, _digitalAddress, _landmark]) {
      controller.dispose();
    }
    super.dispose();
  }

  Future<void> _submit() async {
    if (!(_form.currentState?.validate() ?? false)) {
      return;
    }
    await _save(context, ref, (api) => api.saveAddress({
          'line1': _line1.text.trim(),
          'line2': ?_optional(_line2),
          'city': ?_optional(_city),
          'district': ?_optional(_district),
          'region': ?_optional(_region),
          'digitalAddress': ?_optional(_digitalAddress)?.toUpperCase(),
          'landmark': ?_optional(_landmark),
        }));
  }

  @override
  Widget build(BuildContext context) {
    final proofNeeded = widget.progress.requirements.any((requirement) => requirement.code == 'PROOF_OF_ADDRESS');
    return _StageScaffold(
      title: 'Address',
      intro: 'Where you live now.',
      formKey: _form,
      children: [
        TextFormField(controller: _line1, decoration: const InputDecoration(labelText: 'House number and street'), validator: (value) => Validators.required(value, 'Enter your address')),
        _gap(),
        TextFormField(controller: _line2, decoration: const InputDecoration(labelText: 'Area (optional)')),
        _gap(),
        TextFormField(controller: _city, decoration: const InputDecoration(labelText: 'Town or city')),
        _gap(),
        TextFormField(controller: _district, decoration: const InputDecoration(labelText: 'District (optional)')),
        _gap(),
        TextFormField(controller: _region, decoration: const InputDecoration(labelText: 'Region (optional)')),
        _gap(),
        TextFormField(
          controller: _digitalAddress,
          textCapitalization: TextCapitalization.characters,
          decoration: const InputDecoration(labelText: 'Digital address (optional, e.g. GA-123-4567)'),
          validator: (value) => value == null || value.trim().isEmpty || RegExp(r'^[A-Za-z0-9-]{4,20}$').hasMatch(value.trim()) ? null : 'Use letters, digits and dashes, e.g. GA-123-4567',
        ),
        _gap(),
        TextFormField(controller: _landmark, decoration: const InputDecoration(labelText: 'Nearby landmark (optional)')),
        const SizedBox(height: Gaps.lg),
        PrimaryButton(label: 'Save', onPressed: _submit),
        if (proofNeeded) ...[
          const SizedBox(height: Gaps.xl),
          const Text('A recent utility bill or tenancy agreement showing this address.'),
          const SizedBox(height: Gaps.sm),
          _DocumentUploads(
            progress: widget.progress,
            documents: const [(type: 'PROOF_OF_ADDRESS', label: 'Proof of address', selfie: false, required: true)],
          ),
        ],
      ],
    );
  }
}

// -------------------------------------------------------------------------------------------- next of kin

class _NextOfKinForm extends ConsumerStatefulWidget {
  const _NextOfKinForm({required this.progress});

  final OnboardingProgress progress;

  @override
  ConsumerState<_NextOfKinForm> createState() => _NextOfKinFormState();
}

class _NextOfKinFormState extends ConsumerState<_NextOfKinForm> {
  final _form = GlobalKey<FormState>();
  late final OnboardingNextOfKin? _kin = widget.progress.nextOfKin;
  late final _name = TextEditingController(text: _kin?.fullName);
  late final _relationship = TextEditingController(text: _kin?.relationship);
  late final _phone = TextEditingController(text: _kin?.phone);
  late final _email = TextEditingController(text: _kin?.email);
  late final _address = TextEditingController(text: _kin?.address);

  @override
  void dispose() {
    for (final controller in [_name, _relationship, _phone, _email, _address]) {
      controller.dispose();
    }
    super.dispose();
  }

  Future<void> _submit() async {
    if (!(_form.currentState?.validate() ?? false)) {
      return;
    }
    await _save(context, ref, (api) => api.saveNextOfKin({
          'fullName': _name.text.trim(),
          'relationship': _relationship.text.trim(),
          'phone': ?_optional(_phone)?.replaceAll(RegExp(r'[\s-]'), ''),
          'email': ?_optional(_email),
          'address': ?_optional(_address),
        }));
  }

  @override
  Widget build(BuildContext context) => _StageScaffold(
        title: 'Next of kin',
        intro: 'Someone close to you we can contact if we cannot reach you.',
        formKey: _form,
        onSave: _submit,
        children: [
          TextFormField(controller: _name, textCapitalization: TextCapitalization.words, decoration: const InputDecoration(labelText: 'Full name'), validator: (value) => Validators.required(value, 'Enter their name')),
          _gap(),
          TextFormField(controller: _relationship, textCapitalization: TextCapitalization.sentences, decoration: const InputDecoration(labelText: 'Relationship (e.g. sister)'), validator: (value) => Validators.required(value, 'Say how you are related')),
          _gap(),
          TextFormField(
            controller: _phone,
            keyboardType: TextInputType.phone,
            decoration: const InputDecoration(labelText: 'Phone number'),
            validator: (value) => value == null || value.trim().isEmpty ? null : Validators.phoneNumber(value),
          ),
          _gap(),
          TextFormField(controller: _email, keyboardType: TextInputType.emailAddress, decoration: const InputDecoration(labelText: 'Email (optional)')),
          _gap(),
          TextFormField(controller: _address, decoration: const InputDecoration(labelText: 'Address (optional)')),
        ],
      );
}

// ------------------------------------------------------------------------------------------- risk profile

class _RiskProfileForm extends ConsumerStatefulWidget {
  const _RiskProfileForm({required this.progress});

  final OnboardingProgress progress;

  @override
  ConsumerState<_RiskProfileForm> createState() => _RiskProfileFormState();
}

class _RiskProfileFormState extends ConsumerState<_RiskProfileForm> {
  final _form = GlobalKey<FormState>();
  late String? _source = widget.progress.riskProfile?.sourceOfFunds;
  late String? _purpose = widget.progress.riskProfile?.accountPurpose;
  late String? _turnover = widget.progress.riskProfile?.expectedMonthlyTurnover;
  late bool? _exposed = widget.progress.riskProfile?.politicallyExposed;
  String? _exposedError;

  Future<void> _submit() async {
    final valid = _form.currentState?.validate() ?? false;
    setState(() => _exposedError = _exposed == null ? 'Answer this question' : null);
    if (!valid || _exposed == null) {
      return;
    }
    await _save(
      context,
      ref,
      (api) => api.saveRiskProfile(RiskProfile(sourceOfFunds: _source!, accountPurpose: _purpose!, expectedMonthlyTurnover: _turnover!, politicallyExposed: _exposed!)),
    );
  }

  @override
  Widget build(BuildContext context) => _StageScaffold(
        title: 'About your account',
        intro: 'The law asks us to understand how you will use your account. Your answers stay private.',
        formKey: _form,
        onSave: _submit,
        children: [
          _choice(
            'Where your money comes from',
            _source,
            const {
              'SALARY': 'Salary',
              'BUSINESS': 'My business',
              'TRADING': 'Trading',
              'FARMING': 'Farming',
              'REMITTANCES': 'Money from abroad',
              'PENSION': 'Pension',
              'FAMILY_SUPPORT': 'Family support',
              'SAVINGS': 'My savings',
              'OTHER': 'Other',
            },
            (value) => setState(() => _source = value),
          ),
          _gap(),
          _choice(
            'What the account is for',
            _purpose,
            const {
              'SAVINGS': 'Saving',
              'RECEIVING_SALARY': 'Receiving my salary',
              'BUSINESS_PAYMENTS': 'Business payments',
              'SUSU': 'Susu',
              'LOANS': 'Loans',
              'REMITTANCES': 'Receiving money from abroad',
              'OTHER': 'Other',
            },
            (value) => setState(() => _purpose = value),
          ),
          _gap(),
          _choice(
            'How much will go through it each month',
            _turnover,
            const {
              'UP_TO_1000': 'Up to 1,000',
              'UP_TO_5000': 'Up to 5,000',
              'UP_TO_20000': 'Up to 20,000',
              'UP_TO_100000': 'Up to 100,000',
              'ABOVE_100000': 'More than 100,000',
            },
            (value) => setState(() => _turnover = value),
          ),
          _gap(),
          Text('Do you hold, or have you recently held, a prominent public position (for example a minister, judge, '
              'senior military officer or chief), or are you family or a close associate of someone who does?'),
          RadioGroup<bool>(
            groupValue: _exposed,
            onChanged: (value) => setState(() {
              _exposed = value;
              _exposedError = null;
            }),
            child: const Column(
              children: [
                RadioListTile<bool>(value: false, title: Text('No')),
                RadioListTile<bool>(value: true, title: Text('Yes')),
              ],
            ),
          ),
          if (_exposedError != null) Text(_exposedError!, style: TextStyle(color: Theme.of(context).colorScheme.error)),
        ],
      );
}
