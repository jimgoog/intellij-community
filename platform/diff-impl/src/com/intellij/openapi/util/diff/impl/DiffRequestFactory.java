package com.intellij.openapi.util.diff.impl;

import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.diff.contents.DiffContent;
import com.intellij.openapi.util.diff.requests.DiffRequest;
import com.intellij.openapi.util.diff.requests.SimpleDiffRequest;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DiffRequestFactory {
  @NotNull
  public static DiffRequest createFromFile(@Nullable Project project, @NotNull VirtualFile file1, @NotNull VirtualFile file2) {
    DiffContent content1 = DiffContentFactory.create(project, file1);
    DiffContent content2 = DiffContentFactory.create(project, file2);

    String title1 = getVirtualFileContentTitle(file1);
    String title2 = getVirtualFileContentTitle(file2);

    String title = DiffBundle.message("diff.element.qualified.name.vs.element.qualified.name.dialog.title",
                                      file1.getName(), file2.getName());

    return new SimpleDiffRequest(title, content1, content2, title1, title2);
  }

  @NotNull
  public static String getVirtualFileContentTitle(@NotNull VirtualFile file) {
    String name = file.getName();
    VirtualFile parent = file.getParent();
    if (parent != null) {
      return name + " (" + FileUtil.toSystemDependentName(parent.getPath()) + ")";
    }
    return name;
  }
}
