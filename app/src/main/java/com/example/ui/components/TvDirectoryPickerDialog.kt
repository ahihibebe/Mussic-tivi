package com.example.ui.components

import android.os.Environment
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.ui.theme.LocalTvStyle
import java.io.File
import java.util.Locale

@Composable
fun TvDirectoryPickerDialog(
    onSelectFolderPath: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val style = LocalTvStyle.current
    val initialDir = remember {
        runCatching { Environment.getExternalStorageDirectory() }
            .getOrNull()
            ?.takeIf { it.exists() && it.canRead() }
            ?: File("/storage")
    }
    var currentDir by remember { mutableStateOf(initialDir) }

    val subDirs = remember(currentDir.absolutePath) {
        val list = mutableListOf<File>()
        runCatching {
            currentDir.listFiles()
                ?.filter { it.isDirectory && !it.name.startsWith(".") && it.canRead() }
                ?.sortedBy { it.name.lowercase(Locale.ROOT) }
                ?.let { list.addAll(it) }
        }
        if (currentDir.absolutePath != "/storage") {
            // Also offer external /storage mounts when at primary root
            val storage = File("/storage")
            if (currentDir.absolutePath == initialDir.absolutePath && storage.exists()) {
                storage.listFiles()?.forEach { f ->
                    if (f.isDirectory && f.canRead() &&
                        !f.name.equals("self", true) &&
                        !f.name.equals("emulated", true) &&
                        list.none { it.absolutePath == f.absolutePath }
                    ) {
                        list.add(0, f)
                    }
                }
            }
        }
        list
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(style.sidebarSurface)
                .border(1.dp, style.borderColor, RoundedCornerShape(18.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    val parent = currentDir.parentFile
                    if (parent != null && parent.canRead()) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .tvFocusable(
                                    tag = "tv_dir_picker_up",
                                    shape = RoundedCornerShape(10.dp),
                                    onClick = { currentDir = parent }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Thư mục cha",
                                tint = style.textPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                    }
                    Column {
                        Text(
                            text = "Chọn thư mục trên TV / USB",
                            color = style.textPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = currentDir.absolutePath,
                            color = style.textSecondary,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .tvFocusable(
                            tag = "tv_dir_picker_close",
                            shape = RoundedCornerShape(10.dp),
                            onClick = onDismiss
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Đóng",
                        tint = style.textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 160.dp, max = 300.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (subDirs.isEmpty()) {
                    item {
                        Text(
                            text = "Không có thư mục con bên trong",
                            color = style.textMuted,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                } else {
                    items(subDirs, key = { it.absolutePath }) { dir ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .tvFocusable(
                                    tag = "tv_dir_item_${dir.absolutePath.hashCode()}",
                                    shape = RoundedCornerShape(10.dp),
                                    onClick = { currentDir = dir }
                                )
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = dir.name,
                                tint = style.amberColor,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = dir.name,
                                color = style.textPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .tvFocusable(
                            tag = "tv_dir_picker_select",
                            shape = RoundedCornerShape(10.dp),
                            defaultBgColor = style.accentColor,
                            focusedBgColor = style.accentColor.copy(alpha = 0.85f),
                            defaultBorderColor = style.accentColor,
                            onClick = { onSelectFolderPath(currentDir.absolutePath) }
                        )
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Chọn thư mục này",
                        tint = style.accentContentColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Quét thư mục này",
                        color = style.accentContentColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
