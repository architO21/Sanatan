const { DatabaseSync } = require('node:sqlite');
const path = require('path');
const fs = require('fs');
const crypto = require('crypto');

const DATA_DIR = path.join(__dirname, '..', '..', 'data');
const USERS_DIR = path.join(DATA_DIR, 'users');

if (!fs.existsSync(DATA_DIR)) fs.mkdirSync(DATA_DIR, { recursive: true });
if (!fs.existsSync(USERS_DIR)) fs.mkdirSync(USERS_DIR, { recursive: true });

// ── Master DB: accounts + sessions ────────────────────────────────────────
const master = new DatabaseSync(path.join(DATA_DIR, 'app.db'));
master.exec('PRAGMA journal_mode = WAL');
master.exec(`
  CREATE TABLE IF NOT EXISTS users (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    email TEXT UNIQUE NOT NULL,
    password_salt TEXT NOT NULL,
    password_hash TEXT NOT NULL,
    created_at TEXT DEFAULT (datetime('now'))
  );

  CREATE TABLE IF NOT EXISTS sessions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash TEXT UNIQUE NOT NULL,
    created_at TEXT DEFAULT (datetime('now'))
  );
`);

// ── Per-user data DB schema ────────────────────────────────────────────────
const USER_SCHEMA = `
  CREATE TABLE IF NOT EXISTS journal_entries (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    date TEXT UNIQUE NOT NULL,
    raw_text TEXT NOT NULL,
    extracted_data TEXT,
    created_at TEXT DEFAULT (datetime('now')),
    updated_at TEXT DEFAULT (datetime('now'))
  );

  CREATE TABLE IF NOT EXISTS calories (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    entry_id INTEGER REFERENCES journal_entries(id) ON DELETE CASCADE,
    meal_type TEXT,
    description TEXT,
    calories INTEGER,
    created_at TEXT DEFAULT (datetime('now'))
  );

  CREATE TABLE IF NOT EXISTS activities (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    entry_id INTEGER REFERENCES journal_entries(id) ON DELETE CASCADE,
    activity_type TEXT,
    duration_minutes INTEGER,
    steps INTEGER,
    calories_burned INTEGER,
    created_at TEXT DEFAULT (datetime('now'))
  );

  CREATE TABLE IF NOT EXISTS study_topics (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    assigned_date TEXT NOT NULL,
    topic TEXT NOT NULL,
    description TEXT,
    target_duration_minutes INTEGER DEFAULT 30,
    actual_duration_minutes INTEGER DEFAULT 0,
    status TEXT DEFAULT 'pending',
    spillover_from INTEGER REFERENCES study_topics(id),
    completed_at TEXT,
    created_at TEXT DEFAULT (datetime('now')),
    updated_at TEXT DEFAULT (datetime('now'))
  );

  CREATE TABLE IF NOT EXISTS goals (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    category TEXT NOT NULL,
    target_value INTEGER NOT NULL,
    unit TEXT,
    is_active INTEGER DEFAULT 1,
    created_at TEXT DEFAULT (datetime('now'))
  );

  CREATE TABLE IF NOT EXISTS expense_categories (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT UNIQUE NOT NULL,
    keywords TEXT,
    color TEXT,
    created_at TEXT DEFAULT (datetime('now'))
  );

  CREATE TABLE IF NOT EXISTS expenses (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    entry_id INTEGER REFERENCES journal_entries(id) ON DELETE CASCADE,
    date TEXT NOT NULL,
    description TEXT NOT NULL,
    amount REAL NOT NULL,
    category_id INTEGER REFERENCES expense_categories(id),
    custom_category TEXT,
    created_at TEXT DEFAULT (datetime('now'))
  );
`;

const DEFAULT_CATEGORIES = [
  ['food', 'food,lunch,dinner,breakfast,snack,cafe,coffee,restaurant,eat,ate,meal,tea,zomato,swiggy', '#FF9800'],
  ['transport', 'taxi,uber,cab,fuel,petrol,gas,metro,bus,auto,travel,commute,parking', '#2196F3'],
  ['entertainment', 'movie,netflix,prime,concert,game,party,outing,fun,show,book', '#9C27B0'],
  ['shopping', 'clothes,shoes,amazon,flipkart,shopping,electronics,gadget', '#E91E63'],
  ['health', 'doctor,medicine,gym,medical,hospital,pharmacy,health', '#4CAF50'],
  ['bills', 'electricity,water,internet,phone,recharge,broadband,bill', '#607D8B'],
  ['bestfriendspends', 'bestie,bestfriend,bro,buddy,friend', '#FF5722'],
];

