const express = require('express');
const router = express.Router();
const { today, addDays } = require('../services/spillover');

// GET /api/dashboard/daily - Daily summary
router.get('/daily', (req, res) => {
  const db = req.db;
  const date = req.query.date || today();
  const entry = db.prepare('SELECT * FROM journal_entries WHERE date = ?').get(date);

  // Get goals
  const goals = db.prepare(
    'SELECT * FROM goals WHERE is_active = 1 ORDER BY category'
  ).all();

  let summary = {
    date,
    has_entry: false,
    total_calories: 0,
    calorie_goal: 0,
    total_steps: 0,
    steps_goal: 0,
    study_minutes: 0,
    study_goal: 0,
    mood: null,
    sleep_hours: null,
    spillover: []
  };

  if (entry) {
    const data = JSON.parse(entry.extracted_data);
    summary.has_entry = true;
    summary.total_calories = data.total_calories || 0;
    summary.total_steps = data.total_steps || 0;
    summary.study_minutes = data.study?.duration_minutes || 0;
    summary.mood = data.mood || null;
    summary.sleep_hours = data.sleep_hours || null;
  }

  // Map goals
  for (const goal of goals) {
    if (goal.category === 'calories') summary.calorie_goal = goal.target_value;
    if (goal.category === 'steps') summary.steps_goal = goal.target_value;
    if (goal.category === 'study') summary.study_goal = goal.target_value;
  }

  // Spillover items for today
  summary.spillover = db.prepare(
    `SELECT * FROM study_topics WHERE assigned_date = ? AND spillover_from IS NOT NULL`
  ).all(date);

  res.json(summary);
});

// GET /api/dashboard/weekly - Weekly trend summary
router.get('/weekly', (req, res) => {
  const db = req.db;
  const startDate = req.query.start || addDays(today(), -6);

  const entries = db.prepare(
    `SELECT date, extracted_data FROM journal_entries WHERE date >= ? ORDER BY date ASC`
  ).all(startDate);

  const days = [];
  for (let i = 0; i < 7; i++) {
    const date = addDays(startDate, i);
    const entry = entries.find(e => e.date === date);
    const data = entry ? JSON.parse(entry.extracted_data) : null;

    days.push({
      date: date,
      total_calories: data?.total_calories || 0,
      total_steps: data?.total_steps || 0,
      study_minutes: data?.study?.duration_minutes || 0,
      has_entry: !!entry
    });
  }

  // Averages
  const daysWithData = days.filter(d => d.has_entry);
  const avgCalories = daysWithData.length
    ? Math.round(daysWithData.reduce((s, d) => s + d.total_calories, 0) / daysWithData.length)
    : 0;
  const avgSteps = daysWithData.length
    ? Math.round(daysWithData.reduce((s, d) => s + d.total_steps, 0) / daysWithData.length)
    : 0;
  const avgStudy = daysWithData.length
    ? Math.round(daysWithData.reduce((s, d) => s + d.study_minutes, 0) / daysWithData.length)
    : 0;

  res.json({ days, averages: { avg_calories: avgCalories, avg_steps: avgSteps, avg_study_minutes: avgStudy } });
});

module.exports = router;