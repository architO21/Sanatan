import 'dart:convert';
import 'package:http/http.dart' as http;

/// Exception thrown when the backend rejects the session (HTTP 401).
class AuthException implements Exception {
  final String message;
  AuthException(this.message);
  @override
  String toString() => message;
}

class ApiService {
  // Point this to your backend. For Android emulator use 10.0.2.2,
  // for iOS simulator / web / desktop use localhost.
  static String baseUrl = 'http://localhost:3000';

  /// Session token; set after login/signup. Every request carries it.
  static String? token;
  static int? userId;
  static String? userEmail;

  Map<String, String> _headers({bool json = true}) {
    final h = <String, String>{};
    if (json) h['Content-Type'] = 'application/json';
    if (token != null) h['Authorization'] = 'Bearer $token';
    return h;
  }

  // ── Auth ────────────────────────────────────────────────────────────────
  Future<Map<String, dynamic>> signup(String email, String password) async {
    final res = await http.post(
      Uri.parse('$baseUrl/api/auth/signup'),
      headers: _headers(),
      body: jsonEncode({'email': email, 'password': password}),
    );
    return _handle(res);
  }

  Future<Map<String, dynamic>> login(String email, String password) async {
    final res = await http.post(
      Uri.parse('$baseUrl/api/auth/login'),
      headers: _headers(),
      body: jsonEncode({'email': email, 'password': password}),
    );
    return _handle(res);
  }

  Future<void> logout() async {
    try {
      await http.post(Uri.parse('$baseUrl/api/auth/logout'), headers: _headers());
    } catch (_) {}
    token = null;
    userId = null;
    userEmail = null;
  }

  Future<Map<String, dynamic>> me() async {
    final res = await http.get(
      Uri.parse('$baseUrl/api/auth/me'),
      headers: _headers(json: false),
    );
    return _handle(res);
  }

  /// Returns true if the backend responds with a healthy status.
  Future<bool> getHealth() async {
    try {
      final res = await http
          .get(Uri.parse('$baseUrl/health'))
          .timeout(const Duration(seconds: 3));
      return res.statusCode == 200;
    } catch (_) {
      return false;
    }
  }

  // ── Journal ─────────────────────────────────────────────────────────────
  Future<Map<String, dynamic>> createJournalEntry(
      String rawText, String date) async {
    final res = await http.post(
      Uri.parse('$baseUrl/api/journal'),
      headers: _headers(),
      body: jsonEncode({'raw_text': rawText, 'date': date}),
    );
    return _handle(res);
  }

  Future<Map<String, dynamic>> getJournalEntry(String date) async {
    final res = await http.get(
      Uri.parse('$baseUrl/api/journal/$date'),
      headers: _headers(json: false),
    );
    if (res.statusCode == 404) {
      return {'entry': null};
    }
    return _handle(res);
  }

  Future<Map<String, dynamic>> getJournalEntries() async {
    final res = await http.get(
      Uri.parse('$baseUrl/api/journal?limit=100'),
      headers: _headers(json: false),
    );
    return _handle(res);
  }

  // ── Dashboard ───────────────────────────────────────────────────────────
  Future<Map<String, dynamic>> getDailyDashboard(String? date) async {
    final uri = (date == null)
        ? Uri.parse('$baseUrl/api/dashboard/daily')
        : Uri.parse('$baseUrl/api/dashboard/daily?date=$date');
    final res = await http.get(uri, headers: _headers(json: false));
    return _handle(res);
  }

  Future<Map<String, dynamic>> getWeeklyDashboard() async {
    final res = await http.get(
      Uri.parse('$baseUrl/api/dashboard/weekly'),
      headers: _headers(json: false),
    );
    return _handle(res);
  }

  // ── Study ───────────────────────────────────────────────────────────────
  Future<Map<String, dynamic>> assignStudyTopic(
      String topic, String? description, int? targetMinutes) async {
    final res = await http.post(
      Uri.parse('$baseUrl/api/study/assign'),
      headers: _headers(),
      body: jsonEncode({
        'topic': topic,
        'description': description,
        'target_duration_minutes': targetMinutes,
      }),
    );
    return _handle(res);
  }

