## Output Requirements

### Progress Reporting

Write regular progress updates to: `{report_file}`

Structure your report using this template:

```markdown
# Step {step} status: [Completed | Partially completed | Failed]

[Explain the overall status here.

If the planned work wasn't fully completed, provide a detailed explanation of:

- What was completed
- What wasn't completed
- Why it wasn't completed (blockers, challenges, decisions made)

Be specific and honest about the implementation outcome.]

## Summary

[Summarize the work you just did as a concise bullet point list. Each bullet should describe a concrete accomplishment or change. Focus on what was actually implemented, not just what was planned. Examples:

- Created new spawn command with Click options for agent configuration
- Implemented Pydantic models for flow state and agent status
- Added YAML config parsing with validation
- Created comprehensive unit tests with pytest]

## Deferred responsibilities

[Explicitly list any responsibilities from this step that you decided to defer to future steps.

If nothing was deferred, state: "None - all responsibilities for this step were completed."

If items were deferred, list them as bullets with brief explanations of why, for example:

- CLI output formatting - deferred to Step 5 when Textual dashboard is implemented
- Async process monitoring - deferred to Step 8 after synchronous implementation is validated]

## Modified files

[Paste the output from Step 1 here.

Example format:
flow/commands/spawn.py | 45 +++++++++++++++++++
flow/types.py | 23 ++++++++++
flow/lib/config.py | 67 +++++++++++++++++++++++++++
3 files changed, 135 insertions(+)

If no files were modified, paste the actual empty output.]

## Notes

[Include any relevant additional information such as:

- Important implementation decisions and their rationale
- Unexpected challenges encountered and how they were resolved
- Suggestions for future improvements or follow-up work
- Dependencies on other steps or external factors
- Testing notes or verification you performed
- Any assumptions made during implementation
- Technical debt incurred and why

If there are no additional notes, state: "None."]
```

## Completion Workflow

**CRITICAL**: When finished, follow this exact workflow to generate your final report.

### Step 0: Update Shared Files

- Output: "Step 0/4: Updating shared files..."
- Check the **Writable Shared Files** section above for files you must update
- For each writable file:
  - If file doesn't exist: Create it using the provided template
  - If file exists: Append your entry using the template format
- Ensure all placeholders (like `{timestamp}`) are filled with actual values
- Output: "Step 0 complete"

### Step 1: Gather Git Statistics

- Output: "Step 1/4: Gathering git statistics..."
- Run: `git diff --stat` to get statistics of your changes
- Do NOT run `git add` - show working directory changes only
- Store the output for the "Modified files" section
- Output: "Step 1 complete"

### Step 2: Generate Report

- Output: "Step 2/4: Generating report..."
- Fill in the report template above with:
  - Your step number and overall status
  - Summary of what you accomplished
  - Any deferred responsibilities
  - The exact `git diff --stat` output from Step 1
  - Any notes, blockers, or important decisions
- Output: "Step 2 complete"

### Step 3: Output Report and Signal Completion

- Output: "Step 3/4: Finalizing..."
- Write your completed report to: `{report_file}`
- Signal completion by renaming the file:
  - **Success**: Rename to `{success_file}` - Use when you completed your work
  - **Failure**: Rename to `{failed_file}` - Use ONLY when you could NOT complete (crash, error, blocker)

**IMPORTANT**: "Success" means you finished your task, regardless of outcome.

- A builder who implements a feature signals Success
- A reviewer who finds issues and reports them signals Success (the review was successful!)
- A fixer who applies corrections signals Success
- Only signal Failure if you genuinely couldn't complete (e.g., stuck, error, missing dependencies)

The outcome of your work (approved vs changes required, tests passing vs failing, etc.)
is communicated through your report content, NOT through the completion signal.

- Output: "Report complete"
- **STOP IMMEDIATELY** - your work is done. Do not continue after signaling completion.
