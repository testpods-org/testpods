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

Be specific and honest about the outcome.]

## Summary

[Summarize the work you just did as a concise bullet point list. Each bullet should describe a concrete accomplishment. Focus on what was actually done, not just what was planned. Examples:

- Analyzed feature request and identified clarification questions
- Created draft spec plan with 6 implementation steps
- Reviewed spec structure for completeness and anti-code compliance
- Documented key architectural decisions in planning log]

## Deferred responsibilities

[Explicitly list any responsibilities from this step that you decided to defer to future steps.

If nothing was deferred, state: "None - all responsibilities for this step were completed."

If items were deferred, list them as bullets with brief explanations of why.]

## Artifacts created

[List the files you created or modified during this planning step.

Example format:

- specs/feature-name.md (new spec plan, 150 lines)
- flows/plan_feature/planning-log.md (appended planning log entry)

If no artifacts were created, state: "None."]

## Notes

[Include any relevant additional information such as:

- Important planning decisions and their rationale
- Clarification questions that need human input
- Suggestions for implementation approach
- Open questions for future phases
- Dependencies or assumptions

If there are no additional notes, state: "None."]
```

## Completion Workflow

**CRITICAL**: When finished, follow this exact workflow to generate your final report.

### Step 0: Update Shared Files

- Output: "Step 0/2: Updating shared files..."
- Check the **Writable Shared Files** section above for files you must update
- For each writable file:
  - If file doesn't exist: Create it using the provided template
  - If file exists: Append your entry using the template format
- Ensure all placeholders (like `{timestamp}`) are filled with actual values
- Output: "Step 0 complete"

### Step 1: Generate Report and Signal Completion

- Output: "Step 1/2: Generating report..."
- Fill in the report template above with:
  - Your step number and overall status
  - Summary of what you accomplished
  - Any deferred responsibilities
  - Artifacts you created (spec plans, planning log entries, etc.)
  - Any notes, clarifications, or important decisions
- Write your completed report to: `{report_file}`
- Signal completion by renaming the file:
  - **Success**: Rename to `{success_file}` - Use when you completed your work
  - **Failure**: Rename to `{failed_file}` - Use ONLY when you could NOT complete (crash, error, blocker)

**IMPORTANT**: "Success" means you finished your task, regardless of outcome.

- A plan-creator who drafts a spec plan signals Success
- A plan-reviewer who finds issues and reports them signals Success (the review was successful!)
- Only signal Failure if you genuinely couldn't complete (e.g., stuck, error, missing dependencies)

The outcome of your work (approved vs changes required, complete vs needs more detail, etc.)
is communicated through your report content, NOT through the completion signal.

- Output: "Report complete"
- **STOP IMMEDIATELY** - your work is done. Do not continue after signaling completion.
