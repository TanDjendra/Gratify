package com.tan.gratify.ui

import com.tan.gratify.ui.screen.library.*
import com.tan.gratify.viewModel.PlaylistFolder
import kotlin.test.*

class WorkspaceFolderPathsTest {
    private val folders=listOf(PlaylistFolder("a","Music"),PlaylistFolder("b","Travel","a"),PlaylistFolder("c","Quiet","b"),PlaylistFolder("d","Work"))
    @Test fun breadcrumbShowsActualHierarchy(){assertEquals("Music / Travel / Quiet",folderPath("c",folders));assertEquals(listOf("a","b","c"),folderBreadcrumbs("c",folders).map{it.id})}
    @Test fun moveTargetsExcludeSelfAndAllDescendants(){assertEquals(listOf("d"),folderMoveTargets("a",folders).map{it.id});assertEquals(listOf("a","d"),folderMoveTargets("b",folders).map{it.id})}
    @Test fun malformedServerCycleCannotLoopInUi(){val cycle=listOf(PlaylistFolder("a","A","b"),PlaylistFolder("b","B","a"));assertEquals(2,folderBreadcrumbs("a",cycle).size)}
}
