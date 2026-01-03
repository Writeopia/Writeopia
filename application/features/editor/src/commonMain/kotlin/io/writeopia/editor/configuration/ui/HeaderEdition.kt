package io.writeopia.editor.configuration.ui

// import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import io.writeopia.commonui.SlideInBox
import io.writeopia.theme.WriteopiaTheme

// This screen could live in a module for extra Composables
@Composable
fun BoxScope.HeaderEdition(
    modifier: Modifier = Modifier,
    availableColors: List<Int>,
    onColorSelection: (Int?) -> Unit,
    visibilityState: Boolean,
    outsideClick: () -> Unit,
) {
    val topCorner = CornerSize(16.dp)
    val bottomCorner = CornerSize(0.dp)

    SlideInBox(
        modifier = modifier.align(Alignment.BottomCenter),
        editState = visibilityState,
        outsideClick = outsideClick,
        animationLabel = "HeaderEditionAnimation",
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 500.dp)
                .clip(
                    RoundedCornerShape(
                        topCorner,
                        topCorner,
                        bottomCorner,
                        bottomCorner
                    )
                )
                .background(WriteopiaTheme.colorScheme.lightBackground)
                .padding(vertical = 16.dp)
        ) {
            HeaderEditionOptions(availableColors, onColorSelection)

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

// @Preview
// @Composable
// fun HeaderEditionPreview() {
//    val colors = listOf(
//        Color.Blue.toArgb(),
//        Color.White.toArgb(),
//        Color.Cyan.toArgb(),
//        Color.LightGray.toArgb(),
//        Color.Red.toArgb(),
//    )
//
//    Box(modifier = Modifier.background(Color.White)) {
//        HeaderEdition(
//            availableColors = colors,
//            onColorSelection = {},
//            outsideClick = {},
//            visibilityState = true
//        )
//    }
// }
