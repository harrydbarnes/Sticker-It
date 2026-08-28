package com.stickerit.app.ui.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.unit.dp
import com.stickerit.app.R
import com.stickerit.app.data.model.FinishBackgroundType
import com.stickerit.app.data.model.FinishRecipe
import androidx.compose.ui.res.stringResource

private data class FinishColorChoice(
    val value: Int,
    val labelRes: Int,
)

private val FINISH_COLORS = listOf(
    FinishColorChoice(0xFFFFFFFF.toInt(), R.string.finish_white),
    FinishColorChoice(0xFF111111.toInt(), R.string.finish_black),
    FinishColorChoice(0xFFFF8A72.toInt(), R.string.finish_coral),
    FinishColorChoice(0xFFFFD4C4.toInt(), R.string.finish_peach),
    FinishColorChoice(0xFFB9E7F7.toInt(), R.string.finish_blue),
    FinishColorChoice(0xFFD8C8FF.toInt(), R.string.finish_lavender),
    FinishColorChoice(0xFFC5E8C5.toInt(), R.string.finish_green),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinishStudioPanel(
    recipe: FinishRecipe,
    onRecipeChange: (FinishRecipe) -> Unit,
    onPickBackground: () -> Unit,
    onClearBackground: () -> Unit = {},
    onBackToBrush: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .heightIn(max = 520.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = stringResource(R.string.finish_sticker_title),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = stringResource(R.string.finish_sticker_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        HorizontalDivider()

        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.large,
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.finish_outline),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            text = stringResource(R.string.finish_outline_help),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    val outlineStateDescription = stringResource(
                        if (recipe.outlineEnabled) R.string.selected else R.string.not_selected,
                    )
                    Switch(
                        checked = recipe.outlineEnabled,
                        onCheckedChange = {
                            onRecipeChange(recipe.copy(outlineEnabled = it))
                        },
                        modifier = Modifier.semantics {
                            stateDescription = outlineStateDescription
                        },
                    )
                }

                if (recipe.outlineEnabled) {
                    Text(
                        text = stringResource(R.string.outline_color),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    FinishColorSwatches(
                        selectedColor = recipe.outlineColor,
                        onColorSelected = { onRecipeChange(recipe.copy(outlineColor = it)) },
                    )
                    Text(
                        text = stringResource(R.string.outline_width),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    val outlineWidthDescription = stringResource(R.string.outline_width)
                    Slider(
                        value = recipe.outlineWidth,
                        onValueChange = {
                            onRecipeChange(recipe.copy(outlineWidth = it))
                        },
                        valueRange = 2f..28f,
                        modifier = Modifier.semantics {
                            contentDescription = outlineWidthDescription
                        },
                    )
                }
            }
        }

        Text(
            text = stringResource(R.string.background),
            style = MaterialTheme.typography.titleSmall,
        )
        BackgroundTypeChips(
            selected = recipe.backgroundType,
            onSelected = { type ->
                onRecipeChange(
                    recipe.copy(
                        backgroundType = type,
                        backgroundImagePath = if (type == FinishBackgroundType.IMAGE) {
                            recipe.backgroundImagePath
                        } else {
                            null
                        },
                    )
                )
            },
        )

        when (recipe.backgroundType) {
            FinishBackgroundType.SOLID -> {
                Text(
                    text = stringResource(R.string.background_solid),
                    style = MaterialTheme.typography.labelMedium,
                )
                FinishColorSwatches(
                    selectedColor = recipe.backgroundPrimaryColor,
                    onColorSelected = {
                        onRecipeChange(recipe.copy(backgroundPrimaryColor = it))
                    },
                )
            }

            FinishBackgroundType.GRADIENT -> {
                Text(
                    text = stringResource(R.string.finish_gradient_start),
                    style = MaterialTheme.typography.labelMedium,
                )
                FinishColorSwatches(
                    selectedColor = recipe.backgroundPrimaryColor,
                    onColorSelected = {
                        onRecipeChange(recipe.copy(backgroundPrimaryColor = it))
                    },
                )
                Text(
                    text = stringResource(R.string.finish_gradient_end),
                    style = MaterialTheme.typography.labelMedium,
                )
                FinishColorSwatches(
                    selectedColor = recipe.backgroundSecondaryColor,
                    onColorSelected = {
                        onRecipeChange(recipe.copy(backgroundSecondaryColor = it))
                    },
                )
            }

            FinishBackgroundType.IMAGE -> {
                OutlinedButton(
                    onClick = onPickBackground,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.choose_background_image))
                }
                if (recipe.backgroundImagePath != null) {
                    Text(
                        text = stringResource(R.string.background_image_selected),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            FinishBackgroundType.TRANSPARENT -> Unit
        }

        if (recipe.backgroundType != FinishBackgroundType.TRANSPARENT) {
            OutlinedButton(
                onClick = onClearBackground,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.remove_background))
            }
        }

        HorizontalDivider()

        Text(
            text = stringResource(R.string.transform),
            style = MaterialTheme.typography.titleSmall,
        )
        Text(stringResource(R.string.scale), style = MaterialTheme.typography.labelMedium)
        val scaleDescription = stringResource(R.string.scale)
        Slider(
            value = recipe.scale,
            onValueChange = { onRecipeChange(recipe.copy(scale = it)) },
            valueRange = 0.55f..1.35f,
            modifier = Modifier.semantics {
                contentDescription = scaleDescription
            },
        )
        Text(
            text = stringResource(R.string.horizontal_position),
            style = MaterialTheme.typography.labelMedium,
        )
        val horizontalPositionDescription = stringResource(R.string.horizontal_position)
        Slider(
            value = recipe.offsetX,
            onValueChange = { onRecipeChange(recipe.copy(offsetX = it)) },
            valueRange = -0.45f..0.45f,
            modifier = Modifier.semantics {
                contentDescription = horizontalPositionDescription
            },
        )
        Text(
            text = stringResource(R.string.vertical_position),
            style = MaterialTheme.typography.labelMedium,
        )
        val verticalPositionDescription = stringResource(R.string.vertical_position)
        Slider(
            value = recipe.offsetY,
            onValueChange = { onRecipeChange(recipe.copy(offsetY = it)) },
            valueRange = -0.45f..0.45f,
            modifier = Modifier.semantics {
                contentDescription = verticalPositionDescription
            },
        )

        Text(
            text = stringResource(R.string.overlay_text),
            style = MaterialTheme.typography.titleSmall,
        )
        OutlinedTextField(
            value = recipe.text,
            onValueChange = { onRecipeChange(recipe.copy(text = it.take(32))) },
            label = { Text(stringResource(R.string.sticker_text_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = recipe.emoji,
            onValueChange = { onRecipeChange(recipe.copy(emoji = it.take(4))) },
            label = { Text(stringResource(R.string.emoji_overlay)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(2.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = onBackToBrush,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.back_to_brush))
            }
            Button(
                onClick = onSave,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.finish_save))
            }
        }
    }
}

