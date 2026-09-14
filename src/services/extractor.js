// Rule-based extraction of structured data from journal entries.
// No LLM required. Falls back gracefully. LLM can be wired in later.

/**
 * Extract calories from text like:
 * - "ate 300 calories of oatmeal"
 * - "had a chicken salad for lunch (~500 cal)"
 * - "oatmeal with banana (around 300 cal)"
 * - "dinner was pasta, maybe 600-700 calories"
 */
function extractCalories(text) {
  const calories = [];

  // Match all occurrences: [meal prefix] ... [number] calories/cal/kcal
  const mealTypes = ['breakfast', 'lunch', 'dinner', 'snack', 'brunch', 'dessert'];
  const caloriesPattern = /(\d[\d,]*)\s*(?:calories|cal\b|kcal)\b/gi;
  const lines = text.split(/[.!?;\n]+/).map(l => l.trim()).filter(l => l);

  for (const line of lines) {
    const matches = [...line.matchAll(caloriesPattern)];
    for (const match of matches) {
      const calValue = parseInt(match[1].replace(/,/g, ''), 10);
      if (calValue > 0 && calValue < 10000) {
        const lowerLine = line.toLowerCase();
        let mealType = 'other';

        // Look at the window around the calorie mention for the meal
        const start = Math.max(0, match.index - 80);
        const window = lowerLine.slice(start, match.index + 20);
        for (const meal of mealTypes) {
          if (window.includes(meal)) {
            mealType = meal;
            break;
          }
        }

        calories.push({
          meal_type: mealType,
          description: line,
          calories: calValue
        });
      }
    }
  }

  return calories;
}

/**
 * Extract activities and steps from text like:
 * - "walked to work and back (about 8000 steps)"
 * - "went to the gym for 1 hour"
 * - "ran 5km"
 * - "did 30 minutes of yoga"
 */
function extractActivities(text) {
  const activities = [];

  // Steps detection: "X steps"
  const stepMatch = text.match(/(\d[\d,]*)\s*(?:steps?)\b/i);
  if (stepMatch) {
    const steps = parseInt(stepMatch[1].replace(/,/g, ''), 10);
    let stepsContext = '';
    const lineWithSteps = text.split(/[.!?;\n]+/).find(l =>
      l.toLowerCase().includes('steps')
    );
    if (lineWithSteps) stepsContext = lineWithSteps.trim();

    activities.push({
      activity_type: 'walking',
      duration_minutes: null,
      steps: steps,
      calories_burned: null,
      description: stepsContext
    });
  }

  // Exercise detection: [activity] for [X minutes/hours]
  const activitiesList = [
    { regex: /\bwalk(?:ed|ing)?\b/i, type: 'walking' },
    { regex: /\b(?:ran|running|run|jog(?:ged|ging)?)\b/i, type: 'running' },
    { regex: /\bgym\b/i, type: 'gym' },
    { regex: /\byoga\b/i, type: 'yoga' },
    { regex: /\bworkout\b/i, type: 'workout' },
    { regex: /\bcycl(?:ed|ing|e|ed)\b/i, type: 'cycling' },
    { regex: /\bswim(?:ming|med)?\b/i, type: 'swimming' },
    { regex: /\bstretch(?:ing|ed)?\b/i, type: 'stretching' },
    { regex: /\bmorning run\b/i, type: 'running' },
  ];

  const alreadyDetected = new Set(['walking']); // steps already handled

  for (const { regex, type } of activitiesList) {
    if (alreadyDetected.has(type)) continue;
    if (regex.test(text)) {
      // Try to find duration
      let duration = null;
      const durationMatch = text.match(
        /(?:for|about|around|~)?\s*(\d+(?:\.\d+)?)\s*(hours?|hrs?|minutes?|mins?|min)\b/i
      );
      if (durationMatch) {
        const val = parseFloat(durationMatch[1]);
        const unit = durationMatch[2].toLowerCase();
        duration = unit.startsWith('h')
          ? Math.round(val * 60)
          : Math.round(val);
      }
      activities.push({
        activity_type: type,
        duration_minutes: duration,
        steps: type === 'walking' && stepMatch
          ? parseInt(stepMatch[1].replace(/,/g, ''), 10)
          : null,
        calories_burned: null,
        description: text.split(/[.!?;\n]+/).find(l =>
          regex.test(l.toLowerCase())
        )?.trim() || text.trim()
      });
      alreadyDetected.add(type);
    }
  }

  return activities;
}

