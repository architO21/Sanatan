const express = require('express');
const router = express.Router();
const spillover = require('../services/spillover');

// POST /api/study/assign - Assign a new study topic
router.post('/assign', (req, res) => {
  const db = req.db;
  const { topic, description, target_duration_minutes, date } = req.body;
  if (!topic || !topic.trim()) {
    return res.status(400).json({ error: 'topic is required' });
  }

  const assigned = spillover.assignTopic(db, {
    topic: topic.trim(),
    description: description || null,
    targetDurationMinutes: target_duration_minutes,
    date
  });

  res.status(201).json(assigned);
});

// PUT /api/study/:id/complete - Mark a topic complete/incomplete
router.put('/:id/complete', (req, res) => {
  const db = req.db;
  const { status, actual_duration_minutes } = req.body;
  try {
    const updated = spillover.setTopicStatus(
      db,
      parseInt(req.params.id),
      status || 'completed',
      actual_duration_minutes
    );
    res.json(updated);
  } catch (err) {
    res.status(err.status || 400).json({ error: err.message });
  }
});

// PUT /api/study/:id/status - Update status (pending/in_progress/completed/incomplete)
router.put('/:id/status', (req, res) => {
  const db = req.db;
  const { status, actual_duration_minutes } = req.body;
  try {
    const updated = spillover.setTopicStatus(
      db,
      parseInt(req.params.id),
      status,
      actual_duration_minutes
    );
    res.json(updated);
  } catch (err) {
    res.status(err.status || 400).json({ error: err.message });
  }
});

// GET /api/study/today - Get today's topics including spillover
router.get('/today', (req, res) => {
  res.json(spillover.getTodayTopics(req.db));
});

// GET /api/study/topics - List topics for a date range
router.get('/topics', (req, res) => {
  const db = req.db;
  const start = req.query.start || spillover.addDays(spillover.today(), -6);
  const end = req.query.end || spillover.today();

  const topics = db.prepare(
    `SELECT * FROM study_topics WHERE assigned_date BETWEEN ? AND ? ORDER BY assigned_date DESC, id ASC`
  ).all(start, end);

  res.json(topics);
});

// GET /api/study/spillover - Get pending spillover items
router.get('/spillover', (req, res) => {
  res.json(spillover.getSpillover(req.db, req.query.date));
});

module.exports = router;