import 'package:shared_preferences/shared_preferences.dart';
import 'api_service.dart';

/// Persists the session token/email across app restarts (SharedPreferences
/// works on web + Android + iOS) and applies them to [ApiService].
class AuthService {
  static const _kToken = 'auth_token';
  static const _kUserId = 'auth_user_id';
  static const _kEmail = 'auth_email';

  bool get isLoggedIn => ApiService.token != null;

  Future<void> init() async {
    final prefs = await SharedPreferences.getInstance();
    ApiService.token = prefs.getString(_kToken);
    ApiService.userId = prefs.getInt(_kUserId);
    ApiService.userEmail = prefs.getString(_kEmail);
  }

  Future<void> saveSession(Map<String, dynamic> data) async {
    final token = data['token'] as String;
    final user = data['user'] as Map<String, dynamic>;
    ApiService.token = token;
    ApiService.userId = user['id'] as int;
    ApiService.userEmail = user['email'] as String?;

    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_kToken, token);
    await prefs.setInt(_kUserId, ApiService.userId!);
    if (user['email'] != null) await prefs.setString(_kEmail, user['email'].toString());
  }

  Future<void> clearSession() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.remove(_kToken);
    await prefs.remove(_kUserId);
    await prefs.remove(_kEmail);
    ApiService.token = null;
    ApiService.userId = null;
    ApiService.userEmail = null;
  }
}