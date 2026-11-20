package de.koenidv.spheres.ui.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.koenidv.spheres.data.SheetState
import de.koenidv.spheres.data.SpheresStore
import de.koenidv.spheres.domain.AvatarConfig
import de.koenidv.spheres.domain.intervalInfo
import de.koenidv.spheres.platform.decodeImageBitmap
import de.koenidv.spheres.platform.haptic
import de.koenidv.spheres.platform.rememberPhotoPickerLauncher
import de.koenidv.spheres.ui.components.AVATAR_HAIRS
import de.koenidv.spheres.ui.components.AVATAR_SKINS
import de.koenidv.spheres.ui.components.AvatarRender
import de.koenidv.spheres.ui.components.ButtonSize
import de.koenidv.spheres.ui.components.ButtonVariant
import de.koenidv.spheres.ui.components.Field
import de.koenidv.spheres.ui.components.GlassesStyle
import de.koenidv.spheres.ui.components.HairStyle
import de.koenidv.spheres.ui.components.IconName
import de.koenidv.spheres.ui.components.PhotoCropDialog
import de.koenidv.spheres.ui.components.PixelAvatar
import de.koenidv.spheres.ui.components.PixelButton
import de.koenidv.spheres.ui.components.PixelIcon
import de.koenidv.spheres.ui.components.PxClipShape
import de.koenidv.spheres.ui.components.defaultAvatarConfig
import de.koenidv.spheres.ui.components.randomAvatarConfig
import de.koenidv.spheres.ui.theme.CardBg
import de.koenidv.spheres.ui.theme.DangerText
import de.koenidv.spheres.ui.theme.FieldBg
import de.koenidv.spheres.ui.theme.Ink
import de.koenidv.spheres.ui.theme.Muted
import de.koenidv.spheres.ui.theme.PixelFontFamily
import de.koenidv.spheres.ui.theme.SansFontFamily
import de.koenidv.spheres.ui.theme.parseHexColor
import kotlinx.coroutines.launch

private enum class AvatarSheetMode { PIXEL, PHOTO }

