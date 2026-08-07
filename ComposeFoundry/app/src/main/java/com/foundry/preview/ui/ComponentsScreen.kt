package com.foundry.preview.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.foundry.preview.state.FoundryViewModel

data class ComponentTemplate(
    val name: String,
    val description: String,
    val dsl: String
)

data class PageTemplate(
    val name: String,
    val description: String,
    val componentCount: Int,
    val dsl: String
)

private val PAGE_TEMPLATES = listOf(
    PageTemplate(
        name = "Login Page",
        description = "Email + password fields with login button",
        componentCount = 6,
        dsl = """
{
  "version": "1.0",
  "theme": { "primaryColor": "#FF1976D2", "backgroundColor": "#FFFFFFFF", "surfaceColor": "#FFF5F5F5", "textColor": "#FF212121", "isDark": false },
  "root": {
    "type": "Column",
    "modifier": { "fillMaxWidth": true, "fillMaxHeight": true, "padding": { "all": 32 }, "horizontalAlignment": "center" },
    "children": [
      { "type": "Spacer", "modifier": { "height": 60 } },
      { "type": "Text", "attributes": { "text": "Welcome Back", "fontSize": "28", "fontWeight": "bold" }, "modifier": { "padding": { "bottom": 8 } } },
      { "type": "Text", "attributes": { "text": "Sign in to continue", "fontSize": "14", "color": "#FF888888" }, "modifier": { "padding": { "bottom": 32 } } },
      { "type": "TextField", "attributes": { "label": "Email", "placeholder": "you@example.com" }, "modifier": { "fillMaxWidth": true, "padding": { "bottom": 16 } } },
      { "type": "TextField", "attributes": { "label": "Password", "placeholder": "••••••••" }, "modifier": { "fillMaxWidth": true, "padding": { "bottom": 24 } } },
      { "type": "Button", "attributes": { "text": "Sign In" }, "modifier": { "fillMaxWidth": true } },
      { "type": "Spacer", "modifier": { "height": 16 } },
      { "type": "Text", "attributes": { "text": "Forgot password?", "fontSize": "12", "color": "#FF1976D2" } }
    ]
  }
}
""".trimIndent()
    ),
    PageTemplate(
        name = "Profile Card",
        description = "Avatar + name + email in elevated card",
        componentCount = 5,
        dsl = """
{
  "version": "1.0",
  "theme": { "primaryColor": "#FF6200EE", "backgroundColor": "#FFF5F5F5", "surfaceColor": "#FFFFFFFF", "textColor": "#FF1A1A2E", "isDark": false },
  "root": {
    "type": "Column",
    "modifier": { "fillMaxWidth": true, "padding": { "all": 24 } },
    "children": [
      { "type": "Text", "attributes": { "text": "Profile", "fontSize": "22", "fontWeight": "bold" }, "modifier": { "padding": { "bottom": 16 } } },
      {
        "type": "Card",
        "modifier": { "fillMaxWidth": true, "cornerRadius": 16, "elevation": 4 },
        "children": [
          {
            "type": "Row",
            "modifier": { "fillMaxWidth": true, "padding": { "all": 16 }, "verticalAlignment": "center" },
            "children": [
              { "type": "Image", "attributes": { "contentDescription": "Avatar" }, "modifier": { "width": 56, "height": 56, "cornerRadius": 28 } },
              {
                "type": "Column",
                "modifier": { "padding": { "start": 16 } },
                "children": [
                  { "type": "Text", "attributes": { "text": "Alex Johnson", "fontSize": "18", "fontWeight": "medium" } },
                  { "type": "Text", "attributes": { "text": "alex.johnson@dev.io", "fontSize": "13", "color": "#FF999999" }, "modifier": { "padding": { "top": 4 } } }
                ]
              }
            ]
          }
        ]
      }
    ]
  }
}
""".trimIndent()
    ),
    PageTemplate(
        name = "Settings List",
        description = "Scrollable settings with toggle switches",
        componentCount = 9,
        dsl = """
{
  "version": "1.0",
  "theme": { "primaryColor": "#FF00897B", "backgroundColor": "#FFFFFFFF", "surfaceColor": "#FFF5F5F5", "textColor": "#FF212121", "isDark": false },
  "root": {
    "type": "Column",
    "modifier": { "fillMaxWidth": true, "fillMaxHeight": true, "padding": { "all": 16 } },
    "children": [
      { "type": "Text", "attributes": { "text": "Settings", "fontSize": "22", "fontWeight": "bold" }, "modifier": { "padding": { "bottom": 16 } } },
      {
        "type": "LazyColumn",
        "modifier": { "fillMaxWidth": true },
        "children": [
          { "type": "Switch", "attributes": { "label": "Dark Mode", "checked": "false" }, "modifier": { "fillMaxWidth": true, "padding": { "vertical": 8 } } },
          { "type": "Divider", "modifier": { "padding": { "vertical": 4 } } },
          { "type": "Switch", "attributes": { "label": "Notifications", "checked": "true" }, "modifier": { "fillMaxWidth": true, "padding": { "vertical": 8 } } },
          { "type": "Divider", "modifier": { "padding": { "vertical": 4 } } },
          { "type": "Switch", "attributes": { "label": "Auto-sync", "checked": "true" }, "modifier": { "fillMaxWidth": true, "padding": { "vertical": 8 } } },
          { "type": "Divider", "modifier": { "vertical": 4 } } },
          { "type": "Switch", "attributes": { "label": "Analytics", "checked": "false" }, "modifier": { "fillMaxWidth": true, "padding": { "vertical": 8 } } }
        ]
      }
    ]
  }
}
""".trimIndent()
    ),
    PageTemplate(
        name = "Dashboard",
        description = "Stats cards + progress indicator",
        componentCount = 8,
        dsl = """
{
  "version": "1.0",
  "theme": { "primaryColor": "#FF5C6BC0", "backgroundColor": "#FFF8F9FA", "surfaceColor": "#FFFFFFFF", "textColor": "#FF1A1A2E", "isDark": false },
  "root": {
    "type": "Column",
    "modifier": { "fillMaxWidth": true, "padding": { "all": 16 } },
    "children": [
      { "type": "Text", "attributes": { "text": "Dashboard", "fontSize": "22", "fontWeight": "bold" }, "modifier": { "padding": { "bottom": 16 } } },
      {
        "type": "Row",
        "modifier": { "fillMaxWidth": true, "padding": { "bottom": 12 }, "horizontalArrangement": "spacebetween" },
        "children": [
          {
            "type": "Card",
            "modifier": { "width": 170, "cornerRadius": 12, "elevation": 2 },
            "children": [
              { "type": "Column", "modifier": { "padding": { "all": 16 } }, "children": [
                { "type": "Text", "attributes": { "text": "Users", "fontSize": "12", "color": "#FF888888" } },
                { "type": "Text", "attributes": { "text": "1,234", "fontSize": "24", "fontWeight": "bold" }, "modifier": { "padding": { "top": 4 } } }
              ]}
            ]
          },
          {
            "type": "Card",
            "modifier": { "width": 170, "cornerRadius": 12, "elevation": 2 },
            "children": [
              { "type": "Column", "modifier": { "padding": { "all": 16 } }, "children": [
                { "type": "Text", "attributes": { "text": "Revenue", "fontSize": "12", "color": "#FF888888" } },
                { "type": "Text", "attributes": { "text": "$45.2K", "fontSize": "24", "fontWeight": "bold" }, "modifier": { "padding": { "top": 4 } } }
              ]}
            ]
          }
        ]
      },
      { "type": "Text", "attributes": { "text": "Loading progress: 72%", "fontSize": "13", "color": "#FF666666" }, "modifier": { "padding": { "bottom": 8 } } },
      { "type": "ProgressIndicator", "attributes": { "progress": "0.72" }, "modifier": { "fillMaxWidth": true } }
    ]
  }
}
""".trimIndent()
    ),
    PageTemplate(
        name = "Chat Bubbles",
        description = "Message list with sent/received bubbles",
        componentCount = 9,
        dsl = """
{
  "version": "1.0",
  "theme": { "primaryColor": "#FF2196F3", "backgroundColor": "#FFECEFF1", "surfaceColor": "#FFFFFFFF", "textColor": "#FF212121", "isDark": false },
  "root": {
    "type": "Column",
    "modifier": { "fillMaxWidth": true, "fillMaxHeight": true, "padding": { "all": 12 } },
    "children": [
      {
        "type": "Row",
        "modifier": { "fillMaxWidth": true, "padding": { "bottom": 8 } },
        "children": [
          { "type": "Box", "modifier": { "width": 220, "background": "#FFFFFFFF", "cornerRadius": 12, "padding": { "all": 12 } }, "children": [
            { "type": "Column", "children": [
              { "type": "Text", "attributes": { "text": "Hey! How's the project going?", "fontSize": "14" } },
              { "type": "Text", "attributes": { "text": "10:30 AM", "fontSize": "10", "color": "#FFAAAAAA" }, "modifier": { "padding": { "top": 4 } } }
            ]}
          ]}
        ]
      },
      {
        "type": "Row",
        "modifier": { "fillMaxWidth": true, "padding": { "bottom": 8 }, "horizontalArrangement": "end" },
        "children": [
          { "type": "Box", "modifier": { "width": 200, "background": "#FF2196F3", "cornerRadius": 12, "padding": { "all": 12 } }, "children": [
            { "type": "Column", "children": [
              { "type": "Text", "attributes": { "text": "Going great! Almost done with v1.8", "fontSize": "14", "color": "#FFFFFFFF" } },
              { "type": "Text", "attributes": { "text": "10:32 AM", "fontSize": "10", "color": "#CCFFFFFF" }, "modifier": { "padding": { "top": 4 } } }
            ]}
          ]}
        ]
      },
      {
        "type": "Row",
        "modifier": { "fillMaxWidth": true, "padding": { "bottom": 8 } },
        "children": [
          { "type": "Box", "modifier": { "width": 180, "background": "#FFFFFFFF", "cornerRadius": 12, "padding": { "all": 12 } }, "children": [
            { "type": "Column", "children": [
              { "type": "Text", "attributes": { "text": "Nice! Can't wait to see it 🚀", "fontSize": "14" } },
              { "type": "Text", "attributes": { "text": "10:33 AM", "fontSize": "10", "color": "#FFAAAAAA" }, "modifier": { "padding": { "top": 4 } } }
            ]}
          ]}
        ]
      }
    ]
  }
}
""".trimIndent()
    )
)

