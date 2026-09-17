---
name: extract-to-design-system
description: Extract UI components to the design system module with intelligent grouping and deduplication
arguments: [component_description]
allowed-tools:
  - Bash(find *)
  - Bash(grep *)
  - Bash(tree *)
  - Read
  - Glob
  - Grep
  - Edit
  - Write
  - Task
paths: "application/**,writeopia_ui/**"
---

# Extract UI Component to Design System

Extract the specified UI component (**$component_description**) to the design system module, grouping it with similar components.

## Overview

This skill will:
1. Locate all instances of the requested UI component across the codebase
2. Identify similar components (matching colors, styles, patterns)
3. Extract and consolidate them into the design system module
4. Organize by component type (buttons, forms, cards, etc.)
5. Update all references to use the design system version
6. Create the design system module if it doesn't exist

## Component Organization Strategy

Components should be grouped by category in the design system:

```
design_system/
├── buttons/           # All button variants (primary, secondary, text, icon, etc.)
├── forms/             # Input fields, text areas, checkboxes, dropdowns
├── cards/             # Card containers, content cards, list items
├── navigation/        # Tabs, nav bars, bottom bars, breadcrumbs
├── feedback/          # Alerts, snackbars, dialogs, progress indicators
├── layout/            # Containers, dividers, spacers, grids
├── typography/        # Text styles, headings, labels
├── icons/             # Icon components and wrappers
└── overlays/          # Modals, tooltips, popovers, sheets
```

## Extraction Process

### Step 1: Discovery Phase

First, search for the component across the codebase:

1. Use Grep to find all files containing the component name or similar patterns
2. Read each file to understand the component's implementation
3. Extract color values, modifiers, and styling patterns
4. Identify all usages of this component

### Step 2: Similarity Analysis

Look for similar components that should be consolidated:

**Matching Criteria:**
- Background color (same color = likely same variant)
- Shape/corner radius
- Padding/size
- Text color
- Icon usage
- Modifier patterns (e.g., `.fillMaxWidth()`, `.padding(16.dp)`)

**Example:** If you find:
- `Button(backgroundColor = Color.Blue, ...)` in module A
- `CustomButton(containerColor = Color.Blue, ...)` in module B
- `PrimaryActionButton(...)` with blue background in module C

These should likely become **one** `PrimaryButton` component in the design system.

### Step 3: Module Setup

Check if `design_system` module exists:

```bash
ls -la design_system/
```

If it doesn't exist, create it:

```
design_system/
├── build.gradle.kts
└── src/
    └── commonMain/
        └── kotlin/
            └── io/
                └── writeopia/
                    └── designsystem/
                        ├── buttons/
                        ├── forms/
                        ├── cards/
                        └── ...
```

**build.gradle.kts template:**
```kotlin
plugins {
    kotlin("multiplatform")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    jvm()
    js(IR) { browser() }
    wasmJs { browser() }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(project(":application:core:theme"))
        }
    }
}
```

### Step 4: Component Extraction

Create the consolidated component in the appropriate folder:

**Naming convention:**
- Use descriptive names: `PrimaryButton`, `SecondaryButton`, `TextInput`
- Add variant suffixes: `CardElevated`, `CardOutlined`
- Keep names concise but clear

**Component template:**
```kotlin
package io.writeopia.designsystem.buttons

import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.writeopia.theme.WriteopiaTheme

@Composable
fun PrimaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = WriteopiaTheme.colorScheme.primary,
            contentColor = WriteopiaTheme.colorScheme.onPrimary
        )
    ) {
        content()
    }
}
```

### Step 5: Update References

For each file that used the old component:

1. Add import: `import io.writeopia.designsystem.buttons.PrimaryButton`
2. Replace old component usage with new design system component
3. Update `build.gradle.kts` to depend on `:design_system`

### Step 6: Verification

After extraction:

1. Run ktlint: `./gradlew ktlintFormat`
2. Build affected modules: `./gradlew :design_system:build`
3. Run tests: `./gradlew :design_system:jvmTest`
4. Verify app compiles: `./gradlew :application:composeApp:build`

## Color-Based Matching Examples

When analyzing components for similarity, look for these color patterns:

**Primary Actions (Blue/Brand Color):**
```kotlin
backgroundColor = MaterialTheme.colorScheme.primary
backgroundColor = Color(0xFF2196F3)
containerColor = WriteopiaTheme.colorScheme.primary
```
→ Extract as `PrimaryButton`

**Secondary Actions (Transparent/Outlined):**
```kotlin
backgroundColor = Color.Transparent
border = BorderStroke(1.dp, ...)
```
→ Extract as `SecondaryButton` or `OutlinedButton`

**Destructive Actions (Red):**
```kotlin
backgroundColor = Color.Red
backgroundColor = MaterialTheme.colorScheme.error
```
→ Extract as `DestructiveButton` or `ErrorButton`

**Success Actions (Green):**
```kotlin
backgroundColor = Color.Green
backgroundColor = Color(0xFF4CAF50)
```
→ Extract as `SuccessButton`

## Integration with Existing Modules

**If using `application/core/common_ui`:** Components can also go here instead of a separate `design_system` module.

**If using `writeopia_ui`:** This is the SDK's UI module (published to Maven Central). Only extract here if it should be part of the public SDK.

**Recommended:** Use `application/core/theme` for design tokens and `application/core/common_ui` for reusable app components.

## Example Usage

After running this skill, the user should be able to:

```kotlin
// Before (scattered across codebase)
Button(
    onClick = { /* ... */ },
    colors = ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.primary
    )
) { Text("Save") }

// After (using design system)
import io.writeopia.designsystem.buttons.PrimaryButton

PrimaryButton(onClick = { /* ... */ }) {
    Text("Save")
}
```

## Notes

- Prefer extracting to **`application/core/common_ui`** if the `design_system` module seems redundant
- Components in the design system should have **no business logic**, only UI
- Always use `WriteopiaTheme` or `MaterialTheme` instead of hardcoded colors
- Document variants in KDoc comments
- Consider creating a preview/catalog screen to showcase all design system components

## Execution Instructions

When this skill is invoked:

1. **Ask clarifying questions if needed:**
   - "Which feature module should I search in?" (or search everywhere)
   - "Should this go in `design_system`, `common_ui`, or `writeopia_ui`?"

2. **Use the Explore agent** to thoroughly search for the component and similar patterns:
   ```
   Use Task tool with subagent_type=Explore to find all instances of the component
   ```

3. **Present findings** before making changes:
   - Show all instances found
   - List similar components that could be consolidated
   - Propose the design system location and naming

4. **Execute the extraction** after user approval

5. **Verify** the changes build successfully
