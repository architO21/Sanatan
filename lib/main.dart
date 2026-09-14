import 'package:flutter/material.dart';
import 'screens/login_screen.dart';
import 'screens/journal_screen.dart';
import 'screens/dashboard_screen.dart';
import 'screens/study_screen.dart';
import 'screens/goals_screen.dart';
import 'screens/expenses_screen.dart';
import 'services/api_service.dart';
import 'services/auth_service.dart';
import 'services/sync_service.dart';
import 'widgets/sync_banner.dart';

void main() {
  runApp(const LifeJournalApp());
}

class LifeJournalApp extends StatefulWidget {
  const LifeJournalApp({super.key});

  @override
  State<LifeJournalApp> createState() => _LifeJournalAppState();
}

class _LifeJournalAppState extends State<LifeJournalApp> {
  final _auth = AuthService();
  final _api = ApiService();
  final _sync = SyncService(ApiService());

  bool _ready = false;
  bool _loggedIn = false;

  @override
  void initState() {
    super.initState();
    _bootstrap();
  }

  Future<void> _bootstrap() async {
    await _auth.init();
    if (!mounted) return;
    setState(() {
      _loggedIn = _auth.isLoggedIn;
      _ready = true;
    });
    if (_loggedIn) await _sync.init();
  }

  Future<void> _onLoggedIn() async {
    setState(() => _loggedIn = true);
    await _sync.init();
  }

  Future<void> _onLogout() async {
    await _api.logout();
    await _auth.clearSession();
    if (mounted) setState(() => _loggedIn = false);
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Life Journal',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: Colors.indigo),
        useMaterial3: true,
      ),
      home: !_ready
          ? const Scaffold(body: Center(child: CircularProgressIndicator()))
          : _loggedIn
              ? HomeShell(api: _api, sync: _sync, onLogout: _onLogout)
              : LoginScreen(auth: _auth, api: _api, onLoggedIn: _onLoggedIn),
    );
  }
}

class HomeShell extends StatefulWidget {
  final ApiService api;
  final SyncService sync;
  final VoidCallback onLogout;
  const HomeShell({
    super.key,
    required this.api,
    required this.sync,
    required this.onLogout,
  });

  @override
  State<HomeShell> createState() => _HomeShellState();
}

class _HomeShellState extends State<HomeShell> {
  int _index = 0;

  late final List<Widget> _screens = [
    JournalScreen(api: widget.api, sync: widget.sync),
    DashboardScreen(api: widget.api),
    ExpensesScreen(api: widget.api),
    StudyScreen(api: widget.api),
    GoalsScreen(api: widget.api),
  ];

  @override
  void initState() {
    super.initState();
    widget.sync.addListener(_onSyncChange);
  }

  @override
  void dispose() {
    widget.sync.removeListener(_onSyncChange);
    super.dispose();
  }

  void _onSyncChange() {
    if (mounted) setState(() {});
  }

  void _showSentSummary() {
    final sent = widget.sync.lastSentEntries;
    if (sent.isEmpty) return;

    final lines = <Widget>[];
    for (final item in sent) {
      final date = item['date'];
      final extracted = item['extracted_data'] as Map<String, dynamic>?;
      final calories = extracted?['total_calories'] ?? 0;
      final steps = extracted?['total_steps'] ?? 0;
      final study = extracted?['study'];
      final minutes = study != null
          ? (study['duration_minutes'] ?? 0) as dynamic
          : 0;
      final expenses = extracted?['expenses'];
      final expenseCount = expenses is List ? expenses.length : 0;
      final items = (extracted?['calories'] as List? ?? [])
          .whereType<Map>()
          .map((c) => '· ${c['amount_text'] ?? c['description']} '
              '(${c['calories']} cal)')
          .toList();
      lines.add(
        Padding(
          padding: const EdgeInsets.only(bottom: 8),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                '$date  ·  $calories cal · $steps steps · '
                '$minutes study min · $expenseCount expense${expenseCount == 1 ? '' : 's'}',
                style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w600),
              ),
              if (items.isNotEmpty)
                ...items.map(
                  (m) => Padding(
                    padding: const EdgeInsets.only(left: 12),
                    child: Text(m, style: const TextStyle(fontSize: 12)),
                  ),
                ),
            ],
          ),
        ),
      );
    }

    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Day sent!'),
        content: SingleChildScrollView(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisSize: MainAxisSize.min,
            children: [
              Text('${sent.length} entr${sent.length == 1 ? 'y' : 'ies'} '
                  'synced to your journal.'),
              const SizedBox(height: 12),
              ...lines,
            ],
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('OK'),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: SafeArea(
        child: Column(
          children: [
            SyncBanner(sync: widget.sync, onSent: _showSentSummary),
            Expanded(
              child: IndexedStack(index: _index, children: _screens),
            ),
          ],
        ),
      ),
      appBar: AppBar(
        title: const Text('Life Journal'),
        actions: [
          IconButton(
            tooltip: 'Sign out',
            icon: const Icon(Icons.logout),
            onPressed: widget.onLogout,
          ),
        ],
      ),
      bottomNavigationBar: NavigationBar(
        selectedIndex: _index,
        onDestinationSelected: (i) => setState(() => _index = i),
        destinations: const [
          NavigationDestination(
            icon: Icon(Icons.edit_note),
            label: 'Journal',
          ),
          NavigationDestination(
            icon: Icon(Icons.dashboard_outlined),
            selectedIcon: Icon(Icons.dashboard),
            label: 'Dashboard',
          ),
          NavigationDestination(
            icon: Icon(Icons.receipt_long_outlined),
            selectedIcon: Icon(Icons.receipt_long),
            label: 'Expenses',
          ),
          NavigationDestination(
            icon: Icon(Icons.menu_book_outlined),
            selectedIcon: Icon(Icons.menu_book),
            label: 'Study',
          ),
          NavigationDestination(
            icon: Icon(Icons.flag_outlined),
            selectedIcon: Icon(Icons.flag),
            label: 'Goals',
          ),
        ],
      ),
    );
  }
}