  Future<Map<String, dynamic>> updateStudyTopic(
      int id, String status, int? actualMinutes) async {
    final res = await http.put(
      Uri.parse('$baseUrl/api/study/$id/status'),
      headers: _headers(),
      body: jsonEncode({
        'status': status,
        'actual_duration_minutes': actualMinutes,
      }),
    );
    return _handle(res);
  }

  Future<Map<String, dynamic>> getTodayTopics() async {
    final res = await http.get(
      Uri.parse('$baseUrl/api/study/today'),
      headers: _headers(json: false),
    );
    return _handle(res);
  }

  Future<List<dynamic>> getStudyTopics({String? start, String? end}) async {
    final uri = Uri.parse('$baseUrl/api/study/topics')
        .replace(queryParameters: {
      'start': ?start,
      'end': ?end,
    });
    final res = await http.get(uri, headers: _headers(json: false));
    return _handle(res) as List;
  }

  // ── Goals ───────────────────────────────────────────────────────────────
  Future<List<dynamic>> getGoals() async {
    final res = await http.get(
      Uri.parse('$baseUrl/api/goals'),
      headers: _headers(json: false),
    );
    return _handle(res) as List;
  }

  Future<Map<String, dynamic>> createGoal(
      String category, int targetValue, String? unit) async {
    final res = await http.post(
      Uri.parse('$baseUrl/api/goals'),
      headers: _headers(),
      body: jsonEncode({
        'category': category,
        'target_value': targetValue,
        'unit': unit,
      }),
    );
    return _handle(res);
  }

  // ── Expense categories ──────────────────────────────────────────────────
  Future<List<dynamic>> getCategories() async {
    final res = await http.get(
      Uri.parse('$baseUrl/api/categories'),
      headers: _headers(json: false),
    );
    return _handle(res) as List;
  }

  Future<Map<String, dynamic>> createCategory(
      String name, String keywords, String color) async {
    final res = await http.post(
      Uri.parse('$baseUrl/api/categories'),
      headers: _headers(),
      body: jsonEncode({
        'name': name,
        'keywords': keywords,
        'color': color,
      }),
    );
    return _handle(res);
  }

  Future<void> deleteCategory(int id) async {
    final res = await http.delete(
      Uri.parse('$baseUrl/api/categories/$id'),
      headers: _headers(json: false),
    );
    if (res.statusCode >= 300) _handle(res);
  }

  // ── Expenses ────────────────────────────────────────────────────────────
  Future<List<dynamic>> getExpenses({String? date}) async {
    final uri = Uri.parse('$baseUrl/api/expenses').replace(queryParameters: {
      'date': ?date,
    });
    final res = await http.get(uri, headers: _headers(json: false));
    return _handle(res) as List;
  }

  Future<Map<String, dynamic>> addExpense({
    required String description,
    required double amount,
    String? date,
    int? categoryId,
    String? customCategory,
  }) async {
    final res = await http.post(
      Uri.parse('$baseUrl/api/expenses'),
      headers: _headers(),
      body: jsonEncode({
        'description': description,
        'amount': amount,
        'date': ?date,
        'category_id': ?categoryId,
        'custom_category': ?customCategory,
      }),
    );
    return _handle(res);
  }

  Future<void> deleteExpense(int id) async {
    final res = await http.delete(
      Uri.parse('$baseUrl/api/expenses/$id'),
      headers: _headers(json: false),
    );
    if (res.statusCode >= 300) _handle(res);
  }

  Future<Map<String, dynamic>> getExpenseSummary({String? start, String? end}) async {
    final uri = Uri.parse('$baseUrl/api/expenses/summary')
        .replace(queryParameters: {
      'start': ?start,
      'end': ?end,
    });
    final res = await http.get(uri, headers: _headers(json: false));
    return _handle(res);
  }

  dynamic _handle(http.Response res) {
    if (res.statusCode == 401) {
      throw AuthException('Session expired. Please log in again.');
    }
    if (res.statusCode >= 200 && res.statusCode < 300) {
      if (res.body.isEmpty) return {};
      return jsonDecode(res.body);
    }
    String message = 'Request failed (${res.statusCode})';
    try {
      final body = jsonDecode(res.body);
      if (body is Map && body['error'] != null) {
        message = body['error'].toString();
      }
    } catch (_) {}
    throw Exception(message);
  }
}