private val COMPONENT_TEMPLATES = listOf(
    ComponentTemplate(
        name = "Text",
        description = "Basic text label",
        dsl = """
        {
          "type": "Text",
          "attributes": { "text": "New Text", "fontSize": "16" },
          "modifier": { "padding": { "bottom": 8 } }
        }
        """.trimIndent()
    ),
    ComponentTemplate(
        name = "Button",
        description = "Clickable button",
        dsl = """
        {
          "type": "Button",
          "attributes": { "text": "Click Me" },
          "modifier": { "padding": { "bottom": 8 } }
        }
        """.trimIndent()
    ),
    ComponentTemplate(
        name = "Card",
        description = "Elevated card container",
        dsl = """
        {
          "type": "Card",
          "modifier": { "fillMaxWidth": true, "cornerRadius": 12, "padding": { "bottom": 8 } },
          "children": [
            {
              "type": "Text",
              "attributes": { "text": "Card Content", "fontSize": "14" },
              "modifier": { "padding": { "all": 16 } }
            }
          ]
        }
        """.trimIndent()
    ),
    ComponentTemplate(
        name = "Row",
        description = "Horizontal layout",
        dsl = """
        {
          "type": "Row",
          "modifier": { "fillMaxWidth": true, "padding": { "bottom": 8 } },
          "children": [
            { "type": "Text", "attributes": { "text": "Left" } },
            { "type": "Spacer", "modifier": { "width": 8 } },
            { "type": "Text", "attributes": { "text": "Right" } }
          ]
        }
        """.trimIndent()
    ),
    ComponentTemplate(
        name = "Column",
        description = "Vertical layout",
        dsl = """
        {
          "type": "Column",
          "modifier": { "fillMaxWidth": true, "padding": { "bottom": 8 } },
          "children": [
            { "type": "Text", "attributes": { "text": "Item 1" } },
            { "type": "Text", "attributes": { "text": "Item 2" } }
          ]
        }
        """.trimIndent()
    ),
    ComponentTemplate(
        name = "Box",
        description = "Overlay / stack layout",
        dsl = """
        {
          "type": "Box",
          "modifier": { "width": 100, "height": 100, "background": "#FFE3F2FD", "cornerRadius": 8, "padding": { "bottom": 8 } },
          "children": [
            { "type": "Text", "attributes": { "text": "Centered", "fontSize": "12" } }
          ]
        }
        """.trimIndent()
    ),
    ComponentTemplate(
        name = "TextField",
        description = "Text input field",
        dsl = """
        {
          "type": "TextField",
          "attributes": { "label": "Input", "placeholder": "Type here..." },
          "modifier": { "fillMaxWidth": true, "padding": { "bottom": 8 } }
        }
        """.trimIndent()
    ),
    ComponentTemplate(
        name = "Image Placeholder",
        description = "Image placeholder box",
        dsl = """
        {
          "type": "Image",
          "attributes": { "contentDescription": "Photo" },
          "modifier": { "width": 120, "height": 80, "cornerRadius": 8, "padding": { "bottom": 8 } }
        }
        """.trimIndent()
    ),
    ComponentTemplate(
        name = "Divider",
        description = "Horizontal separator line",
        dsl = """
        {
          "type": "Divider",
          "modifier": { "padding": { "bottom": 8 } }
        }
        """.trimIndent()
    ),
    ComponentTemplate(
        name = "Spacer",
        description = "Empty spacing element",
        dsl = """
        {
          "type": "Spacer",
          "modifier": { "height": 16 }
        }
        """.trimIndent()
    ),
    ComponentTemplate(
        name = "Scroll Container",
        description = "Scrollable vertical container",
        dsl = """
        {
          "type": "Scroll",
          "modifier": { "fillMaxWidth": true, "height": 200, "padding": { "bottom": 8 } },
          "children": [
            { "type": "Text", "attributes": { "text": "Scrollable content" } }
          ]
        }
        """.trimIndent()
    ),
    ComponentTemplate(
        name = "Surface",
        description = "Themed surface container",
        dsl = """
        {
          "type": "Surface",
          "modifier": { "fillMaxWidth": true, "cornerRadius": 8, "padding": { "bottom": 8 } },
          "children": [
            { "type": "Text", "attributes": { "text": "Surface content" }, "modifier": { "padding": { "all": 12 } } }
          ]
        }
        """.trimIndent()
    ),
    ComponentTemplate(
        name = "LazyColumn",
        description = "Scrollable list container",
        dsl = """
        {
          "type": "LazyColumn",
          "modifier": { "fillMaxWidth": true, "height": 200, "padding": { "bottom": 8 } },
          "children": [
            { "type": "Text", "attributes": { "text": "Item 1" }, "modifier": { "padding": { "all": 8 } } },
            { "type": "Text", "attributes": { "text": "Item 2" }, "modifier": { "padding": { "all": 8 } } },
            { "type": "Text", "attributes": { "text": "Item 3" }, "modifier": { "padding": { "all": 8 } } }
          ]
        }
        """.trimIndent()
    ),
    ComponentTemplate(
        name = "Switch",
        description = "Toggle switch with label",
        dsl = """
        {
          "type": "Switch",
          "attributes": { "label": "Enable feature", "checked": "false" },
          "modifier": { "fillMaxWidth": true, "padding": { "bottom": 8 } }
        }
        """.trimIndent()
    ),
    ComponentTemplate(
        name = "Checkbox",
        description = "Checkbox with label",
        dsl = """
        {
          "type": "Checkbox",
          "attributes": { "label": "Accept terms", "checked": "false" },
          "modifier": { "padding": { "bottom": 8 } }
        }
        """.trimIndent()
    ),
    ComponentTemplate(
        name = "Slider",
        description = "Value slider with range",
        dsl = """
        {
          "type": "Slider",
          "attributes": { "min": "0", "max": "100", "value": "50" },
          "modifier": { "fillMaxWidth": true, "padding": { "bottom": 8 } }
        }
        """.trimIndent()
    ),
    ComponentTemplate(
        name = "ProgressIndicator",
        description = "Loading/progress bar",
        dsl = """
        {
          "type": "ProgressIndicator",
          "attributes": { "progress": "0.6" },
          "modifier": { "fillMaxWidth": true, "padding": { "bottom": 8 } }
        }
        """.trimIndent()
    ),
    ComponentTemplate(
        name = "TabRow",
        description = "Tab bar with switchable content",
        dsl = """
        {
          "type": "TabRow",
          "modifier": { "fillMaxWidth": true, "padding": { "bottom": 8 } },
          "children": [
            {
              "type": "Box",
              "attributes": { "text": "Tab 1" },
              "children": [
                { "type": "Text", "attributes": { "text": "Content 1" } }
              ]
            },
            {
              "type": "Box",
              "attributes": { "text": "Tab 2" },
              "children": [
                { "type": "Text", "attributes": { "text": "Content 2" } }
              ]
            }
          ]
        }
        """.trimIndent()
    ),
    ComponentTemplate(
        name = "Gradient Card",
        description = "Card with gradient background",
        dsl = """
        {
          "type": "Card",
          "modifier": { "fillMaxWidth": true, "cornerRadius": 16, "padding": { "bottom": 8 } },
          "children": [
            {
              "type": "Column",
              "modifier": { "fillMaxWidth": true, "background": "gradient:sunset", "cornerRadius": 16, "padding": { "all": 24 } },
              "children": [
                { "type": "Text", "attributes": { "text": "Gradient Card", "fontSize": "20", "fontWeight": "bold", "color": "#FFFFFFFF" } },
                { "type": "Text", "attributes": { "text": "linear / radial / conic / preset", "fontSize": "13", "color": "#CCFFFFFF" }, "modifier": { "padding": { "top": 4 } } }
              ]
            }
          ]
        }
        """.trimIndent()
    ),
    ComponentTemplate(
        name = "Animated Text",
        description = "Text with fade-in animation",
        dsl = """
        {
          "type": "Text",
          "attributes": { "text": "Animated!", "fontSize": "24", "fontWeight": "bold", "animation.type": "fade_in", "animation.duration": "800" },
          "modifier": { "padding": { "bottom": 8 } }
        }
        """.trimIndent()
    )
)

@Composable
fun ComponentsScreen(viewModel: FoundryViewModel) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        item {
            Text(
                text = "Component Palette",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "Tap a component to insert its DSL into the editor",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        items(COMPONENT_TEMPLATES) { template ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable { viewModel.insertComponent(template.dsl) },
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = template.name,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = template.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            Text(
                text = "Page Templates",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
            )
            Text(
                text = "Tap a template to replace current document",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        items(PAGE_TEMPLATES) { template ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable { viewModel.loadTemplate(template.dsl) },
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = template.name,
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "${template.componentCount} elements",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = template.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
    }
}
