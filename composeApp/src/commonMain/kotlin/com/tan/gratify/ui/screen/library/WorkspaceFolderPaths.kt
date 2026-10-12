package com.tan.gratify.ui.screen.library

import com.tan.gratify.viewModel.PlaylistFolder

internal fun folderBreadcrumbs(id:String,folders:List<PlaylistFolder>):List<PlaylistFolder> {
    val byId=folders.associateBy{it.id};val seen=mutableSetOf<String>();val chain=mutableListOf<PlaylistFolder>()
    var next:String?=id
    while(next!=null&&seen.add(next)) {val folder=byId[next]?:break;chain.add(folder);next=folder.parentId}
    return chain.reversed()
}
internal fun folderPath(id:String,folders:List<PlaylistFolder>)=folderBreadcrumbs(id,folders).joinToString(" / "){it.title}
internal fun folderMoveTargets(id:String,folders:List<PlaylistFolder>)=folders.filter{f->folderBreadcrumbs(f.id,folders).none{it.id==id}}