@Composable
private fun BackgroundTypeChips(
    selected: FinishBackgroundType,
    onSelected: (FinishBackgroundType) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FinishBackgroundType.entries.forEach { type ->
            FilterChip(
                selected = selected == type,
                onClick = { onSelected(type) },
                label = {
                    Text(
                        stringResource(
                            when (type) {
                                FinishBackgroundType.TRANSPARENT -> R.string.background_transparent
                                FinishBackgroundType.SOLID -> R.string.background_solid
                                FinishBackgroundType.GRADIENT -> R.string.background_gradient
                                FinishBackgroundType.IMAGE -> R.string.background_image
                            }
                        )
                    )
                },
            )
        }
    }
}

@Composable
private fun FinishColorSwatches(
    selectedColor: Int,
    onColorSelected: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FINISH_COLORS.forEach { choice ->
            val selected = selectedColor == choice.value
            val colorDescription = stringResource(choice.labelRes)
            val selectionDescription = stringResource(
                if (selected) R.string.selected else R.string.not_selected,
            )
            Surface(
                onClick = { onColorSelected(choice.value) },
                modifier = Modifier
                    .size(40.dp)
                    .semantics {
                        contentDescription = colorDescription
                        stateDescription = selectionDescription
                    }
                    .border(
                        border = BorderStroke(
                            width = if (selected) 3.dp else 1.dp,
                            color = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outline
                            },
                        ),
                        shape = CircleShape,
                    ),
                shape = CircleShape,
                color = Color(choice.value),
            ) { }
        }
    }
}