/**
 * Extract mood/sleep from text:
 * - "felt great" / "was tired" / "mood: good"
 * - "slept 7 hours" / "got 8 hours of sleep"
 */
function extractMoodAndSleep(text) {
  const result = { mood: null, sleep_hours: null };

  const moodPatterns = {
    great: /\b(?:felt|was|feeling)\s+(?:great|amazing|awesome|fantastic)\b/i,
    good: /\b(?:felt|was|feeling)\s+(?:good|fine|okay|decent)\b/i,
    tired: /\b(?:felt|was|feeling)\s+(?:tired|exhausted|drained)\b/i,
    stressed: /\b(?:felt|was|feeling)\s+(?:stressed|anxious|worried|overwhelmed)\b/i,
    sad: /\b(?:felt|was|feeling)\s+(?:sad|down|low|depressed)\b/i,
    happy: /\b(?:felt|was|feeling)\s+(?:happy|joyful|cheerful|great)\b/i,
  };

  for (const [mood, pattern] of Object.entries(moodPatterns)) {
    if (pattern.test(text)) {
      result.mood = mood;
      break;
    }
  }

  if (result.mood === null && /\bmood\s*[:,-]?\s*(\w+)/i.test(text)) {
    const moodMatch = text.match(/\bmood\s*[:,-]?\s*(\w+)/i);
    result.mood = moodMatch[1].toLowerCase();
  }

  const sleepPattern =
    /(?:slept|got|had)\s+(\d+(?:\.\d+)?)\s*(?:hours?|hrs?|h)\s*(?:of\s*)?(?:sleep)?/i;
  const sleepMatch = text.match(sleepPattern);
  if (sleepMatch) {
    result.sleep_hours = parseFloat(sleepMatch[1]);
  }

  return result;
}

/**
 * Extract study info. The study topic:
 * - Might be explicitly mentioned: "studied React hooks"
 * - Might be implied: "didn't finish the section on custom hooks"
 *
 * STUDIES are tracked as topics assigned via the study tracker.
 * Here we detect whether the user mentions studying and extract
 * duration + topic keywords for the dashboard.
 */
