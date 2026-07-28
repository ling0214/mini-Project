# Software Analyst Workflow Assistant - Next Plan

## Current Scope

The current platform is mainly a Change Request workflow assistant for Software Analyst work.

It supports the flow:

1. Capture manual/sample ticket intake details
2. Analyse what needs to change
3. Identify missing information and ambiguities
4. Ask clarification and re-analyse the requirement
5. Review the requirement before moving forward
6. Run impact analysis
7. Generate test scenarios
8. Compile a handoff summary

This already covers one important part of a Software Analyst's daily work: handling change requests from requirement understanding until developer or QA handoff.

## Main Gap

At the moment, the platform can analyse the requirement text and continue the workflow, but it is not yet strongly connected to a real project context.

To make it more practical for real Software Analyst work, the platform should be able to read and use project context such as:

- source code
- README and technical documents
- API routes
- database schema
- previous requirements
- previous analysis results
- testing notes
- system logs, if available later

Without project context, the platform behaves more like an AI text analyser.

With project context, it becomes closer to a real Software Analyst workflow assistant.

Recent progress:

- Requirement analysis now has a clean AI provider boundary.
- The platform can keep rule-based analysis for stable demos.
- The same requirement-analysis skill can be switched to an OpenAI-backed implementation through environment variables.
- This supports the original idea: the platform coordinates AI skills in the analyst workflow, instead of recreating Claude Skills as a standalone feature.

## Proposed Next Implementation

### 1. Improve Ticket Intake

Status: started.

The platform now supports a manual/sample ticket intake shape before requirement analysis.

It captures:

- ticket key
- ticket title
- priority
- reporter
- description
- acceptance criteria
- comments or clarification history

Later, this same shape can be populated from Jira, GitHub Issues, email notes, or meeting notes.

Current Jira-like enhancement:

- dry-run Jira import panel in Ticket Intake
- `MBC-204` sample ticket import
- analyst reviews imported fields before analysis
- no external Jira write is performed

Next Jira phases:

- real Jira read-only import by issue key or URL
- reviewed Jira comment/write-back after handoff summary approval

### 2. Connect Real Project Context

Status: started with MyBanjirCare as the configured sample target project.

Allow the platform to connect to a sample project or real FYP project.

The goal is to let the platform understand the existing system before doing impact analysis.

Example:

Requirement:

"Add OTP verification during user login."

The platform should be able to identify related areas such as:

- login page
- authentication controller
- user table
- session handling
- existing validation logic
- related test cases

Current demo target:

- Project: MyBanjirCare
- Framework: Laravel 10 / PHP 8.1
- Main areas: Aid Request, Donation, Flood Report, Collection Center, Auth / OTP
- Current implementation: local keyword retrieval over the configured MyBanjirCare repository files
- Next implementation: replace keyword retrieval with vector RAG/codebase-memory retrieval from the selected repository

### 3. Add RAG / Memory For Project Knowledge

Index project files and documents so the platform can retrieve relevant context during analysis.

This can support:

- finding related files
- remembering previous requirement decisions
- reusing previous analysis results
- comparing a new ticket with similar past changes

Example:

"This change request is similar to the previous login validation change. Last time, the affected areas were AuthController, LoginService, and login test scenarios."

### 4. Improve Impact Analysis With Evidence

Impact analysis should not only output affected areas. It should also explain why each area is affected.

Example output:

- Affected area: Login API
- Reason: The requirement changes the login process.
- Evidence: The project contains an authentication route and login service related to user login.

This is closer to real analyst work because analysts need to justify their findings.

### 5. Improve Test Case Management

The current platform can generate test scenarios. The next step is to make the testing scope editable and reviewable.

Useful functions:

- accept test case
- reject test case
- edit test case
- add manual test case
- set priority
- export testing scope

This is useful because Software Analysts often prepare or support UAT and testing scope.

### 6. Improve Clarification Tracking

The current platform can ask clarification and re-analyse.

The next step is to track clarification more clearly.

Useful functions:

- show unclear points
- record stakeholder answer
- show what changed after clarification
- mark requirement as ready

This makes the platform more realistic because Software Analysts often need to clarify requirements with users, clients, developers, or supervisors.

### 7. Export Analyst Handoff

Even if the platform already shows a handoff summary, real work usually still needs a document or shareable output.

Useful export options:

- Markdown report
- PDF report
- copyable developer handoff
- QA testing brief

This helps the analyst share the final result with developer, tester, or supervisor.

## Suggested Priority

The best next step is:

1. Finish ticket intake validation and history display
2. Replace keyword file retrieval with RAG / Memory retrieval
3. Make impact analysis evidence-based from retrieved code/docs
4. Improve test case management
5. Add export for handoff summary

This order is practical because project context is the key feature that makes the platform different from a normal chatbot.

## Demo Direction

The demo should show:

1. User submits a change request
2. Platform captures the change as a ticket intake
3. Platform analyses requirement and finds missing information
4. User provides clarification
5. Platform checks project context
6. Platform suggests impacted modules or files with evidence
7. Platform generates testing scope
8. Platform compiles a handoff summary

This demonstrates how the platform reduces repetitive manual work in a Software Analyst's daily workflow.
