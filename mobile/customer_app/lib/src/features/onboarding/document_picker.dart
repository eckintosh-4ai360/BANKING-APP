import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:image_picker/image_picker.dart';

/// A photo taken or chosen for signing up (identity document, selfie, signature, proof of address).
class PickedDocument {
  const PickedDocument({required this.bytes, required this.fileName});

  final List<int> bytes;
  final String fileName;
}

/// Takes a photo with the camera (the front camera for a selfie) or picks one from the gallery; null when the
/// customer cancels. Replaced in tests.
typedef DocumentPicker = Future<PickedDocument?> Function({required bool camera, required bool selfie});

final documentPickerProvider = Provider<DocumentPicker>((ref) => _pickImage);

Future<PickedDocument?> _pickImage({required bool camera, required bool selfie}) async {
  // Kept small enough to upload over a mobile connection and legible enough for a reviewer.
  final file = await ImagePicker().pickImage(
    source: camera ? ImageSource.camera : ImageSource.gallery,
    preferredCameraDevice: selfie ? CameraDevice.front : CameraDevice.rear,
    maxWidth: 2000,
    maxHeight: 2000,
    imageQuality: 85,
  );
  if (file == null) {
    return null;
  }
  return PickedDocument(bytes: await file.readAsBytes(), fileName: file.name);
}
