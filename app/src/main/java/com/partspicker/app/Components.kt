package com.partspicker.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val PillShape = RoundedCornerShape(50)
val RadiusS = RoundedCornerShape(14.dp)
val Radius = RoundedCornerShape(22.dp)
val RadiusXl = RoundedCornerShape(32.dp)

fun Modifier.lift(shape: Shape, elevation: Dp = 4.dp): Modifier =
    this.shadow(
        elevation = elevation,
        shape = shape,
        clip = false,
        ambientColor = Color(0x203A2C14),
        spotColor = Color(0x303A2C14)
    )

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier,
        color = LocalAppColors.current.muted,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.5.sp
    )
}

@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier) {
    val c = LocalAppColors.current
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(c.accent))
        SectionLabel(text)
    }
}

@Composable
fun StatusText(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    weight: FontWeight = FontWeight.SemiBold
) {
    if (text.isNotEmpty()) {
        Text(
            text,
            modifier.padding(horizontal = 6.dp),
            color = color,
            fontSize = 13.sp,
            fontWeight = weight
        )
    }
}

@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    container: Color = LocalAppColors.current.accent,
    content: Color = LocalAppColors.current.onAccent,
    enabled: Boolean = true,
    height: Dp = 52.dp,
    fontSize: TextUnit = 16.sp,
    horizontalPadding: Dp = 24.dp
) {
    val c = LocalAppColors.current
    val bg = if (enabled) container else c.surface2
    val fg = if (enabled) content else c.muted
    Box(
        modifier
            .height(height)
            .defaultMinSize(minWidth = 52.dp)
            .clip(PillShape)
            .background(bg)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = horizontalPadding),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = fg, fontSize = fontSize, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
fun IconBtn(
    icon: ImageVector,
    desc: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = LocalAppColors.current.text,
    background: Color = Color.Transparent,
    size: Dp = 40.dp
) {
    Box(
        modifier.size(size).clip(CircleShape).background(background).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = desc, tint = tint, modifier = Modifier.size(18.dp))
    }
}

@Composable
fun Chip(text: String) {
    val c = LocalAppColors.current
    Text(
        text,
        Modifier.clip(PillShape).background(c.surface2).padding(horizontal = 14.dp, vertical = 8.dp),
        color = c.text,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipRow(items: List<String>, modifier: Modifier = Modifier) {
    FlowRow(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items.forEach { Chip(it) }
    }
}

@Composable
fun PillField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    height: Dp = 52.dp,
    mono: Boolean = false,
    fontSize: TextUnit = 16.sp,
    fontWeight: FontWeight = FontWeight.Medium,
    background: Color = LocalAppColors.current.surface2,
    shape: Shape = PillShape,
    singleLine: Boolean = true,
    minLines: Int = 1,
    lettersOnly: Boolean = false,
    // Location/part-number codes want the existing force-to-uppercase behavior;
    // free text like names, notes, or URLs must NOT be uppercased (this would
    // corrupt a pasted link, since URL paths are case-sensitive).
    forceUppercase: Boolean = true,
    centered: Boolean = false,
    imeAction: ImeAction = ImeAction.Default,
    onImeAction: (() -> Unit)? = null,
    focusRequester: FocusRequester? = null,
    onFocusChange: ((Boolean) -> Unit)? = null,
    horizontalPadding: Dp = 20.dp,
    leading: (@Composable () -> Unit)? = null
) {
    val c = LocalAppColors.current
    var mod = modifier
        .then(if (singleLine) Modifier.height(height) else Modifier.heightIn(min = height))
        .clip(shape)
        .background(background)
    if (focusRequester != null) mod = mod.focusRequester(focusRequester)
    if (onFocusChange != null) mod = mod.onFocusChanged { onFocusChange(it.isFocused) }

    BasicTextField(
        value = value,
        onValueChange = { raw ->
            var v = raw
            if (lettersOnly) v = v.filter { ch -> ch in 'a'..'z' || ch in 'A'..'Z' }
            onValueChange(if (forceUppercase) v.uppercase() else v)
        },
        modifier = mod,
        singleLine = singleLine,
        minLines = if (singleLine) 1 else minLines,
        textStyle = TextStyle(
            color = c.text,
            fontSize = fontSize,
            fontWeight = fontWeight,
            fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
            textAlign = if (centered) TextAlign.Center else TextAlign.Start
        ),
        cursorBrush = SolidColor(c.accent),
        keyboardOptions = KeyboardOptions(
            capitalization = if (forceUppercase) KeyboardCapitalization.Characters else KeyboardCapitalization.None,
            imeAction = imeAction
        ),
        keyboardActions = KeyboardActions(
            onNext = { onImeAction?.invoke() },
            onDone = { onImeAction?.invoke() }
        ),
        decorationBox = { inner ->
            Row(
                if (singleLine) Modifier.fillMaxSize().padding(horizontal = horizontalPadding)
                else Modifier.fillMaxWidth().padding(horizontal = horizontalPadding, vertical = 14.dp),
                verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top
            ) {
                if (leading != null) {
                    leading()
                    Spacer(Modifier.width(12.dp))
                }
                Box(Modifier.weight(1f), contentAlignment = if (centered) Alignment.Center else Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(
                            placeholder,
                            color = c.muted,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Normal,
                            maxLines = if (singleLine) 1 else Int.MAX_VALUE
                        )
                    }
                    inner()
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val c = LocalAppColors.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        containerColor = c.bg,
        scrimColor = c.scrim,
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 10.dp, bottom = 16.dp)
                    .size(width = 40.dp, height = 5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(c.surface2)
            )
        }
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 18.dp),
            content = content
        )
    }
}