@Composable
fun AvatarSheet(sheet: SheetState.AvatarEdit) {
    val state by SpheresStore.state.collectAsState()
    val person = state.people.firstOrNull { it.id == sheet.personId } ?: return
    val scope = rememberCoroutineScope()
    val accentHex = remember(person.intervalDays) { intervalInfo(person.intervalDays).color }
    var saving by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    AvatarEditor(
        stateKey = person.id,
        name = person.name,
        accentHex = accentHex,
        initialAvatar = person.avatar,
        initialPhotoUrl = person.photoUrl,
        saving = saving,
        errorText = errorText,
        onErrorTextChange = { errorText = it },
        saveLabel = if (saving) "Saving…" else "Save",
        onSave = { normalizedAvatar, newBytes, _, willRemovePhoto ->
            val nothingChanged = newBytes == null &&
                normalizedAvatar == person.avatar &&
                ((willRemovePhoto && person.photoUrl == null) ||
                    (!willRemovePhoto && person.photoUrl != null))

            if (nothingChanged) {
                SpheresStore.closeSheet()
            } else {
                scope.launch {
                    saving = true
                    errorText = null
                    val result = SpheresStore.updatePersonAvatar(
                        personId = person.id,
                        avatar = normalizedAvatar,
                        newPhotoBytes = newBytes,
                        removePhoto = willRemovePhoto,
                    )
                    saving = false
                    if (result.isSuccess) {
                        SpheresStore.closeSheet()
                        SpheresStore.showToast("Avatar saved")
                    } else {
                        errorText = result.exceptionOrNull()?.message ?: "Couldn't save avatar"
                    }
                }
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AvatarEditor(
    stateKey: Any,
    name: String,
    accentHex: String,
    initialAvatar: AvatarConfig?,
    initialPhotoUrl: String? = null,
    initialPhotoBytes: ByteArray? = null,
    initialPhotoBitmap: ImageBitmap? = null,
    saving: Boolean = false,
    errorText: String? = null,
    onErrorTextChange: (String?) -> Unit = {},
    saveLabel: String = "Save",
    onSave: (
        normalizedAvatar: AvatarConfig?,
        newPhotoBytes: ByteArray?,
        newPhotoBitmap: ImageBitmap?,
        removePhoto: Boolean
    ) -> Unit,
) {
    val accentColor = remember(accentHex) { parseHexColor(accentHex) }
    val defaultConfig = remember(name) { defaultAvatarConfig(name) }

    var mode by remember(stateKey) {
        mutableStateOf(
            if (!initialPhotoUrl.isNullOrBlank() || initialPhotoBitmap != null) {
                AvatarSheetMode.PHOTO
            } else {
                AvatarSheetMode.PIXEL
            }
        )
    }
    var draftAvatar by remember(stateKey) { mutableStateOf(initialAvatar ?: defaultConfig) }
    var lastNonBaldStyle by remember(stateKey) {
        mutableStateOf(
            (initialAvatar?.hairStyle ?: defaultConfig.hairStyle)
                ?.takeIf { it != HairStyle.BALD.id }
                ?: HairStyle.SHORT.id
        )
    }
    var sourcePhotoBytes by remember(stateKey) { mutableStateOf(initialPhotoBytes) }
    var cropTargetBytes by remember(stateKey) { mutableStateOf<ByteArray?>(null) }
    var pickedPhotoBytes by remember(stateKey) { mutableStateOf(initialPhotoBytes) }
    var pickedPhotoBitmap by remember(stateKey) { mutableStateOf(initialPhotoBitmap) }
    var removePhoto by remember(stateKey) { mutableStateOf(false) }

    val launchPhotoPicker = rememberPhotoPickerLauncher { bytes ->
        haptic()
        sourcePhotoBytes = bytes
        cropTargetBytes = bytes
        onErrorTextChange(null)
    }

    val hasActivePhoto = pickedPhotoBitmap != null || (!removePhoto && !initialPhotoUrl.isNullOrBlank())
    val activeHairStyle = draftAvatar.hairStyle ?: defaultConfig.hairStyle
    val isBald = activeHairStyle == HairStyle.BALD.id
    val isModifiedPixel = draftAvatar != defaultConfig

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(PxClipShape)
                .background(FieldBg)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            ModeTab(
                label = "Pixel",
                icon = IconName.SPARKLE,
                selected = mode == AvatarSheetMode.PIXEL,
                enabled = !saving,
                onClick = {
                    haptic()
                    onErrorTextChange(null)
                    mode = AvatarSheetMode.PIXEL
                },
                modifier = Modifier.weight(1f)
            )
            ModeTab(
                label = "Photo",
                icon = IconName.CAMERA,
                selected = mode == AvatarSheetMode.PHOTO,
                enabled = !saving,
                onClick = {
                    haptic()
                    onErrorTextChange(null)
                    mode = AvatarSheetMode.PHOTO
                },
                modifier = Modifier.weight(1f)
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .background(Color.White)
                    .border(2.dp, accentColor)
                    .padding(5.dp)
                    .then(
                        if (mode == AvatarSheetMode.PHOTO && !saving) {
                            Modifier.clickable {
                                val src = sourcePhotoBytes
                                if (hasActivePhoto && src != null) {
                                    cropTargetBytes = src
                                } else {
                                    launchPhotoPicker()
                                }
                            }
                        } else {
                            Modifier
                        }
                    )
            ) {
                if (mode == AvatarSheetMode.PIXEL) {
                    PixelAvatar(
                        name = name,
                        colorHex = accentHex,
                        size = 104.dp,
                        config = draftAvatar
                    )
                } else if (hasActivePhoto) {
                    AvatarRender(
                        name = name,
                        colorHex = accentHex,
                        config = draftAvatar,
                        photoUrl = if (removePhoto) null else initialPhotoUrl,
                        photoOverride = pickedPhotoBitmap,
                        size = 104.dp
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(104.dp)
                            .clip(PxClipShape)
                            .background(CardBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            PixelIcon(
                                name = IconName.CAMERA,
                                size = 28.dp,
                                tint = Muted
                            )
                            Text(
                                text = "Tap to pick",
                                style = TextStyle(
                                    fontFamily = PixelFontFamily(),
                                    fontSize = 13.sp,
                                    color = Muted
                                )
                            )
                        }
                    }
                }
            }

            if (mode == AvatarSheetMode.PIXEL) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .height(32.dp)
                            .clip(PxClipShape)
                            .background(FieldBg)
                            .clickable(enabled = !saving) {
                                haptic()
                                val random = randomAvatarConfig()
                                draftAvatar = random
                                if (random.hairStyle != null && random.hairStyle != HairStyle.BALD.id) {
                                    lastNonBaldStyle = random.hairStyle
                                }
                            }
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        PixelIcon(name = IconName.SPARKLE, size = 14.dp, tint = Ink)
                        Text(
                            text = "Shuffle",
                            style = TextStyle(
                                fontFamily = PixelFontFamily(),
                                fontSize = 14.sp,
                                color = Ink
                            )
                        )
                    }

                    if (isModifiedPixel) {
                        Row(
                            modifier = Modifier
                                .height(32.dp)
                                .clip(PxClipShape)
                                .background(FieldBg)
                                .clickable(enabled = !saving) {
                                    haptic()
                                    draftAvatar = defaultConfig
                                    if (defaultConfig.hairStyle != null && defaultConfig.hairStyle != HairStyle.BALD.id) {
                                        lastNonBaldStyle = defaultConfig.hairStyle
                                    }
                                }
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Reset",
                                style = TextStyle(
                                    fontFamily = PixelFontFamily(),
                                    fontSize = 14.sp,
                                    color = Ink.copy(alpha = 0.7f)
                                )
                            )
                        }
                    }
                }
            }
        }

        if (mode == AvatarSheetMode.PIXEL) {
            Field(label = "Skin") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val selectedSkin = draftAvatar.skin ?: defaultConfig.skin ?: 0
                    AVATAR_SKINS.forEachIndexed { idx, color ->
                        ColorSwatch(
                            color = color,
                            selected = selectedSkin == idx,
                            enabled = !saving,
                            onClick = {
                                haptic()
                                draftAvatar = draftAvatar.copy(skin = idx)
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Field(label = "Hair") {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HairStyle.entries.forEach { style ->
                        val selected = activeHairStyle == style.id
                        OptionChip(
                            label = style.label,
                            selected = selected,
                            enabled = !saving,
                            onClick = {
                                haptic()
                                if (style != HairStyle.BALD) {
                                    lastNonBaldStyle = style.id
                                }
                                draftAvatar = draftAvatar.copy(hairStyle = style.id)
                            }
                        )
                    }
                }
            }

            Field(label = "Hair color") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(if (isBald) 0.45f else 1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val selectedHairColor = draftAvatar.hairColor ?: defaultConfig.hairColor ?: 0
                    AVATAR_HAIRS.forEachIndexed { idx, color ->
                        ColorSwatch(
                            color = color,
                            selected = selectedHairColor == idx,
                            enabled = !saving,
                            onClick = {
                                haptic()
                                draftAvatar = draftAvatar.copy(
                                    hairColor = idx,
                                    hairStyle = if (isBald) lastNonBaldStyle else draftAvatar.hairStyle
                                )
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Field(label = "Eyewear") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val selectedGlasses = draftAvatar.glasses ?: defaultConfig.glasses ?: GlassesStyle.NONE.id
                    GlassesStyle.entries.forEach { style ->
                        OptionChip(
                            label = style.label,
                            selected = selectedGlasses == style.id,
                            enabled = !saving,
                            onClick = {
                                haptic()
                                draftAvatar = draftAvatar.copy(glasses = style.id)
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = if (hasActivePhoto) {
                        "Square-cropped and synced across your devices."
                    } else {
                        "Pick a photo from your library to show instead of the pixel avatar."
                    },
                    style = TextStyle(
                        fontFamily = SansFontFamily(),
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = Muted
                    )
                )
                if (hasActivePhoto) {
                    val src = sourcePhotoBytes
                    if (src != null) {
                        PixelButton(
                            text = "Adjust crop",
                            icon = IconName.EDIT,
                            variant = ButtonVariant.SECONDARY,
                            size = ButtonSize.MD,
                            fullWidth = true,
                            enabled = !saving,
                            onClick = { cropTargetBytes = src }
                        )
                    }
                    PixelButton(
                        text = "Choose another photo",
                        icon = IconName.CAMERA,
                        variant = ButtonVariant.SECONDARY,
                        size = ButtonSize.MD,
                        fullWidth = true,
                        enabled = !saving,
                        onClick = launchPhotoPicker
                    )
                    PixelButton(
                        text = "Use pixel avatar",
                        icon = IconName.TRASH,
                        variant = ButtonVariant.DANGER,
                        size = ButtonSize.MD,
                        fullWidth = true,
                        enabled = !saving,
                        onClick = {
                            haptic()
                            sourcePhotoBytes = null
                            pickedPhotoBytes = null
                            pickedPhotoBitmap = null
                            removePhoto = true
                            onErrorTextChange(null)
                            mode = AvatarSheetMode.PIXEL
                        }
                    )
                }
            }
        }

        if (errorText != null) {
            Text(
                text = errorText!!,
                style = TextStyle(
                    fontFamily = SansFontFamily(),
                    fontSize = 13.sp,
                    color = DangerText
                )
            )
        }

        Spacer(modifier = Modifier.height(2.dp))

        if (mode == AvatarSheetMode.PHOTO && !hasActivePhoto) {
            PixelButton(
                text = "Choose photo",
                icon = IconName.CAMERA,
                size = ButtonSize.LG,
                fullWidth = true,
                enabled = !saving,
                onClick = launchPhotoPicker
            )
        } else {
            PixelButton(
                text = saveLabel,
                size = ButtonSize.LG,
                fullWidth = true,
                enabled = !saving,
                onClick = {
                    val normalizedAvatar = if (draftAvatar == defaultConfig) null else draftAvatar
                    val willRemovePhoto = (mode == AvatarSheetMode.PIXEL) || removePhoto
                    val newBytes = if (mode == AvatarSheetMode.PHOTO) pickedPhotoBytes else null
                    val newBitmap = if (mode == AvatarSheetMode.PHOTO) pickedPhotoBitmap else null
                    onSave(normalizedAvatar, newBytes, newBitmap, willRemovePhoto)
                }
            )
        }
    }

    val cropTarget = cropTargetBytes
    if (cropTarget != null) {
        PhotoCropDialog(
            sourceBytes = cropTarget,
            accentColor = accentColor,
            onConfirm = { cropped ->
                pickedPhotoBytes = cropped
                pickedPhotoBitmap = decodeImageBitmap(cropped)
                removePhoto = false
                onErrorTextChange(null)
                mode = AvatarSheetMode.PHOTO
                cropTargetBytes = null
            },
            onCancel = {
                cropTargetBytes = null
            }
        )
    }
}

@Composable
private fun ModeTab(
    label: String,
    icon: IconName,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .height(36.dp)
            .clip(PxClipShape)
            .background(if (selected) Ink else Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PixelIcon(
                name = icon,
                size = 15.dp,
                tint = if (selected) Color.White else Ink.copy(alpha = 0.65f)
            )
            Text(
                text = label,
                style = TextStyle(
                    fontFamily = PixelFontFamily(),
                    fontSize = 15.sp,
                    color = if (selected) Color.White else Ink.copy(alpha = 0.65f)
                )
            )
        }
    }
}

@Composable
private fun ColorSwatch(
    color: Color,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(32.dp)
            .clip(PxClipShape)
            .then(
                if (selected) Modifier.border(2.dp, Ink).padding(2.dp) else Modifier
            )
            .clip(PxClipShape)
            .background(color)
            .clickable(enabled = enabled, onClick = onClick)
    )
}

@Composable
private fun OptionChip(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(34.dp)
            .clip(PxClipShape)
            .background(if (selected) Ink else FieldBg)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = TextStyle(
                fontFamily = PixelFontFamily(),
                fontSize = 14.sp,
                color = if (selected) Color.White else Ink.copy(alpha = 0.78f)
            )
        )
    }
}
