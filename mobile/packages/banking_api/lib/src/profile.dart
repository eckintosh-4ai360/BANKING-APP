import 'json.dart';

class RoleSummary {
  const RoleSummary({required this.id, required this.code, required this.name});

  factory RoleSummary.fromJson(JsonMap json) =>
      RoleSummary(id: readString(json, 'id'), code: readString(json, 'code'), name: readString(json, 'name'));

  final String id;
  final String code;
  final String name;
}

/// The signed-in staff member (`GET /api/v1/me`).
class StaffProfile {
  const StaffProfile({
    required this.id,
    required this.tenantCode,
    required this.institutionName,
    required this.username,
    required this.firstName,
    required this.lastName,
    required this.email,
    required this.homeBranchId,
    required this.allBranchesAccess,
    required this.roles,
    required this.permissions,
  });

  factory StaffProfile.fromJson(Object? data) {
    final json = asJsonMap(data, 'profile');
    return StaffProfile(
      id: readString(json, 'id'),
      tenantCode: readString(json, 'tenantCode'),
      institutionName: readString(json, 'institutionName'),
      username: readString(json, 'username'),
      firstName: readString(json, 'firstName'),
      lastName: readString(json, 'lastName'),
      email: readString(json, 'email'),
      homeBranchId: readString(json, 'homeBranchId'),
      allBranchesAccess: readBool(json, 'allBranchesAccess'),
      roles: [for (final role in readObjectList(json, 'roles')) RoleSummary.fromJson(role)],
      permissions: Set.unmodifiable(readStringList(json, 'permissions')),
    );
  }

  final String id;
  final String tenantCode;
  final String institutionName;
  final String username;
  final String firstName;
  final String lastName;
  final String email;
  final String homeBranchId;
  final bool allBranchesAccess;
  final List<RoleSummary> roles;
  final Set<String> permissions;

  String get displayName => '$firstName $lastName';

  /// UI hint only; the backend authorises every request itself.
  bool can(String permission) => permissions.contains(permission);
}
