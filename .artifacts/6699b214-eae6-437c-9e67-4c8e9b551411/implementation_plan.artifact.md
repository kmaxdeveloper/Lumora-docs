# V1 UI/UX & Production Audit Fixes

This plan addresses several UI/UX bugs, production deficiencies, and technical issues identified in the V1 audit. The goal is to polish the app for Google Play release by fixing edge-to-edge issues, improving contrast, cleaning up UX inconsistencies, and resolving technical bugs like memory leaks and MIME type errors.

## User Review Required

> [!IMPORTANT]
> - The **PDF** and **OCR** tool cards will be removed from the Home screen as they were redundant/empty and the functionality is already accessible within document details.
> - **Edge-to-edge** support will be implemented across all screens using dynamic `WindowInsets`.
> - **Dark Mode** contrast for the Home Hero card will be corrected.

## Proposed Changes

### [System & Navigation]

#### [MODIFY] [MainActivity](file:///C:/Users/User/AndroidStudioProjects/LumoraDocs/app/src/main/java/uz/kmax/documents/MainActivity.kt)
- Ensure basic edge-to-edge setup is correct.

### [UI/UX Polish - Home]

#### [MODIFY] [fragment_home.xml](file:///C:/Users/User/AndroidStudioProjects/LumoraDocs/app/src/main/res/layout/fragment_home.xml)
- Remove `btnOcr` and `btnPdf` tool cards.
- Add `android:fitsSystemWindows="false"` (or handle insets via code).
- Apply padding for status bar to the top header.

#### [MODIFY] [HomeFragment](file:///C:/Users/User/AndroidStudioProjects/LumoraDocs/app/src/main/java/uz/kmax/documents/presentation/home/HomeFragment.kt)
- Remove listeners for `btnOcr` and `btnPdf`.
- Localize hardcoded strings for Rename/Delete dialogs.
- Apply `WindowInsets` to the root view to handle status and navigation bars.

### [UI/UX Polish - Dark Mode & Contrast]

#### [MODIFY] [colors.xml (night)](file:///C:/Users/User/AndroidStudioProjects/LumoraDocs/app/src/main/res/values-night/colors.xml)
- Update `onPrimary` to a light color (e.g., `#E0E7FF`) to fix contrast on `primaryContainer` backgrounds.

#### [MODIFY] [styles.xml (night)](file:///C:/Users/User/AndroidStudioProjects/LumoraDocs/app/src/main/res/values-night/styles.xml)
- Ensure `Widget_Lumora_HeroCard` uses appropriate colors.

### [Scanner & Review Polish]

#### [MODIFY] [fragment_scanner.xml](file:///C:/Users/User/AndroidStudioProjects/LumoraDocs/app/src/main/res/layout/fragment_scanner.xml)
- Replace hardcoded `#40000000` with `?attr/colorSurfaceVariant` (with alpha) or a theme color.
- Remove hardcoded `"0"` from `tvPageCount`.
- Make bottom controls height dynamic or inset-aware.

#### [MODIFY] [ScannerFragment](file:///C:/Users/User/AndroidStudioProjects/LumoraDocs/app/src/main/java/uz/kmax/documents/presentation/scanner/ScannerFragment.kt)
- Implement bitmap recycling in `processImageProxy` to prevent memory leaks.
- Apply `WindowInsets` to handle system navigation bars.

#### [MODIFY] [fragment_review.xml](file:///C:/Users/User/AndroidStudioProjects/LumoraDocs/app/src/main/res/layout/fragment_review.xml)
- Remove `paddingBottom="100dp"` hack.
- Apply dynamic bottom navigation insets to the `RecyclerView` or bottom action bar.

#### [MODIFY] [ReviewFragment](file:///C:/Users/User/AndroidStudioProjects/LumoraDocs/app/src/main/java/uz/kmax/documents/presentation/scanner/ReviewFragment.kt)
- Handle `WindowInsets` for content padding.

### [Documents & Detail Polish]

#### [MODIFY] [DocumentsFragment](file:///C:/Users/User/AndroidStudioProjects/LumoraDocs/app/src/main/java/uz/kmax/documents/presentation/documents/DocumentsFragment.kt)
- Fix MIME type bug in `shareSelectedDocuments` by using `MimeTypeMap` or checking the file extension of the actual file before sharing.

#### [MODIFY] [fragment_document_detail.xml](file:///C:/Users/User/AndroidStudioProjects/LumoraDocs/app/src/main/res/layout/fragment_document_detail.xml)
- Make the fixed `320dp` height responsive using a `ConstraintLayout` ratio or dynamic layout params.

### [Settings & Premium Polish]

#### [NEW] [ic_premium.xml](file:///C:/Users/User/AndroidStudioProjects/LumoraDocs/app/src/main/res/drawable/ic_premium.xml)
- Add a sparkle/star icon for Premium.

#### [NEW] [ic_chevron_right.xml](file:///C:/Users/User/AndroidStudioProjects/LumoraDocs/app/src/main/res/drawable/ic_chevron_right.xml)
- Add a proper chevron icon.

#### [MODIFY] [fragment_settings.xml](file:///C:/Users/User/AndroidStudioProjects/LumoraDocs/app/src/main/res/layout/fragment_settings.xml)
- Use `ic_premium` instead of `ic_ai`.
- Use `ic_chevron_right` instead of rotated `ic_share`.

#### [MODIFY] [fragment_premium.xml](file:///C:/Users/User/AndroidStudioProjects/LumoraDocs/app/src/main/res/layout/fragment_premium.xml)
- Polish the paywall UI with better contrast and layout.

## Verification Plan

### Automated Tests
- Run existing unit tests for `DocumentRepository` and `ScannerViewModel`.
- Verify build success.

### Manual Verification
- Deploy to a physical device or emulator running Android 15.
- Verify Edge-to-Edge handling (status/navigation bars).
- Check Dark Mode contrast on the Home screen.
- Verify that bitmap recycling works by monitoring memory usage during long scanning sessions.
- Test bulk sharing with both PDFs and Images to ensure correct MIME types.
- Verify that hardcoded strings are gone by switching the device language.
