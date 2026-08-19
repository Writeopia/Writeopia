package io.writeopia.auth.register

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.writeopia.auth.utils.PasswordStrength
import io.writeopia.auth.utils.PasswordValidationResult
import io.writeopia.common.utils.icons.WrIcons
import io.writeopia.resources.WrStrings

@Composable
fun PasswordStrengthIndicator(
    validationResult: PasswordValidationResult,
    password: String,
    modifier: Modifier = Modifier
) {
    if (password.isEmpty()) {
        return
    }

    val strength = validationResult.strength
    val segmentShape = RoundedCornerShape(4.dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            StrengthSegment(
                filled = strength.ordinal >= PasswordStrength.WEAK.ordinal,
                color = getColorForStrength(strength),
                modifier = Modifier.weight(1f),
                shape = segmentShape
            )
            StrengthSegment(
                filled = strength.ordinal >= PasswordStrength.MEDIUM.ordinal,
                color = getColorForStrength(strength),
                modifier = Modifier.weight(1f),
                shape = segmentShape
            )
            StrengthSegment(
                filled = strength.ordinal >= PasswordStrength.STRONG.ordinal,
                color = getColorForStrength(strength),
                modifier = Modifier.weight(1f),
                shape = segmentShape
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = getStrengthLabel(strength),
            style = MaterialTheme.typography.bodySmall,
            color = getColorForStrength(strength),
            modifier = Modifier.align(Alignment.Start)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Password requirements
        PasswordRequirement(
            text = WrStrings.passwordReqMinLength(),
            isMet = validationResult.hasMinLength
        )
        PasswordRequirement(
            text = WrStrings.passwordReqSpecialChar(),
            isMet = validationResult.hasSpecialChar
        )
    }
}

@Composable
private fun PasswordRequirement(
    text: String,
    isMet: Boolean,
    modifier: Modifier = Modifier
) {
    val color = if (isMet) Color(0xFF43A047) else Color.Gray

    Row(
        modifier = modifier.padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = if (isMet) WrIcons.check else WrIcons.close,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = color
        )
    }
}

@Composable
private fun StrengthSegment(
    filled: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape
) {
    val animatedColor by animateColorAsState(
        targetValue = if (filled) color else Color.Gray.copy(alpha = 0.3f),
        animationSpec = tween(durationMillis = 300)
    )

    Box(
        modifier = modifier
            .height(4.dp)
            .clip(shape)
            .background(animatedColor)
    )
}

@Composable
private fun getColorForStrength(strength: PasswordStrength): Color {
    return when (strength) {
        PasswordStrength.NONE -> Color(0xFFE53935) // Red for NONE too when typing
        PasswordStrength.WEAK -> Color(0xFFE53935) // Red
        PasswordStrength.MEDIUM -> Color(0xFFFB8C00) // Orange
        PasswordStrength.STRONG -> Color(0xFF43A047) // Green
    }
}

@Composable
private fun getStrengthLabel(strength: PasswordStrength): String {
    return when (strength) {
        PasswordStrength.NONE -> WrStrings.passwordWeak()
        PasswordStrength.WEAK -> WrStrings.passwordWeak()
        PasswordStrength.MEDIUM -> WrStrings.passwordMedium()
        PasswordStrength.STRONG -> WrStrings.passwordStrong()
    }
}
