package io.writeopia.commonui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.writeopia.theme.WriteopiaTheme

@Composable
fun SettingsPanel(
    accountScreen: @Composable () -> Unit,
    appearanceScreen: @Composable () -> Unit,
    directoryScreen: @Composable () -> Unit,
    aiScreen: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    var pageState by remember {
        mutableStateOf(SettingsPage.APPEARANCE)
    }

    Row(modifier = modifier) {
        Column(modifier = Modifier.width(180.dp)) {
//            Text(
//                "Account",
//                modifier = Modifier.fillMaxWidth()
//                    .padding(top = 2.dp, bottom = 2.dp, end = 16.dp)
//                    .clip(MaterialTheme.shapes.medium)
//                    .background(
//                        if (pageState == SettingsPage.ACCOUNT) {
//                            WriteopiaTheme.colorScheme.highlight
//                        } else {
//                            Color.Unspecified
//                        }
//                    )
//                    .clickable {
//                        pageState = SettingsPage.ACCOUNT
//                    }.padding(vertical = 4.dp, horizontal = 12.dp)
//            )

            Text(
                "Appearance",
                modifier = Modifier.fillMaxWidth()
                    .padding(top = 2.dp, bottom = 2.dp, end = 16.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(
                        if (pageState == SettingsPage.APPEARANCE) {
                            WriteopiaTheme.colorScheme.highlight
                        } else {
                            Color.Unspecified
                        }
                    )
                    .clickable {
                        pageState = SettingsPage.APPEARANCE
                    }.padding(vertical = 4.dp, horizontal = 12.dp)
            )

            Text(
                "AI",
                modifier = Modifier.fillMaxWidth()
                    .padding(top = 2.dp, bottom = 2.dp, end = 16.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(
                        if (pageState == SettingsPage.AI) {
                            WriteopiaTheme.colorScheme.highlight
                        } else {
                            Color.Unspecified
                        }
                    )
                    .clickable {
                        pageState = SettingsPage.AI
                    }.padding(vertical = 4.dp, horizontal = 12.dp)
            )

            Text(
                "Directory",
                modifier = Modifier.fillMaxWidth()
                    .padding(top = 2.dp, bottom = 2.dp, end = 16.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(
                        if (pageState == SettingsPage.DIRECTORY) {
                            WriteopiaTheme.colorScheme.highlight
                        } else {
                            Color.Unspecified
                        }
                    )
                    .clickable {
                        pageState = SettingsPage.DIRECTORY
                    }.padding(vertical = 4.dp, horizontal = 12.dp)
            )

        }

        VerticalDivider(modifier = Modifier.fillMaxHeight(), thickness = 1.dp, color = Color.Blue)

        Crossfade(pageState) { page ->
            when (page) {
                SettingsPage.ACCOUNT -> {
                    accountScreen()
                }

                SettingsPage.APPEARANCE -> {
                    appearanceScreen()
                }

                SettingsPage.DIRECTORY -> {
                    directoryScreen()
                }

                SettingsPage.AI -> {
                    aiScreen()
                }
            }
        }
    }
}

enum class SettingsPage {
    ACCOUNT, APPEARANCE, DIRECTORY, AI
}
