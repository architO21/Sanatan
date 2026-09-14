// Study spillover logic
// Incomplete topics move to the next day's queue.
// Every db-using function requires the caller's user database (`db`),
// so all queries stay within one user's data.

/**
 * Get today's date as YYYY-MM-DD (local time).
 */
function today() {
  const d = new Date();
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

/**
 * Add days to a date string (YYYY-MM-DD).
 */
function addDays(dateStr, days) {
  const [y, m, d] = dateStr.split('-').map(Number);
  const date = new Date(y, m - 1, d + days);
  const yy = date.getFullYear();
  const mm = String(date.getMonth() + 1).padStart(2, '0');
  const dd = String(date.getDate()).padStart(2, '0');
  return `${yy}-${mm}-${dd}`;
}

/**
 * Get all topics assigned for a given date.
 */
function getTopicsForDate(db, dateStr) {
  return db.prepare(
    'SELECT *, spillover_from FROM study_topics WHERE assigned_date = ?'
  ).all(dateStr);
}

/**
 * Get pending (uncompleted) topics for today, including spillover.
 * Marks topics as spilled over (copies them to today's queue).
 */
function getTodayTopics(db) {
  const todayStr = today();
  const todayTopics = getTopicsForDate(db, todayStr);

  // Find incomplete topics from yesterday that haven't been spilled yet
  const yesterday = addDays(todayStr, -1);
  const yesterdayTopics = db.prepare(
    `SELECT * FROM study_topics
     WHERE assigned_date = ?
       AND status != 'completed'
       AND id NOT IN (
         SELECT spillover_from FROM study_topics WHERE spillover_from IS NOT NULL
       )`
  ).all(yesterday);

  // Copy incomplete yesterday topics into today as "spillover"
  const spillover = [];
  for (const topic of yesterdayTopics) {
    const existing = db.prepare(
      'SELECT * FROM study_topics WHERE spillover_from = ?'
    ).get(topic.id);
    if (existing) continue; // already spilled

    const info = db.prepare(
      `INSERT INTO study_topics (assigned_date, topic, description, target_duration_minutes, status, spillover_from)
       VALUES (?, ?, ?, ?, 'pending', ?)`
    ).run(todayStr, topic.topic, topic.description, topic.target_duration_minutes, topic.id);

    spillover.push({
      id: info.lastInsertRowid,
      topic: topic.topic,
      description: topic.description,
      spilled_over: true,
      original_id: topic.id
    });
  }

  return {
    date: todayStr,
    topics: todayTopics,
    spillover: spillover
  };
}

/**
 * Assign a new study topic for a given date (defaults to today).
 */
function assignTopic(db, { topic, description, targetDurationMinutes, date }) {
  const assignedDate = date || today();
  const duration = targetDurationMinutes || 30;

  const info = db.prepare(
    `INSERT INTO study_topics (assigned_date, topic, description, target_duration_minutes, status)
     VALUES (?, ?, ?, ?, 'pending')`
  ).run(assignedDate, topic, description || null, duration);

  return db.prepare('SELECT * FROM study_topics WHERE id = ?').get(info.lastInsertRowid);
}

/**
 * Mark a study topic as completed (or incomplete).
 */
function setTopicStatus(db, id, status, actualMinutes = null) {
  const validStatuses = ['pending', 'completed', 'incomplete'];
  if (!validStatuses.includes(status)) {
    throw new Error(`Invalid status: ${status}`);
  }

  const topic = db.prepare('SELECT * FROM study_topics WHERE id = ?').get(id);
  if (!topic) {
    const err = new Error(`Topic ${id} not found`);
    err.status = 404;
    throw err;
  }

  const completedAt = status === 'completed' ? new Date().toISOString() : null;

  if (actualMinutes !== null) {
    db.prepare(
      `UPDATE study_topics
       SET status = ?, completed_at = ?, actual_duration_minutes = ?,
           updated_at = datetime('now')
       WHERE id = ?`
    ).run(status, completedAt, actualMinutes, id);
  } else {
    db.prepare(
      `UPDATE study_topics
       SET status = ?, completed_at = ?,
           updated_at = datetime('now')
       WHERE id = ?`
    ).run(status, completedAt, id);
  }

  return db.prepare('SELECT * FROM study_topics WHERE id = ?').get(id);
}

/**
 * Get all spillover items for a date.
 */
function getSpillover(db, dateStr) {
  const date = dateStr || today();
  return db.prepare(
    'SELECT * FROM study_topics WHERE spillover_from IS NOT NULL AND assigned_date = ?'
  ).all(date);
}

module.exports = {
  today,
  addDays,
  getTopicsForDate,
  getTodayTopics,
  assignTopic,
  setTopicStatus,
  getSpillover
};