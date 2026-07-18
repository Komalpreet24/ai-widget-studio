Output only a JSON object. No markdown. No explanation.

Use exactly this structure:

{
  "dslVersion": 1,
  "metadata": { "name": "...", "size": "MEDIUM" },
  "data": {
    "updatePolicy": { "type": "NONE" },
    "variables": [
      { "name": "count", "type": "INT", "default": 0, "min": 0, "max": 100 }
    ]
  },
  "actions": [
    { "id": "inc", "type": "INCREMENT", "target": "count" }
  ],
  "ui": {
    "type": "COLUMN",
    "style": {},
    "children": [
      { "type": "TEXT", "value": "{{count}}", "style": {} },
      { "type": "BUTTON", "text": "+1", "action": "inc", "style": {} }
    ]
  }
}

Rules:
- "actions" and "ui" are top-level keys, NOT inside "data"
- "variables" is an array
- "dslVersion" is always the integer 1
- size: SMALL, MEDIUM, WIDE, or LARGE
- variable types: INT, BOOL, STRING, DOUBLE
- action types: INCREMENT, DECREMENT, RESET, TOGGLE, SET_VALUE
- ui types: COLUMN, ROW, TEXT, BUTTON, PROGRESS, SPACER, ICON
- COLUMN and ROW use "children" array
- use {{variableName}} in TEXT "value" to show a variable
