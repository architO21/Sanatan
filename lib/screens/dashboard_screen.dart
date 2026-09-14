import 'package:flutter/material.dart';
import '../services/api_service.dart';

class DashboardScreen extends StatefulWidget {
  final ApiService api;
  const DashboardScreen({super.key, required this.api});

  @override
  State<DashboardScreen> createState() => _DashboardScreenState();
}

class _DashboardScreenState extends State<DashboardScreen> {
  Map<String, dynamic>? _daily;
  List<dynamic>? _weekly;
  bool _loading = true;
  String? _error;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final daily = await widget.api.getDailyDashboard(null);
      final weekly = await widget.api.getWeeklyDashboard();
      if (mounted) {
        setState(() {
          _daily = daily;
          _weekly = weekly['days'] as List;
          _loading = false;
        });
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _error = e.toString();
          _loading = false;
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Dashboard'),
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh),
            onPressed: _load,
          ),
        ],
      ),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : _error != null
              ? Center(
                  child: Column(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      Text('Could not load dashboard.\n$_error',
                          textAlign: TextAlign.center),
                      const SizedBox(height: 12),
                      FilledButton(onPressed: _load, child: const Text('Retry')),
                    ],
                  ),
                )
              : RefreshIndicator(
                  onRefresh: _load,
                  child: ListView(
                    padding: const EdgeInsets.all(16),
                    children: [
                      _buildStatsRow(),
                      const SizedBox(height: 24),
                      if (_daily?['spillover'] is List &&
                          (_daily!['spillover'] as List).isNotEmpty)
                        _buildSpilloverCard(),
                      const SizedBox(height: 24),
                      _buildWeeklyChart(),
                    ],
                  ),
                ),
    );
  }

  Widget _buildStatsRow() {
    final cals = _daily?['total_calories'] ?? 0;
    final calGoal = _daily?['calorie_goal'] ?? 0;
    final steps = _daily?['total_steps'] ?? 0;
    final stepGoal = _daily?['steps_goal'] ?? 0;
    final study = _daily?['study_minutes'] ?? 0;
    final studyGoal = _daily?['study_goal'] ?? 0;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          (_daily?['date'] ?? '').toString(),
          style: Theme.of(context).textTheme.titleMedium,
        ),
        const SizedBox(height: 12),
        Row(
          children: [
            Expanded(
              child: _MetricCard(
                icon: Icons.restaurant,
                label: 'Calories',
                value: '$cals',
                goal: calGoal > 0 ? 'goal $calGoal' : null,
                color: Colors.orange,
              ),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: _MetricCard(
                icon: Icons.directions_walk,
                label: 'Steps',
                value: steps > 0 ? '$steps' : '—',
                goal: stepGoal > 0 ? 'goal $stepGoal' : null,
                color: Colors.blue,
              ),
            ),
          ],
        ),
        const SizedBox(height: 12),
        Row(
          children: [
            Expanded(
              child: _MetricCard(
                icon: Icons.menu_book,
                label: 'Study',
                value: study > 0 ? '$study min' : '—',
                goal: studyGoal > 0 ? 'goal $studyGoal min' : null,
                color: Colors.green,
              ),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: _MetricCard(
                icon: _moodIcon(_daily?['mood']),
                label: 'Mood',
                value: (_daily?['mood'] ?? '—').toString().capitalize(),
                goal: _daily?['sleep_hours'] != null
                    ? 'sleep ${_daily!['sleep_hours']}h'
                    : null,
                color: Colors.purple,
              ),
            ),
          ],
        ),
      ],
    );
  }

  IconData _moodIcon(dynamic mood) {
    if (mood == null) return Icons.face;
    switch (mood.toString()) {
      case 'great':
        return Icons.sentiment_very_satisfied;
      case 'happy':
        return Icons.sentiment_satisfied;
      case 'good':
        return Icons.sentiment_satisfied;
      case 'tired':
        return Icons.sentiment_dissatisfied;
      case 'stressed':
        return Icons.sentiment_neutral;
      case 'sad':
        return Icons.sentiment_very_dissatisfied;
      default:
        return Icons.face;
    }
  }

  Widget _buildSpilloverCard() {
    final spillover = _daily!['spillover'] as List;
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Row(
              children: [
                Icon(Icons.event_repeat, color: Colors.deepOrange),
                SizedBox(width: 8),
                Text('Carried over from yesterday',
                    style: TextStyle(fontWeight: FontWeight.bold)),
              ],
            ),
            const SizedBox(height: 8),
            ...spillover.map(
              (item) => Padding(
                padding: const EdgeInsets.symmetric(vertical: 4),
                child: Text('• ${item['topic']}'),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildWeeklyChart() {
    if (_weekly == null || _weekly!.isEmpty) {
      return const Center(
        child: Text('No data yet. Journal some days to see trends.'),
      );
    }

    final maxVal = _weekly!.fold<double>(0, (max, d) {
      final vals = [
        (d['total_calories'] ?? 0).toDouble(),
        (d['total_steps'] ?? 0).toDouble(),
      ];
      final localMax = vals.reduce((a, b) => a > b ? a : b);
      return localMax > max ? localMax : max;
    });

    // Normalized bar chart for calories and steps per day
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text('Last 7 days',
            style: Theme.of(context).textTheme.titleMedium),
        const SizedBox(height: 12),
        SizedBox(
          height: 200,
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.end,
            children: List.generate(_weekly!.length, (i) {
              final day = _weekly![i];
              final calories = (day['total_calories'] ?? 0).toDouble();
              final steps = (day['total_steps'] ?? 0).toDouble();
              final max = maxVal <= 0 ? 1.0 : maxVal;

              return Expanded(
                child: Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 3),
                  child: Column(
                    mainAxisAlignment: MainAxisAlignment.end,
                    children: [
                      const SizedBox(height: 8),
                      Expanded(
                        child: Row(
                          crossAxisAlignment: CrossAxisAlignment.end,
                          mainAxisAlignment: MainAxisAlignment.center,
                          children: [
                            _Bar(
                              heightFactor: calories / max,
                              color: Colors.orange,
                              width: 10,
                            ),
                            const SizedBox(width: 3),
                            _Bar(
                              heightFactor: steps / max,
                              color: Colors.blue,
                              width: 10,
                            ),
                          ],
                        ),
                      ),
                      const SizedBox(height: 4),
                      Text(
                        _dayLabel((day['date'] ?? '').toString()),
                        style: const TextStyle(fontSize: 10),
                      ),
                    ],
                  ),
                ),
              );
            }),
          ),
        ),
        const SizedBox(height: 8),
        const Row(
          children: [
            _Legend(color: Colors.orange, label: 'Calories'),
            SizedBox(width: 12),
            _Legend(color: Colors.blue, label: 'Steps'),
          ],
        ),
      ],
    );
  }

  String _dayLabel(String date) {
    try {
      final parts = date.split('-');
      final d = DateTime(int.parse(parts[0]), int.parse(parts[1]), int.parse(parts[2]));
      const weekdays = ['M', 'T', 'W', 'T', 'F', 'S', 'S'];
      return weekdays[d.weekday - 1];
    } catch (_) {
      return '?';
    }
  }
}

