package io.writeopia.common.utils.icons

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FormatColorReset
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.ui.graphics.vector.ImageVector
import io.writeopia.common.utils.icons.all.ALargeSmall
import io.writeopia.common.utils.icons.all.ArrowDownAZ
import io.writeopia.common.utils.icons.all.ArrowDownUp
import io.writeopia.common.utils.icons.all.Bold
import io.writeopia.common.utils.icons.all.Bolt
import io.writeopia.common.utils.icons.all.CalendarArrowDown
import io.writeopia.common.utils.icons.all.ChartScatter
import io.writeopia.common.utils.icons.all.ChevronDown
import io.writeopia.common.utils.icons.all.ChevronRight
import io.writeopia.common.utils.icons.all.ChevronUp
import io.writeopia.common.utils.icons.all.CircleArrowLeft
import io.writeopia.common.utils.icons.all.CircleArrowRight
import io.writeopia.common.utils.icons.all.CirclePlus
import io.writeopia.common.utils.icons.all.ClipboardCopy
import io.writeopia.common.utils.icons.all.ClockArrowDown
import io.writeopia.common.utils.icons.all.CloudDownload
import io.writeopia.common.utils.icons.all.CloudUpload
import io.writeopia.common.utils.icons.all.Command
import io.writeopia.common.utils.icons.all.Contrast
import io.writeopia.common.utils.icons.all.Crosshair
import io.writeopia.common.utils.icons.all.Eye
import io.writeopia.common.utils.icons.all.EyeClosed
import io.writeopia.common.utils.icons.all.FileDown
import io.writeopia.common.utils.icons.all.FileMinus
import io.writeopia.common.utils.icons.all.FileUp
import io.writeopia.common.utils.icons.all.FolderOpen
import io.writeopia.common.utils.icons.all.FolderSync
import io.writeopia.common.utils.icons.all.House
import io.writeopia.common.utils.icons.all.Image
import io.writeopia.common.utils.icons.all.Italic
import io.writeopia.common.utils.icons.all.LayoutGrid
import io.writeopia.common.utils.icons.all.LayoutPanelLeft
import io.writeopia.common.utils.icons.all.Moon
import io.writeopia.common.utils.icons.all.MoveLeft
import io.writeopia.common.utils.icons.all.NotebookText
import io.writeopia.common.utils.icons.all.Play
import io.writeopia.common.utils.icons.all.Plus
import io.writeopia.common.utils.icons.all.Redo2
import io.writeopia.common.utils.icons.all.Rows3
import io.writeopia.common.utils.icons.all.Save
import io.writeopia.common.utils.icons.all.Search
import io.writeopia.common.utils.icons.all.SquareCode
import io.writeopia.common.utils.icons.all.Sun
import io.writeopia.common.utils.icons.all.Underline
import io.writeopia.common.utils.icons.all.Undo2
import io.writeopia.common.utils.icons.all.WandSparkles
import io.writeopia.common.utils.icons.all.X
import io.writeopia.common.utils.icons.all.Zap
import io.writeopia.common.utils.icons.all.Link

object WrIcons {
    val settings: ImageVector = Bolt

    val home: ImageVector = House

    val search: ImageVector = Search

    val notifications: ImageVector = Icons.Outlined.Notifications

    val favorites: ImageVector = Icons.Outlined.FavoriteBorder

    val file: ImageVector = FileMinus

    val fileDownload: ImageVector = FileDown

    val folder: ImageVector = FolderOpen

    val exportFile: ImageVector = FileUp

    val save: ImageVector = Save

    val sync: ImageVector = FolderSync

    val moreVert: ImageVector = Icons.Outlined.MoreVert

    val moreHoriz: ImageVector = Icons.Outlined.MoreHoriz

    val sort: ImageVector = ArrowDownUp

    val colorModeLight: ImageVector = Sun

    val colorModeDark: ImageVector = Moon

