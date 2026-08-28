# Custom Dialog Implementation Plan

Convert standard `MaterialAlertDialogBuilder` usages to beautiful custom-designed dialogs matching the Lumora Docs premium brand.

## Proposed Changes

### [app](file:///C:/Users/User/AndroidStudioProjects/LumoraDocs/app)

#### [NEW] [dialog_base.xml](file:///C:/Users/User/AndroidStudioProjects/LumoraDocs/app/src/main/res/layout/dialog_base.xml)
A root layout for custom dialogs containing:
- Rounded background (CardView or ShapeDrawable).
- Optional Icon/Illustration.
- Title and Message.
- Custom styled Primary and Secondary buttons.

#### [NEW] [dialog_input.xml](file:///C:/Users/User/AndroidStudioProjects/LumoraDocs/app/src/main/res/layout/dialog_input.xml)
Specifically for Rename/Input tasks, extending the base design with an `EditText`.

#### [NEW] [DialogUtils.kt](file:///C:/Users/User/AndroidStudioProjects/LumoraDocs/app/src/main/java/uz/kmax/documents/utils/DialogUtils.kt)
A helper object or extension functions to build and show these dialogs:
- `showLumoraDialog(...)`
- `showLumoraInputDialog(...)`
- `showLumoraChoiceDialog(...)`

#### [MODIFY] Existing Fragments
Update the following files to use the new custom dialogs:
- [DocumentDetailFragment.kt](file:///C:/Users/User/AndroidStudioProjects/LumoraDocs/app/src/main/java/uz/kmax/documents/presentation/documents/DocumentDetailFragment.kt) (Rename, Info, Delete)
- [DocumentsFragment.kt](file:///C:/Users/User/AndroidStudioProjects/LumoraDocs/app/src/main/java/uz/kmax/documents/presentation/documents/DocumentsFragment.kt) (Rename, Delete)
- [HomeFragment.kt](file:///C:/Users/User/AndroidStudioProjects/LumoraDocs/app/src/main/java/uz/kmax/documents/presentation/home/HomeFragment.kt) (Rename, Delete)
- [OcrFragment.kt](file:///C:/Users/User/AndroidStudioProjects/LumoraDocs/app/src/main/java/uz/kmax/documents/presentation/ocr/OcrFragment.kt) (OCR Limit)
- [SettingsFragment.kt](file:///C:/Users/User/AndroidStudioProjects/LumoraDocs/app/src/main/java/uz/kmax/documents/presentation/settings/SettingsFragment.kt) (Appearance)

## Verification Plan

### Automated Tests
- Build check to ensure layouts and code compile correctly.

### Manual Verification
- Trigger each dialog in the app to verify:
    - Correct styling and responsiveness.
    - Keyboard behavior in input dialogs.
    - Button click actions.
