# AI Functionality Frozen for V1

## Overview
The AI-powered document intelligence features (Summarization, Structured Analysis, AI Context Preparation) have been successfully implemented but are frozen for the V1 release. All code remains in the project but is disconnected from the active user flow to ensure a focused MVP.

## Preserved Components
The following architectural layers remain intact in the codebase:
- **Domain:** `AiSummary`, `DocumentContext`, `DocumentChunk`, `AiUsage`, `AiRepository`, and all AI Use Cases.
- **Data:** `AiRepositoryImpl`, `AiApiService`, `DocumentAiSummaryEntity`, `DocumentContextBuilder`, and `DocumentTextNormalizer`.
- **Presentation:** `SummaryFragment`, `SummaryViewModel`, and `fragment_summary.xml`.
- **Database:** Room Migration 4→5 and `document_summaries` table.

## Disabled Entry Points (V1)
- **Home Screen:** The "AI Summary" action card has been removed from `fragment_home.xml`.
- **Document Detail:** The "AI Summary" button and "AI Ready" metadata indicator have been removed from `fragment_document_detail.xml`.
- **Navigation:** The `summaryFragment` destination remains in `nav_graph.xml` but is unreachable via UI.
- **Network:** Retrofit `AiApiService` and `AiRepository` initialization in `LumoraApplication` are currently disabled.

## Re-enabling AI in V2/V3
To restore AI functionality, follow these steps:
1. **UI:** Restore the AI buttons in `fragment_home.xml` and `fragment_document_detail.xml` (revert `git` changes or uncomment).
2. **Logic:** Uncomment AI initialization in `LumoraApplication.kt` and restore the `AiRepository` injection in `ViewModelFactory.kt`.
3. **Detail View:** Re-attach the `btnSummarize` listener and `documentContext` observer in `DocumentDetailFragment.kt`.
4. **Backend:** Update the placeholder `baseUrl` in `LumoraApplication.kt` with the production KMAX AI Backend URL.

## Retention Strategy
- **Dependencies:** Retrofit, Gson, and OkHttp are retained as they may be used for other V1 features later.
- **Database:** The schema remains at Version 5 to ensure compatibility with any early adopters who may have generated local metadata.