function extractStudy(text) {
  const studyPatterns = [
    /\bstud(?:ied|ying|y)\s+(?:the\s+|about\s+)?([^.!?;]{2,60}?)(?:\s+for\s+\d+|\s+but\b|\s+and\b|\.|$)/i,
    /\blearn(?:ed|ing)?\s+(?:(?:about|on)\s+)?([^.!?;]{2,60}?)(?:\s+for\s+\d+|\s+but\b|\s+and\b|\.|$)/i,
    /\breview(?:ed|ing)?\s+(?:the\s+)?([^.!?;]{2,60}?)(?:\s+for\s+\d+|\s+but\b|\s+and\b|\.|$)/i,
    /\bpractic(?:ed|ing)\s+([^.!?;]{2,60}?)(?:\s+for\s+\d+|\s+but\b|\s+and\b|\.|$)/i,
  ];

  let topic = null;
  for (const pattern of studyPatterns) {
    const m = text.match(pattern);
    if (m) {
      topic = m[1].trim();
      break;
    }
  }

  if (!topic && /\b(?:studied|studying|learning|learned|reviewing)\b/i.test(text)) {
    // Fallback: pick the noun phrase after "study"
    const m = text.match(/\b(?:studied|studying|learning|learned|reviewing)\b\s*(\w[\w\s]{2,40}?)(?=\s+(?:for|at|in|from|with|but|and|\.|$))/i);
    if (m) topic = m[1].trim();
  }

  if (!topic) return null;

  // Duration
  let duration = null;
  const durationMatch = text.match(
    /\bstud(?:ied|ying|y)\b[^.!?;]*?(?:for|about|around|~)?\s*(\d+)\s*(?:hours?|minutes?|mins?|hrs?)\b/i
  ) || text.match(
    /(?:for|about|around|~)\s*(\d+)\s*(?:hours?|minutes?|mins?|hrs?)\b/i
  );
  if (durationMatch) {
    const val = parseInt(durationMatch[1], 10);
    const after = text.match(
      /(\d+)\s*(hours?|minutes?|mins?|hrs?)\b/i
    );
    duration = after[2].toLowerCase().startsWith('h')
      ? val * 60
      : val;
  }

  // Completed detection: did NOT finish, didn't get through, incomplete, still
  const completedMarkers = /\b(?:finished|completed|done|covered|got through)\b/i;
  const incompleteMarkers = /\b(?:didn'?t\s+finish|didn'?t\s+complete|not\s+finish|not\s+complete|incomplete|didn'?t\s+get|spillover|spilled|partially)\b/i;

  let completed = null;
  if (incompleteMarkers.test(text)) completed = false;
  else if (completedMarkers.test(text)) completed = true;

  // Spillover items - things not finished
  let spilloverItems = [];
  if (completed === false) {
    const spilloverMatch = text.match(
      /(?:didn'?t\s+finish|not\s+finish|didn'?t\s+get\s+through|still\s+need\s+to\s+do|left)\s+(?:the\s+)?([^.!?;]{3,60})/i
    );
    if (spilloverMatch) {
      spilloverItems = [spilloverMatch[1].trim()];
    }
  }

  return {
    topic: topic,
    duration_minutes: duration,
    completed: completed,
    spillover_items: spilloverItems
  };
}

/**
 * Extract expenses from text like:
 * - "spent $50 on lunch"
 * - "bought a shirt for $30"
 * - "paid 200 rs for taxi"
 * - "gift for bestie - ₹799"
 * - "gave mom 500"
 */
function extractExpenses(text) {
  const expenses = [];

  // Split into segments on punctuation, but keep commas inside numbers intact.
  const segments = text
    .split(/[.!?;]\s*(?=\S)|,\s*(?![0-9])/g)
    .map(s => s.trim())
    .filter(s => s.length > 0);

  const seen = new Set();

  const pushExpense = (description, amount) => {
    const amt = parseFloat(amount.replace(/,/g, ''));
    let desc = description.trim();
    // Remove trailing currency leftovers like "rs", "$" and punctuation
    desc = desc
      .replace(/(?:rs\.?|₹|inr|\$)\s*$/i, '')
      .replace(/[.,;:!?]\s*$/, '')
      .trim();
    if (amt > 0 && amt < 10000000 && desc.length > 1) {
      const key = `${desc}|${amt}`;
      if (!seen.has(key)) {
        seen.add(key);
        expenses.push({ description: desc, amount: amt });
      }
    }
  };

  const currencyPrefix = '(?:rs\\.?\\s*|₹\\s*|rupees?\\s*|INR\\s*|\\$\\s*)?';
  const amountWithSuffix =
    '(\\d[\\d,]*[.]?\\d*)\\s*(?:rupees?|rs\\.?|bucks|INR|\\$)?';

  // Pattern 1: verb + amount (+ on/for/in) + thing
  //   "spent $30 on a movie", "paid 200 for uber", "spent 1500 rupees on groceries"
  const verbPattern = new RegExp(
    `\\b(?:spent|spend|paid|pay|gave|give|cost|costs|bought|burnt|dropped)\\s+` +
    `${currencyPrefix}${amountWithSuffix}\\s*(?:on|for|at|in)?\\s*(.+)$`,
    'i'
  );

  // Pattern 2: thing for/at/worth amount
  //   "bought a shirt for $25", "ate lunch for 500", "lunch for 300"
  const thingAmountPattern = new RegExp(
    `^(.+?)\\s+(?:for|at|worth)\\s+${currencyPrefix}${amountWithSuffix}$`,
    'i'
  );

  // Pattern 3: amount followed by "for/on" then thing
  //   "500 for lunch", "$30 on a gift"
  const amountThingPattern = new RegExp(
    `^${currencyPrefix}${amountWithSuffix}\\s+(?:for|on)\\s+(.+)$`,
    'i'
  );

  // Pattern 4: bought/got <thing> for/at <amount> (thing-first, no for)
  //   "bought coffee 100"
  const boughtPattern = new RegExp(
    `^(?:bought|got|ordered|picked up)\\s+(.+?)\\s+(?:for|at)?\\s*${currencyPrefix}${amountWithSuffix}$`,
    'i'
  );

  // Pattern 5: gave/give <recipient> <amount>
  //   "gave mom 1000", "give bestie 500"
  const givePattern = new RegExp(
    `^(?:gave|give)\\s+(.+?)\\s+${currencyPrefix}${amountWithSuffix}$`,
    'i'
  );

  // Pattern 6: <thing> for <someone> <amount>  → "gift for bestie 799"
  const forSomeonePattern = new RegExp(
    `^(.+?)\\s+(?:for|on)\\s+(.+?)\\s+${currencyPrefix}${amountWithSuffix}$`,
    'i'
  );

  // Pattern 7: bare "<Thing> <number>" fallback, e.g. "Dinner 300", "coffee 100"
  const barePattern = new RegExp(
    `^([A-Za-z][A-Za-z\\s']{2,40}?)\\s+(?:${currencyPrefix})${amountWithSuffix}$`,
    'i'
  );

  // Words that indicate a number is NOT an expense amount
  const nonExpense = /steps|calories|\bcal\b|kcal|km\b|kms?\b|hours?|mins?|minutes?|cards?|times?/i;

  for (const seg of segments) {
    const lowerSeg = seg.toLowerCase();

    // Skip segments that look like steps/calories/duration
    if (
      /(\d[\d,.]*)\s*(?:steps?|calories|cal\b|kcal)\b/.test(lowerSeg) ||
      /(?:6k|5k|4k|10k)\s*steps?/i.test(lowerSeg)
    ) {
      continue;
    }

    let m;

    // Pattern 1: verb-first
    m = seg.match(verbPattern);
    if (m) {
      pushExpense(m[2], m[1]);
      continue;
    }

    // Pattern 2: thing for amount
    m = seg.match(thingAmountPattern);
    if (m) {
      const thing = m[1];
      if (!nonExpense.test(thing)) pushExpense(thing, m[2]);
      continue;
    }

    // Pattern 4: bought <thing> for <amount>
    m = seg.match(boughtPattern);
    if (m) {
      const thing = m[1];
      if (!nonExpense.test(thing)) pushExpense(thing, m[2]);
      continue;
    }

    // Pattern 5: gave <recipient> <amount>
    m = seg.match(givePattern);
    if (m) {
      pushExpense(m[1], m[2]);
      continue;
    }

    // Pattern 3: amount for thing
    m = seg.match(amountThingPattern);
    if (m) {
      const thing = m[2];
      if (!nonExpense.test(thing)) pushExpense(thing, m[1]);
      continue;
    }

    // Pattern 6: thing for someone amount (e.g. "gift for bestie 799")
    m = seg.match(forSomeonePattern);
    if (m) {
      const combined = `${m[1].trim()} for ${m[2].trim()}`;
      pushExpense(combined, m[3]);
      continue;
    }

    // Pattern 7: bare "Thing number"
    m = seg.match(barePattern);
    if (m) {
      const thing = m[1];
      if (!nonExpense.test(thing)) pushExpense(thing, m[2]);
      continue;
    }
  }

  return expenses;
}

/**
 * Main extraction entry point.
 * Returns a normalized structure that the database layer persists.
 */
function extract(text) {
  const lowerText = text.toLowerCase();

  const calories = extractCalories(text);
  const activities = extractActivities(text);
  const study = extractStudy(text);
  const moodSleep = extractMoodAndSleep(text);
  const expenses = extractExpenses(text);

  // Total calories
  const totalCalories = calories.reduce((sum, c) => sum + c.calories, 0);

  // Total steps
  const totalSteps = activities
    .filter(a => a.steps)
    .reduce((sum, a) => sum + a.steps, 0);

  return {
    calories: calories,
    total_calories: totalCalories,
    activities: activities,
    total_steps: totalSteps,
    study: study,
    mood: moodSleep.mood,
    sleep_hours: moodSleep.sleep_hours,
    expenses: expenses,
    has_structured_data: calories.length > 0 || activities.length > 0 || study !== null
  };
}

module.exports = { extract };