class _MetricCard extends StatelessWidget {
  final IconData icon;
  final String label;
  final String value;
  final String? goal;
  final Color color;

  const _MetricCard({
    required this.icon,
    required this.label,
    required this.value,
    required this.goal,
    required this.color,
  });

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Icon(icon, color: color, size: 20),
                const SizedBox(width: 8),
                Text(label,
                    style: const TextStyle(
                        fontWeight: FontWeight.w600, fontSize: 13)),
              ],
            ),
            const SizedBox(height: 10),
            Text(value,
                style: Theme.of(context).textTheme.headlineSmall),
            if (goal != null)
              Text(goal!,
                  style: const TextStyle(color: Colors.grey, fontSize: 12)),
          ],
        ),
      ),
    );
  }
}

class _Bar extends StatelessWidget {
  final double heightFactor;
  final Color color;
  final double width;
  const _Bar({
    required this.heightFactor,
    required this.color,
    required this.width,
  });

  @override
  Widget build(BuildContext context) {
    final clamped = heightFactor.clamp(0.02, 1.0);
    return Container(
      width: width,
      height: MediaQuery.of(context).size.height * 0.12 * clamped,
      decoration: BoxDecoration(
        color: color,
        borderRadius: const BorderRadius.vertical(top: Radius.circular(3)),
      ),
    );
  }
}

class _Legend extends StatelessWidget {
  final Color color;
  final String label;
  const _Legend({required this.color, required this.label});

  @override
  Widget build(BuildContext context) {
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Container(width: 10, height: 10, color: color),
        const SizedBox(width: 4),
        Text(label, style: const TextStyle(fontSize: 12)),
      ],
    );
  }
}

extension StringCap on String {
  String capitalize() {
    if (isEmpty) return this;
    return this[0].toUpperCase() + substring(1);
  }
}