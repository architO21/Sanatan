const express = require('express');
const router = express.Router();
const db = require('../db/database');

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

// POST /api/auth/signup
router.post('/signup', (req, res) => {
  const { email, password } = req.body;
  if (!email || !EMAIL_RE.test(email)) {
    return res.status(400).json({ error: 'A valid email is required' });
  }
  if (!password || password.length < 6) {
    return res.status(400).json({ error: 'Password must be at least 6 characters' });
  }

  try {
    const user = db.createUser(email, password);
    const token = db.issueToken(user.id);
    res.status(201).json({ token, user });
  } catch (err) {
    res.status(err.status || 500).json({ error: err.message });
  }
});

// POST /api/auth/login
router.post('/login', (req, res) => {
  const { email, password } = req.body;
  if (!email || !password) {
    return res.status(400).json({ error: 'email and password are required' });
  }

  const user = db.validateLogin(email, password);
  if (!user) {
    return res.status(401).json({ error: 'Invalid email or password' });
  }

  const token = db.issueToken(user.id);
  res.json({ token, user: { id: user.id, email: user.email } });
});

// POST /api/auth/logout
router.post('/logout', (req, res) => {
  const match = (req.headers.authorization || '').match(/^Bearer\s+(.+)$/i);
  if (match) db.revokeToken(match[1]);
  res.status(204).end();
});

// GET /api/auth/me
router.get('/me', (req, res) => {
  const match = (req.headers.authorization || '').match(/^Bearer\s+(.+)$/i);
  const user = db.getUserByToken(match ? match[1] : null);
  if (!user) return res.status(401).json({ error: 'Not authenticated' });
  res.json({ user });
});

module.exports = router;