# Extract to Design System Skill

## Quick Start

Invoke this skill with:

```
/extract-to-design-system <component_description>
```

## Examples

```
/extract-to-design-system PrimaryButton from editor feature
```

```
/extract-to-design-system SearchBar component
```

```
/extract-to-design-system blue buttons across the app
```

## What This Skill Does

1. **Finds** all instances of the component you specify
2. **Identifies** similar components (same colors, styles, patterns)
3. **Creates** the design system module if needed
4. **Extracts** and consolidates components into appropriate folders
5. **Updates** all references across the codebase
6. **Verifies** the changes build successfully

## Component Categories

The skill organizes components into these folders:

- `buttons/` - All button variants
- `forms/` - Input fields, checkboxes, dropdowns
- `cards/` - Card components
- `navigation/` - Nav bars, tabs, breadcrumbs
- `feedback/` - Alerts, dialogs, snackbars
- `layout/` - Containers, dividers, spacers
- `typography/` - Text styles, headings
- `icons/` - Icon components
- `overlays/` - Modals, tooltips, sheets

## Smart Features

### Color-Based Matching

The skill automatically identifies similar components by analyzing:
- Background colors (same color = same variant)
- Border styles
- Padding and sizing
- Text colors
- Modifier patterns

For example, it will find that these are all the same "primary button":
- `Button(backgroundColor = Color.Blue, ...)` in editor module
- `ActionButton(color = primary, ...)` in account module
- `SubmitButton(...)` with blue background in auth module

### Deduplication

Instead of creating multiple similar components, the skill consolidates them into a single, reusable design system component.

## Module Options

By default, components go to `design_system` module, but you can specify:

- `application/core/common_ui` - For app-specific reusable components
- `writeopia_ui` - For SDK components (published to Maven Central)
- `application/core/theme` - For design tokens and theme components

## After Extraction

The skill will:
- ✅ Format code with ktlint
- ✅ Update all imports
- ✅ Update build.gradle.kts dependencies
- ✅ Verify builds pass
- ✅ Show you a summary of changes

## Tips

- Be specific: "blue rounded button" is better than "button"
- Check existing design system first: `/skill edit extract-to-design-system`
- Review the proposed consolidation before approving
- Run tests after extraction: `./gradlew jvmTest`