    val colorModeSystem: ImageVector = Contrast

    val bold: ImageVector = Bold

    val italic: ImageVector = Italic

    val underline: ImageVector = Underline

    val textStyle: ImageVector = ALargeSmall

    val pageStyle: ImageVector = NotebookText

    val code: ImageVector = SquareCode

    val addCircle: ImageVector = CirclePlus

    val add: ImageVector = Plus

    val target: ImageVector = Crosshair

    val layoutStaggeredGrid: ImageVector = LayoutPanelLeft

    val layoutGrid: ImageVector = LayoutGrid

    val layoutList: ImageVector = Rows3

    val close: ImageVector = X

    val delete: ImageVector = Icons.Outlined.DeleteOutline

    val transparent: ImageVector = Icons.Outlined.FormatColorReset

    val person: ImageVector = Icons.Outlined.Person

    val smallArrowRight: ImageVector = ChevronRight

    val smallArrowDown: ImageVector = ChevronDown

    val smallArrowUp: ImageVector = ChevronUp

    val undo: ImageVector = Undo2

    val redo: ImageVector = Redo2

    val sortByName: ImageVector = ArrowDownAZ

    val sortByCreated: ImageVector = CalendarArrowDown

    val sortByUpdate: ImageVector = ClockArrowDown

    val backArrowDesktop: ImageVector = MoveLeft

    val backArrowAndroid: ImageVector = Icons.AutoMirrored.Filled.ArrowBack

    val play: ImageVector = Play

    val circularArrowLeft = CircleArrowLeft

    val circularArrowRight = CircleArrowRight

    val visibilityOn = Eye

    val visibilityOff = EyeClosed

    val lock = Icons.Outlined.Lock

    val image = Image

    val zap = Zap

    val move = ClipboardCopy

    val download = CloudDownload

    val ai = WandSparkles

    val command = Command

    val cloudSync = CloudUpload

    val chart = ChartScatter

    val link = Link

    val allIcons: Map<String, ImageVector> =
        mapOf(
            "settings" to settings,
            "home" to home,
            "search" to search,
            "notifications" to notifications,
            "favorites" to favorites,
            "file" to file,
            "fileDownload" to fileDownload,
            "folder" to folder,
            "exportFile" to exportFile,
            "save" to save,
            "sync" to sync,
            "moreVert" to moreVert,
            "moreHoriz" to moreHoriz,
            "sort" to sort,
            "colorModeLight" to colorModeLight,
            "colorModeDark" to colorModeDark,
            "colorModeSystem" to colorModeSystem,
            "bold" to bold,
            "italic" to italic,
            "underline" to underline,
            "textStyle" to textStyle,
            "pageStyle" to pageStyle,
            "code" to code,
            "addCircle" to addCircle,
            "add" to add,
            "target" to target,
            "layoutStaggeredGrid" to layoutStaggeredGrid,
            "layoutGrid" to layoutGrid,
            "layoutList" to layoutList,
            "close" to close,
            "delete" to delete,
            "transparent" to transparent,
            "person" to person,
            "smallArrowRight" to smallArrowRight,
            "smallArrowDown" to smallArrowDown,
            "undo" to undo,
            "redo" to redo,
            "sortByName" to sortByName,
            "sortByCreated" to sortByCreated,
            "sortByUpdate" to sortByUpdate,
            "backArrowDesktop" to backArrowDesktop,
            "backArrowAndroid" to backArrowAndroid,
            "play" to play,
            "circularArrowLeft" to circularArrowLeft,
            "circularArrowRight" to circularArrowRight,
            "visibilityOn" to visibilityOn,
            "visibilityOff" to visibilityOff,
            "image" to image,
            "zap" to zap,
            "move" to move,
            "download" to download,
            "ai" to ai,
            "command" to command,
            "chart" to chart,
            "link" to link,
        )

    fun fromName(name: String): ImageVector? = allIcons[name]
}
