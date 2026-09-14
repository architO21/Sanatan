const { getUserByToken, getUserDb } = require('../db/database');

/**
 * Middleware: resolve the Authorization Bearer token into a user,
 * and attach that user's own database to req.db + req.user.
 */
function requireAuth(req, res, next) {
  const header = req.headers.authorization || '';
  const match = header.match(/^Bearer\s+(.+)$/i);
  const token = match ? match[1] : null;

  const user = getUserByToken(token);
  if (!user) {
    return res.status(401).json({ error: 'Not authenticated' });
  }

  req.user = user;
  req.db = getUserDb(user.id);
  next();
}

module.exports = { requireAuth };