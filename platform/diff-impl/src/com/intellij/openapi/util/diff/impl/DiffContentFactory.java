package com.intellij.openapi.util.diff.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.DiffContentUtil;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.diff.contents.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.LineSeparator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;

// TODO: pay attention here
public class DiffContentFactory {
  public static final Logger LOG = Logger.getInstance(DiffContentFactory.class);

  @NotNull
  public static EmptyContent createEmpty() {
    return new EmptyContent();
  }

  @NotNull
  public static DocumentContent create(@NotNull String text, @Nullable FileType type) {
    Document document = EditorFactory.getInstance().createDocument(text);
    return new DocumentContentImpl(document, type, null, null, null);
  }

  @NotNull
  public static DocumentContent create(@Nullable Project project, @NotNull Document document) {
    VirtualFile file = FileDocumentManager.getInstance().getFile(document);

    if (file != null) return new FileDocumentContentImpl(project, document, file);

    return new DocumentContentImpl(document);
  }

  @NotNull
  public static DiffContent create(@Nullable Project project, @NotNull VirtualFile file) {
    if (file.isDirectory()) return new DirectoryContentImpl(project, file);
    if (DiffContentUtil.isTextFile(file)) {
      Document document = FileDocumentManager.getInstance().getDocument(file);
      if (document != null) return new FileDocumentContentImpl(project, document, file);
      LOG.warn("Document not found for text file");
    }
    return new BinaryFileContentImpl(project, file);
  }

  @Nullable
  public static DocumentContent createDocument(@Nullable Project project, @NotNull VirtualFile file) {
    if (file.isDirectory()) return null;
    if (!DiffContentUtil.isTextFile(file)) return null;
    Document document = FileDocumentManager.getInstance().getDocument(file);
    if (document == null) return null;
    return new FileDocumentContentImpl(project, document, file);
  }

  @NotNull
  public static BinaryFileContentImpl createTemporalFile(@Nullable Project project,
                                                         @NotNull String name,
                                                         @NotNull String ext,
                                                         @NotNull byte[] content) throws IOException {
    File tempFile = FileUtil.createTempFile(name, "." + ext, true);
    if (content.length != 0) {
      FileUtil.writeToFile(tempFile, content);
    }
    final LocalFileSystem lfs = LocalFileSystem.getInstance();
    VirtualFile file = lfs.findFileByIoFile(tempFile);
    if (file == null) {
      file = lfs.refreshAndFindFileByIoFile(tempFile);
    }
    if (file == null) {
      throw new IOException("Can't create temp file for revision content");
    }
    return new BinaryFileContentImpl(project, file);
  }

  @NotNull
  public static Pair<Document, LineSeparator> buildDocument(@NotNull String text) {
    Pair<String, LineSeparator> pair = convertLineSeparators(text);
    Document document = EditorFactory.getInstance().createDocument(pair.getFirst());
    return Pair.create(document, pair.getSecond());
  }

  @NotNull
  private static Pair<String, LineSeparator> convertLineSeparators(@NotNull String text) {
    StringBuilder builder = null;

    // TODO: remove duplication with LoadTextUtil

    char prev = ' ';
    int crCount = 0;
    int lfCount = 0;
    int crlfCount = 0;

    final int length = text.length();

    for (int src = 0; src < length; src++) {
      char c = text.charAt(src);
      switch (c) {
        case '\r':
          if (builder == null) {
            builder = new StringBuilder(text.length());
            builder.append(text, 0, src);
          }
          builder.append('\n');
          crCount++;
          break;
        case '\n':
          if (prev == '\r') {
            crCount--;
            crlfCount++;
          }
          else {
            if (builder != null) builder.append('\n');
            lfCount++;
          }
          break;
        default:
          if (builder != null) builder.append(c);
          break;
      }
      prev = c;
    }

    LineSeparator separator;
    if (crCount == 0 && lfCount == 0 && crlfCount == 0) {
      separator = null;
    }
    else if (lfCount == 0 && crlfCount == 0) {
      separator = LineSeparator.CR;
    }
    else if (crCount == 0 && crlfCount == 0) {
      separator = LineSeparator.LF;
    }
    else if (crCount == 0 && lfCount == 0) {
      separator = LineSeparator.CRLF;
    }
    else if (crlfCount > crCount && crlfCount > lfCount) {
      separator = LineSeparator.CRLF;
    }
    else if (crCount > lfCount) {
      separator = LineSeparator.CR;
    }
    else if (lfCount > 0) {
      separator = LineSeparator.LF;
    }
    else {
      separator = null;
    }

    String result = builder == null ? text : builder.toString();

    return Pair.create(result, separator);
  }
}