function seedCategories(db) {
  const count = db.prepare('SELECT COUNT(*) as count FROM expense_categories').get().count;
  if (count > 0) return;
  const insert = db.prepare(
    'INSERT INTO expense_categories (name, keywords, color) VALUES (?, ?, ?)'
  );
  for (const [name, keywords, color] of DEFAULT_CATEGORIES) {
    insert.run(name, keywords, color);
  }
}

// ── Per-user DB factory (cached) ───────────────────────────────────────────
const userDbs = new Map();

/**
 * Get (creating if needed) the SQLite database for a given user.
 * A user's DB file is `data/users/<userId>.db` — full data isolation.
 */
function getUserDb(userId) {
  const id = Number(userId);
  if (userDbs.has(id)) return userDbs.get(id);

  const file = path.join(USERS_DIR, `${id}.db`);
  const db = new DatabaseSync(file);
  db.exec('PRAGMA journal_mode = WAL');
  db.exec('PRAGMA foreign_keys = ON');
  db.exec(USER_SCHEMA);
  seedCategories(db);

  // Migration: add updated_at to study_topics if missing
  const cols = db.prepare('PRAGMA table_info(study_topics)').all();
  if (!cols.some(c => c.name === 'updated_at')) {
    db.exec(`
      BEGIN;
      CREATE TABLE study_topics_new (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        assigned_date TEXT NOT NULL,
        topic TEXT NOT NULL,
        description TEXT,
        target_duration_minutes INTEGER DEFAULT 30,
        actual_duration_minutes INTEGER DEFAULT 0,
        status TEXT DEFAULT 'pending',
        spillover_from INTEGER REFERENCES study_topics(id),
        completed_at TEXT,
        created_at TEXT DEFAULT (datetime('now')),
        updated_at TEXT DEFAULT (datetime('now'))
      );
      INSERT INTO study_topics_new (id, assigned_date, topic, description, target_duration_minutes, actual_duration_minutes, status, spillover_from, completed_at, created_at)
        SELECT id, assigned_date, topic, description, target_duration_minutes, actual_duration_minutes, status, spillover_from, completed_at, created_at FROM study_topics;
      DROP TABLE study_topics;
      ALTER TABLE study_topics_new RENAME TO study_topics;
      COMMIT;
    `);
  }

  userDbs.set(id, db);
  return db;
}

// ── Password hashing (node:crypto scrypt — no native deps) ─────────────────
function hashPassword(password) {
  const salt = crypto.randomBytes(16).toString('hex');
  const hash = crypto.scryptSync(password, salt, 64).toString('hex');
  return { salt, hash };
}

function verifyPassword(password, salt, expectedHash) {
  const hash = crypto.scryptSync(password, salt, 64);
  const expected = Buffer.from(expectedHash, 'hex');
  return hash.length === expected.length && crypto.timingSafeEqual(hash, expected);
}

// ── Account & session operations ───────────────────────────────────────────
function createUser(email, password) {
  const { salt, hash } = hashPassword(password);
  try {
    const info = master.prepare(
      'INSERT INTO users (email, password_salt, password_hash) VALUES (?, ?, ?)'
    ).run(email.toLowerCase().trim(), salt, hash);
    return publicUser(info.lastInsertRowid, email);
  } catch (e) {
    if (e.message.includes('UNIQUE')) {
      const err = new Error('An account with this email already exists');
      err.status = 409;
      throw err;
    }
    throw e;
  }
}

function findUserByEmail(email) {
  return master.prepare(
    'SELECT * FROM users WHERE email = ?'
  ).get(email.toLowerCase().trim());
}

function validateLogin(email, password) {
  const user = findUserByEmail(email);
  if (!user) return null;
  if (!verifyPassword(password, user.password_salt, user.password_hash)) return null;
  return user;
}

function issueToken(userId) {
  const token = crypto.randomBytes(32).toString('hex');
  const tokenHash = crypto.createHash('sha256').update(token).digest('hex');
  master.prepare(
    'INSERT INTO sessions (user_id, token_hash) VALUES (?, ?)'
  ).run(userId, tokenHash);
  return token;
}

function getUserByToken(token) {
  if (!token || typeof token !== 'string') return null;
  const tokenHash = crypto.createHash('sha256').update(token).digest('hex');
  return master.prepare(
    `SELECT u.id, u.email FROM sessions s
     JOIN users u ON u.id = s.user_id
     WHERE s.token_hash = ?`
  ).get(tokenHash);
}

function revokeToken(token) {
  if (!token || typeof token !== 'string') return;
  const tokenHash = crypto.createHash('sha256').update(token).digest('hex');
  master.prepare('DELETE FROM sessions WHERE token_hash = ?').run(tokenHash);
}

function publicUser(id, email) {
  return { id: Number(id), email };
}

module.exports = {
  masterDb: master,
  getUserDb,
  createUser,
  findUserByEmail,
  validateLogin,
  issueToken,
  getUserByToken,
  revokeToken,
  publicUser,
};